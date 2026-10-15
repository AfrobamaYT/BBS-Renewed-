package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.util.Map;

/**
 * Pipeline-Zustand eines Zeichenaufrufs vor 1.21.5, ueber 1.21.4s GlStateManager gesetzt
 * (dessen Zwischenspeicher Vanilla benutzt).
 */
public final class PipelineZustand
{
    private PipelineZustand()
    {}

    /** Setzt Programm, Pipeline-Zustand, Bloecke und Texturen; liefert die hoechste belegte Einheit. */
    public static int anwenden(RenderPipeline p, GlProgramm programm, ScissorState schere, Map<String, GpuTextureView> texturen,
        Map<String, GpuSampler> samplerListe, Map<String, GpuBufferSlice> uniforms)
    {
        int hoechste = -1;

        GlStateManager._glUseProgram(programm.id);
        programm.einzel.fuellen(uniforms);

        if (p.getBlendFunction().isPresent())
        {
            BlendFunction f = p.getBlendFunction().get();

            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(f.sourceColor().gl, f.destColor().gl, f.sourceAlpha().gl, f.destAlpha().gl);
        }
        else
        {
            GlStateManager._disableBlend();
        }

        switch (p.getDepthTestFunction())
        {
            case NO_DEPTH_TEST -> GlStateManager._disableDepthTest();
            case EQUAL_DEPTH_TEST -> { GlStateManager._enableDepthTest(); GlStateManager._depthFunc(GL11.GL_EQUAL); }
            case LEQUAL_DEPTH_TEST -> { GlStateManager._enableDepthTest(); GlStateManager._depthFunc(GL11.GL_LEQUAL); }
            case LESS_DEPTH_TEST -> { GlStateManager._enableDepthTest(); GlStateManager._depthFunc(GL11.GL_LESS); }
            case GREATER_DEPTH_TEST -> { GlStateManager._enableDepthTest(); GlStateManager._depthFunc(GL11.GL_GREATER); }
        }

        GlStateManager._depthMask(p.isWriteDepth());
        GlStateManager._colorMask(p.isWriteColor(), p.isWriteColor(), p.isWriteColor(), p.isWriteAlpha());

        if (p.isCull()) GlStateManager._enableCull();
        else GlStateManager._disableCull();

        GlStateManager._polygonMode(GL11.GL_FRONT_AND_BACK, p.getPolygonMode() == PolygonMode.WIREFRAME ? GL11.GL_LINE : GL11.GL_FILL);

        if (p.getDepthBiasConstant() != 0F || p.getDepthBiasScaleFactor() != 0F)
        {
            GlStateManager._enablePolygonOffset();
            GlStateManager._polygonOffset(p.getDepthBiasScaleFactor(), p.getDepthBiasConstant());
        }
        else
        {
            GlStateManager._disablePolygonOffset();
        }

        if (p.getColorLogic() == LogicOp.OR_REVERSE)
        {
            GlStateManager._enableColorLogicOp();
            GlStateManager._logicOp(GL11.GL_OR_REVERSE);
        }
        else
        {
            GlStateManager._disableColorLogicOp();
        }

        if (schere.enabled())
        {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(schere.x(), schere.y(), schere.width(), schere.height());
        }
        else
        {
            GlStateManager._disableScissorTest();
        }

        for (Map.Entry<String, Integer> block : programm.bloecke.entrySet())
        {
            GpuBufferSlice slice = uniforms.get(block.getKey());

            if (slice == null) throw new IllegalStateException("Uniform-Block " + block.getKey() + " fehlt fuer " + p.getLocation());

            GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, block.getValue(), slice.buffer().handle, slice.offset(), slice.length());
        }

        for (Map.Entry<String, Integer> eintrag : programm.einheiten.entrySet())
        {
            int einheit = eintrag.getValue();
            GpuTextureView view = texturen.get(eintrag.getKey());

            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + einheit);
            hoechste = Math.max(hoechste, einheit);

            if (view != null)
            {
                GlTexture textur = (GlTexture) view.texture();
                int ziel = (textur.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0 ? GL13.GL_TEXTURE_CUBE_MAP : GL11.GL_TEXTURE_2D;

                if (ziel == GL11.GL_TEXTURE_2D) GlStateManager._bindTexture(textur.id);
                else GL11.glBindTexture(ziel, textur.id);

                GL11.glTexParameteri(ziel, GL12.GL_TEXTURE_BASE_LEVEL, view.baseMipLevel());
                GL11.glTexParameteri(ziel, GL12.GL_TEXTURE_MAX_LEVEL, view.baseMipLevel() + view.mipLevels() - 1);

                GpuSampler s = samplerListe.get(eintrag.getKey());

                GL33.glBindSampler(einheit, s == null ? 0 : ((GlSampler) s).id);
            }
            else
            {
                GpuBufferSlice texel = uniforms.get(eintrag.getKey());

                if (texel == null) throw new IllegalStateException("Textur bzw. Texel-Puffer " + eintrag.getKey() + " fehlt fuer " + p.getLocation());

                GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, TexelPuffer.textur(p, eintrag.getKey(), texel));
                GL33.glBindSampler(einheit, 0);
            }
        }
    
        return hoechste;
    }
}
