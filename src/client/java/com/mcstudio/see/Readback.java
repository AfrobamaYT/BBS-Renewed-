package com.mcstudio.see;

import com.mcstudio.util.RenderThread;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.GpuReadback;
import org.lwjgl.system.MemoryUtil;


import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * Readback — In-JVM framebuffer capture. The reliable "see" primitive.
 *
 * WHY THIS EXISTS: OS-level window capture photographs whatever window is on top (today it grabbed the
 * Claude-Code terminal instead of Minecraft). glReadPixels runs INSIDE the MC process against MC's own
 * GL context, so it reads the REAL game framebuffer regardless of which window is focused — and works
 * even when MC is not in the foreground. That is the whole point of In-JVM readback.
 *
 * OWNER: W2 (see/). This module self-registers its /shot route via StudioServer.route(...); it never
 * edits StudioServer.java or StudioClient.java. Lead adds ONE line "Readback.register();" to
 * StudioClient.onInitializeClient() BEFORE StudioServer.start() (routes must be registered before start).
 *
 * Inspector (also W2-owned) reuses captureFramebuffer(Framebuffer, String) to export its offscreen FBO.
 *
 * FAIL-LOUD (CLAUDE.md Regel 0 — the Rust-R3 lesson): if the buffer comes back all-zero we DO NOT report
 * a silent "ok"; the JSON carries {"warning":"framebuffer all-zero"} so a dead capture is never mistaken
 * for a black scene.
 */
public final class Readback
{
	private Readback()
	{
	}

	/**
	 * Schreibt einen BBS-eigenen Zwischenpuffer als PNG.
	 *
	 * <p>⛔ WOFUER (2026-08-11). Die Trackball-Hervorhebung erscheint in 26.2 nicht. Die
	 * Java-Seite laeuft nachweislich durch (Waechter offen, Maskenzug abgesetzt, Einkopie mit
	 * richtigem Rechteck), und ein RenderDoc-Mitschnitt zeigt die Kette auch im GL-Strom
	 * vollstaendig. Offen blieb allein, ob in den Puffern ueberhaupt etwas STEHT — und das
	 * beantwortet kein Zustandsabzug, nur ein Bild.
	 *
	 * <p>Die Summe aller RGB-Werte steht mit im Ergebnis: ein leerer Puffer ist damit eine
	 * Zahl, nicht ein Eindruck.
	 */
	/* ⛔ PAKETWEIT statt private (2026-09-09): die HTTP-Route liegt in SeeRouten im
	 * dev-Modul, im SELBEN Paket — diese Sichtbarkeit reicht, public waere zu viel. */
	static String pufferSchreiben(String was)
	{
		try
		{
			java.lang.reflect.Field f = mchorse.bbs_mod.ui.utils.Gizmo.class.getDeclaredField("sphereHighlight");

			f.setAccessible(true);

			mchorse.bbs_mod.ui.utils.StencilFormFramebuffer puffer =
				(mchorse.bbs_mod.ui.utils.StencilFormFramebuffer) f.get(mchorse.bbs_mod.ui.utils.Gizmo.INSTANCE);

			if (puffer == null || puffer.getFramebuffer() == null)
			{
				return "{\"error\":\"sphereHighlight ist noch nicht eingerichtet — erst mit Hover rendern lassen\"}";
			}

			mchorse.bbs_mod.graphics.texture.Texture textur = "zwischenbild".equals(was)
				? puffer.zwischenbild()
				: puffer.getFramebuffer().getMainTexture();

			return texturSchreiben(textur, new File(shotsDir(), "puffer-" + was + ".png").getAbsolutePath());
		}
		catch (Throwable t)
		{
			return "{\"error\":\"" + escape(String.valueOf(t)) + "\"}";
		}
	}

	/** Eine BBS-Textur als PNG, plus die RGB-Summe als Leer-Probe. Nur auf dem Render-Thread. */
	public static String texturSchreiben(mchorse.bbs_mod.graphics.texture.Texture textur, String pfad)
	{
		RenderSystem.assertOnRenderThread();

		if (textur == null || !textur.isValid())
		{
			return "{\"error\":\"textur fehlt oder ist ungueltig\"}";
		}

		mchorse.bbs_mod.utils.resources.Pixels pixels =
			new mchorse.bbs_mod.utils.resources.Pixels(
                GpuReadback.readRgba8(textur.gpu(), GpuReadback.Origin.NATIVE), textur.width, textur.height);

		if (pixels == null)
		{
			return "{\"error\":\"pixelsFromTexture lieferte null\"}";
		}

		int width = textur.width;
		int height = textur.height;
		long summe = 0L;

		try
		{
			ByteBuffer buffer = pixels.getBuffer();
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

			for (int y = 0; y < height; ++y)
			{
				for (int x = 0; x < width; ++x)
				{
					int i = (y * width + x) * 4;
					int r = buffer.get(i) & 0xFF;
					int g = buffer.get(i + 1) & 0xFF;
					int b = buffer.get(i + 2) & 0xFF;

					summe += r + g + b;

					/* Undurchsichtig aus demselben Grund wie beim Bildabzug: das Alpha ist hier
					 * eine Kennung, kein Deckungswert -- als PNG-Alpha macht es das Bild
					 * unlesbar. Es steht stattdessen im blauen Kanal des Berichts. */
					image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
				}
			}

			ImageIO.write(image, "png", new File(pfad));
		}
		catch (Exception e)
		{
			return "{\"error\":\"" + escape(String.valueOf(e)) + "\"}";
		}
		finally
		{
			pixels.delete();
		}

		return "{\"w\":" + width + ",\"h\":" + height + ",\"summeRGB\":" + summe
			+ ",\"path\":\"" + escape(pfad) + "\"}";
	}

	/**
	 * Capture MC's main (on-screen) framebuffer to a PNG. MUST run on the render thread — call it via
	 * RenderThread.submit(...). Returns JSON {w,h,ms,path} (plus {"warning":"framebuffer all-zero"} if
	 * the pixels came back empty).
	 */
	public static String captureMainFramebuffer(String path)
	{
		Minecraft client = Minecraft.getInstance();

		if (client == null)
		{
			return "{\"error\":\"MinecraftClient not available\"}";
		}

		RenderTarget fb = client.gameRenderer.mainRenderTarget();

		if (fb == null)
		{
			return "{\"error\":\"main framebuffer not available\"}";
		}

		return captureFramebuffer(fb, path);
	}

    /** Capture this exact GPU target; the framebuffer convention is an explicit row flip. */
    public static String captureFramebuffer(RenderTarget target, String path)
    {
        RenderSystem.assertOnRenderThread();
        long started = System.nanoTime();

        try
        {
            verifyTarget(target);
            return writeSnapshot(GpuReadback.readRaw(target.getColorTexture()), path, started);
        }
        catch (Throwable error)
        {
            return "{\"error\":\"capture failed: " + escape(error.toString()) + "\"}";
        }
    }

    /** Queue the current frame's GPU snapshot and complete after the actual PNG write. */
    public static java.util.concurrent.CompletableFuture<String> captureFramebufferAsync(RenderTarget target, String path)
    {
        RenderSystem.assertOnRenderThread();
        verifyTarget(target);
        long started = System.nanoTime();
        java.util.concurrent.CompletableFuture<String> result = new java.util.concurrent.CompletableFuture<>();
        GpuReadback.requestRaw(target.getColorTexture(), new GpuReadback.Region(0, 0, target.width, target.height)).whenReady(
            raw -> new Thread(() ->
            {
                try { result.complete(writeSnapshot(raw, path, started)); }
                catch (Throwable error) { result.completeExceptionally(error); }
            }, "bbs-world-snapshot-writer").start(), result::completeExceptionally);
        return result;
    }

    private static void verifyTarget(RenderTarget target)
    {
        if (target == null || target.getColorTexture() == null || target.getColorTexture().isClosed()
            || target.width <= 0 || target.height <= 0
            || target.width != target.getColorTexture().getWidth(0)
            || target.height != target.getColorTexture().getHeight(0))
        {
            throw new IllegalArgumentException("Capture target is incomplete, closed or has stale dimensions");
        }
        GpuReadback.requireRgba8Convertible(target.getColorTexture().getFormat());
    }

    private static String writeSnapshot(GpuReadback.Raw raw, String path, long started) throws java.io.IOException
    {
        int width = raw.width(), height = raw.height();
        ByteBuffer rgba = null;
        boolean allZero = true;

        try (raw)
        {
            rgba = raw.rgba8(GpuReadback.Origin.TOP_LEFT);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    int index = (y * width + x) * 4;
                    int r = rgba.get(index) & 0xFF, g = rgba.get(index + 1) & 0xFF;
                    int b = rgba.get(index + 2) & 0xFF, a = rgba.get(index + 3) & 0xFF;
                    allZero &= (r | g | b | a) == 0;
                    /* Preserve the existing opaque world/diagnostic PNG contract. */
                    image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
                }
            }

            File file = new File(path);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            if (!ImageIO.write(image, "png", file)) throw new java.io.IOException("No PNG writer available");
        }
        finally
        {
            if (rgba != null) MemoryUtil.memFree(rgba);
        }

        long ms = (System.nanoTime() - started) / 1_000_000L;
        return "{\"w\":" + width + ",\"h\":" + height + ",\"ms\":" + ms
            + ",\"path\":\"" + escape(path) + "\""
            + (allZero ? ",\"warning\":\"framebuffer all-zero\"" : "") + "}";
    }

	/**
	 * The mcstudio-shots output folder, created on demand. Anchored to MC's canonical run directory
	 * (MinecraftClient.runDirectory) — the same base MC uses for run/screenshots. Using a relative
	 * "run/..." here would double to run/run/... because runClient already sets the CWD to run/.
	 */
	static File shotsDir()
	{
		Minecraft client = Minecraft.getInstance();
		File base = (client != null && client.gameDirectory != null) ? client.gameDirectory : new File(".");
		File dir = new File(base, "mcstudio-shots");
		dir.mkdirs();
		return dir;
	}

	/* Minimal JSON string escaping (backslash + quote) for paths and error messages. */
	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}

		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

}
