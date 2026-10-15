package com.afrobama.blendertransform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.graphics.Pufferprobe;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Logik hinter {@code WorldRendererMixin} — die Klammern um das Weltbild und die Messpunkte,
 * die dort haengen.
 *
 * <p>⚠️ HIER STEHEN MESSUNGEN IM PRODUKTIVPFAD. {@code Messpunkt} und {@code Pufferprobe}
 * gehoeren zur laufenden Fehlersuche am Bildpuffer, nicht zum Feature. Sie sind absichtlich
 * hier und nicht wegoptimiert — aber wer den Renderpfad misst, muss wissen, dass sie
 * mitlaufen.</p>
 */
public final class WeltRendererLogik
{
    private WeltRendererLogik() {}

    /** {@code addSkyPass} @HEAD — zeichnet der Himmel in unseren Puffer oder in Vanillas? */
    public static void himmelspass()
    {
        RenderTarget ziel = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        RenderTarget bbs = BBSRendering.getFramebuffer();

        Messpunkt.zaehle("sky.pass",
            "canReplace=" + BBSRendering.canReplaceFramebuffer()
            + " | renderingWorld=" + BBSRendering.renderingWorld
            + " | zielIstBBS=" + (ziel == bbs)
            + " | ziel=" + (ziel == null ? "null" : ziel.width + "x" + ziel.height));
    }

    /**
     * {@code render} @HEAD — die Warteschlange fuer durchscheinende Formen oeffnen.
     *
     * <p>⛔ {@link #renderEnde} MUSS FOLGEN. Bleibt die Warteschlange offen, sammeln sich die
     * durchscheinenden Teile des naechsten Bildes mit an — sichtbar als Nachziehen.</p>
     */
    public static void renderAnfang()
    {
        FormTranslucentQueue.begin();
    }

    /** {@code render} @RETURN — leeren und den Hauptpuffer probeweise auslesen. */
    public static void renderEnde()
    {
        FormTranslucentQueue.flush();

        RenderTarget haupt = Minecraft.getInstance().gameRenderer.mainRenderTarget();

        Pufferprobe.vielleichtLesen(haupt,
            "hauptziel" + (haupt == BBSRendering.getFramebuffer() ? "(BBS)" : "(vanilla)"));
    }

    /**
     * {@code render} @HEAD mit voller Argumentliste — die Projektion dieses Bildes festhalten.
     *
     * <p>⛔ HIER HING EIN ZWEI-TAGE-FEHLER. Der Auswahlpuffer zeichnete mit
     * {@code BBSRendering.camera}, gemessen wurde aber gegen {@code mainCamera()} — zwei
     * gleichnamige Werte, die im selben Bild verschieden sind. Deshalb wird die Kameramatrix
     * GENAU HIER gestempelt, an der Stelle, die der Renderer wirklich benutzt.</p>
     */
    //? if >=26.1 {
    /*public static void projektionStempeln(boolean bloecke,
        CameraRenderState kamera, Object nebel, Vector4f nebelFarbe, boolean himmel)
    {
        /^ 26.3's LevelRenderer consumes the projection from the extracted camera. ^/
        Matrix4fc projektion = kamera.bbsProjectionMatrix();

        Messpunkt.zaehle("welt.himmel-" + himmel,
            "nebelfarbe=" + (nebelFarbe == null
                ? "null"
                : nebelFarbe.x + "," + nebelFarbe.y + "," + nebelFarbe.z + "," + nebelFarbe.w)
            + " | nebelpuffer=" + (nebel != null)
            + " | bloecke=" + bloecke
            + " | projektion=" + (projektion == null ? "null" : String.valueOf(projektion.m00())));

        Messpunkt.zaehle("projektion.paar",
            "gezeichnet=" + (projektion == null ? "null" : projektion.m00() + "/" + projektion.m11())
            + " | kamerazustand=" + (kamera == null || kamera.bbsProjectionMatrix() == null ? "null"
                : kamera.bbsProjectionMatrix().m00() + "/" + kamera.bbsProjectionMatrix().m11())
            + " | gleich=" + (projektion != null && kamera != null
                && projektion.equals(kamera.bbsProjectionMatrix())));

        BBSRendering.zeichenProjektionStempeln(projektion);
        BBSRendering.camera.set(kamera.bbsViewRotationMatrix());
    }
    *///?} else {
    /** Vor 26.1 reicht renderLevel Projektion und Blickrotation einzeln herein (WorldRendererMixin). **/
    public static void projektionStempeln(boolean bloecke, Matrix4fc projektion, Matrix4fc blick,
        Object nebel, Vector4f nebelFarbe, boolean himmel)
    {
        Messpunkt.zaehle("welt.himmel-" + himmel,
            "nebelfarbe=" + (nebelFarbe == null ? "null" : nebelFarbe.x + "," + nebelFarbe.y + "," + nebelFarbe.z + "," + nebelFarbe.w)
            + " | nebelpuffer=" + (nebel != null) + " | bloecke=" + bloecke
            + " | projektion=" + (projektion == null ? "null" : String.valueOf(projektion.m00())));

        BBSRendering.zeichenProjektionStempeln(projektion);
        BBSRendering.camera.set(blick);
    }
    //?}

    /**
     * {@code resize} @RETURN.
     *
     * <p>⚠️ DER UMRISS-PUFFER IST DER TUERSTEHER, nicht das Ziel. Ist er noch {@code null},
     * hat Vanilla seine Puffer in diesem Aufruf gar nicht angelegt — dann waere unser
     * Nachziehen zu frueh und wuerde gegen alte Groessen rechnen.</p>
     *
     * @param umrissPuffer {@code entityOutlineTarget}, ein {@code @Shadow}-Feld des Renderers
     */
    public static void groesseGeaendert(Object umrissPuffer)
    {
        if (umrissPuffer == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }
}
