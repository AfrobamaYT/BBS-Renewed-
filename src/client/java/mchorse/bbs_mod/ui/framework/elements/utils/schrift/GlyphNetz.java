package mchorse.bbs_mod.ui.framework.elements.utils.schrift;

import com.mojang.blaze3d.vertex.VertexConsumer;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Glyphen als EIN Netz mit Fliesskomma-Ecken — der Weg um Vanillas ganzzahliges Blit herum.
 *
 * <p>⛔ WARUM ES DAS GIBT. {@code BlitRenderState} nimmt {@code int}-Ecken, und
 * {@code Batcher2D.blit} schneidet dafuer ab: {@code x0 = (int) x}. Bei Oberflaechen-Skala 2
 * liegt ein GUI-Punkt auf ZWEI Bildschirmpunkten, ein Glyph wird also auf ein 2-px-Raster
 * gezwungen. Gemessen an "Rig" (Atlas 18 px): das 'g' ist 9 Atlas-Punkte breit und wurde in
 * einen 10 Punkte breiten Kasten gemalt — 11 % Streckung mit Neuabtastung, und die
 * Grundlinie wandert je Glyph um bis zu einen GUI-Punkt. Genau das liest sich als "pixelig".
 *
 * <p>Mit Fliesskomma-Ecken trifft ein Atlas-Punkt wieder genau einen Bildschirmpunkt; die
 * FreeType-Rasterung kommt unveraendert an. Nebenbei wird aus einem Zeichenzustand JE GLYPH
 * einer je Textstueck.
 */
public final class GlyphNetz
{
    /** Eine Glyphenflaeche in GUI-Punkten plus ihr Ausschnitt im Atlas (in Atlas-Punkten). */
    public record Flaeche(float x, float y, float w, float h, float u, float v, float uw, float vh, int farbe) {}

    private final List<Flaeche> flaechen = new ArrayList<>();

    public void lege(float x, float y, float w, float h, float u, float v, float uw, float vh, int farbe)
    {
        this.flaechen.add(new Flaeche(x, y, w, h, u, v, uw, vh, farbe));
    }

    public boolean leer()
    {
        return this.flaechen.isEmpty();
    }

    /**
     * Reicht die gesammelten Flaechen als ein Netz ein.
     *
     * @param seite Kantenlaenge des Atlas — die Ecken kommen in Atlas-Punkten herein und
     *              werden hier auf 0..1 normiert.
     */
    public void einreichen(Batcher2D batcher, Texture textur, int seite)
    {
        if (this.flaechen.isEmpty())
        {
            return;
        }

        /* ⛔ Die Lage steckt in der Kontext-Matrix (Elementversatz UND jede Drehung aus
         * text(..., winkel)). addVertexWith2DPose traegt sie je Ecke ein — dieselbe
         * Mechanik, die UIQuad benutzt. */
        Matrix3x2fc pose = new Matrix3x2f(batcher.getContext().bbsPose());
        List<Flaeche> satz = new ArrayList<>(this.flaechen);

        batcher.netz(RenderPipelines.GUI_TEXTURED, (VertexConsumer bau) ->
        {
            for (Flaeche f : satz)
            {
                float u1 = f.u() / seite;
                float v1 = f.v() / seite;
                float u2 = (f.u() + f.uw()) / seite;
                float v2 = (f.v() + f.vh()) / seite;

                bau.addVertexWith2DPose(pose, f.x(), f.y()).setUv(u1, v1).setColor(f.farbe());
                bau.addVertexWith2DPose(pose, f.x(), f.y() + f.h()).setUv(u1, v2).setColor(f.farbe());
                bau.addVertexWith2DPose(pose, f.x() + f.w(), f.y() + f.h()).setUv(u2, v2).setColor(f.farbe());
                bau.addVertexWith2DPose(pose, f.x() + f.w(), f.y()).setUv(u2, v1).setColor(f.farbe());
            }
        }, textur.guiSetup(), this.grenzen(pose));

        this.flaechen.clear();
    }

    /** Umschliessendes Rechteck, durch dieselbe Matrix geschoben wie die Ecken. */
    private ScreenRectangle grenzen(Matrix3x2fc pose)
    {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (Flaeche f : this.flaechen)
        {
            minX = Math.min(minX, f.x());
            minY = Math.min(minY, f.y());
            maxX = Math.max(maxX, f.x() + f.w());
            maxY = Math.max(maxY, f.y() + f.h());
        }

        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);

        return new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x, (int) Math.ceil(maxY) - y)
            .transformMaxBounds(pose);
    }
}
