package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 26.2's DynamicUniforms.writeTransform(modelView[, colour]), for the versions before 26.1,
 * whose block also carries ModelOffset and TextureMat: zero offset and identity texture matrix,
 * as vanilla passes them for plain entity draws. Injected into DynamicUniforms.
 */
public interface DynamicUniformsAlt
{
    default GpuBufferSlice writeTransform(Matrix4f modelView, Vector4f colour)
    {
        return ((DynamicUniforms) this).writeTransform(modelView, colour, new Vector3f(), new Matrix4f());
    }

    default GpuBufferSlice writeTransform(Matrix4f modelView)
    {
        return this.writeTransform(modelView, new Vector4f(1F, 1F, 1F, 1F));
    }
}
