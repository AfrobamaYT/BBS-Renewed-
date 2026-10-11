package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.film.audio.VoiceoverTransport;
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
 * Zeitleisten-KOPF im neuen Designsystem: Editor-Typ, Transport, Marker/Key, Loop-Ring —
 * plus die Kanalspalte im Kamera-Zweig.
 *
 * <p>⛔ NUR der Kopf. Alles darunter — Spuren, Clips, Keyframes, Lineal — zeichnet der
 * Koerper-Editor des aktiven Modus ({@link ZeitleisteZweig}): UIClips fuer Kamera-Clips
 * mit seinen 89 Methoden (Auswahl, Kollisionen, Einrasten, Ebenen), der Dope Sheet des
 * Replay-Editors fuer Keyframes. Der Neubau hatte versucht, UIClips nachzubauen, und
 * 88 der 89 Methoden verloren — jede Nutzermeldung seither war ein Stueck dieser Logik.
 * Was der Kopf ausloest, geht an dieselben Stellen wie vorher.</p>
 */
public class UIZeitleisteNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	private final NanoFlaeche flaeche;
	private String stand = "";
	private int gewaehlteSpur = -1;
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
	/** 0 Dope Sheet · 1 Animated only · 2 Camera clips — Boot zeigt den Kamera-Schnitt. */
	public int modus = 2;

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

        return kennung.append('@').append(panel.getCursor()).append('m').append(this.modus).append('a').append(FilmAutoKey.isEnabled()).toString();
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

	/**
	 * Editor-Wahl als KONTEXTMENUE statt als in die Textur gezeichnete Klappe.
	 *
	 * ⛔ Die Klappe lag im Replay-Zweig UNTER dem spaeter gerenderten Keyframe-Editor:
	 * Text uebermalt, Klicks landeten im Editor darunter — ein toter Knopf mit offenen,
	 * unerreichbaren Zonen (gemessen 2026-08-24: editorwahl-Zonen da, modus blieb 0).
	 * Ein Kontextmenue liegt IMMER obenauf, in beiden Zweigen.
	 */
	private void modusMenue()
	{
		this.getContext().replaceContextMenu((menu) ->
		{
			menu.gruppe(mchorse.bbs_mod.l10n.keys.IKey.raw("Editor"));

			String[][] eintraege = {{"Dope Sheet", "0"}, {"Animated only", "1"}, {"Camera clips", "2"}};

			for (String[] e : eintraege)
			{
				int wahl = Integer.parseInt(e[1]);

				menu.action(this.modus == wahl ? mchorse.bbs_mod.ui.utils.icons.Icons.CHECKMARK
					: mchorse.bbs_mod.ui.utils.icons.Icons.GRAPH,
					mchorse.bbs_mod.l10n.keys.IKey.raw(e[0]), () ->
				{
					this.modus = wahl;
					this.auffrischen();
					ZeitleisteZweig.anwenden(filmPanel(), wahl);
				});
			}
		});
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

		/* EIN Trichter-Knopf mit Popover (Blenders Funnel) statt der Knopfreihe: die
		 * brach bei engem Kopf ab und war dann UNERREICHBAR (960er GUI: 0 Zonen). */
		if (panel == null || panel.replayEditor == null || !panel.replayEditor.isVisible()
			|| kx + 22F * k > grenze - 6F * k)
		{
			return kx;
		}

		boolean an = !"all".equals(panel.replayEditor.aktiverFilter());
		float y = (NanoDesign.H_KOPF - 20F) * k / 2F;

		if (an)
		{
			NanoStifte.widget(vg, kx, y, 22F * k, 20F * k, NanoDesign.R_WIDGET * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("kf-trichter"))
		{
			NanoStifte.widget(vg, kx, y, 22F * k, 20F * k, NanoDesign.R_WIDGET * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, "trichter", kx + 11F * k, NanoDesign.H_KOPF * k / 2F,
			an ? 0xFFFFFF : NanoDesign.TXT_DIM, k * 0.8F);

		f.zone("kf-trichter", kx, y, 22F * k, 20F * k, this::filterMenue,
			"Filter channels by type (Blender: funnel)", "");

		return kx + 24F * k;
	}

	private void filterMenue()
	{
		KanalSuche.filterMenue(this);
	}

	/** Kennung brechen + Kopf neu malen — fuer die Kopf-Werkzeuge in {@link KanalSuche}. */
	void kopfNeu()
	{
		this.stand = "";
		this.flaeche.neuMalen();
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

		/* ⛔ NUR Kopfzeile fuellen: Vollflaechen-Grund uebermalte Lineal und Clips (Flaeche liegt UEBER UIClips). */
		NanoStifte.flaeche(vg, 0, 0, b, NanoDesign.H_KOPF * k, NanoDesign.R_AREA * k,
			NanoDesign.REGION);

		float nachKopf = NanoBausteine.kopfzeile(vg, b, k, "dopesheet");

		f.zone("etyp", 8F * k, (NanoDesign.H_KOPF - 22F) * k / 2F, 37F * k, 22F * k, this::modusMenue, "Editor type", "");

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

			f.zone("voll", vollX, vollY, vollB, 20F * k, () -> pf.flaecheMaximieren("main"), "Maximize this area", "");
		}

		/* Zeitanzeige hat Vorrang; der Titel erscheint nur, wenn er danach noch passt. */
		String zeit = cursor + " / " + dauer + "   " + String.format("%.1f s", cursor / 20F);
		float zb = NanoDesign.breite(zeit, 11F * k);
		float zx = vollX - zb - 10F * k;

		NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, zx,
			NanoDesign.H_KOPF * k / 2F, NanoDesign.TXT, zeit);

		/* ⛔ TRANSPORT MITTIG: rechts an der Zeitanzeige liess die Gruppe vor „Marker" ein
		 * breites Loch (Nutzerpfeil). Sie baut weiter von rechts nach links; nur ihre rechte
		 * Kante wandert auf Kopfmitte. Reicht der Platz nicht, bleibt sie rechts stehen. */
		float transportB = 8F * 22F * k + 16F * k;
		float mittig = b / 2F + transportB / 2F;
		float transportRechts = Math.min(zx - 8F * k, Math.max(mittig, transportB + 8F * k));

		float nachTransport = this.transport(vg, f, transportRechts, k, panel, cursor, dauer);

		/* Kanal-Suche rechts der Transportgruppe — links ist der Kopf voll (46 px Rest gemessen). */
		float nachTrichter = this.kanalfilter(vg, f, transportRechts + 8F * k, zx - 8F * k, k);

		KanalSuche.zeichnen(vg, f, nachTrichter, zx - 8F * k, k, this.modus);
		nachTransport = this.schleife(vg, f, nachTransport, k, panel, cursor);
		nachTransport -= 22F * k;
		this.transportKnopf(vg, f, "tr-bone", nachTransport, (NanoDesign.H_KOPF * k - 20F * k) / 2F, 22F * k, 20F * k, k, "knochen",
			NanoDesign.TXT, mchorse.bbs_mod.ui.film.ZeitleisteZweig.istBoneFilter(), mchorse.bbs_mod.ui.film.ZeitleisteZweig::boneFilterToggle,
			"Only selected bone (Blender: Only Show Selected)", "");
		nachTransport -= 22F * k;
		this.transportKnopf(vg, f, "tr-magnet", nachTransport, (NanoDesign.H_KOPF * k - 20F * k) / 2F, 22F * k, 20F * k, k, "raster",
			NanoDesign.TXT, mchorse.bbs_mod.ui.film.ZeitleisteZweig.istMagnet(), mchorse.bbs_mod.ui.film.ZeitleisteZweig::magnetToggle,
			"Snap dragged keys to playhead, markers and clip edges", "");
		nachTransport = this.markerKnopf(vg, f, nachTransport, k, film, cursor);

		/* Titel als Waehler, dann die Menuezeile — Entwurf: [etyp] [Dope Sheet v] Marker Key */
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

			f.zone("titelklappe", kx, (NanoDesign.H_KOPF - 22F) * k / 2F, tw, 22F * k,
				this::modusMenue, "Timeline mode: which editor fills the body", "");

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
					this.stand = "";
					this.flaeche.neuMalen();
				});

				kx += mw;
			}

		}

		/* ⛔ HIER ENDET diese Flaeche: alles darunter zeichnet UIClips mit seinen 89 Methoden —
		 * der Neubau hatte das nachgebaut und 88 davon verloren (jede Nutzermeldung ein Stueck davon). */
		this.kanalspalte(vg, f, b, h, k);

		/* ⛔ Eingaben NUR im Kopf: die volle Flaeche schluckte sonst die komplette Bedienung
		 * des Koerper-Editors darunter; die Kanalspalte bleibt bewusst draussen (Beschriftung). */
		f.eingabeFeld(0F, 0F, this.area.w, NanoDesign.H_KOPF);
		/* Hoehe = NUR Kopf: unterhalb uebermalt das Dope Sheet — der Rand-Kipp klappt den Tooltip so nach OBEN (gemessen). */
		NanoBausteine.tooltip(vg, b, Math.round(NanoDesign.H_KOPF * k), k, f.tooltipFaellig());
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
			() -> panel.preview.recordReplay.clickItself(), "Record replay", kuerzel("record"));
		x = VoiceoverTransport.draw(vg, f, x, y, bw, bh, k, panel);
		x -= bw + 6F * k;
		this.transportKnopf(vg, f, "tr-auto-key", x, y, bw, bh, k, "dopesheet", 0xE58F89, FilmAutoKey.isEnabled(),
			FilmAutoKey::toggle, "Auto Key: pose/transform edits at playhead (no playback)", "");


		x -= 6F * k + bw;
		this.transportKnopf(vg, f, "tr-ende", x, y, bw, bh, k, "ende", NanoDesign.TXT, false,
			() -> panel.setCursor(dauer), "Jump to end", "");

		x -= bw;
		this.transportKnopf(vg, f, "tr-vor", x, y, bw, bh, k, "schritt-vor", NanoDesign.TXT, false,
			() -> panel.setCursor(Math.min(dauer, cursor + 1)), "Next frame", kuerzel("next_frame"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-play", x, y, bw, bh, k, laeuft ? "pause" : "play",
			laeuft ? 0xFFFFFF : NanoDesign.TXT, laeuft, panel::togglePlayback,
			laeuft ? "Pause playback" : "Play", kuerzel("plause"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-zurueck", x, y, bw, bh, k, "schritt-zurueck", NanoDesign.TXT, false,
			() -> panel.setCursor(Math.max(0, cursor - 1)), "Previous frame", kuerzel("prev_frame"));

		x -= bw;
		this.transportKnopf(vg, f, "tr-anfang", x, y, bw, bh, k, "anfang", NanoDesign.TXT, false,
			() -> panel.setCursor(0), "Jump to start", "");

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
		}, "Loop region: 1st click sets start, 2nd sets end, 3rd clears", kuerzel("looping"));

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
		}, "Add or remove a marker at the playhead", kuerzel("marker_add"));

		return x - 8F * k;
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

	public void modusUmschalten(int ziel)
	{
		this.modus = this.modus == ziel ? 0 : ziel;
		this.auffrischen();
		ZeitleisteZweig.anwenden(filmPanel(), this.modus);
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
