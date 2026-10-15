package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.GlVertexBindungen;
import mchorse.bbs_mod.compat.gpu.GlProgramm;
import mchorse.bbs_mod.compat.gpu.PipelineZustand;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * BBS-Shim fuer Minecraft vor 1.21.5: ein Zeichendurchgang der eigenen GL-Schicht. Zustand
 * geht ueber 1.21.4s GlStateManager (dessen Zwischenspeicher Vanilla benutzt); Sampler-
 * Objekte werden nach dem Durchgang wieder geloest, weil 1.21.4 ohne sie arbeitet und sonst
 * die Texturparameter der Vanilla-Texturen ueberdeckt wuerden.
 */
public class GlRenderPass implements RenderPass
{
    private final GlCommandEncoder encoder;
    private final Supplier<String> name;
    private final ScissorState schere = new ScissorState();
    private final Map<String, GpuTextureView> texturen = new HashMap<>();
    private final Map<String, GpuSampler> sampler = new HashMap<>();
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final GpuBufferSlice[] bindungen = new GpuBufferSlice[16];
    private RenderPipeline pipeline;
    private GpuBuffer indexPuffer;
    private VertexFormat.IndexType indexTyp;
    private int ersteInstanz;
    private int hoechsteEinheit = -1;
    private boolean geschlossen;

    GlRenderPass(GlCommandEncoder encoder, Supplier<String> name)
    {
        this.encoder = encoder;
        this.name = name;
    }

    @Override public void pushDebugGroup(Supplier<String> label) {}
    @Override public void popDebugGroup() {}

    @Override
    public void setPipeline(RenderPipeline pipeline)
    {
        this.pipeline = pipeline;
    }

    @Override
    public void bindTexture(String name, GpuTextureView view, GpuSampler sampler)
    {
        this.texturen.put(name, view);
        this.sampler.put(name, sampler);
    }

    @Override
    public void setUniform(String name, GpuBuffer buffer)
    {
        this.uniforms.put(name, buffer.slice());
    }

    @Override
    public void setUniform(String name, GpuBufferSlice slice)
    {
        this.uniforms.put(name, slice);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height)
    {
        this.schere.enable(x, y, width, height);
    }

    @Override
    public void disableScissor()
    {
        this.schere.disable();
    }

    @Override
    public void setVertexBuffer(int slot, GpuBuffer buffer)
    {
        this.setVertexBuffer(slot, buffer == null ? null : buffer.slice());
    }

    @Override
    public void setVertexBuffer(int binding, GpuBufferSlice slice)
    {
        if (binding < 0 || binding >= this.bindungen.length) throw new IllegalArgumentException("Eckenpuffer-Platz ausserhalb: " + binding);

        this.bindungen[binding] = slice;
    }

    @Override
    public void setIndexBuffer(GpuBuffer buffer, VertexFormat.IndexType type)
    {
        this.indexPuffer = buffer;
        this.indexTyp = type;
    }

    @Override
    public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount)
    {
        this.zeichnen(baseVertex, firstIndex, indexCount, this.indexTyp, instanceCount, 0);
    }

    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance)
    {
        this.zeichnen(baseVertex, firstIndex, indexCount, this.indexTyp, instanceCount, firstInstance);
    }

    @Override
    public void draw(int firstVertex, int vertexCount)
    {
        this.zeichnen(firstVertex, 0, vertexCount, null, 1, 0);
    }

    @Override
    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance)
    {
        this.zeichnen(firstVertex, 0, vertexCount, null, instanceCount, firstInstance);
    }

    @Override
    public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, GpuBuffer defaultIndexBuffer, VertexFormat.IndexType defaultIndexType,
        Collection<String> dynamicUniforms, T context)
    {
        for (Draw<T> draw : draws)
        {
            if (draw.uniformUploaderConsumer() != null)
            {
                draw.uniformUploaderConsumer().accept(context, this::setUniform);
            }

            this.setVertexBuffer(draw.slot(), draw.vertexBuffer());
            this.indexPuffer = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
            this.indexTyp = draw.indexType() != null ? draw.indexType() : defaultIndexType;
            this.zeichnen(0, draw.firstIndex(), draw.indexCount(), this.indexTyp, 1, 0);
        }
    }

    @Override
    public GpuBufferSlice[] bbsBindungen()
    {
        return this.bindungen;
    }

    @Override
    public int bbsErsteInstanz()
    {
        return this.ersteInstanz;
    }

    private void zeichnen(int basis, int ersterIndex, int anzahl, VertexFormat.IndexType typ, int instanzen, int ersteInstanz)
    {
        if (this.geschlossen) throw new IllegalStateException("Durchgang " + this.name.get() + " ist geschlossen");
        if (this.pipeline == null) throw new IllegalStateException("Durchgang " + this.name.get() + " ohne Pipeline");

        GlProgramm programm = GlDevice.instanz().programm(this.pipeline);

        this.hoechsteEinheit = Math.max(this.hoechsteEinheit, PipelineZustand.anwenden(this.pipeline, programm, this.schere, this.texturen, this.sampler, this.uniforms));
        this.ersteInstanz = ersteInstanz;
        GlVertexBindungen.binden(this.pipeline, this);
        this.ersteInstanz = 0;

        int modus = this.pipeline.getVertexFormatMode().asGLMode;

        if (typ != null)
        {
            if (this.indexPuffer == null) throw new IllegalStateException("Indexpuffer fehlt in " + this.name.get());

            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.indexPuffer.handle);

            long versatz = (long) ersterIndex * typ.bytes;

            if (instanzen > 1 || basis != 0)
            {
                GL32.glDrawElementsInstancedBaseVertex(modus, anzahl, typ.asGLType, versatz, Math.max(1, instanzen), basis);
            }
            else
            {
                GL11.glDrawElements(modus, anzahl, typ.asGLType, versatz);
            }
        }
        else if (instanzen > 1)
        {
            GL31.glDrawArraysInstanced(modus, basis, anzahl, instanzen);
        }
        else
        {
            GL11.glDrawArrays(modus, basis, anzahl);
        }
    }

    @Override
    public void close()
    {
        if (this.geschlossen)
        {
            return;
        }

        this.geschlossen = true;

        for (int einheit = 0; einheit <= this.hoechsteEinheit; einheit++)
        {
            GL33.glBindSampler(einheit, 0);
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._disableScissorTest();
        GlStateManager._disablePolygonOffset();
        GlStateManager._disableColorLogicOp();
        GlStateManager._polygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GlStateManager._glUseProgram(0);
        GlStateManager._glBindVertexArray(0);
        /* 1.21.4s BufferUploader merkt sich den zuletzt gebundenen Vanilla-Puffer samt VAO; nach
         * unseren VAOs zeichnete er sonst ohne aktives VAO (GL_INVALID_OPERATION, gemessen). */
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, 0);
        this.encoder.durchgangEnde();
    }
}
