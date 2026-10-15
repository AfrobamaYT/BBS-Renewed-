package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import mchorse.bbs_mod.compat.DynamicUniformsAlt;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: der DynamicTransforms-Block (ModelViewMat, ColorModulator,
 * ModelOffset, TextureMat) je Zeichnung, abgelegt im Transient-Speicher der Schicht.
 */
public class DynamicUniforms implements AutoCloseable, DynamicUniformsAlt
{
    public static final int TRANSFORM_UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec3().putMat4f().get();

    public void reset()
    {}

    @Override
    public void close()
    {}

    public GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colour, Vector3fc offset, Matrix4fc textureMatrix)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            var daten = Std140Builder.onStack(stack, TRANSFORM_UBO_SIZE).putMat4f(modelView).putVec4(colour).putVec3(offset).putMat4f(textureMatrix).get();

            return mchorse.bbs_mod.compat.gpu.UniformDaten.hochladen(daten);
        }
    }

    public GpuBufferSlice[] writeTransforms(Transform... transforms)
    {
        GpuBufferSlice[] slices = new GpuBufferSlice[transforms.length];

        for (int i = 0; i < transforms.length; i++)
        {
            Transform t = transforms[i];

            slices[i] = this.writeTransform(t.modelView(), t.colorModulator(), t.modelOffset(), t.textureMatrix());
        }

        return slices;
    }

    public record Transform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix)
    {}
}
