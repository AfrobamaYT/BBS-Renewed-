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
 *
 * <h2>⭐ STAND 2026-08-01: der Weg TRAEGT, es fehlt nur noch die Ansicht</h2>
 *
 * <p>Alles bis zum Bildpunkt ist gemessen, nichts davon vermutet:
 *
 * <pre>
 * pip.eingereiht = 41                    der Zustand wird eingereiht
 * pip.gitter     = 41                    renderToTexture wird erreicht
 * ~pip.gitter    = 200,18 bis 620,340    die Grenzen sind exakt die Vorschauflaeche
 * </pre>
 *
 * <p>Dazu eine Probe OHNE jede Kameramatrix -- zwei Kreuze in der Bodenebene, direkt im
 * Koordinatensystem dieses Zeichners -- und die war SICHTBAR: 92 gelbe und 93 cyanfarbene
 * Bildpunkte im Vorschaufeld. Das Zusammensetzen funktioniert also, der Blit kommt an, die
 * Registrierung stimmt. Der fruehere Absturz ("render area out of bounds") kam vom
 * ausgelaufenen Zeichenbereich und ist mit dessen Freigabe verschwunden.
 *
 * <p>⛔ Damit bleibt {@code zustand.ansicht()} als Ursache uebrig -- genau der Fehlermodus,
 * den der Kommentar an {@link #renderToTexture} beschreibt. Die Probe lag als 92 Punkte
 * breiter, EINEN Punkt hoher Strich am unteren Rand (y=641 von 644): die Kamera dieses Raums
 * schaut waagerecht, die Bodenebene steht kantig dazu, und der Ursprung liegt nicht in der
 * Mitte der Flaeche. Die Ansicht der Vorschau ist eine WELT-Kameramatrix und passt nicht in
 * diesen Raum.
 *
 * <p>Naechster Schritt: an Vanillas {@code GuiEntityRenderer} ablesen ({@code javap -c}),
 * welche Transformation der Grundzeichner selbst schon auf den Stapel legt, und die
 * Vorschaukamera DARIN ausdruecken, statt sie darueberzumultiplizieren.
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
     * ⛔ DIE KAMERAMATRIX GEHOERT AUF DEN UEBERGEBENEN STAPEL, NICHT NEBEN IHN.
     *
     * <p>Der erste Anlauf schrieb die Ecken mit einer eigenen Matrix und ignorierte den
     * Stapel. Ergebnis: kein Absturz, kein Fehler, nur ein schwarzes Feld -- die Geometrie
     * landete ausserhalb des Sichtfelds. Der Stapel steht naemlich bereits in der EIGENEN
     * Projektion dieses Renderers; eine danebengelegte Matrix bezieht sich auf ein anderes
     * Koordinatensystem.
     *
     * <p>An Vanilla abgelesen ({@code javap -c} auf {@code GuiEntityRenderer#renderToTexture}):
     * dort steht {@code stapel.translate(...)} und {@code stapel.mulPose(...)}, und
     * gezeichnet wird anschliessend mit der Pose DIESES Stapels.
     */
    @Override
    protected void renderToTexture(VorschauGitterZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        /* Messfuehler Aufgabe 96: kommt der Bildgraph hier ueberhaupt an? Ohne das sind
         * "der Zustand wird nie zugestellt" und "er wird zugestellt und zeichnet ins Leere"
         * von aussen nicht zu unterscheiden. Standardmaessig aus, siehe Messpunkt. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("pip.gitter",
            zustand.x0() + "," + zustand.y0() + " bis " + zustand.x1() + "," + zustand.y1());

        /* ⭐ WAS LIEGT SCHON AUF DEM STAPEL? Das ist die Frage, an der zwei Versuche
         * gescheitert sind, und sie ist hier direkt abzulesen -- kein Dekompilieren noetig.
         * Der Grundzeichner hat den Stapel bereits eingerichtet; wer seine eigene Matrix
         * darueber multipliziert, muss wissen, worauf. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("pip.stapel",
            "vorgabe=" + stapel.last().pose().toString().replace('\n', ' ')
            + " || ansicht=" + zustand.ansicht().toString().replace('\n', ' '));

        stapel.pushPose();

        /* ⭐ VOM BILDPUNKT-RAUM IN DEN NORMIERTEN GERAETERAUM (2026-08-01, Vorgabe gemessen).
         *
         * Der Grundzeichner uebergibt den Stapel so:
         *
         *     scale(2, 2, -2) . translate(420, 644, 0)
         *
         * Also Bildpunkte, Ursprung waagerecht MITTIG und senkrecht UNTEN, Y nach unten,
         * eine Einheit = ein GUI-Punkt. Eine Welt-Kameramatrix direkt darauf zu
         * multiplizieren ergibt ein Gitter von 20 Punkten Breite am unteren Rand -- genau
         * das war der Befund der Probe (92 Punkte breit, EINEN hoch, y=641 von 644).
         *
         * Richtig ist der uebliche Weg: den Stapel so einrichten, dass er den normierten
         * Geraeteraum (-1..1) auf die Flaeche abbildet, und DANN Projektion x Ansicht
         * darauf. Drei Schritte:
         *   1. Ursprung von unten in die Mitte
         *   2. -1..1 auf die halbe Breite/Hoehe, Y dabei umdrehen (Welt zeigt hoch,
         *      dieser Raum runter)
         *   3. Projektion x Ansicht -- die steckt seit heute in ansicht(), vorher war es
         *      nur die Ansicht, weshalb es keine Perspektive gab. */
        float halbeBreite = (zustand.x1() - zustand.x0()) / 2F;
        float halbeHoehe = (zustand.y1() - zustand.y0()) / 2F;

        stapel.translate(0F, -halbeHoehe, 0F);
        stapel.scale(halbeBreite, -halbeHoehe, -1F);
        stapel.mulPose(zustand.ansicht());

        sammler.submitCustomGeometry(stapel, RenderTypes.LINES, (pose, ecken) ->
        {
            for (int i = 0; i <= 10; i ++)
            {
                float f = i - 5;

                if (i == 0)
                {
                    linie(ecken, pose, f, -5, f, 5, 0F, 0F, 1F);
                    linie(ecken, pose, -5, f, 5, f, 1F, 0F, 0F);
                }
                else
                {
                    linie(ecken, pose, f, -5, f, 5, 0.25F, 0.25F, 0.25F);
                    linie(ecken, pose, -5, f, 5, f, 0.25F, 0.25F, 0.25F);
                }
            }
        });

        stapel.popPose();
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
        com.mojang.blaze3d.vertex.PoseStack.Pose pose,
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

        ecken.addVertex(pose, x1, 0F, z1).setColor(r, g, b, 1F).setNormal(pose, dx, 0F, dz).setLineWidth(2F);
        ecken.addVertex(pose, x2, 0F, z2).setColor(r, g, b, 1F).setNormal(pose, dx, 0F, dz).setLineWidth(2F);
    }
}
