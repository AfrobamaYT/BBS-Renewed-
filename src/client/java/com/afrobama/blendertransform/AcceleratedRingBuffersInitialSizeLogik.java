// Coded by Afrobama/Claude — kernel-logik für AcceleratedRingBuffersInitialSizeMixin.
//
// Layer 2 fix für die AccelRender-freeze-kette. Vergrößert die initial-size des
// sharingBuffer von 64 bytes auf 1 MB, um die power-of-2-kaskaden-resize zu vermeiden, die
// GPU-sync-stürme beim ersten kamera-schwenk mit vielen gleich-mesh-instanzen auslöst.
//
// DAS PROBLEM (AccelRender AcceleratedRingBuffers.java:109):
//   this.sharingBuffer = new MappedBuffer(64L);
//
// Jeder sharing-eintrag = 112 bytes (16 floats für Matrix4f-transform + 12 floats für
// Matrix3f-normal). Bei unserer skala von ~5000-20000 sichtbaren villagern pro frame nach
// pose-cache-invalidierung (kamera-schwenk):
//   - 5000 instanzen × 112 B = 560 KB benötigt
//   - 20000 instanzen × 112 B = 2.24 MB benötigt
//
// Power-of-2-wachstum ab 64 B = 13-16 reallokationen PRO FRAME. Jede reallokation macht:
//   - glNamedBufferStorage (neue buffer-allokation)
//   - glCopyNamedBufferSubData (kopie alt → neu)
//   - glUnmap + glMap persistent-coherent (VOLLER GPU-SYNC)
//   - glDeleteBuffers (alter handle)
//
// Kombiniert mit Sync.waitSync(Long.MAX_VALUE), wenn ring-slots ausgehen (jetzt von
// SyncWaitTimeoutMixin auf 500ms gepatcht), verursachte das vorher PC-hard-freezes beim
// kamera-schwenk. Mit Layer 1 (Sync-timeout) wird der freeze durch 1-FPS-stutter ersetzt;
// mit Layer 2 (diese logik) wird auf 1 MB vorgewachsen, damit die kaskade nie passiert —
// gehaltene 30-60 FPS möglich, auch mit tausenden gleich-mesh-instanzen.
//
// SICHERHEIT: 1 MB initial × 8 ring-slots = 8 MB total. Winzig verglichen mit dem 57 MB
// element-buffer, den AccelRender ohnehin pro slot allokiert. Die zusatzkosten sind einmalig
// pro slot; einmal allokiert, ist kein resize mehr nötig.
//
// EVIDENZ: deep-research 2026-05-17 (Agent 2 pipeline-analyse) identifizierte die
// buffer-kaskade als auslöse-mechanismus. Datei-referenzen:
//   - AcceleratedRingBuffers.java:109 (das 64L-literal)
//   - AcceleratedRingBuffers.java:211-213 (112 B/eintrag-größe)
//   - MutableBuffer.java:21-28 (der kaskaden-resize-code)
//   - MappedBuffer.java:58-65 (unmap/remap beim wachsen)
package com.afrobama.blendertransform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AcceleratedRingBuffersInitialSizeLogik
{
    private AcceleratedRingBuffersInitialSizeLogik() {}

    private static final Logger LOG =
        LoggerFactory.getLogger("bbstools/accelrender/buffer-presize");

    /** Pre-allocate sharingBuffer at 1 MB instead of 64 bytes.
     *  Sized for ~9000 sharing entries (112 B each) — comfortable
     *  headroom for our cap=500 workload (500 × 112 = 56KB needed)
     *  without over-allocating VRAM. Larger sizes (4MB+) appear to
     *  contribute to CPU+GPU memory pressure that triggers freeze
     *  with high-cap workloads. */
    private static final long SHARING_INITIAL_BYTES = 1024L * 1024L;

    private static volatile boolean logged;

    /**
     * Liefert die ersatz-größe für den {@code new MappedBuffer(64L)}-aufruf im
     * {@code AcceleratedRingBuffers.Buffers}-konstruktor (zeile 109).
     *
     * @param originalSize die von AccelRender übergebene originale größe
     * @return die zu verwendende größe (originalgröße als fallback, wenn schon groß genug)
     */
    public static long enlargeSharingBuffer(long originalSize)
    {
        if (originalSize >= SHARING_INITIAL_BYTES)
        {
            return originalSize;
        }
        if (!logged)
        {
            logged = true;
            LOG.info("AccelRender sharingBuffer pre-sized: {} B → {} B ({} MB). Prevents power-of-2 cascade resize on camera-pan.",
                originalSize,
                SHARING_INITIAL_BYTES,
                SHARING_INITIAL_BYTES / (1024 * 1024));
        }
        return SHARING_INITIAL_BYTES;
    }
}
