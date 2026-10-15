// Coded by Afrobama/Claude — UI Overhaul Phase 13: 24px dashboard brand header
package com.afrobama.bbstools.client.ui.chrome;

import com.afrobama.bbstools.client.ui.theme.BBSToolsBrand;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Phase 13 — top dashboard chrome row.
 *
 * <p>24 px tall, Adobe-compact density. Three regions:
 * brand mark (left), project name (center, in TEXT_DIM), and an icon row
 * (right) for the two most-used overlays — Settings (Ctrl+,) and Undo
 * History (Ctrl+H). Each icon is
 * 16×16 px and click-opens its associated overlay so users discover
 * features even without learning the shortcuts.</p>
 *
 * <p>Design notes (per `UI_ARCHITECTURE.md`):</p>
 * <ul>
 *   <li>Background {@code BG_DEEP} — the outermost chrome tone</li>
 *   <li>1 px bottom border {@code BORDER_STRONG} divides chrome from content</li>
 *   <li>Brand text uses {@code TEXT} (full white-grey), project uses {@code TEXT_DIM}</li>
 *   <li>Icons render as tiny pictograms — sharp 1-px stroke, no rounded corners,
 *       hover brightens to {@code ACCENT_PRIMARY}</li>
 * </ul>
 */
public class BBSToolsBrandHeader extends UIElement
{
    public static final int HEIGHT = 20;
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 6;
    private static final int RIGHT_PAD = 8;
    private static final int LEFT_PAD = 10;

    /** Cached hover-state indices — recomputed each render. */
    private int hoveredIcon = -1;

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context.mouseX, context.mouseY))
        {
            return super.subMouseClicked(context);
        }
        if (context.mouseButton != 0) return super.subMouseClicked(context);

        /* Hit-test the right-edge icon row from right-to-left so positioning
         * math mirrors the render loop. */
        int rightEdge = this.area.x + this.area.w - RIGHT_PAD;
        int iconY = this.area.y + (HEIGHT - ICON_SIZE) / 2;
        for (int i = 0; i < 2; i++)
        {
            int iconX = rightEdge - (i + 1) * ICON_SIZE - i * ICON_GAP;
            if (context.mouseX >= iconX && context.mouseX < iconX + ICON_SIZE
                && context.mouseY >= iconY && context.mouseY < iconY + ICON_SIZE)
            {
                triggerIcon(i);
                return true;
            }
        }
        return super.subMouseClicked(context);
    }

    /** Map right-edge icon index (0 = rightmost) to its handler. */
    private static void triggerIcon(int index)
    {
        switch (index)
        {
            case 0 -> com.afrobama.bbstools.client.ui.settings.UIBBSToolsSettingsOverlay.open();
            case 1 -> com.afrobama.bbstools.client.ui.undo.UIBBSToolsUndoOverlay.open();
            default -> { /* unreachable */ }
        }
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int x2 = x + this.area.w;
        int y2 = y + HEIGHT;

        /* Background fill + bottom border. */
        context.batcher.box(x, y, x2, y2, BBSToolsTheme.BG_DEEP);
        context.batcher.box(x, y2 - 1, x2, y2, BBSToolsTheme.BORDER_STRONG);

        /* Left: brand mark "BBS-Renewed". */
        int textY = y + (HEIGHT - 8) / 2;
        context.batcher.text(BBSToolsBrand.NAME, x + LEFT_PAD, textY, BBSToolsTheme.TEXT);

        /* Center: project / film name in TEXT_DIM. Truncated by the
         * available space between brand-right-edge and icon-left-edge. */
        String project = currentProjectName();
        if (project != null && !project.isEmpty())
        {
            int textW = context.batcher.getFont().getWidth(project);
            int center = (x + x2) / 2;
            context.batcher.text(project, center - textW / 2, textY, BBSToolsTheme.TEXT_DIM);
        }

        /* Right: 2 icons (Settings / Undo). */
        int rightEdge = x2 - RIGHT_PAD;
        int iconY = y + (HEIGHT - ICON_SIZE) / 2;
        this.hoveredIcon = -1;
        for (int i = 0; i < 2; i++)
        {
            int iconX = rightEdge - (i + 1) * ICON_SIZE - i * ICON_GAP;
            boolean hover = context.mouseX >= iconX && context.mouseX < iconX + ICON_SIZE
                         && context.mouseY >= iconY && context.mouseY < iconY + ICON_SIZE;
            if (hover) this.hoveredIcon = i;
            int color = hover ? BBSToolsTheme.ACCENT_PRIMARY : BBSToolsTheme.TEXT_DIM;
            drawChromeIcon(context, iconX, iconY, i, color);
        }

        super.render(context);
    }

    /** Pull the current film/project label from the open dashboard, with
     *  fall-back to empty string. Best-effort — failures are silent so a
     *  malformed scene doesn't blank the entire header. */
    private static String currentProjectName()
    {
        try
        {
            java.util.List<mchorse.bbs_mod.film.BaseFilmController> ctls =
                mchorse.bbs_mod.BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                mchorse.bbs_mod.film.BaseFilmController ctl = ctls.get(ctls.size() - 1);
                if (ctl != null && ctl.film != null)
                {
                    String fid = ctl.film.getId();
                    return fid == null ? "" : fid;
                }
            }
        }
        catch (Throwable ignored) { /* best-effort */ }
        return "";
    }

    /** Tiny pictograms drawn as 1-px rectangles. Index matches the icon
     *  row order from right (0) to left (1). */
    private static void drawChromeIcon(UIContext context, int x, int y, int index, int color)
    {
        switch (index)
        {
            case 0 -> drawGearIcon(context, x, y, color);       /* settings */
            case 1 -> drawUndoIcon(context, x, y, color);       /* undo */
            default -> { /* unreachable */ }
        }
    }

    /** Settings gear — 8-spoke star pattern in 16×16. */
    private static void drawGearIcon(UIContext context, int x, int y, int color)
    {
        int cx = x + 8;
        int cy = y + 8;
        /* Outer ring (4 spokes) */
        context.batcher.box(cx - 1, y + 2, cx + 1, y + 5, color);
        context.batcher.box(cx - 1, y + 11, cx + 1, y + 14, color);
        context.batcher.box(x + 2, cy - 1, x + 5, cy + 1, color);
        context.batcher.box(x + 11, cy - 1, x + 14, cy + 1, color);
        /* Center ring (donut) */
        context.batcher.box(cx - 3, cy - 3, cx + 3, cy + 3, color);
        context.batcher.box(cx - 1, cy - 1, cx + 1, cy + 1, BBSToolsTheme.BG_DEEP);
    }

    /** Undo arrow — left-curl arrow. */
    private static void drawUndoIcon(UIContext context, int x, int y, int color)
    {
        /* Arrow shaft (horizontal) */
        context.batcher.box(x + 4, y + 7, x + 13, y + 9, color);
        /* Arrowhead (left, 3 layers) */
        context.batcher.box(x + 4, y + 7, x + 5, y + 9, color);
        context.batcher.box(x + 3, y + 6, x + 6, y + 10, color);
        context.batcher.box(x + 2, y + 5, x + 7, y + 11, color);
        /* Curl up tail (right side) */
        context.batcher.box(x + 11, y + 4, x + 13, y + 7, color);
    }

}
