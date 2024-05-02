package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MatrixStackUtils
{
    private static Matrix3f normal = new Matrix3f();

    private static Matrix4f oldProjection = new Matrix4f();
    private static Matrix4f oldMV = new Matrix4f();
    private static Matrix3f oldInverse = new Matrix3f();

    public static void scaleStack(MatrixStack stack, float x, float y, float z)
    {
        stack.peek().getPositionMatrix().scale(x, y, z);
        stack.peek().getNormalMatrix().scale(x < 0F ? -1F : 1F, y < 0F ? -1F : 1F, z < 0F ? -1F : 1F);
    }

    public static void cacheMatrices()
    {
        /* Cache the global stuff */
        oldProjection.set(RenderSystem.getProjectionMatrix());
        oldMV.set(RenderSystem.getModelViewMatrix());
        oldInverse.set(RenderSystem.getInverseViewRotationMatrix());

        MatrixStack renderStack = RenderSystem.getModelViewStack();

        renderStack.push();
        renderStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        renderStack.pop();
    }

    public static void restoreMatrices()
    {
        /* Return back to orthographic projection */
        RenderSystem.setProjectionMatrix(oldProjection, VertexSorter.BY_Z);
        RenderSystem.setInverseViewRotationMatrix(oldInverse);

        MatrixStack renderStack = RenderSystem.getModelViewStack();

        renderStack.push();
        renderStack.loadIdentity();
        MatrixStackUtils.multiply(renderStack, oldMV);
        RenderSystem.applyModelViewMatrix();
        renderStack.pop();
    }

    public static void applyTransform(MatrixStack stack, Transform transform)
    {
        stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        stack.multiply(RotationAxis.POSITIVE_Z.rotation(transform.rotate.z));
        stack.multiply(RotationAxis.POSITIVE_Y.rotation(transform.rotate.y));
        stack.multiply(RotationAxis.POSITIVE_X.rotation(transform.rotate.x));
        stack.multiply(RotationAxis.POSITIVE_Z.rotation(transform.rotate2.z));
        stack.multiply(RotationAxis.POSITIVE_Y.rotation(transform.rotate2.y));
        stack.multiply(RotationAxis.POSITIVE_X.rotation(transform.rotate2.x));
        scaleStack(stack, transform.scale.x, transform.scale.y, transform.scale.z);
    }

    public static void multiply(MatrixStack stack, Matrix4f matrix)
    {
        normal.set(matrix);

        stack.peek().getPositionMatrix().mul(matrix);
        stack.peek().getNormalMatrix().mul(normal);
    }
}