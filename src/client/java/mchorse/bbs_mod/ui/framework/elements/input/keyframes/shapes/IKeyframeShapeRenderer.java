package mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.joml.Matrix4f;

/**
 * ⚠️ Der Ecken-Empfaenger ist {@link VertexConsumer}, NICHT {@code BufferBuilder}.
 *
 * <p>Die Formen werden seit dem 26.2-Umbau nicht mehr in einen eigenen Sofort-Puffer
 * geschrieben, sondern in den GUI-Strom eingereiht ({@code Batcher2D.netz}). Was dort im
 * Lambda ankommt, ist der Ecken-Empfaenger des Renderers — ein {@code VertexConsumer}, den
 * man nicht auf {@code BufferBuilder} verengen darf. {@code BufferBuilder} erfuellt das
 * Interface weiterhin, alte Aufrufwege bleiben also moeglich.
 */
public interface IKeyframeShapeRenderer
{
    public IKey getLabel();

    public Icon getIcon();

    public void renderKeyframe(UIContext uiContext, VertexConsumer builder, Matrix4f matrix4f, int x, int y, int offset, int c);

    public default void renderKeyframeBackground(UIContext uiContext, VertexConsumer builder, Matrix4f matrix4f, int x, int y, int offset, int c)
    {}
}