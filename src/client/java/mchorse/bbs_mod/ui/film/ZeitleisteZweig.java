package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.settings.EditorZustaende;
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
	/** Action clips as a timeline mode where the camera clips sit (Blender: Action Editor); inside
	 *  the replay editor the Nano timeline covered them. */
	public static UIClipsPanel aktionenEditor(UIFilmPanel panel)
	{
		UIClipsPanel editor = new UIClipsPanel(panel, mchorse.bbs_mod.BBSMod.getFactoryActionClips()).target(panel.editArea);
		editor.relative(panel.main).x(0).y(26).w(1F).h(1F, -26).setVisible(false);
		return editor;
	}

	public static void aktionenZeigen(UIFilmPanel panel, boolean sichtbar, boolean mainActive)
	{
		panel.actionEditor.setVisible(sichtbar);
		panel.actionEditor.setTimelineVisible(mainActive && sichtbar);
	}

	public static void aktionenModus(UIFilmPanel panel)
	{
		if (panel.zeitleisteNano instanceof UIZeitleisteNano z)
		{
			z.modusUmschalten(3);
		}
	}

	public static void anwenden(UIFilmPanel panel, int modus)
	{
		if (panel == null || panel.getData() == null)
		{
			return;
		}

		UIElement ziel = modus == 3 ? panel.actionEditor
			: modus == 2 ? panel.cameraEditor : panel.replayEditor;

		/* Kamera- und Action-Clips teilen den Clip-Wirt im Dock: der verborgene Zweig gibt frei. */
		if (ziel != panel.cameraEditor && panel.cameraEditor.getClip() != null)
		{
			panel.cameraEditor.pickClip(null);
		}

		if (ziel != panel.actionEditor && panel.actionEditor.getClip() != null)
		{
			panel.actionEditor.pickClip(null);
		}

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
			panel.actionEditor.target(props.clipHost());
		}

		/* Das Dock lebt unabhaengig vom Zweig — die Kategorie-Schiene regelt, was zu
		 * sehen ist; propertiesVisible haelt nur noch den Kein-Film-Fall dunkel. */
		panel.cameraEditor.setPropertiesVisible(cameraVisible || replayVisible || mainActive);
		panel.actionEditor.setPropertiesVisible(cameraVisible || replayVisible || mainActive);
		panel.replayEditor.setPropertiesVisible(cameraVisible || replayVisible || mainActive);
	}

	/** Wirt fuer den Keyframe-Werte-Editor: die Keyframe-Kategorie des Properties-Docks.
	 *  Ersetzt das tote editArea-Ziel (Akte 38: „replay properties wie pose/transform
	 *  fehlen — kann die keyframes im dock nicht bewegen"). */
	public static mchorse.bbs_mod.ui.framework.elements.UIElement keyframeHost(UIFilmPanel panel)
	{
		if (panel != null && panel.replayEditor != null && panel.replayEditor.replayProperties != null
			&& panel.replayEditor.replayProperties.propsNano instanceof UIPropertiesNano props)
		{
			return props.keyHost();
		}

		return panel == null ? null : panel.editArea;
	}

	/** Magnet fuer das Key-Ziehen. INVERTIERT gespeichert: ein per Hotswap neues
	 *  static-Feld startet mit false — so ist der Magnet nach einem Swap AN (Default). */

	public static boolean istMagnet()
	{
		return !EditorZustaende.zeitleisteMagnetAus.get();
	}

	public static void magnetToggle()
	{
		EditorZustaende.zeitleisteMagnetAus.set(!EditorZustaende.zeitleisteMagnetAus.get());
	}

	/** „Only selected bone" (Blenders Only Show Selected): filtert die Pose-Bone-Zeilen
	 *  des Dope Sheets auf den im Keyframe-Editor aktiven Knochen. Zustand statisch —
	 *  die Editoren werden je Replay-Wechsel neu gebaut, der Filter soll ueberleben. */

	public static boolean istBoneFilter()
	{
		return EditorZustaende.zeitleisteNurBone.get();
	}

	public static void boneFilterToggle(UIFilmPanel panel)
	{
		EditorZustaende.zeitleisteNurBone.set(!EditorZustaende.zeitleisteNurBone.get());
		boneFilterAnwenden(panel);
	}

	/** Fuer den Kopf-Knopf: holt das Panel selbst — als capture-freie Methodenreferenz swap-fest. */
	public static void boneFilterToggle()
	{
		boneFilterToggle(mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null
			: mchorse.bbs_mod.BBSModClient.getDashboard().getPanel(UIFilmPanel.class));
	}

	/** Kanal-Suche (Blenders Ctrl-F im Dope Sheet): filtert die Zeilen nach Namen.
	 *  Lazy gelesen — ein per Hotswap neues static-Feld startet als null. */
	private static String kanalSuche;

	public static String kanalSuche()
	{
		return kanalSuche == null ? "" : kanalSuche;
	}

	public static void kanalSuche(String text)
	{
		kanalSuche = text == null ? "" : text;
	}

	/** Auch nach jedem Editor-Neubau aufrufen — der Neubau verliert das Predicate.
	 *  Wendet Bone-Filter UND Kanal-Suche als EIN Predicate an; die Kennung macht
	 *  den Aufruf je Frame idempotent. */
	public static void boneFilterAnwenden(UIFilmPanel panel)
	{
		if (panel == null || panel.replayEditor == null || panel.replayEditor.keyframeEditor == null)
		{
			return;
		}

		mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet dope =
			panel.replayEditor.keyframeEditor.view.getDopeSheet();

		if (dope == null)
		{
			return;
		}

		String suche = kanalSuche().toLowerCase();

		if (!EditorZustaende.zeitleisteNurBone.get() && suche.isEmpty())
		{
			if (dope.zusatzFilter != null)
			{
				dope.zusatzFilter = null;
				dope.updateScrollSize();
			}

			return;
		}

		String kennung = (EditorZustaende.zeitleisteNurBone.get() ? "b|" : "|") + suche;

		if (dope.zusatzFilter instanceof KanalFilter alt && alt.kennung.equals(kennung))
		{
			return;
		}

		dope.zusatzFilter = new KanalFilter(kennung, EditorZustaende.zeitleisteNurBone.get(), suche, panel.replayEditor);
		/* Ohne Neuvermessung bleibt der Scrollbereich der VOLLEN Liste stehen und der
		 * alte Offset haengt unter dem geschrumpften Inhalt. */
		dope.updateScrollSize();
	}

	/** Eigene Klasse statt Lambda (swap-fest); die Kennung traegt den Filterzustand. */
	private static final class KanalFilter
		implements java.util.function.Predicate<mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet>
	{
		final String kennung;
		private final boolean bone;
		private final String suche;
		private final mchorse.bbs_mod.ui.film.replays.UIReplaysEditor editor;

		KanalFilter(String kennung, boolean bone, String suche, mchorse.bbs_mod.ui.film.replays.UIReplaysEditor editor)
		{
			this.kennung = kennung;
			this.bone = bone;
			this.suche = suche;
			this.editor = editor;
		}

		@Override
		public boolean test(mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet sheet)
		{
			/* Gruppenkoepfe bleiben stehen — sie tragen die Klapp-Bedienung. */
			if (!this.suche.isEmpty() && !sheet.groupHeader)
			{
				String name = sheet.title == null ? sheet.id : sheet.title.get();

				if (!name.toLowerCase().contains(this.suche) && !sheet.id.toLowerCase().contains(this.suche))
				{
					return false;
				}
			}

			if (this.bone)
			{
				mchorse.bbs_mod.film.replays.PerLimbService.PoseBonePath pfad =
					mchorse.bbs_mod.film.replays.PerLimbService.parsePoseBonePath(sheet.id);

				if (pfad != null)
				{
					mchorse.bbs_mod.utils.Pair<String, Boolean> gewaehlt = this.editor.keyframeEditor == null
						? null : this.editor.keyframeEditor.getBone();

					/* Without an active bone there is no selection to restrict to. */
					return gewaehlt == null || pfad.bone().equals(gewaehlt.a);
				}
			}

			return true;
		}
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

		if (panel.zeitleisteNano instanceof UIZeitleisteNano z && z.modus >= 2)
		{
			z.modus = 1;
			z.auffrischen();
		}

		anwenden(panel, 1);
	}
}
