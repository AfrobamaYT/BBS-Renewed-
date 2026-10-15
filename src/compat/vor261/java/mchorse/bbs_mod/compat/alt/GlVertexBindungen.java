package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPassAlt;
import mchorse.bbs_mod.compat.RenderPipelineAlt;
import mchorse.bbs_mod.compat.VertexFormatAlt;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 26.2's vertex bindings on raw OpenGL, for the versions before 26.1, where Mojang's GL pass
 * knows one interleaved buffer only. One vertex array per pipeline; each draw points the
 * attributes of every binding at its slice (offset, stride, and for per-instance bindings the
 * first instance, which is what Vulkan's firstInstance does to instance-rate attributes).
 * Attribute locations follow the pipeline's combined format (RenderPipelineBuilderAltMixin):
 * every attribute and matrix column in binding order. GL state goes through GlStateManager,
 * whose caches Mojang's own draws rely on.
 */
public final class GlVertexBindungen
{
    private static final Map<RenderPipeline, Integer> ARRAYS = new IdentityHashMap<>();

    private GlVertexBindungen()
    {}

    /** Whether this draw needs the raw path instead of Mojang's VertexArrayCache. */
    public static boolean noetig(RenderPipeline pipeline, RenderPassAlt pass)
    {
        if (((RenderPipelineAlt) pipeline).bbsEigeneBindungen()) return true;

        GpuBufferSlice erste = pass.bbsBindungen()[0];

        return erste != null && erste.offset() != 0;
    }

    public static void binden(RenderPipeline pipeline, RenderPassAlt pass)
    {
        Integer array = ARRAYS.get(pipeline);

        if (array == null)
        {
            array = GlStateManager._glGenVertexArrays();
            ARRAYS.put(pipeline, array);
        }

        GlStateManager._glBindVertexArray(array);

        VertexFormat[] bindungen = ((RenderPipelineAlt) pipeline).getVertexFormatBindings();
        GpuBufferSlice[] slices = pass.bbsBindungen();
        int ort = 0;

        for (int binding = 0; binding < bindungen.length; binding++)
        {
            VertexFormat format = bindungen[binding];

            if (format == null) continue;

            VertexFormatAlt alt = (VertexFormatAlt) format;
            var attribute = alt.bbsAttribute();

            if (attribute.isEmpty()) continue;

            GpuBufferSlice slice = slices[binding];

            if (slice == null) throw new IllegalStateException("Vertex binding " + binding + " of " + pipeline.getLocation() + " has no buffer");

            int stride = alt.bbsGroesse();
            int schritt = alt.getStepRate();
            long basis = slice.offset() + (schritt > 0 ? (long) (pass.bbsErsteInstanz() / schritt) * stride : 0L);

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, ((GlBuffer) slice.buffer()).handle);

            for (VertexFormats.Attribut attribut : attribute)
            {
                GpuFormat f = attribut.format();
                int anzahl = f.glType == 33640 ? 4 : f.componentCount();

                GlStateManager._enableVertexAttribArray(ort);

                if (f.integer()) GlStateManager._vertexAttribIPointer(ort, anzahl, f.glType, stride, basis + attribut.offset());
                else GlStateManager._vertexAttribPointer(ort, anzahl, f.glType, f.normalized(), stride, basis + attribut.offset());

                GL33.glVertexAttribDivisor(ort, schritt);
                ort++;
            }
        }
    }
}
