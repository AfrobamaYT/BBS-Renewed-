package com.mcstudio.nano;

import java.util.List;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/** Gives existing Nano text zones the same BBS focus ownership as native text fields. */
public final class NanoTextFokus extends UIElement implements IFocusedUIElement
{
    private final NanoFlaeche owner;
    private boolean releasing;

    public static void synchronisieren(NanoFlaeche owner)
    {
        UIContext context = owner.getContext();

        if (context == null)
        {
            return;
        }
        if (owner.fokus().isEmpty())
        {
            if (context.activeElement instanceof NanoTextFokus focus && focus.owner == owner && !focus.releasing)
            {
                context.unfocus();
            }
            return;
        }

        List<NanoTextFokus> children = owner.getChildren(NanoTextFokus.class);
        NanoTextFokus focus = children.isEmpty() ? new NanoTextFokus(owner) : children.get(0);

        if (children.isEmpty())
        {
            owner.add(focus);
            focus.relative(owner).wh(1F, 1F);
            focus.resize();
        }

        context.focus(focus);
    }

    public static boolean tippt()
    {
        return UIScreen.getCurrentMenu() != null
            && UIScreen.getCurrentMenu().context.activeElement instanceof NanoTextFokus focus && focus.isFocused();
    }

    public static void aussenKlick(UIContext context)
    {
        if (context.activeElement instanceof NanoTextFokus focus && !focus.trifft(context))
        {
            context.unfocus();
        }
    }

    private NanoTextFokus(NanoFlaeche owner)
    {
        this.owner = owner;
    }

    private boolean trifft(UIContext context)
    {
        int index = this.owner.zonenNamen().indexOf(this.owner.fokus());

        if (index < 0 || !this.owner.canBeSeen())
        {
            return false;
        }
        float[] zone = this.owner.zonenMasse().get(index);
        float x = (context.mouseX() - this.owner.area.x) * this.owner.skala();
        float y = (context.mouseY() - this.owner.area.y) * this.owner.skala();

        return x >= zone[0] && x < zone[0] + zone[2] && y >= zone[1] && y < zone[1] + zone[3];
    }

    @Override
    public boolean isFocused()
    {
        return this.owner.getContext() != null && this.owner.getContext().activeElement == this && !this.owner.fokus().isEmpty();
    }

    @Override
    public void focus(UIContext context)
    {
        this.owner.neuMalen();
    }

    @Override
    public void unfocus(UIContext context)
    {
        this.releasing = true;
        try
        {
            this.owner.setzeFokus("");
        }
        finally
        {
            this.releasing = false;
        }
    }

    /* Existing Nano fields are append-only and have no text-selection state. */
    @Override
    public void selectAll(UIContext context)
    {}

    @Override
    public void unselect(UIContext context)
    {}

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!this.isFocused())
        {
            return false;
        }
        this.owner.subKeyPressed(context);
        return true;
    }

    @Override
    protected boolean subTextInput(UIContext context)
    {
        return this.isFocused() && this.owner.subTextInput(context);
    }
}
