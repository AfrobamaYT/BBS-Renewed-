package mchorse.bbs_mod.ui.dashboard.panels;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.ui.film.UIRenderReiterNano;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.l10n.keys.IKey;
import com.mcstudio.nano.NanoSymbole;
import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoBilder;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Topbar im neuen Designsystem: Wortmarke, Menuezeile, die acht Arbeitsbereiche als BENANNTE
 * Reiter, und rechts der geladene Film als Chip (Blender-Muster: Workspace-Tabs in der Topbar).
 *
 * <p>Der Film-Chip loest die alte Reiterleiste unter der Topbar ab — im Entwurf traegt die
 * Topbar den Filmnamen selbst, darunter beginnt sofort die Arbeitsflaeche.</p>
 */
public class UITopbarNano extends UIElement
{
	/**
	 * Kurzname und Symbol je Arbeitsbereich, in der Reihenfolge des Entwurfs. Der Schluessel ist
	 * der BBS-Originalname aus dem Tooltip; was hier nicht steht, behaelt seinen Namen und laeuft
	 * hinten mit — so faellt kein Bereich weg, wenn BBS einen neuen bekommt.
	 */
	private static String[][] bereiche()
	{
		/* Methode statt statischem Feld: ein nachtraeglich hinzugefuegtes statisches Feld ist
		 * nach einem Hotswap null, weil statische Initialisierer beim Redefine nicht laufen. */
		return new String[][] {
			{"Films", "Film", "film"},
			{"Model editor", "Models", "replay"},
			{"Model blocks", "Blocks", "wuerfel"},
			{"Particles", "Particles", "funken"},
			{"Texture manager", "Textures", "bild"},
			{"Audio editor", "Audio", "ton"},
			{"Morphing", "Morphing", "morph"},
			{"Graphing calculator", "Graph", "kurve"},
		};
	}

	private final UIDashboardPanels panels;
	private final NanoFlaeche flaeche;
	private final List<String> namen = new ArrayList<>();
	private int letzterAktiv = -2;
	private String letzterFilm = "";

	public UITopbarNano(UIDashboardPanels panels)
	{
		this.panels = panels;
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
	}

	/** Namen einsammeln — einmal, sobald alle Panels registriert sind. */
	public void namenLaden(List<String> namen)
	{
		this.namen.clear();
		this.namen.addAll(namen);
		this.flaeche.neuMalen();
	}

	private UIFilmPanel filmPanel()
	{
		return this.panels.panel instanceof UIFilmPanel ? (UIFilmPanel) this.panels.panel : null;
	}

	private String filmName()
	{
		UIFilmPanel panel = this.filmPanel();
		Film film = panel == null ? null : panel.getData();

		return film == null ? "" : film.getId();
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		int aktiv = this.panels.panels.indexOf(this.panels.panel);
		String film = this.filmName();

		if (aktiv != this.letzterAktiv || !film.equals(this.letzterFilm))
		{
			this.letzterAktiv = aktiv;
			this.letzterFilm = film;
			this.flaeche.neuMalen();
		}

		super.render(context);
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());

		NanoStifte.flaeche(vg, 0, 0, b, h, 0F, NanoDesign.TOPBAR);

		/* Rechte Seite ZUERST vermessen, damit die Reiter nicht darunter laufen. */
		float rechtsGrenze = this.zeichneRechts(vg, b, h, mausX, mausY, f, k);

		/* Wortmarke links — schlicht, keine Bildmarke */
		float basis = h / 2F + 3.8F * k;

		NanoStifte.text(vg, NanoDesign.BOLD, 11.5F * k, 9F * k, basis, NanoDesign.ORANGE, "BBS", null);

		float x = 9F * k + NanoDesign.breite("BBS", 11.5F * k) + 9F * k;

		/* Menuezeile. Lokal, nicht als statisches Feld — sonst steht sie nach einem Hotswap
		 * auf null (statische Initialisierer laufen beim Redefine nicht). */
		/* ⛔ „Help" ist RAUS. Es gibt in diesem Baum keine Dokumentation, keinen About-Dialog
		 * und keinen Wiki-Link — das Menue haette nur leer aufgehen koennen. Ein Eintrag ohne
		 * Ziel ist dieselbe Attrappe, die diese fuenf Menues vorher waren. */
		for (String menue : new String[] {"File", "Edit", "Render", "Window"})
		{
			float mb = NanoDesign.breite(menue, 10.5F * k) + 13F * k;
			boolean ueber = f.istHover("tbm" + menue);

			if (ueber)
			{
				NanoStifte.widget(vg, x, 4F * k, mb, h - 8F * k, 4F * k, NanoDesign.WIDGET, k);
			}

			NanoStifte.text(vg, NanoDesign.SANS, 10.5F * k, x + 6.5F * k, basis,
				ueber ? NanoDesign.TXT : NanoDesign.TXT_DIM, menue, null);

			final String name = menue;

			f.zone("tbm" + menue, x, 0, mb, h, () -> this.oeffneMenue(name));

			x += mb;
		}

		x += 8F * k;

		/* Arbeitsbereiche als Reiter — sitzen am unteren Rand auf, wie in Blender */
		int aktiv = this.panels.panels.indexOf(this.panels.panel);
		/* Entwurf: .tab 26 hoch in einer 34er Topbar. Bei den gewuenschten 24 bleiben 20 —
		 * genug fuer 11.5-px-Schrift, ohne dass der Reiter oben anstoesst. */
		float reiterH = h - 4F * k;

		for (int nr = 0; nr < this.namen.size(); nr++)
		{
			int i = this.reihenfolge(nr);

			if (i < 0)
			{
				continue;
			}

			String voll = this.namen.get(i);
			String[] eintrag = eintragZu(voll);
			String name = eintrag == null ? voll : eintrag[1];
			String symbol = eintrag == null ? "" : eintrag[2];
			/* Entwurfsmass .tab: padding 0 11px · Symbol 12 · gap 6 · Schrift 11.5
			 * → Breite = Text + 40. Gemessen im Browser: Film 62, Morphing 86. */
			/* ⛔ Bei der ENTWURFSGROESSE messen und dann skalieren, nicht bei 11.5*k messen:
			 * NanoVG rundet Glyphenbreiten auf ganze Pixel, und bei 23 px faellt der Fehler
			 * anders aus als bei 11.5. Der Browser layoutet in CSS-Pixeln und skaliert danach —
			 * dieselbe Reihenfolge macht die Breiten deckungsgleich. */
			float rb = (NanoStifte.textBreite(vg, NanoDesign.SANS, 11.5F, name)
				+ (symbol.isEmpty() ? 22F : 40F)) * k;

			if (x + rb > rechtsGrenze)
			{
				break;
			}

			boolean an = i == aktiv;
			boolean ueber = mausX >= x && mausX < x + rb && mausY >= h - reiterH && mausY < h;

			if (an)
			{
				/* aktiver Reiter traegt die Farbe der Editorflaeche darunter — wie in Blender */
				NanoStifte.flaeche(vg, x, h - reiterH, rb, reiterH, 4F * k, NanoDesign.KOPF);
				NanoStifte.flaeche(vg, x, h - 5F * k, rb, 5F * k, 0F, NanoDesign.KOPF);
			}
			else if (ueber)
			{
				NanoStifte.flaeche(vg, x, h - reiterH + 1F * k, rb, reiterH - 2F * k, 4F * k, 0x242424);
			}

			/* 11 Rand · Symbolmitte bei 17 · Text ab 29 — die Masse des Entwurfs. */
			float textX = x + 11F * k;

			if (!symbol.isEmpty())
			{
				/* Asset zuerst. Die FARBE traegt den Zustand: nur der gewaehlte Reiter
				 * zeigt das Icon farbig, alle anderen entsaettigt — sonst leuchtet die
				 * ganze Leiste bunt und nichts hebt sich ab. Fehlt die Datei
				 * (Morphing: eigenes Asset folgt), greift das Vektorsymbol wie bisher. */
				if (!NanoBilder.symbol(vg, symbol, x + 17F * k, h / 2F + 0.5F * k,
					15F * k, an ? 1F : 0.75F, an))
				{
					NanoSymbole.symbol(vg, symbol, x + 17F * k, h / 2F + 0.5F * k,
						an ? NanoDesign.ORANGE : NanoDesign.TXT_OFF, k * 0.75F);
				}

				textX = x + 29F * k;
			}

			/* Der Entwurf hebt den aktiven Reiter ueber Grund und Farbe hervor, nicht ueber
			 * Fettschrift — sonst spraenge die Breite beim Wechsel. */
			NanoStifte.text(vg, NanoDesign.SANS, 11.5F * k,
				textX, basis, an ? NanoDesign.TXT : NanoDesign.TXT_DIM, name, null);

			final int idx = i;

			f.zone("reiter" + i, x, 0, rb, h, () ->
			{
				if (idx < this.panels.panels.size())
				{
					this.panels.setPanel(this.panels.panels.get(idx));
					this.flaeche.neuMalen();
				}
			});

			x += rb + 1F * k;
		}
	
		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}

	/**
	 * Zeichnet Film-Chip, Neu und Menue am rechten Rand und liefert die linke Kante zurueck,
	 * damit die Reiter davor enden.
	 */
	private float zeichneRechts(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f, float k)
	{
		UIFilmPanel panel = this.filmPanel();
		float knopf = 17F * k;
		float y = (h - knopf) / 2F;
		float x = b - 8F * k;

		/* Einstellungen ganz rechts — im Entwurf das Zahnrad. Ohne diesen Knopf waeren sie
		 * unerreichbar: ihr alter Zugang hing in der ausgeblendeten Icon-Leiste. */
		x -= knopf;
		this.knopf(vg, f, "tbsetup", x, y, knopf, mausX, mausY, k, () ->
		{
			if (mchorse.bbs_mod.BBSModClient.getDashboard() != null)
			{
				mchorse.bbs_mod.BBSModClient.getDashboard().oeffneEinstellungen();
			}
		});
		NanoSymbole.symbol(vg, "zahnrad", x + knopf / 2F, y + knopf / 2F, NanoDesign.TXT_DIM, k * 0.85F);

		/* Filmmenue */
		x -= knopf + 2F * k;
		this.knopf(vg, f, "tbmenue", x, y, knopf, mausX, mausY, k, () ->
		{
			if (panel != null)
			{
				panel.oeffneFilmMenue();
			}
		});
		NanoSymbole.symbol(vg, "menu", x + knopf / 2F, y + knopf / 2F, NanoDesign.TXT_DIM, k * 0.85F);

		/* Neuer Film */
		x -= knopf + 2F * k;
		this.knopf(vg, f, "tbneu", x, y, knopf, mausX, mausY, k, () ->
		{
			if (panel != null)
			{
				panel.neuerFilm();
			}
		});
		NanoSymbole.symbol(vg, "plus", x + knopf / 2F, y + knopf / 2F, NanoDesign.TXT_DIM, k * 0.85F);

		String film = this.filmName();

		if (film.isEmpty())
		{
			return x - 6F * k;
		}

		/* Film-Chip */
		float chipH = 17F * k;
		float tb = NanoDesign.breite(film, 10.5F * k);

		/* Symbol 10 · Luft 8 · Text · Luft 8 · Pfeil 10 — der Text sass vorher bei x+16
		 * direkt am Symbol (das bis x+16 reicht) und klebte damit links an, statt zwischen
		 * den beiden Zeichen zu stehen. */
		float chipB = tb + 36F * k;

		x -= chipB + 4F * k;

		NanoStifte.widget(vg, x, y, chipB, chipH, 4F * k, NanoDesign.WIDGET, k);
		NanoSymbole.symbol(vg, "film", x + 10F * k, y + chipH / 2F, NanoDesign.TXT_DIM, k * 0.8F);

		/* Mitte des freien Feldes zwischen Symbolkante (18) und Pfeilkante (chipB-14).
		 *
		 * ⛔ KEINE Grundlinien-Korrektur addieren. `textMitte` setzt NVG_ALIGN_MIDDLE, die
		 * y-Koordinate IST schon die Mitte. Das frühere `+ 3.6F * k` stammte von
		 * `NanoStifte.text` (das auf der Grundlinie zeichnet) und schob den Namen so weit nach
		 * unten, dass die Unterlänge von „g" am Chiprand klebte. Der Nutzer hat es angestrichen. */
		NanoStifte.textMitte(vg, NanoDesign.SANS, 10.5F * k,
			x + (18F * k + chipB - 14F * k) / 2F, y + chipH / 2F,
			NanoDesign.TXT, film);
		NanoSymbole.pfeilAb(vg, x + chipB - 8F * k, y + chipH / 2F, NanoDesign.TXT_DIM, k * 0.8F);

		/* ⛔ Filmliste, NICHT das Optionsmenue: der Chip traegt den Filmnamen, also erwartet
		 * man dort die anderen Filme — nicht Speichern/Layout/Umbenennen. Die Optionen
		 * bleiben unter dem Menue-Knopf rechts daneben erreichbar. */
		f.zone("tbfilm", x, y, chipB, chipH, () ->
		{
			if (panel != null)
			{
				this.filmListeAufklappen(panel);
			}
		});

		return x - 6F * k;
	}

	/**
	 * Aufklappliste unter dem Film-Chip: ALLE vorhandenen Filme, der geladene mit Haken.
	 * Das Vollbild-Overlay bleibt als „All Films…" erreichbar — dort sitzt die Suche.
	 *
	 * <p>⛔ Die Namen kommen aus {@code requestKeys}, nicht aus einer eigenen Ordnerliste:
	 * das Repository kann fern liegen, und ein Verzeichnis-Scan waere dort still leer.</p>
	 */
	private void filmListeAufklappen(UIFilmPanel panel)
	{
		String aktuell = panel.getData() == null ? null : panel.getData().getId();

		panel.getType().getRepository().requestKeys((namen) ->
		{
			java.util.List<String> filme = new java.util.ArrayList<>(namen);

			/* ⛔ Namenlose Eintraege raus: im Testbaum lag eine Datei „.dat", die als Film
			 * mit leerem Namen durchkam — eine Zeile, die man weder lesen noch treffen kann. */
			filme.removeIf((n) -> n == null || n.trim().isEmpty());
			filme.sort(String.CASE_INSENSITIVE_ORDER);

			this.getContext().replaceContextMenu((menu) ->
			{
				menu.gruppe(IKey.raw("Films"));

				for (String name : filme)
				{
					boolean geladen = name.equals(aktuell);

					menu.action(geladen ? Icons.CHECKMARK : Icons.FILM, IKey.raw(name),
						() -> panel.pickData(name));
				}

				menu.trenner();
				menu.action(Icons.ADD, IKey.raw("New Film..."), panel::neuerFilm);
				menu.action(Icons.MORE, IKey.raw("All Films..."), panel::openFilmListOverlay);
			});
		});
	}

	/**
	 * Inhalt der Topbar-Menues. Jeder Eintrag ruft eine Aktion, die es in BBS schon gibt —
	 * es wird nichts angelegt, was der Editor nicht kann.
	 */
	private void oeffneMenue(String name)
	{
		UIFilmPanel panel = this.panels.panel instanceof UIFilmPanel p ? p : null;

		this.getContext().replaceContextMenu((menu) ->
		{
			switch (name)
			{
				case "File" ->
				{
					if (panel != null)
					{
						menu.gruppe(IKey.raw("Film"));
						menu.action(Icons.ADD, IKey.raw("New Film"), panel::neuerFilm);
						menu.action(Icons.FILM, IKey.raw("Open Film..."), panel::openFilmListOverlay);
						menu.action(Icons.SAVED, IKey.raw("Save"), panel::forceSave)
							.taste(mchorse.bbs_mod.ui.Keys.SAVE);
						menu.trenner();
					}

					menu.action(Icons.GEAR, IKey.raw("Preferences..."), () ->
					{
						if (mchorse.bbs_mod.BBSModClient.getDashboard() != null)
						{
							mchorse.bbs_mod.BBSModClient.getDashboard().oeffneEinstellungen();
						}
					});
				}
				case "Edit" ->
				{
					if (panel != null)
					{
						menu.action(Icons.UNDO, IKey.raw("Undo"), panel::undo);
						menu.action(Icons.REDO, IKey.raw("Redo"), panel::redo);
					}
				}
				case "Render" ->
				{
					if (panel != null)
					{
						/* Derselbe Weg wie der Render-Reiter im N-Panel — eine Wahrheit. */
						/* Derselbe Weg wie der Render-Reiter im N-Panel — eine Wahrheit.
						 * „Render Settings" fehlt bewusst: es gibt keinen Einstieg, der den
						 * Reiter oeffnet, und ein Eintrag ohne Ziel waere wieder eine Attrappe. */
						menu.action(Icons.SPHERE, IKey.raw("Render Animation"),
							UIRenderReiterNano::rendernStarten);
					}
				}
				case "Window" ->
				{
					if (panel != null)
					{
						menu.action(Icons.FULLSCREEN, IKey.raw("Toggle Viewport Fullscreen"),
							() -> panel.flaecheMaximieren("preview"));
						menu.action(Icons.LAYOUT, IKey.raw("Layout Presets"),
							panel::openLayoutPresetsMenu);
						menu.action(Icons.REFRESH, IKey.raw("Reset Layout"),
							panel::layoutZuruecksetzen);
					}
				}
				default -> { }
			}
		});
	}

	/** Eintrag zum BBS-Originalnamen, oder null wenn der Bereich nicht im Entwurf steht. */
	private static String[] eintragZu(String voll)
	{
		for (String[] e : bereiche())
		{
			if (e[0].equals(voll))
			{
				return e;
			}
		}

		return null;
	}

	/**
	 * Anzeigeposition {@code nr} auf den Bereichsindex abbilden: erst die Bereiche des Entwurfs
	 * in dessen Reihenfolge, danach alles Uebrige. Die Panel-Liste selbst bleibt unberuehrt —
	 * Tastenkuerzel und Durchschalten haengen daran.
	 */
	private int reihenfolge(int nr)
	{
		if (nr < bereiche().length)
		{
			return this.namen.indexOf(bereiche()[nr][0]);
		}

		int uebrig = nr - bereiche().length;

		for (int i = 0; i < this.namen.size(); i++)
		{
			if (eintragZu(this.namen.get(i)) == null && uebrig-- == 0)
			{
				return i;
			}
		}

		return -1;
	}

	private void knopf(long vg, NanoFlaeche f, String id, float x, float y, float gr,
		float mausX, float mausY, float k, Runnable tat)
	{
		if (mausX >= x && mausX < x + gr && mausY >= y && mausY < y + gr)
		{
			NanoStifte.widget(vg, x, y, gr, gr, 4F * ((gr / 17F) + 0.01F), NanoDesign.WIDGET, k);
		}

		f.zone(id, x, y, gr, gr, tat);
	}
}
