package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.graphics.Projektion;
import mchorse.bbs_mod.graphics.Zeichenziel;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.lang.reflect.Field;

/**
 * Zeichnet die Modellvorschau als Bild im Bild statt als Sofortzug.
 *
 * <p>Warum ueberhaupt, steht in {@link VorschauModellZustand}. Der Weg ist am Bodengitter
 * bewiesen ({@link VorschauGitterRenderer}); dieser Zeichner benutzt dieselbe Mechanik fuer
 * den teuren Fall.
 *
 * <h2>Zwei Dinge werden dem Modellzeichner untergeschoben</h2>
 *
 * <ol>
 * <li>{@link Projektion} -- die Perspektive der Vorschau. Ohne sie zeichnet der Durchgang
 *     mit der orthografischen Bildpunkt-Projektion des Grundzeichners.</li>
 * <li>{@link Zeichenziel} -- die Textur DIESES Zeichners. Ohne sie schreibt
 *     {@code ModelVAORenderer} ins Hauptbild, denn dort stand das Ziel fest verdrahtet.</li>
 * </ol>
 *
 * <h2>⚠️ Warum Reflexion</h2>
 *
 * <p>{@link PictureInPictureRenderer} haelt {@code textureView} und {@code depthTextureView}
 * PRIVAT -- an der laufenden JVM abgefragt, nicht vermutet. Eine Unterklasse kommt also
 * nicht heran. Sauberer waere ein Zugriffs-Mixin; das verlangt aber einen Neustart, und die
 * Felder werden einmal je Bild gelesen. Beim naechsten ohnehin faelligen Boot ersetzen.
 */
public class VorschauModellRenderer extends PictureInPictureRenderer<VorschauModellZustand>
{
    private static Field farbFeld;
    private static Field tiefenFeld;

    @Override
    public Class<VorschauModellZustand> getRenderStateClass()
    {
        return VorschauModellZustand.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "bbs vorschau-modell";
    }

    @Override
    protected void renderToTexture(VorschauModellZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        GpuTextureView farbe = feld(true);
        GpuTextureView tiefe = feld(false);

        /* Die Rechteckwerte gehoeren mit in die Probe: "laeuft mit ziel=true" beweist nur,
         * DASS gezeichnet wird, nicht WOHIN. Bei #17a war genau das die Frage -- und die
         * Antwort (0,0 bis 960,485, wie beim Gitter) hat den Verdacht auf die Flaeche
         * ausgeschlossen und den Blick auf die Tiefe gelenkt. */
        Messpunkt.zaehle("pip.modell", (zustand.form() == null
            ? "form=null"
            : zustand.form().getClass().getSimpleName() + " ziel=" + (farbe != null))
            + " rechteck=" + zustand.x0() + "," + zustand.y0()
            + " bis " + zustand.x1() + "," + zustand.y1()
            + " massstab=" + zustand.scale());

        if (zustand.form() == null || farbe == null)
        {
            return;
        }

        /* Der Grundzeichner hat den Stapel in seinen Bildpunkt-Raum gestellt. Mit einer
         * echten Projektion muss das weg, sonst wird zweimal projiziert -- am Gitter
         * gemessen (f19f98364). Es bleibt die reine Ansicht. */
        stapel.last().pose().identity();
        stapel.mulPose(zustand.ansicht());

        /* Umkehren: zustand.projektion() ist BBS' camera.projection, also die alte
         * OpenGL-Form, waehrend die Pipeline GREATER_THAN_OR_EQUAL prueft. Ohne das gewinnt
         * die ENTFERNTERE Flaeche — Messung an Projektion#setzenUndUmkehren. Das ist der
         * Rest des Fehlers, den der Loeschwert unten nur halb erwischt hat. */
        Projektion.setzenUndUmkehren(zustand.projektion(), com.mojang.blaze3d.ProjectionType.PERSPECTIVE);

        /* ⛔ TIEFE LEEREN, SONST STEHT DIE DES VORIGEN BILDES DRIN (2026-08-05, Bugliste #16).
         *
         * Vom Nutzer gemeldet, nachdem das Modell im Editor endlich sichtbar war: "von oben
         * sieht der kopf durchsichtig aus und der becken auch und von unten fehlt einfach die
         * brust und die obere haelfte der beine, das selbe gilt fuer links und rechts".
         *
         * Aus einem Blickwinkel richtig, aus anderen fehlen Flaechen -- das ist kein Fehler an
         * der Form, sondern eine Tiefe, die nicht zum aktuellen Bild gehoert. Der Zug, den
         * ModelVAORenderer oeffnet, haengt die Tiefentextur mit OptionalDouble.empty() an,
         * loescht sie also NICHT; bei einer festen Miniatur faellt das nicht auf, bei einer
         * drehbaren Vorschau schon.
         *
         * ⛔ DER LOESCHWERT IST 0.0, NICHT 1.0 -- 26.2 RECHNET REVERSED-Z (2026-08-05).
         *
         * Hier stand 1.0, aus Gewohnheit von OpenGL-Vorgabe. In 26.2 liegt die FERNE bei 0.0;
         * ein Loeschwert von 1.0 setzt den ganzen Puffer auf die NAHEBENE, und danach faellt
         * jedes Fragment durch den Tiefentest. Wirkung am laufenden Spiel: im Form-Editor war
         * das Modell VOLLSTAENDIG unsichtbar -- und weil der Zug lief und sein Rechteck
         * korrekt meldete (0,0 bis 960,485, wie beim Gitter), sah es nach einem Flaechen-
         * oder Zielproblem aus. Ohne Loeschen war es nur TEILWEISE falsch: Flaechen fielen
         * je nach Blickwinkel aus, weil die Tiefe des vorigen Bildes stehenblieb. Genau so
         * hatte es der Nutzer gemeldet ("von oben ist der Kopf durchsichtig").
         *
         * Belegt: bunte Proben auf der Buehne von ~130 auf 10686, und aus vier gedrehten
         * Blickwinkeln bleibt das Modell massiv (Achsenlinien korrekt verdeckt).
         *
         * Farbe bleibt unangetastet (Optional.empty()) -- die kommt von Vanillas
         * Bild-im-Bild-Durchgang. */
        try (com.mojang.renderpearl.api.commands.RenderPass leeren = com.mojang.blaze3d.systems.RenderSystem
            .getDevice().createCommandEncoder().createRenderPass(
                () -> "bbs-vorschau-tiefe-leeren", farbe, java.util.Optional.empty(),
                tiefe, java.util.OptionalDouble.of(0.0)))
        {
            /* Nichts zeichnen: das Anlegen des Durchgangs fuehrt den Loeschwert aus. */
        }

        Zeichenziel.setzen(farbe, tiefe);

        try
        {
            FormRenderingContext kontext = new FormRenderingContext()
                .set(FormRenderType.PREVIEW, zustand.entity(), stapel,
                    net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY,
                    zustand.uebergang())
                .camera(zustand.kamera())
                .modelRenderer();

            /* With the collector this picture in picture is handed: mob, block and item forms
             * submit their orders there and drew nothing in the form editor without it (same
             * cause as the model block, task 122). */
            mchorse.bbs_mod.forms.FormSammler.mitSammler(sammler, () -> FormUtilsClient.render(zustand.form(), kontext));
        }
        finally
        {
            /* ⛔ IMMER freigeben. Bliebe das Ziel stehen, zeichnete alles Folgende -- Welt
             * eingeschlossen -- in diese Vorschautextur. Das ist die Falle, die bei
             * Zeichenbereich schon einmal zugeschlagen hat (06fbc62e0). */
            Zeichenziel.freigeben();
        }
    }

    /**
     * Das Farb- oder Tiefenziel dieses Zeichners, per Reflexion.
     *
     * <p>⛔ SCHEITERT LAUT, nicht still. Ein {@code catch}, das nur {@code null} liefert,
     * verwandelt einen weggebrochenen Feldnamen (Spiel-Update, Neubenennung) in ein
     * schwarzes Vorschaufeld ohne jede Spur -- genau die Sorte Fehler, an der Aufgabe 96
     * wochenlang hing. Darum landet der Grund in {@link Messpunkt#stoerung}, wo er auch
     * ohne eingeschaltete Messung steht.
     */
    private GpuTextureView feld(boolean farbe)
    {
        try
        {
            if (farbFeld == null)
            {
                Field f = PictureInPictureRenderer.class.getDeclaredField("textureView");
                Field t = PictureInPictureRenderer.class.getDeclaredField("depthTextureView");

                f.setAccessible(true);
                t.setAccessible(true);

                farbFeld = f;
                tiefenFeld = t;
            }

            return (GpuTextureView) (farbe ? farbFeld : tiefenFeld).get(this);
        }
        catch (Exception e)
        {
            Messpunkt.stoerung("vorschau-modell.reflexion",
                e.getClass().getSimpleName() + ": " + e.getMessage());

            return null;
        }
    }
}
