package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.List;

/**
 * Texel-Puffer (samplerBuffer) ueber Pufferausschnitte, fuer die Schicht vor 1.21.5. Eine
 * GL-Puffertextur je Ausschnitt; Eintraege geschlossener Puffer verschwinden beim naechsten
 * Zugriff, damit die Puffer eines Bildes keine Texturen hinterlassen.
 */
public final class TexelPuffer
{
    private static final List<Eintrag> EINTRAEGE = new ArrayList<>();

    private TexelPuffer()
    {}

    public static int textur(RenderPipeline pipeline, String name, GpuBufferSlice slice)
    {
        EINTRAEGE.removeIf(e ->
        {
            boolean weg = e.puffer instanceof GlBuffer gl && gl.bbsGeschlossen();

            if (weg) GL11.glDeleteTextures(e.textur);

            return weg;
        });

        int intern = format(pipeline, name).glInternal;

        for (Eintrag e : EINTRAEGE)
        {
            if (e.puffer == slice.buffer() && e.versatz == slice.offset() && e.laenge == slice.length() && e.intern == intern)
            {
                return e.textur;
            }
        }

        int textur = GL11.glGenTextures();

        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textur);

        if (slice.offset() == 0 && slice.length() == slice.buffer().size)
        {
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, intern, slice.buffer().handle);
        }
        else
        {
            GL43.glTexBufferRange(GL31.GL_TEXTURE_BUFFER, intern, slice.buffer().handle, slice.offset(), slice.length());
        }

        EINTRAEGE.add(new Eintrag(slice.buffer(), slice.offset(), slice.length(), intern, textur));

        return textur;
    }

    private static GpuFormat format(RenderPipeline pipeline, String name)
    {
        for (RenderPipeline.UniformDescription uniform : pipeline.getUniforms())
        {
            if (uniform.name().equals(name) && uniform.textureFormat() != null)
            {
                return GpuFormat.von(uniform.textureFormat());
            }
        }

        throw new IllegalStateException("Texel-Puffer " + name + " ohne Format in " + pipeline.getLocation());
    }

    private record Eintrag(GpuBuffer puffer, long versatz, long laenge, int intern, int textur)
    {}
}
