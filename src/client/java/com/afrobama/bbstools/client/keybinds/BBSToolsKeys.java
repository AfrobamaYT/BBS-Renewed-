// Coded by Afrobama/Claude — KeyCombo registry for BBSTools shortcuts
package com.afrobama.bbstools.client.keybinds;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import org.lwjgl.glfw.GLFW;

/**
 * KeyCombos owned by BBSTools (instead of being added as static fields
 * to BBS's {@code Keys} class — that approach forced BBS-source
 * patches). Registering through the BBS KeyCombo constructor wires the
 * combo into BBS's pendingOverrides system so user preset persistence
 * via {@link KeybindOverrides} keeps working.
 */
public final class BBSToolsKeys
{
    /** Open the BBSTools UIKeybindEditor as an overlay (Shift+F9 default). */
    public static final KeyCombo KEYBIND_EDITOR_OPEN = new KeyCombo(
        "bbstools.keybind_editor",
        IKey.constant("Open Keybind Editor"),
        GLFW.GLFW_KEY_F9,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Open the BBS Quick Replay picker overlay inside the film editor
     *  (^ default — same key the in-world picker uses). */
    public static final KeyCombo QUICK_REPLAY_PICKER = new KeyCombo(
        "bbstools.quick_replay_picker",
        IKey.constant("Quick Replay Picker"),
        GLFW.GLFW_KEY_GRAVE_ACCENT
    );

    /** Cycle the editor-cam through camera-style replays in the active
     *  film (F8 default). Lets the user view the film from any camera
     *  replay's perspective without flying there. See EditorCamHandler. */
    public static final KeyCombo EDITOR_CAM_CYCLE = new KeyCombo(
        "bbstools.editor_cam_cycle",
        IKey.constant("Editor-cam: cycle camera replays"),
        GLFW.GLFW_KEY_F8
    );

    /** Cycle the editor-cam through ALL replays including actor replays
     *  (Shift+F8 default — but readable as a separate KeyCombo so user
     *  can rebind both independently). */
    public static final KeyCombo EDITOR_CAM_CYCLE_ALL = new KeyCombo(
        "bbstools.editor_cam_cycle_all",
        IKey.constant("Editor-cam: cycle ALL replays (incl. actors)"),
        GLFW.GLFW_KEY_F8,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Toggle the in-game log console (F12 default). Captures every
     *  Log4j event from BBS / BBSTools / Minecraft / other mods into a
     *  ring buffer for in-game inspection. */
    public static final KeyCombo LOG_CONSOLE_TOGGLE = new KeyCombo(
        "bbstools.log_console_toggle",
        IKey.constant("Toggle in-game log console"),
        GLFW.GLFW_KEY_F12
    );

    /** Add a new camera-preview window at the player's current pose
     *  (Ctrl+Shift+P default). Lets the user spawn previews without
     *  leaving the dashboard to type chat commands. */
    public static final KeyCombo CAMPREVIEW_ADD = new KeyCombo(
        "bbstools.campreview_add",
        IKey.constant("Camera Preview: add at player"),
        GLFW.GLFW_KEY_P,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Auto-layout all preview windows into a grid (Ctrl+Shift+L). */
    public static final KeyCombo CAMPREVIEW_LAYOUT = new KeyCombo(
        "bbstools.campreview_layout",
        IKey.constant("Camera Preview: auto-layout grid"),
        GLFW.GLFW_KEY_L,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Clear all preview windows (Ctrl+Shift+X). */
    public static final KeyCombo CAMPREVIEW_CLEAR = new KeyCombo(
        "bbstools.campreview_clear",
        IKey.constant("Camera Preview: clear all"),
        GLFW.GLFW_KEY_X,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Phase 14: toggle visibility of all Blender-style camera frustum
     *  meshes in the world (the wireframe pyramids that show where each
     *  Add-Camera clip's camera is pointing). Default K. */
    public static final KeyCombo CAMERA_MESH_TOGGLE = new KeyCombo(
        "bbstools.camera_mesh_toggle",
        IKey.constant("Camera Mesh: toggle visibility"),
        GLFW.GLFW_KEY_K
    );

    /** Phase 9: open the Undo History overlay (Ctrl+H default). Shows
     *  the bounded LIFO of edits we've tracked plus a redo stack. */
    public static final KeyCombo UNDO_HISTORY_OPEN = new KeyCombo(
        "bbstools.undo_history_open",
        IKey.constant("Open Undo History overlay"),
        GLFW.GLFW_KEY_H,
        GLFW.GLFW_KEY_LEFT_CONTROL
    );

    /** Phase 10: open the dedicated Replay Dock (Ctrl+Shift+R default).
     *  Standalone list + properties pane for replays in the active
     *  film without needing the full UIReplaysEditor. */
    public static final KeyCombo REPLAY_DOCK_OPEN = new KeyCombo(
        "bbstools.replay_dock_open",
        IKey.constant("Open Replay Dock"),
        GLFW.GLFW_KEY_R,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Phase 9d: walk one step back through the BBSTools undo stack.
     *  Ctrl+Shift+Z default — avoids colliding with textbox undo
     *  (Ctrl+Z) used by BBS-CML's input widgets. */
    public static final KeyCombo UNDO_STEP = new KeyCombo(
        "bbstools.undo_step",
        IKey.constant("BBSTools: undo last tracked edit"),
        GLFW.GLFW_KEY_Z,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Phase 9d: walk one step forward through the BBSTools redo stack.
     *  Ctrl+Shift+Y default — mirrors UNDO_STEP, avoiding the bare
     *  Ctrl+Y collisions in BBS-CML. */
    public static final KeyCombo REDO_STEP = new KeyCombo(
        "bbstools.redo_step",
        IKey.constant("BBSTools: redo last reverted edit"),
        GLFW.GLFW_KEY_Y,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        GLFW.GLFW_KEY_LEFT_SHIFT
    );

    /** Phase 12: open the BBSTools settings overlay (transparency
     *  slider + overlay shortcuts). Ctrl + Comma default — the
     *  universal "preferences" shortcut across most pro apps. */
    public static final KeyCombo SETTINGS_OPEN = new KeyCombo(
        "bbstools.settings_open",
        IKey.constant("Open BBSTools Settings"),
        GLFW.GLFW_KEY_COMMA,
        GLFW.GLFW_KEY_LEFT_CONTROL
    );

    /** Phase 15: open the DaVinci-style Project Launch Pad with the
     *  worlds-grid. Ctrl + 0 default — "home" / "project page". */
    public static final KeyCombo LAUNCHPAD_OPEN = new KeyCombo(
        "bbstools.launchpad_open",
        IKey.constant("Open Project Launch Pad"),
        GLFW.GLFW_KEY_0,
        GLFW.GLFW_KEY_LEFT_CONTROL
    );

    /** Phase 16: start (or stop) recording the current replay starting
     *  at the editor's current cursor tick instead of tick 0. Mirrors
     *  vanilla BBS {@code record_replay} (Right-Alt by default → "from
     *  tick 0"), but uses the scrub cursor as the start tick so the
     *  user can record from a later point on the timeline without
     *  going through the in-editor "Record replay" popup. Toggle:
     *  pressing again stops and applies keyframes — same semantics as
     *  the vanilla key. Default F7 (free in vanilla MC + vanilla BBS). */
    public static final KeyCombo RECORD_FROM_CURSOR = new KeyCombo(
        "bbstools.record_from_cursor",
        IKey.constant("Record replay from cursor position"),
        GLFW.GLFW_KEY_F7
    );

    /** Switch the transform gizmo between BBS/CML's own controls and the
     *  ported Blender G/R/S kernel. Both stay fully available — this only
     *  decides which one the editor drives right now, so muscle memory
     *  from either tool keeps working. Default F6 (free in vanilla MC and
     *  vanilla BBS, and next to the other editor toggles). */
    public static final KeyCombo TRANSFORM_KERNEL_TOGGLE = new KeyCombo(
        "bbstools.transform_kernel_toggle",
        IKey.constant("Toggle Blender/BBS transform controls"),
        GLFW.GLFW_KEY_F6
    );

    /** Phase 16b: like RECORD_FROM_CURSOR, but additionally teleports
     *  the player to the keyframed position/rotation at the cursor
     *  tick BEFORE recording starts. The user picks up where the old
     *  keyframes left them off — useful for continuing an existing
     *  animation seamlessly (e.g. "I scrubbed to tick 60 where the
     *  character is mid-house facing the floor; press F6 to physically
     *  start there and record forward"). Mirrors the editor's "Record
     *  replay (all keyframes)" mode, but works outside the editor
     *  without the fussy actor-control hand-off. Default F6. */
    public static final KeyCombo RECORD_FROM_KEYFRAMES = new KeyCombo(
        "bbstools.record_from_keyframes",
        IKey.constant("Record replay from current keyframes"),
        GLFW.GLFW_KEY_F6
    );

    private BBSToolsKeys()
    {
    }

    /** Force class-load so the static KeyCombo constructors run and
     *  register with BBS's pendingOverrides + presets system. */
    public static void touch()
    {
        /* No-op; reading any field forces clinit. */
        if (KEYBIND_EDITOR_OPEN == null) throw new IllegalStateException("never");
    }

}
