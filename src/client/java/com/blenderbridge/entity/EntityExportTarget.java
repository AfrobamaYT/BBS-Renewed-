// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export, task 4.1 wire-anschluss, W7-B1).
package com.blenderbridge.entity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * EntityExportTarget — export-scoped static holder that lets the wire path ({@link EntityExportBridge},
 * {@code ENTITY_EXPORT}) feed {@link EntityExporter#export} a THROWAWAY posing entity (a replay's
 * {@code MobForm} mob is not in the world, so {@code EntityExporter.resolveEntity}'s uuid/nearest-villager
 * lookup can never find it) and request a ROTATION-NEUTRAL capture, WITHOUT changing any signature on
 * the already-loaded {@code EntityExporter}.
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (static-holder-umweg, {@code RegionClamp}/{@code TextureStripExporter.pending}-muster)</h2>
 * <p>{@code EntityExporter} is already loaded in the running JVM (task 4.1 gates ran) — a new
 * {@code doExport(LivingEntity)} overload would be a NEW member on a loaded class (deploy rule: body
 * edits only; the {@code canAddMethod} flag is unreliable and the {@code <clinit>} trap is real, see
 * dossier). So the two hooks are plain BODY edits over there ({@code resolveEntity} consults
 * {@link #peekTarget} first, {@code resetBindPose} calls {@link #neutralizeRotation} per part) and ALL
 * new state lives here, in a brand-new lazy-loadable class. No static initializers beyond bytecode
 * defaults (null/false) — hotswap-safe by construction.</p>
 *
 * <h2>WHY ZERO ROTATIONS ("rig-neutral capture")</h2>
 * <p>The wire path exports geometry meant to hang under an ARMATURE that {@code replay_anim.py} /
 * future pose consumers drive with the ABSOLUTE {@code ModelPart} rotations from
 * {@code REPLAY_POSE_EXPORT_RESULT}/{@code ENTITY_BAKE_RESULT} (raw {@code capturePose} values).
 * If the OBJ were captured in true bind pose (villager arms folded at pitch −0.75 rad), applying the
 * absolute pose on top would rotate those parts TWICE. Capturing with every part's rotation zeroed
 * (pivot translations untouched!) makes "apply the absolute rotation" exact for EVERY part at EVERY
 * tree depth — the addon-side conjugation {@code P = H⁻¹ R H} needs no per-part bind compensation
 * (see {@code blender_bbs_bridge/replay_anim.py} "POSE MATH": with zero bind rotations the formula
 * is exact for arbitrary bone rest orientations). Direct/EXEC callers of {@code EntityExporter.export}
 * (holder not armed) still get the unchanged true bind pose.</p>
 *
 * <h2>MAIN-THREAD ONLY, arm→export→clear inside ONE dispatch</h2>
 * <p>Plain static fields, no volatile/locks: armed and cleared strictly within one synchronous
 * {@code BlenderBridgeClient.dispatch} call on the client-tick thread. {@code EntityBakeHandler}'s own
 * per-tick {@code resolveEntity} calls can never observe an armed target (same thread, holder is
 * cleared in the bridge's {@code finally} — leak-schutz exactly like {@code RegionClamp.clear()}).</p>
 */
public final class EntityExportTarget
{
    /** The throwaway posing entity the NEXT resolveEntity call must return; null = normal lookup. */
    private static LivingEntity target;

    /** True = resetBindPose additionally zeroes every part's pitch/yaw/roll (rig-neutral capture). */
    private static boolean zeroRotation;

    private EntityExportTarget()
    {
        /* static-only holder */
    }

    /** Arm for exactly ONE {@link EntityExporter#export} call. Caller MUST {@link #clear()} in a finally. */
    public static void arm(LivingEntity entity, boolean neutralRotations)
    {
        target = entity;
        zeroRotation = neutralRotations;
    }

    /** Disarm — always call from a finally (a leaked target would hijack the next ENTITY_BAKE resolve). */
    public static void clear()
    {
        target = null;
        zeroRotation = false;
    }

    /** Read by {@code EntityExporter.resolveEntity}'s body hook. Package-private on purpose. */
    static LivingEntity peekTarget()
    {
        return target;
    }

    /**
     * Read by {@code EntityExporter.resetBindPose}'s body hook, once per part, AFTER
     * {@code part.resetTransform()}: no-op unless armed with {@code neutralRotations=true},
     * then zeroes the part's rotation while keeping its pivot (translation) intact.
     */
    static void neutralizeRotation(ModelPart part)
    {
        if (!zeroRotation)
        {
            return;
        }

        part.xRot = 0F;
        part.yRot = 0F;
        part.zRot = 0F;
    }
}
