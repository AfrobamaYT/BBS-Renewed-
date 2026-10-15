package com.mcstudio.see;

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

	/* v2-Zustand (2026-08-16): scharfgestellter Pfad + Ergebnis-Future. Nach einem Hotswap sind
	 * neue statische Felder null/false — genau der korrekte Ruhezustand, kein Initialisierer noetig. */
	private static volatile String armPfad;
	private static volatile java.util.concurrent.CompletableFuture<String> armErgebnis;
	private static volatile boolean lauscherDa;

	private WorldShot()
	{
	}

	/**
	 * v2-Lauscher: faengt den Welt-Frame IM normalen Frame, am Ende des Level-Passes (END_MAIN),
	 * bevor die GUI-Phase zeichnet — Welt ohne HUD/Dashboard, ohne jeden Off-Schedule-Render.
	 *
	 * ⛔ WARUM v1 STARB (crash-2026-08-16_20.46.52): renderLevel ausser der Reihe bricht auf 26.2
	 * an den Frame-Graph-Invarianten ("Global terrain uniforms have not been updated") und laesst
	 * den FeatureRenderDispatcher$PreparedFrame scharf zurueck — der NAECHSTE regulaere Frame
	 * stirbt mit "PreparedFrame already in use". Der Frame-Graph ist zustandsbehaftet; einen
	 * zweiten Welt-Render im selben Tick gibt es nicht mehr. Also: den einen echten Frame fangen.
	 */
	static final class EndMainLauscher implements net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.Last
	{
		@Override
		public void onLast(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context)
		{
			/* 26.3: bei END_MAIN ist der Welt-Pass noch offen, GpuReadback.outsidePass verbietet
			 * das Ruecklesen ("Close the render pass before BBS GPU readback", gemessen
			 * 2026-10-14). Gefangen wird darum erst vor dem GUI-Zeichnen (WeltAbgriffNachPass, aus
			 * SpielRendererLogik.vorGuiZeichnen, context == null); der Fabric-Aufruf tut nichts. */
			if (context != null)
			{
				return;
			}

			String pfad = armPfad;

			if (pfad == null)
			{
				return;
			}

			armPfad = null;

			java.util.concurrent.CompletableFuture<String> f = armErgebnis;
			try
			{
				/* Der Film-Editor zeichnet die Welt in BBS' eigenen Puffer (Viewport); vor dem
				 * GUI-Zeichnen liegt das fertige Weltbild dort - dieselbe Quelle, aus der
				 * BBSRendering.fuellen() die Vorschau fuellt. Das Fensterziel war gemessen leer. */
				com.mojang.blaze3d.pipeline.RenderTarget ziel = mchorse.bbs_mod.client.BBSRendering.isCustomSize()
					? mchorse.bbs_mod.client.BBSRendering.getFramebuffer() : null;

				Readback.captureFramebufferAsync(ziel != null ? ziel : Minecraft.getInstance().gameRenderer.mainRenderTarget(), pfad)
                    .whenComplete((result, error) ->
                    {
                        String json = error == null ? result
                            : "{\"error\":\"capture failed: " + escape(error.toString()) + "\"}";
                        if (json.endsWith("}"))
                        {
                            json = json.substring(0, json.length() - 1) + ",\"world\":true,\"hud\":false}";
                        }
                        if (f != null) f.complete(json);
                    });
			}
			catch (Throwable t)
			{
				if (f != null) f.complete("{\"error\":\"capture failed: " + escape(t.toString()) + "\"}");
			}
		}
	}

	/**
	 * Auf dem Render-Thread scharfstellen (via RenderThread.submit aus der Route). Gibt bei
	 * Vorbedingungsfehlern sofort das Fehler-JSON zurueck, sonst null ("scharf, warte auf Frame").
	 */
	/* ⛔ PAKETWEIT statt private (2026-09-09): die HTTP-Route liegt in SeeRouten im
	 * dev-Modul, im SELBEN Paket — diese Sichtbarkeit reicht, public waere zu viel. */
	static String scharfstellen(String pfad)
	{
		if (armErgebnis != null && !armErgebnis.isDone())
        {
            return "{\"error\":\"Another world snapshot is still pending\"}";
        }
		Minecraft client = Minecraft.getInstance();

		if (client == null || client.gameRenderer == null)
		{
			return "{\"error\":\"MinecraftClient not available\"}";
		}

		if (client.level == null)
		{
			return "{\"error\":\"no world loaded (in menu?) — /shot_world needs an in-game world\"}";
		}

		if (!lauscherDa)
		{
			/* Registrierung zur Laufzeit statt im Boot: wir stehen hier AUF dem Render-Thread,
			 * der die Event-Liste iteriert — zwischen zwei Frames ist das add rennfrei. */
			net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(new EndMainLauscher());
			lauscherDa = true;
		}

		armErgebnis = new java.util.concurrent.CompletableFuture<>();
		armPfad = pfad;

		return null;
	}

	/**
	 * Einen Welt-Frame fangen, komplett: scharfstellen auf dem Render-Thread, dann hier (Aufrufer-
	 * Thread, NIE der Render-Thread) auf den naechsten echten Frame warten. Fuer Sequenz-Treiber
	 * (WeltbildFahrt), die pro Tick scrubben und fangen — ein Aufruf, ein Frame, ein JSON.
	 */
	public static String fangen(String pfad)
	{
		String sofort = RenderThread.submit(() -> scharfstellen(pfad));

		return sofort == null ? warten() : sofort;
	}

	/** HTTP-Thread: auf den naechsten Frame warten. Laut scheitern statt ewig haengen. */
	/* ⛔ PAKETWEIT statt private (2026-09-09): die HTTP-Route liegt seit dem Companion-Umbau
	 * in com.mcstudio.see.SeeRouten im dev-Modul — im SELBEN Paket, damit genau diese
	 * Sichtbarkeit reicht und nichts public werden muss. */
	static String warten()
	{
		java.util.concurrent.CompletableFuture<String> f = armErgebnis;

		if (f == null)
		{
			return "{\"error\":\"armed without future (bug)\"}";
		}

		try
		{
			return f.get(10, java.util.concurrent.TimeUnit.SECONDS);
		}
		catch (java.util.concurrent.TimeoutException t)
		{
			armPfad = null;

			return "{\"error\":\"no frame within 10s — is the game rendering (window minimized? no level pass)?\"}";
		}
		catch (Exception e)
		{
			armPfad = null;

			return "{\"error\":\"wait failed: " + escape(String.valueOf(e)) + "\"}";
		}
	}

	/**
	 * ⛔ v1, TOT seit 2026-08-16 — Off-Schedule-renderLevel toetet auf 26.2 den Folgeframe
	 * (PreparedFrame-Absturz, siehe EndMainLauscher). Von keiner Route mehr gerufen; bleibt nur,
	 * weil Methoden-ENTFERNUNG beim Hotswap unbelegt ist. Beim naechsten natuerlichen Boot loeschen.
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

			/* 26.2: setClearColor UND clear() sind beide entfallen -- die Loeschfarbe wird nicht
			 * mehr am Ziel GEPARKT und spaeter benutzt, sondern beim Loeschen mitgegeben. Genau
			 * hier lag ein Fehler aus einem frueheren Teil-Port: die Himmelsfarbe wurde oben
			 * berechnet und dann verworfen, geloescht wurde hart auf Schwarz. Ein Bild mit
			 * schwarzem statt Himmel-Hintergrund haette kein Compiler gemeldet.
			 *
			 * Der macOS-Schalter faellt ersatzlos weg: den Sonderweg gibt es nicht mehr. */
			mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder()
			    .clearColorAndDepthTextures(
			        target.getColorTexture(), new org.joml.Vector4f(clear[0], clear[1], clear[2], 1.0F),
			        target.getDepthTexture(), 0.0);

			/* THE SWAP: point client.framebuffer at our FBO so every getFramebuffer() inside
			 * WorldRenderer.render targets OUR offscreen buffer, never the on-screen one. */
			/* 26.2: das Feld Minecraft.mainRenderTarget gibt es nicht mehr. Umgelenkt wird ueber
			 * RenderSystem.outputColorTextureOverride -- kein Renderziel, sondern dessen
			 * Farb-Textursicht. Vanilla macht es selbst so (LevelRenderer, im Bytecode). */
			mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride = target.getColorTextureView();

			/* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
			 * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
			 * zeichnen will, gibt das Ziel dort an. */

			/* Render ONLY the world — no HUD (that is drawn later in GameRenderer.render, which we do NOT call).
			 * Uses the current live render tick counter (same one the game frame uses) for interpolation. */
			DeltaTracker tickCounter = client.getTimer();
			//? if >=26.3 {
			/*gameRenderer.renderLevel();
			*///?} else {
			gameRenderer.renderLevel(tickCounter.getGameTimeDeltaPartialTick(false), net.minecraft.Util.getNanos(), new com.mojang.blaze3d.vertex.PoseStack());
			//?}

			/* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
			 * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
			 * zeichnen will, gibt das Ziel dort an. */
		}
		catch (Throwable t)
		{
			/* Restore before returning so a failed render can never leave the game pointed at our FBO. */
			/* 26.2: zurueckstellen heisst null, NICHT "das alte Ziel setzen". null ist der
			 * Normalzustand der Umlenkung; wer stattdessen den Hauptpuffer einsetzt, laesst die
			 * Umlenkung stehen und zeichnet alles Folgende dort hinein. */
			mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride = null;
			/* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
			 * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
			 * zeichnen will, gibt das Ziel dort an. */

			return "{\"error\":\"renderWorld failed: " + escape(String.valueOf(t)) + "\"}";
		}
		finally
		{
			/* ALWAYS restore the real framebuffer + rebind it, so the next real game frame renders to the
			 * screen normally. Without this the whole game would keep drawing into our offscreen FBO = black screen. */
			/* 26.2: zurueckstellen heisst null, NICHT "das alte Ziel setzen". null ist der
			 * Normalzustand der Umlenkung; wer stattdessen den Hauptpuffer einsetzt, laesst die
			 * Umlenkung stehen und zeichnet alles Folgende dort hinein. */
			mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride = null;
			/* 26.2: bindWrite/unbindWrite sind entfallen. Ein Renderziel wird nicht mehr global
			 * gebunden, sondern beim Anlegen eines RenderPass benannt -- wer woandershin
			 * zeichnen will, gibt das Ziel dort an. */
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
		/* 26.2: BEIDE alten Wege sind entfallen -- RenderSystem.getShaderFogColor (der feste
		 * Shader-Zustand ist weg) und ClientLevel.getSkyColor. Der Ersatz ist keine Notloesung,
		 * sondern eine bessere Quelle: Nebel- und Himmelfarbe sind UMGEBUNGSATTRIBUTE geworden,
		 * ortsabhaengig abfragbar und von den neuen Zeitleisten steuerbar.
		 *
		 * Reihenfolge bleibt wie vorher gedacht: erst der Nebel (das ist die Farbe, in die die
		 * Ferne blendet, also genau das, was am Horizont fehlen wuerde), dann der Himmel.
		 *
		 * ⚠️ Die Attribute liefern gepacktes ARGB, keine 0..1-Fliesskommazahlen. */
		net.minecraft.world.phys.Vec3 wo = client.gameRenderer.mainCamera().position();

		try
		{
			//? if >=26.3 {
			/*org.joml.Vector3fc nebel = client.level.environmentAttributes()
				.getValue(net.minecraft.world.attribute.EnvironmentAttributes.FOG_COLOR, wo);

			/^ Gegen eine ungesetzte Farbe absichern -- ganz schwarz waere genau das, was wir
			 * vermeiden wollen. ^/
			if (nebel.lengthSquared() != 0F)
			{
				return new float[]{ nebel.x(), nebel.y(), nebel.z() };
			}
			*///?} else {
			//? if >=1.21.11 {
			/*int nebel = client.level.environmentAttributes()
				.getValue(net.minecraft.world.attribute.EnvironmentAttributes.FOG_COLOR, wo);
			*///?} else {
			net.minecraft.client.renderer.FogParameters f = mchorse.bbs_mod.compat.gpu.Vorgaben.nebelParameter();
			int nebel = net.minecraft.util.ARGB.colorFromFloat(1F, f.red(), f.green(), f.blue());
			//?}

			if ((nebel & 0xFFFFFF) != 0)
			{
				return entpacken(nebel);
			}
			//?}
		}
		catch (Throwable ignored)
		{
			/* weiter zum Himmel */
		}

		try
		{
			//? if >=26.3 {
			/*org.joml.Vector3fc himmel = client.level.environmentAttributes()
				.getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, wo);

			if (himmel.lengthSquared() != 0F)
			{
				return new float[]{ himmel.x(), himmel.y(), himmel.z() };
			}
			*///?} else {
			//? if >=1.21.11 {
			/*int himmel = client.level.environmentAttributes()
				.getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, wo);
			*///?} else {
			net.minecraft.world.phys.Vec3 himmelV = client.level.getSkyColor(wo, 1F); int himmel = net.minecraft.util.ARGB.colorFromFloat(1F, (float) himmelV.x, (float) himmelV.y, (float) himmelV.z);
			//?}

			if ((himmel & 0xFFFFFF) != 0)
			{
				return entpacken(himmel);
			}
			//?}
		}
		catch (Throwable ignored)
		{
			/* weiter zur Konstanten */
		}

		/* Never black: a plain daytime sky-blue as the absolute last resort. */
		return new float[]{ 0.47F, 0.65F, 1.0F };
	}

	/** Gepacktes ARGB in die drei 0..1-Kanaele, die der Loesch-Aufruf will. */
	private static float[] entpacken(int argb)
	{
		return new float[]{
			net.minecraft.util.ARGB.red(argb) / 255F,
			net.minecraft.util.ARGB.green(argb) / 255F,
			net.minecraft.util.ARGB.blue(argb) / 255F
		};
	}

	/**
	 * Allocate (once) or resize our offscreen FBO to (w,h). Render thread only (SimpleFramebuffer allocates GL
	 * objects). WITH a depth attachment — renderWorld needs depth for correct occlusion / translucency sorting.
	 */
	private static TextureTarget framebuffer(int w, int h)
	{
		if (fbo == null)
		{
			fbo = new TextureTarget(w, h, true, net.minecraft.client.Minecraft.ON_OSX);
		}
		else if (fbo.width != w || fbo.height != h)
		{
			fbo.resize(w, h);
		}

		return fbo;
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}

		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
