// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Task 1.0 identity foundation).
package com.blenderbridge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import java.util.List;

/**
 * FilmIdentity — the ONE place that answers "which film is the bridge writing to right now?".
 *
 * <p>Task 1.0 (identity foundation). The datenverlust bug it closes: the live actor link binds
 * IMPLICITLY to "whatever film is open". If BBS switches films while Blender is connected and
 * mid-sync, an inbound ACTORS_IMPORT (Blender -&gt; BBS write) would land in the WRONG film — the
 * user loses work in a film they were not even looking at. The fix is to make every WRITE carry the
 * film id Blender THINKS it is editing, and let BBS reject a write whose id does not match the film
 * currently open ({@link com.blenderbridge.actors.ActorImporter}).</p>
 *
 * <p>MEASURED — no uuid-minting / sidecar needed (plan-task 1.0, "JA → die nutzen" branch):
 * {@code Film.getId()} IS the stable persistent id in BBS' OWN data model. BBS keys its film
 * document tabs on it, compares it with {@code .equals(filmId)}, and requests film data by it
 * ({@code UIFilmPanel.openFilmInDocumentTabs}/{@code activateFilmDocumentTab}/{@code requestData} —
 * decompiled-upstream lines 1489/2056/2266/2320). An editor-opened film always comes from
 * {@code requestData(tab.filmId)} with a NON-EMPTY id (that method returns early on an empty id).
 * We only READ {@code getId()} — never write into BBS data (ARCHITEKTUR invariant 2). Hence there is
 * NO {@code FilmIdentity}-owned uuid map and NO sidecar file: BBS already gives us the stable id, and
 * inventing a parallel one would be a second source of truth that could drift from BBS' tabs.</p>
 *
 * <p>WHICH FILM: the exact same editor-first / playing-fallback precedence {@link
 * com.blenderbridge.actors.ActorExporter} and {@link com.blenderbridge.actors.ActorImporter} use —
 * so the id reported at HELLO, the id an export tags its payload with, and the id an import is
 * checked against all name the SAME film. This class centralises that resolution; the two actor
 * classes keep their own copies for their own reasons (deploy rules — they are already-loaded
 * classes and cannot take a new call to this lazy one without a boot), but the LOGIC is identical and
 * documented here as the reference.</p>
 *
 * <p>WHY A NEW, LAZY CLASS (deploy-window-free): every field/method that would otherwise live on
 * {@code ActorImporter} or {@code BlenderBridgeClient} (both already loaded) would need a boot to add
 * (canAddMethod is unreliable on this JVM; the {@code <clinit>} trap is real — package class-doc
 * "Boot/deploy"). A brand-new class loads lazily off the dev classpath, so this ships as a pure
 * body-swap on the two loaded classes plus this new file. No static initialiser, no mutable state —
 * every method is a pure read of live BBS state, safe to call from any hotswapped body.</p>
 *
 * <p>THREADING: MAIN THREAD ONLY — reads live film/editor state, exactly like the actor bridges. All
 * callers are on the client-tick dispatch.</p>
 */
public final class FilmIdentity
{
    private FilmIdentity()
    {
        /* static-only helper */
    }

    /**
     * The stable id of the film the bridge is currently addressing, or {@code ""} when no film is
     * open (or the open film is a brand-new, never-saved one with an empty id — treated the same as
     * "no bound film", so a write is not blocked against a film that has no identity yet).
     *
     * <p>Editor film first, then any playing film — the actor-bridge precedence. Never throws:
     * "nothing open" is a normal answer that comes back as {@code ""}.</p>
     */
    public static String currentFilmId()
    {
        Film film = resolveOpenFilm();

        if (film == null)
        {
            return "";
        }

        String id = film.getId();

        return id == null ? "" : id;
    }

    /**
     * True when {@code expected} names a DIFFERENT film than the one currently open — i.e. a write
     * tagged {@code expected} must be REJECTED because the user switched films since Blender bound.
     *
     * <p>An empty {@code expected} (old add-on that does not tag its writes, or a manual/EXEC caller)
     * is NEVER a mismatch — backward-compatible with the pre-1.0 wire shape (the write applies to the
     * open film exactly as before). An empty CURRENT id (no film open / unsaved film) is also never a
     * mismatch: there is nothing stable to compare against, and "no film open" is already handled as
     * its own {@code ok:false} answer upstream. So this returns true ONLY when BOTH ids are non-empty
     * and they differ — the precise datenverlust case.</p>
     */
    public static boolean isForeignFilm(String expected)
    {
        if (expected == null || expected.isEmpty())
        {
            return false;
        }

        String current = currentFilmId();

        return !current.isEmpty() && !current.equals(expected);
    }

    /* ================================================================= *
     *  Film resolution — the reference copy (editor first, playing next) *
     * ================================================================= */

    /** The film to address: editor first, then any playing film. Null when nothing is open. */
    public static Film resolveOpenFilm()
    {
        Film editorFilm = getEditorFilm();

        if (editorFilm != null)
        {
            return editorFilm;
        }

        return getPlayingFilm();
    }

    /**
     * The film loaded in the film editor panel, or null.
     *
     * <p>peekDashboard() — NOT getDashboard(): the latter CONSTRUCTS a dashboard as a side effect,
     * which a read must never do. Same trap the actor bridges document. Null dashboard simply means
     * the UI was never opened this session.</p>
     */
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
}
