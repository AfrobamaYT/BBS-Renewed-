// Coded by Afrobama/Claude — shared state holder for the sprint FOV
// system. Lives OUTSIDE the mixin package because Mixins forbid
// non-private static fields (see feedback_mixin_no_public_static
// memory note — burned us in iter 26 with a startup crash).
package com.afrobama.bbstools.client.tracker;

public final class SprintFovState
{
    /** Manual override — set via /bbstools sprintfov on|off|toggle.
     *  When true, FOV gets boosted regardless of entity state. Use
     *  this for cinematic recording when auto-detection from BBS
     *  replay state isn't working. */
    public static volatile boolean MANUAL_SPRINT = false;

    private SprintFovState() {}

}
