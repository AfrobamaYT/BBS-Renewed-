package com.mcstudio.nano;

/* Die Token bleiben in NanoDesign — hier nur die Zeichnung. */
import static com.mcstudio.nano.NanoDesign.*;

/**
 * Die Transport- und Zeitleisten-Symbole: Abspielen, Pause, Aufnahme, Sprungmarken,
 * Einrasten, Schleife, Zwiebelhaut.
 *
 * <p>Aus {@link NanoSymbole} herausgeschnitten — eine zusammengehoerige Gruppe, und
 * die Auswahl dort faellt fuer unbekannte Namen hierher durch.</p>
 */
public final class NanoSymboleTransport
{
	private NanoSymboleTransport() {}

	public static void symbol(long vg, String art, float cx, float cy, int farbe, float k)
	{
		switch (art)
		{
			case "play":
				NanoStifte.dreieck(vg, cx - 3.5F * k, cy - 5F * k, cx + 5F * k, cy, cx - 3.5F * k, cy + 5F * k, farbe);
				break;

			case "pause":
				NanoStifte.flaeche(vg, cx - 4F * k, cy - 5F * k, 2.6F * k, 10F * k, 0.8F * k, farbe);
				NanoStifte.flaeche(vg, cx + 1.4F * k, cy - 5F * k, 2.6F * k, 10F * k, 0.8F * k, farbe);
				break;

			case "rec":
				NanoStifte.kreis(vg, cx, cy, 4F * k, farbe);
				break;

			case "anfang":
				NanoStifte.linie(vg, cx - 4.5F * k, cy - 4.5F * k, cx - 4.5F * k, cy + 4.5F * k, 1.6F * k, farbe);
				NanoStifte.dreieck(vg, cx + 4.5F * k, cy - 4.5F * k, cx + 4.5F * k, cy + 4.5F * k, cx - 2.5F * k, cy, farbe);
				break;

			case "ende":
				NanoStifte.linie(vg, cx + 4.5F * k, cy - 4.5F * k, cx + 4.5F * k, cy + 4.5F * k, 1.6F * k, farbe);
				NanoStifte.dreieck(vg, cx - 4.5F * k, cy - 4.5F * k, cx - 4.5F * k, cy + 4.5F * k, cx + 2.5F * k, cy, farbe);
				break;

			case "schritt-zurueck":
				NanoStifte.dreieck(vg, cx + 3.5F * k, cy - 4.5F * k, cx + 3.5F * k, cy + 4.5F * k, cx - 3.5F * k, cy, farbe);
				break;

			case "schritt-vor":
				NanoStifte.dreieck(vg, cx - 3.5F * k, cy - 4.5F * k, cx - 3.5F * k, cy + 4.5F * k, cx + 3.5F * k, cy, farbe);
				break;

			case "magnet":
				NanoStifte.rahmen(vg, cx - 4.5F * k, cy - 5F * k, 9F * k, 7F * k, 4.5F * k, 1.5F * k, farbe);
				NanoStifte.linie(vg, cx - 4.5F * k, cy + 1F * k, cx - 4.5F * k, cy + 5F * k, 1.5F * k, farbe);
				NanoStifte.linie(vg, cx + 4.5F * k, cy + 1F * k, cx + 4.5F * k, cy + 5F * k, 1.5F * k, farbe);
				break;

			case "schleife":
				NanoStifte.rahmen(vg, cx - 5F * k, cy - 3.5F * k, 10F * k, 7F * k, 3.5F * k, 1.3F * k, farbe);
				NanoStifte.dreieck(vg, cx + 1F * k, cy - 5.5F * k, cx + 1F * k, cy - 1.5F * k, cx + 4F * k, cy - 3.5F * k, farbe);
				break;

			case "zwiebel":
				NanoStifte.rahmen(vg, cx - 5F * k, cy - 4F * k, 6F * k, 8F * k, 2F * k, 1.2F * k, farbe);
				NanoStifte.rahmen(vg, cx - 1F * k, cy - 4F * k, 6F * k, 8F * k, 2F * k, 1.2F * k, farbe);
				break;

			default:
				NanoStifte.kreis(vg, cx, cy, 2F * k, farbe);
				break;
		}
	}
}
