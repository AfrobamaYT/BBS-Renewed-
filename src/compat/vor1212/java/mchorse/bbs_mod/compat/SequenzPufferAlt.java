package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.mixin.SequenzPufferAltMixin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * vor1212: 1.21.1 fuellt den Indexpuffer nur ueber bind(n), und das bindet ihn als
 * Element-Puffer -- also ans gerade gebundene VAO. Darum kurz VAO 0 binden und danach das
 * vorige wiederherstellen; der GL-Name wird als fremder GpuBuffer (ohne Loeschen) gereicht.
 */
public interface SequenzPufferAlt
{
    default GpuBuffer getBuffer(int anzahl)
    {
        RenderSystem.AutoStorageIndexBuffer puffer = (RenderSystem.AutoStorageIndexBuffer) (Object) this;
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        GL30.glBindVertexArray(0);
        puffer.bind(anzahl);
        GL30.glBindVertexArray(vao);

        return GpuBuffer.fremd(BufferType.INDICES, ((SequenzPufferAltMixin) this).bbs$name(), anzahl * puffer.type().bytes);
    }
}
