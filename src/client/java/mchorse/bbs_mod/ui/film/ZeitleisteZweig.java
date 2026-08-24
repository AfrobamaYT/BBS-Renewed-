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
	 * Lineal-Dekor des Replay-Dope-Sheets: der alte Ruler-Weg (Audio-Wellenform,
	 * Clip-Gradient). Die Film-Merkstellen zeichnet UIFilmKeyframes.renderOverlay —
	 * der Decorator hier laeuft VOR den Skalen-Ticks, dort uebermalten sie die Koepfe.
	 */
	public static void linealDekor(mchorse.bbs_mod.ui.framework.UIContext context,
		mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes view,
		UIClipsPanel cameraEditor, mchorse.bbs_mod.film.Film film)
	{
		mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.renderRuler(context, view, cameraEditor, film.camera, 0);
	}

	/**
	 * Clip-Eigenschaften sichtbar schalten. Seit editArea aus dem Dock-Layout gefallen
	 * ist (Migration setzt Layouts mit editArea auf den Default zurueck), waren die
	 * Kamera-Clip-Felder NIRGENDS mehr zu sehen — „die camera properties fehlen
	 * komplett" (Akte 38). Sie wohnen jetzt in der Kamera-Kategorie des Properties-
	 * Docks (clipHost der UIPropertiesNano) — Nutzerwunsch: „nicht in der Timeline,
	 * am besten links an diesem doc wo Transform und so steht".
	 */
	public static void propertiesSchalten(UIFilmPanel panel, boolean mainActive,
		boolean editAreaActive, boolean cameraVisible, boolean replayVisible)
	{
		if (!editAreaActive && panel.replayEditor.replayProperties != null
			&& panel.replayEditor.replayProperties.propsNano instanceof UIPropertiesNano props)
		{
			panel.cameraEditor.target(props.clipHost());
		}

		/* Das Dock lebt unabhaengig vom Zweig — die Kategorie-Schiene regelt, was zu
		 * sehen ist; propertiesVisible haelt nur noch den Kein-Film-Fall dunkel. */
		panel.cameraEditor.setPropertiesVisible(cameraVisible || replayVisible || mainActive);
		panel.replayEditor.setPropertiesVisible(editAreaActive && replayVisible);
	}

	/** „Only selected bone" (Blenders Only Show Selected): filtert die Pose-Bone-Zeilen
	 *  des Dope Sheets auf den im Keyframe-Editor aktiven Knochen. Zustand statisch —
	 *  die Editoren werden je Replay-Wechsel neu gebaut, der Filter soll ueberleben. */
	private static boolean nurBone;

	public static boolean istBoneFilter()
	{
		return nurBone;
	}

	public static void boneFilterToggle(UIFilmPanel panel)
	{
		nurBone = !nurBone;
		boneFilterAnwenden(panel);
	}

	/** Auch nach jedem Editor-Neubau aufrufen — der Neubau verliert das Predicate. */
	public static void boneFilterAnwenden(UIFilmPanel panel)
	{
		if (panel == null || panel.replayEditor == null || panel.replayEditor.keyframeEditor == null)
		{
			return;
		}

		mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet dope =
			panel.replayEditor.keyframeEditor.view.getDopeSheet();

		if (dope == null || (dope.zusatzFilter != null) == nurBone)
		{
			return;
		}

		final mchorse.bbs_mod.ui.film.replays.UIReplaysEditor editor = panel.replayEditor;

		dope.zusatzFilter = !nurBone ? null
			: new java.util.function.Predicate<mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet>()
		{
			@Override
			public boolean test(mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet sheet)
			{
				mchorse.bbs_mod.film.replays.PerLimbService.PoseBonePath pfad =
					mchorse.bbs_mod.film.replays.PerLimbService.parsePoseBonePath(sheet.id);

				if (pfad == null)
				{
					return true;
				}

				mchorse.bbs_mod.utils.Pair<String, Boolean> bone = editor.keyframeEditor == null
					? null : editor.keyframeEditor.getBone();

				return bone != null && pfad.bone().equals(bone.a);
			}
		};
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
