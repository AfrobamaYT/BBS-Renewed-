package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Film editor keys as Blender has them (owner's choice 2026-10-15).
 *
 * <ul>
 *   <li>Escape cancels and never closes the editor. It closed the whole editor whenever
 *       nothing was left to cancel; the dashboard key (0 by default) closes it now -- inside
 *       the editor that key did nothing.</li>
 *   <li>Up/Down jump to the next/previous key on the tracks the dope sheet shows. They
 *       jumped a fixed 5 ticks past the keys; with Shift held they still take that step.</li>
 * </ul>
 */
public final class FilmTasten
{
    private static final String OEFFNEN = "key.bbs.dashboard";

    /** Retained for field-compatible live updates; closing no longer depends on key timing. */
    private static long letzteTaste;

    private FilmTasten()
    {}

    /**
     * From the film panel's key hook (after focused text fields, before the menu closes on Escape).
     * That hook runs BEFORE the element tree (UIOrbitCameraKeys), so Escape is first offered to the
     * panel's own tree -- a running R/G/S gesture cancels there -- and then swallowed either way.
     * Swallowing it outright broke Esc-cancel of gestures (measured 2026-10-15).
     */
    public static boolean nehmen(UIContext context, mchorse.bbs_mod.ui.framework.elements.UIElement panel)
    {
        /* Modals over the whole dashboard sit outside the panel: they take Escape and digits in the tree */
        if (mchorse.bbs_mod.ui.film.replays.KeyframeGreifen.laeuft() || mchorse.bbs_mod.ui.film.replays.PoseBreakdowner.laeuft()
            || mchorse.bbs_mod.ui.film.replays.KeyframeMischen.laeuft())
        {
            return false;
        }

        if (panel instanceof UIFilmPanel film && film.replayEditor != null && mchorse.bbs_mod.ui.framework.elements.input.UITransformHandler
            .verborgenNehmen(mchorse.bbs_mod.ui.film.replays.UIReplayWurzelTransform.filmTransform(film), context))
        {
            return true;
        }

        if (context.isPressed(InputConstants.KEY_ESCAPE))
        {
            panel.keyPressed(context);

            return true;
        }

        KeyMapping oeffnen = oeffnen();

        if (oeffnen != null && !oeffnen.isUnbound() && context.isPressed(InputConstants.getKey(oeffnen.saveString()).getValue()))
        {
            /* Active gestures and focused fields consume digits first. A rejected gesture has
             * no number input; unrelated preceding keys must never delay closing the editor. */
            if (panel.keyPressed(context) == null)
            {
                BBSModClient.getDashboard().closeThisMenu();
            }

            return true;
        }

        return false;
    }

    /** Up (+1) / Down (-1) */
    public static void springen(UIFilmPanel panel, int richtung)
    {
        int cursor = panel.getCursor();
        Float ziel = Window.isShiftPressed() ? null : naechsterKey(panel, cursor, richtung);

        panel.setCursor(ziel == null ? cursor + richtung * BBSSettings.editorJump.get() : (int) Math.floor(ziel));
    }

    private static Float naechsterKey(UIFilmPanel panel, int cursor, int richtung)
    {
        if (panel.replayEditor == null || panel.replayEditor.keyframeEditor == null)
        {
            return null;
        }

        Float best = null;

        for (UIKeyframeSheet sheet : panel.replayEditor.keyframeEditor.view.getGraph().getSheets())
        {
            for (Object o : sheet.channel.getKeyframes())
            {
                float tick = ((Keyframe<?>) o).getTick();
                boolean dahinter = richtung > 0 ? tick >= cursor + 1 : tick < cursor;

                if (dahinter && (best == null || (richtung > 0 ? tick < best : tick > best)))
                {
                    best = tick;
                }
            }
        }

        return best;
    }

    private static KeyMapping oeffnen()
    {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
        {
            if (mapping.getName().equals(OEFFNEN))
            {
                return mapping;
            }
        }

        return null;
    }
}
