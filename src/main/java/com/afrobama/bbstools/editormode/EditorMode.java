// Coded by Afrobama/Claude — Phase 17: editor-mode flag (skip mob ticks/renders during setup)
package com.afrobama.bbstools.editormode;

import net.minecraft.world.entity.Mob;

/**
 * Global flag for "editor mode" — the BBSTools companion mode where the
 * world is suppressed in the live preview to give back FPS for heavy
 * scenes (mob armies, lots of villagers, etc.). Off by default; user
 * toggles via {@code /bbsdebug editor-mode on}.
 *
 * <p>While active:</p>
 * <ul>
 *   <li>Vanilla mob ticks are short-circuited (skips AI / pathfinding /
 *       brain processing) — the dominant CPU cost for villager-heavy
 *       scenes.</li>
 *   <li>Mob rendering is skipped on the client so the editor view is
 *       not cluttered.</li>
 *   <li>BBS replay rendering is untouched — replays use BBS's own
 *       form pipeline and don't go through the vanilla mob renderer or
 *       tick chain.</li>
 *   <li>When BBS itself is doing a video export render
 *       ({@link BBSRendering#isCustomSize()}), the bypasses self-disable
 *       so the exported video has the full unmodified scene.</li>
 * </ul>
 *
 * <p>Trade-off: in editor mode, ANY mob in the world is hidden + frozen,
 * even hand-spawned ones. User must toggle off if they want to interact
 * with specific mobs. That trade is acceptable for the target workflow
 * (set up cinematic, switch off, record). </p>
 */
public final class EditorMode
{
    /** Global on/off. Default false → no behavior change for users who
     *  don't opt-in. */
    public static volatile boolean ACTIVE;

    private EditorMode() {}

    /** Client-only side-channel set true while BBS is exporting video,
     *  so the bypass self-disables and the recorded film has the full
     *  scene. Wired by a client mixin into the BBS recorder; not used
     *  on dedicated servers. */
    public static volatile boolean VIDEO_EXPORT_ACTIVE;

    /** True when the bypass logic should fire for the given mob. */
    public static boolean shouldBypass(Mob mob)
    {
        if (!ACTIVE) return false;
        if (VIDEO_EXPORT_ACTIVE) return false;
        return mob != null;
    }

}
