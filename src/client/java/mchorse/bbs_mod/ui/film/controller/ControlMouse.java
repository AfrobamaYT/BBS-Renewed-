package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.FilmGizmoDrag;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.Minecraft;

/**
 * Who owns the mouse while an actor is being controlled — the two halves of one trade.
 *
 * <p>Normally the actor owns it: the movement steers the character, and the UI is told it has
 * no cursor so nothing behind the character hovers, highlights or picks ({@link #pin}). The one
 * exception is a running R/G/S drag. Those read their delta straight out of
 * {@code context.mouseX/mouseY}, and -1/-1 is a delta of zero for the whole gesture — the
 * reason the transform hotkeys did nothing in first person. For the length of such a drag the
 * cursor stays live, and in exchange the look stands down ({@link #steer}), or rotating a bone
 * would swing the head with it.
 */
public class ControlMouse
{
    /** Hand the cursor to the actor for this frame, unless a drag needs it. */
    public static void pin(UIFilmPanel panel, UIContext context)
    {
        if (!panel.getController().isControlling() || context == null)
        {
            return;
        }

        if (!isModalDragRunning(panel))
        {
            context.mouseX = context.mouseY = -1;
        }
    }

    /** Whether a keyboard-started transform is mid-drag and therefore needs the cursor. */
    public static boolean isModalDragRunning(UIFilmPanel panel)
    {
        return BBSSettings.transformHotkeysWhileControlling.get()
            && FilmGizmoDrag.isModalTransformActive(panel);
    }

    /**
     * Turn this frame's mouse movement into look or stick input for the controlled actor.
     *
     * <p>The caller updates its {@code lastMouse} either way, so a drag that held the look back
     * resumes from where the cursor actually is instead of jumping when it ends.
     */
    public static void steer(UIFilmController controller, int x, int y, int lastX, int lastY)
    {
        if (!controller.canControl() || isModalDragRunning(controller.panel))
        {
            return;
        }

        if (controller.isMouseLookMode() && ClientNetwork.isIsBBSModOnServer())
        {
            Minecraft.getInstance().player.turn((x - lastX) / 2F, (y - lastY) / 2F);

            return;
        }

        /* Control sticks and triggers variables */
        float sensitivity = 100F;

        controller.mouseStick.add((y - lastY) / sensitivity, (x - lastX) / sensitivity);
        controller.mouseStick.x = MathUtils.clamp(controller.mouseStick.x, -1F, 1F);
        controller.mouseStick.y = MathUtils.clamp(controller.mouseStick.y, -1F, 1F);
    }
}
