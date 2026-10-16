package com.mcstudio.nano;

/* Die Token bleiben in NanoDesign — hier nur die Zeichnung. */
import static com.mcstudio.nano.NanoDesign.*;

/**
 * Zusammengesetzte Bausteine der Design-Sprache: Flaechenkopf, Suchfeld, Feldzeile,
 * Panelkopf, Knopf, Tooltip und die Editor-Klappe.
 *
 * <p>Aus {@link NanoDesign} herausgeschnitten — dort blieben die Token und das Messen.
 * Alle Masse sind GUI-Pixel und werden mit {@code k} multipliziert.</p>
 */
public final class NanoBausteine
{
	private NanoBausteine() {}

	public static float kopfzeile(long vg, float b, float k, String symbol)
	{
		NanoStifte.flaeche(vg, 0, 0, b, H_KOPF * k, R_AREA * k, KOPF);
		NanoStifte.flaeche(vg, 0, (H_KOPF - R_AREA) * k, b, R_AREA * k, 0F, KOPF);

		/* Entwurfsmass: .etyp ist 37x22 an x=8 — meine 30x20 an x=6 liefen im Pixelvergleich
		 * gegen jede Flaeche um zwei Pixel versetzt. */
		float ex = 8F * k;
		float ey = (H_KOPF - 22F) * k / 2F;

		NanoStifte.widget(vg, ex, ey, 37F * k, 22F * k, R_WIDGET * k, WIDGET, k);
		NanoSymbole.symbol(vg, symbol, ex + 11F * k, ey + 11F * k, TXT, k);
		NanoSymbole.pfeilAb(vg, ex + 28F * k, ey + 11F * k, TXT_DIM, k);

		return ex + 43F * k;
	}

	/**
	 * Wertfeld: Etikett links, Wert rechtsbuendig, optionaler Achs-Punkt.
	 * {@code fuellung} 0..1 zeichnet den Regler-Anteil; negativ = kein Regler.
	 */
	public static void feld(long vg, float x, float y, float b, float k,
		String etikett, String wert, int achsPunkt, float fuellung)
	{
		feld(vg, x, y, b, k, etikett, wert, achsPunkt, fuellung, 0);
	}

	/**
	 * Wie oben, aber mit Lage in der Feldgruppe: 0 = allein · 1 = oben · 2 = mitte · 3 = unten.
	 * Der Entwurf stapelt zusammengehoerige Werte (Location X/Y/Z) zu EINEM Block.
	 */
	public static void feld(long vg, float x, float y, float b, float k,
		String etikett, String wert, int achsPunkt, float fuellung, int gruppe)
	{
		feld(vg, x, y, b, k, etikett, wert, achsPunkt, fuellung, gruppe, FELD);
	}

	/** Wie oben, mit eigenem Feldgrund (G7: FELD_KEY / FELD_ANIMIERT). */
	public static void feld(long vg, float x, float y, float b, float k,
		String etikett, String wert, int achsPunkt, float fuellung, int gruppe, int grund)
	{
		NanoStifte.widgetGruppe(vg, x, y, b, H_WIDGET * k, R_WIDGET * k, grund, k, gruppe);

		if (fuellung >= 0F)
		{
			float fb = Math.max(0F, Math.min(1F, fuellung)) * b;
			NanoStifte.flaecheArgb(vg, x, y, fb, H_WIDGET * k, R_WIDGET * k, 0x8C4772B3);
		}

		float tx = x + 8F * k;

		if (achsPunkt != 0)
		{
			NanoStifte.kreis(vg, tx + 3F * k, y + H_WIDGET * k / 2F, 3.5F * k, achsPunkt);
			tx += 12F * k;
		}

		/* Wert rechts, Etikett links — aber nur, wenn beide nebeneinander passen.
		   Ist das Feld zu schmal, gewinnt der WERT (die Zahl ist die Information). */
		float rechts = x + b - 8F * k;
		float wertB = 0F;
		String w = wert;
		boolean beide = true;

		if (w != null && !w.isEmpty())
		{
			w = kuerzen(w, b * 0.6F, 11F * k);
			wertB = breite(w, 11F * k);
			beide = rechts - wertB - 8F * k - tx > 16F * k;

			/* ⛔ KEINE GRUNDLINIEN-KORREKTUR. NanoStifte.text richtet auf
			 * NVG_ALIGN_LEFT|NVG_ALIGN_MIDDLE aus — sein y IST die Mitte. Das frühere
			 * "+ 3.5F * k" schob jede Beschriftung um genau diesen Wert nach unten;
			 * gemessen 5,0 px Abweichung in einer 40-px-Box bei Skala 2. */
			/* ⛔ OHNE ETIKETT GEHOERT DER WERT IN DIE MITTE (Nutzerbefund 2026-08-24: der
			 * Replay-Name stand rechts). Rechtsbuendig ist nur sinnvoll, wenn LINKS ein
			 * Etikett steht, gegen das er sich absetzt — steht da nichts, sieht es aus,
			 * als waere der Text an den Rand gerutscht. */
			boolean ohneEtikett = etikett == null || etikett.isEmpty();

			if (ohneEtikett)
			{
				NanoStifte.textMitteInBox(vg, SANS, 11F * k, x + b / 2F,
					y + H_WIDGET * k / 2F, TXT_MAX, w);
			}
			else
			{
				NanoStifte.textInBox(vg, SANS, 11F * k,
					beide ? rechts - wertB : tx,
					y + H_WIDGET * k / 2F, TXT_MAX, w);
			}
		}

		if (beide)
		{
			float platzEtikett = rechts - wertB - 8F * k - tx;

			NanoStifte.textInBox(vg, SANS, 11F * k, tx, y + H_WIDGET * k / 2F, 0xC9C9C9,
				kuerzen(etikett, platzEtikett, 11F * k));
		}
	}

	/** Panel-Kopf mit Klapp-Pfeil; gibt die Hoehe zurueck. */
	public static float panelKopf(long vg, float x, float y, float b, float k,
		String titel, boolean offen, String kennzahl)
	{
		float h = 23F * k;

		/* Eigene Stufe der Tiefenleiter: PANELKOPF #353535 liegt EINE Stufe ueber PANEL
		 * #2F2F2F. Vorher lagen Kopf und Karte auf derselben Farbe und der Kopf verschwand. */
		NanoStifte.widgetGruppe(vg, x, y, b, h, R_PANEL * k, PANELKOPF, k, offen ? 1 : 0);

		if (offen)
		{
			NanoSymbole.pfeilAb(vg, x + 12F * k, y + h / 2F, TXT_DIM, k);
		}
		else
		{
			NanoSymbole.pfeilRechts(vg, x + 12F * k, y + h / 2F, TXT_DIM, k);
		}

		NanoStifte.textInBox(vg, BOLD, 11.5F * k, x + 22F * k, y + h / 2F, TXT, titel);

		if (kennzahl != null && !kennzahl.isEmpty())
		{
			float kb = kennzahl.length() * 5.2F * k;
			NanoStifte.textInBox(vg, SANS, 10F * k, x + b - 9F * k - kb, y + h / 2F,
				TXT_OFF, kennzahl);
		}

		return h;
	}

	/**
	 * Optional action line under a tooltip, by zone id: {text, key} — Help's "Watch the 30-second
	 * clip" · "Shift+F1" at a feature that has a short clip (spec section 6). Null = none.
	 */
	public static java.util.function.Function<String, String[]> tooltipZusatz = (id) -> null;

	/** What a click on that action line does, by zone id. */
	public static java.util.function.Consumer<String> tooltipAktion = (id) -> {};

	/** The tooltip of {@code f}, its action line a click zone of {@code f}. */
	public static void tooltip(long vg, int flaecheB, int flaecheH, float k, NanoFlaeche f)
	{
		Object[] faellig = f.tooltipFaellig();
		float[] zeile = tooltip(vg, flaecheB, flaecheH, k, faellig);

		if (zeile != null)
		{
			f.zone("tooltip-aktion", zeile[0], zeile[1], zeile[2], zeile[3], () -> tooltipAktion.accept((String) faellig[4]));
		}
	}

	/**
	 * Tooltip-Box nach Blenders Muster: kurze Beschreibung, darunter das Kuerzel in gedaempfter
	 * Farbe. Wird ZULETZT gezeichnet, damit sie ueber allem liegt, und kippt an den Raendern
	 * nach innen statt aus der Flaeche zu laufen.
	 *
	 * @param faellig {x, y, Beschreibung, Taste} aus {@code NanoFlaeche.tooltipFaellig()}
	 */
	public static float[] tooltip(long vg, int flaecheB, int flaecheH, float k, Object[] faellig)
	{
		if (faellig == null)
		{
			return null;
		}

		String text = (String) faellig[2];
		String taste = (String) faellig[3];
		boolean zweizeilig = taste != null && !taste.isEmpty();

		float tb = breite(text, 11F * k);
		float kb = zweizeilig ? breite(taste, 10F * k) : 0F;
		String[] zusatz = faellig.length > 4 ? tooltipZusatz.apply((String) faellig[4]) : null;
		float zb = zusatz == null ? 0F : 27F * k + breite(zusatz[0], 10.5F * k) + 16F * k + breite(zusatz[1], 10F * k) + 9F * k;
		float b = Math.max(Math.max(tb, kb) + 18F * k, zb);
		float h = (zweizeilig ? 34F : 21F) * k + (zusatz == null ? 0F : 26F * k);
		float x = (Float) faellig[0];
		float y = (Float) faellig[1] + 4F * k;

		/* An den Raendern nach innen kippen, sonst steht der Text halb draussen. */
		x = Math.min(x, flaecheB - b - 4F * k);
		x = Math.max(4F * k, x);

		if (y + h > flaecheH)
		{
			y = Math.max(4F * k, y - h - 8F * k);
		}

		NanoStifte.flaecheArgb(vg, x + 1F * k, y + 1F * k, b, h, R_WIDGET * k, 0x66000000);
		NanoStifte.widget(vg, x, y, b, h, R_WIDGET * k, 0x1E1E1E, k);
		NanoStifte.text(vg, SANS, 11F * k, x + 9F * k, y + 14F * k, TXT, text, null);

		if (zweizeilig)
		{
			NanoStifte.text(vg, SANS, 10F * k, x + 9F * k, y + 28F * k, TXT_OFF, taste, null);
		}

		if (zusatz != null)
		{
			/* Joint, then screen-with-play · text · key on the right */
			float zy = y + h - 26F * k;

			NanoStifte.flaeche(vg, x + 1F * k, zy, b - 2F * k, Math.max(1F, k), 0F, 0x2E2E2E);
			NanoStifte.rahmen(vg, x + 9F * k, zy + 8.5F * k, 12F * k, 9F * k, 1.5F * k, Math.max(1F, k), BLAU_HELL);
			NanoStifte.dreieck(vg, x + 13.5F * k, zy + 10.5F * k, x + 13.5F * k, zy + 15.5F * k, x + 17.5F * k, zy + 13F * k, BLAU_HELL);
			NanoStifte.text(vg, SANS, 10.5F * k, x + 27F * k, zy + 17F * k, TXT, zusatz[0], null);
			NanoStifte.text(vg, SANS, 10F * k, x + b - 9F * k - breite(zusatz[1], 10F * k), zy + 17F * k, TXT_OFF, zusatz[1], null);

			return new float[] {x, zy, b, 26F * k};
		}

		return null;
	}

	/**
	 * Aufgeklappte Editor-Liste unter dem Typ-Waehler. Zeichnet die Auswahl und meldet
	 * die Zonen; die Flaeche entscheidet selbst, was ein Wechsel bedeutet.
	 *
	 * @param eintraege je Zeile {Symbolname, Anzeigetext}
	 * @param aktiv     Index des laufenden Editors
	 * @return Hoehe der Klappe
	 */
	public static float editorKlappe(long vg, NanoFlaeche f, float x, float y, float k,
		String[][] eintraege, int aktiv, java.util.function.IntConsumer wahl)
	{
		float breite = 150F * k;
		float zeile  = 22F * k;
		float hoehe  = eintraege.length * zeile + 8F * k;

		/* Schatten liegt NUR unter Overlays — hier ist er richtig. */
		NanoStifte.flaecheArgb(vg, x + 2F * k, y + 3F * k, breite, hoehe, R_PANEL * k, 0x66000000);
		NanoStifte.flaeche(vg, x, y, breite, hoehe, R_PANEL * k, MENUE);
		NanoStifte.rahmen(vg, x, y, breite, hoehe, R_PANEL * k, 0x101010);

		for (int i = 0; i < eintraege.length; i++)
		{
			float zy = y + 4F * k + i * zeile;
			boolean an = i == aktiv;

			if (an)
			{
				NanoStifte.widget(vg, x + 3F * k, zy, breite - 6F * k, zeile, R_WIDGET * k, BLAU, k);
			}

			NanoSymbole.symbol(vg, eintraege[i][0], x + 16F * k, zy + zeile / 2F,
				an ? 0xFFFFFF : TXT_DIM, k);
			NanoStifte.textInBox(vg, SANS, 11.5F * k, x + 28F * k, zy + zeile / 2F,
				an ? 0xFFFFFF : TXT, eintraege[i][1]);

			final int idx = i;

			f.zone("editorwahl" + i, x, zy, breite, zeile, () -> wahl.accept(idx));
		}

		return hoehe;
	}
}
