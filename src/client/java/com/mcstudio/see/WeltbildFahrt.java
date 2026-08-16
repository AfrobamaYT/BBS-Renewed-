package com.mcstudio.see;

import com.mcstudio.http.StudioServer;
import com.mcstudio.util.RenderThread;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import java.io.File;


public final class WeltbildFahrt
{
	/** Obergrenze je Aufruf — laengere Fahrten in Folgeaufrufen mit start-Offset (Antwort nennt es). */
	private static final int MAX_FRAMES = 600;

	private WeltbildFahrt()
	{
	}

	/** Durchgriff auf das Shots-Verzeichnis fuer fremde Pakete (Readback.shotsDir ist paket-privat;
	 * die Sichtbarkeit einer GELADENEN Klasse zu weiten waere ein unnoetiges Swap-Risiko). */
	public static File verzeichnis()
	{
		return Readback.shotsDir();
	}

	/** Route registrieren — StudioServer kann seit 2026-07-22 auch nach start() live registrieren. */
	public static void register()
	{
		StudioServer.route("/welt_fahrt", exchange ->
		{
			String name = StudioServer.query(exchange, "name");
			int frames = zahl(StudioServer.query(exchange, "frames"), -1);
			int start = Math.max(0, zahl(StudioServer.query(exchange, "start"), 0));
			String wunschFilm = StudioServer.query(exchange, "film");

			if (name == null || name.isEmpty())
			{
				name = "bbs_frame";
			}

			final String basis = name.replaceAll("[^A-Za-z0-9._-]", "_");

			if (frames <= 0)
			{
				StudioServer.respond(exchange, "{\"error\":\"?frames= muss eine positive zahl sein\"}");
				return;
			}

			StudioServer.respond(exchange, fahren(basis, start, Math.min(frames, MAX_FRAMES), wunschFilm));
		});

		System.out.println("[mcstudio] WeltbildFahrt registered /welt_fahrt");
	}

	/**
	 * Den Sweep fahren. Aufrufer-Thread (HTTP oder Orchestrierung) — NIE der Render-Thread
	 * ({@link WorldShot#fangen} wartet auf einen echten Frame und wuerde dort deadlocken).
	 */
	public static String fahren(String basis, int start, int frames, String wunschFilm)
	{
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
				final int tick = start + i;

				/* Scrub auf dem Render-Thread; der Rueckgabewert ist egal, submit synchronisiert. */
				RenderThread.submit(() ->
				{
					panel.setCursor(tick);

					return "";
				});

				File ziel = new File(dir, basis + "_" + String.format("%04d", tick) + ".png");
				String json = WorldShot.fangen(ziel.getAbsolutePath());

				if (json.contains("\"path\""))
				{
					geschrieben++;
				}
				else if (ersterFehler == null)
				{
					ersterFehler = json;
				}
			}
		}
		finally
		{
			klammer.zuruecknehmen(cursorVorher);
		}

		long ms = (System.nanoTime() - t0) / 1_000_000L;
		boolean ok = geschrieben == frames;

		StringBuilder sb = new StringBuilder();

		sb.append("{\"ok\":").append(ok)
			.append(",\"film\":\"").append(escape(offen)).append('"')
			.append(",\"start\":").append(start)
			.append(",\"frames\":").append(frames)
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
