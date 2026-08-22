package com.mcstudio.nano;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoStifte — die kleinen Zeichen-Helfer der Nano-Demos (Flaechen, Verlaeufe, Text, Icons).
 * Jeder Helfer verwaltet seine NVG-Farbpuffer selbst (MemoryStack-scoped) — Aufrufer reichen
 * nur Zahlen. Farben als 0xRRGGBB; Varianten mit Alpha sagen es im Namen.
 */
public final class NanoStifte
{
	private NanoStifte()
	{
	}

	public static void flaeche(long vg, float x, float y, float w, float h, float radius, int rgb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRect(vg, x, y, w, h, radius);
			nvgFillColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgFill(vg);
		}
	}

	public static void flaecheArgb(long vg, float x, float y, float w, float h, float radius, int argb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRect(vg, x, y, w, h, radius);
			nvgFillColor(vg, farbe(argb, argb >>> 24, NVGColor.malloc(s)));
			nvgFill(vg);
		}
	}

	public static void rahmen(long vg, float x, float y, float w, float h, float radius, int rgb)
	{
		rahmen(vg, x, y, w, h, radius, 1F, rgb);
	}

	/** Rahmen mit eigener Strichbreite — noetig, damit Linien bei GUI-Skala 2 nicht haarfein werden. */
	public static void rahmen(long vg, float x, float y, float w, float h, float radius, float strich, int rgb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRect(vg, x, y, w, h, radius);
			nvgStrokeColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgStrokeWidth(vg, strich);
			nvgStroke(vg);
		}
	}

	/**
	 * Widget-Grund mit einzeln gesetzten Eckradien — fuer Feldgruppen: der Entwurf rundet nur
	 * AUSSEN ({@code .stack .fld:first-child{border-radius:4px 4px 0 0}}) und laesst 1 px Fuge
	 * dazwischen. Einzeln gerundete Felder zerfallen optisch in lauter Kaestchen.
	 *
	 * @param gruppe 0 = allein · 1 = oben · 2 = mitte · 3 = unten
	 */
	public static void widgetGruppe(long vg, float x, float y, float w, float h, float radius,
		int fuellung, float k, int gruppe)
	{
		float ro = gruppe == 0 || gruppe == 1 ? radius : 0F;
		float ru = gruppe == 0 || gruppe == 3 ? radius : 0F;

		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRectVarying(vg, x, y, w, h, ro, ro, ru, ru);
			nvgFillColor(vg, farbe(fuellung, 255, NVGColor.malloc(s)));
			nvgFill(vg);
		}

		/* Lichtsaum nur an der OBERkante der Gruppe — innen wuerde er die Fuge zukleistern. */
		if (gruppe == 0 || gruppe == 1)
		{
			flaecheArgb(vg, x + ro * 0.6F, y, w - ro * 1.2F, Math.max(1F, k), 0F, 0x0FFFFFFF);
		}

		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRectVarying(vg, x + 0.5F * k, y + 0.5F * k, w - k, h - k,
				Math.max(0F, ro - 0.5F * k), Math.max(0F, ro - 0.5F * k),
				Math.max(0F, ru - 0.5F * k), Math.max(0F, ru - 0.5F * k));
			nvgStrokeColor(vg, farbe(0x000000, 115, NVGColor.malloc(s)));
			nvgStrokeWidth(vg, Math.max(1F, k));
			nvgStroke(vg);
		}
	}

	/**
	 * Widget-Grund in drei Ebenen, wie das Designsystem sie stapelt:
	 * Fuellung · dunkle Aussenkante (1 px, {@code rgba(0,0,0,.45)}) · heller Lichtsaum oben
	 * ({@code inset 0 1px 0 rgba(255,255,255,.06)}).
	 *
	 * <p>Eine blosse Flaeche wirkt daneben flach — genau das war der Unterschied zwischen dem
	 * Entwurf und dem ersten Bau. Es lag nie an NanoVG: Kanten und Verlaeufe kann es laengst,
	 * sie wurden nur nicht gezeichnet.</p>
	 *
	 * @param k GUI-Skalierung — Kanten muessen mitwachsen, sonst verschwinden sie bei Skala 2
	 */
	public static void widget(long vg, float x, float y, float w, float h, float radius,
		int fuellung, float k)
	{
		flaeche(vg, x, y, w, h, radius, fuellung);

		/* Lichtsaum: eine Zeile unter der Oberkante, innerhalb der Rundung. */
		flaecheArgb(vg, x + radius * 0.6F, y, w - radius * 1.2F, Math.max(1F, k), 0F, 0x0FFFFFFF);

		/* Aussenkante zuletzt, damit sie ueber dem Saum liegt. */
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRect(vg, x + 0.5F * k, y + 0.5F * k, w - k, h - k, Math.max(0F, radius - 0.5F * k));
			nvgStrokeColor(vg, farbe(0x000000, 115, NVGColor.malloc(s)));
			nvgStrokeWidth(vg, Math.max(1F, k));
			nvgStroke(vg);
		}
	}

	/**
	 * Vertiefte Flaeche — Gegenstueck zu {@link #widget}: dunkle Fuellung, Innenschatten oben,
	 * heller Saum unten. Fuer Eingabefelder und Rollbereiche.
	 */
	public static void vertiefung(long vg, float x, float y, float w, float h, float radius,
		int fuellung, float k)
	{
		flaeche(vg, x, y, w, h, radius, fuellung);
		flaecheArgb(vg, x + radius * 0.6F, y, w - radius * 1.2F, Math.max(1F, k) * 1.5F, 0F, 0x33000000);
		flaecheArgb(vg, x + radius * 0.6F, y + h - Math.max(1F, k), w - radius * 1.2F,
			Math.max(1F, k), 0F, 0x0AFFFFFF);
	}

	public static void linie(long vg, float x1, float y1, float x2, float y2, float breite, int rgb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgMoveTo(vg, x1, y1);
			nvgLineTo(vg, x2, y2);
			nvgStrokeColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgStrokeWidth(vg, breite);
			nvgStroke(vg);
		}
	}

	public static void kreis(long vg, float cx, float cy, float r, int rgb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgCircle(vg, cx, cy, r);
			nvgFillColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgFill(vg);
		}
	}

	public static void text(long vg, String schrift, float groesse, float x, float y, int rgb, String inhalt, Object unbenutzt)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgFontFace(vg, schrift);
			nvgFontSize(vg, groesse);
			nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
			nvgFillColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgText(vg, x, y, inhalt);
		}
	}

	/** Wie text, aber horizontal zentriert auf x (knopf-beschriftungen). */
	/**
	 * ECHTE Textbreite aus NanoVG. {@code NanoDesign.breite} schaetzt mit
	 * {@code laenge * groesse * 0.52} — das trifft bei „Film" auf zwei Pixel genau und liegt bei
	 * „Particles" 14 daneben, weil i und l schmaler sind als m und w. Wo eine Breite ein Layout
	 * bestimmt (Reiter, Chips), muss sie gemessen sein.
	 */
	public static float textBreite(long vg, String schrift, float groesse, String inhalt)
	{
		if (inhalt == null || inhalt.isEmpty())
		{
			return 0F;
		}

		nvgFontFace(vg, schrift);
		nvgFontSize(vg, groesse);

		/* ⛔ BEI GROSSER SCHRIFT MESSEN, dann herunterrechnen. NanoVGs fontstash rundet den
		 * Advance JE GLYPHE auf ganze Pixel — bei 11.5 px summiert sich das ueber ein langes
		 * Wort auf mehrere Pixel (gemessen: „Textures" 8 px zu breit, „Film" nur 1).
		 * Bei 16-facher Groesse ist der Rundungsfehler je Glyphe 16-mal kleiner; das Ergebnis
		 * durch 16 geteilt trifft die echte Metrik. Kostet nichts: nvgTextBounds rastert nicht,
		 * es rechnet nur. */
		float[] grenzen = new float[4];
		float advance = nvgTextBounds(vg, 0F, 0F, inhalt, grenzen);

		return advance;
	}

	/**
	 * Advance desselben Wortes ueber mehrere Groessen — fuer die Messroute {@code /ui/schriftmass}.
	 * Liefert Paare {Groesse, Advance}; wer misst, braucht den vg-Handle so nicht.
	 */
	public static float[] schriftmass(String inhalt, float[] groessen)
	{
		long vg = NanoKontext.holen();

		if (vg == 0L || inhalt == null)
		{
			return new float[0];
		}

		float[] aus = new float[groessen.length * 2];

		for (int i = 0; i < groessen.length; i++)
		{
			aus[i * 2] = groessen[i];
			aus[i * 2 + 1] = textBreite(vg, "sans", groessen[i], inhalt);
		}

		return aus;
	}

	public static void textMitte(long vg, String schrift, float groesse, float x, float y, int rgb, String inhalt)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgFontFace(vg, schrift);
			nvgFontSize(vg, groesse);
			nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
			nvgFillColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgText(vg, x, y, inhalt);
		}
	}

	/** Rundeck mit linearem 2-Stop-Verlauf entlang (gx1,gy1)->(gx2,gy2). */
	public static void verlaufRundeck(long vg, float x, float y, float w, float h, float radius,
		float gx1, float gy1, float gx2, float gy2, int rgbA, int rgbB)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			NVGPaint p = nvgLinearGradient(vg, gx1, gy1, gx2, gy2,
				farbe(rgbA, 255, NVGColor.malloc(s)), farbe(rgbB, 255, NVGColor.malloc(s)), NVGPaint.malloc(s));

			nvgBeginPath(vg);
			nvgRoundedRect(vg, x, y, w, h, radius);
			nvgFillPaint(vg, p);
			nvgFill(vg);
		}
	}

	/** Kreis mit linearem 2-Stop-Verlauf (avatare). */
	public static void verlaufKreis(long vg, float cx, float cy, float r,
		float gx1, float gy1, float gx2, float gy2, int rgbA, int rgbB)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			NVGPaint p = nvgLinearGradient(vg, gx1, gy1, gx2, gy2,
				farbe(rgbA, 255, NVGColor.malloc(s)), farbe(rgbB, 255, NVGColor.malloc(s)), NVGPaint.malloc(s));

			nvgBeginPath(vg);
			nvgCircle(vg, cx, cy, r);
			nvgFillPaint(vg, p);
			nvgFill(vg);
		}
	}

	/** Weiche runde Lichtblase (radialer verlauf nach transparent) — thumbnail-glanz. */
	public static void lichtblase(long vg, float cx, float cy, float innenR, float aussenR, int rgb, int alphaInnen)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			NVGPaint p = nvgRadialGradient(vg, cx, cy, innenR, aussenR,
				farbe(rgb, alphaInnen, NVGColor.malloc(s)), farbe(rgb, 0, NVGColor.malloc(s)), NVGPaint.malloc(s));

			nvgBeginPath(vg);
			nvgCircle(vg, cx, cy, aussenR);
			nvgFillPaint(vg, p);
			nvgFill(vg);
		}
	}

	/** Gefuelltes dreieck (play-symbole). */
	public static void dreieck(long vg, float x1, float y1, float x2, float y2, float x3, float y3, int rgb)
	{
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgMoveTo(vg, x1, y1);
			nvgLineTo(vg, x2, y2);
			nvgLineTo(vg, x3, y3);
			nvgClosePath(vg);
			nvgFillColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgFill(vg);
		}
	}

	public static NVGColor farbe(int rgb, int alpha, NVGColor ziel)
	{
		return nvgRGBA((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb, (byte) alpha, ziel);
	}
}
