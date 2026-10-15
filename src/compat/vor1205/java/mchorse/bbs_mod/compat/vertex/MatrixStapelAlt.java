package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * vor1205: in 1.20.4 ist RenderSystem#getModelViewStack ein PoseStack, ab 1.20.5 ein
 * Matrix4fStack. Die Matrix4fStack-Aufrufe, die BBS darauf macht, hier auf PoseStack.
 */
public interface MatrixStapelAlt
{
    private PoseStack selbst()
    {
        return (PoseStack) (Object) this;
    }

    default void pushMatrix()
    {
        this.selbst().pushPose();
    }

    default void popMatrix()
    {
        this.selbst().popPose();
    }

    default void identity()
    {
        this.selbst().setIdentity();
    }

    default void rotateX(float winkel)
    {
        this.selbst().last().pose().rotateX(winkel);
        this.selbst().last().normal().rotateX(winkel);
    }

    default void rotateY(float winkel)
    {
        this.selbst().last().pose().rotateY(winkel);
        this.selbst().last().normal().rotateY(winkel);
    }

    default void mul(Matrix4fc matrix)
    {
        this.selbst().mulPoseMatrix(new Matrix4f(matrix));
    }

    default void mulPose(Matrix4f matrix)
    {
        this.selbst().mulPoseMatrix(matrix);
    }
}
