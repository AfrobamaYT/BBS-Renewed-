package com.afrobama.bbstools.client.bbs.fastpath;

import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Das eigene GL-Programm des Instanz-Zeichners: uebersetzen, binden, Nummer halten.
 *
 * <h2>Warum das eine eigene Klasse ist</h2>
 *
 * <p>{@code Tier2hInstancedDraw} war ueber 1700 Zeilen lang. Das Uebersetzen eines
 * Shaderprogramms hat mit dem Zeichnen von Instanzen nichts zu tun ausser der Nummer, die
 * dabei herauskommt -- es liest zwei Dateien, ruft den Uebersetzer und meldet Fehler.
 *
 * <h2>⚠️ Ein eigenes GL-Programm, KEINE Vanilla-Pipeline</h2>
 *
 * <p>Ueberall sonst in BBS traegt seit 26.2 eine RenderPipeline den Shader. Hier nicht:
 * dieser Zeichner spricht direkt mit OpenGL, weil er Instanzen zeichnet, wofuer Vanillas
 * Weg keine Entsprechung hat. Das heisst auch: er laeuft nur unter OpenGL, nie unter
 * Vulkan. Wer ihn dorthin bringen will, baut ihn neu, er portiert ihn nicht.
 */
final class Tier2hProgramm
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/tier2h");

    /** 0 = noch nicht uebersetzt oder Uebersetzung fehlgeschlagen. */
    private static int nummer = 0;

    /* Die Fundstellen der Uniforms. Sie gehoeren zum Programm, nicht zum Zeichner: sie
     * sind nur zwischen glLinkProgram und glDeleteProgram gueltig und werden beim
     * Uebersetzen gefuellt. -1 heisst: der Shader kennt das Uniform nicht. */
    static int uProjMat = -1;
    static int uModelViewMat = -1;
    static int uSampler0 = -1;
    static int uBoneTex = -1;
    static int uUseLightmap = -1;
    static int uUseVAT = -1;

    /** Die Programmnummer; uebersetzt beim ersten Zugriff. 0 heisst: geht nicht, nicht zeichnen. */
    static int nummer()
    {
        if (nummer == 0)
        {
            try
            {
                uebersetzen();
            }
            catch (Throwable t)
            {
                LOG.error("[tier2h] Programm liess sich nicht uebersetzen", t);
            }
        }

        return nummer;
    }
    /* ================================================================
     *                      Shader compile
     * ================================================================ */

    private static void uebersetzen()
    {
        String vshSrc = loadResource("/assets/bbstools/pinwheel/shaders/program/tier2h_crowd.vsh");
        String fshSrc = loadResource("/assets/bbstools/pinwheel/shaders/program/tier2h_crowd.fsh");
        if (vshSrc == null || fshSrc == null)
        {
            LOG.error("[tier2h] shader sources missing");
            return;
        }
        int vsh = compileShader(GL20C.GL_VERTEX_SHADER, vshSrc);
        int fsh = compileShader(GL20C.GL_FRAGMENT_SHADER, fshSrc);
        if (vsh == 0 || fsh == 0)
        {
            if (vsh != 0) GL20C.glDeleteShader(vsh);
            if (fsh != 0) GL20C.glDeleteShader(fsh);
            return;
        }
        int prog = GL20C.glCreateProgram();
        GL20C.glAttachShader(prog, vsh);
        GL20C.glAttachShader(prog, fsh);
        GL20C.glLinkProgram(prog);
        GL20C.glDetachShader(prog, vsh);
        GL20C.glDetachShader(prog, fsh);
        GL20C.glDeleteShader(vsh);
        GL20C.glDeleteShader(fsh);
        if (GL20C.glGetProgrami(prog, GL20C.GL_LINK_STATUS) == 0)
        {
            String log = GL20C.glGetProgramInfoLog(prog);
            LOG.error("[tier2h] shader link failed: {}", log);
            GL20C.glDeleteProgram(prog);
            return;
        }
        uProjMat          = GL20C.glGetUniformLocation(prog, "ProjMat");
        uModelViewMat     = GL20C.glGetUniformLocation(prog, "ModelViewMat");
        uSampler0         = GL20C.glGetUniformLocation(prog, "Sampler0");
        uBoneTex          = GL20C.glGetUniformLocation(prog, "BoneTex");
        uUseLightmap      = GL20C.glGetUniformLocation(prog, "UseLightmap");
        uUseVAT           = GL20C.glGetUniformLocation(prog, "UseVAT");
        nummer = prog;
        LOG.info("[tier2h] program compiled prog={} uniforms(Proj={}, MV={}, S0={}, BoneTex={}, UseLight={}, UseVAT={})",
            prog, uProjMat, uModelViewMat, uSampler0, uBoneTex, uUseLightmap, uUseVAT);
    }

    private static int compileShader(int type, String src)
    {
        int sh = GL20C.glCreateShader(type);
        GL20C.glShaderSource(sh, src);
        GL20C.glCompileShader(sh);
        if (GL20C.glGetShaderi(sh, GL20C.GL_COMPILE_STATUS) == 0)
        {
            String log = GL20C.glGetShaderInfoLog(sh);
            LOG.error("[tier2h] shader compile failed (type={}): {}", type, log);
            GL20C.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    private static String loadResource(String path)
    {
        try (InputStream in = Tier2hInstancedDraw.class.getResourceAsStream(path))
        {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
