package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.RenderPassAlt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 26.2's draws on Mojang's GL pass before 26.1. Mojang's encoder already draws instanced and
 * with a base vertex; the first instance and the extra bindings are kept here and applied by
 * {@link mchorse.bbs_mod.compat.alt.GlVertexBindungen} when the vertex arrays are bound.
 */
@Mixin(GlRenderPass.class)
public abstract class GlRenderPassAltMixin implements RenderPassAlt
{
    @Shadow
    @Final
    private GlCommandEncoder encoder;
    @Shadow
    private boolean closed;
    @Shadow
    @Final
    protected GpuBuffer[] vertexBuffers;
    @Shadow
    protected VertexFormat.IndexType indexType;

    @Unique
    private final GpuBufferSlice[] bbs$bindungen = new GpuBufferSlice[16];
    @Unique
    private int bbs$ersteInstanz;

    @Override
    public void setVertexBuffer(int binding, GpuBufferSlice slice)
    {
        if (binding < 0 || binding >= this.bbs$bindungen.length) throw new IllegalArgumentException("Vertex buffer slot is out of range: " + binding);

        this.bbs$bindungen[binding] = slice;

        if (binding == 0) this.vertexBuffers[0] = slice == null ? null : slice.buffer();
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance)
    {
        if (this.closed) throw new IllegalStateException("Can't use a closed render pass");

        this.bbs$ersteInstanz = firstInstance;
        this.encoder.executeDraw((GlRenderPass) (Object) this, firstVertex, 0, vertexCount, null, instanceCount);
        this.bbs$ersteInstanz = 0;
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance)
    {
        if (this.closed) throw new IllegalStateException("Can't use a closed render pass");

        this.bbs$ersteInstanz = firstInstance;
        this.encoder.executeDraw((GlRenderPass) (Object) this, baseVertex, firstIndex, indexCount, this.indexType, instanceCount);
        this.bbs$ersteInstanz = 0;
    }

    @Override
    public GpuBufferSlice[] bbsBindungen()
    {
        return this.bbs$bindungen;
    }

    @Override
    public int bbsErsteInstanz()
    {
        return this.bbs$ersteInstanz;
    }
}
