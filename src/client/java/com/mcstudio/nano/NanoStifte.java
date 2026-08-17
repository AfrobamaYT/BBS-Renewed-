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
		try (MemoryStack s = MemoryStack.stackPush())
		{
			nvgBeginPath(vg);
			nvgRoundedRect(vg, x, y, w, h, radius);
			nvgStrokeColor(vg, farbe(rgb, 255, NVGColor.malloc(s)));
			nvgStrokeWidth(vg, 1F);
			nvgStroke(vg);
		}
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
