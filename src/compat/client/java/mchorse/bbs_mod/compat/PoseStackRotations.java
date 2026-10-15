package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * PoseStack's rotation methods from Minecraft 26.3, for older versions. Injected into
 * PoseStack by Loom (src/compat/inject) and at runtime by {@link mchorse.bbs_mod.compat.mixin.PoseStackRotationsMixin},
 * so the shared code can call them on every version.
 */
public interface PoseStackRotations
{
    default void rotate(Quaternionfc rotation)
    {
        /* Vor 1.21.5 nimmt mulPose nur Quaternionf. */
        ((PoseStack) this).mulPose(rotation instanceof Quaternionf q ? q : new Quaternionf(rotation));
    }

    default void rotate(Axis axis, float radians)
    {
        ((PoseStack) this).mulPose(axis.rotation(radians));
    }

    default void rotateDegrees(Axis axis, float degrees)
    {
        ((PoseStack) this).mulPose(axis.rotationDegrees(degrees));
    }
}
