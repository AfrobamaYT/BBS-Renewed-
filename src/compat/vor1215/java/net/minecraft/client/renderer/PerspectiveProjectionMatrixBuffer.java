package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import mchorse.bbs_mod.compat.gpu.UniformDaten;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** BBS-Shim fuer Minecraft vor 1.21.6: ein Projektions-Uniform aus einer Matrix (UniformDaten). */
public class PerspectiveProjectionMatrixBuffer implements AutoCloseable
{
    public PerspectiveProjectionMatrixBuffer(String name)
    {}

    public GpuBufferSlice getBuffer(Matrix4f matrix)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            return UniformDaten.hochladen(Std140Builder.onStack(stack, 64).putMat4f(matrix).get());
        }
    }

    @Override
    public void close()
    {}
}
