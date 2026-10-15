package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.graphics.Messpunkt;
import mchorse.bbs_mod.graphics.Projektion;
import mchorse.bbs_mod.graphics.Sammelpass;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.ui.utils.Gizmo;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4fStack;

import java.lang.reflect.Field;

/**
 * Zeichnet das Gizmo als Bild im Bild statt als Sofortzug.
 *
 * <p>Warum ueberhaupt, steht in {@link GizmoZustand}. Kurz: die GUI von 26.2 zeichnet in zwei
 * Phasen, ein Sofortzug landet deshalb unter dem Panel-Hintergrund; und {@code Batcher2D.netz}
 * scheidet aus, weil das Gizmo eine eigene Projektion und einen eigenen Tiefenpuffer braucht.
 *
 * <h2>Drei Dinge werden dem Gizmo untergeschoben</h2>
 *
 * <ol>
 * <li>{@link Projektion} -- die Projektion des Sichtfelds. Ohne sie zeichnet der Durchgang
 *     mit der Bildpunkt-Projektion des Grundzeichners.</li>
 * <li>Die globale Modellansicht als Einheitsmatrix. Die Zuege des Gizmos reichen
 *     {@code new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())} als Modellansicht durch und tragen die
 *     Pose in den Ecken; stuende dort noch etwas, kaeme es ein zweites Mal obendrauf.
 *     Denselben Griff tat frueher {@code MatrixStackUtils.cacheMatrices()}.</li>
 * <li>{@link Zeichenziel} -- die Textur DIESES Zeichners. Ohne sie zeichnet
 *     {@code SofortZeichner} ins Hauptbild, denn dort ist das Ziel der Normalfall.</li>
 * </ol>
 *
 * <h2>⚠️ Warum Reflexion</h2>
 *
 * <p>{@link PictureInPictureRenderer} haelt {@code textureView} und {@code depthTextureView}
 * PRIVAT; eine Unterklasse kommt nicht heran. Dasselbe Vorgehen wie in
 * {@link VorschauModellRenderer} und {@link MiniaturSammelRenderer} -- beim naechsten ohnehin
 * faelligen Boot durch ein Zugriffs-Mixin ersetzen.
 */
public class GizmoRenderer extends mchorse.bbs_mod.compat.alt.BildImBild<GizmoZustand>
{
    private static Field farbFeld;
    private static Field tiefenFeld;

    @Override
    public Class<GizmoZustand> getRenderStateClass()
    {
        return GizmoZustand.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "bbs gizmo";
    }

    @Override
    protected void renderToTexture(GizmoZustand zustand, PoseStack stapel, SubmitNodeCollector sammler)
    {
        GpuTextureView farbe = feld(true);
        GpuTextureView tiefe = feld(false);

        Messpunkt.zaehle("pip.gizmo", zustand.x0() + "," + zustand.y0()
            + " bis " + zustand.x1() + "," + zustand.y1() + " ziel=" + (farbe != null));

        if (farbe == null)
        {
            return;
        }

        /* Der uebergebene Stapel bleibt unberuehrt -- das Gizmo baut sich seinen eigenen aus
         * der mitgereisten Ansicht.
         *
         * ⛔ NIEMALS den Stapel dieses Zeichners in den UI-Stapel kopieren (oder umgekehrt).
         * Das hat die Instanz NATIV gerissen -- kein Java-Fehler, kein Absturzbericht, das Log
         * endet mitten im Bild. Die Warnung steht ausfuehrlich in MiniaturSammelZustand. */
        com.mojang.blaze3d.vertex.PoseStack global = RenderSystem.getModelViewStack();

        global.pushMatrix();
        global.identity();

        Projektion.setzen(zustand.projektion(), ProjectionType.ORTHOGRAPHIC);
        Zeichenziel.setzen(farbe, tiefe);

        try
        {
            /* ⛔ EIGENER SAMMLER. In dieser Phase ist der Sammler des UI-Durchlaufs laengst
             * geschlossen; greift irgendetwas im Zeichenweg danach, wirft
             * FormSammler.pflicht() "Kein Sammler angemeldet" -- in JEDEM Bild.
             *
             * ⛔ AUSNAHMEN ABFANGEN. Hier wartet kein Aufrufer mehr, der etwas auffangen
             * koennte: eine kaputte Stelle im Gizmo risse sonst das ganze Spiel mit. Ein
             * unsichtbares Gizmo ist ein Fehler, ein Absturz kostet dem Nutzer seine Szene. */
            /* All gizmo parts go to the same PIP target: one render pass instead of one per part. */
            FormSammler.mitEigenemSammler(() ->
            {
                Sammelpass.beginnen();

                try
                {
                    Gizmo.INSTANCE.zeichneInTextur(zustand.ansicht(), zustand.viewportSkalierung());
                }
                finally
                {
                    Sammelpass.beenden();
                }
            });
        }
        catch (Throwable t)
        {
            Messpunkt.stoerung("gizmo.zeichnen", t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        finally
        {
            /* ⛔ IMMER freigeben. Bliebe das Ziel stehen, zeichnete alles Folgende -- Welt
             * eingeschlossen -- in diese Gizmo-Textur (die Falle von 06fbc62e0). */
            Zeichenziel.freigeben();
            global.popMatrix();
        }
    }

    /**
     * Das Farb- oder Tiefenziel dieses Zeichners, per Reflexion.
     *
     * <p>⛔ SCHEITERT LAUT, nicht still -- ein {@code catch}, das nur {@code null} liefert,
     * verwandelt einen weggebrochenen Feldnamen in ein unsichtbares Gizmo ohne jede Spur,
     * also genau in das Symptom, das dieser Zeichner beheben soll.
     */
    private GpuTextureView feld(boolean farbe)
    {
        try
        {
            if (farbFeld == null)
            {
                Field f = mchorse.bbs_mod.utils.LaufzeitFeld.holen(PictureInPictureRenderer.class, "textureView", "field_60572");
                Field t = mchorse.bbs_mod.utils.LaufzeitFeld.holen(PictureInPictureRenderer.class, "depthTextureView", "field_60573");

                f.setAccessible(true);
                t.setAccessible(true);

                farbFeld = f;
                tiefenFeld = t;
            }

            return (GpuTextureView) (farbe ? farbFeld : tiefenFeld).get(this);
        }
        catch (Exception e)
        {
            Messpunkt.stoerung("gizmo.reflexion",
                e.getClass().getSimpleName() + ": " + e.getMessage());

            return null;
        }
    }
}
