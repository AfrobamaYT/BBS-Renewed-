// Coded by Afrobama/Claude — UI Overhaul Phase 2: AE/Premiere-style button
package com.afrobama.bbstools.client.ui.theme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * AE/Premiere-flavored rectangular button. Variants distinguish primary
 * (accent fill), secondary (subtle bg), and destructive (red accent on
 * hover) actions. Sharp corners (2px), 1px border, hover/active
 * brightening — no Refreshed-style M3 pills.
 *
 * <p>Use {@link #onClick(Runnable)} to set the click handler.</p>
 */
public class BBSToolsButton extends UIElement
{
    public enum Variant { PRIMARY, SECONDARY, DESTRUCTIVE }

    /** Volatile because {@link #label(String)} mutates this from
     *  arbitrary callers (e.g. dock applySelection) while the render
     *  thread reads it. Volatile keeps the read in step with writes
     *  on the same thread without the cost of a full sync. */
    private volatile String label;
    private Variant variant = Variant.SECONDARY;
    private Runnable onClick;
    private boolean enabled = true;

    public BBSToolsButton(String label)
    {
        this.label = label;
    }

    public BBSToolsButton variant(Variant v)
    {
        this.variant = v;
        return this;
    }

    /** Mutate the displayed label without recreating the element —
     *  used by long-lived buttons (e.g. the Replay Dock's actor
     *  toggle) whose text reflects external state. (M1 from the
     *  Phase 10 review.) */
    public BBSToolsButton label(String newLabel)
    {
        this.label = newLabel == null ? "" : newLabel;
        return this;
    }

    public String label()
    {
        return this.label;
    }

    public BBSToolsButton primary() { return variant(Variant.PRIMARY); }
    public BBSToolsButton destructive() { return variant(Variant.DESTRUCTIVE); }

    public BBSToolsButton onClick(Runnable r)
    {
        this.onClick = r;
        return this;
    }

    public BBSToolsButton enabled(boolean e)
    {
        this.enabled = e;
        return this;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.enabled
            && this.area.isInside(context.mouseX, context.mouseY)
            && context.mouseButton == 0
            && this.onClick != null)
        {
            this.onClick.run();
            return true;
        }
        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int x2 = x + this.area.w;
        int y2 = y + this.area.h;
        boolean hover = this.enabled && this.area.isInside(context.mouseX, context.mouseY);

        /* Background fill — variant + hover + disabled. */
        int bg;
        int border;
        int textColor = this.enabled ? BBSToolsTheme.TEXT : BBSToolsTheme.TEXT_FAINT;
        switch (this.variant)
        {
            case PRIMARY:
                bg = hover ? brighten(BBSToolsTheme.ACCENT_PRIMARY, 0.15F) : BBSToolsTheme.ACCENT_PRIMARY;
                border = BBSToolsTheme.ACCENT_PRIMARY;
                textColor = 0xFFFFFFFF;
                break;
            case DESTRUCTIVE:
                bg = hover ? brighten(BBSToolsTheme.ACCENT_DESTRUCTIVE, 0.15F) : BBSToolsTheme.BG_FIELD;
                border = hover ? BBSToolsTheme.ACCENT_DESTRUCTIVE : BBSToolsTheme.BORDER;
                textColor = hover ? 0xFFFFFFFF : BBSToolsTheme.ACCENT_DESTRUCTIVE;
                break;
            case SECONDARY:
            default:
                bg = hover ? BBSToolsTheme.BG_HOVER : BBSToolsTheme.BG_ELEVATED;
                border = hover ? BBSToolsTheme.BORDER_HOVER : BBSToolsTheme.BORDER;
                break;
        }
        if (!this.enabled)
        {
            bg = BBSToolsTheme.BG_PANEL;
            border = BBSToolsTheme.BORDER;
        }
        context.batcher.box(x, y, x2, y2, bg);
        context.batcher.outline(x, y, x2, y2, border);

        /* Label centered. */
        if (this.label != null && !this.label.isEmpty())
        {
            int tw = context.batcher.getFont().getWidth(this.label);
            int tx = x + (this.area.w - tw) / 2;
            int ty = y + (this.area.h - 8) / 2;
            context.batcher.text(this.label, tx, ty, textColor);
        }

        super.render(context);
    }

    private static int brighten(int argb, float amt)
    {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amt));
        g = Math.min(255, Math.round(g + (255 - g) * amt));
        b = Math.min(255, Math.round(b + (255 - b) * amt));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

}
