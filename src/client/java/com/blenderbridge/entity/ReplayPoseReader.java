// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export/bake, task 4.3a).
package com.blenderbridge.entity;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * ReplayPoseReader — plan v2 task 4.3a: pose a replay-bound mob-form actor DIRECTLY from its BBS
 * keyframe channels, no per-tick runtime sampling required, because — unlike a free (AI-driven) mob
 * ({@link EntityBakeHandler}, task 4.3b) — a replay's ENTIRE performance already exists as
 * {@link KeyframeChannel}s the instant a film is open. {@code BridgeProtocol.TYPE_ENTITY_BAKE}'s own
 * javadoc says as much: "Replay-bound actors do NOT use this — their keyframes are read directly."
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS (deploy-model, unchanged reasoning)</h2>
 * <p>Same constraint as every other class in this package (W1+, restarts forbidden except
 * crash/freeze/system — project hard rule; {@code canAddMethod=false} measured for already-loaded
 * classes, task 0.2): brand new, lazy-loadable class, zero new members on any already-loaded class.
 * Wiring is a single BODY-only widening of {@code BlenderBridgeClient.dispatch}'s switch — one raw
 * string case, exactly the {@code FILM_CAMERA_EXPORT}/{@code PLAYHEAD} pattern
 * ({@link com.blenderbridge.camera.FilmCameraExporter}, {@link com.blenderbridge.film.PlayheadSync}):
 * {@code "REPLAY_POSE_EXPORT"} in, {@code "REPLAY_POSE_EXPORT_RESULT"} out, no new
 * {@code BridgeProtocol.TYPE_*} constant. No {@code onClientTick} change either — unlike
 * {@code EntityBakeHandler}'s incremental per-tick job, this class answers the WHOLE requested tick
 * range synchronously in one dispatch call (see next section), so there is nothing to drain later.</p>
 *
 * <h2>"KEIN SAMPLING" — direct interpolation, not incremental real-time capture</h2>
 * <p>{@code EntityBakeHandler} must wait real wall-clock ticks to elapse (its job drains one sampled
 * tick per {@code ClientTickEvents.END_CLIENT_TICK}, because a free mob's future pose does not exist
 * yet — it only comes into being as the AI actually moves it). A replay's pose is the opposite: every
 * tick of it is already stored as {@link KeyframeChannel} keyframes, and {@link KeyframeChannel#interpolate}
 * evaluates ANY tick (integer or fractional) against BBS' own segment/interpolation math instantly —
 * this class calls that method directly, in a plain synchronous loop over the requested tick range, no
 * event registration, no waiting, no job object at all.</p>
 *
 * <h2>ONLY THE POSE-RELEVANT CHANNELS DRIVE THE RIG — {@code yaw}/{@code pitch}/{@code headYaw}/{@code bodyYaw}</h2>
 * <p>{@code x}/{@code y}/{@code z} (world position) are real channels on every replay but have NO
 * effect on {@link EntityExporter#capturePose}'s output: that method walks {@link ModelPart}-LOCAL
 * pivot/rotation/scale only (bind-pose-relative bone space), exactly like the live-entity path
 * {@code EntityBakeHandler} already uses — a free mob's bake output does not encode its world position
 * either. So this class interpolates {@code headYaw}/{@code bodyYaw}/{@code pitch} (the three channels
 * that actually feed {@link EntityExporter#poseEntity}'s math) and nothing else; sneaking/riding/limb-
 * swing pose-blends stay Blender-side (task 2.6 — "sneaking→geduckte pose-blend" is explicitly a
 * Blender-side F-curve concern, not a Java bone-space one) and are NOT invented here (project hard
 * rule: never re-derive animation math the real methods don't already give you).</p>
 *
 * <h2>POSE MATH — feed ABSOLUTE headYaw/bodyYaw, let {@code poseEntity} do its OWN relative subtraction</h2>
 * <p>{@link EntityExporter#poseEntity} computes the model-relative headYaw itself —
 * {@code headYaw = MathHelper.lerp(tickDelta, entity.prevHeadYaw, entity.headYaw) - bodyYaw} — reading
 * {@code entity.headYaw}/{@code entity.bodyYaw} as ABSOLUTE (world-space) fields, exactly what a real
 * {@code LivingEntity} carries. This class therefore writes the channel's ABSOLUTE {@code headYaw}
 * value straight into {@code posingEntity.headYaw} and lets {@code poseEntity} subtract
 * {@code bodyYaw} on its own. <b>MEASURED BUG (2026-07-17, caught by the ground-truth check below):</b>
 * an earlier version of this class pre-subtracted {@code bodyYaw} itself before storing into
 * {@code posingEntity.headYaw} — copying {@code MobFormRenderer.applyMorphRotation}'s convention
 * ({@code float relativeHeadYaw = source.getHeadYaw() - source.getBodyYaw();}), which is WRONG here:
 * that call site zeroes {@code bodyYaw} and feeds the model's {@code setLivingAngles} DIRECTLY,
 * bypassing {@code poseEntity} entirely — a different calling convention than the one this class uses.
 * Feeding an already-relative value into {@code poseEntity} made it subtract {@code bodyYaw} a SECOND
 * time; against the BRIDGE_TEST_MOB fixture (headYaw keyframes 0/30/-20 @ ticks 0/10/20, bodyYaw
 * 0/-40/15) the live head-bone yaw exactly matched {@code headYawAbsDeg - 2*bodyYawDeg} instead of the
 * correct {@code headYawAbsDeg - bodyYawDeg} — e.g. tick 10 produced 110° (should be 70°). Two
 * independent call sites using the SAME subtraction formula is good evidence the formula itself is
 * right; it does NOT excuse skipping the "which fields does THIS call path expect" question.</p>
 *
 * <h2>THE THROWAWAY POSING ENTITY — {@code EntityType.create(world, request)}, never spawned, never ticked</h2>
 * <p><b>26.2:</b> the call is now {@code create(Level, EntitySpawnRequest)} with
 * {@code ignoreChecks=true} — see the comment at the call site for why the check-running variant
 * would be wrong here (it refuses hostile types on PEACEFUL).</p>
 * <p>A {@link MobForm} only stores a registry id string ({@code mobID}, e.g.
 * {@code "minecraft:villager"}) — it is not itself an {@link Entity}. To reuse
 * {@code EntityExporter}'s (package-private, same-package) {@code resolveRootPart}/{@code poseEntity}/
 * {@code capturePose} trio — which all expect a real {@link LivingEntity} whose live renderer/model can
 * be resolved — this class constructs ONE via {@code Registries.ENTITY_TYPE.get(ResourceLocation).create(world)}.
 * This is not a new trick: {@code MobFormRenderer.ensureEntity()} constructs a mob-form's preview
 * entity with the IDENTICAL call, for the identical reason (a form is not an entity, rendering code
 * needs one). The entity is NEVER {@code world.spawnEntity(...)}'d and NEVER {@code .tick()}'d — its
 * {@code limbAnimator} therefore stays at its constructed rest state (pos=0, speed=0) for every sampled
 * tick, which is exactly the documented "no invented walk-cycle math" boundary above, achieved for free
 * by simply not driving it, rather than by writing (and having to justify) a fake swing formula.</p>
 *
 * <h2>tickDelta=1.0F collapses prev/current — see {@code EntityExporter.poseEntity}'s own doc</h2>
 * <p>{@code poseEntity} always lerps with {@code tickDelta=1.0F}, and {@code MathHelper.lerp(1F, a, b)}
 * is algebraically just {@code b} regardless of {@code a}. So setting ONLY the "current" fields
 * ({@code bodyYaw}/{@code headYaw}/pitch) would already be correct; this class also sets the matching
 * {@code prev*} fields to the same interpolated value (same belt-and-suspenders hygiene
 * {@code MobFormRenderer.applyMorphRotation} itself applies), so nothing downstream that happens to
 * read a {@code prev*} field directly is left holding a stale default.</p>
 *
 * <h2>FORMAT — deliberately byte-for-byte {@code ENTITY_BAKE_RESULT}'s schema</h2>
 * <p>The whole point (plan v2 task 4.3a): "damit Blender-seitig derselbe import-pfad wie bei
 * ENTITY_BAKE greift" (so the Blender-side import path for {@code ENTITY_BAKE_RESULT} handles this
 * unmodified). The reply is {@code {ok, fps, frames:[{tick, "<replayUuid>":{bonePath:[9 floats]}}],
 * error}} — the SAME {@link EntityProtocol} field-key constants ({@code RES_OK}/{@code RES_FPS}/
 * {@code RES_FRAMES}/{@code RES_ERROR}/{@code RES_FRAME_TICK}/{@code TICKS_PER_SECOND}) are reused
 * directly (this class lives in the same {@code com.blenderbridge.entity} package as
 * {@code EntityProtocol}) rather than duplicated as string literals, so the two outputs cannot drift
 * apart on key spelling. The per-uuid bone map is built by the identical loop shape
 * {@code EntityBakeHandler.tick()} uses (same {@code "root"} convention for the empty root path). The
 * replay's OWN uuid ({@link Replay#uuid}) is used as the per-frame identity key — the Python importer
 * treats that key as an opaque actor id already (it never assumes it is a live {@code Entity} uuid), so
 * this is a drop-in identity, not a schema change.</p>
 *
 * <h2>WHICH FILM / WHICH REPLAY</h2>
 * <p>Film resolution (editor first, then any playing film) is DELIBERATELY duplicated here rather than
 * shared — same established convention as {@code ActorExporter}/{@code FilmCameraExporter}/
 * {@code FilmBackup} (see any of those class docs): a future change to one must not silently change the
 * others. Within the resolved film, the requested replay is found by {@link Replay#uuid}. A group
 * replay (no performance of its own) or a replay whose form is not a {@link MobForm} both fail loudly
 * with a clear reason rather than guessing — MobForm is the only form type task 4.3a targets ("auf das
 * 4.1-mob-rig ... anwenden"); a player/{@code ModelForm} actor keeps using the generic humanoid rig
 * this task is explicitly meant to move mob-typed actors AWAY from.</p>
 *
 * <h2>read-only, main-thread-only</h2>
 * <p>Every read here is against already-loaded film/replay/registry state — no world mutation, no
 * render-frame side effect, nothing queued via {@code MinecraftClient.execute}. Runs on the client-tick
 * dispatch thread (the SAME thread {@code ActorExporter}/{@code EntityBakeHandler} already run their
 * reads on), which is exactly where BBS film state may be touched.</p>
 */
public final class ReplayPoseReader
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Request field: which replay (by {@link Replay#uuid}) to read. Required. */
    private static final String REQ_REPLAY_UUID = "replayUuid";

    /** Request field: first tick of the output range (inclusive). Defaults to 0. */
    private static final String REQ_START_TICK = "startTick";

    private ReplayPoseReader()
    {
        /* static-only helper */
    }

    /**
     * Handle a REPLAY_POSE_EXPORT request (called from {@code BlenderBridgeClient.dispatch}'s raw
     * string case). Never throws — every failure path answers immediately with an {@code ok:false}
     * REPLAY_POSE_EXPORT_RESULT rather than leaving the caller waiting.
     */
    public static void handle(JsonObject message)
    {
        try
        {
            sendResult(read(BridgeProtocol.getFields(message)));
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] replay pose export failed", t);
            sendResult(failure(t.getClass().getSimpleName() + ": " + t.getMessage()));
        }
    }

    /**
     * Read a replay's pose-relevant keyframe channels over a tick range and pose the matching mob rig
     * once per output tick. Never throws on its own (failures are returned, not thrown) — every
     * throwing path is left to {@link #handle}'s catch.
     *
     * @return the REPLAY_POSE_EXPORT_RESULT payload; never null, every contract key always present
     */
    private static JsonObject read(JsonObject fields)
    {
        String replayUuid = BridgeProtocol.getFieldString(fields, REQ_REPLAY_UUID, null);

        if (replayUuid == null || replayUuid.isBlank())
        {
            return failure("replayUuid is required");
        }

        long ticksRequested = BridgeProtocol.getLong(fields, EntityProtocol.REQ_TICKS, 20);

        if (ticksRequested <= 0)
        {
            return failure("ticks must be >= 1");
        }

        long startTick = BridgeProtocol.getLong(fields, REQ_START_TICK, 0);

        Minecraft client = Minecraft.getInstance();

        if (client == null || client.level == null)
        {
            return failure("no world loaded (join a world before reading a replay pose)");
        }

        Replay replay = resolveReplay(replayUuid);

        if (replay == null)
        {
            return failure("replay not found for uuid " + replayUuid);
        }

        if (replay.isGroup.get())
        {
            return failure("replay " + replayUuid + " is a group folder — no performance/keyframes of its own");
        }

        Form form = replay.form.get();

        if (!(form instanceof MobForm mobForm))
        {
            return failure("replay " + replayUuid + "'s form is not a MobForm (task 4.3a only reposes mob-rig actors)");
        }

        LivingEntity posingEntity = createPosingEntity(client, mobForm);

        if (posingEntity == null)
        {
            return failure("could not construct a poseable entity for mobID '" + mobForm.mobID.get() + "'");
        }

        ModelPart root = EntityExporter.resolveRootPart(client, posingEntity);

        if (root == null)
        {
            return failure("unsupported model shape for mobID '" + mobForm.mobID.get()
                + "' (only SinglePartEntityModel is supported, same limitation as EntityExporter/EntityBakeHandler)");
        }

        KeyframeChannel<Double> yawChannel = replay.keyframes.yaw;
        KeyframeChannel<Double> pitchChannel = replay.keyframes.pitch;
        KeyframeChannel<Double> headYawChannel = replay.keyframes.headYaw;
        KeyframeChannel<Double> bodyYawChannel = replay.keyframes.bodyYaw;

        JsonArray frames = new JsonArray();

        for (long i = 0; i < ticksRequested; i++)
        {
            long tick = startTick + i;
            float channelTick = (float) tick;

            /* yaw is read for completeness (it is one of the named pose channels — task text) but has
             * no effect on model-local bone capture, same as x/y/z (see class-doc); harmless to read. */
            interp(yawChannel, channelTick);

            float bodyYawDeg = interp(bodyYawChannel, channelTick);
            float headYawAbsDeg = interp(headYawChannel, channelTick);
            float pitchDeg = interp(pitchChannel, channelTick);

            /* entity.headYaw/entity.bodyYaw are ABSOLUTE (world-space) fields — EntityExporter.
             * poseEntity itself computes the model-relative headYaw as
             * "MathHelper.lerp(tickDelta, entity.prevHeadYaw, entity.headYaw) - bodyYaw" internally
             * (class-doc "POSE MATH"/EntityExporter.poseEntity source). Storing an ALREADY-relative
             * value here would make poseEntity subtract bodyYaw a SECOND time — measured live via the
             * BRIDGE_TEST_MOB fixture (2026-07-17): observed head-bone yaw exactly matched
             * headYawAbsDeg - 2*bodyYawDeg instead of headYawAbsDeg - bodyYawDeg, i.e. double
             * subtraction. Feed the ABSOLUTE value straight from the channel and let poseEntity do
             * the (single) subtraction — this is also why MobFormRenderer.applyMorphRotation is NOT
             * the right model here: that call site zeroes bodyYaw and bypasses poseEntity entirely
             * (calls setLivingAngles directly), a different convention than the one this class uses. */
            posingEntity.yBodyRot = bodyYawDeg;
            posingEntity.yBodyRotO = bodyYawDeg;
            posingEntity.yHeadRot = headYawAbsDeg;
            posingEntity.yHeadRotO = headYawAbsDeg;
            posingEntity.setXRot(pitchDeg);
            posingEntity.xRotO = pitchDeg;
            posingEntity.tickCount = (int) tick;

            EntityExporter.poseEntity(client, posingEntity);
            Map<String, float[]> pose = EntityExporter.capturePose(root);

            frames.add(frame(tick, replayUuid, pose));
        }

        JsonObject out = new JsonObject();
        out.addProperty(EntityProtocol.RES_OK, true);
        out.addProperty(EntityProtocol.RES_FPS, EntityProtocol.TICKS_PER_SECOND);
        out.add(EntityProtocol.RES_FRAMES, frames);
        out.addProperty(EntityProtocol.RES_ERROR, "");

        LOGGER.info("[blenderbridge] replay pose export DONE: replayUuid={} mobId={} startTick={} frames={}",
            replayUuid, mobForm.mobID.get(), startTick, frames.size());

        return out;
    }

    /** One interpolated frame: {@code {tick, "<replayUuid>": {bonePath: [9 floats], ...}}} — same
     *  shape {@code EntityBakeHandler.tick()} builds per sampled tick. */
    private static JsonObject frame(long tick, String replayUuid, Map<String, float[]> pose)
    {
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

        JsonObject frame = new JsonObject();
        frame.addProperty(EntityProtocol.RES_FRAME_TICK, tick);
        frame.add(replayUuid, bones);

        return frame;
    }

    /** BBS-interpolation, exactly {@link KeyframeChannel#interpolate(float, Object)} — never a custom
     *  re-implementation (class-doc "KEIN SAMPLING"). Missing/empty channel -&gt; 0F (same fallback
     *  {@code interpolate} itself already returns for an empty channel). */
    private static float interp(KeyframeChannel<Double> channel, float tick)
    {
        if (channel == null)
        {
            return 0F;
        }

        Double value = channel.interpolate(tick, 0D);

        return value == null ? 0F : value.floatValue();
    }

    /**
     * Construct a throwaway, never-spawned, never-ticked {@link LivingEntity} for the given mob form —
     * IDENTICAL call to {@code MobFormRenderer.ensureEntity()} (class-doc "THE THROWAWAY POSING
     * ENTITY"). Returns null (never throws) for an unresolvable/invalid mobID or a non-{@link LivingEntity}
     * type (e.g. a projectile) — no fallback empty-pose is invented, same "fail loud" convention
     * {@code EntityExporter.doExport} already uses for unsupported model shapes.
     */
    private static LivingEntity createPosingEntity(Minecraft client, MobForm mobForm)
    {
        ResourceLocation mobId;

        try
        {
            mobId = new ResourceLocation(mobForm.mobID.get());
        }
        catch (RuntimeException e)
        {
            return null;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(mobId);

        /* Registries.ENTITY_TYPE is a defaulted registry (falls back to minecraft:pig for an unknown
         * id) — an unrecognised mobID would otherwise silently produce a pig rig instead of a clear
         * failure. Round-tripping the id catches that (project hard rule: never silently substitute). */
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(mobId))
        {
            return null;
        }

        /* ⚠️ 26.2: create() nimmt kein blosses Level mehr, sondern Level + EntitySpawnRequest.
         * ignoreChecks=TRUE ist Absicht: die kurze Variante create(Level, EntitySpawnReason)
         * laeuft durch canSpawn(level), und canSpawn prueft in 26.2 zusaetzlich die
         * Schwierigkeit — ein nicht-friedlicher Mob liefert auf PEACEFUL null. In 1.21.1 gab
         * es diese Huerde nicht (nur Feature-Flags). Diese Entity wird NIE gespawnt und NIE
         * geticked (Klassendoc "THE THROWAWAY POSING ENTITY"), also darf sie nicht an einer
         * Spawn-Regel scheitern, die fuer sie gar nicht gilt. Preis: eine per Feature-Flag
         * abgeschaltete Entity-Art ist jetzt konstruierbar statt null — fuer ein reines
         * Posier-Objekt folgenlos. Der EntitySpawnReason selbst hat keine Wirkung (Bytecode
         * geprueft: er erreicht die Factory nie, gelesen wird nur ignoreChecks). */
        Entity entity = type.create(client.level);

        /* 26.2: Wegwerf-Entities brauchen eine Kennung, sonst stirbt der Zeichenweg.
         * Siehe mchorse.bbs_mod.forms.renderers.AnzeigeEntity. */
        mchorse.bbs_mod.forms.renderers.AnzeigeEntity.kennungGeben(entity);

        if (!(entity instanceof LivingEntity living))
        {
            return null;
        }

        /* Same hygiene MobFormRenderer.ensureEntity() applies to its own throwaway preview entity. */
        living.noPhysics = true;

        return living;
    }

    /* ================================================================= *
     *  Replay / film resolution — deliberately duplicated, see class-doc *
     * ================================================================= */

    private static Replay resolveReplay(String replayUuid)
    {
        Film film = getEditorFilm();

        if (film == null)
        {
            film = getPlayingFilm();
        }

        if (film == null)
        {
            return null;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay != null && replayUuid.equals(replay.uuid.get()))
            {
                return replay;
            }
        }

        return null;
    }

    /** peekDashboard() — NOT getDashboard(): the latter builds a dashboard as a side effect, which a
     *  read-only lookup must not do. Null dashboard just means the UI was never opened this session. */
    private static Film getEditorFilm()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

        if (dashboard == null)
        {
            return null;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel == null)
        {
            return null;
        }

        return panel.getData();
    }

    /** The first film currently playing in-world, or null when none is. */
    private static Film getPlayingFilm()
    {
        if (BBSModClient.getFilms() == null)
        {
            return null;
        }

        List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();

        if (controllers == null)
        {
            return null;
        }

        for (BaseFilmController controller : controllers)
        {
            if (controller != null && controller.film != null)
            {
                return controller.film;
            }
        }

        return null;
    }

    /* ================================================================= *
     *  Payload skeleton + wire helper                                    *
     * ================================================================= */

    /** Failure payload. {@code frames} is an empty array, never null — byte-identical key set to a
     *  success payload (and to {@code EntityBakeHandler}'s own failure()), see class-doc "FORMAT". */
    private static JsonObject failure(String reason)
    {
        JsonObject out = new JsonObject();
        out.addProperty(EntityProtocol.RES_OK, false);
        out.addProperty(EntityProtocol.RES_FPS, EntityProtocol.TICKS_PER_SECOND);
        out.add(EntityProtocol.RES_FRAMES, new JsonArray());
        out.addProperty(EntityProtocol.RES_ERROR, reason == null ? "" : reason);
        return out;
    }

    private static void sendResult(JsonObject payload)
    {
        long seq = BlenderBridgeClient.STATE.nextOutboundSeq();

        /* Raw string type, no BridgeProtocol.TYPE_* constant — see class-doc "WHY A NEW,
         * SELF-CONTAINED CLASS" (same convention as FilmCameraExporter/PlayheadSync). */
        JsonObject envelope = BridgeProtocol.envelope("REPLAY_POSE_EXPORT_RESULT", "replay-pose", seq, BridgeProtocol.ORIGIN_BBS);

        /* Replace the empty fields object with the payload directly — same convention as
         * BridgeProtocol.worldExportResult/actorsExportResult/EntityBakeHandler.sendResult. */
        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        BlenderBridgeClient.SERVER.send(envelope);
    }
}
