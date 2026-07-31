package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Zeichnet das Bodengitter der Modellvorschau -- als Bild im Bild, nicht als Sofortzug.
 *
 * <h2>Warum ueber Bild-im-Bild (2026-07-31, nach zwei gescheiterten Versuchen)</h2>
 *
 * <p>Das Gitter war seit dem 26.2-Port unsichtbar. Zwei naheliegende Reparaturen halfen
 * nicht (Aufgabe 96): weder ein eigener Sofortzug (der zeichnet VOR dem GUI und
 * verschwindet darunter) noch das Einreihen ueber {@code Batcher2D.netz} (die Geometrie
 * kommt an, wird aber mit der GUI-Projektion gezeichnet statt mit der Kameraprojektion
 * der Vorschau -- gemessen: 0 von 2100 Rasterproben ueber Helligkeit 60).
 *
 * <p>{@link PictureInPictureRenderer} loest genau das: er bringt eine EIGENE Projektion,
 * einen eigenen Tiefenpuffer und einen eigenen Sammelspeicher mit, rendert in eine Textur
 * und blittet sie danach ins GUI. Es ist Vanillas eigener Weg fuer 3D-Inhalte in der
 * Oberflaeche -- {@code GuiEntityRenderer} (Spielerfigur im Inventar) macht dasselbe.
 *
 * <p>⚠️ VERTIKALSCHNITT: dieser Renderer kann VORERST NUR DAS GITTER. Er ist der Beweis,
 * dass der Weg traegt, nicht die fertige Loesung. Modell, Partikel und die uebrigen
 * Vorschauinhalte folgen erst, wenn das Gitter gemessen sichtbar ist -- ein grosser Umbau
 * auf einen unbewiesenen Weg waere derselbe Fehler wie die zwei Versuche davor.
 */
public class VorschauGitterRenderer extends PictureInPictureRenderer<VorschauGitterZustand>
{
    @Override
    public Class<VorschauGitterZustand> getRenderStateClass()
    {
        return VorschauGitterZustand.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "bbs vorschau-gitter";
    }

    /**
     * ⚠️ Die Pose kommt aus dem ZUSTAND, nicht vom uebergebenen Stapel. Der Stapel traegt
     * die Bild-im-Bild-eigene Sicht; unsere Vorschau hat ihre eigene Kamera, deren Matrix
     * beim Einreihen eingefroren wurde. Wer hier den Stapel nimmt, zeichnet das Gitter aus
     * der falschen Richtung.
     */
    @Override
    protected void renderToTexture(VorschauGitterZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        sammler.submitCustomGeometry(stapel, RenderTypes.LINES, (pose, ecken) ->
        {
            for (int i = 0; i <= 10; i ++)
            {
                float f = i - 5;

                if (i == 0)
                {
                    linie(ecken, pose, zustand.ansicht(), f, -5, f, 5, 0F, 0F, 1F);
                    linie(ecken, pose, zustand.ansicht(), -5, f, 5, f, 1F, 0F, 0F);
                }
                else
                {
                    linie(ecken, pose, zustand.ansicht(), f, -5, f, 5, 0.25F, 0.25F, 0.25F);
                    linie(ecken, pose, zustand.ansicht(), -5, f, 5, f, 0.25F, 0.25F, 0.25F);
                }
            }
        });
    }

    /**
     * Eine Gitterlinie auf der Bodenebene (y = 0).
     *
     * <p>⛔ {@code RenderTypes.LINES} verlangt VIER Angaben je Ecke: Position, Farbe,
     * Normale UND Linienbreite. Fehlt eine, stirbt der Client beim naechsten Bild mit
     * {@code IllegalStateException: Missing elements in vertex} -- nicht beim Einreichen,
     * sondern erst im Zeichendurchgang, also weit weg vom Verursacher.
     *
     * <p>Genau das passierte am 2026-07-31: die Linienbreite fehlte, weil ich das Format
     * aus dem Kopf schrieb statt es abzulesen. Die richtige Kette steht in
     * {@code CameraFrustumMeshRenderer} und in Vanillas {@code ShapeOutlineFeatureRenderer}:
     * {@code addVertex(...).setColor(...).setNormal(pose, ...).setLineWidth(...)}.
     *
     * <p>⚠️ {@code setNormal} nimmt die POSE als erstes Argument, nicht die Matrix -- die
     * Normale wird mit der Pose gedreht, sonst zeigt sie in die falsche Richtung.
     */
    private static void linie(com.mojang.blaze3d.vertex.VertexConsumer ecken,
        com.mojang.blaze3d.vertex.PoseStack.Pose pose, org.joml.Matrix4f ansicht,
        float x1, float z1, float x2, float z2, float r, float g, float b)
    {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float laenge = (float) Math.sqrt(dx * dx + dz * dz);

        if (laenge == 0F)
        {
            return;
        }

        dx /= laenge;
        dz /= laenge;

        ecken.addVertex(ansicht, x1, 0F, z1).setColor(r, g, b, 1F).setNormal(pose, dx, 0F, dz).setLineWidth(2F);
        ecken.addVertex(ansicht, x2, 0F, z2).setColor(r, g, b, 1F).setNormal(pose, dx, 0F, dz).setLineWidth(2F);
    }
}
