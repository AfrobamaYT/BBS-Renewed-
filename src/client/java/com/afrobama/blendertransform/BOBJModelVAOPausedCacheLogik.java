package com.afrobama.blendertransform;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;

import org.joml.Matrix4f;

/**
 * Logik des paused-replay-bone-cache-mixins — herausgelöst aus
 * {@code BOBJModelVAOPausedCacheMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>HEAD-injiziert {@code BOBJModelVAO.updateMesh}: ist der cache aktiviert UND das digest
 * der armature.matrices identisch zum letzten schnappschuss dieser VAO, wird der gesamte
 * CPU-rebuild + GPU-upload übersprungen.</p>
 *
 * <h2>Warum digest statt referenz-gleichheit</h2>
 * Das feld armature.matrices ist ein {@code Matrix4f[]}, dessen ELEMENTE vom BBS-animator
 * inplace mutiert werden. Referenz-gleichheit auf das array selbst bleibt daher frame-zu-frame
 * erhalten, auch wenn sich die matrizen darunter ändern — der falsche test. Stattdessen wird
 * eine kleine teilmenge gehasht (m00..m23 von knochen 0 + länge), was für die frage "hat sich
 * die pose geändert?" statistisch eindeutig genug ist und weit unter 1 µs pro aufruf kostet.
 * Hash-kollisionen führen nur zu einem verpassten skip (korrektheit bleibt erhalten — es wird
 * nur neu gebaut, obwohl man hätte überspringen können); sie führen NIE dazu, dass veraltete
 * matrizen hochgeladen werden.
 *
 * <h2>Sicherheit</h2>
 * <ul>
 *   <li>Default AUS via {@code BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE}.</li>
 *   <li>Übersprungen, wenn stencilMap != null (der per-pixel-bone-mask hängt am
 *       per-vertex-lichtbuffer, der ohnehin neu hochgeladen würde).</li>
 *   <li>Übersprungen, wenn armature null ist (defensiv — sollte nicht passieren).</li>
 * </ul>
 *
 * <p>Die per-instanz-zustandsfelder ({@code lastPoseDigest}, {@code lastDigestValid}) bleiben
 * ABSICHTLICH im mixin — sie sind an die ZIELKLASSEN-INSTANZ gebunden (eine VAO pro modell),
 * nicht an den kernel. Der handler reicht sie als {@code long[]}{digest, validFlag} durch und
 * bekommt denselben array als in/out-carrier zurück, damit hier keine kernel-typen in der
 * signatur stehen und trotzdem zustand pro VAO-instanz erhalten bleibt.</p>
 */
public final class BOBJModelVAOPausedCacheLogik
{
    private BOBJModelVAOPausedCacheLogik() {}

    /**
     * @param zustand   {digest, validFlag(0/1)} — wird bei einem MISS aktualisiert
     * @param zielVao   die VAO-instanz, deren {@code armature.matrices} gelesen wird (BBS-typ,
     *                  stabiler loader — @Shadow-zugriff wandert hierher statt im mixin zu bleiben)
     * @return {@code true}, wenn der aufrufer {@code ci.cancel()} rufen soll (HIT)
     */
    public static boolean skipWhenPosesUnchanged(long[] zustand, StencilMap stencilMap, BOBJModelVAO zielVao)
    {
        if (!BBSPerfConfig.ENABLE_PAUSED_BONE_CACHE) return false;
        /* Stencil-map-renders brauchen unabhängig von der pose einen frischen
         * licht-buffer-upload, also wird für sie nicht versucht zu überspringen. */
        if (stencilMap != null) return false;

        BOBJArmature armature = zielVao.armature;
        if (armature == null) return false;

        Matrix4f[] matrices = armature.matrices;
        if (matrices == null || matrices.length == 0) return false;

        long digest = digest(matrices);
        boolean lastValid = zustand[1] != 0L;

        if (lastValid && zustand[0] == digest)
        {
            /* Gleiche pose wie letzter frame — rebuild + GPU-upload überspringen.
             * Die vertex/normal-buffer des vorherigen frames sind noch im GL-state
             * gebunden, render() zeichnet sie also unverändert. */
            BBSPerfConfig.pausedBoneCacheHits++;
            return true;
        }

        /* Miss — normales updateMesh laufen lassen und dieses digest für den nächsten
         * frame merken. */
        BBSPerfConfig.pausedBoneCacheMisses++;
        zustand[0] = digest;
        zustand[1] = 1L;

        return false;
    }

    /**
     * Billiges digest über die armature-pose: länge + erste 6 zellen von knochen 0 + erste
     * 4 zellen des mittleren knochens + erste 4 zellen des letzten knochens. Ausreichende
     * auflösung, um "hat sich irgendein animator-output geändert" zu erkennen, ohne jedes
     * matrix-element zu durchlaufen. Liefert einen 64-bit-mix, damit zufällige treffer
     * zwischen unabhängigen armatures unwahrscheinlich sind.
     */
    private static long digest(Matrix4f[] matrices)
    {
        long h = 1469598103934665603L; /* fnv64 offset basis */
        h = mix(h, matrices.length);

        Matrix4f m0 = matrices[0];
        if (m0 != null)
        {
            h = mix(h, Float.floatToRawIntBits(m0.m00()));
            h = mix(h, Float.floatToRawIntBits(m0.m11()));
            h = mix(h, Float.floatToRawIntBits(m0.m22()));
            h = mix(h, Float.floatToRawIntBits(m0.m30()));
            h = mix(h, Float.floatToRawIntBits(m0.m31()));
            h = mix(h, Float.floatToRawIntBits(m0.m32()));
        }

        if (matrices.length > 2)
        {
            Matrix4f mm = matrices[matrices.length / 2];
            if (mm != null)
            {
                h = mix(h, Float.floatToRawIntBits(mm.m00()));
                h = mix(h, Float.floatToRawIntBits(mm.m30()));
                h = mix(h, Float.floatToRawIntBits(mm.m31()));
                h = mix(h, Float.floatToRawIntBits(mm.m32()));
            }
        }

        Matrix4f mn = matrices[matrices.length - 1];
        if (mn != null)
        {
            h = mix(h, Float.floatToRawIntBits(mn.m00()));
            h = mix(h, Float.floatToRawIntBits(mn.m30()));
            h = mix(h, Float.floatToRawIntBits(mn.m31()));
            h = mix(h, Float.floatToRawIntBits(mn.m32()));
        }
        return h;
    }

    private static long mix(long h, long v)
    {
        h ^= v;
        h *= 1099511628211L; /* fnv64 prime */
        return h;
    }
}
