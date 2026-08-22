package com.mcstudio.nano;

/* Die Token bleiben in NanoDesign — hier nur die Zeichnung. */
import static com.mcstudio.nano.NanoDesign.*;

/**
 * Die Vektor-Symbole der Design-Sprache, als NanoVG-Pfade gezeichnet.
 *
 * <p>Aus {@link NanoDesign} herausgeschnitten. {@code symbol(...)} waehlt ueber den
 * Namen; ein unbekannter Name zeichnet nichts, das ist Absicht.</p>
 */
public final class NanoSymbole
{
	private NanoSymbole() {}

	/* ===================== Symbole ===================== */

	public static void pfeilAb(long vg, float cx, float cy, int farbe, float k)
	{
		NanoStifte.linie(vg, cx - 3F * k, cy - 1.5F * k, cx, cy + 1.5F * k, 1.2F * k, farbe);
		NanoStifte.linie(vg, cx, cy + 1.5F * k, cx + 3F * k, cy - 1.5F * k, 1.2F * k, farbe);
	}

	public static void pfeilRechts(long vg, float cx, float cy, int farbe, float k)
	{
		NanoStifte.linie(vg, cx - 1.5F * k, cy - 3F * k, cx + 1.5F * k, cy, 1.2F * k, farbe);
		NanoStifte.linie(vg, cx + 1.5F * k, cy, cx - 1.5F * k, cy + 3F * k, 1.2F * k, farbe);
	}

	/** Symbolsatz im Stil des Design-Icon-Satzes. */
	public static void symbol(long vg, String art, float cx, float cy, int farbe, float k)
	{
		switch (art)
		{
			case "film":
				NanoStifte.rahmen(vg, cx - 7F * k, cy - 5F * k, 14F * k, 10F * k, 1.5F * k, farbe);
				NanoStifte.linie(vg, cx - 7F * k, cy - 1.5F * k, cx + 7F * k, cy - 1.5F * k, 1F * k, farbe);
				NanoStifte.linie(vg, cx - 3.5F * k, cy - 5F * k, cx - 4.5F * k, cy - 1.5F * k, 1F * k, farbe);
				NanoStifte.linie(vg, cx + 0.5F * k, cy - 5F * k, cx - 0.5F * k, cy - 1.5F * k, 1F * k, farbe);
				break;

			case "kamera":
				NanoStifte.rahmen(vg, cx - 7F * k, cy - 3.5F * k, 9F * k, 7F * k, 1.5F * k, farbe);
				NanoStifte.dreieck(vg, cx + 2.5F * k, cy, cx + 7F * k, cy - 3.5F * k, cx + 7F * k, cy + 3.5F * k, farbe);
				NanoStifte.kreis(vg, cx - 4.5F * k, cy - 5.5F * k, 1.6F * k, farbe);
				NanoStifte.kreis(vg, cx - 1F * k, cy - 5.5F * k, 1.6F * k, farbe);
				break;

			case "replay":
				NanoStifte.kreis(vg, cx, cy - 4.5F * k, 1.8F * k, farbe);
				NanoStifte.linie(vg, cx, cy - 2.5F * k, cx, cy + 1F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx - 3.2F * k, cy - 1F * k, cx + 3.2F * k, cy - 1F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx, cy + 1F * k, cx - 2.4F * k, cy + 5F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx, cy + 1F * k, cx + 2.4F * k, cy + 5F * k, 1.3F * k, farbe);
				break;

			case "outliner":
				NanoStifte.kreis(vg, cx - 4F * k, cy - 4F * k, 1.4F * k, farbe);
				NanoStifte.linie(vg, cx - 1.5F * k, cy - 4F * k, cx + 5F * k, cy - 4F * k, 1.2F * k, farbe);
				NanoStifte.kreis(vg, cx, cy, 1.4F * k, farbe);
				NanoStifte.linie(vg, cx + 2.5F * k, cy, cx + 5F * k, cy, 1.2F * k, farbe);
				NanoStifte.kreis(vg, cx, cy + 4F * k, 1.4F * k, farbe);
				NanoStifte.linie(vg, cx + 2.5F * k, cy + 4F * k, cx + 5F * k, cy + 4F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 4F * k, cy - 2.6F * k, cx - 4F * k, cy + 4F * k, 1.2F * k, farbe);
				break;

			case "props":
				NanoStifte.linie(vg, cx - 6F * k, cy - 4F * k, cx + 6F * k, cy - 4F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 6F * k, cy, cx + 6F * k, cy, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 6F * k, cy + 4F * k, cx + 6F * k, cy + 4F * k, 1.2F * k, farbe);
				NanoStifte.kreis(vg, cx + 2F * k, cy - 4F * k, 1.8F * k, farbe);
				NanoStifte.kreis(vg, cx - 2.5F * k, cy, 1.8F * k, farbe);
				NanoStifte.kreis(vg, cx + 3.5F * k, cy + 4F * k, 1.8F * k, farbe);
				break;

			case "dopesheet":
				for (int i = -1; i <= 1; i++)
				{
					float dx = cx + i * 5F * k;
					NanoStifte.dreieck(vg, dx, cy - 2.6F * k, dx + 2.6F * k, cy, dx, cy + 2.6F * k, farbe);
					NanoStifte.dreieck(vg, dx, cy - 2.6F * k, dx - 2.6F * k, cy, dx, cy + 2.6F * k, farbe);
				}
				break;

			case "suche":
				NanoStifte.rahmen(vg, cx - 4F * k, cy - 4F * k, 7F * k, 7F * k, 3.5F * k, farbe);
				NanoStifte.linie(vg, cx + 2.6F * k, cy + 2.6F * k, cx + 5F * k, cy + 5F * k, 1.2F * k, farbe);
				break;

			case "auge":
				NanoStifte.kreis(vg, cx, cy, 2.2F * k, farbe);
				NanoStifte.rahmen(vg, cx - 6F * k, cy - 3.4F * k, 12F * k, 6.8F * k, 3.4F * k, farbe);
				break;

			case "auge-aus":
				NanoStifte.rahmen(vg, cx - 6F * k, cy - 3.4F * k, 12F * k, 6.8F * k, 3.4F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy + 4F * k, cx + 5F * k, cy - 4F * k, 1.3F * k, farbe);
				break;

			case "schloss":
				NanoStifte.rahmen(vg, cx - 4F * k, cy - 1F * k, 8F * k, 6F * k, 1.4F * k, farbe);
				NanoStifte.rahmen(vg, cx - 2.3F * k, cy - 5F * k, 4.6F * k, 5F * k, 2.3F * k, farbe);
				break;

			case "knochen":
				NanoStifte.kreis(vg, cx - 4F * k, cy + 4F * k, 2F * k, farbe);
				NanoStifte.kreis(vg, cx + 4F * k, cy - 4F * k, 2F * k, farbe);
				NanoStifte.linie(vg, cx - 2.5F * k, cy + 2.5F * k, cx + 2.5F * k, cy - 2.5F * k, 1.3F * k, farbe);
				break;

			case "persp":
				NanoStifte.linie(vg, cx, cy - 6F * k, cx + 5.5F * k, cy - 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5.5F * k, cy - 3F * k, cx + 5.5F * k, cy + 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5.5F * k, cy + 3F * k, cx, cy + 6F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx, cy + 6F * k, cx - 5.5F * k, cy + 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5.5F * k, cy + 3F * k, cx - 5.5F * k, cy - 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5.5F * k, cy - 3F * k, cx, cy - 6F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx, cy, cx, cy + 6F * k, 1F * k, farbe);
				break;

			case "pfad":
				NanoStifte.linie(vg, cx - 6F * k, cy + 4F * k, cx - 1F * k, cy - 4F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx - 1F * k, cy - 4F * k, cx + 6F * k, cy + 2F * k, 1.3F * k, farbe);
				NanoStifte.kreis(vg, cx - 6F * k, cy + 4F * k, 1.5F * k, farbe);
				NanoStifte.kreis(vg, cx + 6F * k, cy + 2F * k, 1.5F * k, farbe);
				break;

			case "flug":
				NanoStifte.dreieck(vg, cx - 6F * k, cy + 2F * k, cx + 6F * k, cy - 4F * k, cx + 1F * k, cy + 5F * k, farbe);
				break;

			case "teleport":
				NanoStifte.linie(vg, cx, cy - 6F * k, cx, cy, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx - 2.5F * k, cy - 2.5F * k, cx, cy, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx + 2.5F * k, cy - 2.5F * k, cx, cy, 1.3F * k, farbe);
				NanoStifte.rahmen(vg, cx - 5F * k, cy + 1.5F * k, 10F * k, 5F * k, 2.5F * k, farbe);
				break;

			case "ton":
				NanoStifte.dreieck(vg, cx - 5F * k, cy, cx + 1F * k, cy - 5F * k, cx + 1F * k, cy + 5F * k, farbe);
				NanoStifte.rahmen(vg, cx + 2.5F * k, cy - 3F * k, 3F * k, 6F * k, 1.5F * k, farbe);
				break;

			case "wuerfel":
				NanoStifte.linie(vg, cx - 5F * k, cy - 2.5F * k, cx, cy - 5.5F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy - 2.5F * k, cx, cy - 5.5F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy - 2.5F * k, cx - 5F * k, cy + 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy - 2.5F * k, cx + 5F * k, cy + 3F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy + 3F * k, cx, cy + 6F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy + 3F * k, cx, cy + 6F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx, cy, cx, cy + 6F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy - 2.5F * k, cx, cy, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy - 2.5F * k, cx, cy, 1.2F * k, farbe);
				break;

			case "funken":
				NanoStifte.kreis(vg, cx - 3.5F * k, cy - 3F * k, 1.6F * k, farbe);
				NanoStifte.kreis(vg, cx + 3.5F * k, cy - 1.5F * k, 1.2F * k, farbe);
				NanoStifte.kreis(vg, cx - 1F * k, cy + 3.5F * k, 1.4F * k, farbe);
				NanoStifte.kreis(vg, cx + 2F * k, cy + 4F * k, 1F * k, farbe);
				NanoStifte.kreis(vg, cx, cy - 0.5F * k, 1F * k, farbe);
				break;

			case "bild":
				NanoStifte.rahmen(vg, cx - 6F * k, cy - 4.5F * k, 12F * k, 9F * k, 1.5F * k, 1.2F * k, farbe);
				NanoStifte.kreis(vg, cx - 2.5F * k, cy - 1.5F * k, 1.3F * k, farbe);
				NanoStifte.dreieck(vg, cx - 4F * k, cy + 4F * k, cx + 1F * k, cy + 4F * k, cx - 1.5F * k, cy + 0.5F * k, farbe);
				NanoStifte.dreieck(vg, cx - 1F * k, cy + 4F * k, cx + 5.5F * k, cy + 4F * k, cx + 2F * k, cy - 1F * k, farbe);
				break;

			case "morph":
				NanoStifte.rahmen(vg, cx - 6F * k, cy - 4F * k, 7F * k, 8F * k, 3.5F * k, 1.2F * k, farbe);
				NanoStifte.rahmen(vg, cx - 1F * k, cy - 4F * k, 7F * k, 8F * k, 3.5F * k, 1.2F * k, farbe);
				break;

			case "kurve":
				NanoStifte.linie(vg, cx - 5.5F * k, cy + 4.5F * k, cx - 5.5F * k, cy - 5F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 5.5F * k, cy + 4.5F * k, cx + 5.5F * k, cy + 4.5F * k, 1.2F * k, farbe);
				NanoStifte.linie(vg, cx - 4F * k, cy + 2.5F * k, cx - 1F * k, cy - 1F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx - 1F * k, cy - 1F * k, cx + 1.5F * k, cy + 1F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx + 1.5F * k, cy + 1F * k, cx + 4.5F * k, cy - 4F * k, 1.3F * k, farbe);
				break;

			case "menu":
				for (int i = -1; i <= 1; i++)
				{
					NanoStifte.linie(vg, cx - 5F * k, cy + i * 3.5F * k, cx + 5F * k, cy + i * 3.5F * k, 1.3F * k, farbe);
				}
				break;

			case "plus":
				NanoStifte.linie(vg, cx - 4.5F * k, cy, cx + 4.5F * k, cy, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx, cy - 4.5F * k, cx, cy + 4.5F * k, 1.3F * k, farbe);
				break;

			case "zahnrad":
				NanoStifte.rahmen(vg, cx - 4F * k, cy - 4F * k, 8F * k, 8F * k, 4F * k, 1.3F * k, farbe);
				NanoStifte.rahmen(vg, cx - 1.6F * k, cy - 1.6F * k, 3.2F * k, 3.2F * k, 1.6F * k, 1.3F * k, farbe);
				break;

			case "stift":
				NanoStifte.linie(vg, cx - 4F * k, cy + 4F * k, cx + 4F * k, cy - 4F * k, 1.6F * k, farbe);
				NanoStifte.linie(vg, cx - 4.5F * k, cy + 4.5F * k, cx - 2.5F * k, cy + 3.5F * k, 1.3F * k, farbe);
				break;

			case "voll":
				NanoStifte.linie(vg, cx - 5F * k, cy - 5F * k, cx - 5F * k, cy - 1.5F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy - 5F * k, cx - 1.5F * k, cy - 5F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy + 5F * k, cx + 5F * k, cy + 1.5F * k, 1.3F * k, farbe);
				NanoStifte.linie(vg, cx + 5F * k, cy + 5F * k, cx + 1.5F * k, cy + 5F * k, 1.3F * k, farbe);
				break;

			case "raster":
				NanoStifte.linie(vg, cx - 5F * k, cy - 1.7F * k, cx + 5F * k, cy - 1.7F * k, 1F * k, farbe);
				NanoStifte.linie(vg, cx - 5F * k, cy + 1.7F * k, cx + 5F * k, cy + 1.7F * k, 1F * k, farbe);
				NanoStifte.linie(vg, cx - 1.7F * k, cy - 5F * k, cx - 1.7F * k, cy + 5F * k, 1F * k, farbe);
				NanoStifte.linie(vg, cx + 1.7F * k, cy - 5F * k, cx + 1.7F * k, cy + 5F * k, 1F * k, farbe);
				break;

			default:
				/* Nicht hier? Dann in der Transport-Gruppe nachsehen. */
				NanoSymboleTransport.symbol(vg, art, cx, cy, farbe, k);
				break;
		}
	}
}
