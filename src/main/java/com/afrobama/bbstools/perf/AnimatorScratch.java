// Coded by Afrobama/Claude — Tier 1e Parallel Animator.
//
// Thread-local scratch buffers for {@code CubicModelAnimator}'s p/s/r
// temporaries. The upstream BBS class uses three static
// {@code Vector3d} fields that are reused for every
// {@code applyGroupAnimation} / {@code applyGroupAnimationPost} call.
// That is fine when only one thread is animating, but Tier 1e fans the
// per-replay {@code startRenderFrame} loop out across
// {@link java.util.concurrent.ForkJoinPool#commonPool()} so multiple
// threads may evaluate animation segments concurrently. Without thread-
// local scratch, two replays sharing the same Animation instance can
// stomp each other's intermediate position/scale/rotation, producing
// garbled poses.
//
// Usage from inside the parallelStream task:
//   AnimatorScratch s = AnimatorScratch.get();
//   // s.p / s.s / s.r are private to this thread for the rest of the
//   // task — pass them into interpolation helpers if you need scratch.
//
// CubicModelAnimator itself still references its own static fields, so
// our path's safety relies on the BBS animation code only running
// inside FormUtilsClient.render (always render-thread). The per-tick
// path we parallelize (FormProperties.applyProperties → KeyframeChannel
// interpolation → Molang eval) does NOT touch CubicModelAnimator
// directly; the static p/s/r fields stay quiescent across our parallel
// region. This class is kept as scaffolding for the future case where
// we want to fan the cubic animator itself in parallel (Tier 1f).
package com.afrobama.bbstools.perf;

import org.joml.Vector3d;

public final class AnimatorScratch
{
    /** Per-thread scratch instance. Constructed lazily on first
     *  {@link #get()} per thread. */
    private static final ThreadLocal<AnimatorScratch> TLS =
        ThreadLocal.withInitial(AnimatorScratch::new);

    public final Vector3d p = new Vector3d();
    public final Vector3d s = new Vector3d();
    public final Vector3d r = new Vector3d();

    private AnimatorScratch()
    {
    }

    public static AnimatorScratch get()
    {
        return TLS.get();
    }

    /** Optional cleanup — call from the main thread between sessions if
     *  you want to drop scratch references. Currently a no-op marker. */
    public static void clear()
    {
        TLS.remove();
    }

}
