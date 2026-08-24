package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mcstudio.nano.NanoDesign;
import com.mojang.blaze3d.vertex.BufferBuilder;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Die Kanalspalte des Dope Sheets — Entwurf screens/film-editor.html: CHANNELS-Kopf im
 * Linealband, Ebenen-Toenung (Koepfe dunkelblau, Gruppen gruen), Zaehler in Tabellenziffern,
 * dezente Achsstreifen an x/y/z. Aus {@link UIKeyframeDopeSheet} ausgelagert (Monolith-Schnitt).
 */
final class DopeSheetSpalte
{
	private final UIKeyframeDopeSheet dope;

	DopeSheetSpalte(UIKeyframeDopeSheet dope)
	{
		this.dope = dope;
	}

	void render(UIContext context, BufferBuilder builder, Matrix4f matrix, List<UIKeyframeElement> elements, int offset, int y)
	{
		Area area = this.dope.keyframes.area;
		int w = this.dope.keyframes.getLabelWidth();

		/* Spaltengrund im Nano-Ton (0x191919) — ohne ihn stand die alte Haut durch,
		 * die Namen lagen auf dem Editor-Grund. */
		context.batcher.box(area.x, area.y, area.x + w, area.ey(), 0xFF191919);
		context.batcher.box(area.x + w - 1, area.y, area.x + w, area.ey(), 0xFF0D0D0D);

		/* Spaltenkopf im Linealband — „CHANNELS" wie im Entwurf und im Kamera-Zweig. */
		int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);

		context.batcher.text("CHANNELS", area.x + 8,
			area.y + (rulerBottom - area.y - context.batcher.getFont().getHeight()) / 2, 0xFF616161);

		context.batcher.clip(area.x, rulerBottom, area.x + w, area.ey(), context);
		this.zeilen(context, builder, matrix, elements, offset, y);
		context.batcher.unclip(context);
	}

	/* ⛔ Grund/CHANNELS/Clip NUR im Wurzelaufruf — die Rekursion malte den Grund je
	 * Abstieg neu und uebermalte alle Zeilen davor (Koepfe unsichtbar, gemessen). */
	private void zeilen(UIContext context, BufferBuilder builder, Matrix4f matrix, List<UIKeyframeElement> elements, int offset, int y)
	{
		Area area = this.dope.keyframes.area;
		int w = this.dope.keyframes.getLabelWidth();

		for (UIKeyframeElement element : elements)
		{
			if (element instanceof UIKeyframeSheet sheet)
			{
				if (this.dope.isVisible(sheet))
				{
					this.zeile(context, area, sheet, offset, y, w);
				}
			}
			else if (element instanceof UIKeyframeGroup group)
			{
				this.gruppe(context, area, group, offset, y, w);

				/* Kinder direkt UNTER der Kopfzeile — wie calculateLayout und clickElements. */
				if (!group.collapsed)
				{
					this.zeilen(context, builder, matrix, group.children, offset + 10, y + (int) this.dope.trackHeight);
				}
			}

			y += this.dope.getElementHeight(element);
		}
	}

	private void gruppe(UIContext context, Area area, UIKeyframeGroup group, int offset, int y, int w)
	{
		int summe = group.children.stream().filter(UIKeyframeSheet.class::isInstance)
			.mapToInt((k) -> ((UIKeyframeSheet) k).channel.getKeyframes().size()).sum();

		/* Blender-Farbcode der Ebenen: Koepfe (Replay/Kamera) dunkelblau, Gruppen gruen,
		 * die Summary-Zeile neutral hell (Entwurf .cr.sum). */
		context.batcher.box(area.x, y, area.x + w, y + (int) this.dope.trackHeight,
			group.summary ? Colors.setA(Colors.WHITE, 0.05F)
				: group.kopf ? Colors.setA(0x4772B3, 0.14F) : Colors.setA(NanoDesign.GRUEN, 0.13F));

		this.label(context, area, y, w, summe > 0 ? String.valueOf(summe) : group.zaehler,
			group.title.get(), group.children.isEmpty() ? null : !group.collapsed, group.auge, offset, true);
	}

	private void zeile(UIContext context, Area area, UIKeyframeSheet sheet, int offset, int y, int w)
	{
		String kern = sheet.id.contains("/") ? sheet.id.substring(sheet.id.lastIndexOf('/') + 1) : sheet.id;
		int achse = "x".equals(kern) || "vX".equals(kern) ? NanoDesign.ACHSE_X
			: "y".equals(kern) || "vY".equals(kern) ? NanoDesign.ACHSE_Y
			: "z".equals(kern) || "vZ".equals(kern) ? NanoDesign.ACHSE_Z : 0;

		if (achse != 0)
		{
			context.batcher.box(area.x, y, area.x + 2, y + (int) this.dope.trackHeight, achse | Colors.A100);
		}

		/* Entwurfszeile .cr.sel: die zuletzt angefasste Spur traegt die blaue Toenung. */
		if (sheet == this.dope.lastSheet)
		{
			context.batcher.box(area.x, y, area.x + w, y + (int) this.dope.trackHeight,
				Colors.setA(0x4772B3, 0.32F));
		}

		List keys = sheet.channel.getKeyframes();
		String zaehler = String.valueOf(keys.size());

		if (!keys.isEmpty() && ((Keyframe) keys.get(0)).getValue() instanceof mchorse.bbs_mod.utils.pose.Pose pose
			&& !pose.transforms.isEmpty())
		{
			zaehler = pose.transforms.size() + " bones · " + zaehler;
		}

		String titel = sheet.title.get();

		/* Property-Titel im Klartext: pose_overlay -> Pose Overlay (Kanaele bleiben roh). */
		if (sheet.property != null && !titel.isEmpty())
		{
			titel = java.util.Arrays.stream(titel.split("_"))
				.map((t) -> t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1))
				.collect(java.util.stream.Collectors.joining(" "));
		}

		boolean poseKlapp = this.dope.isPoseTabParent(sheet) && this.dope.trackHeight >= 12D;

		this.label(context, area, y, w - (poseKlapp ? 14 : 0), zaehler, titel, null, null,
			offset + this.dope.getSheetIndent(sheet), false);

		/* Pose-Knochen-Klapp bleibt RECHTS: die Klickzone (isPoseTabArrowHit) rechnet dort. */
		if (poseKlapp)
		{
			this.chevron(context,
				area.x + w - UIKeyframeDopeSheet.LABEL_RIGHT_PAD - UIKeyframeDopeSheet.LABEL_ICON_SIZE / 2 - 2,
				y + (int) this.dope.trackHeight / 2, this.dope.expandedPoseTabs.contains(sheet));
		}
	}

	/** Duennes Vektor-Chevron (Entwurf) statt des dicken Atlas-Pfeils. */
	private void chevron(UIContext context, int cx, int cy, boolean offen)
	{
		int c = NanoDesign.TXT_DIM | Colors.A100;

		if (offen)
		{
			context.batcher.polygon(new float[] {cx - 4, cx + 4, cx}, new float[] {cy - 2, cy - 2, cy + 3}, c);
		}
		else
		{
			context.batcher.polygon(new float[] {cx - 2, cx - 2, cx + 3}, new float[] {cy - 4, cy + 4, cy}, c);
		}
	}

	/* Entwurf foundations/zeitleiste.html (.cr): Chevron und Name links, Zaehler und Auge
	 * rechts in Tabellenziffern — kein Farbbalken, kein Kanal-Icon (Akte 36). */
	private void label(UIContext context, Area area, int y, int w, String zaehler,
		String titel, Boolean klapp, java.util.function.BooleanSupplier auge, int einzug, boolean gruppe)
	{
		if (y + this.dope.trackHeight < area.y || y > area.ey())
		{
			return;
		}

		int h = (int) this.dope.trackHeight;
		boolean hover = area.isInside(context) && context.mouseY >= y && context.mouseY < y + h;

		if (hover)
		{
			context.batcher.box(area.x, y, area.x + w, y + h, Colors.setA(Colors.WHITE, 0.05F));
		}

		int textColor = gruppe
			? (hover ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.75F))
			: (hover ? NanoDesign.TXT : NanoDesign.TXT_DIM);
		int textX = area.x + UIKeyframeDopeSheet.LABEL_TEXT_LEFT + einzug;
		FontRenderer font = context.batcher.getFont();
		int my = y + h / 2;
		int rechts = area.x + w - UIKeyframeDopeSheet.LABEL_RIGHT_PAD;

		/* Klapp-Chevron LINKS vor dem Namen — wie im Entwurf (.cr .chev). */
		if (klapp != null)
		{
			this.chevron(context, textX + 3, my, klapp);
			textX += 11;
		}

		if (auge != null)
		{
			Icon symbol = auge.getAsBoolean() ? Icons.VISIBLE : Icons.INVISIBLE;

			context.batcher.icon(symbol, Colors.A100 | (NanoDesign.TXT_OFF & 0xFFFFFF),
				rechts - UIKeyframeDopeSheet.LABEL_ICON_SIZE / 2, my, 0.5F, 0.5F);
			rechts -= UIKeyframeDopeSheet.LABEL_ICON_SIZE + UIKeyframeDopeSheet.LABEL_TEXT_ICON_GAP;
		}

		if (!zaehler.isEmpty() && !"0".equals(zaehler))
		{
			int zb = font.getWidth(zaehler);

			context.batcher.text(zaehler, rechts - zb, my - font.getHeight() / 2, NanoDesign.TXT_OFF);
			rechts -= zb + UIKeyframeDopeSheet.LABEL_TEXT_ICON_GAP;
		}

		context.batcher.text(font.limitToWidth(titel, Math.max(0, rechts - textX)),
			textX, my - font.getHeight() / 2, textColor);
	}
}
