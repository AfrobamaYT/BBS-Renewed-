// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export/bake, task 4.3b).
package com.blenderbridge.entity;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * EntityBakeHandler — plan v2 task 4.3b: bake a free (AI-driven) mob's per-tick pose over N ticks
 * and answer with a dense frame series ({@code ENTITY_BAKE_RESULT}), because a 0.5s poll can never
 * reconstruct 20tps motion (panel B4 — explicitly NOT the actor poll's job).
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS + NO NEW METHODS ON {@code BlenderBridgeClient}</h2>
 * <p>Same deploy-model constraint as everywhere else in this package right now (W1+, restarts
 * forbidden, {@code canAddMethod=false} measured for already-loaded classes — task 0.2): this class
 * is brand new (lazy-loadable, no boot), and {@code BlenderBridgeClient} gains ZERO new members.
 * Wiring happens by widening the BODY of two methods that already exist there:</p>
 * <ul>
 * <li>{@code dispatch(JsonObject)}'s switch gets one more case,
 * {@code BridgeProtocol.TYPE_ENTITY_BAKE -> EntityBakeHandler.handle(message)} — the {@code TYPE_
 * ENTITY_BAKE}/{@code TYPE_ENTITY_BAKE_RESULT} constants themselves were already added to
 * {@code BridgeProtocol} during the 2026-07-16 W1 boot as a schema reservation for exactly this.</li>
 * <li>{@code onClientTick(MinecraftClient)}'s body gets one more call, {@code EntityBakeHandler.tick()}
 * — a per-tick drain, same shape as {@code StreamingWorldExporter}'s own
 * {@code ClientTickEvents.END_CLIENT_TICK} hook, except THAT class self-registers its own listener
 * (it is new code with no existing tick loop to hook into); this one rides the ALREADY-REGISTERED
 * {@code BlenderBridgeClient} tick handler instead, because widening that existing body is strictly
 * cheaper than registering a second listener for the same event.</li>
 * </ul>
 *
 * <h2>POSE SOURCE — re-pose per sampled tick, never trust "last render" state</h2>
 * <p>{@link EntityExporter#poseEntity} is called FRESH every sampled tick for every target uuid,
 * right before {@link EntityExporter#capturePose}. This matters because the {@link ModelPart} tree
 * backing a given entity TYPE is a single instance SHARED across every entity of that type (vanilla's
 * own model-caching) — relying on "whatever the last actual render call left it at" would be wrong
 * the moment two villagers exist, or the target was off-screen last frame. Re-deriving the pose
 * ourselves from the entity's own public fields (see {@link EntityExporter}'s POSE section) sidesteps
 * that entirely: correct regardless of render order or visibility.</p>
 *
 * <h2>ONE JOB AT A TIME</h2>
 * <p>Same guard shape as {@code StreamingWorldExporter.activeJob}: a second {@code ENTITY_BAKE}
 * while one is running is rejected immediately with a clear error rather than silently interleaved
 * or queued.</p>
 */
public final class EntityBakeHandler
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The one active bake job, or null. Touched only from the main thread (dispatch + tick both run there). */
    private static Job activeJob;

    private EntityBakeHandler()
    {
        /* static-only helper */
    }

    /**
     * Handle an ENTITY_BAKE request (called from {@code BlenderBridgeClient.dispatch}'s new switch
     * case). Never throws — every failure path answers immediately with an {@code ok:false}
     * ENTITY_BAKE_RESULT rather than leaving the caller waiting.
     */
    public static void handle(JsonObject message)
    {
        try
        {
            doHandle(BridgeProtocol.getFields(message));
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] entity bake failed to start", t);
            sendResult(failure(t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    private static void doHandle(JsonObject fields)
    {
        if (activeJob != null)
        {
            sendResult(failure("a bake job is already running"));
            return;
        }

        long ticksRequested = BridgeProtocol.getLong(fields, EntityProtocol.REQ_TICKS, 20);

        if (ticksRequested <= 0)
        {
            sendResult(failure("ticks must be >= 1"));
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            sendResult(failure("no world loaded (join a world before baking)"));
            return;
        }

        List<String> uuids = requestedUuids(fields);

        if (uuids.isEmpty())
        {
            LivingEntity nearest = EntityExporter.resolveEntity(client, null);

            if (nearest == null)
            {
                sendResult(failure("no villager found near the player"));
                return;
            }

            uuids.add(nearest.getStringUUID());
        }

        /* Fail fast: verify every target resolves NOW, so we never start a job doomed to fail on
         * tick 1 (dropping mid-bake would waste ticks and confuse "frames==N" gate checks). */
        for (String uuid : uuids)
        {
            if (EntityExporter.resolveEntity(client, uuid) == null)
            {
                sendResult(failure("entity not found for uuid " + uuid));
                return;
            }
        }

        activeJob = new Job(uuids, (int) ticksRequested);

        LOGGER.info("[blenderbridge] entity bake START: uuids={} ticks={}", uuids, ticksRequested);
    }

    /**
     * Per-tick drain (called from {@code BlenderBridgeClient.onClientTick}'s widened body). No-op
     * when no job is active — cheap to call unconditionally every tick.
     */
    public static void tick()
    {
        Job job = activeJob;

        if (job == null)
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            activeJob = null;
            sendResult(failure("world unloaded during bake"));
            return;
        }

        JsonObject frame = new JsonObject();
        frame.addProperty(EntityProtocol.RES_FRAME_TICK, job.ticksDone);

        for (String uuid : job.uuids)
        {
            LivingEntity entity = EntityExporter.resolveEntity(client, uuid);

            if (entity == null)
            {
                activeJob = null;
                sendResult(failure("entity " + uuid + " no longer present (despawned mid-bake)"));
                return;
            }

            ModelPart root = EntityExporter.resolveRootPart(client, entity);

            if (root == null)
            {
                activeJob = null;
                sendResult(failure("entity " + uuid + " lost its model mid-bake"));
                return;
            }

            EntityExporter.poseEntity(client, entity);
            Map<String, float[]> pose = EntityExporter.capturePose(root);

            JsonObject bones = new JsonObject();

            for (Map.Entry<String, float[]> boneEntry : pose.entrySet())
            {
                JsonArray values = new JsonArray();

                for (float value : boneEntry.getValue())
                {
                    values.add(value);
                }

                String boneName = boneEntry.getKey().isEmpty() ? "root" : boneEntry.getKey();
                bones.add(boneName, values);
            }

            frame.add(uuid, bones);
        }

        job.frames.add(frame);
        job.ticksDone++;

        if (job.ticksDone >= job.totalTicks)
        {
            activeJob = null;
            finish(job);
        }
    }

    private static void finish(Job job)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty(EntityProtocol.RES_OK, true);
        payload.addProperty(EntityProtocol.RES_FPS, EntityProtocol.TICKS_PER_SECOND);
        payload.add(EntityProtocol.RES_FRAMES, job.frames);
        payload.addProperty(EntityProtocol.RES_ERROR, "");

        LOGGER.info("[blenderbridge] entity bake DONE: uuids={} frames={}", job.uuids, job.frames.size());

        sendResult(payload);
    }

    private static JsonObject failure(String reason)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty(EntityProtocol.RES_OK, false);
        payload.addProperty(EntityProtocol.RES_FPS, EntityProtocol.TICKS_PER_SECOND);
        payload.add(EntityProtocol.RES_FRAMES, new JsonArray());
        payload.addProperty(EntityProtocol.RES_ERROR, reason);
        return payload;
    }

    private static void sendResult(JsonObject payload)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
        JsonObject envelope = BridgeProtocol.envelope(BridgeProtocol.TYPE_ENTITY_BAKE_RESULT, EntityProtocol.UUID_ENTITY_BAKE, seq, BridgeProtocol.ORIGIN_BBS);

        /* Replace the empty fields object with the bake payload directly — same convention as
         * BridgeProtocol.worldExportResult/actorsExportResult. */
        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        BlenderBridgeClient.SERVER.send(envelope);
    }

    /** Reads {@code fields.uuids} (array, preferred) then falls back to {@code fields.entityUuid}. */
    private static List<String> requestedUuids(JsonObject fields)
    {
        List<String> out = new ArrayList<>();

        if (fields == null)
        {
            return out;
        }

        JsonElement uuidsElement = fields.get(EntityProtocol.REQ_UUIDS);

        if (uuidsElement != null && uuidsElement.isJsonArray())
        {
            for (JsonElement element : uuidsElement.getAsJsonArray())
            {
                if (element.isJsonPrimitive())
                {
                    out.add(element.getAsString());
                }
            }
        }

        if (out.isEmpty())
        {
            String single = BridgeProtocol.getFieldString(fields, EntityProtocol.REQ_ENTITY_UUID, null);

            if (single != null && !single.isBlank())
            {
                out.add(single);
            }
        }

        return out;
    }

    /** One in-flight bake job. Touched only from the main thread. */
    private static final class Job
    {
        final List<String> uuids;
        final int totalTicks;
        final JsonArray frames = new JsonArray();
        int ticksDone;

        Job(List<String> uuids, int totalTicks)
        {
            this.uuids = uuids;
            this.totalTicks = totalTicks;
        }
    }
}
