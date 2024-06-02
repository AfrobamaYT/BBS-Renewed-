package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.renderers.InterpolationRenderer;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interps;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class UIInterpolationContextMenu extends UIContextMenu
{
    public static final Map<IInterp, Icon> INTERP_ICON_MAP = new HashMap<>();

    public UIElement grid;
    public UITrackpad v1;
    public UITrackpad v2;
    public UITrackpad v3;
    public UITrackpad v4;

    private Interpolation interpolation;
    private Map<IInterp, UIIcon> icons = new HashMap<>();

    static
    {
        INTERP_ICON_MAP.put(Interps.LINEAR, Icons.INTERP_LINEAR);
        INTERP_ICON_MAP.put(Interps.CONST, Icons.INTERP_CONST);
        INTERP_ICON_MAP.put(Interps.STEP, Icons.INTERP_STEP);
        INTERP_ICON_MAP.put(Interps.QUAD_IN, Icons.INTERP_QUAD);
        INTERP_ICON_MAP.put(Interps.QUAD_OUT, Icons.INTERP_QUAD);
        INTERP_ICON_MAP.put(Interps.QUAD_INOUT, Icons.INTERP_QUAD);
        INTERP_ICON_MAP.put(Interps.CUBIC_IN, Icons.INTERP_CUBIC);
        INTERP_ICON_MAP.put(Interps.CUBIC_OUT, Icons.INTERP_CUBIC);
        INTERP_ICON_MAP.put(Interps.CUBIC_INOUT, Icons.INTERP_CUBIC);
        INTERP_ICON_MAP.put(Interps.EXP_IN, Icons.INTERP_EXP);
        INTERP_ICON_MAP.put(Interps.EXP_OUT, Icons.INTERP_EXP);
        INTERP_ICON_MAP.put(Interps.EXP_INOUT, Icons.INTERP_EXP);
        INTERP_ICON_MAP.put(Interps.BACK_IN, Icons.INTERP_BACK);
        INTERP_ICON_MAP.put(Interps.BACK_OUT, Icons.INTERP_BACK);
        INTERP_ICON_MAP.put(Interps.BACK_INOUT, Icons.INTERP_BACK);
        INTERP_ICON_MAP.put(Interps.ELASTIC_IN, Icons.INTERP_ELASTIC);
        INTERP_ICON_MAP.put(Interps.ELASTIC_OUT, Icons.INTERP_ELASTIC);
        INTERP_ICON_MAP.put(Interps.ELASTIC_INOUT, Icons.INTERP_ELASTIC);
        INTERP_ICON_MAP.put(Interps.BOUNCE_IN, Icons.INTERP_BOUNCE);
        INTERP_ICON_MAP.put(Interps.BOUNCE_OUT, Icons.INTERP_BOUNCE);
        INTERP_ICON_MAP.put(Interps.BOUNCE_INOUT, Icons.INTERP_BOUNCE);
        INTERP_ICON_MAP.put(Interps.SINE_IN, Icons.INTERP_SINE);
        INTERP_ICON_MAP.put(Interps.SINE_OUT, Icons.INTERP_SINE);
        INTERP_ICON_MAP.put(Interps.SINE_INOUT, Icons.INTERP_SINE);
        INTERP_ICON_MAP.put(Interps.QUART_IN, Icons.INTERP_QUART);
        INTERP_ICON_MAP.put(Interps.QUART_OUT, Icons.INTERP_QUART);
        INTERP_ICON_MAP.put(Interps.QUART_INOUT, Icons.INTERP_QUART);
        INTERP_ICON_MAP.put(Interps.QUINT_IN, Icons.INTERP_QUINT);
        INTERP_ICON_MAP.put(Interps.QUINT_OUT, Icons.INTERP_QUINT);
        INTERP_ICON_MAP.put(Interps.QUINT_INOUT, Icons.INTERP_QUINT);
        INTERP_ICON_MAP.put(Interps.CIRCLE_IN, Icons.INTERP_CIRCLE);
        INTERP_ICON_MAP.put(Interps.CIRCLE_OUT, Icons.INTERP_CIRCLE);
        INTERP_ICON_MAP.put(Interps.CIRCLE_INOUT, Icons.INTERP_CIRCLE);
        INTERP_ICON_MAP.put(Interps.CIRCULAR, Icons.INTERP_CIRCLE);
        INTERP_ICON_MAP.put(Interps.CUBIC, Icons.INTERP_CUBIC);
        INTERP_ICON_MAP.put(Interps.HERMITE, Icons.INTERP_CUBIC);
        INTERP_ICON_MAP.put(Interps.BEZIER, Icons.INTERP_BEZIER);
    }

    public UIInterpolationContextMenu(Interpolation interpolation)
    {
        this.interpolation = interpolation;

        int w = 120;
        int h = (int) Math.ceil(interpolation.getMap().values().size() / (w / 20F)) * 20;

        this.v1 = new UITrackpad((v) -> this.interpolation.setV1(v));
        this.v2 = new UITrackpad((v) -> this.interpolation.setV2(v));
        this.v3 = new UITrackpad((v) -> this.interpolation.setV3(v));
        this.v4 = new UITrackpad((v) -> this.interpolation.setV4(v));

        this.v1.setValue(interpolation.getV1());
        this.v2.setValue(interpolation.getV2());
        this.v3.setValue(interpolation.getV3());
        this.v4.setValue(interpolation.getV4());

        this.grid = new UIElement();
        this.grid.relative(this).xy(10, 85 + 45).w(w).h(h).grid(0).items(6);

        for (IInterp value : interpolation.getMap().values())
        {
            UIIcon icon = new UIIcon(INTERP_ICON_MAP.getOrDefault(value, Icons.INTERP_LINEAR), (b) -> this.interpolation.setInterp(value));

            icon.tooltip(InterpolationUtils.getName(value));
            this.grid.add(icon);
            this.icons.put(value, icon);
            this.setupKeybind(value, icon);
        }

        UIElement vs = UI.column(UI.row(this.v1, this.v2), UI.row(this.v3, this.v4));

        vs.relative(this).xy(10, 80).w(w);

        this.wh(w + 20, 85 + 45 + h + 10);
        this.add(vs, this.grid);
    }

    private void setupKeybind(IInterp interp, UIIcon icon)
    {
        IKey label = InterpolationUtils.getName(interp);
        IKey category = UIKeys.INTERPOLATIONS_KEY_CATEGORY;
        String key = interp.getKey();
        KeyCombo combo = new KeyCombo(label, interp.getKeyCode());

        if (key.endsWith("_in"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_SHIFT);
        }
        else if (key.endsWith("_out"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_CONTROL);
        }

        this.keys().register(combo.category(category), icon::clickItself);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY()).bounds(context.menu.overlay, 5);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int color = BBSSettings.primaryColor.get();
        IInterp interp = this.interpolation.getInterp();
        UIIcon icon = this.icons.get(interp);
        Color fg = new Color().set(color);

        fg.a = 0.5F;

        InterpolationRenderer.renderInterpolationGraph(this.interpolation.wrap(), context, fg, Colors.WHITE, this.area.x + 10, this.area.y + 10, 120, 60, 20,10);

        if (icon != null)
        {
            icon.area.render(context.batcher, color | Colors.A50);
        }
    }
}