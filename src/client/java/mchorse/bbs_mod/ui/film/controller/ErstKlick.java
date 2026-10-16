package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Pair;

/**
 * One plain click on a bone of an actor that is not selected picks the actor AND that bone, so the
 * next R poses it. Blender needs click, Ctrl+Tab, click; BBS FS (and the fork before) Alt+click and
 * a second click -- speed test T2, 2026-10-16. While no actor is selected the replay pass runs
 * without Alt, so the click finds the actor; its bone comes once the new actor's bone pass has read
 * the pixel under the unmoved mouse. Alt+click still picks only the actor, as in BBS FS.
 */
public final class ErstKlick
{
    private static boolean offen;
    private static int x;
    private static int y;
    private static long bis;

    private ErstKlick()
    {}

    /** UIFilmController: the replay pass (one id per actor) also without Alt while none is selected */
    public static boolean replayDurchgang(IEntity aktuell)
    {
        return aktuell == null;
    }

    /** While the bone pass is awaited: the root gizmo of the new actor would cover the bone under the mouse */
    public static boolean wartet()
    {
        return offen && System.currentTimeMillis() <= bis;
    }

    /** From the click that selected an actor: its bone follows unless Alt asked for the actor only */
    public static void gewaehlt(UIContext context)
    {
        offen = !Window.isAltPressed();
        x = context.mouseX;
        y = context.mouseY;
        bis = System.currentTimeMillis() + 2000L;
    }

    /** Each frame the pick was read: pick the bone under the mouse once it is a bone (the new pass) */
    public static void nachwahl(UIFilmPanel panel, UIContext context, Pair<Form, String> gepickt)
    {
        if (!offen)
        {
            return;
        }

        if (System.currentTimeMillis() > bis || context.mouseX != x || context.mouseY != y)
        {
            offen = false;

            return;
        }

        if (gepickt == null || gepickt.a == null || gepickt.b == null || gepickt.b.isEmpty())
        {
            return; /* still the replay pass of the frame before: actors carry no bone there */
        }

        offen = false;
        panel.replayEditor.pickFormWithOffers(context, gepickt.a, gepickt.b);
    }
}
