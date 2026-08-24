package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Schaltet den KOERPER der Zeitleiste passend zum Modus im Nano-Kopf.
 *
 * <p>Die Nano-Zeitleiste ist nur der Kopf (Transport, Modus-Waehler, Kanalfilter); den
 * Koerper stellt je Modus ein alter Editor mit seiner kompletten Bedienlogik — fuer
 * Kamera-Clips das {@code UIClips} des Kamera-Editors, fuer Replay-Keyframes der Dope
 * Sheet des Replay-Editors. Bis 2026-08-24 stellte der Modus-Waehler nur ein Feld um,
 * das nie gezeichnet wurde (der eigene Spur-Zeichenpfad liegt hinter {@code nurKopf()}
 * dauerhaft still): der Nutzer sah in jedem Modus Kamera-Clips, und der Dope Sheet war
 * seit dem Ende der alten Timeline unerreichbar — „ich kann so nicht animieren".</p>
 */
public final class ZeitleisteZweig
{
	private ZeitleisteZweig()
	{
	}

	/** Koerper zum Modus zeigen: 0/1 = Replay-Dope-Sheet, 2 = Kamera-Clips. */
	public static void anwenden(UIFilmPanel panel, int modus)
	{
		if (panel == null || panel.getData() == null)
		{
			return;
		}

		UIElement ziel = modus == 2 ? panel.cameraEditor : panel.replayEditor;

		/* isVisible() ist die eigene Flagge des Panels; updateMainEditorVisibility setzt
		 * sie exakt fuer den aktiven Zweig — als Wechsel-Pruefung reicht sie, und sie
		 * erspart ein teures showPanel() (Viewport-Capture) beim Klick auf den Modus,
		 * der schon steht. */
		if (!ziel.isVisible())
		{
			panel.showPanel(ziel);
		}
	}

	/**
	 * Lineal-Dekor des Replay-Dope-Sheets: erst der alte Ruler-Weg (Audio-Wellenform,
	 * Clip-Gradient), dann die Film-Merkstellen als Nur-Anzeige (Entwurf zeigt die
	 * Dreiecke in BEIDEN Zweigen; bedient werden sie in der Kamera-Zeitleiste).
	 */
	public static void linealDekor(mchorse.bbs_mod.ui.framework.UIContext context,
		mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes view,
		UIClipsPanel cameraEditor, mchorse.bbs_mod.film.Film film)
	{
		mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.renderRuler(context, view, cameraEditor, film.camera, 0);
		mchorse.bbs_mod.ui.film.markers.UIMarkerLane.zeichneImLineal(context, view.graphArea, film.markers, view::toGraphX);
	}

	/**
	 * Ring-Grab im Viewport: Animier-Bereich zeigen und den Kopf-Modus mitziehen.
	 * Der reine Ring-KLICK wechselt bewusst nicht (Nutzer 2026-08-23: Klick riss ihn
	 * in die alte Timeline; Blender wechselt bei Bone-Auswahl auch keinen Editor).
	 */
	public static void animationOeffnen(UIFilmPanel panel)
	{
		if (panel == null)
		{
			return;
		}

		if (panel.zeitleisteNano instanceof UIZeitleisteNano z && z.modus == 2)
		{
			z.modus = 1;
			z.auffrischen();
		}

		anwenden(panel, 1);
	}
}
