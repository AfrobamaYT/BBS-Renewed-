package com.mcstudio.see;

import com.mcstudio.util.RenderThread;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

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
			mchorse.bbs_mod.graphics.texture.Texture.pixelsFromTexture(textur);

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

	/**
	 * Capture ANY Framebuffer's color attachment to a PNG. Shared by the main-shot path and by Inspector's
	 * offscreen FBO. MUST run on the render thread.
	 *
	 * FLOW: bind the FBO for reading -> glReadPixels(GL_BGRA, UNSIGNED_BYTE) -> pack into an INT_ARGB
	 * BufferedImage with a Y-FLIP (OpenGL origin is bottom-left, PNG/BufferedImage is top-left) -> ImageIO.
	 * Timed with System.nanoTime around the whole capture+encode.
	 */
	public static String captureFramebuffer(RenderTarget fb, String path)
	{
		RenderSystem.assertOnRenderThread();

		int width = fb.width;
		int height = fb.height;

		if (width <= 0 || height <= 0)
		{
			return "{\"error\":\"framebuffer has zero size " + width + "x" + height + "\"}";
		}

		long t0 = System.nanoTime();

		/* Bind THIS framebuffer as the read source. beginRead() binds the color texture; we need the FBO
		 * itself bound as GL_READ_FRAMEBUFFER so glReadPixels(0,0,...) reads its attachment, not the screen. */
		/* 26.2: RenderTarget.frameBufferId ist entfallen -- 26.2 gibt nur noch GpuTexture heraus,
		 * keine rohen GL-Kennungen (der Renderer soll auf Vulkan umziehen). glReadPixels braucht
		 * aber einen gebundenen Lese-Rahmenpuffer. Also haengen wir die Farbtextur des Ziels an
		 * einen eigenen, einmal angelegten Rahmenpuffer.
		 *
		 * ⛔ BEWUSST ROHES GL MIT ABLAUFDATUM -- unter Vulkan gibt es keine glId mehr; dann muss
		 * das Auslesen ueber den Befehlsgeber laufen. Siehe docs/26.2-UMSTIEG-SPICKZETTEL.md. */
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, leseFboFuer(fb));

		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

		/* Tight packing so rows are width*4 bytes with no padding — matters for non-4-aligned widths. */
		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

		/* Restore the default framebuffer binding so we do not leave the read-FBO bound. */
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);

		boolean allZero = true;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int i = (y * width + x) * 4;

				/* GL_BGRA byte order in memory: B, G, R, A. Pack into INT_ARGB (0xAARRGGBB). */
				int b = buffer.get(i) & 0xFF;
				int g = buffer.get(i + 1) & 0xFF;
				int r = buffer.get(i + 2) & 0xFF;
				int a = buffer.get(i + 3) & 0xFF;

				if (allZero && (r != 0 || g != 0 || b != 0 || a != 0))
				{
					allZero = false;
				}

				/* ⛔ ALPHA UNDURCHSICHTIG SCHREIBEN (2026-08-11, hat einen Befund erfunden).
				 *
				 * Hier stand `a << 24`, also landete das Rest-Alpha des Bildpuffers im PNG. RGB
				 * ist da laengst zusammengerechnet -- das Alpha ist Rauschen. Unser Puffer fuehrt
				 * zufaellig 255, der von 1.21.1 nicht; im A/B las sich das als 17,8 % Bildunter-
				 * schied und als "aktive Werkzeugtaste rot statt blau". Ohne Alpha: Werkzeugleiste
				 * 0 von 34160 Punkten verschieden. Beide Baeume schreiben jetzt opak wie Vanilla. */
				int argb = 0xFF000000 | (r << 16) | (g << 8) | b;

				/* Y-FLIP: GL row 0 is the BOTTOM of the image; BufferedImage row 0 is the TOP. */
				image.setRGB(x, (height - 1) - y, argb);
			}
		}

		try
		{
			File file = new File(path);

			if (file.getParentFile() != null)
			{
				file.getParentFile().mkdirs();
			}

			ImageIO.write(image, "png", file);
		}
		catch (Exception e)
		{
			return "{\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}";
		}

		long ms = (System.nanoTime() - t0) / 1_000_000L;

		String json = "{\"w\":" + width + ",\"h\":" + height + ",\"ms\":" + ms
			+ ",\"path\":\"" + escape(path) + "\"";

		if (allZero)
		{
			/* FAIL-LOUD: an all-zero buffer means we read nothing (wrong FBO bound, or GL not current) —
			 * NOT a legitimately black scene reported as success. */
			json += ",\"warning\":\"framebuffer all-zero\"";
		}

		json += "}";

		return json;
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

		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/* Eigener Lese-Rahmenpuffer, an den die Farbtextur des jeweiligen Ziels gehaengt wird.
	 * Einmal angelegt, VOR JEDEM LESEN neu behaengt. Siehe Kommentar oben. */
	private static int leseFbo = 0;

	/**
	 * ⛔ HIER WIRD DIE TEXTURNUMMER NICHT GEMERKT -- das war ein Fehler, kein Verzicht auf
	 * eine Optimierung.
	 *
	 * <p>Bis 2026-07-31 stand hier ein Zwischenspeicher (`leseFboTextur`), der das Behaengen
	 * uebersprang, solange die GL-Nummer der Farbtextur gleich blieb. Die Annahme dahinter --
	 * gleiche Nummer heisst gleiche Textur -- gilt in 26.2 nicht mehr: {@code RenderTarget.resize()}
	 * schliesst die alte GpuTexture, und GL vergibt die freigewordene Nummer sofort wieder.
	 * Nach einem Groessenwechsel zeigte der Anhang also auf ein totes Objekt, waehrend die
	 * Nummer unveraendert aussah.
	 *
	 * <p>⚠️ Das Fatale daran ist nicht der Absturz, sondern dass es keinen gibt: glReadPixels
	 * auf einen toten Anhang liefert lauter Nullen und keinen GL-Fehler. Das Werkzeug meldet
	 * dann ein schwarzes Bild -- und man sucht den Fehler im Produkt statt im Messgeraet.
	 * Genau das kostete in Aufgabe 91 eine Runde: derselbe Zwischenspeicher steckte in
	 * {@code BBSRendering.leseFboFuer} und liess einen bereits wirksamen Fix wirkungslos
	 * aussehen (dieselbe Textur: direkt gelesen Helligkeit 198, ueber den Puffer 0).
	 *
	 * <p>Das Behaengen kostet einen GL-Aufruf je Lesen. Ein Lesen kopiert ohnehin das ganze
	 * Bild in den Hauptspeicher -- daneben faellt das nicht ins Gewicht.
	 */
	private static int leseFboFuer(RenderTarget ziel)
	{
		int textur = ((com.mojang.blaze3d.opengl.GlTexture) ziel.getColorTexture()).glId();

		if (leseFbo == 0)
		{
			leseFbo = GL30.glGenFramebuffers();
		}

		int vorher = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, leseFbo);
		GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
			GL11.GL_TEXTURE_2D, textur, 0);
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);

		return leseFbo;
	}
}
