// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (plan v2 task 1.3b: entity-tracking
// camera clips in the offline sampler).
package com.blenderbridge.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.clips.modifiers.LookClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.util.collection.IntObjectMap;

/**
 * OfflineEntityResolver — plan v2 task 1.3b: make entity-tracking camera clips ({@link EntityClip}
 * subclasses: {@code LookClip}/{@code OrbitClip}/{@code TrackerClip} family) resolve their target
 * during {@link FilmCameraExporter}'s OFFLINE sampling, by feeding the open film's replays into
 * {@link CameraClipContext#entities} exactly the way real playback does — plus the task's stage-1
 * warning channel for everything that still CANNOT resolve offline (never silently wrong, rule 0).
 *
 * <h2>HOW REAL PLAYBACK FILLS {@code context.entities} (measured, source-read 2026-07-17)</h2>
 * <p>{@code BaseFilmController.createEntities()} walks {@code film.replays.getList()} by index
 * {@code i}, and for every ENABLED replay constructs {@code new StubEntity(world)}, sets a COPY of
 * the replay's form, applies {@code replay.keyframes.apply(0, entity)}, collapses the prev* fields
 * onto the current ones, and stores {@code entities.put(i, entity)}. {@code Films.playFilm} then
 * shares that VERY map with the camera evaluator ({@code controller.getContext().entities
 * .putAll(filmController.getEntities())} — {@code Films.java:78}), and the editor runner does the
 * same ({@code UIFilmController.createEntities():387}). Each tick, {@code updateEntities(ticks)}
 * re-applies {@code replay.keyframes.apply(replay.getTick(ticks), entity)}. The camera clips only
 * ever read back {@code lerp(prev*, current*, transition)} of position/headYaw/pitch (LookClip:81,
 * OrbitClip:94/101, TrackerClientClip:1051) plus {@code entities.get(selector)} by REPLAY INDEX
 * (EntityClip:47). This class replays exactly that recipe against the exporter's own context —
 * same {@code StubEntity}, same {@code keyframes.apply}, same index keying — instead of inventing
 * a parallel resolution scheme.</p>
 *
 * <h2>WHAT IS DELIBERATELY NOT REPLICATED (and why it is safe to skip)</h2>
 * <ul>
 * <li>{@code replay.applyClientActions(...)} — client ACTION clips (sounds, particles, item
 *     swings): world-visible side effects an offline export must never trigger; none of them feed
 *     the camera math above.</li>
 * <li>{@code updateEntityAndForm()} ({@code entity.update()} + {@code form.update()}) — advances
 *     limb animators/form timers for RENDERING; the camera math reads none of that state.</li>
 * <li>{@code MorphMountSync.assignMountTargets/syncMountedState} — touch LIVE render entities
 *     ({@code ensureRenderEntity()}, {@code startRiding} on in-world actors). Skipping them means a
 *     tracked replay that RIDES another gets its position from its own keyframe channels instead of
 *     the mount-follow composition — flagged as a warning (see below), not silently wrong.</li>
 * <li>{@code MobCemPoseCapture.syncReplay} — CEM pose-capture registry, render concern.</li>
 * <li>{@code spawnReplayStepSound/spawnSprintParticles} — audible/visible world effects.</li>
 * </ul>
 *
 * <h2>prev == current COLLAPSE — why the sampled tick is exact</h2>
 * <p>The sampler runs with {@code transition = 0}, and every entity read in the clip code is
 * {@code Lerps.lerp(prev, current, transition)} — with keyframes.apply's own prev values that would
 * yield the value at {@code tick-1}, one tick late. {@code createEntities()} itself collapses
 * prev onto current after its initial apply (lines 1491-1498) for exactly this "no inter-tick
 * history exists" situation; this class does the same collapse EVERY sampled tick, so a frame at
 * tick T reads the channel value at exactly T — consistent with how the camera position clips
 * themselves evaluate at exactly {@code relativeTick + 0}.</p>
 *
 * <h2>STAGE-1 WARNINGS (task 1.3b) — emitted for what offline (or live!) cannot resolve</h2>
 * <p>Collected as {@code {type, tickStart, tickEnd, reason}} objects, returned to the Blender
 * add-on in the {@code FILM_CAMERA_EXPORT_RESULT.warnings} array (always present, usually empty):</p>
 * <ul>
 * <li>entity-mode clip with {@code selector < 0} — no target chosen; the clip no-ops live too,
 *     but the user should know WHY the camera holds still during it;</li>
 * <li>selector pointing at a disabled/out-of-range replay — not spawned during live playback
 *     either, same "matches live, but say it" reasoning;</li>
 * <li>tracked replay whose {@code riding} channel is ever active — mount-follow is not replicated
 *     offline (see list above), position may differ from live while mounted;</li>
 * <li>a clip whose offline evaluation THROWS ({@link #recordClipFailure}) — caught per-clip by the
 *     exporter, position holds, export continues (stage-1 contract: warn, never abort). Candidate:
 *     {@code TrackerClientClip} in bone-group mode calls {@code FormUtilsClient.getRenderer(form)
 *     .collectMatrices(...)}, whose render-side assumptions are unverified offline.</li>
 * </ul>
 * <p>A {@code LookClip} in {@code atBlock}/{@code forward} mode is NOT warned about — those modes
 * do not reference an entity at all.</p>
 *
 * <h2>TRACKER RENDER-STATICS BRACKET ({@link #finish()})</h2>
 * <p>{@code TrackerClientClip.applyClip} sets the LIVE render statics {@code renderHand}/
 * {@code renderHud}/{@code currentEntity} as a side effect of plain evaluation — pre-1.3b the
 * sampler could already flip {@code renderHand} for a showHand-tracker clip (entity == null branch
 * still sets it), leaking a 1-frame hand-render flash into the running game. {@link #arm} saves the
 * three statics, {@link #finish()} (exporter's {@code finally}) restores them. Safe without locking:
 * export runs on the client-tick thread, render reads them between ticks, and the whole
 * sample loop completes within one tick.</p>
 *
 * <h2>WHY A NEW, SELF-CONTAINED CLASS</h2>
 * <p>Same deploy-window reasoning as every sibling ({@code RegionClamp}/{@code ChunkCoverage}/
 * {@code ExportCorrelation}): {@code FilmCameraExporter} is already loaded in the live JVM, so it
 * only gets BODY edits (JDI hotswap); all NEW state and methods live here, in a brand-new
 * lazy-loadable class. Instance-based (one per export) — no statics, no {@code <clinit>} trap,
 * nothing survives the export.</p>
 */
final class OfflineEntityResolver
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Full replay list of the film, INDEX-ALIGNED with the entity map keys (the selector space). */
    private final List<Replay> replays;

    /** The context's OWN map ({@link CameraClipContext#entities}) — shared, not copied, mirroring
     * how {@code Films.playFilm} shares the film controller's map with the camera context. */
    private final IntObjectMap<IEntity> entities;

    private final JsonArray warnings = new JsonArray();

    /** Clips that already produced a failure warning — warn once per clip, not once per tick
     * (a 300-tick clip that throws every tick must not emit 300 identical warnings). Identity
     * semantics: clip VALUE equality is meaningless here, the instance IS the timeline element. */
    private final Set<Clip> failedClips = Collections.newSetFromMap(new IdentityHashMap<>());

    /* TrackerClientClip render-statics bracket (see class-doc). */
    private final boolean savedRenderHand;
    private final boolean savedRenderHud;
    private final IEntity savedCurrentEntity;

    private OfflineEntityResolver(List<Replay> replays, IntObjectMap<IEntity> entities)
    {
        this.replays = replays;
        this.entities = entities;

        this.savedRenderHand = TrackerClientClip.renderHand;
        this.savedRenderHud = TrackerClientClip.renderHud;
        this.savedCurrentEntity = TrackerClientClip.currentEntity;
    }

    /**
     * Build the offline entity map for the given film into {@code context.entities} (the same
     * map instance the clips will read) and pre-scan the film's camera clips for stage-1 warnings.
     * Never throws on its own; a null/empty replay list simply yields an empty map (every
     * entity-mode clip then warns, which is exactly the pre-1.3b behaviour made visible).
     */
    static OfflineEntityResolver arm(Film film, CameraClipContext context)
    {
        List<Replay> replays = film.replays.getList();
        OfflineEntityResolver resolver = new OfflineEntityResolver(replays, context.entities);

        /* Same uuid/id double-keying BaseFilmController.createEntities uses for its replayMap —
         * needed only for the group-chain enabled check below. */
        Map<String, Replay> replayMap = new HashMap<>();

        for (Replay replay : replays)
        {
            if (replay != null)
            {
                replayMap.put(replay.uuid.get(), replay);
                replayMap.put(replay.getId(), replay);
            }
        }

        Level world = Minecraft.getInstance() == null ? null : Minecraft.getInstance().level;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);

            if (!isReplayEnabled(replay, replayMap))
            {
                continue;
            }

            /* Verbatim recipe of BaseFilmController.createEntities (1486-1500) minus the
             * render-side syncReplay call — see class-doc. */
            IEntity entity = new StubEntity(world);

            entity.setForm(FormUtils.copy(replay.form.get()));
            replay.keyframes.apply(0, entity);
            collapsePrev(entity);

            resolver.entities.put(i, entity);
        }

        resolver.prescan(film);

        return resolver;
    }

    /**
     * Pose every offline entity at the given FILM tick — the offline stand-in for
     * {@code BaseFilmController.updateEntities(ticks)}'s keyframe half. Call once per sampled
     * tick, BEFORE the clips are applied.
     */
    void applyTick(int tick)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = i >= 0 && i < this.replays.size() ? this.replays.get(i) : null;

            if (replay == null || entity == null)
            {
                continue;
            }

            /* replay.getTick applies the replay's own looping window, same as updateEntities. */
            replay.keyframes.apply(replay.getTick(tick), entity);
            collapsePrev(entity);

            /* The offline form copies were never drawn, so their ModelFormRenderer has no animator
             * yet - and without one collectMatrices returns no bones: a head tracker resolved no
             * frame and the camera stayed at 0/0/0 with no warning (measured 2026-10-14,
             * Moggityfirst). Live playback creates the animator on first render; do it here before
             * any clip reads bones. Body-only on purpose: the CF instance only takes body swaps. */
            Form form = entity.getForm();

            if (form != null && FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer)
            {
                renderer.ensureAnimator(0F);

                if (renderer.getAnimator() == null && tick == 0)
                {
                    JsonObject warning = new JsonObject();

                    warning.addProperty("type", "replay");
                    warning.addProperty("replay", i);
                    warning.addProperty("reason", "model not loaded offline: tracker/anchor bones unavailable");
                    this.warnings.add(warning);
                    LOGGER.warn("[blenderbridge] film camera export: replay {} has no posable model offline", i);
                }
            }
        }
    }

    /**
     * A clip threw during offline evaluation (caught per-clip by the exporter): warn ONCE for the
     * clip, log the stack once, let the export continue — stage-1 contract, never abort, never
     * silently swallow.
     */
    void recordClipFailure(Clip clip, Throwable error)
    {
        if (!this.failedClips.add(clip))
        {
            return;
        }

        LOGGER.error("[blenderbridge] film camera export: clip failed to evaluate offline (export continues, position holds)", error);

        this.warn(clip, "offline evaluation failed (" + error.getClass().getSimpleName() + ": " + error.getMessage()
            + ") — camera holds the previous pose wherever only this clip is active");
    }

    /** The collected stage-1 warnings — always an array, empty when everything resolved. */
    JsonArray getWarnings()
    {
        return this.warnings;
    }

    /**
     * Restore the {@code TrackerClientClip} render statics captured at construction — MUST run in
     * the exporter's {@code finally} (see class-doc "TRACKER RENDER-STATICS BRACKET").
     */
    void finish()
    {
        TrackerClientClip.renderHand = this.savedRenderHand;
        TrackerClientClip.renderHud = this.savedRenderHud;
        TrackerClientClip.currentEntity = this.savedCurrentEntity;
    }

    /* ================================================================= *
     *  Internals                                                         *
     * ================================================================= */

    /** Verbatim BaseFilmController.isReplayEnabled (private there): enabled flag + every group
     * folder in the replay's group chain must be enabled too. */
    private static boolean isReplayEnabled(Replay replay, Map<String, Replay> replayMap)
    {
        if (replay == null || !replay.enabled.get())
        {
            return false;
        }

        if (!replay.group.get().isEmpty())
        {
            for (String uuid : replay.group.get().split("/"))
            {
                Replay groupReplay = replayMap.get(uuid);

                if (groupReplay != null && !groupReplay.enabled.get())
                {
                    return false;
                }
            }
        }

        return true;
    }

    /** The prev==current collapse createEntities applies after its initial keyframes.apply —
     * here applied after EVERY apply, so a sampled tick reads exact channel values (class-doc). */
    private static void collapsePrev(IEntity entity)
    {
        entity.setPrevX(entity.getX());
        entity.setPrevY(entity.getY());
        entity.setPrevZ(entity.getZ());

        entity.setPrevYaw(entity.getYaw());
        entity.setPrevHeadYaw(entity.getHeadYaw());
        entity.setPrevPitch(entity.getPitch());
        entity.setPrevBodyYaw(entity.getBodyYaw());
    }

    /** Stage-1 pre-scan over the film's camera clips — see class-doc for the exact warning set. */
    private void prescan(Film film)
    {
        for (Clip clip : film.camera.get())
        {
            if (!(clip instanceof EntityClip entityClip) || !clip.enabled.get())
            {
                continue;
            }

            if (entityClip instanceof LookClip look && (look.atBlock.get() || look.forward.get()))
            {
                continue;
            }

            int selector = entityClip.selector.get();

            if (selector < 0)
            {
                this.warn(clip, "no target entity selected (selector=" + selector
                    + ") — the clip cannot resolve a target, offline or live");
            }
            else if (!this.entities.containsKey(selector))
            {
                this.warn(clip, selector < this.replays.size()
                    ? "target replay " + selector + " is disabled — not spawned during live playback either"
                    : "selector " + selector + " is out of range (film has " + this.replays.size() + " replays)");
            }
            else if (usesRiding(this.replays.get(selector)))
            {
                this.warn(clip, "target replay " + selector + " uses riding/mounting — offline sampling reads its own"
                    + " keyframe channels only (mount-follow not replicated), position may differ from live while mounted");
            }
        }
    }

    /** True when the replay's riding channel ever activates (any keyframe value != 0) — the
     * cheap static form of ReplayKeyframes.getRidingAt's "active" check, without evaluating
     * every tick. */
    private static boolean usesRiding(Replay replay)
    {
        if (replay == null)
        {
            return false;
        }

        /* `riding` is a newer BBS-CML keyframe channel — the jar this fork compiles against has no
         * such field on ReplayKeyframes (measured). Read it reflectively: absent means no replay in
         * this build can ride anything, so there is no riding-target to warn about and the whole
         * check answers false. Present (newer base) and the original per-keyframe scan runs. */
        Object channel = com.blenderbridge.actors.compat.MaterialCompat.readObject(replay.keyframes, "riding");

        if (!(channel instanceof mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> riding))
        {
            return false;
        }

        for (Object obj : riding.getKeyframes())
        {
            Object value = ((Keyframe<?>) obj).getValue();

            if (value instanceof Number number && number.doubleValue() != 0D)
            {
                return true;
            }
        }

        return false;
    }

    private void warn(Clip clip, String reason)
    {
        JsonObject warning = new JsonObject();

        warning.addProperty("type", clip.getClass().getSimpleName());
        warning.addProperty("tickStart", clip.tick.get());
        warning.addProperty("tickEnd", clip.tick.get() + clip.duration.get());
        warning.addProperty("reason", reason);

        this.warnings.add(warning);

        LOGGER.warn("[blenderbridge] film camera export warning: {} @ {}-{}: {}",
            clip.getClass().getSimpleName(), clip.tick.get(), clip.tick.get() + clip.duration.get(), reason);
    }
}
