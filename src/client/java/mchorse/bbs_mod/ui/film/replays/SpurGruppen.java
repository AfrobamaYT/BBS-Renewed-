package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
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
			default -> false;
		};
	}

	private static boolean istItem(String kern)
	{
		return switch (kern)
		{
			case "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs",
				"item_feet", "selected_slot" -> true;
			default -> false;
		};
	}

	/** Hand- und HUD-Spuren (Nutzerwunsch 2026-07-28) — ohne die Items, die haben ihre eigene Gruppe. */
	private static boolean istHandHud(String kern)
	{
		return switch (kern)
		{
			case "hotbar_slot_1", "hotbar_slot_2", "hotbar_slot_3", "hotbar_slot_4", "hotbar_slot_5",
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

	/** Topf-Name für ein Sheet, oder null = bleibt eigene Zeile (Pose, Overlays, Shape Keys …). */
	private static String topf(UIKeyframeSheet sheet)
	{
		/* Form-Properties matchen per Grundnamen: bei mehreren Formen heißt die Id „<pfad>/transform". */
		String kern = sheet.id.contains("/") ? sheet.id.substring(sheet.id.lastIndexOf('/') + 1) : sheet.id;

		if (istTransform(kern))
		{
			return "Transform";
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
	 * Hängt die Spuren in den Editor — gruppiert nach dem Entwurf, alle übrigen unverändert
	 * einzeln in ihrer Reihenfolge.
	 */
	public static void einfuegen(UIKeyframes view, List<UIKeyframeSheet> sheets)
	{
		if (!gruppierungAn())
		{
			for (UIKeyframeSheet sheet : sheets)
			{
				view.addSheet(sheet);
			}

			return;
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

		Set<String> eingehaengt = new HashSet<>();

		for (UIKeyframeSheet sheet : sheets)
		{
			String name = topf(sheet);

			if (name == null || !gruppen.containsKey(name))
			{
				view.addSheet(sheet);
			}
			else if (eingehaengt.add(name))
			{
				view.addElement(gruppen.get(name));
			}
		}
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
