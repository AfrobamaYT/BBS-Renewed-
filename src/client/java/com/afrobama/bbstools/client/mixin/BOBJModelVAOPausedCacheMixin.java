// Coded by Afrobama/Claude — Tier 3g static-batch paused replays.
//
// HEAD-injects {@code BOBJModelVAO.updateMesh}: if the cache is enabled
// AND the armature.matrices reference + digest matches the last-seen
// snapshot for this VAO, skip the entire CPU rebuild + GPU upload.
//
// WHY DIGEST OVER REFERENCE EQUALITY
//   The armature.matrices field is a {@code Matrix4f[]} whose ELEMENTS
//   are mutated in place by the BBS animator. Reference equality on the
//   array itself is therefore preserved frame-to-frame even when the
//   underlying matrices change — it's the wrong test. Instead we hash
//   a small subset (m00..m23 of bone 0 + length) which is statistically
//   unique enough for the "did this pose change?" question while costing
//   well under 1 µs per call. Hash collisions only ever cause a missed
//   skip (correctness preserved — we just rebuild when we could have
//   skipped); they never cause stale matrices to be uploaded.
//
// SAFETY
//   - Default OFF via BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE.
//   - Skipped when stencilMap != null (per-pixel bone mask depends on
//     the per-vertex light buffer which we'd be re-uploading anyway).
//   - Skipped when armature is null (defensive — shouldn't happen).
//   - On first call after the mixin loads, the snapshot is null and
//     the normal path runs; the snapshot is taken at TAIL.
//
// PER-MIXIN-CONVENTION
//   No public static state lives in this mixin class. Counters live on
//   {@link com.afrobama.bbstools.perf.BBSPerfConfig} +
//   {@link com.afrobama.bbstools.perf.PausedReplayBoneCache}.
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BOBJModelVAO.class, remap = false)
public abstract class BOBJModelVAOPausedCacheMixin
{
    /* {digest, validFlag(0/1)} — bleibt im mixin, weil es an DIESE VAO-instanz gebunden ist.
     * Die brücke bekommt ihn als in/out-carrier durchgereicht, siehe
     * BOBJModelVAOPausedCacheLogik-javadoc. */
    @Unique
    private final long[] bbs$zustand = new long[2];

    @Inject(method = "updateMesh", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbs$skipWhenPosesUnchanged(StencilMap stencilMap, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.BOBJModelVAOPausedCacheLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. Der @Shadow-zugriff auf armature.matrices
         * ist mit in den kernel gewandert (BOBJModelVAO ist ein stabiler BBS-typ). */
        if (MixinBruecke.rufeUndAbbrechen("BOBJModelVAOPausedCacheLogik", "skipWhenPosesUnchanged",
            bbs$zustand, stencilMap, (BOBJModelVAO)(Object) this))
        {
            ci.cancel();
        }
    }

}
