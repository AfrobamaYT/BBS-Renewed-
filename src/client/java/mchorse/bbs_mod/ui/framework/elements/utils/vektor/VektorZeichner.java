package mchorse.bbs_mod.ui.framework.elements.utils.vektor;

import com.mojang.blaze3d.PrimitiveTopology;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

/**
 * Einreiher fuer die P2-Vektorformen: schnappt die 2D-Pose des Batchers, waehlt die
 * GUI-Farb-Pipeline und reiht die Tessellation ueber {@code netz()} ein. Ausgelagert,
 * damit der gelistete Batcher-Monolith nur die schmalen Weichen traegt.
 */
public final class VektorZeichner
{
    private VektorZeichner()
    {}

    public static boolean an()
    {
        return BBSSettings.vectorUi != null && BBSSettings.vectorUi.get();
    }

    /** Saumbreite: ein physisches Pixel, in GUI-Einheiten. */
    private static float saum()
    {
        return 1F / (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    /**
     * ⛔ Das Kontroll-Rechteck MUSS durch die Pose — die Ecken gehen ja auch durch sie.
     *
     * <p>Der Renderer cullt am {@code bounds()}-Rechteck. In einem ScrollView zeichnen die
     * Kinder an ihrer UNGESCROLLTEN Area (gemessen: Lagen-Knopf area.y=948 bei einem Panel,
     * das bei ~510 endet) und die Pose schiebt sie ins Bild. Ein Rechteck ohne Pose liegt
     * dann ausserhalb des Ausschnitts, der Zug wird verworfen — der Knopf-TEXT (Vanilla-
     * State, Pose eingerechnet) bleibt, der Hintergrund fehlt. Genau so sah der Nutzer am
     * 2026-08-16 "Rig Layers" als blosse Beschriftungen. {@code Batcher2D.bounds()} macht
     * es fuer Quads seit jeher richtig: {@code transformMaxBounds(pose)}.
     */
    private static ScreenRectangle posiert(Matrix3x2f pose, int x, int y, int w, int h)
    {
        return new ScreenRectangle(x, y, w, h).transformMaxBounds(pose);
    }

    public static void rundeck(Batcher2D batcher, float x, float y, float w, float h, float radius, int color)
    {
        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float saum = saum();

        batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
            (ecken) -> VektorFlaeche.rundeck(ecken, pose, x, y, w, h, radius, color, saum),
            posiert(pose, (int) x - 1, (int) y - 1, (int) w + 3, (int) h + 3));
    }

    /** Weicher Schein + Fuellung — der Vektor-Ersatz fuer dropShadow. */
    public static void schein(Batcher2D batcher, float x, float y, float w, float h, float ausdehnung, int innen, int aussen)
    {
        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float saum = saum();
        float radius = Math.min(6F, ausdehnung + 2F);
        int rand = (int) ausdehnung + 2;

        batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
            (ecken) -> VektorFlaeche.schein(ecken, pose, x, y, w, h, radius, ausdehnung, innen, aussen, saum),
            posiert(pose, (int) x - rand, (int) y - rand, (int) w + rand * 2 + 1, (int) h + rand * 2 + 1));
    }

    /** Geglaettete Polylinie; {@code punkte} sind x/y-Paare in GUI-Einheiten,
     *  {@code bereich} im selben (ungescrollten) Raum wie die Punkte. */
    public static void linie(Batcher2D batcher, float[] punkte, float breite, int color, ScreenRectangle bereich)
    {
        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float saum = saum();

        batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
            (ecken) -> VektorLinie.zug(ecken, pose, punkte, breite, color, saum),
            bereich.transformMaxBounds(pose));
    }
}
