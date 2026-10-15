package com.afrobama.bbstools.client.bbs.fastpath;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

/**
 * Der rohe GL-Zustand des Instanz-Zeichners: merken, umschalten, zuruecklegen.
 *
 * <h2>Warum das eine eigene Klasse ist</h2>
 *
 * <p>{@code Tier2hInstancedDraw} ist ueber 1700 Zeilen lang und zeichnet bewusst an
 * Minecraft vorbei -- eigenes Shaderprogramm, eigene Puffer, eigene Texturbindungen. Das
 * Umschalten des GL-Zustands ist davon der kleinste und der gefaehrlichste Teil: er wird
 * an zwei Stellen fast, aber nicht ganz gleich gemacht, und ein vergessenes Zuruecklegen
 * faellt nicht hier auf, sondern irgendwo im naechsten Vanilla-Zug.
 *
 * <h2>26.2: warum hier rohes GL steht und anderswo Pipelines</h2>
 *
 * <p>Ueberall sonst in BBS traegt die Pipeline den Zustand, und das ist richtig. Hier
 * nicht: dieser Zeichner benutzt gar keine Vanilla-Pipeline, sondern ein eigenes
 * GL-Programm. Ein Zustand, den keine Pipeline setzt, muss von Hand gesetzt werden.
 *
 * <p>⚠️ {@link GlStateManager#_activeTexture(int)} statt {@code glActiveTexture}: das
 * Backend fuehrt Buch darueber, welche Texturmengeneinheit aktiv ist. Rohes Umschalten
 * liefe daran vorbei, und der naechste Vanilla-Zug baende seine Textur auf die falsche
 * Einheit -- ein Fehler, der nichts meldet.
 *
 * <p>⚠️ Diese Klasse laeuft NUR unter OpenGL. Das opengl-Paket ist die Backend-Schicht;
 * unter Vulkan gibt es sie nicht. Das galt fuer diesen Zeichner schon immer, seit 26.2
 * steht es aber auch im Import.
 */
final class Tier2hGlZustand
{
    /** GL_ACTIVE_TEXTURE -- die Zahl steht in keiner LWJGL-Konstante, die hier importiert ist. */
    private static final int AKTIVE_EINHEIT = 0x84E0;

    private final int einheit;
    private final boolean tiefeWar;
    private final boolean cullWar;
    private final int tiefenFunktion;
    private final boolean tiefenMaske;
    private final int programm;
    private final int vao;
    private final int arrayPuffer;
    private final int[] texturen = new int[2];
    private final boolean[] mischZiele;
    private final int[] mischFunktion = new int[6];

    /** Merkt sich den Zustand, den der Aufrufer vorgefunden hat. */
    Tier2hGlZustand()
    {
        this.einheit = GL11C.glGetInteger(AKTIVE_EINHEIT);
        this.tiefeWar = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        this.cullWar = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        this.tiefenFunktion = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        this.tiefenMaske = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        this.programm = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        this.vao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        this.arrayPuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        this.mischZiele = new boolean[GL11C.glGetInteger(GL20C.GL_MAX_DRAW_BUFFERS)];
        for (int i = 0; i < this.mischZiele.length; i++) this.mischZiele[i] = GL30C.glIsEnabledi(GL11C.GL_BLEND, i);
        int[] names = {GL14C.GL_BLEND_SRC_RGB, GL14C.GL_BLEND_DST_RGB, GL14C.GL_BLEND_SRC_ALPHA,
            GL14C.GL_BLEND_DST_ALPHA, GL20C.GL_BLEND_EQUATION_RGB, GL20C.GL_BLEND_EQUATION_ALPHA};
        for (int i = 0; i < names.length; i++) this.mischFunktion[i] = GL11C.glGetInteger(names[i]);
        for (int i = 0; i < this.texturen.length; i++)
        {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
            this.texturen[i] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(this.einheit);
    }

    static void tiefeAn()
    {
        GL11C.glEnable(GL11C.GL_DEPTH_TEST);
    }

    /** Standardmischung: Quell-Alpha ueber Gegen-Alpha, wie Vanillas altes defaultBlendFunc. */
    static void mischenAn()
    {
        GL11C.glEnable(GL11C.GL_BLEND);
        GL11C.glBlendFunc(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void mischenAus()
    {
        GL11C.glDisable(GL11C.GL_BLEND);
    }

    static void einheit(int nummer)
    {
        GlStateManager._activeTexture(nummer);
    }

    /**
     * Legt zurueck, was gemerkt wurde.
     *
     * <p>⚠️ Gehoert in einen finally-Zweig. Bleibt der Tiefentest aus oder die falsche
     * Textureinheit aktiv, zeichnet nicht dieser Zeichner falsch, sondern der naechste --
     * und dort sucht niemand.
     */
    void zurueck()
    {
        if (!this.tiefeWar)
        {
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        }
        else GL11C.glEnable(GL11C.GL_DEPTH_TEST);
        GL11C.glDepthFunc(this.tiefenFunktion);
        GL11C.glDepthMask(this.tiefenMaske);

        if (this.cullWar)
        {
            GL11C.glEnable(GL11C.GL_CULL_FACE);
        }
        else
        {
            GL11C.glDisable(GL11C.GL_CULL_FACE);
        }

        GL20C.glUseProgram(this.programm);
        GL30C.glBindVertexArray(this.vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.arrayPuffer);
        GL14C.glBlendFuncSeparate(this.mischFunktion[0], this.mischFunktion[1], this.mischFunktion[2], this.mischFunktion[3]);
        GL20C.glBlendEquationSeparate(this.mischFunktion[4], this.mischFunktion[5]);
        for (int i = 0; i < this.mischZiele.length; i++)
        {
            if (this.mischZiele[i]) GL30C.glEnablei(GL11C.GL_BLEND, i);
            else GL30C.glDisablei(GL11C.GL_BLEND, i);
        }
        for (int i = 0; i < this.texturen.length; i++)
        {
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(this.texturen[i]);
        }
        GlStateManager._activeTexture(this.einheit);
    }
}
