// Coded by Afrobama/Claude — kernel-logik für SyncWaitTimeoutMixin.
package com.afrobama.blendertransform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kernel-hälfte des AccelRender {@code Sync.waitSync()}-patches.
 *
 * <p><b>Der schnitt:</b> alles, woran man iteriert (der timeout-wert, die log-schwelle, die
 * entscheidung "war das ein timeout/fehler ja/nein", das throttle-zählen), liegt hier —
 * swapbar über den kernel-generation-mechanismus. Nur der reine {@code glClientWaitSync}-
 * aufruf bleibt im gewobenen {@code @WrapMethod}-handler (SyncWaitTimeoutMixin), weil ein
 * LWJGL-API-call sich nie ändert und ein {@code Operation<T>} (MixinExtras-typ) laut
 * MixinBruecke-javadoc NIEMALS über die brücke gereicht werden darf — genau das
 * identitäts-problem, das bei einem classloader-swap zuschlägt.</p>
 *
 * <p>Ersetzt die frühere {@code @Overwrite}-version 1:1 in der beobachtbaren wirkung: gleicher
 * timeout-wert, gleiche throttle-schwelle, gleiche log-message. Nur der weave-mechanismus hat
 * sich geändert.</p>
 */
public final class SyncWaitLogik
{
    private SyncWaitLogik() {}

    private static final Logger LOG =
        LoggerFactory.getLogger("bbstools/accelrender/sync-timeout");

    /** Timeout in nanoseconds for glClientWaitSync. 500ms — generous enough that healthy
     *  GPU work always signals, short enough that driver TDR doesn't trip. */
    private static final long TIMEOUT_NANOS = 500_000_000L;

    /** Sentinel value Sync.java initializes syncHandle to before first use. */
    private static final long UNSET_SYNC_HANDLE = -1L;

    /** Throttle log spam — log first timeout + every 60th after. */
    private static long timeoutCount;

    /** Getter statt öffentlichem feld — {@link com.afrobama.bbstools.client.blender.MixinBruecke#rufe}
     *  ruft nur METHODEN, kein feld-lesen über den normalen weg (das gibt es nur für
     *  @Shadow-felder der ZIELKLASSE via {@code feldLesen}, nicht für kernel-eigene felder). */
    public static long timeoutNanos()
    {
        return TIMEOUT_NANOS;
    }

    /**
     * @param syncHandle aktueller wert von {@code Sync.syncHandle} (via @Shadow im handler)
     * @return {@code true} wenn der gewobene handler den echten GL-wait ausführen soll,
     *         {@code false} wenn er sofort zurückkehren soll (unset-sentinel-fall)
     */
    public static boolean sollWarten(long syncHandle)
    {
        /* Guard die unset-sentinel value. Sync.java:10 initializes syncHandle = -1L; calling
         * glClientWaitSync(-1, ...) returns GL_WAIT_FAILED (or undefined per driver). Ohne
         * diesen guard würde mit unserem bounded timeout jede frame beim start/nach
         * resetSync() einen "timeout" loggen — und einen echten GL-fehler maskieren. */
        return syncHandle != UNSET_SYNC_HANDLE;
    }

    /**
     * Wertet das ergebnis von {@code glClientWaitSync} aus und loggt (throttled) bei timeout
     * oder fehler.
     *
     * @param glResult roher rückgabewert von glClientWaitSync (GL_TIMEOUT_EXPIRED /
     *                 GL_WAIT_FAILED / sonstiger erfolgs-code)
     * @param istTimeoutOderFehler true wenn glResult == GL_TIMEOUT_EXPIRED || GL_WAIT_FAILED
     *                             (der handler kennt die GL-konstanten, der kernel nicht)
     */
    public static void nachWarten(int glResult, boolean istTimeoutOderFehler)
    {
        if (!istTimeoutOderFehler)
        {
            return;
        }

        timeoutCount++;

        if (timeoutCount == 1L || timeoutCount % 60L == 0L)
        {
            LOG.warn(
                "AccelRender Sync.waitSync timed out (global count={}, result=0x{}). GPU under heavy load — frame may stutter but PC will NOT freeze. Original unbounded wait would have hard-frozen the render thread.",
                timeoutCount,
                Integer.toHexString(glResult));
        }
    }
}
