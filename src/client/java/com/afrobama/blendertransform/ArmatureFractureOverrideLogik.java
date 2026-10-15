package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.fracture.BonePhysicsSimulator;
import com.afrobama.bbstools.client.fracture.FractureRenderContext;
import com.afrobama.bbstools.client.ragdoll.ActorRagdollProxy;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.forms.entities.IEntity;

/**
 * Logik des fracture-override-mixins — herausgelöst aus
 * {@code BOBJArmatureFractureOverrideMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Phase-B-fracture: nachdem BBS die knochen-matrizen für einen render-pass über
 * {@code BOBJArmature.setupMatrices()} berechnet hat, überschreibt dieser code die matrizen
 * für alle knochen, die gerade vom {@link BonePhysicsSimulator} gesteuert werden. Die
 * per-tick-integration des simulators läuft unabhängig vom renderer; diese logik verbindet
 * nur seinen zustand mit BBS' matrix-array pro frame.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. {@code BOBJArmature} und {@code IEntity} sind
 * BBS-typen (stabiler loader) — erlaubt.</p>
 */
public final class ArmatureFractureOverrideLogik
{
    private ArmatureFractureOverrideLogik() {}

    /**
     * Wird am TAIL von {@code setupMatrices()} gerufen. {@code armature} kommt als
     * {@code Object} über die brücke (das mixin castet vor dem aufruf), damit die signatur
     * hier stabil bleibt, falls sich der cast-weg je ändert — aktuell reicht der direkte
     * BBS-typ, weil {@code BOBJArmature} selbst stabil ist.
     */
    public static void applyFractureOverrides(BOBJArmature armature)
    {
        IEntity actor = FractureRenderContext.current();
        if (actor == null) return;

        BonePhysicsSimulator.applyOverrides(actor, armature);

        /* Phase 2 ragdoll proxy: wenn für diesen actor ein entity-proxy-ragdoll erfasst wurde,
         * überschreibt es dessen BOBJ-knochenmatrizen mit den PhysicsEntity-transforms des
         * ragdolls. Läuft NACH der fracture-sim, damit ragdoll-körper die massgebliche quelle
         * für knochen sind, die der simulator noch nicht übernommen hat. */
        ActorRagdollProxy.applyOverrides(actor, armature);
    }
}
