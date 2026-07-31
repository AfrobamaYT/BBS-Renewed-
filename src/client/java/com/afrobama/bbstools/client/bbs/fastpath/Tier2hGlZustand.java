package com.afrobama.bbstools.client.bbs.fastpath;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11C;

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

    /** Merkt sich den Zustand, den der Aufrufer vorgefunden hat. */
    Tier2hGlZustand()
    {
        this.einheit = GL11C.glGetInteger(AKTIVE_EINHEIT);
        this.tiefeWar = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        this.cullWar = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
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

        if (this.cullWar)
        {
            GL11C.glEnable(GL11C.GL_CULL_FACE);
        }
        else
        {
            GL11C.glDisable(GL11C.GL_CULL_FACE);
        }

        GlStateManager._activeTexture(this.einheit);
    }
}
