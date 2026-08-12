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

    public static void rundeck(Batcher2D batcher, float x, float y, float w, float h, float radius, int color)
    {
        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float saum = saum();

        batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
            (ecken) -> VektorFlaeche.rundeck(ecken, pose, x, y, w, h, radius, color, saum),
            new ScreenRectangle((int) x - 1, (int) y - 1, (int) w + 3, (int) h + 3));
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
            new ScreenRectangle((int) x - rand, (int) y - rand, (int) w + rand * 2 + 1, (int) h + rand * 2 + 1));
    }

    /** Geglaettete Polylinie; {@code punkte} sind x/y-Paare in GUI-Einheiten. */
    public static void linie(Batcher2D batcher, float[] punkte, float breite, int color, ScreenRectangle bereich)
    {
        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float saum = saum();

        batcher.netz(FormPipelines.holen(PrimitiveTopology.TRIANGLES, true, false, false),
            (ecken) -> VektorLinie.zug(ecken, pose, punkte, breite, color, saum), bereich);
    }
}
