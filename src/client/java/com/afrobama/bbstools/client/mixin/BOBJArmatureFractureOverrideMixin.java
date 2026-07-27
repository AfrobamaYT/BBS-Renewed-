package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.bobj.BOBJArmature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase B fracture: after BBS computes the bone matrices for a render
 * pass via {@code BOBJArmature.setupMatrices()}, override the matrices
 * for any bones that are currently driven by
 * {@link BonePhysicsSimulator}. The simulator's per-tick integration
 * is independent of the renderer; this mixin just bridges its state
 * into BBS's per-frame matrix array so the bone vertices end up at
 * the simulated body's world transform during rendering.
 *
 * <p><b>Identity</b>: we use {@link FractureRenderContext} (set by
 * {@code ModelFormRendererFractureCaptureMixin} at {@code render3D}
 * HEAD) to know which {@link IEntity} is being rendered. If null
 * (preview / non-actor render) we skip the override.</p>
 *
 * <p><b>Camera origin</b>: BBS's vertex matrix is camera-relative —
 * we subtract the camera's world position from the body's world
 * coords so the matrix produces small floats stable for GPU.</p>
 */
@Mixin(BOBJArmature.class)
public abstract class BOBJArmatureFractureOverrideMixin
{
    @Inject(method = "setupMatrices", at = @At("RETURN"), require = 0)
    private void bbstools$applyFractureOverrides(CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.ArmatureFractureOverrideLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc. */
        MixinBruecke.rufe("ArmatureFractureOverrideLogik", "applyFractureOverrides",
            (BOBJArmature)(Object) this);
    }

}
