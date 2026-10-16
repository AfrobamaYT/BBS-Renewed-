package mchorse.bbs_mod.ui;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

/**
 * IF THE KEYS DON'T APPEAR IN THE CONFIGURATION MENU, you used wrong constructor!
 * Use {@link KeyCombo#KeyCombo(String, IKey, int...)} intead of {@link KeyCombo#KeyCombo(IKey, int...)}!
 */
public class Keys
{
    /* General */
    public static final KeyCombo DESELECT = new KeyCombo("deselect", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_DESELECT, InputConstants.KEY_D, InputConstants.KEY_LCONTROL);
    public static final KeyCombo KEYBINDS = new KeyCombo("keybinds", UIKeys.KEYS_LIST, InputConstants.KEY_F9);
    /* Help (2026-10-14): F1 stays the original "toggle dashboard"; Shift+F1 steps in front of it */
    public static final KeyCombo HILFE = new KeyCombo("help", mchorse.bbs_mod.l10n.keys.IKey.raw("Help for the editor you're in"), InputConstants.KEY_F1, InputConstants.KEY_LSHIFT);
    public static final KeyCombo NEXT = new KeyCombo("next", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_NEXT, InputConstants.KEY_RIGHT).repeatable();
    public static final KeyCombo PLAUSE = new KeyCombo("plause", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, InputConstants.KEY_SPACE);
    public static final KeyCombo BEFEHLS_PALETTE = new KeyCombo("command_palette", L10n.lang("bbs.ui.keys.command_palette"), InputConstants.KEY_P, InputConstants.KEY_LCONTROL);
    public static final KeyCombo SKRIPT_KONSOLE = new KeyCombo("script_console", L10n.lang("bbs.ui.keys.script_console"), InputConstants.KEY_J, InputConstants.KEY_LCONTROL);
    public static final KeyCombo PREV = new KeyCombo("prev", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PREV, InputConstants.KEY_LEFT).repeatable();
    public static final KeyCombo REDO = new KeyCombo("redo", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_REDO, InputConstants.KEY_Y, InputConstants.KEY_LCONTROL);
    public static final KeyCombo UNDO = new KeyCombo("undo", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_UNDO, InputConstants.KEY_Z, InputConstants.KEY_LCONTROL);
    public static final KeyCombo COPY = new KeyCombo("copy", UIKeys.GENERAL_COPY, InputConstants.KEY_C, InputConstants.KEY_LCONTROL);
    public static final KeyCombo CUT = new KeyCombo("cut", UIKeys.GENERAL_CUT, InputConstants.KEY_X, InputConstants.KEY_LCONTROL);
    public static final KeyCombo PASTE = new KeyCombo("paste", UIKeys.GENERAL_PASTE, InputConstants.KEY_V, InputConstants.KEY_LCONTROL);
    public static final KeyCombo PRESETS = new KeyCombo("presets", UIKeys.GENERAL_PRESETS, InputConstants.KEY_V, InputConstants.KEY_LSHIFT, InputConstants.KEY_LCONTROL);
    public static final KeyCombo SAVE = new KeyCombo("save", UIKeys.GENERAL_SAVE, InputConstants.KEY_S, InputConstants.KEY_LCONTROL);
    public static final KeyCombo DELETE = new KeyCombo("delete", UIKeys.GENERAL_REMOVE, InputConstants.KEY_DELETE);
    public static final KeyCombo CONFIRM = new KeyCombo("confirm", UIKeys.GENERAL_CONFIRM, InputConstants.KEY_RETURN, InputConstants.KEY_LCONTROL);
    public static final KeyCombo CLOSE = new KeyCombo("close", UIKeys.GENERAL_CLOSE, InputConstants.KEY_ESCAPE);
    public static final KeyCombo UI_SCALE_INC = new KeyCombo("ui_scale_inc", UIKeys.KEYS_UI_SCALE_INC, InputConstants.KEY_EQUALS, InputConstants.KEY_LCONTROL).repeatable();
    public static final KeyCombo UI_SCALE_DEC = new KeyCombo("ui_scale_dec", UIKeys.KEYS_UI_SCALE_DEC, InputConstants.KEY_MINUS, InputConstants.KEY_LCONTROL).repeatable();

    /* Camera editor */
    public static final KeyCombo ADD_AT_CURSOR = new KeyCombo("add_at_cursor", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_CURSOR, InputConstants.KEY_A, InputConstants.KEY_LSHIFT).categoryKey("camera");
    public static final KeyCombo ADD_AT_TICK = new KeyCombo("add_at_tick", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_TICK, InputConstants.KEY_A, InputConstants.KEY_LALT).categoryKey("camera");
    public static final KeyCombo ADD_ON_TOP = new KeyCombo("add_on_top", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_ON_TOP, InputConstants.KEY_A, InputConstants.KEY_LSHIFT, InputConstants.KEY_LALT).categoryKey("camera");
    public static final KeyCombo CLIP_CUT = new KeyCombo("clip_cut", UIKeys.CAMERA_TIMELINE_CONTEXT_CUT, InputConstants.KEY_C, InputConstants.KEY_LALT).categoryKey("camera");
    public static final KeyCombo CLIP_DURATION = new KeyCombo("clip_duration", UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, InputConstants.KEY_M).categoryKey("camera");
    public static final KeyCombo CLIP_ENABLE = new KeyCombo("clip_enable", UIKeys.CAMERA_TIMELINE_KEYS_ENABLED, InputConstants.KEY_J).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_ALL = new KeyCombo("clip_select_all", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_ALL, InputConstants.KEY_A, InputConstants.KEY_LCONTROL).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK = new KeyCombo("clip_select_track", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK, InputConstants.KEY_A, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK_BEFORE = new KeyCombo("clip_select_track_before", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK_BEFORE, InputConstants.KEY_COMMA, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK_AFTER = new KeyCombo("clip_select_track_after", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK_AFTER, InputConstants.KEY_PERIOD, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_LAYER_UP = new KeyCombo("clip_layer_up", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_LAYER_UP, InputConstants.KEY_UP, InputConstants.KEY_LALT).repeatable().categoryKey("camera");
    public static final KeyCombo CLIP_LAYER_DOWN = new KeyCombo("clip_layer_down", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_LAYER_DOWN, InputConstants.KEY_DOWN, InputConstants.KEY_LALT).repeatable().categoryKey("camera");
    public static final KeyCombo CLIP_SHIFT = new KeyCombo("clip_shift", UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT, InputConstants.KEY_S, InputConstants.KEY_LSHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_AFTER = new KeyCombo("clip_select_after", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_SELECT_AFTER, InputConstants.KEY_PERIOD, InputConstants.KEY_LCONTROL).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_BEFORE = new KeyCombo("clip_select_before", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_SELECT_BEFORE, InputConstants.KEY_COMMA, InputConstants.KEY_LCONTROL).categoryKey("camera");
    public static final KeyCombo FLIGHT = new KeyCombo("flight", UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT, InputConstants.KEY_F).categoryKey("camera");
    public static final KeyCombo LOOPING = new KeyCombo("looping", UIKeys.CAMERA_EDITOR_KEYS_MODES_LOOPING, InputConstants.KEY_L).categoryKey("camera");
    public static final KeyCombo LOOPING_SET_MAX = new KeyCombo("looping_set_max", UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MAX, InputConstants.KEY_RBRACKET).categoryKey("camera");
    public static final KeyCombo LOOPING_SET_MIN = new KeyCombo("looping_set_min", UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MIN, InputConstants.KEY_LBRACKET).categoryKey("camera");
    public static final KeyCombo NEXT_CLIP = new KeyCombo("next_clip", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_NEXT_CLIP, InputConstants.KEY_RIGHT, InputConstants.KEY_LSHIFT).repeatable().categoryKey("camera");
    public static final KeyCombo PREV_CLIP = new KeyCombo("prev_clip", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PREV_CLIP, InputConstants.KEY_LEFT, InputConstants.KEY_LSHIFT).repeatable().categoryKey("camera");
    public static final KeyCombo JUMP_FORWARD = new KeyCombo("jump_forward", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_JUMP_FORWARD, InputConstants.KEY_UP).repeatable().categoryKey("camera");
    public static final KeyCombo JUMP_BACKWARD = new KeyCombo("jump_backward", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_JUMP_BACKWARD, InputConstants.KEY_DOWN).repeatable().categoryKey("camera");
    public static final KeyCombo FADE_IN = new KeyCombo("fade_in", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FADE_IN, InputConstants.KEY_COMMA).categoryKey("camera");
    public static final KeyCombo FADE_OUT = new KeyCombo("fade_out", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FADE_OUT, InputConstants.KEY_PERIOD).categoryKey("camera");

    /* Flight mode keybinds */
    public static final KeyCombo FLIGHT_FORWARD = new KeyCombo("flight_forward", UIKeys.CAMERA_FLIGHT_FLIGHT_FORWARD, InputConstants.KEY_W).categoryKey("flight");
    public static final KeyCombo FLIGHT_BACKWARD = new KeyCombo("flight_backward", UIKeys.CAMERA_FLIGHT_FLIGHT_BACKWARD, InputConstants.KEY_S).categoryKey("flight");
    public static final KeyCombo FLIGHT_LEFT = new KeyCombo("flight_left", UIKeys.CAMERA_FLIGHT_FLIGHT_LEFT, InputConstants.KEY_A).categoryKey("flight");
    public static final KeyCombo FLIGHT_RIGHT = new KeyCombo("flight_right", UIKeys.CAMERA_FLIGHT_FLIGHT_RIGHT, InputConstants.KEY_D).categoryKey("flight");
    public static final KeyCombo FLIGHT_UP = new KeyCombo("flight_up", UIKeys.CAMERA_FLIGHT_FLIGHT_UP, InputConstants.KEY_SPACE).categoryKey("flight");
    public static final KeyCombo FLIGHT_DOWN = new KeyCombo("flight_down", UIKeys.CAMERA_FLIGHT_FLIGHT_DOWN, InputConstants.KEY_LSHIFT).categoryKey("flight");
    public static final KeyCombo FLIGHT_TILT_UP = new KeyCombo("flight_tilt_up", UIKeys.CAMERA_FLIGHT_FLIGHT_TILT_UP, InputConstants.KEY_UP).categoryKey("flight");
    public static final KeyCombo FLIGHT_TILT_DOWN = new KeyCombo("flight_tilt_down", UIKeys.CAMERA_FLIGHT_FLIGHT_TILT_DOWN, InputConstants.KEY_DOWN).categoryKey("flight");
    public static final KeyCombo FLIGHT_PAN_LEFT = new KeyCombo("flight_pan_left", UIKeys.CAMERA_FLIGHT_FLIGHT_PAN_LEFT, InputConstants.KEY_LEFT).categoryKey("flight");
    public static final KeyCombo FLIGHT_PAN_RIGHT = new KeyCombo("flight_pan_right", UIKeys.CAMERA_FLIGHT_FLIGHT_PAN_RIGHT, InputConstants.KEY_RIGHT).categoryKey("flight");

    /* Dashboard */
    public static final KeyCombo OPEN_UTILITY_PANEL = new KeyCombo("utility_panel", UIKeys.UTILITY_TITLE, InputConstants.KEY_F6).categoryKey("dashboard");
    public static final KeyCombo OPEN_DATA_MANAGER = new KeyCombo("data_manager", UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER, InputConstants.KEY_N).categoryKey("dashboard");
    public static final KeyCombo OPEN_NEW_TAB = new KeyCombo("new_tab", UIKeys.PANELS_KEYS_OPEN_NEW_TAB, InputConstants.KEY_N, InputConstants.KEY_LCONTROL).categoryKey("dashboard");
    public static final KeyCombo TOGGLE_VISIBILITY = new KeyCombo("toggle", UIKeys.DASHBOARD_CONTEXT_TOGGLE_VISIBILITY, InputConstants.KEY_F1).categoryKey("dashboard");
    public static final KeyCombo TOGGLE_DEBUG = new KeyCombo("toggle_debug", UIKeys.DASHBOARD_KEYS_TOGGLE_DEBUG, InputConstants.KEY_F5).categoryKey("dashboard");

    /* Forms */
    public static final KeyCombo FORMS_FOCUS = new KeyCombo("focus", UIKeys.FORMS_LIST_CONTEXT_FOCUS, InputConstants.KEY_F, InputConstants.KEY_LCONTROL).categoryKey("forms");
    public static final KeyCombo FORMS_PICK = new KeyCombo("pick", UIKeys.GENERAL_PICK, InputConstants.KEY_P).categoryKey("forms");
    public static final KeyCombo FORMS_EDIT = new KeyCombo("edit", UIKeys.GENERAL_EDIT, InputConstants.KEY_E).categoryKey("forms");
    public static final KeyCombo FORMS_PICK_ALT = new KeyCombo("pick_alt", UIKeys.GENERAL_PICK, InputConstants.KEY_P, InputConstants.KEY_LSHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_EDIT_ALT = new KeyCombo("edit_alt", UIKeys.GENERAL_EDIT, InputConstants.KEY_E, InputConstants.KEY_LSHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_PICK_TEXTURE = new KeyCombo("pick_texture", UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, InputConstants.KEY_P, InputConstants.KEY_LSHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_OPEN_STATES_EDITOR = new KeyCombo("open_states_editor", UIKeys.FORMS_EDITOR_STATES_OPEN, InputConstants.KEY_BACKSLASH).categoryKey("forms");
    public static final KeyCombo FORMS_TOGGLE_BODY_PART_GIZMO = new KeyCombo("toggle_body_part_gizmo", UIKeys.FORMS_EDITOR_BODY_PART_GIZMO, InputConstants.KEY_Q, InputConstants.KEY_LCONTROL).categoryKey("forms");

    /* Pixel editor */
    public static final KeyCombo PIXEL_SWAP = new KeyCombo("swap", UIKeys.TEXTURES_KEYS_SWAP, InputConstants.KEY_X).categoryKey("pixels");
    public static final KeyCombo PIXEL_PICK = new KeyCombo("pick", UIKeys.TEXTURES_KEYS_PICK, InputConstants.KEY_R).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_BRUSH = new KeyCombo("tool_brush", UIKeys.TEXTURES_KEYS_TOOL_BRUSH, InputConstants.KEY_B).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_ERASER = new KeyCombo("tool_eraser", UIKeys.TEXTURES_KEYS_TOOL_ERASER, InputConstants.KEY_E).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_MOVE = new KeyCombo("tool_move", UIKeys.TEXTURES_KEYS_TOOL_MOVE, InputConstants.KEY_V).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_FILL = new KeyCombo("tool_fill", UIKeys.TEXTURES_KEYS_TOOL_FILL, InputConstants.KEY_F).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_SELECTION = new KeyCombo("tool_selection", UIKeys.TEXTURES_TOOLS_SELECTION, InputConstants.KEY_M).categoryKey("pixels");
    public static final KeyCombo PIXEL_DESELECT = new KeyCombo("deselect", UIKeys.TEXTURES_DESELECT, InputConstants.KEY_D, InputConstants.KEY_LCONTROL).categoryKey("pixels");
    public static final KeyCombo PIXEL_COPY_HEX = new KeyCombo("copy_hex", UIKeys.TEXTURES_VIEWER_CONTEXT_COPY_HEX, InputConstants.KEY_C, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_BRUSH_DEC = new KeyCombo("brush_dec", UIKeys.TEXTURES_KEYS_BRUSH_DEC, InputConstants.KEY_LBRACKET).repeatable().categoryKey("pixels");
    public static final KeyCombo PIXEL_BRUSH_INC = new KeyCombo("brush_inc", UIKeys.TEXTURES_KEYS_BRUSH_INC, InputConstants.KEY_RBRACKET).repeatable().categoryKey("pixels");

    /* Keyframes */
    public static final KeyCombo KEYFRAMES_ENABLE = new KeyCombo("keyframes_enable", L10n.lang("bbs.ui.keyframes.keys.enabled"), InputConstants.KEY_J).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_MAXIMIZE = new KeyCombo("maximize", UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, InputConstants.KEY_HOME).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_FIT_SELECTED = new KeyCombo("fit_selected", L10n.lang("bbs.ui.keyframes.graph.fit_selected"), InputConstants.KEY_HOME, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_ALL = new KeyCombo("select_all", UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, InputConstants.KEY_A, InputConstants.KEY_LCONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK = new KeyCombo("select_track", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK, InputConstants.KEY_A, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK_LEFT = new KeyCombo("select_track_left", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK_LEFT, InputConstants.KEY_COMMA, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK_RIGHT = new KeyCombo("select_track_right", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK_RIGHT, InputConstants.KEY_PERIOD, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_INTERP = new KeyCombo("interp", UIKeys.KEYFRAMES_KEYS_TOGGLE_INTERP, InputConstants.KEY_T, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_LEFT = new KeyCombo("select_left", UIKeys.KEYFRAMES_KEYS_SELECT_LEFT, InputConstants.KEY_COMMA, InputConstants.KEY_LCONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_RIGHT = new KeyCombo("select_right", UIKeys.KEYFRAMES_KEYS_SELECT_RIGHT, InputConstants.KEY_PERIOD, InputConstants.KEY_LCONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_SAME = new KeyCombo("select_same", UIKeys.KEYFRAMES_KEYS_SELECT_SAME, InputConstants.KEY_L).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SCALE_TIME = new KeyCombo("scale_time", UIKeys.KEYFRAMES_KEYS_SCALE_TIME, InputConstants.KEY_V).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_STACK_KEYFRAMES = new KeyCombo("stack_keyframes", UIKeys.KEYFRAMES_KEYS_STACK_KEYFRAMES, InputConstants.KEY_B).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_PREV = new KeyCombo("select_prev", UIKeys.KEYFRAMES_KEYS_SELECT_PREV, InputConstants.KEY_LBRACKET).repeatable().categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_NEXT = new KeyCombo("select_next", UIKeys.KEYFRAMES_KEYS_SELECT_NEXT, InputConstants.KEY_RBRACKET).repeatable().categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SPREAD = new KeyCombo("spread", UIKeys.KEYFRAMES_CONTEXT_SPREAD, InputConstants.KEY_B, InputConstants.KEY_LALT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_ADJUST_VALUES = new KeyCombo("adjust_values", UIKeys.KEYFRAMES_CONTEXT_ADJUST_VALUES, InputConstants.KEY_N, InputConstants.KEY_LSHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_FLIP = new KeyCombo("flip", UIKeys.KEYFRAMES_CONTEXT_FLIP, InputConstants.KEY_F, InputConstants.KEY_LCONTROL).categoryKey("keyframes");

    /* World menu */
    public static final KeyCombo CYCLE_PANELS = new KeyCombo("cycle_panels", UIKeys.WORLD_KEYS_CYCLE_PANELS, InputConstants.KEY_TAB).categoryKey("world");
    /* Alt+Z wie Blenders X-Ray — der User arbeitet in Blender, das Kürzel sitzt dort in den Fingern.
     *
     * ⛔ GLFW_KEY_Y, NICHT _Z. Die GLFW-Konstanten benennen PHYSISCHE Tasten nach dem
     * US-Layout. Auf der deutschen QWERTZ-Tastatur des Users sitzt an der Stelle von
     * GLFW_KEY_Z (90) die Taste mit der Aufschrift Y — und umgekehrt. Mit _Z belegt tat
     * Alt+Z bei ihm nichts (2026-07-29 gemeldet und nachgemessen); BBS zeigte die Belegung
     * sogar korrekt lokalisiert als "Alt + Y" an. _Y trifft die Taste, auf der Z steht. */
    public static final KeyCombo WIREFRAME = new KeyCombo("wireframe", UIKeys.WORLD_KEYS_WIREFRAME, InputConstants.KEY_Y, InputConstants.KEY_LALT).categoryKey("world");

    /* Transformations */
    public static final KeyCombo TRANSFORMATIONS_TRANSLATE = new KeyCombo("translate", UIKeys.TRANSFORMS_TRANSLATE, InputConstants.KEY_G).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_SCALE = new KeyCombo("scale", UIKeys.TRANSFORMS_SCALE, InputConstants.KEY_S).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_ROTATE = new KeyCombo("rotate", UIKeys.TRANSFORMS_ROTATE, InputConstants.KEY_R).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_COMBINED = new KeyCombo("combined", UIKeys.TRANSFORMS_COMBINED, InputConstants.KEY_T).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_X = new KeyCombo("x", UIKeys.GENERAL_X, InputConstants.KEY_X).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_Y = new KeyCombo("y", UIKeys.GENERAL_Y, InputConstants.KEY_Y).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_Z = new KeyCombo("z", UIKeys.GENERAL_Z, InputConstants.KEY_Z).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_TOGGLE_AXES = new KeyCombo("toggle_axes", UIKeys.TRANSFORMS_KEYS_TOGGLE_AXES, InputConstants.KEY_F8).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_HIDE_GIZMO = new KeyCombo("hide_gizmo", UIKeys.TRANSFORMS_KEYS_HIDE_GIZMO, InputConstants.KEY_LALT).categoryKey("transformations");
    /* The combo id is the settings key a custom binding is stored under, so it stays
     * "toggle_local" — the name this key had when it toggled the local flag — even
     * though it now opens the space list. Renaming it would silently reset everyone's
     * rebound key to the default. */
    public static final KeyCombo TRANSFORMATIONS_SPACE_MENU = new KeyCombo("toggle_local", UIKeys.TRANSFORMS_SPACE_OPEN, InputConstants.KEY_Q).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_ROTATION_MODE = new KeyCombo("rotation_mode", UIKeys.TRANSFORMS_KEYS_ROTATION_MODE, InputConstants.KEY_Q, InputConstants.KEY_LSHIFT).categoryKey("transformations");
    /** Shift+R as in BBS FS (Blender: Alt+G/R/S): the bone back to rest without the context menu */
    public static final KeyCombo TRANSFORMATIONS_RESET = new KeyCombo("reset", UIKeys.TRANSFORMS_CONTEXT_RESET, InputConstants.KEY_R, InputConstants.KEY_LSHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_TOGGLE_FIX = new KeyCombo("toggle_fix", UIKeys.TRANSFORMS_KEYS_TOGGLE_FIX, InputConstants.KEY_Y).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_MIRROR_EDIT = new KeyCombo("mirror_edit", UIKeys.TRANSFORMS_KEYS_MIRROR_EDIT, InputConstants.KEY_F, InputConstants.KEY_LSHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_FLIP_POSE = new KeyCombo("flip_pose", UIKeys.POSE_CONTEXT_FLIP_POSE, InputConstants.KEY_F, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_SEARCH_BONE = new KeyCombo("search_bone", UIKeys.TRANSFORMS_KEYS_SEARCH_BONE, InputConstants.KEY_F, InputConstants.KEY_LCONTROL).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_COPY_WORLD = new KeyCombo("copy_world", UIKeys.TRANSFORMS_CONTEXT_COPY_WORLD, InputConstants.KEY_C, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT, InputConstants.KEY_LALT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_PASTE_WORLD = new KeyCombo("paste_world", UIKeys.TRANSFORMS_CONTEXT_PASTE_WORLD, InputConstants.KEY_V, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT, InputConstants.KEY_LALT).categoryKey("transformations");

    /* Film controller */
    public static final KeyCombo FILM_CONTROLLER_START_RECORDING = new KeyCombo("start_recording", UIKeys.FILM_CONTROLLER_KEYS_START_RECORDING, InputConstants.KEY_R, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_INSERT_FRAME = new KeyCombo("insert_frame", UIKeys.FILM_CONTROLLER_KEYS_INSERT_FRAME, InputConstants.KEY_I).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_CONTROL = new KeyCombo("toggle_control", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL, InputConstants.KEY_H).categoryKey("film_controller");
    /* Mouse 4 (id -3): Blender's View Selected on a side button. Was hard-wired in
     * UIReplaysEditor#clickViewport, so a preset that put Mouse 4 elsewhere lost it with no
     * way back (owner, 2026-10-14). */
    public static final KeyCombo FILM_CONTROLLER_ZOOM_TO_REPLAY = new KeyCombo("zoom_to_replay", mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.film.controller.keys.zoom_to_replay"), -3).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ORBIT_MODE = new KeyCombo("toggle_orbit_mode", UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE, InputConstants.KEY_F3).categoryKey("film_controller");
    /* Direkt in EINEN kamera-modus springen, statt mit toggle_orbit_mode erst das
     * auswahl-menue zu oeffnen. Reihenfolge und beschriftung sind dieselben wie im menue.
     * Strg + ziffer, weil die nackten ziffern schon den replay-tabs und den
     * aufnahme-gruppen gehoeren — die bleiben erreichbar, seit die spezifischere
     * bindung gewinnt (BBSSettings.editorKeybindSpecificWins). */
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_CAMERA = new KeyCombo("camera_mode_camera", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_CAMERA, InputConstants.KEY_1, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_FREE = new KeyCombo("camera_mode_free", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_FREE, InputConstants.KEY_2, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_ORBIT = new KeyCombo("camera_mode_orbit", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_ORBIT, InputConstants.KEY_3, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_FIRST_PERSON = new KeyCombo("camera_mode_first_person", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_FIRST_PERSON, InputConstants.KEY_4, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_THIRD_PERSON_BACK = new KeyCombo("camera_mode_third_person_back", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_THIRD_PERSON_BACK, InputConstants.KEY_5, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CAMERA_MODE_THIRD_PERSON_FRONT = new KeyCombo("camera_mode_third_person_front", UIKeys.FILM_CONTROLLER_KEYS_CAMERA_MODE_THIRD_PERSON_FRONT, InputConstants.KEY_6, InputConstants.KEY_LCONTROL).categoryKey("film_controller");

    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ORTHO = new KeyCombo("toggle_ortho", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, InputConstants.KEY_NUMPAD5).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TELEPORT_ORBIT = new KeyCombo("teleport_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_TELEPORT_ORBIT, InputConstants.KEY_C).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_ATTACH_ORBIT = new KeyCombo("attach_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, InputConstants.KEY_Z, InputConstants.KEY_LSHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_REPLAY_MENU = new KeyCombo("toggle_replay_menu", UIKeys.FILM_CONTROLLER_KEYS_OPEN_REPLAYS, InputConstants.KEY_F4).categoryKey("film_controller");
    /* Alt + F ("find"): Strg + F ist in vier anderen kategorien schon eine suche
     * (bone, model-bone, textur, form-fokus) — mit alt kollidiert die box mit keiner
     * davon, auch wenn beide elemente gleichzeitig lauschen. */
    public static final KeyCombo FILM_CONTROLLER_QUICK_REPLAY = new KeyCombo("quick_replay", UIKeys.FILM_CONTROLLER_KEYS_QUICK_REPLAY, InputConstants.KEY_F, InputConstants.KEY_LALT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR = new KeyCombo("move_replay_to_cursor", UIKeys.FILM_CONTROLLER_KEYS_MOVE_REPLAY_TO_CURSOR, InputConstants.KEY_G, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_RESTART_ACTIONS = new KeyCombo("restart_actions", UIKeys.FILM_CONTROLLER_KEYS_RESTART_ACTIONS, InputConstants.KEY_R, InputConstants.KEY_LALT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ONION_SKIN = new KeyCombo("toggle_onion_skin", UIKeys.FILM_CONTROLLER_ONION_SKIN_KEYS_TOGGLE, InputConstants.KEY_O).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_MOTION_PATH = new KeyCombo("toggle_motion_path", UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYS_TOGGLE, InputConstants.KEY_M).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_MOTION_PATH_PIN = new KeyCombo("toggle_motion_path_pin", UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYS_TOGGLE_PIN, InputConstants.KEY_M, InputConstants.KEY_LSHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_OPEN_REPLAYS = new KeyCombo("toggle_replays", UIKeys.FILM_CONTROLLER_KEYS_OPEN_REPLAYS, InputConstants.KEY_RSHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CYCLE_EDITORS = new KeyCombo("cycle_editors", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputConstants.KEY_GRAVE).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ACTIONS = new KeyCombo("toggle_actions", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ACTIONS, InputConstants.KEY_GRAVE, InputConstants.KEY_LSHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_NEXT_DOCK_TAB = new KeyCombo("next_dock_tab", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputConstants.KEY_TAB, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_PREV_DOCK_TAB = new KeyCombo("prev_dock_tab", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputConstants.KEY_TAB, InputConstants.KEY_LSHIFT, InputConstants.KEY_LCONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_PREV_REPLAY = new KeyCombo("prev_replay", UIKeys.FILM_CONTROLLER_KEYS_PREV_REPLAY, InputConstants.KEY_PAGEUP).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_NEXT_REPLAY = new KeyCombo("next_replay", UIKeys.FILM_CONTROLLER_KEYS_NEXT_REPLAY, InputConstants.KEY_PAGEDOWN).categoryKey("film_controller");

    /* Replays editor */
    /** Find a track of the open replay (BBS FS 0c1708ac7 uses Ctrl+F, which flips keys here). */
    public static final KeyCombo FILM_TRACK_SEARCH = new KeyCombo("track_search", L10n.lang("bbs.ui.film.track_search.title"), InputConstants.KEY_F, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_ENABLE = new KeyCombo("replays_enable", UIKeys.CAMERA_TIMELINE_KEYS_ENABLED, InputConstants.KEY_J).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_1 = new KeyCombo("tab_1", UIKeys.FILM_REPLAY_TAB_1, InputConstants.KEY_1).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_2 = new KeyCombo("tab_2", UIKeys.FILM_REPLAY_TAB_2, InputConstants.KEY_2).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_3 = new KeyCombo("tab_3", UIKeys.FILM_REPLAY_TAB_3, InputConstants.KEY_3).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_4 = new KeyCombo("tab_4", UIKeys.FILM_REPLAY_TAB_4, InputConstants.KEY_4).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_5 = new KeyCombo("tab_5", UIKeys.FILM_REPLAY_TAB_5, InputConstants.KEY_5).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_DUPE = new KeyCombo("replays_dupe", UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, InputConstants.KEY_D, InputConstants.KEY_LCONTROL).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_SELECT_ALL = new KeyCombo("replays_select_all", UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, InputConstants.KEY_A, InputConstants.KEY_LCONTROL).categoryKey("replays_editor");

    /* Recording groups */
    public static final KeyCombo RECORDING_GROUP_ALL = new KeyCombo("all", UIKeys.FILM_GROUPS_ALL, InputConstants.KEY_1).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_LEFT_STICK = new KeyCombo("left_stick", UIKeys.FILM_GROUPS_LEFT_STICK, InputConstants.KEY_2).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_RIGHT_STICK = new KeyCombo("right_stick", UIKeys.FILM_GROUPS_RIGHT_STICK, InputConstants.KEY_3).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_TRIGGERS = new KeyCombo("triggers", UIKeys.FILM_GROUPS_TRIGGERS, InputConstants.KEY_4).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_EXTRA_1 = new KeyCombo("extra_1", UIKeys.FILM_GROUPS_EXTRA_1, InputConstants.KEY_5).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_EXTRA_2 = new KeyCombo("extra_2", UIKeys.FILM_GROUPS_EXTRA_2, InputConstants.KEY_6).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_ONLY_POSITION = new KeyCombo("only_position", UIKeys.FILM_GROUPS_ONLY_POSITION, InputConstants.KEY_7).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_ONLY_ROTATION = new KeyCombo("only_rotation", UIKeys.FILM_GROUPS_ONLY_ROTATION, InputConstants.KEY_8).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_POS_ROT = new KeyCombo("pos_rot", UIKeys.FILM_GROUPS_ONLY_POS_ROT, InputConstants.KEY_9).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_TRANSFORM = new KeyCombo("transform", UIKeys.FILM_GROUPS_TRANSFORM, InputConstants.KEY_0).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_OUTSIDE = new KeyCombo("outside", UIKeys.FILM_GROUPS_OUTSIDE, InputConstants.KEY_R).categoryKey("recording_groups");

    /* Model block editor */
    public static final KeyCombo MODEL_BLOCKS_MOVE_TO = new KeyCombo("move_to", UIKeys.MODEL_BLOCKS_KEYS_MOVE_TO, InputConstants.KEY_G, InputConstants.KEY_LCONTROL).categoryKey("model_blocks");
    public static final KeyCombo MODEL_BLOCKS_TOGGLE_RENDERING = new KeyCombo("toggle_rendering", UIKeys.MODEL_BLOCKS_KEYS_TOGGLE_RENDERING, InputConstants.KEY_F7).categoryKey("model_blocks");
    public static final KeyCombo MODEL_BLOCKS_TELEPORT = new KeyCombo("teleport", UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, InputConstants.KEY_T, InputConstants.KEY_LCONTROL).categoryKey("model_blocks");

    /* Model editor */
    public static final KeyCombo MODEL_EDITOR_EXPAND_ALL = new KeyCombo("expand_all", UIKeys.MODEL_EDITOR_KEYS_EXPAND_ALL, InputConstants.KEY_E, InputConstants.KEY_LCONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_COLLAPSE_ALL = new KeyCombo("collapse_all", UIKeys.MODEL_EDITOR_KEYS_COLLAPSE_ALL, InputConstants.KEY_E, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_FIND_BONE = new KeyCombo("find_bone", UIKeys.MODEL_EDITOR_KEYS_FIND_BONE, InputConstants.KEY_F, InputConstants.KEY_LCONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_OPEN_HISTORY = new KeyCombo("open_history", UIKeys.MODEL_EDITOR_OPEN_HISTORY, InputConstants.KEY_H, InputConstants.KEY_LCONTROL).categoryKey("model_editor");

    /* Texture picker */
    public static final KeyCombo TEXTURE_PICKER_FIND = new KeyCombo("find", UIKeys.TEXTURE_KEYS_FIND_ALL, InputConstants.KEY_F, InputConstants.KEY_LCONTROL).categoryKey("texture_picker");
}
