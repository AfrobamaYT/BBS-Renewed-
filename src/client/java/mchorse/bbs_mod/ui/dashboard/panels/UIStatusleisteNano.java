package mchorse.bbs_mod.ui.dashboard.panels;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Statusleiste am unteren Rand (Designsystem: --h-status 22, --statusbar #191919).
 *
 * <p>Blender-Muster: die Statusleiste zeigt links die Maustasten-Belegung des Kontexts,
 * rechts Kennzahlen zur Datei. Hier: Maus/Tastatur-Hinweise, der offene Film mit Dauer,
 * die Zahl der Replays und die Version.</p>
 */
public class UIStatusleisteNano extends UIElement
{
	private final NanoFlaeche flaeche;
	private String stand = "";

	public UIStatusleisteNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		UIFilmPanel panel = filmPanel();
		String jetzt = panel == null || panel.getData() == null ? "-"
			: panel.getData().getId() + "|" + panel.getCursor() + "|" + lage()[0][1];

		if (!jetzt.equals(this.stand))
		{
			this.stand = jetzt;
			this.flaeche.neuMalen();
		}

		super.render(context);
	}

	/** Maus-Umriss mit hervorgehobener Taste — 8x11, wie die Symbole des Entwurfs. */
	private static void maussymbol(long vg, float cx, float cy, float k, String art)
	{
		float bw = 8F * k;
		float bh = 11F * k;

		NanoStifte.rahmen(vg, cx - bw / 2F, cy - bh / 2F, bw, bh, 3.5F * k, 1F * k, NanoDesign.TXT_OFF);
		NanoStifte.linie(vg, cx - bw / 2F, cy - 1F * k, cx + bw / 2F, cy - 1F * k, 1F * k, NanoDesign.TXT_OFF);

		float th = 4.5F * k;

		if ("maus-l".equals(art))
		{
			NanoStifte.flaeche(vg, cx - bw / 2F + 1F * k, cy - bh / 2F + 1F * k,
				bw / 2F - 1.5F * k, th, 1F * k, NanoDesign.TXT_DIM);
		}
		else if ("maus-r".equals(art))
		{
			NanoStifte.flaeche(vg, cx + 0.5F * k, cy - bh / 2F + 1F * k,
				bw / 2F - 1.5F * k, th, 1F * k, NanoDesign.TXT_DIM);
		}
		else
		{
			NanoStifte.flaeche(vg, cx - 1F * k, cy - bh / 2F + 1F * k, 2F * k, th, 1F * k, NanoDesign.TXT_DIM);
		}
	}

	/**
	 * Die Maustasten-Hinweise fuer die AKTUELLE Lage. Jede Zeile {Symbol, Text}; das letzte
	 * Paar darf ein Tastenkuerzel statt eines Maussymbols tragen ({@code taste:NAME}).
	 */
	private static String[][] lage()
	{
		UIFilmPanel panel = filmPanel();

		if (panel == null)
		{
			return new String[][] {{"maus-l", "Select"}, {"maus-r", "Menu"}};
		}

		if (panel.isFlying())
		{
			return new String[][] {
				{"maus-m", "Look"}, {"taste:W A S D", "Move"}, {"taste:Shift", "Faster"},
			};
		}

		if (panel.getController() != null && panel.getController().isControlling())
		{
			return new String[][] {
				{"maus-l", "Move actor"}, {"maus-r", "Menu"},
				{"taste:" + KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey()), "Leave control"},
			};
		}

		if (panel.isRunning())
		{
			return new String[][] {
				{"taste:Space", "Pause"}, {"maus-l", "Scrub"}, {"maus-m", "Orbit"},
			};
		}

		return new String[][] {{"maus-l", "Select"}, {"maus-r", "Menu"}, {"maus-m", "Orbit"}};
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());

		NanoStifte.flaeche(vg, 0, 0, b, h, 0F, NanoDesign.STATUSBAR);

		float y = h / 2F + 3.5F * k;
		float x = 12F * k;

		/* Links: was die Maus HIER GERADE tut. Blenders Statusleiste zeigt „the keymap of the
		 * ACTIVE TOOL" (manual/interface/window_system/status_bar.rst) — sie ist kontextsensitiv,
		 * keine feste Beschriftung. Wer im Flug ist, liest dort etwas anderes als im Editor. */
		String[][] hinweise = lage();

		for (String[] hw : hinweise)
		{
			if (hw[0].startsWith("taste:"))
			{
				String taste = hw[0].substring(6);
				float tb = NanoDesign.breite(taste, 9.5F * k) + 10F * k;

				NanoStifte.widget(vg, x, h / 2F - 6.5F * k, tb, 13F * k, 3F * k, 0x2C2C2C, k);
				NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, x + 5F * k, h / 2F,
					0xC6C6C6, taste);
				x += tb + 6F * k;
			}
			else
			{
				maussymbol(vg, x + 5F * k, h / 2F, k, hw[0]);
				x += 14F * k;
			}

			NanoStifte.text(vg, NanoDesign.SANS, 10.5F * k, x, y, NanoDesign.TXT_DIM, hw[1], null);
			x += NanoDesign.breite(hw[1], 10.5F * k) + 16F * k;
		}

		/* F3 als Chip — die einzige Taste, die der Entwurf hier zeigt */
		float f3B = NanoDesign.breite("F3", 9.5F * k) + 10F * k;

		NanoStifte.widget(vg, x, h / 2F - 6.5F * k, f3B, 13F * k, 3F * k, 0x2C2C2C, k);

		/* ⛔ `textMitte` statt geschaetzter Grundlinien-Korrektur. Vorher stand hier
		 * `h / 2F + 3F * k` — ein Naeherungswert fuer den Abstand Mitte→Grundlinie, der bei
		 * dieser Schriftgroesse zu gross war: „F3" sass sichtbar zu tief im Chip. `textMitte`
		 * setzt NVG_ALIGN_MIDDLE|CENTER und trifft die Mitte exakt, in beiden Achsen. */
		NanoStifte.textMitte(vg, NanoDesign.BOLD, 9.5F * k, x + f3B / 2F, h / 2F, 0xC6C6C6, "F3");
		x += f3B + 6F * k;
		NanoStifte.text(vg, NanoDesign.SANS, 10.5F * k, x, y, NanoDesign.TXT_DIM, "Command search", null);

		/* Rechts: Speicherstand und Dateipfad — was der Entwurf dort zeigt. */
		UIFilmPanel panel = filmPanel();
		String rechts = "BBS 26.2";

		if (panel != null && panel.getData() != null)
		{
			rechts = "films/" + panel.getData().getId() + ".bbs     BBS 26.2";
		}

		float rb = NanoDesign.breite(rechts, 10.5F * k);

		if (rb + 20F * k < b - x)
		{
			NanoStifte.text(vg, NanoDesign.SANS, 10.5F * k, b - rb - 12F * k, y,
				NanoDesign.TXT_OFF, rechts, null);
		}
	}
}
