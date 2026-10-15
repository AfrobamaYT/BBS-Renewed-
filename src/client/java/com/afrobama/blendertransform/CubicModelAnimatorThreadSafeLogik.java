package com.afrobama.blendertransform;

import com.afrobama.bbstools.perf.AnimatorScratch;

import org.joml.Vector3d;

/**
 * Logik des thread-safe-scratch-mixins für {@code CubicModelAnimator} — im kernel-paket, damit
 * sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code CubicModelAnimatorThreadSafeMixin} (2026-07-26). Die sechs
 * {@code @Redirect}-handler dort ersetzen je einen GETSTATIC von BBS' statischen p/s/r-feldern
 * durch das pro-thread-scratch aus {@link AnimatorScratch} — siehe dessen javadoc für das
 * paralleler-animator-problem. Diese klasse liefert nur noch die drei lookups; die handler
 * selbst bleiben einzeiler, die hierher delegieren.</p>
 *
 * <p>THREAD-SICHERHEIT UNVERÄNDERT: {@link AnimatorScratch#get()} liefert bereits ein
 * pro-thread-objekt (siehe dessen {@code ThreadLocal}); die brücke selbst führt keine
 * synchronisation ein und braucht keine — der cache in {@code MixinBruecke} ist ein
 * {@code ConcurrentHashMap} und damit für parallele aufrufer sicher.</p>
 *
 * <p>{@code Vector3d} (org.joml) ist ein bibliotheks-typ auf einem stabilen loader (schon in
 * mehreren anderen kernel-klassen als signatur-typ verwendet, z.B. {@code BBSTransformAdapter})
 * — kein kernel-typ, daher in der signatur erlaubt.</p>
 */
public final class CubicModelAnimatorThreadSafeLogik
{
    private CubicModelAnimatorThreadSafeLogik() {}

    public static Vector3d p()
    {
        return AnimatorScratch.get().p;
    }

    public static Vector3d s()
    {
        return AnimatorScratch.get().s;
    }

    public static Vector3d r()
    {
        return AnimatorScratch.get().r;
    }
}
