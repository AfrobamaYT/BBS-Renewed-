package com.mcstudio.see;

import com.mcstudio.util.RenderThread;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import java.io.File;

/**
 * WeltbildFahrt — die Kamerafahrt des offenen Films als Sequenz UI-freier Welt-Frames, einer je
 * Film-Tick. Route: {@code /welt_fahrt?frames=N[&start=t0][&name=basis][&film=id]}. Nachfolger von
 * /shot_world_seq (v1), dessen Off-Schedule-renderLevel auf 26.2 den Folgeframe toetet.
 *
 * <p>MECHANIK: pro Tick zwei Schritte — (a) auf dem Render-Thread {@code UIFilmPanel.setCursor(tick)}
 * (derselbe Scrub wie Timeline-Drag/PlayheadSync), (b) {@link WorldShot#fangen} vom Aufrufer-Thread:
 * scharfstellen und den NAECHSTEN echten Frame am Ende des Level-Passes abgreifen. Zwischen Scrub und
 * Fang liegt damit immer eine echte Frame-Grenze; die Pose des Ticks steht, wenn gefangen wird.
 * Der ganze Sweep laeuft auf dem HTTP-Thread — der Render-Thread wird nie blockiert.</p>
 *
 * <p>Playhead wird vorher gemerkt und im finally restauriert (die Fahrt darf die Timeline des
 * Nutzers nicht ans Ende scrubben). Film-Guard wie ueberall: ein benannter Film muss der offene
 * sein, sonst Fehler statt stiller Falschrender.</p>
 *
 * <p>EHRLICHE GRENZE (geerbt von v1, dort dokumentiert): die KAMERA folgt dem Scrub sicher je Tick;
 * ob jede Replay-KOERPERpose im selben Frame nachzieht, ist nicht zugesichert. Fuer den Composite-
 * Hintergrund (Welt + Kamera) reicht das; die Modelle kommen aus Blender.</p>
 */
public final class WeltbildFahrt
{
	/** Obergrenze je Aufruf — laengere Fahrten in Folgeaufrufen mit start-Offset (Antwort nennt es). */
	/* ⛔ PAKETWEIT statt private (2026-09-09): die HTTP-Route liegt seit dem Companion-Umbau
	 * in com.mcstudio.see.SeeRouten im dev-Modul — im SELBEN Paket, damit genau diese
	 * Sichtbarkeit reicht und nichts public werden muss. */
	static final int MAX_FRAMES = 600;

	private WeltbildFahrt()
	{
	}

	/** Durchgriff auf das Shots-Verzeichnis fuer fremde Pakete (Readback.shotsDir ist paket-privat;
	 * die Sichtbarkeit einer GELADENEN Klasse zu weiten waere ein unnoetiges Swap-Risiko). */
	public static File verzeichnis()
	{
		return Readback.shotsDir();
	}

	/**
	 * Den Sweep fahren, ein Welt-Frame je TICK (fps 20 — Ausgabeframe == Tick). Aufrufer-Thread
	 * (HTTP oder Orchestrierung) — NIE der Render-Thread ({@link WorldShot#fangen} wartet auf
	 * einen echten Frame und wuerde dort deadlocken).
	 */
	public static String fahren(String basis, int start, int frames, String wunschFilm)
	{
		return fahren(basis, start, frames, wunschFilm, 20);
	}

	/**
	 * SUB-TICK-FAHRT (§3 Zeitbasis, REDACTED Abnahme 1 bei 24/30/60 fps): ein Welt-Frame je
	 * AUSGABEFRAME. {@code start} und {@code frames} zaehlen Ausgabeframes j; der Zeitpunkt von
	 * j ist t = j·20/fps Ticks. Der Cursor steht auf ⌊t⌋, der Rest t−⌊t⌋ geht als erzwungener
	 * Partial-Tick ueber {@link SubTick} an die Kamera ({@code RunnerCameraController.setup})
	 * und die Actor-Pose ({@code FilmEditorController.getTransition}) — dieselbe Interpolation,
	 * die das Spiel beim Abspielen zwischen zwei Ticks rechnet. Die Datei heisst nach j, nicht
	 * nach dem Tick (bei 60 fps teilen sich drei Frames einen Tick). Bei fps 20 ist j == Tick,
	 * der Rest 0 und die Fahrt exakt die alte.
	 *
	 * <p>Ganzzahlig gerechnet ({@code j·20 = tick·fps + rest·fps}), damit t = 5,0 nie zu
	 * 4,999… wird und Cursor/Dateiname um einen Tick verrutschen.</p>
	 */
	public static String fahren(String basis, int start, int frames, String wunschFilm, int fps)
	{
		if (fps <= 0)
		{
			return "{\"error\":\"fps muss > 0 sein (bekommen: " + fps + ")\"}";
		}

		UIFilmPanel panel = film();

		if (panel == null)
		{
			return "{\"error\":\"kein film-editor offen — /welt_fahrt scrubbt den offenen film\"}";
		}

		String offen = panel.getData() != null && panel.getData().getId() != null
			? panel.getData().getId() : "";

		if (wunschFilm != null && !wunschFilm.isEmpty() && !wunschFilm.equals(offen))
		{
			return "{\"error\":\"film mismatch: angefragt '" + escape(wunschFilm) + "', offen '"
				+ escape(offen) + "'\",\"openFilm\":\"" + escape(offen) + "\"}";
		}

		File dir = Readback.shotsDir();
		int cursorVorher = Math.max(0, panel.getCursor());
		int geschrieben = 0;
		String ersterFehler = null;
		long t0 = System.nanoTime();

		/* Herleitung der drei Handgriffe (Besitz-Guard, POV, Verstecken): WeltPassKlammer. */
		String guard = WeltPassKlammer.besitzGuard();

		if (guard != null)
		{
			return guard;
		}

		WeltPassKlammer klammer = new WeltPassKlammer(panel);

		klammer.anwenden();

		try
		{
			for (int i = 0; i < frames; i++)
			{
				final int index = start + i;
				int zaehler = index * 20;
				final int tick = zaehler / fps;
				float rest = (zaehler % fps) / (float) fps;

				/* Rest UND Scrub zusammen auf dem Render-Thread setzen, zwischen zwei Frames. Vorher
				 * schrieb die Fahrt den Rest aus IHREM Thread mitten in einen laufenden Frame:
				 * Vitrail sah die Pose zwischen Einsammeln und Zeichnen wechseln und brach mit "Late
				 * native form/entity/pose changed" ab (zwei Abstuerze 2026-10-14, 11:18 und 11:35). */
				WeltbildFahrtRest.vormerken(rest);
				RenderThread.submit(() ->
				{
					WeltbildFahrtRest.anwenden();
					panel.setCursor(tick);

					return "";
				});

				File ziel = new File(dir, basis + "_" + String.format("%04d", index) + ".png");
				String json = WorldShot.fangen(ziel.getAbsolutePath());

				if (json.contains("\"path\""))
				{
					geschrieben++;
				}
				else
				{
					/* Erster Fehler bricht ab: ohne Abbruch wartete die Fahrt jeden Frame erneut in
					 * denselben Timeout (108 Frames, ~18 min, 0 Bilder) und meldete erst am Ende. */
					ersterFehler = json;
					break;
				}
			}
		}
		finally
		{
			/* Erst den Rest loesen, dann den Cursor zurueck — der Editor scrubbt danach wieder
			 * mit Partial-Tick 0, wie vor der Fahrt. */
			WeltbildFahrtRest.loesen();
			klammer.zuruecknehmen(cursorVorher);
		}

		long ms = (System.nanoTime() - t0) / 1_000_000L;
		boolean ok = geschrieben == frames;

		StringBuilder sb = new StringBuilder();

		sb.append("{\"ok\":").append(ok)
			.append(",\"film\":\"").append(escape(offen)).append('"')
			.append(",\"start\":").append(start)
			.append(",\"frames\":").append(frames)
			.append(",\"fps\":").append(fps)
			.append(",\"written\":").append(geschrieben)
			.append(",\"dir\":\"").append(escape(dir.getAbsolutePath())).append('"')
			.append(",\"pattern\":\"").append(escape(basis)).append("_%04d.png\"")
			.append(",\"ms\":").append(ms)
			.append(",\"restoredCursor\":").append(cursorVorher);

		if (ersterFehler != null)
		{
			sb.append(",\"firstError\":").append(ersterFehler);
		}

		sb.append('}');

		return sb.toString();
	}

	/** peekDashboard, NICHT getDashboard — letzteres baut als Nebenwirkung ein Dashboard. */
	private static UIFilmPanel film()
	{
		UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

		if (dashboard == null)
		{
			return null;
		}

		UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

		if (panel == null || panel.getData() == null)
		{
			return null;
		}

		return panel;
	}

	private static int zahl(String s, int sonst)
	{
		if (s == null || s.isEmpty())
		{
			return sonst;
		}

		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return sonst;
		}
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}

		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
