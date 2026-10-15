package com.mcstudio.nano;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL33C;
import java.util.IdentityHashMap;
import java.util.Map;

/** Owns allocated GPU objects, without retaining surface widgets or UI trees. */
public final class NanoRessourcen
{
    private static final class Allokation
    {
        final Texture textur;
        int fbo, rbo;
        Allokation(Texture textur) { this.textur = textur; }
        void schliessen()
        {
            try
            {
                if (this.fbo != 0) { GL33C.glDeleteFramebuffers(this.fbo); this.fbo = 0; }
                if (this.rbo != 0) { GL33C.glDeleteRenderbuffers(this.rbo); this.rbo = 0; }
            }
            finally { this.textur.delete(); }
        }
    }
    private static final Map<Texture, Allokation> ALLOZIERT = new IdentityHashMap<>();
    private static boolean geschlossen;
    private NanoRessourcen() {}

    public static Texture textur()
    {
        RenderSystem.assertOnRenderThread();
        if (geschlossen) throw new IllegalStateException("Nano surface GPU owner is closed");
        Texture texture = new Texture();
        ALLOZIERT.put(texture, new Allokation(texture));
        return texture;
    }
    public static int fbo(Texture texture) { return ALLOZIERT.get(texture).fbo = GL33C.glGenFramebuffers(); }
    public static int rbo(Texture texture) { return ALLOZIERT.get(texture).rbo = GL33C.glGenRenderbuffers(); }

    public static void schliessen()
    {
        RenderSystem.assertOnRenderThread();
        geschlossen = true;
        RuntimeException failure = null;
        for (Allokation allocation : ALLOZIERT.values())
        {
            try { allocation.schliessen(); }
            catch (RuntimeException error)
            {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        ALLOZIERT.clear();
        if (failure != null) throw failure;
    }
}
