package com.mcstudio.nano;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * PNG-Symbole fuer NanoVG — geladen aus dem Classpath, gecacht je Kontext.
 *
 * <p>Die Vektorsymbole in {@link NanoDesign} bleiben fuer alles Kleine und Zustandsabhaengige.
 * Hier liegen die gezeichneten Assets der Werkzeugleiste, wo ein Glyph erkennbar sein muss:
 * „Go to camera" war als Pfeil-im-Kasten nicht als solcher lesbar.</p>
 */
public final class NanoBilder
{
	/* ⛔ Der Cache haengt am KONTEXT: NanoVG-Bild-Handles gelten nur in dem Kontext, in dem
	 * sie erzeugt wurden. Nach einem Kontextwechsel (Fensterwechsel, Neuaufbau) waere ein
	 * altes Handle stillschweigend ein FREMDES Bild — deshalb der Schluessel auf vg. */
	private static final Map<Long, Map<String, Integer>> CACHE = new HashMap<>();

	private NanoBilder()
	{
	}

	static void verwerfen(long vg)
	{
		CACHE.remove(vg);
	}

	/**
	 * Symbol mittig auf (cx, cy) in der Kantenlaenge {@code gr} zeichnen.
	 *
	 * @return false, wenn das Bild fehlt — der Aufrufer faellt dann auf das Vektorsymbol zurueck
	 */
	public static boolean symbol(long vg, String name, float cx, float cy, float gr, float alpha)
	{
		return symbol(vg, name, cx, cy, gr, alpha, true);
	}

	/**
	 * @param farbig false zeichnet die entsaettigte Variante ({@code name_off}), falls vorhanden.
	 *               So traegt die FARBE den Auswahlzustand, statt dass alle Reiter bunt leuchten.
	 */
	public static boolean symbol(long vg, String name, float cx, float cy, float gr, float alpha,
		boolean farbig)
	{
		int bild = farbig ? handle(vg, name) : handle(vg, name + "_off");

		/* Ohne graue Variante lieber farbig als gar nichts. */
		if (bild == 0 && !farbig)
		{
			bild = handle(vg, name);
		}

		if (bild == 0)
		{
			return false;
		}

		float x = cx - gr / 2F;
		float y = cy - gr / 2F;

		try (NVGPaint muster = NVGPaint.calloc())
		{
			NanoVG.nvgImagePattern(vg, x, y, gr, gr, 0F, bild, alpha, muster);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRect(vg, x, y, gr, gr);
			NanoVG.nvgFillPaint(vg, muster);
			NanoVG.nvgFill(vg);
		}

		return true;
	}

	/**
	 * A picture from the classpath (full path) filling (x, y, w, h) like CSS object-fit: cover —
	 * scaled to cover, cut centred — with rounded corners.
	 *
	 * @return false when the picture is missing
	 */
	public static boolean bild(long vg, String pfad, float x, float y, float w, float h, float radius)
	{
		Map<String, Integer> je = CACHE.computeIfAbsent(vg, k -> new HashMap<>());
		int bild = je.computeIfAbsent(pfad, (p) -> laden(vg, p));

		if (bild == 0)
		{
			return false;
		}

		try (MemoryStack s = MemoryStack.stackPush())
		{
			IntBuffer bb = s.mallocInt(1), bh = s.mallocInt(1);

			NanoVG.nvgImageSize(vg, bild, bb, bh);

			float skala = Math.max(w / Math.max(1, bb.get(0)), h / Math.max(1, bh.get(0)));
			float pb = bb.get(0) * skala, ph = bh.get(0) * skala;
			NVGPaint muster = NVGPaint.malloc(s);

			NanoVG.nvgImagePattern(vg, x + (w - pb) / 2F, y + (h - ph) / 2F, pb, ph, 0F, bild, 1F, muster);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, w, h, radius);
			NanoVG.nvgFillPaint(vg, muster);
			NanoVG.nvgFill(vg);
		}

		return true;
	}

	/** 0 = nicht ladbar. Ein Fehlversuch wird gemerkt, damit nicht jedes Bild neu sucht. */
	private static int handle(long vg, String name)
	{
		Map<String, Integer> je = CACHE.computeIfAbsent(vg, k -> new HashMap<>());
		Integer da = je.get(name);

		if (da != null)
		{
			return da;
		}

		int bild = laden(vg, "/assets/bbs/nano/icons/" + name + ".png");

		je.put(name, bild);

		return bild;
	}

	private static int laden(long vg, String pfad)
	{
		try (java.io.InputStream strom = NanoBilder.class.getResourceAsStream(pfad))
		{
			if (strom == null)
			{
				System.out.println("[nano] symbol fehlt: " + pfad);

				return 0;
			}

			byte[] bytes = strom.readAllBytes();
			ByteBuffer puffer = MemoryUtil.memAlloc(bytes.length);

			puffer.put(bytes).flip();

			/* nvgCreateImageMem dekodiert SOFORT und kopiert in eine GL-Textur — anders als
			 * nvgCreateFontMem muss der Puffer danach nicht am Leben bleiben. */
			int bild = NanoVG.nvgCreateImageMem(vg, NanoVG.NVG_IMAGE_GENERATE_MIPMAPS, puffer);

			MemoryUtil.memFree(puffer);

			if (bild == 0)
			{
				System.out.println("[nano] symbol nicht dekodierbar: " + pfad);
			}

			return bild;
		}
		catch (Exception e)
		{
			System.out.println("[nano] symbol-fehler " + pfad + ": " + e);

			return 0;
		}
	}
}
