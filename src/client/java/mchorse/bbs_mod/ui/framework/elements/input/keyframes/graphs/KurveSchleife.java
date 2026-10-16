package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The graph of a track with a keyframe loop: the actual looped playback, sampled every two pixels
 * of the visible area, instead of a line from the source's last key straight to the next ordinary
 * key; the original Bezier handles stay where they are editable. From BBS FS 494fe5d19
 * (Wemppy4/bbs-fs, MIT), drawn the 26.3 way UIKeyframeGraph draws (kept apart: that file is
 * at the 800-line limit).
 */
final class KurveSchleife
{
    private KurveSchleife()
    {}

    static void zeichnen(UIKeyframeGraph graph, UIContext context, Matrix4f matrix)
    {
        var keyframes = graph.keyframes;
        var sheet = graph.sheet;
        LineBuilder line = new LineBuilder(0.7F);
        float previousSource = -Float.MAX_VALUE;

        for (int x = keyframes.graphArea.x; x <= keyframes.graphArea.ex(); x += 2)
        {
            float tick = (float) keyframes.fromGraphX(x);
            float source = sheet.channel.getSourceTick(tick);

            if (source < previousSource)
            {
                line.push();
            }

            Object value = sheet.channel.interpolate(tick);

            if (value != null)
            {
                line.add(x, graph.toGraphY(sheet.channel.getFactory().getY(value)));
            }

            previousSource = source;
        }

        List<Keyframe> originals = sheet.channel.getKeyframes();

        for (int index = 0; index < originals.size(); index++)
        {
            Keyframe key = originals.get(index);
            int x = keyframes.toGraphX(key.getTick()), y = graph.toGraphY(key.getY(0));

            if (x < keyframes.graphArea.x - 20 || x > keyframes.graphArea.ex() + 20)
            {
                continue;
            }

            Keyframe previous = sheet.channel.get(index - 1);

            if (key.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                line.push();
                line.add(x, y);
                line.add(keyframes.toGraphX(key.getTick() + key.rx), graph.toGraphY(key.getY(0) + key.ry));
            }

            if (previous != null && previous.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                line.push();
                line.add(x, y);
                line.add(keyframes.toGraphX(key.getTick() - key.lx), graph.toGraphY(key.getY(0) + key.ly));
            }
        }

        line.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(sheet.color, 1F))));
        context.batcher.netz(FormPipelines.holen(PrimitiveTopology.QUADS, true, false, false),
            (ecken) -> graph.renderGraphPointShapes(context, ecken, matrix, originals),
            mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet.flaeche(keyframes.graphArea));
    }
}
