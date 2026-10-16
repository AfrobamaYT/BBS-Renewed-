package mchorse.bbs_mod.ui.framework.elements.context;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.utils.colors.Colors;

public abstract class UIContextMenu extends UIElement
{
    public UIContextMenu()
    {
        super();

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    public abstract boolean isEmpty();

    /**
     * Set mouse coordinate
     *
     * In this method for subclasses, you should setup the resizer
     */
    /** Runs on every menu once it is placed: Help moves it off the guide's mini-player, which
     * nothing may cover (YouTube's embed rules). */
    public static java.util.function.Consumer<UIContextMenu> platz = (menu) -> {};

    public abstract void setMouse(UIContext context);

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            this.removeFromParent();
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputConstants.KEY_ESCAPE))
        {
            this.removeFromParent();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* A menu covers what lies under it: hand the waiting miniatures (film tiles) in first, as
         * UIOverlay does -- otherwise they go out at the end of the pass and land on top of the
         * menu (Help menu over the film tiles, 2026-10-14). An empty queue costs nothing. */
        mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange.abgeben(
            context, context.menu.width, context.menu.height);
        this.renderBackground(context);

        super.render(context);
    }

    protected void renderBackground(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());
        /* Design-System: Menues sitzen auf --menu #181818, Dialoge auf --region #242424 —
         * die eine Stelle, an der sich beide unterscheiden. Der Token steht in NanoDesign,
         * das helle Theme deckt das Design nicht ab und bleibt wie es war. */
        int flaeche = BBSSettings.isLightTheme()
            ? BBSSettings.raisedSurface()
            : (Colors.A100 | com.mcstudio.nano.NanoDesign.MENUE);

        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an())
        {
            /* Kante als schwarzes Rundeck einen Punkt groesser: outline() ist eckig. */
            context.batcher.rundeck(this.area.x - 1, this.area.y - 1, this.area.w + 2, this.area.h + 2, 7F, Colors.A100);
            context.batcher.rundeck(this.area.x, this.area.y, this.area.w, this.area.h, 6F, flaeche);
        }
        else
        {
            this.area.render(context.batcher, flaeche);
        }
    }
}
