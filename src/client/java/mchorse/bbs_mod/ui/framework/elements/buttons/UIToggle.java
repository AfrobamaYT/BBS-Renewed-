package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIToggle extends UIClickable<UIToggle> implements ITextColoring
{
    public IKey label;
    public int color = Colors.WHITE;
    public boolean textShadow = true;
    private boolean value;
    private float anim;

    public UIToggle(IKey label, Consumer<UIToggle> callback)
    {
        this(label, false, callback);
    }

    public UIToggle(IKey label, boolean value, Consumer<UIToggle> callback)
    {
        super(callback);

        this.label = label;
        this.value = value;
        this.anim = value ? 1F : 0F;
        this.h(14);
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.color(color, shadow);
    }

    public UIToggle label(IKey label)
    {
        this.label = label;

        return this;
    }

    public UIToggle setValue(boolean value)
    {
        this.value = value;

        return this;
    }

    public UIToggle color(int color)
    {
        return this.color(color, true);
    }

    public UIToggle color(int color, boolean textShadow)
    {
        this.color = color;
        this.textShadow = textShadow;

        return this;
    }

    public boolean getValue()
    {
        return this.value;
    }

    @Override
    protected void click(int mouseWheel)
    {
        this.value = !this.value;

        super.click(mouseWheel);
    }

    @Override
    protected UIToggle get()
    {
        return this;
    }

    private static final int TRACK_W = 20;
    private static final int TRACK_H = 12;

    @Override
    protected void renderSkin(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(this.label.get(), this.area.w - TRACK_W - 6);

        context.batcher.text(label, this.area.x, this.area.my(font.getHeight()), this.color, this.textShadow);

        this.anim += ((this.value ? 1F : 0F) - this.anim) * 0.4F;

        if (Math.abs((this.value ? 1F : 0F) - this.anim) < 0.001F)
        {
            this.anim = this.value ? 1F : 0F;
        }

        int x = this.area.ex() - TRACK_W - 2;
        int y = this.area.my() - TRACK_H / 2;

        int track = Colors.lerp(BBSSettings.inputSurface(), Colors.A100 | BBSSettings.primaryColor.get(), this.anim);
        int border = Colors.lerp(BBSSettings.dividerColor(), Colors.A100 | BBSSettings.primaryColor.get(), this.anim);

        if (this.hover)
        {
            track = Colors.lerp(track, Colors.WHITE, 0.08F);
        }

        /* Track: hairline border, flat fill, crisp inner shadow along the top */
        context.batcher.box(x, y, x + TRACK_W, y + TRACK_H, border);
        context.batcher.box(x + 1, y + 1, x + TRACK_W - 1, y + TRACK_H - 1, track);
        context.batcher.box(x + 1, y + 1, x + TRACK_W - 1, y + 2, Colors.A25);

        /* Knob slides between the ends, lifted by a sharp drop shadow */
        int knob = TRACK_H - 4;
        int kx = x + 2 + Math.round((TRACK_W - knob - 4) * this.anim);
        int ky = y + 2;

        context.batcher.box(kx + 1, ky + 1, kx + knob + 1, ky + knob + 1, Colors.A50);
        context.batcher.box(kx, ky, kx + knob, ky + knob, 0xfff0f2f5);

        if (!this.isEnabled())
        {
            context.batcher.box(x, y, x + TRACK_W, y + TRACK_H, Colors.A50);
            context.batcher.outlinedIcon(Icons.LOCKED, x + TRACK_W / 2, this.area.my(), 0.5F, 0.5F);
        }
    }
}