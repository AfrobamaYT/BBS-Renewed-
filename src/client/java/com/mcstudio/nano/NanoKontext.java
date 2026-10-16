package com.mcstudio.nano;

import org.lwjgl.nanovg.NanoVGGL3;

/**
 * NanoKontext — DER geteilte NanoVG-Kontext des Mods (ein GL-Kontext, ein Glyphen-Atlas,
 * alle Flaechen teilen ihn). Schriften kommen aus den MOD-RESSOURCEN (Noto Sans, Apache-2.0,
 * Lizenz liegt daneben) — kein Systempfad mehr (der Spike las /usr/share/fonts, das gibt es
 * auf Windows/macOS nicht).
 *
 * <p>⛔ GC-FALLE {@code nvgCreateFontMem}: NanoVG kopiert die TTF-Bytes NICHT — der
 * ByteBuffer muss so lange leben wie der Kontext. Deshalb halten die statischen Felder
 * {@link #schriftRegular}/{@link #schriftBold} die Puffer fuer immer; wer sie "aufraeumt",
 * bekommt zerhackte Glyphen ohne Fehlermeldung.</p>
 *
 * <p>Render-Thread only. Vor jedem Zeichnen gehoert eine {@link NanoGlKlammer} darum —
 * die zwei gemessenen 26.2-Fallen (PBO, Sampler) stecken dort.</p>
 */
final class NanoKontext
{
	private static long vg;
	private static NanoVkRenderer vulkan;
	private static boolean shutdownAngemeldet;
	private static java.nio.ByteBuffer schriftRegular;
	private static java.nio.ByteBuffer schriftBold;

	private NanoKontext()
	{
	}

	/** Kontext holen (lazy). 0 = Anlage fehlgeschlagen — Grund steht dann im Log. */
	/** Oeffentlich fuer Messrouten — der Handle selbst bleibt unveraendert. */
	public static long holen()
	{
		if (!shutdownAngemeldet)
		{
			shutdownAngemeldet = true;
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
				.register(client -> verwerfen());
		}

		if (vg != 0)
		{
			return vg;
		}

		long neu;

		if (NanoVkRenderer.active())
		{
			vulkan = new NanoVkRenderer();
			neu = vulkan.context();
		}
		else
		{
			neu = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
		}

		if (neu == 0)
		{
			System.out.println("[nano] nvgCreate lieferte 0 — kein NanoVG-kontext");

			return 0;
		}

		schriftRegular = ressource("/assets/bbs/fonts/nano/NotoSans-Regular.ttf");
		schriftBold = ressource("/assets/bbs/fonts/nano/NotoSans-Bold.ttf");

		if (schriftRegular == null || schriftBold == null)
		{
			loeschen(neu);

			return 0;
		}

		int regular = org.lwjgl.nanovg.NanoVG.nvgCreateFontMem(neu, "sans", schriftRegular, false);
		int fett = org.lwjgl.nanovg.NanoVG.nvgCreateFontMem(neu, "bold", schriftBold, false);

		if (regular == -1 || fett == -1)
		{
			System.out.println("[nano] font-anlage fehlgeschlagen (regular=" + regular + ", bold=" + fett + ")");
			loeschen(neu);

			return 0;
		}

		vg = neu;

		return vg;
	}

	static NanoVkRenderer vulkan()
	{
		return vulkan;
	}

	private static void loeschen(long kontext)
	{
		NanoBilder.verwerfen(kontext);

		if (vulkan != null)
		{
			vulkan.close();
			vulkan = null;
		}
		else
		{
			NanoVGGL3.nvgDelete(kontext);
		}
	}

	/** Verwerfen (frisch=1-Debugweg der Probe). Die Schrift-Puffer bleiben — naechstes holen() nutzt sie neu. */
	static void verwerfen()
	{
		if (vg != 0)
		{
			loeschen(vg);
			vg = 0;
		}
	}

	/** Classpath-Ressource als daurhaften Off-Heap-Puffer. null = fehlt (laut geloggt). */
	private static java.nio.ByteBuffer ressource(String pfad)
	{
		try (java.io.InputStream strom = NanoKontext.class.getResourceAsStream(pfad))
		{
			if (strom == null)
			{
				System.out.println("[nano] ressource fehlt: " + pfad);

				return null;
			}

			byte[] bytes = strom.readAllBytes();
			java.nio.ByteBuffer puffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);

			puffer.put(bytes).flip();

			return puffer;
		}
		catch (java.io.IOException e)
		{
			System.out.println("[nano] ressource unlesbar: " + pfad + " — " + e);

			return null;
		}
	}
}
