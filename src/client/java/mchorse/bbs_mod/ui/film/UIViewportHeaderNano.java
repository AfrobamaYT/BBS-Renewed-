package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoSymbole;
import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;

/**
 * Viewport-Header im neuen Designsystem — die Werkzeugreihe sitzt OBEN als Header-Leiste,
 * nicht mehr unten mittig ueber dem Bild (Blender-Muster: jede Flaeche hat ihren Header oben).
 *
 * <p>Er zeichnet nur; ausgeloest werden die BESTEHENDEN {@link UIIcon}-Aktionen per
 * {@code clickItself()} — damit bleibt die ganze Logik (Kontextmenues, Zustaende,
 * Tastenkuerzel) unveraendert und es gibt keine zweite Wahrheit.</p>
 */
public class UIViewportHeaderNano extends UIElement
{
	private final UIFilmPreview preview;
	private final NanoFlaeche flaeche;
	private boolean letzterLauf;

	public UIViewportHeaderNano(UIFilmPreview preview)
	{
		this.preview = preview;
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		boolean laeuft = laeuft();

		if (laeuft != this.letzterLauf)
		{
			this.letzterLauf = laeuft;
			this.flaeche.neuMalen();
		}

		super.render(context);
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());

		NanoStifte.flaeche(vg, 0, 0, b, h, 0F, NanoDesign.KOPF);

		/* Editor-Typ-Waehler ganz links — die Signatur jeder Flaeche */
		float x = 6F * k;
		NanoStifte.widget(vg, x, (h - 22F * k) / 2F, 37F * k, 22F * k, 4F * k, NanoDesign.WIDGET, k);
		NanoSymbole.symbol(vg, "persp", x + 11F * k, h / 2F, NanoDesign.TXT, k);
		NanoSymbole.pfeilAb(vg, x + 28F * k, h / 2F, NanoDesign.TXT_DIM, k);
		x += 43F * k;

		/* Modus-Waehler „Replay Edit" wie im Entwurf: benennt, worauf der Viewport gerade
		 * wirkt. Er zeigt den Zustand des Kamera-Editors, den es in BBS wirklich gibt. */
		boolean kamera = mchorse.bbs_mod.BBSModClient.getDashboard() != null
			&& mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel pk
			&& pk.cameraEditor != null && pk.cameraEditor.isVisible();
		String modus = kamera ? "Camera Edit" : "Replay Edit";
		float mw = NanoDesign.breite(modus, 11F * k) + 26F * k;

		NanoStifte.widget(vg, x, (h - 22F * k) / 2F, mw, 22F * k, 4F * k, NanoDesign.WIDGET, k);
		NanoStifte.text(vg, NanoDesign.SANS, 11F * k, x + 9F * k, h / 2F + 3.8F * k,
			NanoDesign.TXT, modus, null);
		NanoSymbole.pfeilAb(vg, x + mw - 9F * k, h / 2F, NanoDesign.TXT_DIM, k * 0.8F);
		x += mw + 8F * k;

		trenner(vg, x, h, k);
		x += 8F * k;

		/* Menuezeile wie im Entwurf — beschriftet, was diese Flaeche kann. */
		for (String menue : new String[] {"View", "Replay", "Camera"})
		{
			float mb = NanoDesign.breite(menue, 11F * k) + 14F * k;
			boolean ueber = mausX >= x && mausX < x + mb && mausY >= 0 && mausY < h;

			if (ueber)
			{
				NanoStifte.widget(vg, x, 3F * k, mb, h - 6F * k, 4F * k, NanoDesign.WIDGET, k);
			}

			NanoStifte.text(vg, NanoDesign.SANS, 11F * k, x + 7F * k, h / 2F + 3.8F * k,
				ueber ? NanoDesign.TXT : NanoDesign.TXT_DIM, menue, null);
			x += mb;
		}

		/* Rechts: Ansichtsschalter und Aufnahme. Die Arbeitswerkzeuge sitzen jetzt in der
		 * schwebenden Leiste am Bild, die Wiedergabe im Kopf der Zeitleiste. */
		float rx = b - 6F * k - 22F * k;

		/* Vollbild fuer die Vorschau — dieselbe Geste wie im Kopf der Zeitleiste. */
		UIFilmPanel fp = mchorse.bbs_mod.BBSModClient.getDashboard() != null
			&& mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p2 ? p2 : null;
		boolean max = fp != null && fp.istMaximiert();

		if (max)
		{
			NanoStifte.widget(vg, rx, (h - 20F * k) / 2F, 22F * k, 20F * k, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("voll"))
		{
			NanoStifte.widget(vg, rx, (h - 20F * k) / 2F, 22F * k, 20F * k, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, "voll", rx + 11F * k, h / 2F, max ? 0xFFFFFF : NanoDesign.TXT_DIM, k * 0.85F);

		if (fp != null)
		{
			f.zone("voll", rx, (h - 20F * k) / 2F, 22F * k, 20F * k, () -> fp.flaecheMaximieren("preview"));
		}

		rx -= 22F * k;

		rx = aufnahmeRueck(vg, f, rx, h, k, "rec", "Replay aufnehmen", this.preview.recordReplay);

		trenner(vg, rx + 11F * k, h, k);
		rx -= 8F * k;

		/* Ansichtsschalter bleiben im Kopf; die Arbeitswerkzeuge sitzen in der Leiste am Bild. */
		rx = knopfRueck(vg, f, rx, h, k, "raster", "Raster", null);
		knopfRueck(vg, f, rx, h, k, "persp", "Perspektive", this.preview.perspective);
	
		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}

	/** Wie {@link #knopf}, aber von rechts nach links gesetzt. */
	private float knopfRueck(long vg, NanoFlaeche f, float x, float h, float k,
		String symbol, String titel, UIIcon ziel)
	{
		knopf(vg, f, x, h, k, symbol, titel, ziel, false);

		return x - 22F * k;
	}

	private float aufnahmeRueck(long vg, NanoFlaeche f, float x, float h, float k,
		String symbol, String titel, UIIcon ziel)
	{
		aufnahme(vg, f, x, h, k, symbol, titel, ziel);

		return x - 22F * k;
	}

	/** Laeuft die Wiedergabe? Ueber das Dashboard, weil {@code UIFilmPreview.panel} privat ist. */
	private boolean laeuft()
	{
		return mchorse.bbs_mod.BBSModClient.getDashboard() != null
			&& mchorse.bbs_mod.BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p
			&& p.isRunning();
	}

	private static void trenner(long vg, float x, float h, float k)
	{
		NanoStifte.linie(vg, x, h / 2F - 7F * k, x, h / 2F + 7F * k, 1F * k, 0x3A3A3A);
	}

	/** Ein Werkzeugknopf; loest das bestehende UIIcon aus. Gibt das naechste x zurueck. */
	private float knopf(long vg, NanoFlaeche f, float x, float h, float k,
		String symbol, String titel, UIIcon ziel, boolean an)
	{
		/* Entwurfsmass .tbtn{width:22px;height:20px}, ohne Abstand dazwischen. */
		float bw = 22F * k;
		float bh = 20F * k;
		float y = (h - bh) / 2F;
		boolean ueber = f.istHover(titel);

		if (an)
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.BLAU, k);
		}
		else if (ueber)
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, symbol, x + bw / 2F, h / 2F,
			an ? 0xFFFFFF : NanoDesign.TXT, k);

		if (ziel != null)
		{
			f.zone(titel, x, y, bw, bh, ziel::clickItself);
		}

		return x + bw;
	}

	private float aufnahme(long vg, NanoFlaeche f, float x, float h, float k,
		String symbol, String titel, UIIcon ziel)
	{
		/* Entwurfsmass .tbtn{width:22px;height:20px}, ohne Abstand dazwischen. */
		float bw = 22F * k;
		float bh = 20F * k;
		float y = (h - bh) / 2F;

		if (f.istHover(titel))
		{
			NanoStifte.flaeche(vg, x, y, bw, bh, 4F * k, 0x4A2523);
		}

		NanoSymbole.symbol(vg, symbol, x + bw / 2F, h / 2F, 0xE58F89, k);

		if (ziel != null)
		{
			f.zone(titel, x, y, bw, bh, ziel::clickItself);
		}

		return x + bw;
	}
}
