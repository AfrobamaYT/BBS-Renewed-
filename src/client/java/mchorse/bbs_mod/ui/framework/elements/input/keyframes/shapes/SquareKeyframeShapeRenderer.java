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
        /* fillRect nimmt seit 447bb3ef7 einen VertexConsumer, taugt also auch fuer den
         * eingereihten Weg -- die Abschrift der vier Ecken ist damit weg. Gleiche Ecken,
         * gleiche Reihenfolge: (x,y) → (x,y+h) → (x+w,y+h) → (x+w,y), eine Farbe viermal. */
        uiContext.batcher.fillRect(builder, matrix4f, x - offset, y - offset, offset * 2, offset * 2, c, c, c, c);
    }
}
