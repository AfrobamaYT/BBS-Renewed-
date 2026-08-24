package mchorse.bbs_mod.ui.film;

import java.util.ArrayList;
import java.util.List;

import com.mcstudio.nano.NanoSymbole;
import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Outliner in der Sprache des neuen Designsystems (Claude Design, styles.css + Bausteine-Karte).
 *
 * <p>Zeigt den Baum des GELADENEN Films: Kamera, alle Replays mit ihren Kanal-Zaehlern, Audio.
 * Keine Beispieldaten — jede Zeile kommt aus {@code UIFilmPanel.getData()}.</p>
 *
 * <p>Masse und Farben stammen 1:1 aus der Vorlage: Zeile 22 px, Schrift 12 px, Einzug 12 px mit
 * Fuehrungslinie #303030, Zaehler rechts in #616161, Auswahl rgba(71,114,179,.38), Hover #2A2A2A,
 * Flaechen-Kopf 26 px auf #2B2B2B, Koerper #1D1D1D.</p>
 */
public class UIOutlinerNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	/* ---- Token aus styles.css ---- */
	private static final int C_REGION   = 0x242424;
	private static final int C_HEADER   = 0x2B2B2B;
	private static final int C_EDITOR   = 0x1D1D1D;
	private static final int C_WIDGET   = 0x3D3D3D;
	private static final int C_FELD     = 0x1A1A1A;
	private static final int C_TXT      = 0xE0E0E0;
	private static final int C_DIM      = 0x9C9C9C;
	private static final int C_OFF      = 0x616161;
	private static final int C_LINIE    = 0x303030;
	private static final int C_HOVER    = 0x2A2A2A;
	private static final int C_BLAU     = 0x4772B3;
	private static final int C_ROT      = 0xE5484D;

	private final NanoFlaeche flaeche;
	private final List<Zeile> zeilen = new ArrayList<>();
	private String stand = "";
	private int gewaehlt = -1;
	/** Zeile mit dem im Editor aktiven Replay — Blenders „active data-block". Eigener
	 *  Zustand neben der Auswahl; wird bei jedem Auffrischen neu bestimmt. */
	private int aktivZeile = -1;
	/** Zuletzt gesehenes aktives Replay. Springt der Editor von aussen woanders hin,
	 *  folgt die Auswahl — eine eigene Wahl ueberschreibt sie dagegen nicht mehr. */
	private Replay letztesAktiv;
	/** Klappe des Editor-Typ-Waehlers offen? */
	private boolean klappeOffen;
	/** Anzeigemodus wie Blenders Display Mode: 0 = Film (Kamera+Replays), 1 = nur Replays, 2 = nur Kanaele. */
	private int modus;
	/** Suchtext — filtert den Baum. Leer = alles zeigen. */
	private String suche = "";

	/** Eine Baumzeile: Tiefe, Symbol, Name, optionaler Zaehler rechts. */
	private static final class Zeile
	{
		final int tiefe;
		final String art;      // "film" | "kamera" | "replay" | "kanal" | "audio"
		final String name;
		final String zaehler;
		final boolean aus;     // ausgeblendet (enabled == false)
		final Replay replay;   // null bei Film-/Kamera-Zeile

		Zeile(int tiefe, String art, String name, String zaehler, boolean aus, Replay replay)
		{
			this.tiefe = tiefe; this.art = art; this.name = name;
			this.zaehler = zaehler; this.aus = aus; this.replay = replay;
		}
	}

	public static UIElement neu()
	{
		return new UIOutlinerNano();
	}

	public UIOutlinerNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.beimTippen(this::tippen);

		/* Rechtsklick auf eine Replay-Zeile: das Kontextmenue der ALTEN Liste — die Logik
		 * (Selektion, removeReplay mit Fokus-Nachruecken, Undo) lebt dort weiter, nur das
		 * Zuhause ist neu (Nutzerbefund Akte 34: "kein rechtsklick, kein hotkey fuer delete"). */
		this.flaeche.beimRechtsklick((zoneId, pos) ->
		{
			if (!zoneId.startsWith("zeile"))
			{
				return;
			}

			int idx = Integer.parseInt(zoneId.substring(5));
			Replay ziel = idx < this.zeilen.size() ? this.zeilen.get(idx).replay : null;
			UIFilmPanel panel = filmPanel();

			if (ziel == null || panel == null)
			{
				return;
			}

			mchorse.bbs_mod.ui.film.replays.UIReplayList liste = panel.replayEditor.replaysList.replays;

			liste.scrollToReplay(ziel);   /* pick(i) — selektiert wirklich, nicht nur scrollen */
			this.gewaehlt = idx;
			this.flaeche.neuMalen();

			this.getContext().replaceContextMenu((menu) ->
			{
				menu.gruppe(mchorse.bbs_mod.l10n.keys.IKey.raw("Replay"));
				menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.REMOVE,
					mchorse.bbs_mod.ui.UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE,
					mchorse.bbs_mod.utils.colors.Colors.NEGATIVE, liste::removeReplay)
					.taste(mchorse.bbs_mod.ui.Keys.DELETE);
			});
		});

		/* Entf loescht das im Outliner gewaehlte Replay — derselbe Weg wie das Menue. */
		this.keys().register(mchorse.bbs_mod.ui.Keys.DELETE, () ->
		{
			Replay ziel = this.gewaehlt >= 0 && this.gewaehlt < this.zeilen.size()
				? this.zeilen.get(this.gewaehlt).replay : null;
			UIFilmPanel panel = filmPanel();

			if (ziel != null && panel != null)
			{
				panel.replayEditor.replaysList.replays.scrollToReplay(ziel);
				panel.replayEditor.replaysList.replays.removeReplay();
			}
		});
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
		this.h(300);
	}

	/** Tastatur im Suchfeld: Zeichen anhaengen, Ruecktaste loescht. */
	private void tippen(String fokusId, char c)
	{
		if (!"suche".equals(fokusId))
		{
			return;
		}

		if (c == '\b')
		{
			if (!this.suche.isEmpty())
			{
				this.suche = this.suche.substring(0, this.suche.length() - 1);
			}
		}
		else
		{
			this.suche += c;
		}

		this.stand = "";
		this.auffrischen();
		this.flaeche.neuMalen();
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	/** Anzeigename: derselbe Weg wie die echte Replay-Liste (UIReplayList nutzt replay.getName()). */
	private static String replayName(Replay replay, int nummer)
	{
		String name = replay.getName();

		return name == null || name.isEmpty() ? "replay " + nummer : name;
	}

	/** Baum aus dem geladenen Film aufbauen. Gibt eine Kennung zurueck, die den Stand beschreibt. */
	private String baumBauen()
	{
		this.zeilen.clear();

		UIFilmPanel panel = filmPanel();
		Film film = panel == null ? null : panel.getData();

		if (film == null)
		{
			this.zeilen.add(new Zeile(0, "film", "Kein Film geladen", "", true, null));
			return "leer";
		}

		StringBuilder kennung = new StringBuilder(film.getId()).append('m').append(this.modus).append('s').append(this.suche);

		this.zeilen.add(new Zeile(0, "film", film.getId(), "", false, null));

		if (this.modus == 0)
		{
			int kameraClips = film.camera.get().size();
			this.zeilen.add(new Zeile(1, "kamera", "Camera", kameraClips + (kameraClips == 1 ? " clip" : " clips"), false, null));
			kennung.append('|').append(kameraClips);
		}

		List<Replay> liste = film.replays.getList();

		String filter = this.suche.toLowerCase();

		for (int i = 0; i < liste.size(); i++)
		{
			Replay replay = liste.get(i);
			String name = replayName(replay, i + 1);

			if (!filter.isEmpty() && !name.toLowerCase().contains(filter))
			{
				continue;
			}

			boolean aus = !replay.enabled.get();
			int kanaele = replay.keyframes.getAll().size();

			this.zeilen.add(new Zeile(1, "replay", name, kanaele + (kanaele == 1 ? " channel" : " channels"), aus, replay));
			kennung.append('|').append(name).append(aus ? "-" : "+").append(kanaele);
		}

		return kennung.toString();
	}

	@Override
	public void auffrischen()
	{
		String jetzt = this.baumBauen();

		/* ⛔ Auswahl und aktives Replay sind ZWEI zustaende, nicht einer. Blender macht es
		 * genauso (Handbuch, „Selecting Outliner Items"): eine einfache auswahl aktiviert
		 * das datablock zusaetzlich, ausgewaehlte zeilen sind blau, die AKTIVE in einem
		 * helleren blau.
		 *
		 * Vorher wurde `gewaehlt` bei JEDEM auffrischen aus dem aktiven Replay abgeleitet.
		 * Die wurzelzeilen (Film, Camera) haben keins — ihre auswahl war im naechsten bild
		 * wieder weg, und sie sahen tot aus. */
		UIFilmPanel panel = filmPanel();
		Replay aktiv = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		this.aktivZeile = -1;

		for (int i = 0; i < this.zeilen.size(); i++)
		{
			if (aktiv != null && this.zeilen.get(i).replay == aktiv)
			{
				this.aktivZeile = i;
				break;
			}
		}

		/* Die auswahl folgt nur, wenn der editor VON AUSSEN gesprungen ist. */
		if (aktiv != this.letztesAktiv)
		{
			this.letztesAktiv = aktiv;
			this.gewaehlt = this.aktivZeile;
		}

		jetzt = jetzt + "#" + this.gewaehlt + "@" + this.aktivZeile;

		if (!jetzt.equals(this.stand))
		{
			this.stand = jetzt;
			this.flaeche.neuMalen();
		}
	}


	/**
	 * ⛔ SELBST auffrischen. Als Seitenleisten-Reiter rief {@code UISeitenleiste} das
	 * {@link UISeitenleiste.Auffrischbar#auffrischen()} jeden Frame — in einer Dock-Flaeche
	 * tut das NIEMAND. Ohne diesen Aufruf zeigt die Flaeche ihren Stand vom Aufbau und
	 * reagiert auf keine Aenderung (gemessen 2026-08-18: enabled=false blieb unsichtbar).
	 */
	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		this.auffrischen();

		super.render(context);
	}

	/* ================= Zeichnen ================= */

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		if (this.zeilen.isEmpty())
		{
			this.baumBauen();
		}

		/* Masse sind GUI-Pixel; die Flaeche zeichnet in Textur-Pixeln. */
		final float k      = Math.max(1F, f.skala());
		final float kopfH  = 26F * k;
		/* Entwurfsmass .tr{height:20px} — mit 22 lief der Baum gegenueber dem Entwurf
		 * Zeile fuer Zeile weiter auseinander (im Pixelvergleich als Doppelbild sichtbar). */
		final float zeileH = 20F * k;

		NanoStifte.flaeche(vg, 0, 0, b, h, 7F * k, C_REGION);
		NanoStifte.flaeche(vg, 0, 0, b, kopfH, 7F * k, C_HEADER);
		NanoStifte.flaeche(vg, 0, kopfH - 7F * k, b, 7F * k, 0F, C_HEADER);

		/* Editor-Typ-Waehler links im Kopf — Signatur jeder Flaeche, jetzt bedienbar */
		float ex = 6F * k, ey = (kopfH - 20F * k) / 2F;
		NanoStifte.flaeche(vg, ex, ey, 30F * k, 20F * k, 4F * k,
			this.klappeOffen ? NanoDesign.WIDGET_HI : C_WIDGET);
		zeichneSymbol(vg, "outliner", ex + 9F * k, ey + 10F * k, C_TXT, k);
		zeichnePfeilAb(vg, ex + 23F * k, ey + 10F * k, C_DIM, k);

		f.zone("etyp", ex, ey, 30F * k, 20F * k, () ->
		{
			this.klappeOffen = !this.klappeOffen;
			this.flaeche.neuMalen();
		});

		/* Suchfeld — echte Texteingabe mit Fokus; bei enger Flaeche weggelassen */
		if (!NanoDesign.eng(b, k))
		{
		float sx = ex + 42F * k;
		float sw = b - sx - 30F * k;
		boolean sucheAktiv = "suche".equals(f.fokus());

		NanoStifte.widget(vg, sx, kopfH / 2F - 9F * k, sw, 18F * k, 4F * k, C_FELD, k);

		if (sucheAktiv)
		{
			NanoStifte.rahmen(vg, sx, kopfH / 2F - 9F * k, sw, 18F * k, 4F * k, NanoDesign.BLAU);
		}

		zeichneSymbol(vg, "suche", sx + 8F * k, kopfH / 2F, sucheAktiv ? NanoDesign.BLAU : C_OFF, k);

		String angezeigt = this.suche.isEmpty() && !sucheAktiv ? "Search…" : this.suche;

		NanoStifte.textInBox(vg, "sans", 11F * k, sx + 18F * k, kopfH / 2F,
			this.suche.isEmpty() ? C_OFF : C_TXT,
			NanoDesign.kuerzen(angezeigt, sw - 26F * k, 11F * k));

		if (sucheAktiv)
		{
			float cx2 = sx + 18F * k + NanoDesign.breite(this.suche, 11F * k) + 1F * k;

			NanoStifte.widget(vg, cx2, kopfH / 2F - 6F * k, 1.5F * k, 12F * k, 0F, NanoDesign.BLAU, k);
		}

		f.zone("suche", sx, kopfH / 2F - 9F * k, sw, 18F * k, () -> f.setzeFokus("suche"));
		}

		/* Neues Replay — rechts im Kopf, wie im Entwurf. Ruft dieselbe Aktion wie das
		 * Kontextmenue der alten Liste, damit es nur einen Weg gibt, ein Replay anzulegen. */
		float px = b - 24F * k;
		float py = (kopfH - 20F * k) / 2F;

		if (f.istHover("neu"))
		{
			NanoStifte.widget(vg, px, py, 20F * k, 20F * k, 4F * k, C_WIDGET, k);
		}

		NanoSymbole.symbol(vg, "plus", px + 10F * k, py + 10F * k, C_DIM, k * 0.85F);

		f.zone("neu", px, py, 20F * k, 20F * k, () ->
		{
			UIFilmPanel panel = filmPanel();

			if (panel != null && panel.replayEditor != null && panel.replayEditor.replaysList != null)
			{
				panel.replayEditor.replaysList.replays.addReplay();
			}

			this.stand = "";
			this.flaeche.neuMalen();
		});

		/* Koerper */
		NanoStifte.flaeche(vg, 0, kopfH, b, h - kopfH, 0F, C_EDITOR);

		float y = kopfH + 3F * k;

		int passen = Math.max(1, (int) ((h - y) / zeileH));
		int ersteZeile = Math.min((int) f.rollVersatz(), Math.max(0, this.zeilen.size() - passen));

		f.rollGrenze(Math.max(0, this.zeilen.size() - passen));

		for (int i = ersteZeile; i < this.zeilen.size(); i++)
		{
			Zeile z = this.zeilen.get(i);

			if (y + zeileH > h)
			{
				break;
			}

			boolean ueber = mausX >= 0 && mausY >= y && mausY < y + zeileH;
			boolean sel   = i == this.gewaehlt;
			boolean ist   = i == this.aktivZeile;

			if (sel && ist)
			{
				/* Ausgewaehlt UND aktiv — Blenders helleres blau, der haeufigste fall. */
				NanoStifte.flaecheArgb(vg, 0, y, b, zeileH, 0F, 0x8A5B8FD0);
			}
			else if (sel)
			{
				NanoStifte.flaecheArgb(vg, 0, y, b, zeileH, 0F, 0x614772B3);
			}
			else if (ist)
			{
				/* Aktiv, aber nicht ausgewaehlt: der nutzer hat eine wurzelzeile gewaehlt.
				 * Schwaecher, damit beide zustaende gleichzeitig lesbar bleiben. */
				NanoStifte.flaecheArgb(vg, 0, y, b, zeileH, 0F, 0x2E4772B3);
			}
			else if (ueber)
			{
				NanoStifte.flaeche(vg, 0, y, b, zeileH, 0F, C_HOVER);
			}

			float x = 9F * k;

			/* Einzugslinien — fuehren das Auge, wie in der Vorlage */
			for (int t = 0; t < z.tiefe; t++)
			{
				float lx = x + 5F * k;
				NanoStifte.linie(vg, lx, y, lx, y + zeileH, 1F * k, C_LINIE);
				x += 12F * k;
			}

			int farbeText = z.aus ? C_OFF : (sel ? 0xFFFFFF : C_TXT);
			int farbeIkon = z.aus ? C_OFF : (sel ? 0xE9F0FA : C_DIM);

			zeichneSymbol(vg, z.art, x + 7F * k, y + zeileH / 2F, farbeIkon, k);
			x += 18F * k;

			/* Rechte Seite zuerst vermessen: Auge, dann Zaehler — beide nur, wenn Platz bleibt. */
			float augeB = b > 90F * k ? 20F * k : 0F;
			float zw = 0F;
			boolean zeigeZaehler = false;

			if (!z.zaehler.isEmpty())
			{
				zw = NanoDesign.breite(z.zaehler, 10F * k) + 8F * k;
				zeigeZaehler = b - x - augeB - zw > 46F * k;

				if (!zeigeZaehler)
				{
					zw = 0F;
				}
			}

			float platz = b - x - augeB - zw - 4F * k;

			NanoStifte.textInBox(vg, z.tiefe == 0 ? "bold" : "sans", 12F * k,
				x, y + zeileH / 2F, farbeText,
				NanoDesign.kuerzen(z.name, platz, 12F * k));

			if (zeigeZaehler)
			{
				NanoStifte.textInBox(vg, "sans", 10F * k, b - augeB - zw + 4F * k,
					y + zeileH / 2F, sel ? 0xB9CCE9 : C_OFF, z.zaehler);
			}

			if (augeB > 0F)
			{
				zeichneSymbol(vg, z.aus ? "auge-aus" : "auge", b - 11F * k, y + zeileH / 2F,
					z.aus ? C_OFF : farbeIkon, k);

			}

			final int idx = i;
			final Replay ziel = z.replay;

			final float augeAb = b - Math.max(augeB, 34F * k);

			f.zone("zeile" + i, 0, y, b, zeileH, () ->
			{
				/* Rechts im Auge-Bereich: Sichtbarkeit umschalten. Sonst: auswaehlen.
				   EINE Zone statt zwei ueberlappender — die Reihenfolge war nicht verlaesslich. */
				if (ziel != null && augeB > 0F && f.letzterKlickX() >= augeAb)
				{
					ziel.enabled.set(!ziel.enabled.get());
					this.stand = "";
					this.auffrischen();
					this.flaeche.neuMalen();

					return;
				}

				this.gewaehlt = idx;

				UIFilmPanel panel = filmPanel();

				if (ziel != null && panel != null && panel.replayEditor != null)
				{
					panel.replayEditor.setReplay(ziel);
				}

				this.flaeche.neuMalen();
			});


			y += zeileH;
		}

		/* Aufgeklappte Editor-Liste — zuletzt gezeichnet UND zuletzt gemeldet, liegt also obenauf */
		if (this.klappeOffen)
		{
			String[][] eintraege = {
				{"outliner", "Film tree"},
				{"replay", "Replays only"},
				{"dopesheet", "Channels only"},
			};

			NanoBausteine.editorKlappe(vg, f, 6F * k, kopfH + 2F * k, k, eintraege, this.modus, (wahl) ->
			{
				this.modus = wahl;
				this.klappeOffen = false;
				this.stand = "";
				this.auffrischen();
				this.flaeche.neuMalen();
			});

			return;
		}

		/* Fusszeile: was der Baum zeigt */
		if (this.zeilen.size() > 1 && h - y > 22F * k)
		{
			NanoStifte.text(vg, "sans", 10F * k, 9F * k, h - 7F * k, C_OFF,
				(this.zeilen.size() - 2) + (this.zeilen.size() - 2 == 1 ? " replay" : " replays"), null);
		}
	
		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}

	/** Text auf die verfuegbare Breite kuerzen; Noto Sans laeuft mit ~0.52 em je Zeichen. */
	private static String kuerzen(String text, float platz, float groesse)
	{
		float je = groesse * 0.52F;
		int passt = (int) (platz / je);

		if (passt >= text.length() || passt < 2)
		{
			return text;
		}

		return text.substring(0, Math.max(1, passt - 1)) + "…";
	}

	/* --- Symbole: schlanke Vektorzeichen im Stil des Icon-Satzes --- */

	private static void zeichneSymbol(long vg, String art, float cx, float cy, int farbe, float k)
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

			default:
				NanoStifte.kreis(vg, cx, cy, 2F * k, farbe);
				break;
		}
	}

	private static void zeichnePfeilAb(long vg, float cx, float cy, int farbe, float k)
	{
		NanoStifte.linie(vg, cx - 3F * k, cy - 1.5F * k, cx, cy + 1.5F * k, 1.2F * k, farbe);
		NanoStifte.linie(vg, cx, cy + 1.5F * k, cx + 3F * k, cy - 1.5F * k, 1.2F * k, farbe);
	}
}
