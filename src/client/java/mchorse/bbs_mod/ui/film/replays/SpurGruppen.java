package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bündelt die Spuren des Replays zu den Gruppen des Entwurfs
 * (screens/film-editor.html im Projekt „BBS Design System"):
 * <em>Transform · Pose · Pose Overlay · Items · Shape Keys</em> — dazu die
 * Hand/HUD-Gruppe (Nutzerwunsch 2026-07-28) und „Other" für den Rest der
 * kuratierten Kanäle. Pose, Pose Overlay und Shape Keys bleiben eigene Zeilen
 * (das Pose-Sheet trägt seine aufklappbaren Knochen-Tabs selbst).
 *
 * <p>Die Aufklapp-Mechanik liegt fertig im Dope-Sheet ({@link UIKeyframeGroup});
 * hier wird nur zugeordnet. Jede Gruppe erscheint an der Position ihres ersten
 * Mitglieds; unter zwei Treffern entfällt die Kopfzeile.</p>
 */
public final class SpurGruppen
{
	/* ⛔ Die Zuordnung lebt in switch-Methoden, NICHT in statischen Sets: ein per Hotswap
	 * hinzugefuegtes statisches Feld steht auf null (gemessen 2026-08-24: NPE auf TRANSFORM
	 * riss die ganze Spurliste ab). Ein switch traegt keinen Zustand. */

	/** Bewegungskanäle des Replays — plus das transform-Property der Form (per Grundnamen). */
	private static boolean istTransform(String kern)
	{
		return switch (kern)
		{
			case "x", "y", "z", "yaw", "pitch", "headYaw", "bodyYaw", "vX", "vY", "vZ", "transform" -> true;
			case "scale_x", "scale_y", "scale_z" -> true;
			default -> false;
		};
	}

	private static boolean istItem(String kern)
	{
		return switch (kern)
		{
			case "item_main_hand", "item_head", "item_chest", "item_legs",
				"item_feet", "selected_slot" -> true;
			default -> false;
		};
	}

	/** Offhand, hotbar and hand/HUD tracks share one collapsible inventory group. */
	private static boolean istHandHud(String kern)
	{
		return switch (kern)
		{
			case "item_off_hand", "hotbar_slot_1", "hotbar_slot_2", "hotbar_slot_3", "hotbar_slot_4", "hotbar_slot_5",
				"hotbar_slot_6", "hotbar_slot_7", "hotbar_slot_8", "hotbar_slot_9",
				"health", "hunger", "show_hand", "show_hud",
				"using_item", "item_use_time", "active_hand" -> true;
			default -> false;
		};
	}

	/**
	 * Aufgeklappt-Zustände, absichtlich statisch: die Spurliste wird bei jedem Replay-Wechsel
	 * und jeder Filter-Änderung komplett neu gebaut; am Editor gehalten klappte alles wieder zu.
	 * NICHT final und lazy angelegt — ein neues statisches Feld steht nach einem Hotswap auf null.
	 */
	private static Map<String, Boolean> aufgeklappt;

	private static Map<String, Boolean> klappZustand()
	{
		if (aufgeklappt == null)
		{
			aufgeklappt = new HashMap<>();
		}

		return aufgeklappt;
	}

	private SpurGruppen()
	{
	}

	/**
	 * The actor's own pose track and its overlays first, everything else in its order: the pose
	 * is what an animator keys most, and it was row 55 of 63 for a model actor, below the edge
	 * (measured 2026-10-15). BBS FS shows pose and transform first in its pose category.
	 */
	private static List<UIKeyframeSheet> poseZuerst(List<UIKeyframeSheet> sheets)
	{
		List<UIKeyframeSheet> reihe = new ArrayList<>(sheets.size());

		for (UIKeyframeSheet sheet : sheets)
		{
			if (sheet.id.equals("pose") || sheet.id.startsWith("pose_overlay"))
			{
				reihe.add(sheet);
			}
		}

		for (UIKeyframeSheet sheet : sheets)
		{
			if (!reihe.contains(sheet))
			{
				reihe.add(sheet);
			}
		}

		return reihe;
	}

	/** Topf-Name für ein Sheet, oder null = bleibt eigene Zeile (Pose, Overlays, Shape Keys …). */
	private static String topf(UIKeyframeSheet sheet)
	{
		/* Form-Properties matchen per Grundnamen: bei mehreren Formen heißt die Id „<pfad>/transform". */
		String kern = sheet.id.contains("/") ? sheet.id.substring(sheet.id.lastIndexOf('/') + 1) : sheet.id;

		/* The extra transform tracks sit with the transform, the pose overlays together (BBS FS 8ce6f0238). */
		if (istTransform(kern) || kern.startsWith("transform_overlay"))
		{
			return "Transform";
		}

		if (kern.startsWith("pose_overlay"))
		{
			return "Pose Overlay";
		}

		if (istItem(kern))
		{
			return "Items";
		}

		if (istHandHud(kern))
		{
			return "Hand/HUD";
		}

		/* Übrige kuratierte Kanäle (sneaking, fall, sticks, shadow …) — Properties nie. */
		return sheet.property == null ? "Other" : null;
	}

	/**
	 * Hängt die Spuren in den Editor — Entwurfsstruktur (screens/film-editor.html):
	 * Kamera-Kopfzeilen, dann je Replay ein Kopf; das gewählte trägt seine Gruppen als
	 * Kinder, fremde stehen zugeklappt und wählen sich beim Aufklappen aus.
	 */
	public static void einfuegen(UIKeyframes view, List<UIKeyframeSheet> sheets)
	{
		for (UIKeyframeSheet sheet : sheets)
		{
			if (sheet.id.equals("item_off_hand"))
			{
				sheet.title = IKey.constant("Offhand Slot");
			}
		}

		if (!gruppierungAn())
		{
			for (UIKeyframeSheet sheet : sheets)
			{
				view.addSheet(sheet);
			}

			return;
		}

		mchorse.bbs_mod.ui.film.UIFilmPanel panel =
			mchorse.bbs_mod.BBSModClient.getDashboard() == null ? null
				: mchorse.bbs_mod.BBSModClient.getDashboard().getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);
		mchorse.bbs_mod.film.Film film = panel == null ? null : panel.getData();

		/* Kamera-Kopfzeilen zuoberst — eine je belegter Ebene. KameraSpuren zeichnet die
		 * Clips auf Zeile==Ebene; mit den Koepfen ganz oben treffen die Balken genau diese
		 * Zeilen statt wie bisher als Geister ueber den ersten Kanaelen zu liegen. */
		if (film != null)
		{
			int ebenen = 0;

			for (mchorse.bbs_mod.utils.clips.Clip c : film.camera.get())
			{
				ebenen = Math.max(ebenen, c.layer.get() + 1);
			}

			for (int e = 0; e < ebenen; e++)
			{
				UIKeyframeGroup kamera = new UIKeyframeGroup(
					IKey.constant(ebenen == 1 ? "Camera" : "Camera " + (e + 1)), 0);

				kamera.kopf = true;
				kamera.kameraEbene = e;
				kamera.onToggle = () -> {
					if (panel.zeitleisteNano instanceof mchorse.bbs_mod.ui.film.UIZeitleisteNano z)
					{
						z.modusUmschalten(2);
					}
				};

				view.addElement(kamera);
			}
		}

		/* Summary-Zeile (Entwurf, Blenders show_summary): alle Schluessel des gewaehlten
		 * Replays aggregiert, direkt unter den Kamera-Spuren. Die Rauten zeichnet
		 * DopeSheetFlaeche aus view.getSheets() — Kinder braucht die Zeile keine. */
		int schluessel = 0;

		for (UIKeyframeSheet s : sheets)
		{
			schluessel += s.channel.getKeyframes().size();
		}

		if (schluessel > 0)
		{
			UIKeyframeGroup summary = new UIKeyframeGroup(IKey.constant("Summary"), 0);

			summary.summary = true;
			summary.zaehler = String.valueOf(schluessel);
			view.addElement(summary);
		}

		Map<String, List<UIKeyframeSheet>> toepfe = new LinkedHashMap<>();

		for (UIKeyframeSheet sheet : sheets)
		{
			String name = topf(sheet);

			if (name != null)
			{
				toepfe.computeIfAbsent(name, (k) -> new ArrayList<>()).add(sheet);
			}
		}

		/* Unter zwei Treffern lohnt keine Kopfzeile — sie kostete mehr Zeilen als sie spart. */
		toepfe.values().removeIf((liste) -> liste.size() < 2);

		Map<String, UIKeyframeGroup> gruppen = new HashMap<>();

		for (Map.Entry<String, List<UIKeyframeSheet>> topf : toepfe.entrySet())
		{
			String name = topf.getKey();
			UIKeyframeGroup gruppe = new UIKeyframeGroup(titel(name), topf.getValue().get(0).color);

			gruppe.collapsed = !klappZustand().getOrDefault(name, false);
			gruppe.onToggle = () -> klappZustand().put(name, !gruppe.collapsed);

			for (UIKeyframeSheet sheet : topf.getValue())
			{
				gruppe.add(sheet);
			}

			gruppen.put(name, gruppe);
		}

		mchorse.bbs_mod.film.replays.Replay gewaehlt =
			panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();
		UIKeyframeGroup kopf = gewaehlt == null ? null
			: new UIKeyframeGroup(IKey.constant(gewaehlt.getName()), 0);
		Set<String> eingehaengt = new HashSet<>();

		if (kopf != null)
		{
			kopf.kopf = true;
			kopf.labelFarbe = gewaehlt.labelColor == null ? 0 : gewaehlt.labelColor.get(); /* null nach Hotswap (Add-Field) */
			kopf.collapsed = false;
			kopf.auge = gewaehlt.enabled::get;
		}

		for (UIKeyframeSheet sheet : poseZuerst(sheets))
		{
			String name = topf(sheet);
			UIKeyframeElement element = name == null || !gruppen.containsKey(name) ? sheet
				: eingehaengt.add(name) ? gruppen.get(name) : null;

			if (element == null)
			{
				continue;
			}

			if (kopf != null)
			{
				kopf.add(element);
			}
			else if (element instanceof UIKeyframeSheet einzeln)
			{
				view.addSheet(einzeln);
			}
			else
			{
				view.addElement(element);
			}
		}

		/* ⛔ Den Kopf erst JETZT anmelden: addElement() flacht die Kinder in die
		 * sheets-Liste — ein leerer Kopf liess getSheet(Keyframe) und alle Index-Pfade
		 * ins Leere laufen (gemessen: sheet=null, Regressionslauf T8). */
		if (kopf != null)
		{
			view.addElement(kopf);
		}

		/* Fremde Replays: zugeklappte Koepfe mit Kanalzahl — Aufklappen waehlt sie aus,
		 * der Neubau der Liste laedt dann ihre Kanaele (Blender: das aktive Objekt). */
		if (film != null && panel.replayEditor != null)
		{
			for (mchorse.bbs_mod.film.replays.Replay replay : film.replays.getList())
			{
				if (replay == gewaehlt)
				{
					continue;
				}

				int kanaele = 0;

				for (mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> k : replay.keyframes.getChannels())
				{
					if (!k.getKeyframes().isEmpty())
					{
						kanaele++;
					}
				}

				UIKeyframeGroup fremd = new UIKeyframeGroup(IKey.constant(replay.getName()), 0);

				fremd.kopf = true;
				fremd.labelFarbe = replay.labelColor == null ? 0 : replay.labelColor.get();
				fremd.auge = replay.enabled::get;
				fremd.zaehler = kanaele + (kanaele == 1 ? " channel" : " channels");
				fremd.onToggle = () -> panel.replayEditor.replaysList.replays.scrollToReplay(replay);

				view.addElement(fremd);
			}
		}
	}

	/**
	 * Right click on a pose or transform track, or on one of its overlays: one more track of that
	 * kind. The count is global, so every form gets it, and the group opens to show it (BBS FS
	 * 8ce6f0238, Wemppy4/bbs-fs, MIT).
	 */
	public static void overlayAnbieten(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu, UIKeyframeSheet sheet, Runnable neuBauen)
	{
		mchorse.bbs_mod.forms.forms.Form form = sheet == null || sheet.property == null ? null : mchorse.bbs_mod.forms.FormUtils.getForm(sheet.property);
		String kern = sheet == null ? "" : sheet.id.substring(sheet.id.lastIndexOf('/') + 1);
		boolean pose = kern.equals("pose") || kern.startsWith("pose_overlay");
		boolean posiert = form instanceof mchorse.bbs_mod.forms.forms.ModelForm || form instanceof mchorse.bbs_mod.forms.forms.MobForm;

		if (form == null || (pose ? !posiert : !(kern.equals("transform") || kern.startsWith("transform_overlay"))))
		{
			return;
		}

		mchorse.bbs_mod.settings.values.numeric.ValueInt anzahl = pose ? BBSSettings.recordingPoseOverlays : BBSSettings.recordingTransformOverlays;

		if (anzahl.get() >= anzahl.getMax())
		{
			return;
		}

		menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.ADD, mchorse.bbs_mod.l10n.L10n.lang(pose ? "bbs.ui.keyframes.context.add_pose_track" : "bbs.ui.keyframes.context.add_transform_track"), () ->
		{
			anzahl.set(anzahl.get() + 1);
			form.syncOverlayTracks();
			klappZustand().put(pose ? "Pose Overlay" : "Transform", true);
			neuBauen.run();
		});
	}

	/** Hand/HUD hat einen Sprachschlüssel; die Entwurfs-Gruppen heißen wie im Entwurf. */
	private static IKey titel(String name)
	{
		if ("Hand/HUD".equals(name))
		{
			IKey key = UIKeys.FILM_REPLAY_GROUP_HAND_HUD;

			return key == null ? IKey.constant(name) : key;
		}

		return IKey.constant(name);
	}

	/**
	 * ⚠ Der Schalter ist ein neues statisches Feld. Beim Hotswap laufen statische Initialisierer
	 * nicht erneut, das Feld bleibt dann null und würde die ganze Spurliste mit einer NPE
	 * abreißen. Im Zweifel gruppieren — das ist der Zustand, den der Nutzer bestellt hat.
	 */
	private static boolean gruppierungAn()
	{
		try
		{
			return BBSSettings.editorGroupHandHudTracks.get();
		}
		catch (Throwable t)
		{
			return true;
		}
	}
}
