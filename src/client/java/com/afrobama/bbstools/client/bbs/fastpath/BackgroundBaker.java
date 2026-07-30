// Coded by Afrobama/Claude — Army System: decoupled per-unique-animation baker.
//
// WHY this exists: the old bg-bake lived inside BaseFilmController.render (the
// render-loop bypass). That path only runs during PLAYBACK — when idle in the
// editor the game renders at 400+fps but render() never fires, so AFK baking
// did nothing. This baker runs on ClientTickEvents (fires every client tick,
// idle OR playing) so the cache fills while you wait, DaVinci-style.
//
// PER-UNIQUE-ANIMATION sharing (the scale fix): identical replays (3000 copies
// of one villager) produce identical MODEL-LOCAL bone matrices (world position
// is applied separately in the shader, so it doesn't count). We fingerprint
// each replay by sampling its bone output at a few ticks; identical fingerprints
// share ONE baked frame-map, and only the representative is actually baked — the
// other 2999 read it for free. Different models → different fingerprints → each
// baked once (correct for varied-model crowds; those scale by unique count, not
// replay count, and benefit from the always-on tick budget).
//
// Runs on the client thread (same thread as render → no model race), time-boxed
// per tick so it never freezes the editor.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import io.netty.util.collection.IntObjectMap;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BackgroundBaker
{
    private BackgroundBaker() {}

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("bbstools/bg-bake");

    /** Active editor/world film controller (stashed by BaseFilmControllerActiveStashMixin
     *  on update(); persists while a film is loaded so the baker works idle). */
    private static volatile BaseFilmController ACTIVE = null;

    /** replay UUID -> its final (bone-verified) signature (memoized). Keyed by
     *  the STABLE replay uuid, NOT the entity: BBS rebuilds entities constantly
     *  (createEntities fires on every film edit), and an entity-keyed WeakHashMap
     *  lost its memo on every rebuild → the baker re-ran the expensive data-hash
     *  toData for all 3000 replays every tick, burning the whole budget on
     *  re-hashing and never advancing the bake (froze at half). uuid is stable
     *  across rebuilds → hashed once, ever. */
    private static final Map<String, Object> UUID_SIG = new java.util.HashMap<>();

    /** cheap data-hash (String) -> resolved bone signature. Lets byte-identical
     *  replays (true duplicates) reuse a signature with ZERO bone extraction,
     *  while the bone signature itself MERGES data-groups that animate
     *  identically (e.g. the same animation at different world positions →
     *  different data, identical model-local bones → one shared bake). */
    private static final Map<String, Object> DATA_TO_SIG = new java.util.HashMap<>();

    /** replay UUID -> last seen data-hash (edit watch for auto re-bake). */
    private static final Map<String, String> UUID_DATAHASH = new java.util.HashMap<>();

    /** Debounce: the replay whose values are changing + when they last changed.
     *  The re-bake fires only once editing settles (BAKE_REBAKE_DEBOUNCE_MS). */
    private static String bbstools$editPendingUuid = null;
    private static long   bbstools$editLastChangeMs = 0L;

    private static int cursor = 0;
    /** Film-tick stall tracking → reliable idle detection (see tick()). */
    private static int lastFilmTick = Integer.MIN_VALUE;
    private static int stallTicks = 0;
    private static volatile boolean REGISTERED = false;
    private static long bbstools$lastErrMs = 0L;

    /** Baked-stable fast-out state. When the last full scan found EVERY replay
     *  fully baked, this is set; subsequent ticks then skip the whole 3000-job
     *  rebuild + bind scan and only O(1)-watch the selected for edits. Cleared on
     *  any edit (bake invalidated) or when the replay count changes. */
    private static boolean bbstools$bakedStable = false;
    private static int     bbstools$stableCount = -1;
    /** The entities map the stable flag was armed for. The bake cache is keyed by
     *  IEntity INSTANCE, so when BBS re-creates the entities (every film edit, and
     *  on the editor->in-world playback switch) the new instances are uncached →
     *  hide-until-ready hides them. Requiring the SAME map in the idle fast-out
     *  forces a re-bake on any entity-set change, so in-world playback re-bakes and
     *  the crowd becomes visible (was: only replays.size() checked → stale skip). */
    private static Object  bbstools$stableEntities = null;

    /** Cached bakeable-job list. Rebuilding it every tick (getRenderer/getModel ×
     *  N) was O(N) UNBUDGETED and ran for the WHOLE bake-scan phase — at 9000
     *  replays that alone is hundreds of ms/tick → the multi-minute freeze after a
     *  bulk duplicate. The job set only changes when the entities map changes
     *  (dupe/load/edit), so cache it keyed by that map identity + size; rebuild
     *  only then. Invalidated on edit (renderer may change) and reset(). */
    private static java.util.List<Job> bbstools$cachedJobs = null;
    private static Object bbstools$jobsCacheKey  = null;
    private static int    bbstools$jobsCacheSize = -1;

    public static void setActive(BaseFilmController c) { ACTIVE = c; }

    /** Drop memoized signatures (call when the bake cache is cleared). */
    public static synchronized void reset()
    {
        UUID_SIG.clear();
        DATA_TO_SIG.clear();
        UUID_DATAHASH.clear();
        bbstools$bakedStable = false;
        bbstools$stableCount = -1;
        bbstools$stableEntities = null;
        bbstools$cachedJobs = null;
        bbstools$jobsCacheKey = null;
        bbstools$jobsCacheSize = -1;
        bbstools$editPendingUuid = null;
        bbstools$editLastChangeMs = 0L;
        cursor = 0;
        lastFilmTick = Integer.MIN_VALUE;
        stallTicks = 0;
    }

    public static void register()
    {
        if (REGISTERED) return;
        REGISTERED = true;
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            /* One-shot UI refresh for a batched bulk duplicate (the per-iter UI was
             * cancelled to kill the O(N^2) freeze; flush it once here). */
            try { BulkDupeFlush.flush(); } catch (Throwable ignored) {}

            /* Drive the createEntities coalesce/drain FIRST, independent of
             * ENABLE_BG_BAKE (tick() early-returns when baking is off, but the
             * per-tick guard MUST still reset or coalesced bulk dupes would stay
             * dirty forever). If the crowd is still forming, skip baking this tick. */
            BaseFilmController active = ACTIVE;
            if (active instanceof com.afrobama.bbstools.client.bbs.fastpath.IIncrementalEntities ie)
            {
                try
                {
                    if (ie.bbstools$onClientTick())
                    {
                        return;
                    }
                }
                catch (Throwable t)
                {
                    long now = System.currentTimeMillis();
                    if (now - bbstools$lastErrMs > 5000L)
                    {
                        bbstools$lastErrMs = now;
                        org.slf4j.LoggerFactory.getLogger("bbstools/bg-bake")
                            .warn("[bg-bake] onClientTick threw: {}", t.toString(), t);
                    }
                }
            }

            try { tick(); }
            catch (Throwable t)
            {
                /* NEVER swallow silently — a thrown tick used to freeze the
                 * bake with zero trace. Rate-limited so a persistent fault
                 * can't spam. */
                long now = System.currentTimeMillis();
                if (now - bbstools$lastErrMs > 5000L)
                {
                    bbstools$lastErrMs = now;
                    org.slf4j.LoggerFactory.getLogger("bbstools/bg-bake")
                        .warn("[bg-bake] tick threw (bake may stall): {}", t.toString(), t);
                }
            }
        });
    }

    private record Job(int index, Replay replay, IEntity entity,
                       ModelFormRenderer renderer, ModelInstance instance, ModelForm form) {}

    private static void tick()
    {
        if (!BBSPerfConfig.ENABLE_BG_BAKE) return;

        BaseFilmController c = ACTIVE;
        if (c == null || c.film == null) return;

        /* Chunked entity-creation drain + bulk-dupe coalesce flush moved to
         * IIncrementalEntities.bbstools$onClientTick, called from the tick driver
         * BEFORE this method (and before the ENABLE_BG_BAKE gate). If the crowd is
         * still forming it returns true there and we never reach tick(). */

        IntObjectMap<IEntity> entities = c.getEntities();
        if (entities == null || entities.isEmpty()) return;

        List<Replay> replays = c.film.replays.getList();
        if (replays == null || replays.isEmpty()) return;

        /* Count gate: don't bake small scenes (vanilla renders them correctly + fast). If we were
         * baking and the crowd shrank below the threshold (e.g. deleted replays), drop the bake +
         * completeness flag so no stale baked/instanced state survives into the now-small scene. The
         * bypass also disengages below the threshold, so the instanced draw stops; clearing here frees
         * the bone cache and flips crowdBakeComplete so the sim-skips disengage too. */
        if (replays.size() < BBSPerfConfig.ARMY_REPLAY_THRESHOLD)
        {
            if (BBSPerfConfig.crowdBakeComplete || AnimationBakeCache.signatureGroups() > 0)
            {
                AnimationBakeCache.clear();
                BBSPerfConfig.crowdBakeComplete = false;
            }
            bbstools$bakedStable = false;
            BBSPerfConfig.bgBakeActive = false;
            return;
        }

        int camDur;
        try { camDur = c.film.camera.calculateDuration(); }
        catch (Throwable t) { camDur = 0; }

        int liveTick = c.getTick();
        int fullLight = net.minecraft.util.LightCoordsUtil.pack(15, 15);

        /* IDLE FAST-OUT. Once the previous full scan confirmed EVERY replay is
         * baked and the scene is stable (same replay count, not mid-edit), the
         * per-tick 3000-job rebuild (getRenderer/getModel × N) + synchronized
         * AnimationBakeCache.bind × N + firstUncached scan is pure waste — it
         * bakes nothing (bgBakeTicks delta = 0) yet costs ~10% of the render
         * thread (bind alone 6.6%). Skip it; do only the O(1) edit-watch on the
         * SELECTED replay so its cache bar still turns red on a keyframe change
         * (which clears the flag → full baking resumes next tick → re-bake on
         * deselect). Works for unique replays too: stability is per-scan, not
         * per-dedup. */
        if (bbstools$bakedStable
            && replays.size() == bbstools$stableCount
            && entities == bbstools$stableEntities
            && System.currentTimeMillis() >= BBSPerfConfig.editLiveUntilMs)
        {
            bbstools$watchSelectedEdit(replays, entities);
            BBSPerfConfig.bgBakeIdleSkips++;
            BBSPerfConfig.bgBakeActive = false;
            return;
        }

        /* Collect bakeable jobs (non-actor ModelForm + cubic model). CACHED: the
         * job set only changes when the entities map changes (dupe/load) — so reuse
         * it across ticks. Rebuilding O(N) every tick during the bake-scan phase was
         * the bulk-duplicate freeze (9000 getRenderer/getModel/tick, unbudgeted). */
        List<Job> jobs;
        if (bbstools$cachedJobs != null
            && entities == bbstools$jobsCacheKey
            && bbstools$jobsCacheSize == entities.size())
        {
            jobs = bbstools$cachedJobs;
        }
        else
        {
            jobs = new ArrayList<>(entities.size());
            for (Map.Entry<Integer, IEntity> entry : entities.entrySet())
            {
                IEntity entity = entry.getValue();
                int i = entry.getKey();
                if (entity == null || i < 0 || i >= replays.size()) continue;
                Replay replay = replays.get(i);
                if (replay == null || replay.actor.get()) continue;

                Form form = entity.getForm();
                if (!(form instanceof ModelForm modelForm)) continue;

                Object r = FormUtilsClient.getRenderer(form);
                if (!(r instanceof ModelFormRenderer renderer)) continue;
                ModelInstance instance = renderer.getModel();
                if (instance == null || !(instance.model instanceof Model)) continue;

                jobs.add(new Job(i, replay, entity, renderer, instance, modelForm));
            }
            bbstools$cachedJobs = jobs;
            bbstools$jobsCacheKey = entities;
            bbstools$jobsCacheSize = entities.size();
        }

        int n = jobs.size();
        if (n == 0) return;

        /* --- Edit detection → open the full-native render window ---
         * Watch ONLY the currently-selected replay's data-hash (uuid published by
         * the getCurrentEntity mixin → O(1) find + one toData per tick). On any
         * change to its data, stamp editLiveUntilMs = now + EDIT_LIVE_WINDOW_MS.
         * While that window is open the render-loop bypass returns early and BBS
         * renders the WHOLE film natively (live + correct, no bake). After it
         * lapses the baked fast-path resumes. This is the user-requested
         * "bearbeiten = komplett live, auswählen = baked" — no carve-out, no
         * re-bake, no per-entity re-pose to fight BBS. */
        if (BBSPerfConfig.ENABLE_EDIT_LIVE)
        {
            long now = System.currentTimeMillis();
            String curUuid = BBSPerfConfig.currentReplayUuid;
            if (curUuid != null)
            {
                for (int ji = 0; ji < n; ji++)
                {
                    Job job = jobs.get(ji);
                    String u = null;
                    try { u = job.replay.uuid.get(); } catch (Throwable ig) {}
                    if (u == null || !curUuid.equals(u)) continue;

                    /* r74: edit-watch uses the REPLAY-only hash (NO model-id suffix).
                     * r73 added " m="+instance.id to the JOB overload for bake GROUPING;
                     * if instance.id is a per-tick-unstable handle, feeding it into the
                     * edit-watch made the hash differ every tick → false "edited" →
                     * endless re-bake (measured: bakeAutoRebakes climbs ~1/tick, stops
                     * when EDIT_LIVE off). The edit-watch only needs to detect REAL
                     * data edits to one replay, where the replay-only hash is correct
                     * and stable. Grouping still uses the Job overload (line ~438). */
                    String h = bbstools$dataHash(job.replay);
                    String prev = UUID_DATAHASH.put(curUuid, h);
                    if (prev != null && !prev.equals(h))
                    {
                        /* Selected replay was edited → its bake is stale. Invalidate
                         * NOW so its cache bar turns RED. We DON'T re-bake it while it
                         * stays selected — the bake loop skips the current selection,
                         * and it renders LIVE the whole time (render3D skip). It only
                         * re-bakes once the user DESELECTS / picks another replay
                         * (then it's no longer skipped → bakes GREEN). This is exactly
                         * the requested "selected = never rebake; deselect = rebake",
                         * and it removes the rebake-while-editing fps drop.
                         * unbind() de-merges this one replay from the shared dedup map
                         * WITHOUT freeing the bones the identical villagers read
                         * (invalidateEntity would corrupt them → negative counts);
                         * UUID_SIG.remove forces a fresh signature/group on re-bake. */
                        try { AnimationBakeCache.unbind(job.entity); } catch (Throwable ig) {}
                        /* World-transform bake: the edited replay's cached transforms are stale →
                         * drop them so it re-captures fresh after deselect (stays live meanwhile). */
                        try { WorldTransformCache.invalidateEntity(job.entity); } catch (Throwable ig) {}
                        UUID_SIG.remove(curUuid);
                        BBSPerfConfig.editLiveUntilMs = now + BBSPerfConfig.EDIT_LIVE_WINDOW_MS;
                        bbstools$editLastChangeMs = now;
                        BBSPerfConfig.bakeAutoRebakes++;
                        bbstools$bakedStable = false; /* edit → resume full baking */
                    }
                    break;
                }
            }
        }

        boolean dedup = BBSPerfConfig.ENABLE_BG_BAKE_DEDUP;
        /* Idle = NOT actively playing → big budget so it finishes in ~1-2s.
         * c.paused ALONE is unreliable: sitting in the replay editor AFK, BBS
         * reports paused=false (budget stayed at 8ms → bake crawled / froze at
         * half). So also treat a film tick that hasn't ADVANCED for 2 client
         * ticks as idle. Real playback advances getTick() every tick → not idle
         * → moderate 8ms budget (no stutter). */
        int filmTick = c.getTick();
        if (filmTick == lastFilmTick) { if (stallTicks < 1000) stallTicks++; }
        else { stallTicks = 0; lastFilmTick = filmTick; }
        boolean idle = c.paused || stallTicks >= 2;
        long budgetMs = Math.max(1L, idle ? BBSPerfConfig.BG_BAKE_MS_PER_TICK_IDLE
                                          : BBSPerfConfig.BG_BAKE_MS_PER_TICK);
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;

        /* Publish live state so the status line shows whether the idle budget
         * actually engages while AFK + how many distinct data-variants exist. */
        BBSPerfConfig.bgBakeActive = true;
        BBSPerfConfig.bgBakeIdle = idle;
        BBSPerfConfig.bgBakeBudgetMs = budgetMs;
        BBSPerfConfig.bgBakeUniqueData = DATA_TO_SIG.size();

        int start = ((cursor % n) + n) % n;
        List<Job> touched = new ArrayList<>();
        /* Signatures already baked THIS tick — so identical replays are baked
         * once per tick by whichever entity is active (no persistent rep). */
        java.util.HashSet<Object> bakedSigs = dedup ? new java.util.HashSet<>() : null;

        /* Baked-stable tracking: did we scan EVERY job this tick, and was every
         * one already fully baked? Only then is it safe to arm the idle fast-out. */
        int bbstools$scanned = 0;
        boolean bbstools$sawUnbaked = false;

        for (int off = 0; off < n && System.nanoTime() < deadline; off++)
        {
            Job job = jobs.get((start + off) % n);
            cursor = (start + off);
            bbstools$scanned++;

            /* Skip the selected replay's re-bake ONLY while it's being ACTIVELY
             * edited (within the edit window, re-armed on every value change). It
             * renders live the whole time (render3D skip), so during the drag its
             * bar stays RED and we don't waste a bake. Once editing STOPS (no change
             * for EDIT_LIVE_WINDOW_MS), the window lapses → it bakes here → bar goes
             * GREEN, even though the replay is still selected. (Earlier this skipped
             * the selection PERMANENTLY → bar stuck red forever because deselecting a
             * keyframe doesn't deselect the replay.) */
            if (BBSPerfConfig.currentReplayUuid != null
                && System.currentTimeMillis() < BBSPerfConfig.editLiveUntilMs)
            {
                try { if (BBSPerfConfig.currentReplayUuid.equals(job.replay.uuid.get())) continue; }
                catch (Throwable ig) {}
            }

            int expected = ReplayBakeUtil.expectedTicks(job.replay, camDur);

            /* Fingerprint (memoized) + bind to the shared per-animation map. */
            Object sig = null;
            if (dedup)
            {
                String uuid = job.replay.uuid.get();
                sig = (uuid != null) ? UUID_SIG.get(uuid) : null;
                if (sig == null)
                {
                    /* TWO-LEVEL signature.
                     * (1) Cheap data-hash (form+keyframes+properties) → no
                     *     extract. Byte-identical replays (true duplicates)
                     *     resolve instantly via DATA_TO_SIG, zero animator runs.
                     * (2) First time a given data-hash appears, VERIFY by actual
                     *     model-local bones (sampled). This MERGES data-groups
                     *     that animate identically but differ in bone-irrelevant
                     *     data — e.g. the same crowd member at 699 different
                     *     world positions: 699 data-hashes, but identical bones
                     *     (extractFlat is model-local) → ONE shared bake.
                     * Old data-only signature over-segmented (699 groups for a
                     * unique=1 crowd → 699× the bake work, ~14 min). Bone-verify
                     * collapses it to the true unique-animation count. */
                    String dataHash = bbstools$dataHash(job);
                    sig = DATA_TO_SIG.get(dataHash);
                    if (sig == null)
                    {
                        sig = bbstools$boneSignature(job, expected, fullLight);
                        DATA_TO_SIG.put(dataHash, sig);
                        touched.add(job); /* posed while sampling → restore later */
                    }
                    if (uuid != null) UUID_SIG.put(uuid, sig);
                }
                AnimationBakeCache.bind(job.entity, sig);
                if (bakedSigs.contains(sig)) continue; /* this animation handled already */
            }

            int first = AnimationBakeCache.firstUncachedTick(job.entity, expected);
            if (first < 0)
            {
                if (sig != null) bakedSigs.add(sig);
                continue; /* fully baked */
            }

            bbstools$sawUnbaked = true; /* something still needs baking → not stable */

            boolean any = false;
            for (int t = first; t < expected && System.nanoTime() < deadline; t++)
            {
                if (AnimationBakeCache.get(job.entity, t) != null) continue; /* skip cached */
                bbstools$pose(job, t);
                FastPathBatch.Submission sub = new FastPathBatch.Submission(
                    job.renderer, job.entity, job.instance, job.form,
                    IDENTITY, fullLight, OverlayTexture.NO_OVERLAY,
                    mchorse.bbs_mod.utils.colors.Colors.WHITE, 0F, false);
                if (Tier2hInstancedDraw.bakeRecord(sub))
                {
                    BBSPerfConfig.bgBakeTicks++;
                    any = true;
                }
            }
            if (any) touched.add(job);
            if (sig != null) bakedSigs.add(sig);
        }

        cursor++;
        BBSPerfConfig.bgBakeExpectedSample = touched.isEmpty() ? BBSPerfConfig.bgBakeExpectedSample
            : ReplayBakeUtil.expectedTicks(touched.get(0).replay, camDur);
        BBSPerfConfig.bgBakeSignatureGroups = AnimationBakeCache.signatureGroups();

        /* Restore every entity we posed back to the live film tick so the
         * editor preview is unaffected. */
        for (Job job : touched)
        {
            bbstools$pose(job, job.replay.getTick(liveTick));
            BBSPerfConfig.bgBakeRestores++;
        }

        /* Arm the idle fast-out ONLY if this tick scanned every job, none needed
         * baking, nothing was posed, and we're not mid-edit (the selected is
         * skipped during the edit window → don't mark stable while it's unbaked).
         * Next tick then skips the whole scan until an edit / count-change. */
        if (bbstools$scanned >= n && !bbstools$sawUnbaked && touched.isEmpty()
            && System.currentTimeMillis() >= BBSPerfConfig.editLiveUntilMs)
        {
            bbstools$bakedStable = true;
            bbstools$stableCount = replays.size();
            bbstools$stableEntities = entities;
        }
    }

    /** O(1) edit-watch used by the idle fast-out: find the SELECTED replay by
     *  uuid, hash its data, and if it changed since last tick invalidate its bake
     *  (cache bar → red), arm the edit-live window, and clear the stable flag so
     *  full baking resumes (→ re-bake on deselect). Mirrors the full-path edit
     *  detection but without building the 3000-job list. */
    private static void bbstools$watchSelectedEdit(List<Replay> replays, IntObjectMap<IEntity> entities)
    {
        if (!BBSPerfConfig.ENABLE_EDIT_LIVE) return;
        String curUuid = BBSPerfConfig.currentReplayUuid;
        if (curUuid == null) return;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay r = replays.get(i);
            if (r == null) continue;
            String u = null;
            try { u = r.uuid.get(); } catch (Throwable ig) {}
            if (u == null || !curUuid.equals(u)) continue;

            String h = bbstools$dataHash(r);
            String prev = UUID_DATAHASH.put(curUuid, h);
            if (prev != null && !prev.equals(h))
            {
                IEntity ent = entities.get(i);
                if (ent != null) { try { AnimationBakeCache.unbind(ent); } catch (Throwable ig) {} }
                UUID_SIG.remove(curUuid);
                long now = System.currentTimeMillis();
                BBSPerfConfig.editLiveUntilMs = now + BBSPerfConfig.EDIT_LIVE_WINDOW_MS;
                bbstools$editLastChangeMs = now;
                BBSPerfConfig.bakeAutoRebakes++;
                bbstools$bakedStable = false; /* resume full baking */
            }
            break;
        }
    }

    /** Cheap DATA fingerprint (form + keyframes + properties, stringified).
     *  No posing/extract → microseconds. Used only to recognise byte-identical
     *  replays (true duplicates) so they skip bone re-extraction; the actual
     *  sharing key is the bone signature below. */
    private static String bbstools$dataHash(Job job)
    {
        /* r73: namespace the data-hash by the resolved MODEL id. Root cause of
         * "bakes only 1 replay" with 2 idle replay types: their form.toData() was
         * byte-identical, so both types collapsed to ONE data-hash → boneSignature
         * was computed once (type A) and type B reused type A's sig/cache
         * (signatureGroups=1, uniqueData=1 measured via :7777). Idle poses are
         * near-identity so the bone-verify couldn't separate them either. Folding
         * the model id in forces a distinct bake group per model. */
        String modelKey = (job.instance == null) ? "" : String.valueOf(job.instance.id);
        return bbstools$dataHash(job.replay) + " m=" + modelKey;
    }

    private static String bbstools$dataHash(Replay r)
    {
        MapType m = new MapType();
        try { BaseType d = r.form.toData();                 if (d != null) m.put("f",  d); } catch (Throwable ignored) {}
        try { BaseType d = r.keyframes.toData();            if (d != null) m.put("k",  d); } catch (Throwable ignored) {}
        try { BaseType d = r.properties.toData();           if (d != null) m.put("p",  d); } catch (Throwable ignored) {}
        /* r.modelBlockProperties was dropped in CML beta-2 — this was only a fingerprint input, so
         * omitting it just makes the fingerprint slightly coarser, never wrong. */
        try { m.putInt("loop", r.looping.get()); } catch (Throwable ignored) {}
        return DataToString.toString(m);
    }

    /** Fingerprint a replay by its MODEL-LOCAL bone output at a few spread ticks.
     *  This is the canonical sharing key: equal bones ⇒ equal animation ⇒ one
     *  bake, regardless of world position (extractFlat is model-local). Computed
     *  ONCE per distinct data-hash (not per entity), so the cost is the unique
     *  data-variant count, not the replay count. Poses the entity at the sample
     *  ticks — caller restores it (added to `touched`). */
    private static Object bbstools$boneSignature(Job job, int expected, int fullLight)
    {
        int e = Math.max(1, expected);
        int[] samples = { 0, e / 4, e / 2, (3 * e) / 4, Math.max(0, e - 1) };
        List<float[]> parts = new ArrayList<>();
        int total = 0;
        for (int s : samples)
        {
            bbstools$pose(job, s);
            FastPathBatch.Submission sub = new FastPathBatch.Submission(
                job.renderer, job.entity, job.instance, job.form,
                IDENTITY, fullLight, OverlayTexture.NO_OVERLAY,
                mchorse.bbs_mod.utils.colors.Colors.WHITE, 0F, false);
            float[] flat = Tier2hInstancedDraw.extractFlat(sub);
            if (flat != null) { parts.add(flat); total += flat.length; }
        }
        float[] concat = new float[total];
        int o = 0;
        for (float[] p : parts) { System.arraycopy(p, 0, concat, o, p.length); o += p.length; }
        /* Namespace by model id: identical bones from DIFFERENT models must NOT share a bake group
         * (idle cubic poses are near-identity across models → false collapse → only one renders). */
        String modelKey = job.instance == null ? null : job.instance.id;
        return AnimationBakeCache.signatureOf(modelKey, concat);
    }

    /** Build a throwaway Submission for diagnostic bone sampling. */
    private static FastPathBatch.Submission bbstools$diagSub(Job job, int fullLight)
    {
        return new FastPathBatch.Submission(
            job.renderer, job.entity, job.instance, job.form,
            IDENTITY, fullLight, OverlayTexture.NO_OVERLAY,
            mchorse.bbs_mod.utils.colors.Colors.WHITE, 0F, false);
    }

    /** Pose an entity at a tick exactly like BaseFilmController.updateEntities
     *  (minus client actions — they don't affect bone matrices).
     *
     *  CRITICAL off-by-one (the real "stuck at half" bug for the whole session):
     *  StubEntity.update() does `age += 1`, so setAge(tick)+update() leaves
     *  age = tick+1. Tier2hInstancedDraw.bakeRecord keys the cache by
     *  round(getAge()) = tick+1, but the bake LOOP's skip-check (get(entity,t))
     *  and firstUncachedTick use the loop tick `t`. Result: pose(t) stored under
     *  key t+1 → only odd keys (1,3,5,…) ever filled (=expected/2=1500 of 3000),
     *  key 0 never filled → firstUncachedTick returns 0 forever → bar frozen at
     *  half, replays hidden forever. We RE-PIN age to `tick` after update() so
     *  getAge()==tick: cache key == loop tick == bar/firstUncached bookkeeping,
     *  and the invariant cache[k]=bones(age k) still matches the live read
     *  (Tier2hInstancedDraw line 841 keys by getAge() too). */
    private static void bbstools$pose(Job job, int tick)
    {
        IEntity entity = job.entity;
        entity.setAge(tick);
        entity.update();
        entity.setAge(tick); /* re-pin: update() bumped age to tick+1 */
        mchorse.bbs_mod.forms.forms.Form form = entity.getForm();
        if (form != null)
        {
            form.update(entity);
            /* THE POSE. Bones reflect replay.properties (FormProperties), which BBS
             * applies in startRenderFrame — not in keyframes.apply. Without this the
             * bake captured a pose-LESS skeleton → baked villagers showed the wrong
             * limbs (and a post-edit re-bake would freeze the wrong pose). Apply it
             * AFTER form.update so it isn't clobbered, exactly like startRenderFrame. */
            try
            {
                job.replay.properties.resetProperties(form);
                job.replay.properties.applyProperties(form, tick);
            }
            catch (Throwable ignored) {}
        }
        job.replay.keyframes.apply(tick, entity);
    }

}
