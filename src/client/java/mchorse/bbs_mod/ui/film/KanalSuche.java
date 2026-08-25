package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import com.mcstudio.nano.NanoSymbole;

/**
 * Kanal-Suche im Zeitleisten-Kopf (Blenders Ctrl-F): filtert die Dope-Sheet-Zeilen
 * nach Namen. Eigene Klasse statt weiterer Zeilen im Zeitleisten-Monolithen; der
 * Zustand selbst (Suchtext) wohnt swap-fest in {@link ZeitleisteZweig}.
 */
final class KanalSuche
{
	/** Flaeche, deren Tastatur schon angeschlossen ist. Statisch: ueberlebt den
	 *  Editor-Neubau, und nach einem Hotswap (null) schliesst sie sich selbst nach. */
	private static NanoFlaeche angeschlossen;

	private KanalSuche()
	{
	}

	/** Zeichnet Feld + Zone zwischen {@code kx} und {@code rechts}; still bei
	 *  Kamera-Clip-Modus (keine Kanalliste) oder fehlendem Platz. */
	static void zeichnen(long vg, NanoFlaeche f, float kx, float rechts, float k, int modus)
	{
		float sw = Math.min(130F * k, rechts - 10F * k - kx);

		if (modus == 2 || sw < 60F * k)
		{
			return;
		}

		if (angeschlossen != f)
		{
			angeschlossen = f;
			f.beimTippen(KanalSuche::tippen);
		}

		String text = ZeitleisteZweig.kanalSuche();
		boolean aktiv = "kanalsuche".equals(f.fokus());
		float sy = NanoDesign.H_KOPF * k / 2F - 9F * k;

		NanoStifte.widget(vg, kx, sy, sw, 18F * k, 4F * k, NanoDesign.WIDGET, k);

		if (aktiv)
		{
			NanoStifte.rahmen(vg, kx, sy, sw, 18F * k, 4F * k, NanoDesign.BLAU);
		}

		NanoSymbole.symbol(vg, "suche", kx + 9F * k, NanoDesign.H_KOPF * k / 2F,
			aktiv || !text.isEmpty() ? NanoDesign.BLAU : NanoDesign.TXT_DIM, k);

		String angezeigt = text.isEmpty() && !aktiv ? "Search…" : text;

		NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, kx + 19F * k, NanoDesign.H_KOPF * k / 2F,
			text.isEmpty() ? NanoDesign.TXT_DIM : NanoDesign.TXT,
			NanoDesign.kuerzen(angezeigt, sw - 27F * k, 11F * k));

		if (aktiv)
		{
			float cx = kx + 19F * k + NanoDesign.breite(text, 11F * k) + 1F * k;

			NanoStifte.widget(vg, cx, NanoDesign.H_KOPF * k / 2F - 6F * k, 1.5F * k, 12F * k, 0F, NanoDesign.BLAU, k);
		}

		f.zone("kanalsuche", kx, sy, sw, 18F * k, () -> f.setzeFokus("kanalsuche"),
			"Filter channels by name (Blender: Ctrl-F)", "");
	}

	/** Zeichen anhaengen, Ruecktaste loescht; Filter sofort anwenden. */
	private static void tippen(String fokusId, char c)
	{
		if (!"kanalsuche".equals(fokusId))
		{
			return;
		}

		String s = ZeitleisteZweig.kanalSuche();

		ZeitleisteZweig.kanalSuche(c == '\b' ? (s.isEmpty() ? s : s.substring(0, s.length() - 1)) : s + c);
		ZeitleisteZweig.boneFilterAnwenden(mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null
			: mchorse.bbs_mod.BBSModClient.getDashboard().getPanel(UIFilmPanel.class));

		if (angeschlossen != null)
		{
			angeschlossen.neuMalen();
		}
	}
}
