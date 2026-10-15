// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4: Blender lights -> BBS LightForm).
package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.blenderbridge.BridgeState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import org.slf4j.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LightImporter — Phase 4: writes Blender-authored LIGHTS as {@link LightForm} actors into the open
 * BBS film, so the light POSITIONS the user placed in Blender show up as real Minecraft block-light
 * in a BBS render.
 *
 * <h2>WHY A LightForm IS AN ACTOR, NOT A STANDALONE OBJECT</h2>
 * <p>A {@link LightForm} renders NOTHING in 3D on its own ({@code LightFormRenderer.render3D} is
 * empty — it is only a UI icon). What actually makes light appear is
 * {@code LambDynLightsIntegration}: it registers a dynamic-light handler on {@code BBSMod.ACTOR_ENTITY}
 * that reads {@code getLightLevelFromForm(entity.getForm())}. So a LightForm emits light ONLY when it
 * is the form of a spawned actor entity. This importer therefore creates, per Blender light, ONE
 * {@link Replay} whose {@code form} is a LightForm, positions it via the replay's {@code x/y/z}
 * keyframe channels, and (in the editor) respawns the actor entities so LambDynLights lights them.</p>
 *
 * <h2>THE HONEST LIMIT (Regel 0 — documented, not hidden)</h2>
 * <p>BBS' {@link LightForm} carries ONLY {@code enabled}(bool) + {@code level}(int 0-15 MC block
 * light). NO colour, NO radius, NO direction, NO cone. A Blender light becomes a coarse brightness
 * POINT at its position; the add-on maps energy -> level and owns that lossy mapping. This class does
 * NOT try to fake colour/direction — it writes exactly what the form can hold and nothing more.</p>
 *
 * <h2>MANAGED LIGHTS — idempotent mirror, never touches user actors</h2>
 * <p>Every replay this importer creates is tagged: its {@code group} is {@link #LIGHT_GROUP} and its
 * {@code label} is prefixed {@link #LABEL_PREFIX}. A re-import with {@code replaceManaged=true}
 * (default) first REMOVES every replay carrying that group, then rebuilds from the current payload —
 * so the user can move/delete lights in Blender and re-import to get an exact mirror, and a genuine
 * user actor (any replay NOT in the managed group) is never removed. {@code replaceManaged=false}
 * only adds, leaving stale managed lights in place.</p>
 *
 * <h2>WRITE-SAFETY (task 1.5) + ECHO GUARD + FILM GUARD (task 1.0)</h2>
 * <p>Mirrors {@link ActorImporter}/{@code CameraClipWriter}: snapshots {@code Film.toData()} BEFORE
 * the mutation and commits it to {@code run/blenderbridge_backups/<filmId>/<ts>.json} only when
 * something landed (through {@link FilmBackup}'s public folder/rotation helpers — a new method on the
 * loaded FilmBackup would break the deploy window); runs the whole mutation inside
 * {@link BridgeState#withRemoteApply(Runnable)} so BBS change-detection does not bounce it back; and
 * rejects a write tagged for a DIFFERENT film than the one open ({@code fields.film} vs
 * {@link com.blenderbridge.FilmIdentity#isForeignFilm}) BEFORE any mutation — the datenverlust seal.</p>
 *
 * <h2>MESSAGE WIRING — raw string type (deploy-window-free)</h2>
 * <p>{@code "LIGHTS_IMPORT"} in, {@code "LIGHTS_IMPORT_RESULT"} out — a raw-string dispatch case, no
 * new {@code BridgeProtocol.TYPE_*} constant (that class is loaded — the W1+ deploy window forbids a
 * new static). This whole class is NEW and lazy, so it loads off the dev classpath without a boot;
 * the only loaded-class edit is the one-line dispatch case in {@code BlenderBridgeClient}. requestId
 * is echoed on every reply path (ce2d139/W8-B4 discipline) so the broadcasting bridge lets the add-on
 * tell its own result from a foreign agent's.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY — it mutates live film/editor state and respawns actor entities. Invoked from
 * {@code BlenderBridgeClient.dispatch}.</p>
 */
public final class LightImporter
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The BBS form-registry id for a LightForm (see {@code BBSMod.getForms().register(Link.bbs("light"), LightForm.class)}). */
    private static final String LIGHT_FORM_LABEL = "Light";

    /** Group tag on every replay this importer owns — the handle for the idempotent re-import mirror. */
    public static final String LIGHT_GROUP = "BBS_Light";

    /** Label prefix for a managed light replay (human-readable in the BBS replay list). */
    public static final String LABEL_PREFIX = "BBS_Light_";

    /** Same filesystem-safe, sortable-by-name==sortable-by-time format {@link FilmBackup} uses. */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private LightImporter()
    {
        /* static-only helper */
    }

    /* ================================================================= *
     *  Dispatch entry                                                    *
     * ================================================================= */

    /**
     * Handle a LIGHTS_IMPORT request (from {@code BlenderBridgeClient.dispatch}). Never throws — every
     * failure answers immediately with an {@code ok:false} result rather than leaving the add-on
     * waiting. requestId is echoed on every path (W8-B4 pattern).
     */
    public static void handle(JsonObject message, BridgeState state)
    {
        JsonObject fields = BridgeProtocol.getFields(message);
        String requestId = BridgeProtocol.getFieldString(fields, "requestId", "");

        JsonObject out;

        try
        {
            out = write(fields, state);
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] lights import failed", t);
            out = failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        if (!requestId.isEmpty())
        {
            out.addProperty("requestId", requestId);
        }

        sendResult(out);
    }

    /* ================================================================= *
     *  The write                                                        *
     * ================================================================= */

    /**
     * Write the Blender-authored lights into the open film as LightForm replays.
     *
     * <p>MAIN THREAD ONLY. Never throws on its own — "no film open" / "no lights" are normal
     * {@code ok:false} answers; a genuinely unexpected throw is left to {@link #handle}'s catch.</p>
     *
     * @param fields the LIGHTS_IMPORT {@code fields} object: {@code {"lights":[...],"replaceManaged":bool,"film":"..."}}
     * @param state  the shared bridge state (echo guard). Mandatory — a null state is rejected.
     * @return the LIGHTS_IMPORT_RESULT payload; never null, every contract key always present
     */
    public static JsonObject write(JsonObject fields, BridgeState state)
    {
        if (fields == null)
        {
            return failure("no payload");
        }

        if (state == null)
        {
            /* Echo guard is a class contract, not an optimisation — refuse rather than loop. */
            return failure("no bridge state");
        }

        JsonElement lightsElement = fields.get("lights");

        if (lightsElement == null || !lightsElement.isJsonArray())
        {
            return failure("no lights in payload");
        }

        JsonArray lights = lightsElement.getAsJsonArray();

        FilmRef ref = resolveOpenFilm();

        if (ref == null)
        {
            return failure("no film open");
        }

        /* Task 1.0 IDENTITY GUARD — the datenverlust seal (mirror ActorImporter). Reject a write
         * tagged for a film other than the one open BEFORE any mutation. Empty/absent expected id
         * (EXEC caller / older add-on) is deliberately not a mismatch. */
        String expectedFilm = BridgeProtocol.getFieldString(fields, "film", "");

        if (com.blenderbridge.FilmIdentity.isForeignFilm(expectedFilm))
        {
            return filmMismatch(expectedFilm, ref.film.getId() == null ? "" : ref.film.getId());
        }

        boolean replaceManaged = getBoolean(fields, "replaceManaged", true);

        /* Snapshot the film's data tree BEFORE the mutation (task 1.5). */
        BaseType before = ref.film.toData();

        int[] result = new int[3]; /* [0]=created, [1]=removed, [2]=(reserved) */

        state.withRemoteApply(() -> writeGuarded(ref.film, ref.panel, lights, replaceManaged, result));

        int created = result[0];
        int removed = result[1];

        /* Commit the backup only when something actually changed. */
        File backupFile = null;

        if (created > 0 || removed > 0)
        {
            backupFile = commitBackup(ref.film.getId(), before);
        }

        int total = countManaged(ref.film);

        JsonObject out = new JsonObject();

        out.addProperty("ok", true);
        out.addProperty("film", ref.film.getId() == null ? "" : ref.film.getId());
        out.addProperty("source", ref.source);
        out.addProperty("created", created);
        out.addProperty("updated", 0); /* v1 is create/replace only — no in-place update path yet */
        out.addProperty("removed", removed);
        out.addProperty("total", total);
        out.addProperty("replaceManaged", replaceManaged);
        out.addProperty("backup", backupFile == null ? "" : backupFile.getName());
        out.addProperty("error", "");

        LOGGER.info("[blenderbridge] lights import: film={} source={} created={} removed={} total={} replaceManaged={} backup={}",
            ref.film.getId(), ref.source, created, removed, total, replaceManaged,
            backupFile == null ? "-" : backupFile.getName());

        return out;
    }

    /**
     * The actual mutation, run inside the echo guard by {@link #write}. Optionally clears the old
     * managed lights, creates one LightForm replay per payload light, then hands the change over to
     * BBS (undo/refresh/save) and RESPAWNS actor entities so LambDynLights lights them.
     */
    private static void writeGuarded(Film film, UIFilmPanel panel, JsonArray lights,
        boolean replaceManaged, int[] result)
    {
        /* 1. Remove the old managed lights (idempotent mirror) — never touches a user actor. */
        if (replaceManaged)
        {
            result[1] = removeManagedLights(film);
        }

        /* 2. Create one LightForm replay per payload light. */
        int created = 0;

        for (JsonElement element : lights)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            if (createLightReplay(film, element.getAsJsonObject()))
            {
                created += 1;
            }
        }

        result[0] = created;

        film.replays.sync();

        /* 3. Hand over to BBS (undo entry, disk, entity respawn) — only when something landed and an
         *    editor is attached (a playing film has no editor UI). */
        if ((created > 0 || result[1] > 0) && panel != null)
        {
            handOverToBBS(panel);
        }
    }

    /**
     * Create one LightForm replay from a payload light entry:
     * {@code {"name":str,"level":int,"x":num,"y":num,"z":num,"enabled":bool}}.
     *
     * <p>The x/y/z are RAW BBS-space coordinates (the add-on applied COORDINATE_CONTRACT.md); we write
     * them verbatim as single keyframes at tick 0 on the replay's position channels — a static light
     * needs exactly one key per axis. The form's {@code level} is clamped 0-15
     * (LambDynLightsIntegration clamps again, but a clean payload should already be in range).</p>
     *
     * @return true when a replay was created
     */
    private static boolean createLightReplay(Film film, JsonObject light)
    {
        String name = BridgeProtocol.getFieldString(light, "name", "light");
        int level = clampLevel((int) Math.round(BridgeProtocol.getDouble(light, "level", 15D)));
        boolean enabled = getBoolean(light, "enabled", true);

        double x = BridgeProtocol.getDouble(light, "x", 0D);
        double y = BridgeProtocol.getDouble(light, "y", 0D);
        double z = BridgeProtocol.getDouble(light, "z", 0D);

        LightForm form = new LightForm();

        form.level.set(level);
        form.enabled.set(enabled);
        form.name.set(LABEL_PREFIX + name);

        /* addReplay() fires the pre/postNotify the undo handler needs and appends to the list. */
        Replay replay = film.replays.addReplay();

        replay.form.set(form);
        replay.label.set(LABEL_PREFIX + name);
        replay.group.set(LIGHT_GROUP);
        replay.uuid.set(UUID.randomUUID().toString());
        /* Enabled controls whether BBS plays the actor at all; the LightForm.enabled toggles the
         * emission. Keep the replay enabled so the actor spawns; light on/off lives on the form. */
        replay.enabled.set(true);

        /* Static position: one keyframe per axis at tick 0 (BaseFilmController.update re-applies these
         * onto the actor entity each tick, so the light sits where Blender placed it). */
        replay.keyframes.x.insert(0F, x);
        replay.keyframes.y.insert(0F, y);
        replay.keyframes.z.insert(0F, z);

        return true;
    }

    /**
     * Remove every replay this importer owns (group == {@link #LIGHT_GROUP}). Copy first — remove()
     * mutates the backing list. A replay is "managed" purely by its group tag, so a user actor (any
     * other group, or none) is never touched.
     *
     * @return how many managed replays were removed
     */
    private static int removeManagedLights(Film film)
    {
        List<Replay> toRemove = new ArrayList<>();

        for (Replay replay : film.replays.getList())
        {
            if (replay != null && LIGHT_GROUP.equals(replay.group.get()))
            {
                toRemove.add(replay);
            }
        }

        for (Replay replay : toRemove)
        {
            film.replays.remove(replay);
        }

        return toRemove.size();
    }

    /** How many managed light replays the film currently holds (for the result's {@code total}). */
    private static int countManaged(Film film)
    {
        int count = 0;

        for (Replay replay : film.replays.getList())
        {
            if (replay != null && LIGHT_GROUP.equals(replay.group.get()))
            {
                count += 1;
            }
        }

        return count;
    }

    private static int clampLevel(int level)
    {
        if (level < 0)
        {
            return 0;
        }

        return Math.min(level, 15);
    }

    /* ================================================================= *
     *  Hand-over to BBS: undo, UI refresh, persistence, entity respawn  *
     * ================================================================= */

    /**
     * Tell BBS the film changed. Unlike {@link ActorImporter} (which only edits existing channels),
     * this changes the replay LIST — so it MUST respawn actor entities via
     * {@code UIFilmController.createEntitiesNow()}, otherwise the new LightForm actors never exist in
     * world and LambDynLights has nothing to light. Order mirrors BBS' own writers: submit the undo
     * entry the value-tree notifications cached, respawn entities, then persist.
     */
    private static void handOverToBBS(UIFilmPanel panel)
    {
        if (panel.getUndoHandler() != null)
        {
            panel.getUndoHandler().submitUndo();
        }

        /* The replay LIST changed — respawn actor entities so the new LightForm actors exist and
         * LambDynLights lights them (createEntities() is exactly for a list change; ActorImporter
         * skips it because it never changes the list). Guarded null-safe. */
        if (panel.getController() != null)
        {
            /* BBSCompat: this BBS build spells it createEntities() (the newer createEntitiesNow()
             * is a pure delegate to it) — same respawn, resolved at compile time. */
            com.blenderbridge.BBSCompat.createEntitiesNow(panel.getController());
        }

        /* Guarded internally: a no-op while the panel is mid-load or its editor is disabled. */
        panel.save();
    }

    /* ================================================================= *
     *  Write-safety backup (task 1.5) — through FilmBackup's public API  *
     * ================================================================= */

    /**
     * Commit a pre-mutation snapshot of the film, then rotate via {@link FilmBackup#enforceLimit}.
     * Same on-disk shape {@link FilmBackup} writes for actor imports, so {@code restore} + the 20-file
     * rotation apply verbatim. Never throws — a backup failure is logged and swallowed; the mutation
     * already happened and must not be undone by a backup bug.
     */
    private static File commitBackup(String filmId, BaseType before)
    {
        if (before == null)
        {
            return null;
        }

        try
        {
            File folder = FilmBackup.backupFolder(filmId);

            if (!folder.exists() && !folder.mkdirs() && !folder.exists())
            {
                LOGGER.error("[blenderbridge] LightImporter could not create backup folder {}", folder);

                return null;
            }

            File file = new File(folder, LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".json");

            DataToString.write(file, before, true);

            FilmBackup.enforceLimit(filmId == null ? "" : filmId);

            return file;
        }
        catch (Throwable t)
        {
            LOGGER.error("[blenderbridge] LightImporter backup failed for film '{}'", filmId, t);

            return null;
        }
    }

    /* ================================================================= *
     *  Film resolution — deliberately duplicated (house convention)     *
     * ================================================================= */

    /** Editor film first, then any playing film — same precedence as {@code ActorImporter}/
     *  {@code CameraClipWriter}. Duplicated on purpose so a change to one is a conscious change. */
    private static FilmRef resolveOpenFilm()
    {
        UIFilmPanel editorPanel = getEditorPanel();

        if (editorPanel != null)
        {
            return new FilmRef(editorPanel.getData(), editorPanel, "editor");
        }

        Film playingFilm = getPlayingFilm();

        if (playingFilm != null)
        {
            return new FilmRef(playingFilm, null, "playing");
        }

        return null;
    }

    /** peekDashboard() — NOT getDashboard(): the latter builds a dashboard as a side effect. */
    private static UIFilmPanel getEditorPanel()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();

        if (dashboard == null)
        {
            return null;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return null;
        }

        return panel;
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
     *  Small JSON helpers + payload skeleton + wire                     *
     * ================================================================= */

    /** Defensive boolean read (BridgeProtocol has no getBoolean). Missing/garbled = fallback. */
    private static boolean getBoolean(JsonObject fields, String key, boolean fallback)
    {
        if (fields == null || !fields.has(key))
        {
            return fallback;
        }

        try
        {
            JsonElement element = fields.get(key);

            return element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    /** Failure payload — every success key present, so the python side never branches on presence. */
    private static JsonObject failure(String error)
    {
        JsonObject out = new JsonObject();

        out.addProperty("ok", false);
        out.addProperty("film", "");
        out.addProperty("source", "");
        out.addProperty("created", 0);
        out.addProperty("updated", 0);
        out.addProperty("removed", 0);
        out.addProperty("total", 0);
        out.addProperty("replaceManaged", false);
        out.addProperty("backup", "");
        out.addProperty("error", error == null ? "" : error);

        return out;
    }

    /**
     * Task 1.0 film-mismatch rejection (the datenverlust seal — mirror {@link ActorImporter}). A
     * normal {@code ok:false} with the two ids echoed as their own machine-readable fields so the
     * add-on can auto-pause its sync and name the film it drifted onto.
     */
    private static JsonObject filmMismatch(String expected, String open)
    {
        JsonObject out = failure("film mismatch: write was for '" + expected + "', open film is '" + open + "' — sync paused to protect your work");

        out.addProperty("film", open);
        out.addProperty("expectedFilm", expected);
        out.addProperty("filmMismatch", true);

        return out;
    }

    private static void sendResult(JsonObject payload)
    {
        long seq = com.blenderbridge.BlenderBridgeClient.STATE.nextOutboundSeq();

        /* Raw string type, no BridgeProtocol.TYPE_* constant — see class-doc "MESSAGE WIRING". */
        JsonObject envelope = BridgeProtocol.envelope("LIGHTS_IMPORT_RESULT", "lights", seq, BridgeProtocol.ORIGIN_BBS);

        envelope.add(BridgeProtocol.KEY_FIELDS, payload);

        com.blenderbridge.BlenderBridgeClient.SERVER.send(envelope);
    }

    /** A resolved film plus the editor panel (null for a playing film) and which lookup produced it. */
    private static final class FilmRef
    {
        private final Film film;
        private final UIFilmPanel panel;
        private final String source;

        private FilmRef(Film film, UIFilmPanel panel, String source)
        {
            this.film = film;
            this.panel = panel;
            this.source = source;
        }
    }
}
