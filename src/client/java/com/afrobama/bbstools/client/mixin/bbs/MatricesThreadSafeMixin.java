// Coded by Afrobama/Claude — Tier 2-i: thread-safe BBS Matrices scratch.
//
// BBS's mchorse.bbs_mod.utils.joml.Matrices uses SHARED static scratch
// objects (rotation, forward, TEMP_4F, lerpA/B, lerpQa/Qb, lerpVa/Vb) in
// its rotate()/rotation()/lerp() helpers. When the replay animator
// (extractMatricesFor → applyActions → CubicRenderer matrix math) is run
// in parallel across ForkJoinPool workers, those statics get clobbered
// mid-computation by other threads → garbage matrices → mesh stretching
// (the exact failure that broke Tier 2-f).
//
// This mixin @Overwrites the scratch-using helpers to read from
// ThreadLocal scratch instead. Behaviour is IDENTICAL single-threaded
// (a ThreadLocal returns the same instance every call within one thread,
// just like the old static did), so there is no regression for BBS's
// normal serial render — it only makes the helpers safe to call from
// multiple threads at once, unlocking parallel Phase A in Tier 2-h.
//
// direction() is NOT overwritten — it already allocates locals (no statics).
package com.afrobama.bbstools.client.mixin.bbs;

import mchorse.bbs_mod.utils.joml.Matrices;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Matrices.class, remap = false)
public class MatricesThreadSafeMixin
{
    @Unique private static final ThreadLocal<Matrix3f>    bbstools$rotation = ThreadLocal.withInitial(Matrix3f::new);
    @Unique private static final ThreadLocal<Vector3f>    bbstools$forward  = ThreadLocal.withInitial(Vector3f::new);
    @Unique private static final ThreadLocal<Matrix4f>    bbstools$temp4f   = ThreadLocal.withInitial(Matrix4f::new);
    @Unique private static final ThreadLocal<Matrix3f>    bbstools$lerpA    = ThreadLocal.withInitial(Matrix3f::new);
    @Unique private static final ThreadLocal<Matrix3f>    bbstools$lerpB    = ThreadLocal.withInitial(Matrix3f::new);
    @Unique private static final ThreadLocal<Quaternionf> bbstools$lerpQa   = ThreadLocal.withInitial(Quaternionf::new);
    @Unique private static final ThreadLocal<Quaternionf> bbstools$lerpQb   = ThreadLocal.withInitial(Quaternionf::new);
    @Unique private static final ThreadLocal<Vector3f>    bbstools$lerpVa   = ThreadLocal.withInitial(Vector3f::new);
    @Unique private static final ThreadLocal<Vector3f>    bbstools$lerpVb   = ThreadLocal.withInitial(Vector3f::new);

    /**
     * @author Afrobama/Claude
     * @reason Thread-safe scratch — rotate() used a shared static Matrix3f.
     */
    @Overwrite
    public static Vector3f rotate(Vector3f vector, float pitch, float yaw)
    {
        Matrix3f rot = bbstools$rotation.get();
        rot.identity().rotateY(yaw).rotateX(pitch);
        rot.transform(vector);
        return vector;
    }

    /**
     * @author Afrobama/Claude
     * @reason Thread-safe scratch — rotation() used a shared static Vector3f.
     */
    @Overwrite
    public static Vector3f rotation(float pitch, float yaw)
    {
        Vector3f fwd = bbstools$forward.get();
        fwd.set(0F, 0F, 1F);
        return rotate(fwd, pitch, yaw);
    }

    /**
     * @author Afrobama/Claude
     * @reason Thread-safe scratch — lerp(3-arg) used the shared static TEMP_4F.
     */
    @Overwrite
    public static Matrix4f lerp(Matrix4f a, Matrix4f b, float t)
    {
        return lerp(a, b, t, bbstools$temp4f.get());
    }

    /**
     * @author Afrobama/Claude
     * @reason Thread-safe scratch — lerp(4-arg) used shared static lerpA/B/Qa/Qb/Va/Vb.
     */
    @Overwrite
    public static Matrix4f lerp(Matrix4f a, Matrix4f b, float t, Matrix4f out)
    {
        Quaternionf qa = bbstools$lerpQa.get().setFromNormalized(bbstools$lerpA.get().set(a));
        Quaternionf qb = bbstools$lerpQb.get().setFromNormalized(bbstools$lerpB.get().set(b));
        qa.slerp(qb, t);
        out.identity().rotate(qa);
        out.setTranslation(a.getTranslation(bbstools$lerpVa.get()).lerp(b.getTranslation(bbstools$lerpVb.get()), t));
        return out;
    }

}
