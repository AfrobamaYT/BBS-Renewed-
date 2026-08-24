package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph-Schmuck des Dope Sheets — Entwurf foundations/zeitleiste.html: „keine sichtbare
 * Zeile ohne Inhalt". Gruppen- und Kopfzeilen ziehen ihre Toenung ueber die volle Breite,
 * tragen ein Range-Band vom ersten bis zum letzten Schluessel und zeigen die Schluessel
 * ihrer Kinder als Aggregat-Rauten (Summary-Zeile: alle Kanaele des Graphen). Kanalzeilen
 * bekommen das Range-Band. Aus {@link UIKeyframeDopeSheet} herausgehalten (Monolith).
 */
final class DopeSheetFlaeche
{
	private static final int BAND_BLAU = 0x4772B3;
	private static final int BAND_GRUEN = 0x7CBF6B;

	private final UIKeyframeDopeSheet dope;

	DopeSheetFlaeche(UIKeyframeDopeSheet dope)
	{
		this.dope = dope;
	}

	/** Toenung, Range-Band und Aggregat-Rauten einer Gruppen-/Kopf-/Summary-Zeile. */
	void gruppenZeile(UIContext context, Matrix4f matrix, Area area, UIKeyframeGroup group, int by, int bh)
	{
		if (group.kameraEbene >= 0)
		{
			return;
		}

		int ton = group.summary ? Colors.setA(Colors.WHITE, 0.035F)
			: group.kopf ? Colors.setA(BAND_BLAU, 0.07F) : Colors.setA(BAND_GRUEN, 0.05F);

		context.batcher.box(area.x, by, area.ex(), by + bh, ton);

		List<Keyframe> alle = this.sammle(group);

		if (alle.isEmpty())
		{
			return;
		}

		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		for (Keyframe frame : alle)
		{
			min = Math.min(min, frame.getTick());
			max = Math.max(max, frame.getTick());
		}

		int x1 = Math.max(area.x, this.dope.keyframes.toGraphX(min));
		int x2 = Math.min(area.ex(), this.dope.keyframes.toGraphX(max));

		if (x2 > x1)
		{
			int band = group.kopf || group.summary
				? Colors.setA(Colors.WHITE, 0.055F) : Colors.setA(BAND_GRUEN, 0.10F);

			context.batcher.box(x1, by + 2, x2, by + bh - 2, band);
		}

		this.rauten(context, matrix, area, alle, by + bh / 2);
	}

	/** Range-Band einer Kanalzeile: erster bis letzter Schluessel, dezent hell. */
	void kanalBand(UIContext context, Area area, UIKeyframeSheet sheet, int by, int bh)
	{
		List<?> keyframes = sheet.channel.getKeyframes();

		if (keyframes.size() < 2)
		{
			return;
		}

		int x1 = Math.max(area.x, this.dope.keyframes.toGraphX(((Keyframe<?>) keyframes.get(0)).getTick()));
		int x2 = Math.min(area.ex(), this.dope.keyframes.toGraphX(((Keyframe<?>) keyframes.get(keyframes.size() - 1)).getTick()));

		if (x2 > x1)
		{
			context.batcher.box(x1, by + 2, x2, by + bh - 2, Colors.setA(Colors.WHITE, 0.055F));
		}
	}

	/** Held-Balken (Entwurf/Blender: „Pose steht") + Naeherungs-Unterstrich einer Kanalzeile. */
	void heldBalken(UIContext context, VertexConsumer ecken, Matrix4f matrix, List<?> keyframes, int my, int trackWidth)
	{
		for (int j = 1; j < keyframes.size(); j++)
		{
			Keyframe<?> previous = (Keyframe<?>) keyframes.get(j - 1);
			Keyframe<?> frame = (Keyframe<?>) keyframes.get(j);
			int xx = this.dope.keyframes.toGraphX(previous.getTick());
			int xxx = this.dope.keyframes.toGraphX(frame.getTick());

			if (previous.getFactory().compare(previous.getValue(), frame.getValue()))
			{
				int w = Math.min(trackWidth + 2, 5);
				int c = 0x4E4E4E | Colors.A100;
				int licht = Colors.setA(Colors.WHITE, 0.12F);

				context.batcher.fillRect(ecken, matrix, xx, my - w / 2, xxx - xx, w, c, c, c, c);
				context.batcher.fillRect(ecken, matrix, xx, my - w / 2, xxx - xx, 1, licht, licht, licht, licht);
			}

			/* Blender: gruene Linie zwischen Keys mit Nicht-Standard-Interpolation
			 * (Manual: „Green line between keys — custom interpolation"). */
			mchorse.bbs_mod.utils.interps.IInterp interp = previous.getInterpolation().getInterp();

			if (interp != mchorse.bbs_mod.utils.interps.Interpolations.LINEAR
				&& interp != mchorse.bbs_mod.utils.interps.Interpolations.CONST && xxx > xx)
			{
				int g = Colors.setA(BAND_GRUEN, 0.85F);

				context.batcher.fillRect(ecken, matrix, xx, my + 5, xxx - xx, 1, g, g, g, g);
			}

			if (Math.abs(xxx - xx) < 5)
			{
				int c = Colors.setA(Colors.WHITE, 0.25F);

				context.batcher.fillRect(ecken, matrix, xx - 2, my + trackWidth / 2 + 4, xxx - xx + 4, 2, c, c, c, c);
			}
		}
	}

	/** Rand- und Fuell-Pass einer Kanalzeile — der Topmost-Pfad des Dope Sheets. */
	void markerReihen(UIContext context, VertexConsumer builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y)
	{
		if (!this.dope.isVisible(sheet) || y + this.dope.trackHeight < area.y || y > area.ey())
		{
			return;
		}

		List<?> keyframes = sheet.channel.getKeyframes();
		int my = y + (int) this.dope.trackHeight / 2;

		for (int j = 0; j < keyframes.size(); j++)
		{
			Keyframe<?> frame = (Keyframe<?>) keyframes.get(j);

			this.dope.randMarker(context, builder, matrix, frame, this.dope.keyframes.toGraphX(frame.getTick()), my);
		}

		for (int j = 0; j < keyframes.size(); j++)
		{
			this.dope.fuelleMarker(context, builder, matrix, sheet, j, my);
		}
	}

	/** Aggregat-Rauten: klein, grau, dedupliziert auf 3 px — die Dichte traegt die Info. */
	private void rauten(UIContext context, Matrix4f matrix, Area area, List<Keyframe> alle, int my)
	{
		List<Integer> xs = new ArrayList<>(alle.size());

		for (Keyframe frame : alle)
		{
			int x = this.dope.keyframes.toGraphX(frame.getTick());

			if (x >= area.x - 4 && x <= area.ex() + 4)
			{
				xs.add(x);
			}
		}

		if (xs.isEmpty())
		{
			return;
		}

		xs.sort(null);

		Keyframe muster = alle.get(0);

		context.batcher.netz(FormPipelines.holen(PrimitiveTopology.QUADS, true, false, false), (ecken) ->
		{
			int rand = 0x0F0F0F | Colors.A100;
			int fuellung = 0xB9B9B9 | Colors.A100;
			/* ⛔ NICHT Integer.MIN_VALUE: x - letzt lief ueber und uebersprang JEDE Raute (gemessen). */
			int letzt = -100000;

			for (int x : xs)
			{
				if (x - letzt < 3)
				{
					continue;
				}

				letzt = x;
				UIKeyframeDopeSheet.renderShape(muster, context, ecken, matrix, x, my, 3, rand);
			}

			letzt = -100000;

			for (int x : xs)
			{
				if (x - letzt < 3)
				{
					continue;
				}

				letzt = x;
				UIKeyframeDopeSheet.renderShape(muster, context, ecken, matrix, x, my, 2, fuellung);
			}
		}, UIKeyframeDopeSheet.flaeche(area));
	}

	/** Schluessel der Zeile: Summary nimmt ALLE Kanaele des Graphen, Gruppen ihre Kinder. */
	private List<Keyframe> sammle(UIKeyframeGroup group)
	{
		List<Keyframe> alle = new ArrayList<>();

		if (group.summary)
		{
			for (UIKeyframeSheet sheet : this.dope.getSheets())
			{
				alle.addAll(sheet.channel.getKeyframes());
			}

			return alle;
		}

		this.sammle(group.children, alle);

		return alle;
	}

	private void sammle(List<UIKeyframeElement> elements, List<Keyframe> alle)
	{
		for (UIKeyframeElement element : elements)
		{
			if (element instanceof UIKeyframeSheet sheet)
			{
				alle.addAll(sheet.channel.getKeyframes());
			}
			else if (element instanceof UIKeyframeGroup group)
			{
				this.sammle(group.children, alle);
			}
		}
	}
}
