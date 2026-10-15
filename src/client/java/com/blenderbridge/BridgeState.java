// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 transport).
package com.blenderbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;

/**
 * BridgeState — shared, thread-safe sync state for the bridge.
 *
 * <p>WHY THIS EXISTS: the transport ({@link BridgeServer}) runs on socket threads while
 * all BBS/MC mutation happens on the client main thread (from the tick handler in
 * {@link BlenderBridgeClient}). This class is the small piece of state both sides touch,
 * so every field here is thread-safe.</p>
 *
 * <p>ECHO SUPPRESSION ({@link #applyingRemote}): while the tick handler applies a value
 * that came FROM Blender, it sets this flag. Any BBS-side change-detection that runs
 * during the apply must check {@link #isApplyingRemote()} and skip re-sending — otherwise
 * a value bounces bbs->blender->bbs forever. The flag is a plain volatile: set/clear
 * happens only on the main thread, reads on the main thread; it never crosses to the
 * socket thread, but volatile keeps it honest and cheap.</p>
 *
 * <p>LWW ({@link #lastSeqPerUuid}): last-writer-wins de-dup keyed by object uuid. Before
 * applying an inbound message, call {@link #shouldApply(String, long)} — it atomically
 * checks that {@code seq} is strictly newer than the last accepted seq for that uuid and,
 * if so, records it. Stale/out-of-order frames are dropped. Called from the main thread.</p>
 */
public final class BridgeState
{
    /**
     * Set to true (on the main thread) for the duration of applying a remote (Blender)
     * value into BBS. BBS-side change-detection MUST consult {@link #isApplyingRemote()}
     * and skip re-emitting while this is true. See class-doc.
     */
    private volatile boolean applyingRemote = false;

    /** LWW cursor: highest accepted seq per object uuid. */
    private final ConcurrentHashMap<String, Long> lastSeqPerUuid = new ConcurrentHashMap<>();

    /** Monotonic outbound sequence counter (this = the "bbs" origin). */
    private final AtomicLong outboundSeq = new AtomicLong(0L);

    /**
     * The most recent CAMERA message received from Blender that the main thread has NOT
     * yet applied. The tick handler drains the inbox, LWW-filters, and parks the accepted
     * camera delta here; the camera-apply worker consumes it on the main thread.
     *
     * <p>HOOK POINT for the OTHER worker — see {@link #takePendingCamera()}.</p>
     */
    private final AtomicReference<JsonObject> pendingCamera = new AtomicReference<>(null);

    /** Last peer handshake; cleared and replaced atomically on every HELLO. */
    private volatile int peerProtocol = 0;
    private volatile String peerVersion = "";
    private final Set<String> peerCapabilities = ConcurrentHashMap.newKeySet();

    /* ---- Echo-suppression flag ---- */

    /** @return true while a remote value is being applied into BBS (skip re-emit). */
    public boolean isApplyingRemote()
    {
        return this.applyingRemote;
    }

    /**
     * Run {@code apply} with the {@link #applyingRemote} guard raised, then lowered in a
     * {@code finally} so an exception in the apply-path can never leave the flag stuck.
     * MUST be called on the main thread.
     */
    public void withRemoteApply(Runnable apply)
    {
        this.applyingRemote = true;

        try
        {
            apply.run();
        }
        finally
        {
            this.applyingRemote = false;
        }
    }

    /* ---- LWW ---- */

    /**
     * Atomically decide whether an inbound message for {@code uuid} with sequence
     * {@code seq} is newer than what we last accepted, and if so record it.
     *
     * @return true if the caller should apply the message (it was strictly newer);
     *         false if it is stale/duplicate and must be dropped.
     */
    public boolean shouldApply(String uuid, long seq)
    {
        if (uuid == null)
        {
            /* No identity → cannot dedup; accept and do not track. */
            return true;
        }

        /* merge() is atomic on ConcurrentHashMap: keep the max of old vs. new seq. */
        Long previous = this.lastSeqPerUuid.get(uuid);

        if (previous != null && seq <= previous)
        {
            return false;
        }

        /* Record only when strictly newer. compute() keeps this race-free under
         * concurrent callers (though today only the main thread calls this). */
        boolean[] accepted = new boolean[1];

        this.lastSeqPerUuid.compute(uuid, (key, current) ->
        {
            if (current != null && seq <= current)
            {
                accepted[0] = false;
                return current;
            }

            accepted[0] = true;
            return seq;
        });

        return accepted[0];
    }

    /** Forget the LWW cursor for a uuid (e.g. on disconnect / re-sync). */
    public void resetSeq(String uuid)
    {
        if (uuid != null)
        {
            this.lastSeqPerUuid.remove(uuid);
        }
    }

    /** Clear all LWW cursors (full re-sync). */
    public void resetAllSeq()
    {
        this.lastSeqPerUuid.clear();
    }

    /* ---- Outbound sequence ---- */

    /** @return the next monotonic outbound sequence number (for messages we send). */
    public long nextOutboundSeq()
    {
        return this.outboundSeq.incrementAndGet();
    }

    /* ---- Peer capability handshake ---- */

    public void updatePeerHandshake(JsonObject fields)
    {
        this.peerProtocol = (int) BridgeProtocol.getLong(fields, "protocol", 0L);
        this.peerVersion = BridgeProtocol.getFieldString(fields, "version", "");
        this.peerCapabilities.clear();

        JsonElement capabilities = fields == null ? null : fields.get("capabilities");

        if (capabilities != null && capabilities.isJsonArray())
        {
            for (JsonElement capability : capabilities.getAsJsonArray())
            {
                try
                {
                    this.peerCapabilities.add(capability.getAsString());
                }
                catch (RuntimeException ignored)
                {
                    /* One malformed optional entry must not invalidate the complete handshake. */
                }
            }
        }
    }

    public int getPeerProtocol()
    {
        return this.peerProtocol;
    }

    public String getPeerVersion()
    {
        return this.peerVersion;
    }

    public boolean peerHasCapability(String capability)
    {
        return capability != null && this.peerCapabilities.contains(capability);
    }

    /* ================================================================= *
     *  CAMERA HOOK — the ONLY surface the camera-apply worker needs.    *
     * ================================================================= */

    /**
     * Park an accepted inbound CAMERA envelope for the main thread to apply. Called by the
     * tick handler after LWW-filtering. If a previous pending camera was not yet consumed,
     * it is overwritten (coalescing — only the newest camera state matters).
     *
     * @param cameraMessage the full CAMERA envelope (type/uuid/seq/origin/fields)
     */
    public void setPendingCamera(JsonObject cameraMessage)
    {
        this.pendingCamera.set(cameraMessage);
    }

    /**
     * HOOK POINT (camera-apply worker): atomically take the pending inbound CAMERA envelope
     * and clear it. Returns {@code null} if none is pending.
     *
     * <p>The worker should, on the MAIN THREAD:</p>
     * <pre>
     *   JsonObject cam = state.takePendingCamera();
     *   if (cam != null)
     *   {
     *       state.withRemoteApply(() -&gt; applyCameraToBBS(cam)); // TODO(worker): implement
     *   }
     * </pre>
     *
     * @return the pending CAMERA envelope, or {@code null}
     */
    public JsonObject takePendingCamera()
    {
        return this.pendingCamera.getAndSet(null);
    }

    /** @return true if an inbound CAMERA envelope is waiting to be applied. */
    public boolean hasPendingCamera()
    {
        return this.pendingCamera.get() != null;
    }
}
