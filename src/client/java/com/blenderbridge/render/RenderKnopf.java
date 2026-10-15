// Coded by Afrobama/Claude — Weg A (P7): der EIN-Klick-Worker hinter dem Render-Reiter.
package com.blenderbridge.render;

import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * RenderKnopf — Worker hinter dem Rendering-Bereich der Seitenleiste ({@code UIRenderReiter}):
 * ganzer Film (Tick 0..Dauer-1) als Blender-Composite, auf einem eigenen Thread (die Route
 * wartet auf Blenders RENDER_RESULT — der Render-Thread darf da nie stehen). Ergebnis-Ordner
 * geht bei Erfolg auf; {@link #status} traegt den Zustand fuer die Statuszeile des Reiters.
 *
 * <p>Der Nutzer muss in Blender NICHTS vorbereiten — das Addon richtet sich beim RENDER-Auftrag
 * selbst ein (fps 20, Kamera/Actors frisch baken, Welt holen falls sie fehlt; render_bridge.py).
 * Einzige Voraussetzung: Blender offen, Bridge-Addon verbunden.</p>
 */
public final class RenderKnopf
{
	/** Statuszeile fuer den Reiter (englisch — sichtbarer UI-Text). Volatile: Worker schreibt. */
	public static volatile String status = "";

	/** Genau EIN Lauf zur Zeit — der Reiter-Knopf prueft das mit. */
	public static volatile boolean laeuft;

	private RenderKnopf()
	{
	}

	/** Ganzen Film rendern, asynchron. engine: "EEVEE" oder "CYCLES". */
	public static void starten(UIFilmPanel panel, String engine)
	{
		if (panel == null || panel.getData() == null)
		{
			status = "No film open";

			return;
		}

		if (laeuft)
		{
			status = "A render is already running";

			return;
		}

		final int dauer = panel.getData().camera.calculateDuration();

		if (dauer <= 0)
		{
			status = "Film has no duration (no camera clip?)";

			return;
		}

		laeuft = true;
		status = "Rendering 0.." + (dauer - 1) + " (" + engine + ") ...";

		Thread arbeiter = new Thread(() ->
		{
			try
			{
				String json = RenderComposite.rendern(0, dauer - 1, true, engine);

				System.out.println("[blenderbridge] Render (" + engine + "): " + json);

				int i = json.indexOf("\"outDir\":\"");

				if (json.contains("\"ok\":true") && i >= 0)
				{
					String rest = json.substring(i + 10);
					String ordner = rest.substring(0, rest.indexOf('"')).replace("\\\\", "\\");

					status = "Done -> " + ordner;
					mchorse.bbs_mod.ui.utils.UIUtils.openFolder(new java.io.File(ordner));
				}
				else
				{
					/* Fehlergrund kompakt in die Statuszeile — die Route antwortet IMMER mit Grund. */
					int e = json.indexOf("\"error\":\"");
					status = e >= 0
						? "Failed: " + json.substring(e + 9, Math.min(json.length(), e + 9 + 120)).split("\"")[0]
						: "Failed (see log)";
				}
			}
			finally
			{
				laeuft = false;
			}
		}, "blenderbridge-render");

		arbeiter.setDaemon(true);
		arbeiter.start();
	}
}
