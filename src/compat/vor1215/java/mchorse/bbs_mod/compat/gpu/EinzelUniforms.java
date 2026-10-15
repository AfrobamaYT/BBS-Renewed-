package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * Einzel-Uniforms der 1.21.4-Kern-Shader (ModelViewMat, ProjMat, FogStart ...), gefuellt aus den
 * Bloecken von 1.21.6+ (Anordnung wie dort). 1.21.4 kennt einen Nebel mit Form: der
 * Umgebungsnebel gilt als Kugel, sonst der Sichtweitennebel als Zylinder.
 */
public final class EinzelUniforms
{
    private static final float FERN = Float.MAX_VALUE / 2F;

    private final int modelView, farbe, versatz, texturMatrix, projektion;
    private final int nebelFarbe, nebelAnfang, nebelEnde, nebelForm;
    private final int licht0, licht1, bildschirm, spielzeit, glanz, linie;
    private final boolean leer;

    private EinzelUniforms(int programm)
    {
        this.modelView = GL20.glGetUniformLocation(programm, "ModelViewMat");
        this.farbe = GL20.glGetUniformLocation(programm, "ColorModulator");
        this.versatz = GL20.glGetUniformLocation(programm, "ModelOffset");
        this.texturMatrix = GL20.glGetUniformLocation(programm, "TextureMat");
        this.projektion = GL20.glGetUniformLocation(programm, "ProjMat");
        this.nebelFarbe = GL20.glGetUniformLocation(programm, "FogColor");
        this.nebelAnfang = GL20.glGetUniformLocation(programm, "FogStart");
        this.nebelEnde = GL20.glGetUniformLocation(programm, "FogEnd");
        this.nebelForm = GL20.glGetUniformLocation(programm, "FogShape");
        this.licht0 = GL20.glGetUniformLocation(programm, "Light0_Direction");
        this.licht1 = GL20.glGetUniformLocation(programm, "Light1_Direction");
        this.bildschirm = GL20.glGetUniformLocation(programm, "ScreenSize");
        this.spielzeit = GL20.glGetUniformLocation(programm, "GameTime");
        this.glanz = GL20.glGetUniformLocation(programm, "GlintAlpha");
        this.linie = GL20.glGetUniformLocation(programm, "LineWidth");
        this.leer = this.modelView < 0 && this.farbe < 0 && this.projektion < 0 && this.nebelAnfang < 0 && this.licht0 < 0
            && this.bildschirm < 0 && this.spielzeit < 0 && this.linie < 0 && this.versatz < 0 && this.texturMatrix < 0;
    }

    public static EinzelUniforms finden(int programm)
    {
        return new EinzelUniforms(programm);
    }

    /** Erwartet das Programm schon in Benutzung. */
    public void fuellen(Map<String, GpuBufferSlice> bloecke)
    {
        if (this.leer)
        {
            return;
        }

        ByteBuffer dt = block(bloecke, "DynamicTransforms");
        ByteBuffer pr = block(bloecke, "Projection");
        ByteBuffer ne = block(bloecke, "Fog");
        ByteBuffer li = block(bloecke, "Lighting");
        ByteBuffer gl = block(bloecke, "Globals");

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer m = stack.mallocFloat(16);

            if (dt != null)
            {
                matrix(this.modelView, dt, 0, m);
                vektor(this.farbe, dt, 64, 4);
                vektor(this.versatz, dt, 80, 3);
                matrix(this.texturMatrix, dt, 96, m);
            }

            if (pr != null) matrix(this.projektion, pr, 0, m);

            if (ne != null)
            {
                boolean kugel = ne.getFloat(16) < FERN;

                vektor(this.nebelFarbe, ne, 0, 4);
                if (this.nebelAnfang >= 0) GL20.glUniform1f(this.nebelAnfang, ne.getFloat(kugel ? 16 : 24));
                if (this.nebelEnde >= 0) GL20.glUniform1f(this.nebelEnde, ne.getFloat(kugel ? 20 : 28));
                if (this.nebelForm >= 0) GL20.glUniform1i(this.nebelForm, kugel ? 0 : 1);
            }

            if (li != null)
            {
                vektor(this.licht0, li, 0, 3);
                vektor(this.licht1, li, 16, 3);
            }

            if (gl != null)
            {
                vektor(this.bildschirm, gl, 32, 2);
                if (this.glanz >= 0) GL20.glUniform1f(this.glanz, gl.getFloat(40));
                if (this.spielzeit >= 0) GL20.glUniform1f(this.spielzeit, gl.getFloat(44));
            }

            if (this.linie >= 0) GL20.glUniform1f(this.linie, RenderSystem.getShaderLineWidth());
        }
    }

    private static ByteBuffer block(Map<String, GpuBufferSlice> bloecke, String name)
    {
        GpuBufferSlice slice = bloecke.get(name);

        return slice == null ? null : UniformDaten.kopie(slice);
    }

    private static void matrix(int ort, ByteBuffer quelle, int versatz, FloatBuffer m)
    {
        if (ort < 0) return;

        for (int i = 0; i < 16; i++) m.put(i, quelle.getFloat(versatz + i * 4));

        GL20.glUniformMatrix4fv(ort, false, m);
    }

    private static void vektor(int ort, ByteBuffer quelle, int versatz, int anzahl)
    {
        if (ort < 0) return;

        float x = quelle.getFloat(versatz), y = quelle.getFloat(versatz + 4);

        switch (anzahl)
        {
            case 2 -> GL20.glUniform2f(ort, x, y);
            case 3 -> GL20.glUniform3f(ort, x, y, quelle.getFloat(versatz + 8));
            default -> GL20.glUniform4f(ort, x, y, quelle.getFloat(versatz + 8), quelle.getFloat(versatz + 12));
        }
    }
}
