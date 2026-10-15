// Coded by Afrobama/Claude — UI Overhaul Phase 2: DaVinci-style thin slider
package com.afrobama.bbstools.client.ui.theme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.function.Consumer;

/**
 * Horizontal slider in DaVinci-style: 2-pixel thin filled track, small
 * rectangular thumb (no circles), accent fill from min to thumb. Click
 * + drag anywhere on the track sets the value. Range is configurable
 * (defaults 0..1).
 */
public class BBSToolsSlider extends UIElement
{
    private static final int TRACK_H = 2;
    private static final int THUMB_W = 5;
    private static final int THUMB_H = 12;

    public float min = 0F;
    public float max = 1F;
    public float value = 0F;
    public int accent = BBSToolsTheme.ACCENT_PRIMARY;
    private Consumer<Float> callback;
    private boolean dragging;

    public BBSToolsSlider range(float min, float max)
    {
        this.min = min;
        this.max = max;
        return this;
    }

    public BBSToolsSlider initial(float v)
    {
        this.value = clamp(v);
        return this;
    }

    public BBSToolsSlider accent(int color)
    {
        this.accent = color;
        return this;
    }

    public BBSToolsSlider onChange(Consumer<Float> cb)
    {
        this.callback = cb;
        return this;
    }

    public float getValue()
    {
        return this.value;
    }

    /** Externally update the slider value without firing the callback —
     *  useful for keeping the slider in sync with an external state
     *  source (e.g. a keybind-driven toggle of the same field). */
    public BBSToolsSlider setValue(float v)
    {
        this.value = this.clamp(v);
        return this;
    }

    private float clamp(float v)
    {
        if (v < this.min) return this.min;
        if (v > this.max) return this.max;
        return v;
    }

    private void setFromMouse(int mouseX)
    {
        float t = (mouseX - this.area.x) / (float) Math.max(1, this.area.w);
        if (t < 0F) t = 0F;
        if (t > 1F) t = 1F;
        float old = this.value;
        this.value = this.min + (this.max - this.min) * t;
        if (this.callback != null && this.value != old) this.callback.accept(this.value);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY) && context.mouseButton == 0)
        {
            this.dragging = true;
            setFromMouse(context.mouseX);
            return true;
        }
        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging)
        {
            this.dragging = false;
            return true;
        }
        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        int cy = y + h / 2;

        if (this.dragging)
        {
            setFromMouse(context.mouseX);
        }

        /* Track background. */
        context.batcher.box(x, cy - TRACK_H / 2, x + w, cy + TRACK_H / 2 + (TRACK_H % 2),
            BBSToolsTheme.BORDER);

        /* Filled portion (min → thumb). */
        float t = (this.value - this.min) / Math.max(0.0001F, this.max - this.min);
        if (t < 0F) t = 0F;
        if (t > 1F) t = 1F;
        int thumbX = x + Math.round((w - THUMB_W) * t);
        context.batcher.box(x, cy - TRACK_H / 2, thumbX + THUMB_W / 2,
            cy + TRACK_H / 2 + (TRACK_H % 2), this.accent);

        /* Thumb. */
        int thumbY = y + (h - THUMB_H) / 2;
        context.batcher.box(thumbX, thumbY, thumbX + THUMB_W, thumbY + THUMB_H,
            this.accent);

        super.render(context);
    }

}
