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
 * <p>⛔ DIE BEIDEN GEHOEREN ZUSAMMEN. {@code beginHudRendering} und {@code endHudRendering}
 * sind ein Paar; wer nur eine Seite abbricht, laesst die Aufzeichnung im halb geoeffneten
 * Zustand stehen. Deshalb faellt die Entscheidung "HUD ueberhaupt zeichnen" an BEIDEN Stellen
 * ueber dieselbe Abfrage {@code shouldRenderHud()} und nicht ueber ein gemerktes Flag.</p>
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

        if (TrackerClientClip.shouldRenderHud() && BBSModClient.getCameraController().getCurrent() != null)
        {
            TrackerClientClip.endHudRendering(zeichner);
            HudSichtbarkeit.verstecken(true);
        }
    }
}
