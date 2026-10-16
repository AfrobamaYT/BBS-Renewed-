package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoSymbole;
import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoBilder;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;

/**
 * Werkzeugleiste im Viewport — schwebend links oben, senkrecht, wie Blenders Toolbar.
 *
 * <p>Der Flaechenkopf traegt danach nur noch Ansicht und Aufnahme; was am Objekt arbeitet
 * (Kamera anspringen, Flug, Steuern, Perspektive) sitzt hier am Bild, wo es gebraucht wird.
 * Ausgeloest werden dieselben {@link UIIcon}-Aktionen — keine zweite Wahrheit.</p>
 */
public class UIWerkzeugleisteNano extends UIElement
{
	/** Entwurfsmass .tools span{width:26px;height:26px} plus 3 px Rand. */
	private static final int KNOPF = 26;
	private static final int RAND = 3;

	private final UIFilmPreview preview;
	private final NanoFlaeche flaeche;

	public UIWerkzeugleisteNano(UIFilmPreview preview)
	{
		this.preview = preview;
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
	}

	/** Breite/Hoehe, die die Leiste braucht — der Viewport setzt sie damit selbst. */
	public static int breite()
	{
		return KNOPF + RAND * 2;
	}

	public static int hoehe(int knoepfe, int luecken)
	{
		return RAND * 2 + knoepfe * KNOPF + luecken * RAND;
	}

	/** Ein Knopf der Leiste. {@code ziel} wird spaet aufgeloest — die UIIcons entstehen erst
	 * im Konstruktor des Preview. */
	private record Knopf(String symbol, String titel, int farbe,
		java.util.function.Function<UIFilmPreview, UIIcon> holen)
	{
		UIIcon ziel(UIFilmPreview p)
		{
			return this.holen.apply(p);
		}
	}

	/** Index, ab dem die zweite Gruppe (Aufnahme) beginnt.
	 *
	 * ⛔ Methode, kein statisches Feld: statische Initialisierer laufen beim Redefine nicht,
	 * das Feld staende nach dem Swap auf 0 — der Hotswap-Waechter lehnt es zu Recht ab. */
	private static int trennung()
	{
		return 4;
	}

	/** Abstand der schwebenden Leiste zur Viewport-Kante — muss zu UIFilmPreview passen.
	 * Methode aus demselben Grund wie {@link #trennung()}: neue statische Felder stehen
	 * nach einem Hotswap auf 0. */
	private static int versatz()
	{
		return 7;
	}

	private static Knopf[] knoepfe()
	{
		return new Knopf[] {
			new Knopf("control", "Control", 0xCFCFCF, p -> p.control),
			new Knopf("fly", "Fly mode", 0xCFCFCF, p -> p.flight),
			new Knopf("onion", "Onion skin", 0xCFCFCF, p -> p.onionSkin),
			new Knopf("path", "Motion path", 0xCFCFCF, p -> p.motionPath),
			new Knopf("rec", "Record replay", 0xE58F89, p -> p.recordReplay),
			new Knopf("camera", "Record video", 0xCFCFCF, p -> p.recordVideo),
		};
	}

	/**
	 * Spaltenzahl fuer die verfuegbare Hoehe.
	 *
	 * <p>⛔ Die Leiste ragte sonst in die Zeitleiste: sechs Knoepfe brauchen 165 px, ein flach
	 * gezogener Viewport hatte im Test 106. Blender loest das ueber die Breite der Toolbar —
	 * mehr Breite, mehr Spalten. Hier automatisch, weil die Leiste schwebt und keine eigene
	 * Kante zum Ziehen hat.</p>
	 */
	private int spalten()
	{
		/* ⛔ VERSATZ, nicht RAND: die Leiste sitzt 7 px unter der Viewport-Oberkante
		 * (relative(this).y(7)) und braucht denselben Abstand unten. Mit RAND*2 gerechnet
		 * blieben 3 px Ueberlauf — gemessen. */
		float platz = this.getParent() == null ? 0F : this.getParent().area.h - versatz() * 2F;
		int knoepfe = knoepfe().length;

		if (platz <= 0F)
		{
			return 1;
		}

		int passt = Math.max(1, (int) (platz / KNOPF));

		return Math.max(1, (knoepfe + passt - 1) / passt);
	}

	@Override
	public void afterResizeApplied()
	{
		super.afterResizeApplied();

		int spalten = this.spalten();
		int jeSpalte = (knoepfe().length + spalten - 1) / spalten;

		this.area.w = RAND * 2 + spalten * KNOPF;
		this.area.h = RAND * 2 + jeSpalte * KNOPF + (spalten == 1 ? RAND : 0);
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());

		/* Schwebende Platte statt Vollflaeche: das Bild darunter bleibt sichtbar. */
		NanoStifte.flaecheArgb(vg, 0, 0, b, h, NanoDesign.R_PANEL * k, 0xD9121212);

		float y = RAND * k;
		float gr = KNOPF * k;
		float x = RAND * k;

		/* Knoepfe als Liste, damit das Layout sie umbrechen kann. Gruppe 1 = Werkzeuge
		 * (schalten die Maus um), Gruppe 2 = Aufnahme. */
		Knopf[] knoepfe = knoepfe();
		int spalten = this.spalten();
		int jeSpalte = (knoepfe.length + spalten - 1) / spalten;

		for (int i = 0; i < knoepfe.length; i++)
		{
			int spalte = i / jeSpalte;
			int reihe = i % jeSpalte;

			/* Gruppenluecke nur in einspaltiger Lage — umgebrochen faellt sie ohnehin weg. */
			float luecke = spalten == 1 && reihe >= trennung() ? RAND * k : 0F;

			this.knopf(vg, f, x + spalte * gr, y + reihe * gr + luecke, gr, k,
				knoepfe[i].symbol, knoepfe[i].titel, knoepfe[i].ziel(this.preview),
				knoepfe[i].farbe);
		}

		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}

	private float knopf(long vg, NanoFlaeche f, float x, float y, float gr, float k,
		String symbol, String titel, UIIcon ziel)
	{
		return this.knopf(vg, f, x, y, gr, k, symbol, titel, ziel, 0xCFCFCF);
	}

	private float knopf(long vg, NanoFlaeche f, float x, float y, float gr, float k,
		String symbol, String titel, UIIcon ziel, int farbe)
	{
		if (f.istHover(titel))
		{
			NanoStifte.flaecheArgb(vg, x, y, gr, gr, 4F * k, 0x22FFFFFF);
		}

		/* Asset zuerst; NanoBilder meldet false, wenn die PNG fehlt — dann greift das
		 * Vektorsymbol wie bisher, statt dass der Knopf leer bleibt. */
		if (!NanoBilder.symbol(vg, symbol, x + gr / 2F, y + gr / 2F, gr * 0.82F, 1F))
		{
			NanoSymbole.symbol(vg, symbol, x + gr / 2F, y + gr / 2F, farbe, k);
		}

		if (ziel != null)
		{
			f.zone(titel, x, y, gr, gr, ziel::clickItself);
		}

		return y + gr;
	}

	/** Vollbild haengt am Panel, nicht an einem UIIcon — deshalb eigener Knopf. */
	private float vollbild(long vg, NanoFlaeche f, float x, float y, float gr, float k)
	{
		/* getDashboardIfCreated: getDashboard wuerde das Dashboard erzeugen — hier zwar
		 * harmlos (wir zeichnen ja schon darin), aber die Regel gilt ueberall. */
		mchorse.bbs_mod.ui.dashboard.UIDashboard dash = mchorse.bbs_mod.BBSModClient.getDashboardIfCreated();
		UIFilmPanel panel = dash != null && dash.panels.panel instanceof UIFilmPanel p ? p : null;
		boolean max = panel != null && panel.istMaximiert();

		if (max || f.istHover("Fullscreen"))
		{
			NanoStifte.flaecheArgb(vg, x, y, gr, gr, 4F * k, max ? 0x664772B3 : 0x22FFFFFF);
		}

		NanoSymbole.symbol(vg, "voll", x + gr / 2F, y + gr / 2F, max ? 0xFFFFFF : 0xCFCFCF, k);

		if (panel != null)
		{
			f.zone("Fullscreen", x, y, gr, gr, () -> panel.flaecheMaximieren("preview"));
		}

		return y + gr;
	}
}
