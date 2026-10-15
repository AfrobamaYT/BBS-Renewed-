// Coded by Afrobama/Claude — Veil-batch diagnostic logging r29.
// Coded by Afrobama/Claude — Veil RenderType holder for batched BBS
// replay rendering. Resolves to assets/bbstools/pinwheel/rendertypes/
// bbs_replay.json (Veil JSON-driven RenderType format).
//
// Phase 2: real Veil lookup. Iris auto-patches our shader via the
// RenderType's setupRenderState callback, so the patched
// gbuffers_entities fragment runs on top of our skinning vertex shader
// and we get full shaderpack lighting.
package com.afrobama.bbstools.client.bbs.veil;

import foundry.veil.api.client.render.rendertype.VeilRenderType;

import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VeilReplayRenderType
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/bbs-veil/rt");

    /** RenderType identifier — must match the JSON file name under
     *  assets/bbstools/pinwheel/rendertypes/. */
    public static final ResourceLocation ID = new ResourceLocation("bbstools", "bbs_replay");

    private static boolean warned;
    /* Veil-batch diagnostic logging r29 — one-time WARN when
     * VeilRenderType.get returns null (Veil-loaded but RT JSON unresolved). */
    private static boolean warnedNullRt;
    /* Veil-batch diagnostic logging r29 — one-time INFO on first success. */
    private static boolean infoFirstResolve;

    private VeilReplayRenderType() {}

    /** Resolve the JSON-defined Veil RenderType for a given texture
     *  link. The JSON's "${texture}" placeholder is substituted from the
     *  varargs we pass. Returns null + logs once if Veil isn't loaded or
     *  the RT can't be resolved; callers must null-check. */
    public static Object get(Object textureLink)
    {
        try
        {
            String texturePath = textureLink instanceof Link l
                ? l.toString()
                : String.valueOf(textureLink);
            Object raw = VeilRenderType.get(ID, texturePath);
            if (raw == null)
            {
                if (!warnedNullRt)
                {
                    warnedNullRt = true;
                    LOG.warn("[veil-batch] draw skipped: reason=VeilRenderType.get-returned-null, id={}, texturePath={}",
                        ID, texturePath);
                }
                return null;
            }
            if (!(raw instanceof RenderType rl))
            {
                if (!warnedNullRt)
                {
                    warnedNullRt = true;
                    LOG.warn("[veil-batch] draw skipped: reason=VeilRenderType-not-RenderLayer, id={}, texturePath={}, raw-type={}",
                        ID, texturePath, raw.getClass().getName());
                }
                return null;
            }
            if (!infoFirstResolve)
            {
                infoFirstResolve = true;
                LOG.info("[veil-batch] RenderType resolved OK first time: id={}, texturePath={}, rl-type={}",
                    ID, texturePath, rl.getClass().getSimpleName());
            }
            return rl;
        }
        catch (Throwable t)
        {
            if (!warned)
            {
                LOG.warn("VeilReplayRenderType.get({}) failed — falling back to vanilla BBS draw",
                    textureLink, t);
                warned = true;
            }
            return null;
        }
    }

}
