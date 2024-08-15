package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UIOnionSkinContextMenu extends UIContextMenu
{
    public UIToggle enable;
    public UITrackpad preFrames;
    public UIColor preColor;
    public UITrackpad postFrames;
    public UIColor postColor;

    private UIElement column;

    private OnionSkin onionSkin;

    public UIOnionSkinContextMenu(OnionSkin onionSkin)
    {
        this.onionSkin = onionSkin;

        this.enable = new UIToggle(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE, (b) -> this.onionSkin.enabled = b.getValue());
        this.enable.setValue(this.onionSkin.enabled);
        this.preFrames = new UITrackpad((v) -> this.onionSkin.preFrames = v.intValue());
        this.preFrames.limit(0, 10, true).setValue(this.onionSkin.preFrames);
        this.preColor = new UIColor((c) -> this.onionSkin.preColor = c);
        this.preColor.withAlpha().setColor(this.onionSkin.preColor);
        this.postFrames = new UITrackpad((v) -> this.onionSkin.postFrames = v.intValue());
        this.postFrames.limit(0, 10, true).setValue(this.onionSkin.postFrames);
        this.postColor = new UIColor((c) -> this.onionSkin.postColor = c);
        this.postColor.withAlpha().setColor(this.onionSkin.postColor);

        this.column = UI.column(5, 10,
            this.enable,
            UI.row(this.preFrames, this.preColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_PREV),
            UI.row(this.postFrames, this.postColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_NEXT)
        );
        this.column.relative(this).w(140);

        this.add(this.column);
        this.column.resize();
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}