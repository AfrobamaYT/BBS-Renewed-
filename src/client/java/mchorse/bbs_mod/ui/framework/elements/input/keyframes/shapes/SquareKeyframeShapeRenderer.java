package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Matrix4f;

public class SquareKeyframeShapeRenderer implements IKeyframeShapeRenderer
{
    @Override
    public IKey getLabel()
    {
        return UIKeys.KEYFRAMES_SHAPES_SQUARE;
    }

    @Override
    public Icon getIcon()
    {
        return Icons.SQUARE;
    }

    @Override
    public void renderKeyframe(UIContext uiContext, VertexConsumer builder, Matrix4f matrix4f, int x, int y, int offset, int c)
    {
        /* ⛔ Hier stand batcher.fillRect(builder, ...) -- das nimmt einen Sofort-Puffer.
         * Eingereiht wird aber ein VertexConsumer, und Batcher2D gehoert in dieser Welle
         * einem anderen Arbeiter. Die vier Ecken sind Zeile fuer Zeile dieselben wie dort
         * (oben links, unten links, unten rechts, oben rechts) -- gleiche Reihenfolge,
         * gleiche Drehrichtung, alle vier Farben identisch. */
        builder.addVertex(matrix4f, x - offset, y - offset, 0F).setColor(c);
        builder.addVertex(matrix4f, x - offset, y + offset, 0F).setColor(c);
        builder.addVertex(matrix4f, x + offset, y + offset, 0F).setColor(c);
        builder.addVertex(matrix4f, x + offset, y - offset, 0F).setColor(c);
    }
}
