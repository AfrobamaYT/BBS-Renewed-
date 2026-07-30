package com.mcstudio.see;

import com.mcstudio.http.StudioServer;
import com.mcstudio.util.RenderThread;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import java.io.File;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/**
 * WorldShot — the UI-FREE world frame. Renders MC's WORLD (terrain + sky + entities, NO HUD, NO BBS
 * dashboard) into a DEDICATED offscreen framebuffer and exports it as PNG. Route: /shot_world?name=X.
 *
 * WHY THIS EXISTS (the composite blocker): /shot captures MC's MAIN on-screen framebuffer AS IT IS — with
 * the BBS editor / UIFilmPanel dashboard baked into the pixels (SYMPTOME.md "/shot-frame enthält das
 * BBS-dashboard"). The BBS↔Blender hybrid composite needs the world layer ALONE (dashboard-free) so it can
 * sit behind the Blender-rendered layer. This is Option (c) from that SYMPTOME entry, named there as the
 * "aussichtsreichste + regel-konforme pfad": render the world into an OFFSCREEN FBO, never touch the screen.
 *
 * WHY NOT setScreen(null) (the crash the project hit 3x): setScreen(null) mutates BBS's live screen +
 * film-controller state mid-frame -> the startRenderFrame entity/replay-index IndexOutOfBounds crash
 * (SYMPTOME.md, 3x reproduced). This class NEVER calls setScreen and NEVER touches the BBS film controller.
 * The real on-screen dashboard is left completely intact; we only render into a SEPARATE FBO and read it back.
 *
 * THE ONE TRICK (framebuffer swap): GameRenderer.renderWorld(RenderTickCounter) renders the world WITHOUT the
 * HUD (the HUD is drawn later, only in the full GameRenderer.render(...) path). BUT WorldRenderer.render — which
 * renderWorld calls — hardcodes client.getFramebuffer() as the render target between its multi-passes
 * (translucency / particles / clouds / weather each do getFramebuffer().beginWrite()). Verified in the
 * 1.21.1 yarn bytecode. So binding our own FBO up front is NOT enough; WorldRenderer would rebind the MAIN
 * (on-screen) FBO and paint the world over the dashboard. FIX: MinecraftClient.framebuffer is a PUBLIC,
 * non-final field, and getFramebuffer() returns it. We temporarily POINT that field at our offscreen FBO for
 * the duration of the render, then restore it in a finally. Every getFramebuffer() inside renderWorld now
 * targets OUR FBO; the real screen is never written. This is the whole mechanism.
 *
 * SIZE MATCHES THE WINDOW: WorldRenderer's auxiliary framebuffers (entity/translucent/particles/clouds) are
 * sized to the window's framebuffer, and it copyDepthFrom()s between them and client.getFramebuffer(). Our FBO
 * must match the window's framebuffer size so those depth-copies and the projection aspect line up exactly.
 * We allocate/resize to the current window framebuffer dims on every call.
 *
 * CAMERA (v1): renderWorld updates + uses the CURRENT player camera itself (camera.update from the player
 * entity, FOV/projection derived live). So this v1 frame is from the CURRENT in-game camera. The composite
 * wants camera-sync with Blender; when a BBS camera controller / FilmCam is active, renderWorld already runs
 * through the BBS GameRendererMixin (fov/roll/authority injects) — the same camera path BBS itself renders
 * with — so a FilmCam pose is honored automatically. Driving an ARBITRARY camera (independent of the live
 * game camera) is NOT done here; see HONEST LIMITS below.
 *
 * OWNER: see/ (same domain as Readback/Inspector). Self-registers /shot_world via StudioServer.route(...);
 * reuses Readback.captureFramebuffer + Readback.shotsDir (no cross-file edit). Lead adds ONE register line.
 *
 * RENDER-THREAD-CORRECT: all GL work runs inside RenderThread.submit(...) (like Inspector/Readback). The
 * caller (HTTP handler thread) blocks on the result. Never call renderWorldToFbo off the render thread.
 */
public final class WorldShot
{
	/** Our dedicated offscreen world FBO, allocated lazily on the render thread, resized to the window. */
	private static TextureTarget fbo;

	private WorldShot()
	{
	}

	/**
	 * Self-register /shot_world. Called by StudioClient (one line, added by Lead) BEFORE StudioServer.start().
	 * /shot_world?name=foo renders the dashboard-free world into run/mcstudio-shots/foo.png.
	 */
	public static void register()
	{
		StudioServer.route("/shot_world", exchange ->
		{
			String name = StudioServer.query(exchange, "name");

			if (name == null || name.isEmpty())
			{
				name = "world";
			}

			/* Sanitize to a bare filename (no path traversal, no separators) — same rule as /shot. */
			name = name.replaceAll("[^A-Za-z0-9._-]", "_");

			File out = new File(Readback.shotsDir(), name + ".png");

			final String path = out.getAbsolutePath();
			String json = RenderThread.submit(() -> renderWorldToFbo(path));

			StudioServer.respond(exchange, json);
		});

		System.out.println("[mcstudio] WorldShot registered /shot_world");
	}

	/**
	 * Render the current world (no HUD, no dashboard) into our offscreen FBO and export it via Readback.
	 * MUST run on the render thread (call via RenderThread.submit). Returns Readback's {w,h,ms,path} JSON on
	 * success, or an explicit error JSON (FAIL-LOUD, never a silent success).
	 */
	public static String renderWorldToFbo(String path)
	{
		com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();

		Minecraft client = Minecraft.getInstance();

		if (client == null)
		{
			return "{\"error\":\"MinecraftClient not available\"}";
		}

		/* renderWorld dereferences client.level with NO null-guard (verified in 1.21.1 bytecode: it reads
		 * client.level.getTickManager() etc.) — calling it in the menu would NPE. Fail loud instead. */
		if (client.level == null)
		{
			return "{\"error\":\"no world loaded (in menu?) — /shot_world needs an in-game world\"}";
		}

		GameRenderer gameRenderer = client.gameRenderer;

		if (gameRenderer == null)
		{
			return "{\"error\":\"gameRenderer not available\"}";
		}

		Window window = client.getWindow();

		if (window == null)
		{
			return "{\"error\":\"window not available\"}";
		}

		/* Match the window's framebuffer size so WorldRenderer's aux framebuffers (entity/translucent/particles),
		 * which are sized to the window, depth-copy against our FBO consistently, and the aspect ratio is right. */
		int width = window.getWidth();
		int height = window.getHeight();

		if (width <= 0 || height <= 0)
		{
			return "{\"error\":\"window framebuffer has zero size " + width + "x" + height + "\"}";
		}

		TextureTarget target = framebuffer(width, height);

		/* Keep the real on-screen framebuffer so we can restore it no matter what renderWorld does. This is the
		 * ONLY game state we mutate, and it is restored in the finally below. */
		RenderTarget mainFbo = client.gameRenderer.mainRenderTarget();

		long t0 = System.nanoTime();

		try
		{
			/* Clear our FBO to the SKY/FOG colour, not black. WHY (the horizon-notch bug): the old clear was
			 * opaque black (0,0,0,1) on the ASSUMPTION that renderWorld's sky "fills the frame". At the HORIZON
			 * that assumption breaks — a far chunk at the render-distance edge, or the fog/sky cutoff, can leave a
			 * thin band of pixels that NEITHER the sky-dome NOR geometry NOR fog paints. Those un-painted pixels
			 * keep the clear colour, so a black clear shows a black chunk-load notch at the horizon (SYMPTOME:
			 * "schwarzer notch im WorldShot-horizont"). Clearing to the sky colour instead makes any such un-painted
			 * gap read as sky, not black — the notch becomes invisible. We derive the colour from the ACTUAL fog
			 * colour renderWorld will apply (RenderSystem.getShaderFogColor, read here on the render thread where it
			 * is legal + current); if that is unavailable/degenerate we fall back to world.getSkyColor for the
			 * camera. Alpha stays 1 (opaque) so the composite treats the frame as a solid world layer. */
			float[] clear = skyClearColor(client);
			target.setClearColor(clear[0], clear[1], clear[2], 1.0F);
			target.clear(Minecraft.ON_OSX);

			/* THE SWAP: point client.framebuffer at our FBO so every getFramebuffer() inside
			 * WorldRenderer.render targets OUR offscreen buffer, never the on-screen one. */
			client.mainRenderTarget = target;

			target.bindWrite(true);

			/* Render ONLY the world — no HUD (that is drawn later in GameRenderer.render, which we do NOT call).
			 * Uses the current live render tick counter (same one the game frame uses) for interpolation. */
			DeltaTracker tickCounter = client.getDeltaTracker();
			gameRenderer.renderLevel(tickCounter);

			target.unbindWrite();
		}
		catch (Throwable t)
		{
			/* Restore before returning so a failed render can never leave the game pointed at our FBO. */
			client.mainRenderTarget = mainFbo;
			mainFbo.bindWrite(true);

			return "{\"error\":\"renderWorld failed: " + escape(String.valueOf(t)) + "\"}";
		}
		finally
		{
			/* ALWAYS restore the real framebuffer + rebind it, so the next real game frame renders to the
			 * screen normally. Without this the whole game would keep drawing into our offscreen FBO = black screen. */
			client.mainRenderTarget = mainFbo;
			mainFbo.bindWrite(true);
		}

		/* Export our FBO to PNG via the shared Readback helper (see/ owns both). */
		String json = Readback.captureFramebuffer(target, path);

		long ms = (System.nanoTime() - t0) / 1_000_000L;

		/* Tag the total render+capture time + a "world":true marker so the caller can distinguish a real
		 * world frame from a /shot dashboard frame. */
		if (json.endsWith("}"))
		{
			json = json.substring(0, json.length() - 1)
				+ ",\"renderMs\":" + ms + ",\"world\":true,\"hud\":false}";
		}

		return json;
	}

	/**
	 * The colour to CLEAR the offscreen FBO to so the horizon-notch (un-painted horizon pixels) reads as sky, not
	 * black. Render-thread only (touches RenderSystem). Preference order:
	 *   1. RenderSystem.getShaderFogColor() — the EXACT fog tint renderWorld fades the far horizon to; a notch at
	 *      the render-distance edge sits inside this fog band, so matching it hides the notch perfectly.
	 *   2. world.getSkyColor(camera-pos) — fallback if the fog colour is null/degenerate (e.g. a fresh frame
	 *      before any world render populated it). Still a correct sky-blue, far better than black.
	 *   3. A neutral sky-blue constant — last-ditch so we NEVER fall back to black (the whole point of this fix).
	 * Returns {r,g,b} in 0..1.
	 */
	private static float[] skyClearColor(Minecraft client)
	{
		try
		{
			float[] fog = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();

			/* Guard against an un-set fog colour (all-zero = would be the black we are trying to avoid). */
			if (fog != null && fog.length >= 3 && (fog[0] + fog[1] + fog[2]) > 0.001F)
			{
				return new float[]{ fog[0], fog[1], fog[2] };
			}
		}
		catch (Throwable ignored)
		{
			/* fall through to the sky-colour path */
		}

		try
		{
			net.minecraft.world.phys.Vec3 sky = client.level.getSkyColor(
				client.gameRenderer.mainCamera().position(),
				client.getDeltaTracker().getGameTimeDeltaPartialTick(true));

			if (sky != null)
			{
				return new float[]{ (float) sky.x, (float) sky.y, (float) sky.z };
			}
		}
		catch (Throwable ignored)
		{
			/* fall through to the constant */
		}

		/* Never black: a plain daytime sky-blue as the absolute last resort. */
		return new float[]{ 0.47F, 0.65F, 1.0F };
	}

	/**
	 * Allocate (once) or resize our offscreen FBO to (w,h). Render thread only (SimpleFramebuffer allocates GL
	 * objects). WITH a depth attachment — renderWorld needs depth for correct occlusion / translucency sorting.
	 */
	private static TextureTarget framebuffer(int w, int h)
	{
		if (fbo == null)
		{
			fbo = new TextureTarget(w, h, true, Minecraft.ON_OSX);
		}
		else if (fbo.width != w || fbo.height != h)
		{
			fbo.resize(w, h, Minecraft.ON_OSX);
		}

		return fbo;
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}

		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
