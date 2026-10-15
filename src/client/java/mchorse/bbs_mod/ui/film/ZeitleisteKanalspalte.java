package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoStifte;
import com.mcstudio.nano.NanoSymbole;

/** Kanalspalte der Nano-Zeitleiste in den Clip-Modi: die Namen zu den Ebenen, die UIClips
 *  daneben zeichnet — reine Zier, nicht in UIClips (dort waere es eine zweite Layout-Logik). */
final class ZeitleisteKanalspalte
{
	static void zeichnen(long vg, int h, float k, int modus, float areaY, UIFilmPanel panel)
	{
		/* ⛔ Im REPLAY-ZWEIG nicht zeichnen. Dort fuehrt `UIKeyframeDopeSheet` seine eigenen
		 * Kanalnamen (transform, pose, …) mit eigenem Spaltengrund — diese Spalte lag
		 * darueber und stellte ihren Kopf „CHANNELS" (abgeschnitten „CH") ueber fremde
		 * Zeilen. Zwei Kanalspalten uebereinander, eine davon leer. */
		if (panel != null && panel.replayEditor != null && panel.replayEditor.isVisible())
		{
			return;
		}

		float oben = NanoDesign.H_KOPF * k;
		float breite = 105F * k;
		float linealH = 20F * k;

		NanoStifte.flaeche(vg, 0, oben, breite, h - oben, 0F, 0x191919);
		NanoStifte.linie(vg, breite, oben, breite, h, 1F * k, 0x0D0D0D);
		NanoStifte.text(vg, NanoDesign.SANS, 9.5F * k, 8F * k, oben + 13F * k,
			NanoDesign.TXT_OFF, "CHANNELS", null);

		if (panel == null || panel.getData() == null)
		{
			return;
		}

		/* ⛔ GEOMETRIE NICHT SELBST NACHRECHNEN — VON UIClips HOLEN. Diese Spalte zaehlte
		 * ihre Zeilen von OBEN nach unten mit fester Hoehe; UIClips legt die Spuren von UNTEN
		 * nach oben, zeichnet eine mehr und rechnet den Scroll ein. Gemessen bei k=2: Zeile 0
		 * der Spalte lag auf y=399, dort liegt aber Ebene 3 — die Namen standen exakt
		 * spiegelverkehrt zu den Clips. Nutzer: „die channel liste hat immernoch das
		 * verschiebe problem was umgekehrt ist." */
		boolean aktionen = modus == 3;
		mchorse.bbs_mod.film.replays.Replay replay = panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		if (aktionen && replay == null)
		{
			NanoStifte.text(vg, NanoDesign.SANS, 10F * k, 8F * k, oben + 40F * k,
				NanoDesign.TXT_OFF, "Select a replay", null);

			return;
		}

		UIClips spuren = (aktionen ? panel.actionEditor : panel.cameraEditor).clips;
		java.util.List<mchorse.bbs_mod.utils.clips.Clip> liste = aktionen ? replay.actions.get() : panel.getData().camera.get();
		int ebenen = spuren.layers;

		/* Wie viele Ebenen tragen ueberhaupt Clips? Danach richtet sich die Nummerierung. */
		int belegteEbenen = 0;

		for (int e = 0; e < ebenen; e++)
		{
			for (mchorse.bbs_mod.utils.clips.Clip c : liste)
			{
				if (c.layer.get() == e)
				{
					belegteEbenen++;

					break;
				}
			}
		}

		float zeileH = spuren.getLayerHeight() * k;
		int belegt = 0;

		for (int e = 0; e < ebenen; e++)
		{
			int n = 0;

			for (mchorse.bbs_mod.utils.clips.Clip c : liste)
			{
				if (c.layer.get() == e)
				{
					n++;
				}
			}

			/* ⛔ EINE LEERE EBENE IST KEINE KAMERA — sonst standen sechs „Camera N" in der
			 * Liste, fuenf davon leer („man sieht immer mehr kameras je hoeher der camera
			 * clip"). Die ZEILEN bleiben, ihre Zahl muss zu UIClips passen; beschriftet wird
			 * nur Belegtes, und die Nummer zaehlt belegte Ebenen, nicht den Ebenenindex. */
			if (n > 0) belegt++;

			/* Dieselbe y-Position, die UIClips fuer diese Ebene benutzt — mitsamt Scroll, in
			 * Flaechen-Koordinaten umgerechnet. Was ausserhalb liegt, wird nicht gezeichnet. */
			float y = (spuren.toLayerY(e) - areaY) * k;

			if (y < oben + linealH || y + zeileH > h) continue;

			if (n > 0)
			{
				String name = aktionen ? "Actions" : "Camera";
				int farbe = aktionen ? NanoDesign.BLAU : NanoDesign.GRUEN;

				NanoSymbole.symbol(vg, aktionen ? "funken" : "kamera", 12F * k, y + zeileH / 2F, farbe, k * 0.7F);
				NanoStifte.textInBox(vg, NanoDesign.SANS, 10F * k, 22F * k, y + zeileH / 2F,
					farbe, belegteEbenen == 1 ? name : name + " " + belegt);
				NanoStifte.textInBox(vg, NanoDesign.SANS, 9F * k, breite - 8F * k,
					y + zeileH / 2F, NanoDesign.TXT_OFF, n + "");
			}
			else
			{
				NanoStifte.textInBox(vg, NanoDesign.SANS, 10F * k, 22F * k, y + zeileH / 2F,
					NanoDesign.TXT_OFF, "—");
			}
		}
	}

	private ZeitleisteKanalspalte()
	{}
}
