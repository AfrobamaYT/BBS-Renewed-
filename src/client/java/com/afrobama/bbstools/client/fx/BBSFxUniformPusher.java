// Coded by Afrobama/Claude — push BBSFxState uniforms into our
// post-FX shaders every frame. Hooks LevelRenderEvents.START_MAIN
// so the values are visible to the shaders before they execute.
// Reflective Veil API access — wrapped in try-catch so a Veil
// API change doesn't kill the renderer.
package com.afrobama.bbstools.client.fx;

import com.afrobama.bbstools.diag.BBSDiag;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BBSFxUniformPusher
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fx/uniform-pusher");

    private static final Identifier SHADOWS_SHADER     = Identifier.fromNamespaceAndPath("bbstools", "fx/shadows");
    private static final Identifier FINAL_SHADER       = Identifier.fromNamespaceAndPath("bbstools", "fx/final");
    private static final Identifier NUKE_SHADER        = Identifier.fromNamespaceAndPath("bbstools", "fx/nuke");
    private static final Identifier GLITCH_SHADER     = Identifier.fromNamespaceAndPath("bbstools", "fx/glitch");
    private static final Identifier ANTIMATTER_SHADER = Identifier.fromNamespaceAndPath("bbstools", "fx/antimatter");
    private static final Identifier CRYSTAL_SHADER    = Identifier.fromNamespaceAndPath("bbstools", "fx/crystal");
    private static final Identifier BLOOM_BLUR_H       = Identifier.fromNamespaceAndPath("bbstools", "fx/bloom/blur_h");
    private static final Identifier BLOOM_BLUR_V       = Identifier.fromNamespaceAndPath("bbstools", "fx/bloom/blur_v");
    private static final Identifier BLOOM_APPLY        = Identifier.fromNamespaceAndPath("bbstools", "fx/bloom/bloom_apply");

    private static boolean registered;

    /** Anchor for the GameTime uniform (seconds since pusher load).
     *  Veil 4.0.1 does NOT auto-bind a GameTime uniform — without
     *  this push the FBM noise in nuke.fsh stays frozen at t=0,
     *  i.e. the mushroom cloud's swirling smoke appears static. */
    private static final long START_NANOS = System.nanoTime();

    private BBSFxUniformPusher() {}

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        /* Push uniforms each frame before world-rendering completes —
         *  post-FX runs at LevelRenderEvents.END_MAIN so AFTER_SETUP is
         *  early enough to have values in place. */
        // PHASE NICHT BELEGT GLEICHWERTIG: alt war LAST/END/AFTER_SETUP. 26.2 hat dafuer kein benanntes Gegenstueck -- ob diese Phase an derselben Stelle liegt, ist am laufenden Bild zu pruefen (Export gegen Fenster).
        LevelRenderEvents.START_MAIN.register(ctx -> push());
        BBSDiag.feature("fx-uniforms").event("register").emit();
    }

    private static void push()
    {
        try
        {
            ShaderProgram shadows = VeilRenderSystem.renderer()
                .getShaderManager().getShader(SHADOWS_SHADER);
            if (shadows != null)
            {
                shadows.bind();
                trySetFloat(shadows, "supernovaTimer",        BBSFxState.supernovaTimer);
                trySetFloat(shadows, "flashTimer",            BBSFxState.flashTimer);
                trySetVec3 (shadows, "supernovaPos",          BBSFxState.supernovaPos);
                trySetFloat(shadows, "meteorIntensity",       BBSFxState.meteorIntensity);
                trySetVec3 (shadows, "meteorPos",             BBSFxState.meteorPos);
                trySetFloat(shadows, "revealLightIntensity",  BBSFxState.revealLightIntensity);
                trySetVec3 (shadows, "revealLightPos",        BBSFxState.revealLightPos);
            }
            ShaderProgram fin = VeilRenderSystem.renderer()
                .getShaderManager().getShader(FINAL_SHADER);
            if (fin != null)
            {
                fin.bind();
                trySetFloat(fin, "supernovaTimer", BBSFxState.supernovaTimer);
                trySetFloat(fin, "nukeFlash",      BBSFxState.nukeFlash);
                trySetFloat(fin, "vibranceAmount", BBSFxState.vibranceAmount);
                trySetFloat(fin, "tonemapExposure",BBSFxState.tonemapExposure);
                trySetVec3 (fin, "supernovaPos",   BBSFxState.supernovaPos);
            }

            /* Hoist GameTime + screen size so both the nuke and glitch
             *  shader blocks can reference them without re-computing. */
            float gameTimeSec = (System.nanoTime() - START_NANOS) / 1_000_000_000F;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            float screenW = (mc != null && mc.getWindow() != null) ? mc.getWindow().getWidth()  : 1920F;
            float screenH = (mc != null && mc.getWindow() != null) ? mc.getWindow().getHeight() : 1080F;

            /* Nuke shader uniforms (mushroom cloud raymarch). */
            ShaderProgram nuke = VeilRenderSystem.renderer()
                .getShaderManager().getShader(NUKE_SHADER);
            if (nuke != null)
            {
                nuke.bind();
                trySetVec3 (nuke, "NUKE_POS",       BBSFxState.nukePos);
                trySetFloat(nuke, "smokeRiseTimer", BBSFxState.smokeRiseTimer);
                trySetFloat(nuke, "flashTimer",     BBSFxState.flashTimer);
                trySetFloat(nuke, "GameTime",       gameTimeSec);
            }

            /* REALITY GLITCH shader uniforms. */
            ShaderProgram glitch = VeilRenderSystem.renderer()
                .getShaderManager().getShader(GLITCH_SHADER);
            if (glitch != null)
            {
                glitch.bind();
                trySetVec3 (glitch, "glitchPos",       BBSFxState.glitchPos);
                trySetFloat(glitch, "glitchRadius",    BBSFxState.glitchRadius);
                trySetFloat(glitch, "glitchIntensity", BBSFxState.glitchIntensity);
                trySetFloat(glitch, "GameTime",        gameTimeSec);
                trySetVec2 (glitch, "ScreenSize",      screenW, screenH);
            }

            /* ANTIMATTER IMPLOSION shader uniforms. */
            ShaderProgram antimatter = VeilRenderSystem.renderer()
                .getShaderManager().getShader(ANTIMATTER_SHADER);
            if (antimatter != null)
            {
                antimatter.bind();
                trySetVec3 (antimatter, "antimatterPos",       BBSFxState.antimatterPos);
                trySetFloat(antimatter, "antimatterIntensity", BBSFxState.antimatterIntensity);
                trySetVec2 (antimatter, "ScreenSize",          screenW, screenH);
            }

            /* CRYSTALLIZATION WAVE shader uniforms. */
            ShaderProgram crystal = VeilRenderSystem.renderer()
                .getShaderManager().getShader(CRYSTAL_SHADER);
            if (crystal != null)
            {
                crystal.bind();
                trySetVec3 (crystal, "crystalPos",       BBSFxState.crystalPos);
                trySetFloat(crystal, "crystalRadius",    BBSFxState.crystalRadius);
                trySetFloat(crystal, "crystalIntensity", BBSFxState.crystalIntensity);
                trySetVec2 (crystal, "ScreenSize",       screenW, screenH);
                trySetFloat(crystal, "GameTime",         gameTimeSec);
            }

            /* Bloom chain: xLimit + blurStrength + ScreenSize. Without
             *  these the blur_h/blur_v shaders early-out (texCoord.x <=
             *  xLimit == 0) -> bloom_apply reads a cleared framebuffer
             *  -> no bloom emits at all. Same defaults as MAS pipeline.
             *  ScreenSize comes from the hoisted screenW/screenH above. */
            for (Identifier sid : new Identifier[] { BLOOM_BLUR_H, BLOOM_BLUR_V, BLOOM_APPLY })
            {
                ShaderProgram p = VeilRenderSystem.renderer().getShaderManager().getShader(sid);
                if (p == null) continue;
                p.bind();
                trySetVec2 (p, "ScreenSize",   screenW, screenH);
                trySetFloat(p, "xLimit",       1.0F);
                trySetFloat(p, "blurStrength", 1.0F);
            }
            ShaderProgram.unbind();

            BBSDiag.feature("fx-uniforms").throttled1Hz().event("push")
                .field("nova", BBSFxState.supernovaTimer)
                .field("nuke", BBSFxState.nukeFlash)
                .field("flash", BBSFxState.flashTimer)
                .emit();
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-uniforms").sticky("push-error")
                .field("err", t.toString()).asWarn().emit();
        }
    }

    private static void trySetFloat(ShaderProgram p, String name, float v)
    {
        try
        {
            p.getUniform(name).setFloat(v);
        }
        catch (Throwable t)
        {
            /* Sticky: each (shader, uniform) pair logs once per session.
             *  Missing-uniform is normal for inactive shaders but should
             *  still be surfaced so we notice typos / spec mismatches. */
            BBSDiag.feature("fx-uniforms").sticky("missing:" + name)
                .field("uniform", name).field("err", t.toString()).asWarn().emit();
        }
    }

    private static void trySetVec3(ShaderProgram p, String name, org.joml.Vector3f v)
    {
        try
        {
            p.getUniform(name).setVector(v.x, v.y, v.z);
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-uniforms").sticky("missing:" + name)
                .field("uniform", name).field("err", t.toString()).asWarn().emit();
        }
    }

    private static void trySetVec2(ShaderProgram p, String name, float x, float y)
    {
        try
        {
            p.getUniform(name).setVector(x, y);
        }
        catch (Throwable t)
        {
            BBSDiag.feature("fx-uniforms").sticky("missing:" + name)
                .field("uniform", name).field("err", t.toString()).asWarn().emit();
        }
    }

}
