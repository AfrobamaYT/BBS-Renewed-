package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
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
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Zeitleiste im neuen Designsystem: Kanal-Spalte links, Spuren rechts, Lineal, Abspielkopf.
 *
 * <p>Alle Keyframes kommen aus den ECHTEN Kanaelen des gewaehlten Replays; der Abspielkopf
 * steht auf dem echten Cursor des Film-Editors.</p>
 */
public class UIZeitleisteNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	private final NanoFlaeche flaeche;
	private String stand = "";
	private int gewaehlteSpur = -1;
	/** Clip unter der Maus seit dem Druck — der Zieh-Handler kennt sonst nur die Zonen-Id,
	 * und die Zuordnung Id→Clip lebt nur im Zeichendurchgang. */
	private mchorse.bbs_mod.utils.clips.Clip zugClip;
	/** Ob der Zieh-Handler an der Flaeche haengt — siehe zeichnen(). */
	private boolean ziehenVerdrahtet;
	/** Sichtbarer Zeitausschnitt. Beide 0 = ganze Dauer zeigen.
	 *
	 * ⛔ Ohne das rechnete die Zeitleiste stur {@code t / dauer} — kein Zoom, kein
	 * waagerechter Schwenk. Beides konnte die ALTE Zeitleiste ueber ihre {@code Scale}
	 * (zoomAnchor), und der Umbau hat es verloren: eine Regression, kein fehlendes Feature
	 * (Nutzerbefund 2026-08-20: „das sind alles features die schon funktioniert haben").
	 *
	 * 0 als „unbenutzt", nicht -1: nachtraeglich hinzugefuegte Felder stehen nach einem
	 * Hotswap auf 0. */
	private float sichtVon;
	private float sichtBis;
	/** Angesammelte Verschiebung in Ticks, damit auch Zuege unter 1 Tick nicht verpuffen. */
	private float zugRest;
	/** 0 = kein Trimm, 1 = linke Kante, 2 = rechte Kante. */
	private int zugTrimmLinks;
	/** Mehrfachauswahl: alle mitgezogenen Clips. Leer = nur zugClip. */
	private final java.util.List<mchorse.bbs_mod.utils.clips.Clip> auswahl = new ArrayList<>();
	/** Zwischenablage — ein kopierter Clip überlebt den Wechsel der Auswahl. */
	private mchorse.bbs_mod.utils.clips.Clip ablage;
	private boolean klappeOffen;
	/** 0 = Dope Sheet (Kanaele), 1 = nur bewegte Kanaele, 2 = Kamera-Clips. */
	private int modus;

	private final List<ZeitleisteSpuren.Zeile> spuren = new ArrayList<>();
	/**
	 * Auf-/Zuklappzustand je Zeile. NICHT final und lazy angelegt: ein nachtraeglich
	 * hinzugefuegtes Instanzfeld steht nach einem Hotswap auf null.
	 */
	private java.util.Map<String, Boolean> offen;

	public static UIElement neu()
	{
		return new UIZeitleisteNano();
	}

	public UIZeitleisteNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		/* ⛔ Abspielkopf ZIEHEN, nicht nur anklicken. Die Scrub-Zone reagierte auf den
		 * Klick und danach auf nichts mehr — man konnte den Kopf setzen, aber nicht
		 * schrubben, was die Zeitleiste praktisch unbedienbar machte. */
		this.flaeche.beimZiehen(this::ziehen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
		this.h(240);
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	private String spurenBauen()
	{
		this.spuren.clear();

		UIFilmPanel panel = filmPanel();
		Film film = panel == null ? null : panel.getData();
		Replay gewaehlt = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		if (film == null)
		{
			return "leer";
		}

		if (this.offen == null)
		{
			this.offen = new java.util.HashMap<>();
		}

		this.spuren.addAll(ZeitleisteSpuren.bauen(film, gewaehlt, this.offen, this.modus == 1, this.modus == 2));

		StringBuilder kennung = new StringBuilder(film.getId());

		for (ZeitleisteSpuren.Zeile z : this.spuren)
		{
			kennung.append('|').append(z.ebene).append(z.name).append(z.zaehler).append(z.auf ? '+' : '-');
		}

		return kennung.append('@').append(panel.getCursor()).append('m').append(this.modus).toString();
	}

	@Override
	public void auffrischen()
	{
		String jetzt = this.spurenBauen();

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

	/**
	 * Zieh-Handler der Flaeche. Der Abspielkopf folgt der ABSOLUTEN Mausposition, nicht der
	 * Verschiebung — sonst driftet er bei schnellen Zuegen weg.
	 */
	private void ziehen(String zoneId, float d)
	{
		if (zoneId.startsWith("clip") || zoneId.startsWith("trimm"))
		{
			this.clipZiehen(d);

			return;
		}

		if (!"scrub".equals(zoneId))
		{
			return;
		}

		UIFilmPanel panel = this.filmPanel();

		if (panel == null || panel.getData() == null)
		{
			return;
		}

		float mx = this.flaeche.mausPositionX();

		if (mx < 0F)
		{
			return;
		}

		/* ⛔ Dieselbe Formel wie in zeichnen(): die Kanalspalte ist nicht fest, sondern
		 * haengt an der Flaechenbreite. Eine eigene Konstante hier waere eine zweite
		 * Wahrheit, die beim naechsten Layout-Wechsel auseinanderliefe. */
		float k = Math.max(1F, this.flaeche.skala());
		float b = this.flaeche.area.w * k;
		float spurX = Math.max(Math.min(210F * k, b * 0.52F), Math.min(b * 0.5F, 96F * k));
		float spurB = Math.max(1F, b - spurX);
		int dauer = Math.max(1, panel.getData().camera.calculateDuration());
		int tick = Math.round(this.von(dauer) + (mx - spurX) / spurB * this.spanne(dauer));

		panel.setCursor(Math.max(0, Math.min(dauer, tick)));
		this.stand = "";
		this.flaeche.neuMalen();
	}

	/** Linker Rand des sichtbaren Ausschnitts in Ticks. */
	private float von(int dauer)
	{
		return this.sichtBis > this.sichtVon ? this.sichtVon : 0F;
	}

	/** Breite des sichtbaren Ausschnitts in Ticks — nie 0, sonst teilt der Zeichner durch null. */
	private float spanne(int dauer)
	{
		return this.sichtBis > this.sichtVon ? this.sichtBis - this.sichtVon : Math.max(1, dauer);
	}

	/** Waagerecht schwenken (MMB) und zoomen (Rad) — beides in Ticks, nicht in Pixeln. */
	public void zeitSchwenken(float ticks, int dauer)
	{

		float breite = this.spanne(dauer);
		float neu = this.von(dauer) + ticks;

		/* ⛔ SPIELRAUM ueber den Filmrand hinaus — eine halbe Ausschnittsbreite nach jeder
		 * Seite. Ohne ihn klemmte der Ausschnitt bei voller Sicht auf 0..dauer fest, und
		 * Schwenken tat GAR NICHTS, sobald man herausgezoomt hatte (Nutzerbefund
		 * 2026-08-20: „wenn man reinzoomt geht das, wenn man rauszoomt nicht mehr").
		 * Rechnerisch war das richtig — es gab keinen Raum — und trotzdem falsch: Blender
		 * laesst die Zeitachse ueber ihr Ende hinausschieben, damit die Geste immer
		 * antwortet. Eine Grenze braucht es weiter, sonst verliert man den Film aus dem Bild. */
		float spielraum = breite * 0.5F;

		neu = Math.max(-spielraum, Math.min(Math.max(0F, dauer - breite) + spielraum, neu));

		this.sichtVon = neu;
		this.sichtBis = neu + breite;
		this.stand = "";
		this.flaeche.neuMalen();
	}

	public void zeitZoomen(float faktor, int dauer)
	{
		float mitte = this.von(dauer) + this.spanne(dauer) / 2F;
		float breite = Math.max(4F, Math.min(dauer, this.spanne(dauer) * faktor));

		this.sichtVon = Math.max(0F, mitte - breite / 2F);
		this.sichtBis = this.sichtVon + breite;
		this.stand = "";
		this.flaeche.neuMalen();
	}

	/**
	 * ⛔ NUR die Kopfzeile. Alles darunter — Spuren, Clips, Keyframes, Lineal — zeichnet
	 * {@code UIClips}, das dafuer 89 Methoden mitbringt (Auswahl, Kollisionen, Einrasten,
	 * Ebenen). Der Neubau hatte versucht, das nachzubauen, und 88 davon verloren.
	 *
	 * <p>Diese Flaeche ist damit reine Zier: Editor-Typ, Transport, Marker/Key, Loop-Ring.
	 * Was sie ausloest, geht an dieselben Stellen wie vorher.</p>
	 */
	/**
	 * Kanalspalte links — die Namen zu den Ebenen, die UIClips daneben zeichnet.
	 *
	 * <p>Sie gehoert hierher und nicht in UIClips: dort waere sie eine zweite Layout-Logik in
	 * einer Klasse, die schon 89 Methoden traegt. Hier ist sie reine Zier ueber einer Flaeche,
	 * die ihre Zeilenhoehe selbst kennt.</p>
	 */
	private void kanalspalte(long vg, NanoFlaeche f, int b, int h, float k)
	{
		UIFilmPanel oben_panel = filmPanel();

		/* ⛔ Im REPLAY-ZWEIG nicht zeichnen. Dort fuehrt `UIKeyframeDopeSheet` seine eigenen
		 * Kanalnamen (transform, pose, …) mit eigenem Spaltengrund — diese Spalte lag
		 * darueber und stellte ihren Kopf „CHANNELS" (abgeschnitten „CH") ueber fremde
		 * Zeilen. Zwei Kanalspalten uebereinander, eine davon leer. */
		if (oben_panel != null && oben_panel.replayEditor != null
			&& oben_panel.replayEditor.isVisible())
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

		UIFilmPanel panel = filmPanel();

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
		UIClips spuren = panel.cameraEditor.clips;
		int ebenen = spuren.layers;

		/* Wie viele Ebenen tragen ueberhaupt Clips? Danach richtet sich die Nummerierung. */
		int belegteEbenen = 0;

		for (int e = 0; e < ebenen; e++)
		{
			for (mchorse.bbs_mod.utils.clips.Clip c : panel.getData().camera.get())
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

			for (mchorse.bbs_mod.utils.clips.Clip c : panel.getData().camera.get())
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
			float y = (spuren.toLayerY(e) - this.area.y) * k;

			if (y < oben + linealH || y + zeileH > h) continue;

			if (n > 0)
			{
				NanoSymbole.symbol(vg, "kamera", 12F * k, y + zeileH / 2F, NanoDesign.GRUEN, k * 0.7F);
				NanoStifte.textInBox(vg, NanoDesign.SANS, 10F * k, 22F * k, y + zeileH / 2F,
					NanoDesign.GRUEN, belegteEbenen == 1 ? "Camera" : "Camera " + belegt);
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

	/** Aufgeklappte Editor-Liste. Gibt ihre Hoehe in GUI-Pixeln zurueck, 0 wenn zu.
	 *
	 * ⛔ Sie MUSS in beiden Zeichenwegen laufen. Im Rahmen-Modus stand sie hinter dem
	 * Frueh-`return`, also schaltete der Klick zwar `klappeOffen` um, aber es erschien
	 * nichts — der Nutzer sah einen toten Knopf. */
	private float klappe(long vg, NanoFlaeche f, float k)
	{
		if (!this.klappeOffen)
		{
			return 0F;
		}

		String[][] eintraege = {
			{"dopesheet", "Dope Sheet"},
			{"kurve", "Animated only"},
			{"kamera", "Camera clips"},
		};

		float hoehe = NanoBausteine.editorKlappe(vg, f, 6F * k, NanoDesign.H_KOPF * k + 2F * k, k,
			eintraege, this.modus, (wahl) ->
		{
			this.modus = wahl;
			this.klappeOffen = false;
			this.stand = "";
			this.auffrischen();
			this.flaeche.neuMalen();
		});

		return hoehe / k + 4F;
	}

	/**
	 * Kanal-Filter im Kopf — nur im Replay-Zweig, wie Blenders Dope-Sheet-Filter.
	 *
	 * <p>⛔ Sie sassen in einer vertikalen Icon-Spalte links neben den Kanalnamen: BBS-
	 * Handschrift, und das letzte Stueck, das dem Replay-Zweig die alte Haut ansah. Blender
	 * setzt Filter in den Kopf neben den Anzeigemodus.</p>
	 *
	 * <p>Verschoben wird nur die DARSTELLUNG. Auswahl, Spurenlogik und die Tastenkuerzel
	 * (REPLAYS_TAB_1..4) bleiben in {@code UIReplaysEditor} und werden ueber
	 * {@code filterWaehlen}/{@code aktiverFilter} angesteuert — nicht nachgebaut. Genau der
	 * Fehler, der beim Clip-Bereich 88 von 89 Logikmethoden gekostet hat.</p>
	 *
	 * @return die neue linke Kante, damit der Aufrufer weiterzeichnen kann
	 */
	private float kanalfilter(long vg, NanoFlaeche f, float kx, float grenze, float k)
	{
		UIFilmPanel panel = filmPanel();

		if (panel == null || panel.replayEditor == null || !panel.replayEditor.isVisible())
		{
			return kx;
		}

		final UIReplaysEditor editor = panel.replayEditor;
		String aktiv = editor.aktiverFilter();

		/* Reihenfolge wie die alte Spalte: die vier Kategorien, dann die zwei Umschalter. */
		String[][] filter = {
			{"player", "replay", "Player channels"},
			{"model", "wuerfel", "Model channels"},
			{"pose", "knochen", "Pose channels"},
			{"physics", "funken", "Physics channels"},
			{"all", "outliner", "All tracks"},
			{"actions", "play", "Action timeline"},
		};

		float b = 22F * k;
		float y = (NanoDesign.H_KOPF - 20F) * k / 2F;

		for (String[] e : filter)
		{
			if (kx + b > grenze - 6F * k)
			{
				break;
			}

			boolean an = e[0].equals(aktiv);
			boolean ueber = f.istHover("kf" + e[0]);

			if (an)
			{
				NanoStifte.widget(vg, kx, y, b, 20F * k, NanoDesign.R_WIDGET * k, NanoDesign.BLAU, k);
			}
			else if (ueber)
			{
				NanoStifte.widget(vg, kx, y, b, 20F * k, NanoDesign.R_WIDGET * k, NanoDesign.WIDGET, k);
			}

			NanoSymbole.symbol(vg, e[1], kx + b / 2F, NanoDesign.H_KOPF * k / 2F,
				an ? 0xFFFFFF : NanoDesign.TXT_DIM, k * 0.8F);

			final String id = e[0];

			f.zone("kf" + id, kx, y, b, 20F * k, () ->
			{
				editor.filterWaehlen(id);
				this.stand = "";
				this.flaeche.neuMalen();
			}, e[2], "");

			kx += b + 2F * k;
		}

		return kx;
	}

	private boolean nurKopf()
	{
		return true;
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		/* ⛔ Handler HIER sichern, nicht nur im Konstruktor. Der laeuft beim Hotswap nicht
		 * neu, und eine bestehende Flaeche stand danach ohne Zieh-Handler da: Abspielkopf
		 * und Clips liessen sich nicht mehr ziehen, obwohl der Code stimmte. Gemessen
		 * 2026-08-20: zieheHandler == null bei laufender Instanz. */
		/* ⛔ OHNE Flag, bei jedem Bild. Ein `if (!verdrahtet)` verhinderte nach einem Hotswap
		 * genau das, wofuer es gedacht war: die NEUEN Handler wurden nie gesetzt, weil das
		 * Flag noch stand. Drei Zuweisungen je Bild kosten nichts — eine Absicherung, die am
		 * Hotswap scheitert, kostet eine Stunde Fehlersuche. */
		{
			f.beimZiehen(this::ziehen);

			/* MMB schwenkt die ZEIT, das Rad zoomt sie — beides konnte die alte Zeitleiste
			 * ueber ihre Scale, die neue bis 2026-08-20 nicht. */
			f.beimSchwenken((dx, breite) ->
			{
				UIFilmPanel p2 = filmPanel();

				if (p2 == null || p2.getData() == null)
				{
					return;
				}

				int d = Math.max(1, p2.getData().camera.calculateDuration());
				float k2 = Math.max(1F, this.flaeche.skala());
				float sX = Math.max(Math.min(210F * k2, breite * 0.52F),
					Math.min(breite * 0.5F, 96F * k2));

				this.zeitSchwenken(dx / Math.max(1F, breite - sX) * this.spanne(d), d);
			});
			/* Rechtsklick auf einen Clip: Kontextmenue wie in der alten Zeitleiste. */
			f.beimRechtsklick((zone, punkt) ->
			{
				if (zone.startsWith("clip") || zone.startsWith("trimm"))
				{
					this.oeffneClipMenue();
				}
			});
			f.beimZoomen((faktor) ->
			{
				UIFilmPanel p2 = filmPanel();

				if (p2 != null && p2.getData() != null)
				{
					this.zeitZoomen(faktor,
						Math.max(1, p2.getData().camera.calculateDuration()));
				}
			});

		}

		final float k = Math.max(1F, f.skala());

		if (this.spuren.isEmpty())
		{
			this.spurenBauen();
		}

		/* ⛔ NUR Kopfzeile und Kanalspalte fuellen, nicht die ganze Flaeche. Diese Flaeche
		 * liegt UEBER UIClips (sie wird spaeter eingehaengt); ein Vollflaechen-Grund uebermalte
		 * Lineal und Clips vollstaendig — im Bild blieb rechts der CHANNELS-Spalte nichts. */
		if (this.nurKopf())
		{
			NanoStifte.flaeche(vg, 0, 0, b, NanoDesign.H_KOPF * k, NanoDesign.R_AREA * k,
				NanoDesign.REGION);
		}
		else
		{
			NanoStifte.flaeche(vg, 0, 0, b, h, NanoDesign.R_AREA * k, NanoDesign.REGION);
		}

		float nachKopf = NanoBausteine.kopfzeile(vg, b, k, "dopesheet");

		f.zone("etyp", 8F * k, (NanoDesign.H_KOPF - 22F) * k / 2F,
			37F * k, 22F * k, () ->
		{
			this.klappeOffen = !this.klappeOffen;
			this.flaeche.neuMalen();
		});

		UIFilmPanel panel = filmPanel();
		Film film = panel == null ? null : panel.getData();
		int cursor = panel == null ? 0 : panel.getCursor();
		int dauer = film == null ? 1 : Math.max(1, film.camera.calculateDuration());

		/* Vollbild ganz rechts — Blenders Ctrl+Space fuer diese Flaeche. */
		float vollB = 22F * k;
		float vollY = (NanoDesign.H_KOPF * k - 20F * k) / 2F;
		float vollX = b - vollB - 6F * k;
		boolean max = panel != null && panel.istMaximiert();

		if (max)
		{
			NanoStifte.widget(vg, vollX, vollY, vollB, 20F * k, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("voll"))
		{
			NanoStifte.widget(vg, vollX, vollY, vollB, 20F * k, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, "voll", vollX + vollB / 2F, vollY + 10F * k,
			max ? 0xFFFFFF : NanoDesign.TXT_DIM, k * 0.85F);

		if (panel != null)
		{
			final UIFilmPanel pf = panel;

			f.zone("voll", vollX, vollY, vollB, 20F * k, () -> pf.flaecheMaximieren("main"));
		}

		/* Zeitanzeige hat Vorrang; der Titel erscheint nur, wenn er danach noch passt. */
		String zeit = cursor + " / " + dauer + "   " + String.format("%.1f s", cursor / 20F);
		float zb = NanoDesign.breite(zeit, 11F * k);
		float zx = vollX - zb - 10F * k;

		NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, zx,
			NanoDesign.H_KOPF * k / 2F, NanoDesign.TXT, zeit);

		/* ⛔ TRANSPORT MITTIG. Vorher hing die Gruppe rechts an der Zeitanzeige und liess
		 * zwischen „Marker" und den Knoepfen ein breites Loch — der Nutzer hat genau dieses
		 * Loch mit einem Pfeil markiert. Die Gruppe baut weiterhin von rechts nach links
		 * auf; verschoben wird nur ihre rechte Kante, und zwar so, dass ihre Mitte auf der
		 * Mitte der Kopfzeile liegt. Reicht der Platz nicht (schmale Flaeche), bleibt sie
		 * rechts stehen, statt in den Titel zu laufen. */
		float transportB = 6F * 22F * k + 6F * k;
		float mittig = b / 2F + transportB / 2F;
		float transportRechts = Math.min(zx - 8F * k, Math.max(mittig, transportB + 8F * k));

		float nachTransport = this.transport(vg, f, transportRechts, k, panel, cursor, dauer);

		nachTransport = this.schleife(vg, f, nachTransport, k, panel, cursor);
		nachTransport = this.markerKnopf(vg, f, nachTransport, k, film, cursor);

		/* Titel als Wähler, danach die Menuezeile — wie im Entwurf:
		 * [etyp] [Dope Sheet ⌄] Marker  Key */
		float titelPlatz = nachTransport - nachKopf - 10F * k;
		float kx = nachKopf;

		if (titelPlatz > 40F * k)
		{
			String[] modi = {"Dope Sheet", "Animated only", "Camera clips"};
			String titel = modi[Math.min(this.modus, modi.length - 1)];
			float tw = NanoDesign.breite(titel, 11F * k) + 26F * k;

			NanoStifte.widget(vg, kx, (NanoDesign.H_KOPF - 22F) * k / 2F, tw, 22F * k,
				NanoDesign.R_WIDGET * k, NanoDesign.WIDGET, k);
			NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, kx + 9F * k,
				NanoDesign.H_KOPF * k / 2F, NanoDesign.TXT, titel);
			NanoSymbole.pfeilAb(vg, kx + tw - 9F * k, NanoDesign.H_KOPF * k / 2F,
				NanoDesign.TXT_DIM, k * 0.8F);

			f.zone("titelklappe", kx, (NanoDesign.H_KOPF - 22F) * k / 2F, tw, 22F * k, () ->
			{
				this.klappeOffen = !this.klappeOffen;
				this.flaeche.neuMalen();
			});

			kx += tw + 10F * k;

			/* Menuezeile: Marker setzt/entfernt am Cursor.
			 *
			 * ⛔ „Key" ist raus (Nutzeransage 2026-08-21: „der bringt nicht wirklich etwas").
			 * Die Funktion dahinter lebt weiter — `oeffneKeyMenue` bleibt im Baum und haengt
			 * an der Taste; nur der Platz im Kopf ist frei. */
			for (String menue : new String[] {"Marker"})
			{
				float mw = NanoDesign.breite(menue, 11F * k) + 14F * k;

				if (kx + mw > nachTransport - 6F * k)
				{
					break;
				}

				boolean ueber = f.istHover("menue" + menue);

				if (ueber)
				{
					NanoStifte.flaeche(vg, kx, 4F * k, mw, NanoDesign.H_KOPF * k - 8F * k,
						4F * k, NanoDesign.WIDGET);
				}

				NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, kx + 7F * k,
					NanoDesign.H_KOPF * k / 2F,
					ueber ? NanoDesign.TXT : NanoDesign.TXT_DIM, menue);

				final String name = menue;
				final int cursorF = cursor;
				final Film filmF = film;

				f.zone("menue" + menue, kx, 0, mw, NanoDesign.H_KOPF * k, () ->
				{
					if ("Marker".equals(name) && filmF != null)
					{
						mchorse.bbs_mod.film.markers.Marker vorhanden = filmF.markers.bei(cursorF, 0);

						if (vorhanden != null)
						{
							filmF.markers.remove(vorhanden);
						}
						else
						{
							filmF.markers.setzeBei(cursorF);
						}
					}
					else if ("Key".equals(name))
					{
						this.oeffneKeyMenue(cursorF);
					}

					this.stand = "";
					this.flaeche.neuMalen();
				});

				kx += mw;
			}

			kx = this.kanalfilter(vg, f, kx + 8F * k, nachTransport, k);
		}

		/* ⛔ HIER ENDET diese Flaeche. Alles ab `oben` — Kanalspalte, Lineal, Spuren, Clips,
		 * Keyframes — zeichnet ab jetzt UIClips darunter, mit seinen 89 Methoden fuer Auswahl,
		 * Kollisionen, Einrasten und Ebenen. Der Neubau hatte das nachgebaut und 88 davon
		 * verloren; jede Nutzermeldung seither war ein Stueck dieser Logik. */
		if (this.nurKopf())
		{
			/* ⛔ Eingaben NUR im Kopf annehmen. Diese Flaeche hat volle Hoehe (sie traegt auch
			 * die Kanalspalte), liegt aber ueber UIClips — ohne diese Grenze schluckte sie
			 * dessen komplette Bedienung: Rechtsklick, Mittelklick, Clip-Griffe, alles kam
			 * nicht mehr an. Der Nutzer meldete „die regressions von davor sind back".
			 *
			 * Die Kanalspalte bleibt bewusst DRAUSSEN: sie ist Beschriftung, und ein Klick
			 * darauf soll die Spur darunter treffen. */
			this.kanalspalte(vg, f, b, h, k);

			/* Die Klappe ragt unter den Kopf — das Eingabefeld muss sie einschliessen,
			 * sonst zeichnet sie sich und nimmt trotzdem keinen Klick an. */
			f.eingabeFeld(0F, 0F, this.area.w, NanoDesign.H_KOPF + this.klappe(vg, f, k));
			NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());

			return;
		}

		float oben     = NanoDesign.H_KOPF * k;
		/* Entwurfsmass .chan{width:210px}; bei schmaler Flaeche auf die halbe Breite gedeckelt,
		 * sonst frisst die Kanalspalte die Spuren. */
		float kanalB   = Math.max(Math.min(210F * k, b * 0.52F), Math.min(b * 0.5F, 96F * k));
		float linealH  = 20F * k;
		float zeileH   = 18F * k;

		/* Kanal-Spalte */
		NanoStifte.flaeche(vg, 0, oben, kanalB, h - oben, 0F, 0x191919);
		NanoStifte.text(vg, NanoDesign.SANS, 9.5F * k, 8F * k, oben + 13F * k,
			NanoDesign.TXT_OFF, "CHANNELS", null);
		/* Spurenflaeche */
		NanoStifte.flaeche(vg, kanalB, oben, b - kanalB, h - oben, 0F, NanoDesign.EDITOR);

		/* Lineal */
		NanoStifte.flaeche(vg, kanalB, oben, b - kanalB, linealH, 0F, 0x151515);

		float spurX = kanalB + 4F * k;
		float spurB = b - spurX - 6F * k;

		int schritt = dauer <= 40 ? 5 : dauer <= 120 ? 20 : 50;

		for (int t = 0; t <= dauer; t += schritt)
		{
			float tx = spurX + spurB * ((t - this.von(dauer)) / this.spanne(dauer));

			NanoStifte.linie(vg, tx, oben + linealH - 4F * k, tx, oben + linealH, 1F * k, 0x3A3A3A);
			NanoStifte.text(vg, NanoDesign.SANS, 9F * k, tx + 2F * k, oben + 10F * k,
				NanoDesign.TXT_DIM, String.valueOf(t), null);
		}

		/* Merkstellen unten am Lineal: Dreieck plus Beschriftung, wie im Entwurf. */
		if (film != null)
		{
			for (mchorse.bbs_mod.film.markers.Marker m : film.markers.getList())
			{
				float mx = spurX + spurB * ((m.tick.get() - this.von(dauer)) / this.spanne(dauer));

				NanoStifte.dreieck(vg, mx - 3.5F * k, oben + linealH - 5F * k,
					mx + 3.5F * k, oben + linealH - 5F * k, mx, oben + linealH, 0xB9B9B9);

				String beschriftung = m.label.get().isEmpty()
					? String.valueOf(m.tick.get()) : m.label.get();

				NanoStifte.text(vg, NanoDesign.SANS, 8.5F * k, mx + 5F * k,
					oben + linealH - 1F * k, NanoDesign.TXT_DIM, beschriftung, null);
			}
		}

		if (this.spuren.isEmpty())
		{
			NanoStifte.text(vg, NanoDesign.SANS, 11F * k, 9F * k, oben + linealH + 20F * k,
				NanoDesign.TXT_OFF, "No replay selected", null);
			return;
		}

		float y = oben + linealH;
		java.util.List<Object[]> clipZonen = new ArrayList<>();
		java.util.List<Object[]> trimmZonen = new ArrayList<>();
		java.util.List<Object[]> klappZonen = new ArrayList<>();

		/* Rollen: wie viele Zeilen passen, wie viele gibt es? Die Flaeche kennt nur die Grenze. */
		int passen = Math.max(1, (int) ((h - oben - linealH) / zeileH));
		int ersteZeile = Math.min((int) f.rollVersatz(), Math.max(0, this.spuren.size() - passen));

		f.rollGrenze(Math.max(0, this.spuren.size() - passen));

		for (int i = ersteZeile; i < this.spuren.size(); i++)
		{
			ZeitleisteSpuren.Zeile s = this.spuren.get(i);

			if (y + zeileH > h)
			{
				break;
			}

			boolean sel = i == this.gewaehlteSpur;
			boolean kopfzeile = s.ebene == 0;

			if (sel)
			{
				NanoStifte.flaecheArgb(vg, 0, y, b, zeileH, 0F, NanoDesign.A_AUSWAHL);
			}
			else if ((i - ersteZeile) % 2 == 1)
			{
				NanoStifte.flaeche(vg, kanalB, y, b - kanalB, zeileH, 0F, 0x1B1B1B);
			}

			/* Einrueckung nach Ebene: Replay · Gruppe · Einzelkanal */
			float tx = 8F * k + s.ebene * 14F * k;

			if (kopfzeile && s.replay != null)
			{
				NanoStifte.flaeche(vg, 0, y, 3F * k, zeileH, 0F, NanoDesign.GRUEN);
			}

			/* Klapppfeil, wo es etwas zu klappen gibt */
			if (!s.klappe.isEmpty())
			{
				if (s.auf)
				{
					NanoSymbole.pfeilAb(vg, tx + 4F * k, y + zeileH / 2F, NanoDesign.TXT_DIM, k * 0.75F);
				}
				else
				{
					NanoSymbole.pfeilRechts(vg, tx + 4F * k, y + zeileH / 2F, NanoDesign.TXT_DIM, k * 0.75F);
				}

				/* Gemerkt, nicht sofort gemeldet: die Spurzone deckt die ganze Kanalspalte und
				 * kommt spaeter — sie wuerde den Klick sonst schlucken (treffe() nimmt die
				 * zuletzt gemeldete Zone). Dieselbe Falle wie bei den Clips. */
				klappZonen.add(new Object[] {s.klappe, s.auf, tx - 2F * k, y, 14F * k, zeileH});

				tx += 13F * k;
			}

			if (kopfzeile)
			{
				NanoSymbole.symbol(vg, s.replay == null ? "kamera" : "replay",
					tx + 6F * k, y + zeileH / 2F, NanoDesign.TXT_DIM, k * 0.85F);
				tx += 16F * k;
			}

			/* Zaehler zuerst vermessen — der Name wird danach gegen SEINE Kante gekuerzt.
			 * Vorher stand beides uebereinander, sobald der Name lang war. */
			float zaehlerB = s.zaehler.isEmpty() ? 0F : NanoDesign.breite(s.zaehler, 9.5F * k);
			/* Platz fuer das Auge freihalten. */
			float zaehlerRechts = kanalB - (kopfzeile ? 24F * k : 7F * k);
			float namePlatz = zaehlerRechts - zaehlerB - 8F * k - tx;

			NanoStifte.textInBox(vg, kopfzeile ? NanoDesign.BOLD : NanoDesign.SANS, 11F * k,
				tx, y + zeileH / 2F,
				sel ? NanoDesign.TXT_MAX : (kopfzeile ? NanoDesign.GRUEN : NanoDesign.TXT),
				NanoDesign.kuerzen(s.name, namePlatz, 11F * k));

			if (!s.zaehler.isEmpty())
			{
				NanoStifte.textInBox(vg, NanoDesign.SANS, 9.5F * k, zaehlerRechts - zaehlerB,
					y + zeileH / 2F,
					sel ? 0xB9CCE9 : NanoDesign.TXT_OFF, s.zaehler);
			}

			/* Kamera-Clips als Balken — Typ-Farbe, Titel, Auswahlkontur wie im Entwurf */
			if (s.clips != null)
			{
				for (int c = 0; c < s.clips.size(); c++)
				{
					mchorse.bbs_mod.utils.clips.Clip clip = s.clips.get(c);
					float cx = spurX + spurB * ((clip.tick.get() - this.von(dauer)) / this.spanne(dauer));
					float cb = Math.max(6F * k, spurB * (clip.duration.get() / this.spanne(dauer)));

					boolean gewaehltC = gewaehlterClip() == clip;

					NanoStifte.flaeche(vg, cx, y + 2F * k, cb, zeileH - 4F * k, 3F * k,
						!clip.enabled.get() ? 0x333333 : gewaehltC ? 0x4A6FA5 : 0x3E5C86);
					NanoStifte.rahmen(vg, cx, y + 2F * k, cb, zeileH - 4F * k, 3F * k, 1F * k,
						gewaehltC ? 0xE8E8E8 : 0x2B4265);

					/* Typ-Symbol vor dem Titel, wie im Entwurf. */
					String art = clip.getClass().getSimpleName().replace("Clip", "");
					String sym = clipSymbol(art);
					float tx3 = cx + 5F * k;

					if (!sym.isEmpty() && cb > 34F * k)
					{
						NanoSymbole.symbol(vg, sym, cx + 9F * k, y + zeileH / 2F, 0xDCE6F5, k * 0.62F);
						tx3 += 12F * k;
					}

					String titel = clip.title.get().isEmpty() ? art : clip.title.get();

					NanoStifte.textInBox(vg, NanoDesign.SANS, 9.5F * k, tx3,
						y + zeileH / 2F, gewaehltC ? 0xFFFFFF : 0xDCE6F5,
						NanoDesign.kuerzen(titel, cb - (tx3 - cx) - 4F * k, 9.5F * k));

					/* Trimm-Griffe in Bernstein an den Kanten des gewaehlten Clips. */
					if (gewaehltC && cb > 10F * k)
					{
						NanoStifte.flaeche(vg, cx + 1F * k, y + 3F * k, 2.5F * k, zeileH - 6F * k,
							1.5F * k, NanoDesign.BERNSTEIN);
						NanoStifte.flaeche(vg, cx + cb - 3.5F * k, y + 3F * k, 2.5F * k, zeileH - 6F * k,
							1.5F * k, NanoDesign.BERNSTEIN);
					}

					/* Waehlbar wie in der alten Zeitleiste. Gemerkt, nicht sofort gemeldet:
					 * die Scrub-Zone kommt spaeter und laege sonst darueber. */
					clipZonen.add(new Object[] {clip, cx, y + 2F * k, cb, zeileH - 4F * k});

					/* ⛔ Trimm-Zonen an den Kanten. Die Griffe wurden GEZEICHNET (Bernstein,
					 * siehe unten), reagierten aber auf nichts — eine Attrappe, die aussieht
					 * wie eine Funktion. Der Nutzer meldete genau das: „clips kann ich nicht
					 * verkuerzen oder verlaengern mit dem zieh symbol".
					 * 6 px breit, damit sie greifbar sind, ohne den Clip-Zug zu schlucken. */
					float griff = Math.min(6F * k, cb / 3F);

					trimmZonen.add(new Object[] {clip, cx, y + 2F * k, griff, zeileH - 4F * k,
						Boolean.TRUE});
					trimmZonen.add(new Object[] {clip, cx + cb - griff, y + 2F * k, griff,
						zeileH - 4F * k, Boolean.FALSE});
				}
			}

			/* Keyframes zeichnen */
			/* ⛔ ALLE Kanaele der Zeile, nicht nur s.kanal: der ist bei Gruppen mit mehreren
			 * Kanaelen null, und „Transform" (10) wie „Pose" blieben dadurch leer. */
			for (KeyframeChannel<?> kanal : s.kanaele)
			{
				for (Keyframe<?> kf : kanal.getKeyframes())
				{
					float tx2 = spurX + spurB * ((kf.getTick() - this.von(dauer)) / this.spanne(dauer));
					/* Der Keyframe AM Cursor ist der, den die Properties gerade zeigen —
					 * groesser und bernstein, wie der gewaehlte im Entwurf. */
					boolean amCursor = Math.abs(kf.getTick() - cursor) < 1;
					float r = (amCursor ? 4.4F : 3.4F) * k;
					int farbe = amCursor ? NanoDesign.BERNSTEIN : 0xCFCFCF;

					NanoStifte.dreieck(vg, tx2, y + zeileH / 2F - r, tx2 + r, y + zeileH / 2F,
						tx2, y + zeileH / 2F + r, farbe);
					NanoStifte.dreieck(vg, tx2, y + zeileH / 2F - r, tx2 - r, y + zeileH / 2F,
						tx2, y + zeileH / 2F + r, farbe);
				}
			}

			/* Auge je Spur: Gruppe schaltet das Replay, Kameraspur die Clips. */
			if (kopfzeile)
			{
				boolean sichtbar = s.replay == null || s.replay.enabled.get();

				NanoSymbole.symbol(vg, sichtbar ? "auge" : "auge-aus", kanalB - 12F * k,
					y + zeileH / 2F, sichtbar ? NanoDesign.TXT_DIM : NanoDesign.TXT_OFF, k * 0.85F);

				if (s.replay != null)
				{
					final Replay r = s.replay;

					f.zone("auge" + i, kanalB - 22F * k, y, 20F * k, zeileH, () ->
					{
						r.enabled.set(!r.enabled.get());
						this.stand = "";
						this.flaeche.neuMalen();
					});
				}
			}

			final int idx = i;
			f.zone("spur" + i, 0, y, kanalB, zeileH, () ->
			{
				this.gewaehlteSpur = this.gewaehlteSpur == idx ? -1 : idx;
				this.flaeche.neuMalen();
			});

			y += zeileH;
		}

		/* Scrubbing im LINEAL-Band — wie in Blender: dort setzt der Klick den Abspielkopf.
		   Zuletzt registriert, damit sie ueber allem liegt (treffe() nimmt die letzte Zone). */
		if (panel != null)
		{
			final int dauerF = dauer;
			final float spurXF = spurX;
			final float spurBF = spurB;
			final UIFilmPanel panelF = panel;

			f.zone("scrub", kanalB, oben, b - kanalB, h - oben, () ->
			{
				float rel = (f.letzterKlickX() - spurXF) / Math.max(1F, spurBF);
				int tick = Math.round(rel * dauerF);

				panelF.setCursor(Math.max(0, Math.min(dauerF, tick)));
				this.stand = "";
				this.flaeche.neuMalen();
			});
		}

		/* Clips ZULETZT: ein Klick auf einen Balken waehlt ihn, daneben scrubbt er weiter. */
		for (int c = 0; c < clipZonen.size(); c++)
		{
			Object[] z = clipZonen.get(c);
			final mchorse.bbs_mod.utils.clips.Clip gewaehlt = (mchorse.bbs_mod.utils.clips.Clip) z[0];

			f.zone("clip" + c, (Float) z[1], (Float) z[2], (Float) z[3], (Float) z[4], () ->
			{
				UIFilmPanel pp = filmPanel();

				if (pp != null && pp.cameraEditor != null && pp.cameraEditor.clips != null)
				{
					pp.cameraEditor.clips.pickClip(gewaehlt);
				}

				/* Fuer das Ziehen merken — losgelassen wird er im Handler. */
				this.zugClip = gewaehlt;
				this.zugTrimmLinks = 0;
				this.zugRest = 0F;

				/* ⛔ MEHRFACHAUSWAHL mit Shift/Strg. Die alte Zeitleiste konnte mehrere Clips
				 * zusammen greifen (`grabbedClips`); ohne das muss man jeden einzeln schieben
				 * und verliert dabei ihre Abstaende. */
				if (mchorse.bbs_mod.graphics.window.Window.isShiftPressed()
					|| mchorse.bbs_mod.graphics.window.Window.isCtrlPressed())
				{
					if (!this.auswahl.remove(gewaehlt))
					{
						this.auswahl.add(gewaehlt);
					}
				}
				else if (!this.auswahl.contains(gewaehlt))
				{
					this.auswahl.clear();
				}
				this.stand = "";
				this.flaeche.neuMalen();
			});
		}

		/* Trimm-Griffe NACH den Clips: treffe() nimmt die zuletzt registrierte Zone, und der
		 * Griff liegt IM Clip — sonst schluckt der Clip-Zug ihn. */
		for (int t = 0; t < trimmZonen.size(); t++)
		{
			Object[] z = trimmZonen.get(t);
			final mchorse.bbs_mod.utils.clips.Clip clip = (mchorse.bbs_mod.utils.clips.Clip) z[0];
			final boolean links = (Boolean) z[5];

			f.zone("trimm" + t, (Float) z[1], (Float) z[2], (Float) z[3], (Float) z[4], () ->
			{
				this.zugClip = clip;
				this.zugTrimmLinks = links ? 1 : 2;
				this.zugRest = 0F;
			});
		}

		/* Klapppfeile ZULETZT — sie liegen ueber der Spurzeile, nicht darunter. */
		for (int c = 0; c < klappZonen.size(); c++)
		{
			Object[] z = klappZonen.get(c);
			final String schluessel = (String) z[0];
			final boolean jetzt = (Boolean) z[1];

			f.zone("klapp" + c, (Float) z[2], (Float) z[3], (Float) z[4], (Float) z[5], () ->
			{
				this.offen.put(schluessel, !jetzt);
				this.stand = "";
				this.flaeche.neuMalen();
			});
		}

		/* Aufgeklappte Editor-Liste — ueber allem */
		if (this.klappe(vg, f, k) > 0F)
		{
			return;
		}

		/* Abspielkopf */
		float px = spurX + spurB * ((cursor - this.von(dauer)) / this.spanne(dauer));

		NanoStifte.flaeche(vg, px - 1F * k, oben, 2F * k, h - oben, 0F, 0x5FA3E7);

		String marke = String.valueOf(cursor);
		float mb = NanoDesign.breite(marke, 9.5F * k) + 10F * k;

		NanoStifte.widget(vg, px - mb / 2F, oben + 2F * k, mb, 13F * k, 3F * k, NanoDesign.BLAU, k);
		NanoStifte.textInBox(vg, NanoDesign.BOLD, 9.5F * k, px - mb / 2F, oben + 11.5F * k,
			0xFFFFFF, marke);
	
		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}
	/**
	 * Abspielsteuerung im Flaechenkopf, von rechts nach links gesetzt: Anfang, Schritt zurueck,
	 * Play/Pause, Schritt vor, Ende, Aufnahme. Liefert die linke Kante zurueck, damit der Titel
	 * davor endet. Alles wirkt auf den echten Cursor des Film-Editors.
	 */
	private float transport(long vg, NanoFlaeche f, float rechts, float k,
		UIFilmPanel panel, int cursor, int dauer)
	{
		if (panel == null)
		{
			return rechts;
		}

		boolean laeuft = panel.isRunning();
		float bw = 22F * k;
		float bh = 20F * k;
		float y = (NanoDesign.H_KOPF * k - bh) / 2F;
		float x = rechts;

		/* Aufnahme ganz rechts — folgenreich, deshalb abgesetzt und rot */
		x -= bw;
		this.transportKnopf(vg, f, "tr-rec", x, y, bw, bh, k, "rec", 0xE58F89, false,
			() -> panel.preview.recordReplay.clickItself(), "Replay aufnehmen", kuerzel("record"));

		x -= 6F * k + bw;
		this.transportKnopf(vg, f, "tr-ende", x, y, bw, bh, k, "ende", NanoDesign.TXT, false,
			() -> panel.setCursor(dauer), "Zum Ende springen", "");

		x -= bw;
		this.transportKnopf(vg, f, "tr-vor", x, y, bw, bh, k, "schritt-vor", NanoDesign.TXT, false,
			() -> panel.setCursor(Math.min(dauer, cursor + 1)), "Next frame", kuerzel("next_frame"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-play", x, y, bw, bh, k, laeuft ? "pause" : "play",
			laeuft ? 0xFFFFFF : NanoDesign.TXT, laeuft, panel::togglePlayback,
			laeuft ? "Wiedergabe anhalten" : "Wiedergabe starten", kuerzel("plause"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-zurueck", x, y, bw, bh, k, "schritt-zurueck", NanoDesign.TXT, false,
			() -> panel.setCursor(Math.max(0, cursor - 1)), "Previous frame", kuerzel("prev_frame"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-anfang", x, y, bw, bh, k, "anfang", NanoDesign.TXT, false,
			() -> panel.setCursor(0), "Zum Anfang springen", "");

		return x - 8F * k;
	}

	private void transportKnopf(long vg, NanoFlaeche f, String id, float x, float y,
		float bw, float bh, float k, String symbol, int farbe, boolean an, Runnable tat)
	{
		this.transportKnopf(vg, f, id, x, y, bw, bh, k, symbol, farbe, an, tat, "", "");
	}

	private void transportKnopf(long vg, NanoFlaeche f, String id, float x, float y,
		float bw, float bh, float k, String symbol, int farbe, boolean an, Runnable tat,
		String hinweis, String taste)
	{
		if (an)
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover(id))
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, symbol, x + bw / 2F, y + bh / 2F, farbe, k * 0.85F);
		f.zone(id, x, y, bw, bh, () ->
		{
			tat.run();
			this.flaeche.neuMalen();
		}, hinweis, taste);
	}

	/**
	 * Kuerzel eines registrierten Keybinds fuer den Tooltip. BBS traegt 221 davon mit Kategorie
	 * und Label — der Tooltip liest sie, statt sie zu wiederholen. Unbekannt = leer.
	 */
	private static String kuerzel(String id)
	{
		try
		{
			for (java.lang.reflect.Field feld : mchorse.bbs_mod.ui.Keys.class.getFields())
			{
				Object wert = feld.get(null);

				if (wert instanceof mchorse.bbs_mod.ui.utils.keys.KeyCombo kc && id.equals(kc.id))
				{
					return kc.getKeyCombo();
				}
			}
		}
		catch (Exception e)
		{
			/* Kein Kuerzel ist kein Fehler — der Tooltip zeigt dann nur die Beschreibung. */
		}

		return "";
	}

	private static boolean replaySichtbar()
	{
		UIFilmPanel panel = filmPanel();
		Replay replay = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		return replay == null || replay.enabled.get();
	}

	private static void replaySichtbarkeitUmschalten()
	{
		UIFilmPanel panel = filmPanel();
		Replay replay = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		if (replay != null)
		{
			replay.enabled.set(!replay.enabled.get());
		}
	}
	/**
	 * Schleifenbereich setzen — links vom Transport. In/Out springen auf den Cursor, wie es
	 * die Tastenkuerzel {@code LOOPING_SET_MIN/MAX} schon tun; hier mit der Maus erreichbar.
	 */
	private float schleife(long vg, NanoFlaeche f, float rechts, float k, UIFilmPanel panel, int cursor)
	{
		if (panel == null || panel.cameraEditor == null || panel.cameraEditor.clips == null)
		{
			return rechts;
		}

		UIClips clips = panel.cameraEditor.clips;
		boolean an = clips.loopMin != clips.loopMax && clips.loopMax != 0;
		float bw = 22F * k;
		float bh = 20F * k;
		float y = (NanoDesign.H_KOPF * k - bh) / 2F;
		float x = rechts - bw;

		if (an)
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("schleife"))
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, "schleife", x + bw / 2F, y + bh / 2F,
			an ? 0xFFFFFF : NanoDesign.TXT, k * 0.85F);

		f.zone("schleife", x, y, bw, bh, () ->
		{
			/* Drei Zustaende im Ring: Anfang setzen -> Ende setzen -> aufheben.
			 * Erkennbar an loopMax: 0 heisst „Anfang steht, Ende fehlt noch". */
			if (clips.loopMin != clips.loopMax && clips.loopMax != 0)
			{
				clips.loopMin = 0;
				clips.loopMax = 0;
			}
			else if (clips.loopMin != 0 && clips.loopMax == 0 && cursor > clips.loopMin)
			{
				clips.setLoopMax();
			}
			else
			{
				clips.setLoopMin();
				clips.loopMax = 0;
			}

			this.stand = "";
			this.flaeche.neuMalen();
		});

		return x - 8F * k;
	}
	/**
	 * Merkstelle am Cursor setzen oder entfernen. Blau, solange an dieser Stelle eine steht —
	 * derselbe Knopf ist damit Setzen und Loeschen, ohne zweites Bedienelement.
	 */
	private float markerKnopf(long vg, NanoFlaeche f, float rechts, float k, Film film, int cursor)
	{
		if (film == null)
		{
			return rechts;
		}

		boolean da = film.markers.bei(cursor, 0) != null;
		float bw = 22F * k;
		float bh = 20F * k;
		float y = (NanoDesign.H_KOPF * k - bh) / 2F;
		float x = rechts - bw;

		if (da)
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("marker"))
		{
			NanoStifte.widget(vg, x, y, bw, bh, 4F * k, NanoDesign.WIDGET, k);
		}

		/* Dreieck nach unten — dasselbe Zeichen wie am Lineal, damit der Bezug klar ist. */
		NanoStifte.dreieck(vg, x + bw / 2F - 4.5F * k, y + bh / 2F - 3.5F * k,
			x + bw / 2F + 4.5F * k, y + bh / 2F - 3.5F * k,
			x + bw / 2F, y + bh / 2F + 4.5F * k, da ? 0xFFFFFF : NanoDesign.TXT);

		f.zone("marker", x, y, bw, bh, () ->
		{
			mchorse.bbs_mod.film.markers.Marker vorhanden = film.markers.bei(cursor, 0);

			if (vorhanden != null)
			{
				film.markers.remove(vorhanden);
			}
			else
			{
				film.markers.setzeBei(cursor);
			}

			this.stand = "";
			this.flaeche.neuMalen();
		});

		return x - 8F * k;
	}
	/** Der im Kamera-Editor gewaehlte Clip, oder null. */
	private static mchorse.bbs_mod.utils.clips.Clip gewaehlterClip()
	{
		UIFilmPanel panel = filmPanel();

		return panel == null || panel.cameraEditor == null || panel.cameraEditor.clips == null
			? null : panel.cameraEditor.clips.getLastSelectedClip();
	}

	/** Symbol zur Clip-Art — die Namen stammen aus den echten Clip-Klassen von BBS. */
	private static String clipSymbol(String art)
	{
		return switch (art)
		{
			case "Keyframe" -> "rec";
			case "Path" -> "pfad";
			case "Translate", "Drag" -> "flug";
			case "Orbit", "Angle", "Look" -> "persp";
			case "Idle" -> "kamera";
			case "Shake", "DollyZoom" -> "funken";
			case "Audio" -> "ton";
			default -> "";
		};
	}
	/**
	 * Camera-Clip auf der Zeitachse verschieben.
	 *
	 * <p>⛔ Die Verschiebung wird in {@link #zugRest} ANGESAMMELT. Ein Pixel ist bei langen
	 * Filmen weniger als ein Tick; wer jeden Aufruf einzeln rundet, verschiebt gar nichts —
	 * der Clip klebte fest, obwohl der Zug ankam.</p>
	 *
	 * <p>Der Clip wird nicht unter 0 geschoben; ein negativer Tick waere fuer BBS ein
	 * ungueltiger Zustand, den erst der Export bemerkt.</p>
	 */
	private void clipZiehen(float d)
	{
		UIFilmPanel panel = filmPanel();

		if (this.zugClip == null || panel == null || panel.getData() == null)
		{
			return;
		}

		float k = Math.max(1F, this.flaeche.skala());
		float b = this.flaeche.area.w * k;
		float spurX = Math.max(Math.min(210F * k, b * 0.52F), Math.min(b * 0.5F, 96F * k));
		float spurB = Math.max(1F, b - spurX);
		int dauer = Math.max(1, panel.getData().camera.calculateDuration());

		this.zugRest += d / spurB * dauer;

		int ticks = (int) this.zugRest;

		if (ticks == 0)
		{
			return;
		}

		this.zugRest -= ticks;

		if (this.zugTrimmLinks == 1)
		{
			/* Linke Kante: Start UND Dauer gegenlaeufig, sonst wandert der Clip statt zu
			 * wachsen. Mindestens 1 Tick, sonst verschwindet er. */
			int neuTick = Math.max(0, this.zugClip.tick.get() + ticks);
			int diff = neuTick - this.zugClip.tick.get();
			int neuDauer = this.zugClip.duration.get() - diff;

			if (neuDauer >= 1)
			{
				this.zugClip.tick.set(neuTick);
				this.zugClip.duration.set(neuDauer);
			}
		}
		else if (this.zugTrimmLinks == 2)
		{
			this.zugClip.duration.set(Math.max(1, this.zugClip.duration.get() + ticks));
		}
		else
		{
			/* ⛔ EINRASTEN an den Kanten anderer Clips und am Abspielkopf — die alte
			 * Zeitleiste sammelte dafuer `snappingPoints`. Ohne das trifft man Anschluesse
			 * nie exakt, und im fertigen Film klafft ein Bild Luecke. */
			int ziel = Math.max(0, this.zugClip.tick.get() + ticks);
			int gerastet = this.einrasten(panel, ziel);
			int wirklich = gerastet - this.zugClip.tick.get();

			this.zugClip.tick.set(gerastet);

			/* Mitgewaehlte um denselben Betrag — sonst zerfaellt ihr Abstand. */
			for (mchorse.bbs_mod.utils.clips.Clip mit : this.auswahl)
			{
				if (mit != this.zugClip)
				{
					mit.tick.set(Math.max(0, mit.tick.get() + wirklich));
				}
			}
		}
		this.stand = "";
		this.flaeche.neuMalen();
	}

	/**
	 * Das „Key"-Menue: Keyframes setzen, loeschen, anspringen.
	 *
	 * <p>Vorher sprang „Key" nur zum naechsten Keyframe — setzen und loeschen gab es in der
	 * neuen Zeitleiste ueberhaupt nicht, was sie als Editor unbrauchbar machte
	 * (Nutzerbefund 2026-08-20: „kann keyframes nicht platzieren oder löschen").</p>
	 *
	 * <p>Gearbeitet wird auf der GEWAEHLTEN Spur — ohne Auswahl wuesste der Befehl nicht,
	 * welchen der bis zu 55 Kanaele er meint. Das Menue sagt das, statt stillschweigend
	 * nichts zu tun.</p>
	 */
	private void oeffneKeyMenue(int cursor)
	{
		ZeitleisteSpuren.Zeile spur = this.gewaehlteSpur >= 0
			&& this.gewaehlteSpur < this.spuren.size()
			? this.spuren.get(this.gewaehlteSpur) : null;

		this.getContext().replaceContextMenu((menu) ->
		{
			/* Ohne gewaehlte Zeile gibt es nichts anzubieten — ein Eintrag, der nicht weiss,
			 * auf welchen der bis zu 55 Kanaele er wirkt, waere eine Attrappe. */
			if (spur != null && !spur.kanaele.isEmpty())
			{
				menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.ADD,
					mchorse.bbs_mod.l10n.keys.IKey.raw("Insert keyframe at " + cursor),
					() -> this.keyframeSetzen(spur, cursor));

				if (this.keyframeBei(spur, cursor) != null)
				{
					menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.REMOVE,
						mchorse.bbs_mod.l10n.keys.IKey.raw("Delete keyframe at " + cursor),
						() -> this.keyframeLoeschen(spur, cursor));
				}
			}

			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.ARROW_RIGHT,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Jump to next keyframe"),
				() -> this.springeZumNaechstenKeyframe(cursor));
		});
	}

	/**
	 * Keyframe am Cursor setzen oder loeschen — der Tastenweg.
	 *
	 * <p>Arbeitet auf der gewaehlten Zeile. Ohne Auswahl passiert nichts: welchen von bis zu
	 * 55 Kanaelen sollte die Taste sonst meinen?</p>
	 */
	/**
	 * Naechster Rastpunkt zu {@code tick}, wenn einer nah genug liegt.
	 *
	 * <p>Kandidaten sind Anfang und Ende aller ANDEREN Clips sowie der Abspielkopf. Die
	 * Fangweite haengt am Zoom: bei weit herausgezoomter Achse sind zwei Ticks ein Pixel,
	 * und ein fester Wert in Ticks fienge dort alles oder nichts.</p>
	 */
	private int einrasten(UIFilmPanel panel, int tick)
	{
		float weite = Math.max(1F, this.spanne(Math.max(1,
			panel.getData().camera.calculateDuration())) / 40F);
		int besterAbstand = Integer.MAX_VALUE;
		int bester = tick;

		java.util.List<Integer> punkte = new ArrayList<>();

		punkte.add(panel.getCursor());

		for (mchorse.bbs_mod.utils.clips.Clip c : panel.getData().camera.get())
		{
			if (c == this.zugClip || this.auswahl.contains(c))
			{
				continue;
			}

			punkte.add(c.tick.get());
			punkte.add(c.tick.get() + c.duration.get());
		}

		for (int p : punkte)
		{
			int abstand = Math.abs(p - tick);

			if (abstand < besterAbstand && abstand <= weite)
			{
				besterAbstand = abstand;
				bester = p;
			}
		}

		return Math.max(0, bester);
	}

	/** Kontextmenue fuer den gewaehlten Camera-Clip — Rechtsklick, wie in der alten Zeitleiste. */
	private void oeffneClipMenue()
	{
		UIFilmPanel panel = filmPanel();
		mchorse.bbs_mod.utils.clips.Clip clip = this.zugClip;

		if (panel == null || clip == null)
		{
			return;
		}

		this.getContext().replaceContextMenu((menu) ->
		{
			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.MOVE_TO,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Move to cursor"), () ->
			{
				clip.tick.set(Math.max(0, panel.getCursor()));
				this.stand = "";
				this.flaeche.neuMalen();
			});
			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.DUPE,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Duplicate"), () ->
			{
				/* copy() ohne Argument — der Clip weiss selbst, wie er sich vervielfaeltigt. */
				mchorse.bbs_mod.utils.clips.Clip kopie = clip.copy();

				kopie.tick.set(clip.tick.get() + clip.duration.get());
				panel.getData().camera.get().add(kopie);
				this.stand = "";
				this.flaeche.neuMalen();
			});
			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.REMOVE,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Remove"), () ->
			{
				/* Mitgewaehlte gleich mit — sonst muesste man bei einer Auswahl von zehn
				 * Clips zehnmal dasselbe Menue oeffnen. */
				panel.getData().camera.get().remove(clip);

				for (mchorse.bbs_mod.utils.clips.Clip mit : new ArrayList<>(this.auswahl))
				{
					panel.getData().camera.get().remove(mit);
				}

				this.auswahl.clear();
				this.zugClip = null;
				this.stand = "";
				this.flaeche.neuMalen();
			});

			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.COPY,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Copy"), () -> this.ablage = clip.copy());

			if (this.ablage != null)
			{
				menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.PASTE,
					mchorse.bbs_mod.l10n.keys.IKey.raw("Paste at cursor"), () ->
				{
					mchorse.bbs_mod.utils.clips.Clip neu2 = this.ablage.copy();

					neu2.tick.set(Math.max(0, panel.getCursor()));
					panel.getData().camera.get().add(neu2);
					this.stand = "";
					this.flaeche.neuMalen();
				});
			}

			menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.MOVE_DOWN,
				mchorse.bbs_mod.l10n.keys.IKey.raw("Move to layer " + (Math.min(clip.layer.get() + 1, panel.cameraEditor.clips.hoechsteFremdeEbene(clip) + 1) + 1)),
				() ->
			{
				/* Ebene wechseln — die Zeitleiste zeigt jetzt eine Zeile je Ebene, also ist
				 * das der Weg, ueberlappende Clips auseinanderzuziehen. */
				clip.layer.set(Math.min(clip.layer.get() + 1, panel.cameraEditor.clips.hoechsteFremdeEbene(clip) + 1));
				this.stand = "";
				this.flaeche.neuMalen();
			});

			if (clip.layer.get() > 0)
			{
				menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.MOVE_UP,
					mchorse.bbs_mod.l10n.keys.IKey.raw("Move to layer " + clip.layer.get()),
					() ->
				{
					clip.layer.set(clip.layer.get() - 1);
					this.stand = "";
					this.flaeche.neuMalen();
				});
			}
		});
	}

	public void modusUmschalten(int ziel)
	{
		this.modus = this.modus == ziel ? 0 : ziel;
		this.stand = "";
		this.flaeche.neuMalen();
	}

	/**
	 * ⛔ OEFFENTLICH und am PANEL registriert, nicht hier. Tasten gehen in BBS an das
	 * fokussierte Element; die Zeitleiste hat den Fokus nur, solange man gerade in ihr
	 * geklickt hat. Gemessen 2026-08-20: I und Entf blieben wirkungslos, obwohl die
	 * Registrierung lief. Das Film-Panel haelt die uebrigen Editor-Tasten ohnehin.
	 */
	public void keyframeAmCursor(boolean setzen)
	{
		UIFilmPanel panel = filmPanel();

		if (panel == null || this.gewaehlteSpur < 0 || this.gewaehlteSpur >= this.spuren.size())
		{
			return;
		}

		ZeitleisteSpuren.Zeile spur = this.spuren.get(this.gewaehlteSpur);

		if (spur.kanaele.isEmpty())
		{
			return;
		}

		if (setzen)
		{
			this.keyframeSetzen(spur, panel.getCursor());
		}
		else
		{
			this.keyframeLoeschen(spur, panel.getCursor());
		}
	}

	/** Keyframe am Cursor in JEDEN Kanal der Zeile — eine Gruppe wird als Ganzes gekeyt. */
	private void keyframeSetzen(ZeitleisteSpuren.Zeile spur, int cursor)
	{
		for (KeyframeChannel<?> kanal : spur.kanaele)
		{
			/* ⛔ insertInterpolated, nicht insert(tick, wert): der Kanal rechnet den Wert AM
			 * Cursor selbst aus. Ein Keyframe darauf aendert nichts am Bild, haelt den
			 * Zustand aber fest — genau das erwartet man von „Insert". */
			kanal.insertInterpolated(cursor);
		}

		this.stand = "";
		this.flaeche.neuMalen();
	}

	private void keyframeLoeschen(ZeitleisteSpuren.Zeile spur, int cursor)
	{
		for (KeyframeChannel<?> kanal : spur.kanaele)
		{
			for (int i = kanal.getKeyframes().size() - 1; i >= 0; i--)
			{
				if (Math.abs(kanal.getKeyframes().get(i).getTick() - cursor) < 1)
				{
					kanal.remove(i);
				}
			}
		}

		this.stand = "";
		this.flaeche.neuMalen();
	}

	/** Erster Keyframe der Zeile am Cursor, oder null. */
	private Keyframe<?> keyframeBei(ZeitleisteSpuren.Zeile spur, int cursor)
	{
		for (KeyframeChannel<?> kanal : spur.kanaele)
		{
			for (Keyframe<?> kf : kanal.getKeyframes())
			{
				if (Math.abs(kf.getTick() - cursor) < 1)
				{
					return kf;
				}
			}
		}

		return null;
	}

	/** Zum naechsten Keyframe rechts vom Cursor springen — der „Key"-Eintrag im Kopfmenue. */
	private void springeZumNaechstenKeyframe(int cursor)
	{
		UIFilmPanel panel = filmPanel();

		if (panel == null)
		{
			return;
		}

		Film film = panel.getData();

		if (film == null)
		{
			return;
		}

		/* ⛔ NICHT in den sichtbaren Spuren suchen: zugeklappte Gruppen liefern nur Kopfzeilen
		 * ohne Kanal, und der Sprung fand dann nie etwas. Direkt am Film suchen. */
		int naechster = Integer.MAX_VALUE;

		for (Replay replay : film.replays.getList())
		{
			for (mchorse.bbs_mod.settings.values.base.BaseValue wert : replay.keyframes.getAll())
			{
				if (!(wert instanceof mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> kanal))
				{
					continue;
				}

				for (Keyframe<?> kf : kanal.getKeyframes())
				{
					int t = (int) kf.getTick();

					if (t > cursor && t < naechster)
					{
						naechster = t;
					}
				}
			}

			for (mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> kanal : replay.properties.properties.values())
			{
				for (Keyframe<?> kf : kanal.getKeyframes())
				{
					int t = (int) kf.getTick();

					if (t > cursor && t < naechster)
					{
						naechster = t;
					}
				}
			}
		}

		if (naechster != Integer.MAX_VALUE)
		{
			panel.setCursor(naechster);
		}
	}
}
