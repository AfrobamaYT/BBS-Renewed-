package mchorse.bbs_mod.ui.film.menu;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;

public class UIFilmsMenu extends UIBaseMenu
{
    public UIFilmsOverlayPanel films;

    public UIFilmsMenu()
    {
        super();

        this.films = new UIFilmsOverlayPanel();
        this.films.onClose((event) -> this.closeThisMenu());

        UIOverlay.addOverlay(this.context, this.films);
    }
}