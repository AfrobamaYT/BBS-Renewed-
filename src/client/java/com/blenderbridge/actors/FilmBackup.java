// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 1.5: write-safety backup).
package com.blenderbridge.actors;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * FilmBackup — task 1.5's write-safety net: a rotated, per-film snapshot taken right before every
 * Blender-authored write into a BBS film, so a bad ACTORS_IMPORT (garbage push, wrong film, python
 * bug) can never destroy user work the way an unguarded live-link could (panel H54 — the link is
 * PROVEN to write live into user films, and since task 1.6 it does so automatically/unattended).
 *
 * <p>WHY JAVA, NOT THE ADDON (deviation from the plan doc's original python-side sketch): since
 * task 1.6 the sync is a live PUSH — Blender-authored writes land via {@code ActorBridge.importActors}
 * automatically, not from a user-invoked button. The addon has no reliable hook that fires
 * "immediately before this exact write", but {@code ActorBridge.importActors} does — it is the one
 * chokepoint every Blender-authored film mutation passes through. Backing up there also means the
 * snapshot is BBS' own {@link Film#toData()} tree (bit-exact, no coordinate/schema translation),
 * and restore is a straight {@link Film#fromData(BaseType)} call — no addon/network round-trip
 * needed to recover.</p>
 *
 * <p>HOOK: {@code ActorBridge.importActors} calls {@link #protect(Supplier)} instead of invoking
 * {@link ActorImporter#applyActors} directly — ONE line at the call site. {@link #protect(Supplier)}
 * resolves the film {@link ActorImporter#applyActors} is ABOUT to mutate (same editor-first/
 * playing-fallback lookup, kept in lock-step here on purpose — see {@link #resolveOpenFilm()}),
 * snapshots it BEFORE calling the wrapped import, then only commits that snapshot to disk once the
 * import's own {@code applied} count says something actually landed. A no-op import (bad uuid, empty
 * payload, unknown film) writes nothing — there is nothing to protect against yet.</p>
 *
 * <p>THROTTLE: at most one on-disk backup per film per {@link #THROTTLE_MILLIS}ms. The push tracker
 * (task 1.6) can drive several imports per second under a busy Blender session; without a throttle
 * every one of them would hit disk. A throttled call still ran the import (its consequences are
 * never skipped) — only the SNAPSHOT is skipped, exactly like a debounce.</p>
 *
 * <p>ROTATION: at most {@link #MAX_BACKUPS_PER_FILM} files kept per film, oldest deleted first.
 * Filenames are {@code yyyyMMdd_HHmmss_SSS.json} — lexicographic order IS chronological order, so
 * {@link #enforceLimit(String)} only needs to sort filenames, never parse them. That also makes the
 * rotation logic fake-timestamp testable: drop N files with any chronologically-sortable name into
 * {@link #backupFolder(String)} and call {@link #enforceLimit(String)} directly — no real 30s waits
 * or 21 real imports needed (task 1.5 proof d).</p>
 *
 * <p>THREADING: MAIN THREAD ONLY, same as {@link ActorImporter} — it reads/writes live film state.
 * {@link #lastBackupAt} is a plain (non-concurrent) map on purpose; nothing here is ever touched off
 * the client thread.</p>
 *
 * <p>NOT BUILT (out of scope for this task, see plan v2 task 1.5 checklist): a restore UI/operator.
 * {@link #restore(File, Film)} is the mechanism a future button would call.</p>
 */
public final class FilmBackup
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Sub-folder of the run directory that holds every film's backups, one folder per film id.
     *  Mirrors {@code WorldExportBridge.DEFAULT_SUBDIR}'s "<runDirectory>/blenderbridge_*" convention. */
    private static final String BACKUP_ROOT = "blenderbridge_backups";

    /** Rotation ceiling per film (task 1.5 spec: N=20). */
    private static final int MAX_BACKUPS_PER_FILM = 20;

    /** Throttle window: at most one on-disk backup per film in this many milliseconds. */
    static final long THROTTLE_MILLIS = 30_000L;

    /** Sortable-by-name == sortable-by-time, and filesystem-safe (no ':' like ISO-8601 would use). */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    /** Placeholder folder name for a film with no id yet (never saved). Keeps the path filesystem-legal. */
    private static final String UNSAVED_FILM_ID = "_unsaved_";

    /** filmId (sanitized) -> epoch millis of the last backup actually WRITTEN to disk (throttle gate). */
    private static final Map<String, Long> lastBackupAt = new HashMap<>();

    private FilmBackup()
    {
        /* static-only helper */
    }

    /* ================================================================= *
     *  The hook — wraps ActorImporter.applyActors                       *
     * ================================================================= */

    /**
     * Run a Blender-authored import with a backup net around it.
     *
     * <p>MAIN THREAD ONLY. Snapshots the film {@code importAction} is about to mutate BEFORE running
     * it, then commits that snapshot to disk only if the import actually applied something
     * ({@code payload.applied > 0}) and the per-film throttle isn't currently active. Never throws —
     * a backup failure is logged and swallowed, it must never block the import it is protecting.</p>
     *
     * @param importAction the wrapped call, normally {@code () -> ActorImporter.applyActors(fields, state)}
     * @return exactly {@code importAction.get()}'s return value, unmodified
     */
    public static JsonObject protect(Supplier<JsonObject> importAction)
    {
        Film film = resolveOpenFilm();
        BaseType before = film == null ? null : film.toData();

        JsonObject payload = importAction.get();

        if (before != null)
        {
            try
            {
                maybeBackup(film.getId(), before, appliedCount(payload));
            }
            catch (Throwable t)
            {
                /* Belt-and-braces: the import already happened and must not be undone by a backup
                 * bug. See class-doc — this net must never become the hazard it guards against. */
                LOGGER.error("[blenderbridge] FilmBackup.protect failed", t);
            }
        }

        return payload;
    }

    /** Reads {@code applied} the same defensive way the rest of the bridge reads contract fields. */
    private static int appliedCount(JsonObject payload)
    {
        if (payload == null || !payload.has(ActorProtocol.APPLIED))
        {
            return 0;
        }

        try
        {
            return payload.get(ActorProtocol.APPLIED).getAsInt();
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /**
     * Commit {@code before} to disk unless the import was a no-op or the film is currently
     * throttled. Package-private so a test can drive it without going through {@link #protect}.
     */
    static void maybeBackup(String filmId, BaseType before, int applied)
    {
        if (applied <= 0)
        {
            /* Nothing landed — nothing to protect against yet (task 1.5 spec: no backup on no-op). */
            return;
        }

        String safeId = sanitize(filmId);
        long now = System.currentTimeMillis();
        Long last = lastBackupAt.get(safeId);

        if (last != null && now - last < THROTTLE_MILLIS)
        {
            return;
        }

        File folder = backupFolder(safeId);

        if (!folder.exists() && !folder.mkdirs() && !folder.exists())
        {
            LOGGER.error("[blenderbridge] FilmBackup could not create folder {}", folder);

            return;
        }

        File file = new File(folder, LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".json");

        try
        {
            DataToString.write(file, before, true);
            lastBackupAt.put(safeId, now);
        }
        catch (Exception e)
        {
            LOGGER.error("[blenderbridge] FilmBackup write failed for film '{}'", safeId, e);

            return;
        }

        enforceLimit(safeId);
    }

    /* ================================================================= *
     *  Rotation                                                         *
     * ================================================================= */

    /**
     * Delete the oldest backups of {@code filmId} until at most {@link #MAX_BACKUPS_PER_FILM}
     * remain. PUBLIC and independent of {@link #maybeBackup} on purpose (task 1.5 proof d): drop any
     * number of chronologically-named {@code .json} files into {@link #backupFolder(String)}
     * yourself (fake timestamps are fine — only the filename sort order matters) and call this
     * directly to prove the cleanup, no 30s throttle wait or 21 real imports required.
     */
    public static void enforceLimit(String filmId)
    {
        File[] files = backupFolder(filmId).listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length <= MAX_BACKUPS_PER_FILM)
        {
            return;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        int excess = files.length - MAX_BACKUPS_PER_FILM;

        for (int i = 0; i < excess; i++)
        {
            if (!files[i].delete())
            {
                LOGGER.error("[blenderbridge] FilmBackup could not delete {}", files[i]);
            }
        }
    }

    /** {@code <runDirectory>/blenderbridge_backups/<filmId>} — mirrors {@code WorldExportBridge}'s
     *  {@code DEFAULT_SUBDIR} convention. Sanitizes {@code filmId} itself, so raw ids are safe to pass. */
    public static File backupFolder(String filmId)
    {
        Minecraft client = Minecraft.getInstance();
        File runDir = client != null && client.gameDirectory != null ? client.gameDirectory : new File(".");

        return new File(new File(runDir, BACKUP_ROOT), sanitize(filmId));
    }

    /** The newest backup file for {@code filmId}, or null when none exist. Filename order == time order. */
    public static File latestBackup(String filmId)
    {
        File[] files = backupFolder(filmId).listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0)
        {
            return null;
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        return files[files.length - 1];
    }

    /** Keeps a film id filesystem-legal as a single path segment (ids are not expected to nest, but a
     *  stray '/' or ':' must not escape {@link #backupFolder}, and idempotent so double-sanitizing a
     *  already-safe id is a no-op). */
    private static String sanitize(String filmId)
    {
        String id = filmId == null || filmId.isEmpty() ? UNSAVED_FILM_ID : filmId;

        return id.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /* ================================================================= *
     *  Restore (task 1.5: method only, no UI — see class-doc)           *
     * ================================================================= */

    /**
     * Read a backup file written by {@link #maybeBackup} and overwrite {@code film}'s live state
     * with it — the inverse of the snapshot, via BBS' own {@link Film#fromData(BaseType)}.
     *
     * <p>MAIN THREAD ONLY — mutates live film state exactly like {@link ActorImporter}. Does NOT run
     * the {@code applyingRemote} echo guard itself; a caller driving this against a connected
     * Blender session should wrap it the same way {@link ActorImporter} does if echo suppression
     * matters for that call site. The restore UI/operator this would back is explicitly NOT part of
     * this task (plan v2 task 1.5) — this method is that future operator's mechanism.</p>
     *
     * @return true on success; false when the file is missing/unreadable/unparseable or {@code film}
     *         is null — never throws
     */
    public static boolean restore(File backupFile, Film film)
    {
        if (backupFile == null || !backupFile.exists() || film == null)
        {
            return false;
        }

        try
        {
            BaseType data = DataToString.read(backupFile);

            if (data == null)
            {
                return false;
            }

            film.fromData(data);

            return true;
        }
        catch (Exception e)
        {
            LOGGER.error("[blenderbridge] FilmBackup restore failed for {}", backupFile, e);

            return false;
        }
    }

    /* ================================================================= *
     *  Film resolution — MUST mirror ActorImporter.applyActors exactly  *
     * ================================================================= */

    /**
     * The film {@link ActorImporter#applyActors} is about to target: editor first, playing film as
     * fallback. Deliberately duplicated (not shared) from {@link ActorImporter}/{@link ActorExporter}
     * — same duplication pattern those two already use for the same lookup — so a fix to one is not
     * silently forgotten in the others. Any change to the resolution order in
     * {@code ActorImporter.applyActors} must be mirrored here, or the backup could snapshot the
     * WRONG film relative to the one actually being mutated.
     */
    private static Film resolveOpenFilm()
    {
        UIFilmPanel panel = getEditorPanel();

        return panel != null ? panel.getData() : getPlayingFilm();
    }

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
}
