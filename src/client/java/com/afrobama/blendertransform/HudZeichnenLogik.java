package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.GuiZeichner;
import mchorse.bbs_mod.client.HudSichtbarkeit;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.camera.controller.PlayCameraController;

/**
 * Logik hinter {@code InGameHudMixin} — das Spiel-HUD waehrend Aufnahme und Editor.
 *
 * <p>Zwei Haelften desselben Bildes: {@link #anfang} bei {@code extractRenderState} @HEAD,
 * {@link #ende} bei @TAIL.</p>
 *
 * <p>{@code beginHudRendering} and {@code endHudRendering} own one scope. Its end
 * must restore the player even if the tracker or camera controller has stopped
 * in the meantime.</p>
 */
public final class HudZeichnenLogik
{
    private HudZeichnenLogik() {}

    /**
     * {@code Gui.extractRenderState} @HEAD.
     *
     * <p>⚠️ DIE SEITENWIRKUNGEN LAUFEN VOR DER ENTSCHEIDUNG. {@code onRenderBeforeScreen} und
     * {@code fuellen} sind keine Abfragen, sondern bereiten das Bild vor — sie muessen auch
     * dann laufen, wenn danach abgebrochen wird.</p>
     *
     * @return {@code true} = Vanillas HUD-Rumpf wird uebersprungen
     */
    public static boolean anfang()
    {
        Minecraft spiel = Minecraft.getInstance();
        var zeichner = GuiZeichner.neuFuerBild(spiel);
        ICameraController aktuell = BBSModClient.getCameraController().getCurrent();

        if (aktuell instanceof PlayCameraController)
        {
            BBSRendering.onRenderBeforeScreen();

            if (!TrackerClientClip.shouldRenderHud())
            {
                return true;
            }
        }
        else
        {
            BBSRendering.fuellen();
        }

        if (!TrackerClientClip.shouldRenderHud() || aktuell == null)
        {
            return false;
        }

        HudSichtbarkeit.verstecken(false);
        TrackerClientClip.beginHudRendering(zeichner);

        return false;
    }

    /** {@code Gui.extractRenderState} @TAIL — die Gegenseite zu {@link #anfang}. */
    public static void ende()
    {
        var zeichner = GuiZeichner.aktuell(Minecraft.getInstance());

        BBSRendering.abschliessen();
        TrackerClientClip.endHudRendering(zeichner);

        if (TrackerClientClip.shouldRenderHud() && BBSModClient.getCameraController().getCurrent() != null)
        {
            HudSichtbarkeit.verstecken(true);
        }
    }
}
