package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

/**
 * Keyframe-Typen nach Blenders Muster (Key -> Keyframe Type): der TYP ist eine Farbe,
 * die die Rolle der Pose markiert — Breakdown, Extreme, Jitter, Moving Hold. Technisch
 * ist das unsere bestehende Custom-Farbe; hier gibt es nur die feste Palette als
 * Ein-Klick-Menue. Ausgelagert aus {@link UIKeyframes} (Monolith), zusammen mit dem
 * Runden-Eintrag desselben Menues.
 */
final class KeyframeTypen
{
	/** {Name, RGB} — an Blenders Theme-Farben angelehnt, auf unserem Grund lesbar. */
	private static final Object[][] TYPEN = {
		{"Keyframe", null},
		{"Breakdown", 0x6FC1D8},
		{"Extreme", 0xE06A60},
		{"Jitter", 0x8FCE7A},
		{"Moving Hold", 0xD8A03F},
	};

	private KeyframeTypen()
	{
	}

	/** Untermenue „Keyframe Type" fuer die aktuelle Auswahl. */
	static void menue(ContextMenuManager menu, UIKeyframes keyframes)
	{
		for (Object[] typ : TYPEN)
		{
			String name = (String) typ[0];
			Integer rgb = (Integer) typ[1];

			menu.action(rgb == null ? Icons.REFRESH : Icons.MATERIAL,
				IKey.raw("Type: " + name), rgb == null ? 0xFFFFFF : rgb,
				() -> setzeTyp(keyframes, rgb));
		}
	}

	private static void setzeTyp(UIKeyframes keyframes, Integer rgb)
	{
		for (UIKeyframeSheet sheet : keyframes.getGraph().getSheets())
		{
			List<Keyframe> selected = sheet.selection.getSelected();

			if (selected.isEmpty())
			{
				continue;
			}

			sheet.channel.preNotify();

			for (Keyframe kf : selected)
			{
				kf.setColor(rgb == null ? null : new Color().set(rgb, false));
			}

			sheet.channel.postNotify();
		}
	}

	/** Der Runden-Eintrag (aus dem UIKeyframes-Kontextmenue hierher verschoben). */
	static void runden(UIKeyframes keyframes)
	{
		for (UIKeyframeSheet sheet : keyframes.getGraph().getSheets())
		{
			List<Keyframe> selected = sheet.selection.getSelected();

			if (selected.isEmpty())
			{
				continue;
			}

			sheet.channel.preNotify();

			for (Keyframe kf : selected)
			{
				kf.setTick(Math.round(kf.getTick()), false);
			}

			sheet.channel.postNotify();
		}
	}
}
