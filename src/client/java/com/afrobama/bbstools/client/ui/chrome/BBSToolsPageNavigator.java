// Coded by Afrobama/Claude — UI Overhaul Phase 13: 36px DaVinci-style page navigator
package com.afrobama.bbstools.client.ui.chrome;

import com.afrobama.bbstools.client.ui.anim.BBSToolsAnim;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 13 — bottom horizontal page navigator strip.
 *
 * <p>Inspired by DaVinci Resolve's Media / Cut / Edit / Fusion / Color /
 * Fairlight / Deliver page bar — but multi-accent. Each registered page
 * has a category-specific accent color used for the 2 px underline on
 * the active state, so the user gets a color cue for which dashboard
 * panel they're in.</p>
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li>36 px tall, full-width, background {@code BG_PANEL}</li>
 *   <li>1 px top border {@code BORDER_STRONG}, 1 px bottom border {@code BORDER}</li>
 *   <li>Each entry: 16 px icon + 6 px gap + label text, 8 px horizontal padding</li>
 *   <li>Hover row background {@code BG_HOVER}, active row keeps base bg</li>
 *   <li>Active page underline is 2 px tall, sits flush with the bottom border</li>
 * </ul>
 */
public class BBSToolsPageNavigator extends UIElement
{
    /** Slim chrome — clean / minimalistic per design feedback. Icon-only
     *  by default; label appears as a hover overlay above the strip. */
    public static final int HEIGHT = 22;
    private static final int ENTRY_W = 28;
    private static final int ICON_SIZE = 14;
    private static final int UNDERLINE_PX = 2;

    /** All registered pages, in display order. */
    private final List<Page> pages = new ArrayList<>();

    /** Index of the currently-active page, or -1 if none. */
    private int activeIndex = -1;

    /** Callback fired when the user clicks a page. Receives the page id. */
    private Consumer<String> onSelect;

    /** Smooth-sliding underline x-position in entry units (0..N). */
    private final BBSToolsAnim.Tween underlinePos = new BBSToolsAnim.Tween(0F);
    private long lastFrameNanos;

    public BBSToolsPageNavigator onSelect(Consumer<String> cb)
    {
        this.onSelect = cb;
        return this;
    }

    /** Register a navigable page. Returns this for chaining. */
    public BBSToolsPageNavigator addPage(String id, String label, int accent)
    {
        this.pages.add(new Page(id, label, accent));
        if (this.activeIndex < 0) this.activeIndex = 0;
        return this;
    }

    public BBSToolsPageNavigator setActive(String id)
    {
        for (int i = 0; i < this.pages.size(); i++)
        {
            if (this.pages.get(i).id.equals(id))
            {
                this.activeIndex = i;
                return this;
            }
        }
        return this;
    }

    public String activeId()
    {
        if (this.activeIndex < 0 || this.activeIndex >= this.pages.size()) return null;
        return this.pages.get(this.activeIndex).id;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context.mouseX, context.mouseY)
            || context.mouseButton != 0)
        {
            return super.subMouseClicked(context);
        }

        int hit = this.entryAt(context.mouseX, context.mouseY);
        if (hit >= 0)
        {
            this.activeIndex = hit;
            if (this.onSelect != null) this.onSelect.accept(this.pages.get(hit).id);
            return true;
        }
        return super.subMouseClicked(context);
    }

    private int entryAt(int mouseX, int mouseY)
    {
        int y = this.area.y;
        if (mouseY < y || mouseY >= y + HEIGHT) return -1;
        int x = this.area.x;
        int rel = mouseX - x;
        if (rel < 0 || rel >= this.pages.size() * ENTRY_W) return -1;
        return rel / ENTRY_W;
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int y2 = y + HEIGHT;

        /* Strip background + top/bottom borders. */
        context.batcher.box(x, y, x + w, y2, BBSToolsTheme.BG_PANEL);
        context.batcher.box(x, y, x + w, y + 1, BBSToolsTheme.BORDER_STRONG);
        context.batcher.box(x, y2 - 1, x + w, y2, BBSToolsTheme.BORDER);

        int hoverIdx = this.entryAt(context.mouseX, context.mouseY);

        /* Tick the underline tween toward the active index. dt in seconds
         * from real wall-clock so animation rate is FPS-independent. */
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0F : (now - this.lastFrameNanos) / 1_000_000_000F;
        this.lastFrameNanos = now;
        if (this.activeIndex >= 0)
        {
            this.underlinePos.tick((float) this.activeIndex, dt);
        }

        /* Icon-only entries. Label only renders on hover, as a tooltip
         * above the strip — keeps the chrome tight & minimal. */
        Page activePage = this.activeIndex >= 0 && this.activeIndex < this.pages.size()
            ? this.pages.get(this.activeIndex) : null;
        for (int i = 0; i < this.pages.size(); i++)
        {
            Page p = this.pages.get(i);
            int cursor = x + i * ENTRY_W;
            boolean hover = i == hoverIdx;
            boolean active = i == this.activeIndex;

            if (hover && !active)
            {
                context.batcher.box(cursor, y + 1, cursor + ENTRY_W, y2 - 1, BBSToolsTheme.BG_HOVER);
            }

            int iconX = cursor + (ENTRY_W - ICON_SIZE) / 2;
            int iconY = y + (HEIGHT - ICON_SIZE) / 2;
            int iconColor = active ? p.accent
                          : hover ? BBSToolsTheme.TEXT
                          : BBSToolsTheme.TEXT_DIM;
            drawPageIcon(context, iconX, iconY, p.id, iconColor);
        }

        /* Animated underline — slides smoothly between active entries. */
        if (activePage != null)
        {
            int uy = y2 - UNDERLINE_PX;
            int underlineX = x + Math.round(this.underlinePos.value * ENTRY_W);
            context.batcher.box(underlineX + 4, uy, underlineX + ENTRY_W - 4, uy + UNDERLINE_PX, activePage.accent);
        }

        /* Hover tooltip — label floats above the hovered entry. */
        if (hoverIdx >= 0)
        {
            Page p = this.pages.get(hoverIdx);
            int tipX = x + hoverIdx * ENTRY_W + ENTRY_W / 2;
            int tipY = y - 14;
            int textW = context.batcher.getFont().getWidth(p.label);
            int boxX = tipX - textW / 2 - 4;
            int boxY = tipY - 2;
            context.batcher.box(boxX, boxY, boxX + textW + 8, boxY + 12, BBSToolsTheme.BG_OVERLAY);
            context.batcher.outline(boxX, boxY, boxX + textW + 8, boxY + 12, BBSToolsTheme.BORDER);
            context.batcher.text(p.label, tipX - textW / 2, tipY, BBSToolsTheme.TEXT);
        }

        super.render(context);
    }

    /** Tiny pictograms per page id. Sharp 1-2 px shapes, no curves. */
    private static void drawPageIcon(UIContext context, int x, int y, String id, int color)
    {
        switch (id)
        {
            case "films" -> drawFilmIcon(context, x, y, color);
            case "models" -> drawCubeIcon(context, x, y, color);
            case "replays" -> drawPersonIcon(context, x, y, color);
            case "particles" -> drawSparkleIcon(context, x, y, color);
            case "audio" -> drawSpeakerIcon(context, x, y, color);
            case "texture", "paint" -> drawBrushIcon(context, x, y, color);
            case "scene" -> drawCubeIcon(context, x, y, color);
            case "settings" -> drawGearGlyph(context, x, y, color);
            default -> drawSquareIcon(context, x, y, color);
        }
    }

    private static void drawFilmIcon(UIContext context, int x, int y, int color)
    {
        /* Film strip — rectangle with 3 small tick-marks on each side. */
        context.batcher.box(x + 1, y + 2, x + 13, y + 12, color);
        context.batcher.box(x + 3, y + 4, x + 11, y + 10, BBSToolsTheme.BG_PANEL);
        /* Sprocket holes */
        context.batcher.box(x, y + 3, x + 1, y + 5, color);
        context.batcher.box(x, y + 9, x + 1, y + 11, color);
        context.batcher.box(x + 13, y + 3, x + 14, y + 5, color);
        context.batcher.box(x + 13, y + 9, x + 14, y + 11, color);
    }

    private static void drawCubeIcon(UIContext context, int x, int y, int color)
    {
        /* Cube isometric outline. */
        context.batcher.box(x + 2, y + 4, x + 9, y + 11, color);
        context.batcher.box(x + 3, y + 5, x + 8, y + 10, BBSToolsTheme.BG_PANEL);
        context.batcher.box(x + 5, y + 2, x + 12, y + 9, color);
        context.batcher.box(x + 6, y + 3, x + 11, y + 8, BBSToolsTheme.BG_PANEL);
        context.batcher.box(x + 5, y + 5, x + 9, y + 6, color);
        context.batcher.box(x + 8, y + 5, x + 9, y + 9, color);
    }

    private static void drawPersonIcon(UIContext context, int x, int y, int color)
    {
        /* Head + body */
        context.batcher.box(x + 5, y + 2, x + 9, y + 6, color);
        context.batcher.box(x + 6, y + 3, x + 8, y + 5, BBSToolsTheme.BG_PANEL);
        context.batcher.box(x + 3, y + 7, x + 11, y + 13, color);
        context.batcher.box(x + 4, y + 8, x + 10, y + 13, BBSToolsTheme.BG_PANEL);
    }

    private static void drawSparkleIcon(UIContext context, int x, int y, int color)
    {
        /* Plus + diagonals — 4-point star pattern. */
        context.batcher.box(x + 6, y + 2, x + 8, y + 12, color);
        context.batcher.box(x + 2, y + 6, x + 12, y + 8, color);
        context.batcher.box(x + 3, y + 3, x + 5, y + 5, color);
        context.batcher.box(x + 9, y + 9, x + 11, y + 11, color);
    }

    private static void drawSpeakerIcon(UIContext context, int x, int y, int color)
    {
        /* Speaker cone + waves. */
        context.batcher.box(x + 2, y + 5, x + 4, y + 9, color);
        context.batcher.box(x + 4, y + 3, x + 7, y + 11, color);
        context.batcher.box(x + 9, y + 5, x + 10, y + 9, color);
        context.batcher.box(x + 11, y + 3, x + 12, y + 11, color);
    }

    private static void drawBrushIcon(UIContext context, int x, int y, int color)
    {
        /* Brush handle + bristles. */
        context.batcher.box(x + 8, y + 2, x + 12, y + 6, color);
        context.batcher.box(x + 6, y + 4, x + 10, y + 8, color);
        context.batcher.box(x + 4, y + 6, x + 8, y + 10, color);
        context.batcher.box(x + 2, y + 9, x + 6, y + 13, color);
    }

    private static void drawGearGlyph(UIContext context, int x, int y, int color)
    {
        context.batcher.box(x + 5, y + 3, x + 9, y + 11, color);
        context.batcher.box(x + 3, y + 5, x + 11, y + 9, color);
        context.batcher.box(x + 6, y + 6, x + 8, y + 8, BBSToolsTheme.BG_PANEL);
    }

    private static void drawSquareIcon(UIContext context, int x, int y, int color)
    {
        context.batcher.box(x + 3, y + 3, x + 11, y + 11, color);
        context.batcher.box(x + 4, y + 4, x + 10, y + 10, BBSToolsTheme.BG_PANEL);
    }

    /** Internal page record. */
    private static final class Page
    {
        final String id;
        final String label;
        final int accent;

        Page(String id, String label, int accent)
        {
            this.id = id;
            this.label = label;
            this.accent = accent;
        }
    }

}
