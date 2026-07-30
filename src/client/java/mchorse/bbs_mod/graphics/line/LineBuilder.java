package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.SofortZeichner;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
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

    public void render(Batcher2D batcher2D, ILineRenderer<T> renderer)
    {
        Matrix4f matrix = batcher2D.getContext().pose().last().pose();
        List<List<LinePoint<T>>> build = this.build();

        for (List<LinePoint<T>> points : build)
        {
            /* Zwei Ecken je Punkt a 16 Byte (POSITION_COLOR); der Puffer waechst
             * notfalls nach, die Schaetzung spart nur das Nachwachsen. */
            try (ByteBufferBuilder speicher = new ByteBufferBuilder(points.size() * 2 * 16 + 256))
            {
                BufferBuilder builder = SofortZeichner.beginnen(speicher, PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

                for (LinePoint<T> point : points)
                {
                    renderer.render(builder, matrix, point);
                }

                /* mischen = true: der alte Block rief enableBlend() (die Linienfarben
                 * kommen mit Alpha herein). tiefePruefen/culling = false: der alte Block
                 * setzte dafuer nichts, und beides koennte eine 2D-Linie nur unsichtbar
                 * machen — Tiefe aus heisst hier auch Tiefenschreiben aus, also bleibt
                 * auch keine Wand vor spaeteren Zeichnungen stehen.
                 * modelView ist die Einheitsmatrix: die Pose steckt schon in jeder Ecke,
                 * ILineRenderer.render bekommt matrix herein und rechnet sie ein. */
                SofortZeichner.zeichnen(
                    FormPipelines.holen(PrimitiveTopology.TRIANGLE_STRIP, true, false, false),
                    builder.build(),
                    new Matrix4f(),
                    null,
                    null);
            }
        }
    }
}