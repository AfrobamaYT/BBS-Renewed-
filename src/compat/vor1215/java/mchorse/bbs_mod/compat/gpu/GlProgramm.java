package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein GL-Programm der Schicht vor 1.21.5, gebaut aus den Shadern einer RenderPipeline in der
 * Form von 1.21.11 (#moj_import, Uniform-Bloecke). Includes im Namensraum minecraft sucht es
 * zuerst unter assets/bbs/shaders/vor1215/include/ (die Bloecke von 1.21.6+, die 1.21.4 nicht
 * hat), sonst in den Ressourcen; 1.21.4s eigene Includes bleiben dabei unberuehrt.
 *
 * <p>Bindungen wie Mojang ab 1.21.5: Eckenattribute in der Reihenfolge des Formats,
 * Uniform-Bloecke in der Reihenfolge der Pipeline-Uniforms, danach Sampler und Texel-Puffer
 * auf aufeinanderfolgenden Textureinheiten.</p>
 */
public final class GlProgramm
{
    public final int id;
    public final RenderPipeline pipeline;
    /** Uniform-Block-Name -> Bindungspunkt. */
    public final Map<String, Integer> bloecke = new HashMap<>();
    /** Sampler- oder Texel-Puffer-Name -> Textureinheit. */
    public final Map<String, Integer> einheiten = new HashMap<>();
    /** Einzel-Uniforms der 1.21.4-Kern-Shader (leer fuer BBS-Shader mit Bloecken). */
    public EinzelUniforms einzel;

    private GlProgramm(int id, RenderPipeline pipeline)
    {
        this.id = id;
        this.pipeline = pipeline;
    }

    public static GlProgramm bauen(RenderPipeline pipeline)
    {
        String vertex = ShaderQuellen.laden(pipeline.getVertexShader(), ".vsh", pipeline);
        String fragment = ShaderQuellen.laden(pipeline.getFragmentShader(), ".fsh", pipeline);
        int vs = kompilieren(GL20.GL_VERTEX_SHADER, vertex, pipeline.getVertexShader());
        int fs = kompilieren(GL20.GL_FRAGMENT_SHADER, fragment, pipeline.getFragmentShader());
        int programm = GL20.glCreateProgram();

        GL20.glAttachShader(programm, vs);
        GL20.glAttachShader(programm, fs);

        List<String> attribute = pipeline.getVertexFormat().getElementAttributeNames();

        for (int i = 0; i < attribute.size(); i++)
        {
            GL20.glBindAttribLocation(programm, i, attribute.get(i));
        }

        GL20.glLinkProgram(programm);
        GL20.glDetachShader(programm, vs);
        GL20.glDetachShader(programm, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        if (GL20.glGetProgrami(programm, GL20.GL_LINK_STATUS) == 0)
        {
            String log = GL20.glGetProgramInfoLog(programm);

            GL20.glDeleteProgram(programm);

            throw new IllegalStateException("Pipeline " + pipeline.getLocation() + " laesst sich nicht linken: " + log);
        }

        GlProgramm ergebnis = new GlProgramm(programm, pipeline);
        int block = 0;
        int einheit = 0;

        GL20.glUseProgram(programm);

        for (RenderPipeline.UniformDescription uniform : pipeline.getUniforms())
        {
            if (uniform.type() == UniformType.UNIFORM_BUFFER)
            {
                int index = GL31.glGetUniformBlockIndex(programm, uniform.name());

                if (index != GL31.GL_INVALID_INDEX)
                {
                    GL31.glUniformBlockBinding(programm, index, block);
                    ergebnis.bloecke.put(uniform.name(), block++);
                }
            }
        }

        for (String sampler : pipeline.getSamplers())
        {
            einheit = ergebnis.einheitZuweisen(sampler, einheit);
        }

        for (RenderPipeline.UniformDescription uniform : pipeline.getUniforms())
        {
            if (uniform.type() == UniformType.TEXEL_BUFFER)
            {
                einheit = ergebnis.einheitZuweisen(uniform.name(), einheit);
            }
        }

        ergebnis.einzel = EinzelUniforms.finden(programm);
        GL20.glUseProgram(0);

        return ergebnis;
    }

    private int einheitZuweisen(String name, int einheit)
    {
        int ort = GL20.glGetUniformLocation(this.id, name);

        if (ort == -1)
        {
            return einheit;
        }

        GL20.glUniform1i(ort, einheit);
        this.einheiten.put(name, einheit);

        return einheit + 1;
    }

    public void schliessen()
    {
        GL20.glDeleteProgram(this.id);
    }

    private static int kompilieren(int art, String quelle, ResourceLocation ort)
    {
        int shader = GL20.glCreateShader(art);

        GL20.glShaderSource(shader, quelle);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        {
            String log = GL20.glGetShaderInfoLog(shader);

            GL20.glDeleteShader(shader);

            throw new IllegalStateException("Shader " + ort + " laesst sich nicht uebersetzen: " + log);
        }

        return shader;
    }

}
