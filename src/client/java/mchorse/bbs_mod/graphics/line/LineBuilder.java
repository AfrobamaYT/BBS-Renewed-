package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.PrimitiveTopology;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Line builder 2D
 *
 * This class provides a neat way to construct 2D line
 * segments that is thicker than default OpenGL3 line renderer.
 */
public class LineBuilder <T>
{
    public float thickness;
    public List<Line<T>> lines = new ArrayList<>();

    public LineBuilder(float thickness)
    {
        this.thickness = thickness;
    }

    public LineBuilder<T> add(float x, float y)
    {
        return this.add(x, y, null);
    }

    public LineBuilder<T> add(float x, float y, T user)
    {
        if (this.lines.isEmpty())
        {
            this.push();
        }

        Line line = this.lines.get(this.lines.size() - 1);

        line.add(x, y, user);

        return this;
    }

    public LineBuilder<T> push()
    {
        return this.push(new Line<>());
    }

    public LineBuilder<T> push(Line<T> line)
    {
        this.lines.add(line);

        return this;
    }

    public List<List<LinePoint<T>>> build()
    {
        List<List<LinePoint<T>>> output = new ArrayList<>();

        for (Line line : this.lines)
        {
            List<LinePoint<T>> compiled = line.build(this.thickness);

            if (!compiled.isEmpty())
            {
                output.add(compiled);
            }
        }

        return output;
    }

    /**
     * Zeichnet die Linien in die Oberflaeche.
     *
     * <h2>⛔ WARUM HIER KEIN {@code SofortZeichner} STEHT (2026-07-31 gemessen)</h2>
     *
     * <p>Bis zu diesem Tag oeffnete diese Methode einen eigenen Zeichendurchgang
     * ({@code SofortZeichner.beginnen} + {@code zeichnen}). Das kompiliert, laeuft ohne
     * Fehler, wirft nichts — und man sieht KEINE EINZIGE LINIE.
     *
     * <p>26.2 zeichnet die Oberflaeche in zwei Phasen: der Durchlauf, in dem diese Methode
     * laeuft, SAMMELT nur; gezeichnet wird alles spaeter in {@code GuiRenderer.render()}.
     * Ein sofort geoeffneter Durchgang malt also, BEVOR das GUI gezeichnet wird — und
     * verschwindet danach unter dem Panel-Hintergrund. {@link Batcher2D#netz} warnt
     * woertlich davor; dieser Aufrufer hielt sich als einziger nicht daran.
     *
     * <p>Gemessen im Graph-Panel: dieselbe Methode zeichnete Gitter (ueber Batcher2D,
     * sichtbar) und Kurve (ueber SofortZeichner, unsichtbar). Der Wechsel der Funktion von
     * {@code sin(x)} auf {@code sin(x)*3} aenderte NUR das Textfeld; die Graphflaeche blieb
     * Pixel fuer Pixel gleich. Betroffen waren ausserdem UIKeyframeGraph,
     * UIVector3KeyframeGraph, UICurve und InterpolationRenderer -- also die Keyframe-Kurven
     * des Film-Editors.
     *
     * <p>⚠️ Das Lambda laeuft SPAETER. Alles, was es liest, muss bis dahin gueltig sein:
     * {@code matrix} und {@code points} werden hier fertig berechnet und danach nicht mehr
     * angefasst.
     */
    public void render(Batcher2D batcher2D, ILineRenderer<T> renderer)
    {
        /* P2: geglaettete Polylinie fuer die Einfarb-Zeichnung (Kurven des Editors);
         * Renderer mit eigener Punktlogik behalten den alten Weg. Farbe SOFORT
         * einfrieren — der Renderer ist ein je Aufruf umgefaerbter Singleton. */
        if (mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.an() && renderer instanceof SolidColorLineRenderer solid)
        {
            int farbe = solid.argb();

            for (Line<T> line : this.lines)
            {
                if (line.points.size() < 2)
                {
                    continue;
                }

                float[] xy = new float[line.points.size() * 2];

                for (int i = 0; i < line.points.size(); i++)
                {
                    xy[i * 2] = line.points.get(i).x;
                    xy[i * 2 + 1] = line.points.get(i).y;
                }

                mchorse.bbs_mod.ui.framework.elements.utils.vektor.VektorZeichner.linie(batcher2D, xy, this.thickness, farbe, this.bereichVon(line.points));
            }

            return;
        }

        Matrix4f matrix = new Matrix4f().mul(batcher2D.getContext().pose());
        List<List<LinePoint<T>>> build = this.build();

        for (List<LinePoint<T>> points : build)
        {
            if (points.isEmpty())
            {
                continue;
            }

            /* mischen = true: die Linienfarben kommen mit Alpha herein. tiefePruefen und
             * culling = false: beides koennte eine 2D-Linie nur unsichtbar machen, und
             * Tiefe aus heisst auch Tiefenschreiben aus -- es bleibt keine Wand vor
             * spaeteren Zeichnungen stehen. */
            batcher2D.netz(
                FormPipelines.holen(PrimitiveTopology.TRIANGLE_STRIP, true, false, false),
                (ecken) ->
                {
                    for (LinePoint<T> point : points)
                    {
                        renderer.render(ecken, matrix, point);
                    }
                },
                bereichVon(points));
        }
    }

    /**
     * Die Bildschirmflaeche, die dieser Linienzug belegt.
     *
     * <p>{@code Batcher2D.netz} braucht sie zum Keulen und zum Sortieren innerhalb einer
     * Ebene. ⚠️ Zu KLEIN angegeben heisst: das Netz wird weggekeult und man sucht den
     * Fehler wieder im Zeichencode. Darum grosszuegig aufgerundet und um die halbe
     * Linienbreite erweitert -- die Ecken liegen quer zur Linie ausserhalb der Punkte.
     */
    private ScreenRectangle bereichVon(List<LinePoint<T>> points)
    {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (LinePoint<T> point : points)
        {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        int rand = (int) Math.ceil(this.thickness) + 2;
        int x = (int) Math.floor(minX) - rand;
        int y = (int) Math.floor(minY) - rand;

        return new ScreenRectangle(x, y,
            (int) Math.ceil(maxX) + rand - x,
            (int) Math.ceil(maxY) + rand - y);
    }
}
