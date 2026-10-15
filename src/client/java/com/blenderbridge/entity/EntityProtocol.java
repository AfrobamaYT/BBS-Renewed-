// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export/bake).
package com.blenderbridge.entity;

/**
 * EntityProtocol — payload-internal field keys for the entity-export/bake channel.
 *
 * <p>The message TYPE constants ({@code ENTITY_BAKE}, {@code ENTITY_BAKE_RESULT}) already live in
 * {@code BridgeProtocol} — they were added there during the 2026-07-16 W1 boot as a "schema
 * reservation" (see that file's BOOT-WINDOW SCHEMA RESERVATIONS comment) specifically so this
 * package could land its handler body later WITHOUT another boot. This class is the NEW,
 * lazy-loadable counterpart that holds the payload-shape keys — mirrors {@code ActorProtocol}'s
 * relationship to {@code BridgeProtocol}.</p>
 *
 * <p>ENTITY_BAKE request ({@code fields}):</p>
 * <pre>
 * {
 *   "uuids": ["&lt;entity uuid string&gt;", ...],   // preferred, matches plan v2 task 4.3b wire shape
 *   "entityUuid": "&lt;entity uuid string&gt;",     // singular convenience alias, used when "uuids" absent
 *   "ticks": 20                                     // how many client ticks to sample, &gt;=1
 * }
 * </pre>
 * <p>Neither {@code uuids} nor {@code entityUuid} given -&gt; bakes the single nearest villager to
 * the player (dev-convenience for EXEC-driven testing; the wire contract with Blender is expected
 * to always send an explicit uuid).</p>
 *
 * <p>ENTITY_BAKE_RESULT reply ({@code fields}):</p>
 * <pre>
 * {
 *   "ok": true,
 *   "fps": 20,              // sampling is one sample per client tick == 20tps, always
 *   "frames": [
 *     {
 *       "tick": 0,
 *       "&lt;uuid&gt;": { "&lt;bonePath&gt;": [pivotX,pivotY,pivotZ,pitch,yaw,roll,xScale,yScale,zScale], ... }
 *     },
 *     ...
 *   ],
 *   "error": ""
 * }
 * </pre>
 * <p>{@code bonePath} uses the EXACT same "/parent/child" path convention as
 * {@code ModelPart.forEachCuboid}'s own internal path building (root = {@code ""}), so a bone path
 * seen here lines up 1:1 with the {@code g} groups in an {@link EntityExporter}-written OBJ for the
 * same entity type. Angles are RADIANS (raw {@code ModelPart.pitch/yaw/roll} — BBS-internal-rotation
 * convention, matches the project's existing radian contract elsewhere).</p>
 */
public final class EntityProtocol
{
    /* ---- ENTITY_BAKE request field keys ---- */

    /** Preferred: JSON array of entity uuid strings to bake together, one shared frame per tick. */
    public static final String REQ_UUIDS = "uuids";

    /** Convenience alias: a single uuid string, used when {@link #REQ_UUIDS} is absent/empty. */
    public static final String REQ_ENTITY_UUID = "entityUuid";

    /** How many client ticks to sample. */
    public static final String REQ_TICKS = "ticks";

    /* ---- ENTITY_BAKE_RESULT reply field keys ---- */

    public static final String RES_OK = "ok";
    public static final String RES_FPS = "fps";
    public static final String RES_FRAMES = "frames";
    public static final String RES_ERROR = "error";
    public static final String RES_FRAME_TICK = "tick";

    /** Sampling rate: one sample per client tick, always — the whole point of task 4.3b (panel B4:
     *  a 0.5s poll cannot reconstruct 20tps motion). */
    public static final int TICKS_PER_SECOND = 20;

    /** UUID used for the entity-bake channel envelope (mirrors UUID_WORLD/UUID_ACTORS/UUID_EXEC). */
    public static final String UUID_ENTITY_BAKE = "entity-bake";

    private EntityProtocol()
    {
        /* static-only constants holder */
    }
}
