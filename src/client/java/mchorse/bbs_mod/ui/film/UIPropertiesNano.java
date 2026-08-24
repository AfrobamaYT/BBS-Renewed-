package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoSymbole;
import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Properties-Flaeche im neuen Designsystem: Icon-Schiene links, aufklappbare Panels rechts.
 *
 * <p>Zeigt die ECHTEN Werte des im Replay-Editor gewaehlten Replays am aktuellen Tick —
 * Position/Rotation aus den Keyframe-Kanaelen, Form, Kanal-Zahlen. Keine Beispieldaten.</p>
 */
public class UIPropertiesNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	private final NanoFlaeche flaeche;
	/** Ob der Zieh-Handler an der Flaeche haengt — siehe zeichnen(). */
	private boolean ziehenVerdrahtet;
	private String stand = "";
	/** Zaehler fuer den naechsten Panel-Kopf; null = Standardanzeige. */
	private String kennzahl;
	/** Angeheftetes Replay: haelt die Anzeige fest, unabhaengig von der Auswahl im Outliner. */
	private Replay angeheftet;
	private int schiene = 0;
	/**
	 * Klappzustand je Panel. NICHT final und ueber {@link #auf} zugegriffen: nach einem Hotswap
	 * behaelt die Instanz das ALTE Array — kommt ein Panel dazu, fliegt sonst jedes Bild eine
	 * ArrayIndexOutOfBounds. Dritte Runde derselben Falle in diesem Umbau.
	 */
	private boolean[] offen = {true, true, false, false, false};

	/** Klappzustand lesen; waechst das Array bei Bedarf mit. */
	private boolean auf(int nr)
	{
		if (this.offen == null || this.offen.length <= nr)
		{
			boolean[] neu = new boolean[nr + 4];

			if (this.offen == null)
			{
				neu[0] = true;
				neu[1] = true;
			}
			else
			{
				System.arraycopy(this.offen, 0, neu, 0, this.offen.length);
			}

			this.offen = neu;
		}

		return this.offen[nr];
	}

	public static UIElement neu()
	{
		return new UIPropertiesNano();
	}

	public UIPropertiesNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.beimZiehen(this::ziehen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
		this.h(330);
	}

	/**
	 * Schrubben: Ziehen auf einem Zahlenfeld aendert den Wert und SCHREIBT ihn in den
	 * Keyframe-Kanal am aktuellen Tick — wie in Blender. {@code d} ist die Verschiebung
	 * in Flaechen-Pixeln seit dem letzten Bild.
	 */
	private void ziehen(String zoneId, float d)
	{
		Replay replay = this.angeheftet != null ? this.angeheftet : gewaehltesReplay();
		UIFilmPanel panel = filmPanel();

		if (replay == null || panel == null || !zoneId.startsWith("feld:"))
		{
			return;
		}

		String kanal = zoneId.substring(5);
		int tick = panel.getCursor();
		/* Feinfuehlig: 100 Pixel Zug = 1 Einheit; Winkel gröber. */
		double schritt = kanal.equals("yaw") || kanal.equals("pitch") ? d * 0.25D : d * 0.01D;

		/* ⛔ Drehung und Groesse liegen NICHT in den Replay-Kanaelen, sondern an
		 * {@code Form.transform}. Sie fielen hier auf default -> null, waehrend Zeile 448
		 * ihre Zonen brav registriert: vier ziehbare Felder, die nichts taten. Gemessen am
		 * 2026-08-20 — der Klicktest konnte es nicht sehen, weil Ziehzonen absichtlich kein
		 * Klickziel haben, und der Ziehtest hielt die Hover-Aenderung fuer eine Wirkung. */
		if (kanal.startsWith("r") || kanal.equals("sx"))
		{
			this.formWertZiehen(replay, kanal, d);

			return;
		}

		mchorse.bbs_mod.utils.keyframes.KeyframeChannel<Double> ziel = switch (kanal)
		{
			case "x" -> replay.keyframes.x;
			case "y" -> replay.keyframes.y;
			case "z" -> replay.keyframes.z;
			case "yaw" -> replay.keyframes.yaw;
			case "pitch" -> replay.keyframes.pitch;
			default -> null;
		};

		if (ziel == null)
		{
			return;
		}

		double alt = ziel.interpolate(tick, 0D);

		ziel.insert(tick, alt + schritt);
		this.stand = "";
		this.flaeche.neuMalen();
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	/**
	 * Drehung und Groesse ziehen — die sitzen an {@code Form.transform}, nicht an den
	 * Replay-Kanaelen.
	 *
	 * <p>Der Schritt ist grober als bei der Position: 100 px Zug = 90 Grad waeren
	 * unbrauchbar fein, 100 px = 45 Grad trifft die Erwartung. Die Skalierung bleibt
	 * feinfuehlig (100 px = 1.0) und wird bei aktiver Gleichskalierung auf alle drei
	 * Achsen gelegt — sonst zeigt das Feld einen Wert, den das Modell nicht hat.</p>
	 */
	private void formWertZiehen(Replay replay, String kanal, float d)
	{
		mchorse.bbs_mod.forms.forms.Form form = replay.form.get();
		mchorse.bbs_mod.utils.pose.Transform tf = form == null ? null : form.transform.get();

		if (tf == null)
		{
			return;
		}

		if (kanal.equals("sx"))
		{
			float neu = Math.max(0.01F, tf.scale.x + d * 0.01F);
			boolean gleich = Math.abs(tf.scale.x - tf.scale.y) < 0.001F
				&& Math.abs(tf.scale.y - tf.scale.z) < 0.001F;

			tf.scale.x = neu;

			if (gleich)
			{
				tf.scale.y = neu;
				tf.scale.z = neu;
			}
		}
		else
		{
			float bogen = (float) Math.toRadians(d * 0.45D);

			switch (kanal)
			{
				case "rx" -> tf.rotate.x += bogen;
				case "ry" -> tf.rotate.y += bogen;
				case "rz" -> tf.rotate.z += bogen;
				default -> { return; }
			}
		}

		this.stand = "";
		this.flaeche.neuMalen();
	}

	private static Replay gewaehltesReplay()
	{
		UIFilmPanel panel = filmPanel();

		return panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();
	}

	private static String zahl(double wert)
	{
		return String.format("%.3f", wert);
	}

	private static String winkel(double wert)
	{
		return String.format("%.1f°", wert);
	}

	@Override
	public void auffrischen()
	{
		Replay replay = this.angeheftet != null ? this.angeheftet : gewaehltesReplay();
		UIFilmPanel panel = filmPanel();
		int tick = panel == null ? 0 : panel.getCursor();

		String jetzt = (replay == null ? "leer"
			: replay.getName() + "|" + tick + "|" + this.schiene
				+ "|" + this.auf(0) + this.auf(1) + this.auf(2) + this.auf(3) + this.auf(4))
			+ (this.angeheftet != null ? "|pin" : "");

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

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		/* ⛔ Zieh-Handler HIER sichern, nicht nur im Konstruktor: der laeuft beim Hotswap
		 * nicht neu, und die bestehende Flaeche stand danach ohne Handler da — die
		 * Zahlenfelder liessen sich nicht mehr ziehen, obwohl der Code stimmte. Gemessen
		 * 2026-08-20 an der Zeitleiste (zieheHandler == null bei laufender Instanz); es war
		 * dieselbe Ursache wie beim „unerklaerlichen" Mausweg-Fehler der Ziehzonen. */
		if (!this.ziehenVerdrahtet)
		{
			f.beimZiehen(this::ziehen);
			this.ziehenVerdrahtet = true;
		}

		final float k = Math.max(1F, f.skala());

		NanoStifte.flaeche(vg, 0, 0, b, h, NanoDesign.R_AREA * k, NanoDesign.REGION);

		/* Kopf mit Editor-Typ-Waehler und Brotkrume */
		float nachKopf = NanoBausteine.kopfzeile(vg, b, k, "props");

		Replay gewaehlt = gewaehltesReplay();
		UIFilmPanel panel = filmPanel();
		int tick = panel == null ? 0 : panel.getCursor();
		String filmName = panel == null || panel.getData() == null ? "—" : panel.getData().getId();

		/* Anheften: haelt die Anzeige beim aktuellen Replay fest, auch wenn im Outliner ein
		 * anderes gewaehlt wird — Blenders Pin. Angeheftet wird der Name mitgefuehrt. */

		float pinX = b - 24F * k;
		float pinY = (NanoDesign.H_KOPF * k - 20F * k) / 2F;

		if (this.angeheftet != null)
		{
			NanoStifte.widget(vg, pinX, pinY, 20F * k, 20F * k, 4F * k, NanoDesign.BLAU, k);
		}
		else if (f.istHover("pin"))
		{
			NanoStifte.widget(vg, pinX, pinY, 20F * k, 20F * k, 4F * k, NanoDesign.WIDGET, k);
		}

		NanoSymbole.symbol(vg, "stift", pinX + 10F * k, pinY + 10F * k,
			this.angeheftet != null ? 0xFFFFFF : NanoDesign.TXT_DIM, k * 0.85F);

		f.zone("pin", pinX, pinY, 20F * k, 20F * k, () ->
		{
			this.angeheftet = this.angeheftet == null ? gewaehltesReplay() : null;
			this.stand = "";
			this.flaeche.neuMalen();
		});

		final Replay replay = this.angeheftet != null ? this.angeheftet : gewaehlt;

		String krume = filmName + "  ›  " + (replay == null ? "nothing selected" : replay.getName())
			+ (this.angeheftet != null ? "  (angeheftet)" : "");
		NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, nachKopf,
			NanoDesign.H_KOPF * k / 2F, NanoDesign.TXT_DIM,
			NanoDesign.kuerzen(krume, b - nachKopf - 30F * k, 11F * k));

		float oben = NanoDesign.H_KOPF * k;

		/* Icon-Schiene links (28 px) */
		float schieneB = 28F * k;
		NanoStifte.flaeche(vg, 0, oben, schieneB, h - oben, 0F, 0x1F1F1F);

		String[] symbole = {"replay", "knochen", "film", "props"};

		for (int i = 0; i < symbole.length; i++)
		{
			float sy = oben + 4F * k + i * 26F * k;
			boolean aktiv = i == this.schiene;

			if (aktiv)
			{
				NanoStifte.flaeche(vg, 0, sy, schieneB, 26F * k, 0F, NanoDesign.REGION);
				NanoStifte.widget(vg, 0, sy + 4F * k, 2F * k, 18F * k, 1F * k, NanoDesign.BLAU, k);
			}

			NanoSymbole.symbol(vg, symbole[i], schieneB / 2F, sy + 13F * k,
				aktiv ? NanoDesign.TXT_MAX : NanoDesign.TXT_DIM, k);

			final int idx = i;
			f.zone("schiene" + i, 0, sy, schieneB, 26F * k, () ->
			{
				this.schiene = idx;
				this.flaeche.neuMalen();
			});
		}

		/* Inhaltsspalte. Rollen in 6-px-Schritten: die Panels haben keine feste Zeilenhoehe,
		 * also zaehlt hier der Versatz in Pixeln, nicht in Zeilen. */
		float ix = schieneB + 6F * k;
		float ib = b - ix - 6F * k;
		float y  = oben + 5F * k - f.rollVersatz() * 6F * k;

		/* --- Film-Tab (Blenders Scene-Properties): Daten des FILMS, unabhaengig vom
		 * gewaehlten Replay — deshalb VOR dem Kein-Replay-Ausstieg. --- */
		if (this.schiene == 2)
		{
			mchorse.bbs_mod.film.Film film = panel == null ? null : panel.getData();

			if (film == null)
			{
				NanoStifte.text(vg, NanoDesign.SANS, 11F * k, ix, y + 18F * k, NanoDesign.TXT_OFF,
					"No film loaded", null);
				return;
			}

			panel(vg, f, ix, y, ib, k, 5, "Film", () -> new String[][]{
				{"Name", film.getId(), ""},
				{"Duration", film.camera.calculateDuration() + " ticks", ""},
				{"Replays", String.valueOf(film.replays.getList().size()), ""},
				{"Camera clips", String.valueOf(film.camera.get().size()), ""},
			});
			NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
			return;
		}

		if (replay == null)
		{
			NanoStifte.text(vg, NanoDesign.SANS, 11F * k, ix, y + 18F * k, NanoDesign.TXT_OFF,
				"No replay selected", null);
			return;
		}

		/* Rotation und Skalierung stehen NICHT in den Replay-Kanaelen, sondern in der Form
		 * ({@code Form.transform} traegt translate/rotate/scale). Deshalb hier von dort. */
		mchorse.bbs_mod.forms.forms.Form form = replay.form.get();
		mchorse.bbs_mod.utils.pose.Transform tf = form == null ? null : form.transform.get();

		/* --- Transform: Ort aus den Kanaelen, Drehung und Groesse aus der Form --- */
		if (this.schiene == 0)
		y = panel(vg, f, ix, y, ib, k, 0, "Transform", () ->
		{
			double px = replay.keyframes.x.interpolate(tick, 0D);
			double py = replay.keyframes.y.interpolate(tick, 0D);
			double pz = replay.keyframes.z.interpolate(tick, 0D);

			if (tf == null)
			{
				return new String[][]{
					{"Location X", zahl(px), "x", "x", "loc"},
					{"Y", zahl(py), "y", "y", "loc"},
					{"Z", zahl(pz), "z", "z", "loc"},
				};
			}

			return new String[][]{
				{"Location X", zahl(px), "x", "x", "loc"},
				{"Y", zahl(py), "y", "y", "loc"},
				{"Z", zahl(pz), "z", "z", "loc"},
				{"Rotation X", winkel(Math.toDegrees(tf.rotate.x)), "x", "rx", "rot"},
				{"Y", winkel(Math.toDegrees(tf.rotate.y)), "y", "ry", "rot"},
				{"Z", winkel(Math.toDegrees(tf.rotate.z)), "z", "rz", "rot"},
				{"Scale", zahl(tf.scale.x), "", "sx", ""},
			};
		});

		/* Ankreuzfeld unter Transform — gleichmaessige Skalierung, wie im Entwurf. */
		if (this.schiene == 0 && this.auf(0) && tf != null)
		{
			boolean gleich = Math.abs(tf.scale.x - tf.scale.y) < 0.001F
				&& Math.abs(tf.scale.y - tf.scale.z) < 0.001F;

			y = ankreuz(vg, f, ix + 6F * k, y - 2F * k, k, "Uniform scale", gleich, "uniform");
		}

		/* --- Form: Dateizeilen mit Ordner-Knopf, wie im Entwurf --- */
		if (this.schiene == 1)
		y = panel(vg, f, ix, y, ib, k, 1, "Form", () ->
		{
			String modell = "—";
			String textur = "—";

			if (form instanceof mchorse.bbs_mod.forms.forms.ModelForm mf)
			{
				modell = mf.model.get().isEmpty() ? "—" : mf.model.get();
				textur = mf.texture.get() == null ? "—" : mf.texture.get().toString();
			}
			else if (form != null)
			{
				modell = form.getFormIdOrName();
			}

			return new String[][]{
				{"", modell, "", "", "", "datei"},
				{"", textur, "", "", "", "datei"},
			};
		});

		/* --- Visibility --- */
		if (this.schiene == 0)
		y = panel(vg, f, ix, y, ib, k, 2, "Visibility", () -> new String[][]{
			{"Visible", replay.enabled.get() ? "Yes" : "No", ""},
			{"Form visible", form == null || form.visible.get() ? "Yes" : "No", ""},
			{"Lighting", form == null ? "—" : zahl(form.lighting.get()), ""},
		});

		/* --- Shape Keys mit Zaehler im Kopf --- */
		int shapes = form instanceof mchorse.bbs_mod.forms.forms.ModelForm mf2
			? mf2.shapeKeys.get().shapeKeys.size() : 0;

		if (this.schiene == 1)
		y = panelMitZahl(vg, f, ix, y, ib, k, 3, "Shape Keys", shapes,
			() -> new String[0][]);

		/* --- Kanäle --- */
		if (this.schiene == 3)
		y = panel(vg, f, ix, y, ib, k, 4, "Channels", () -> new String[][]{
			{"Total", String.valueOf(replay.keyframes.getAll().size()), ""},
			{"Actions", String.valueOf(replay.actions.get().size()), ""},
		});

		/* Was unten uebersteht, wird rollbar — sonst sind zugeklappte Panels unerreichbar. */
		float ueberstand = (y + f.rollVersatz() * 6F * k) - h;

		f.rollGrenze(Math.max(0F, ueberstand / (6F * k)));
	
		/* Tooltip ganz zuletzt — er liegt ueber allem, auch ueber aufgeklappten Listen. */
		NanoBausteine.tooltip(vg, b, h, k, f.tooltipFaellig());
	}

	/**
	 * Ankreuzfeld-Zeile, wie im Entwurf ({@code .crow} mit {@code .chk}): 12x12-Kaestchen,
	 * blau gefuellt mit Haken, wenn gesetzt.
	 */
	private float ankreuz(long vg, NanoFlaeche f, float x, float y, float k,
		String text, boolean an, String id)
	{
		float gr = 12F * k;
		float cy = y + 8F * k;

		NanoStifte.widget(vg, x, cy - gr / 2F, gr, gr, 3F * k,
			an ? NanoDesign.BLAU : NanoDesign.FELD, k);

		if (an)
		{
			/* Haken aus zwei Strichen — kein Zeichensatz-Glyph, der je nach Schrift fehlt. */
			NanoStifte.linie(vg, x + 3F * k, cy, x + 5F * k, cy + 2.5F * k, 1.6F * k, 0xFFFFFF);
			NanoStifte.linie(vg, x + 5F * k, cy + 2.5F * k, x + 9F * k, cy - 2.5F * k, 1.6F * k, 0xFFFFFF);
		}

		NanoStifte.text(vg, NanoDesign.SANS, 11F * k, x + gr + 7F * k, cy + 3.5F * k,
			NanoDesign.TXT, text, null);

		return y + 18F * k;
	}

	/** Panel mit Zaehler im Kopf — der Entwurf zeigt so „Shape Keys 6". */
	private float panelMitZahl(long vg, NanoFlaeche f, float x, float y, float b, float k,
		int nummer, String titel, int zahl, java.util.function.Supplier<String[][]> zeilen)
	{
		this.kennzahl = String.valueOf(zahl);

		float aus = this.panel(vg, f, x, y, b, k, nummer, titel, zeilen);

		this.kennzahl = null;

		return aus;
	}

	/** Ein aufklappbares Panel; gibt das y darunter zurueck. */
	private float panel(long vg, NanoFlaeche f, float x, float y, float b, float k,
		int nummer, String titel, java.util.function.Supplier<String[][]> zeilen)
	{
		boolean auf = this.auf(nummer);
		String[][] daten = auf ? zeilen.get() : new String[0][];
		String kennzahl = this.kennzahl != null ? this.kennzahl : (auf ? "" : "•••");

		/* Karte ZUERST, in voller Hoehe — der Kopf legt sich darauf. So sitzt der Inhalt auf
		 * PANEL #2F2F2F statt direkt auf dem Flaechengrund; das ist die Stufe des Entwurfs. */
		if (auf && daten.length > 0)
		{
			/* Hoehe mit DERSELBEN Fugenregel wie die Schleife unten: 1 px innerhalb einer
			 * Gruppe, 4 px dazwischen. Eine Pauschale traf die Karte um mehrere Pixel daneben. */
			float kartenH = 23F * k + 2F * k + daten.length * NanoDesign.H_WIDGET * k;

			for (int i = 0; i + 1 < daten.length; i++)
			{
				String a = daten[i].length > 4 ? daten[i][4] : "";
				String c = daten[i + 1].length > 4 ? daten[i + 1][4] : "";

				kartenH += (!a.isEmpty() && a.equals(c) ? 1F : 4F) * k;
			}

			NanoStifte.flaeche(vg, x, y, b, kartenH + 4F * k, NanoDesign.R_PANEL * k, NanoDesign.PANEL);
		}

		float kopfH = NanoBausteine.panelKopf(vg, x, y, b, k, titel, auf, kennzahl);

		final int nr = nummer;
		f.zone("panel" + nummer, x, y, b, kopfH, () ->
		{
			this.offen[nr] = !this.auf(nr);
			this.flaeche.neuMalen();
		});

		float yy = y + kopfH + 2F * k;

		for (int i = 0; i < daten.length; i++)
		{
			String[] zeile = daten[i];
			int achse = zeile[2].equals("x") ? NanoDesign.ACHSE_X
				: zeile[2].equals("y") ? NanoDesign.ACHSE_Y
				: zeile[2].equals("z") ? NanoDesign.ACHSE_Z : 0;

			/* Lage im Stapel aus Spalte 5: gleicher Gruppenname wie der Nachbar heisst
			 * zusammenhaengender Block — Radius nur aussen, 1 px Fuge dazwischen. */
			String gr = zeile.length > 4 ? zeile[4] : "";
			boolean vorGleich = i > 0 && daten[i - 1].length > 4 && daten[i - 1][4].equals(gr) && !gr.isEmpty();
			boolean nachGleich = i + 1 < daten.length && daten[i + 1].length > 4
				&& daten[i + 1][4].equals(gr) && !gr.isEmpty();
			int lage = vorGleich && nachGleich ? 2 : vorGleich ? 3 : nachGleich ? 1 : 0;

			NanoBausteine.feld(vg, x + 6F * k, yy, b - 12F * k, k, zeile[0], zeile[1], achse, -1F, lage);

			/* Zahlenfelder sind schrubbbar: die Zone traegt den Kanalnamen. */
			if (zeile.length > 3 && !zeile[3].isEmpty())
			{
				f.zone("feld:" + zeile[3], x + 6F * k, yy, b - 12F * k, NanoDesign.H_WIDGET * k, () -> {});
			}

			/* Innerhalb einer Gruppe 1 px Fuge, zwischen Gruppen 4. */
			yy += NanoDesign.H_WIDGET * k + (nachGleich ? 1F : 4F) * k;
		}

		return yy + (daten.length == 0 ? 3F : 5F) * k;
	}
}
