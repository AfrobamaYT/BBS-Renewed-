bl_info = {
    "name": "BBS Live Bridge",
    "version": (0, 34),   # 0.34: protected actor-root scale curves
    "blender": (4, 0, 0),
    "author": "Blender-BBS-Integration",
    "location": "N-Panel > BBS Bridge",
    "description": "Live bidirectional sync between Blender and BBS (Minecraft) — camera + world + native/BOBJ rigged actors + hybrid rendering + film/playhead sync.",
    "category": "Import-Export",
}

# ============================================================================
# BBS LIVE BRIDGE — Blender-seite.
#   Phase 1: kamera bidirektional live.
#   Phase 2: MC-welt als OBJ nach Blender holen (WORLD_EXPORT -> _RESULT -> import).
#   Phase 3: film-actors als animierte objekte holen (ACTORS_EXPORT -> _RESULT -> F-curves).
#   Phase 3b: actor = ECHTES BOBJ-modell (mesh + armature + weights + skin) statt empty;
#             empty bleibt der fallback wenn kein modell auffindbar ist. Siehe BOBJ-block.
#   Phase 3c: optionaler Blender-native actor aus config.json `blender_source` fuer den
#             Hybrid-Render; BOBJ bleibt die schnelle interaktive BBS-Darstellung.
#
# Verbindet sich per TCP an den BBS-socket-server (localhost:9880, null-byte-
# delimited JSON). Muster: non-blocking socket + bpy.app.timers-poll (NICHT
# threads — Blender verbietet bpy in threads). Echo-schutz via state._applying_remote-
# flag im try/finally. Konflikt: last-write-wins per seq.
#
# Mapping Blender<->BBS (verifiziert aus Fingle's exporter):
#   bbs_x = bl_x ; bbs_y = bl_z ; bbs_z = -bl_y
#   bbs_pitch = 90 - bl_pitch_deg ; bbs_yaw = -bl_yaw_deg ; bbs_roll = bl_roll_deg
#   invers (BBS->Blender):
#   bl_x = bbs_x ; bl_y = -bbs_z ; bl_z = bbs_y
#   bl_pitch_deg = 90 - bbs_pitch ; bl_yaw_deg = -bbs_yaw ; bl_roll_deg = bbs_roll
#
# WICHTIG für den OBJ-import: dieselbe inverse (bl_x=x, bl_y=-z, bl_z=y) IST
# exakt Blenders standard-achsen-konvertierung Y-up -> Z-up (forward=-Z, up=Y).
# Solange BBS die vertices in ABSOLUTEN MC-weltkoordinaten schreibt, landet die
# geometrie damit deckungsgleich unter der gesyncten kamera. Nicht dran drehen.
#
# PAKET-LAYOUT (seit dem split, 2026-07-16): state.py (globals/konstanten, nur daten) ->
# core.py (socket-io + kamera-mapping) -> world_import.py (PHASE 2) -> bobj_import.py /
# native_model.py (BOBJ-vorschau / native Hybrid-Quelle) -> actors.py (PHASE 3,
# importiert beide; entity_import nur LAZY im
# mob-zweig) -> film_camera_import.py (Task 1.3) -> replay_anim.py (Task 4.3a: mob-replay-
# pose auf pose-bones) -> entity_import.py (Task 4.1: ENTITY_EXPORT-consumer, mob-mesh+
# armature -- importiert replay_anim fuer die raum-konstante) -> fp_hand.py (Task 5.1:
# first-person hand+HUD-compositing-rig, rein lokal, kein socket-request; zieht LAZY
# fp_arm.py dazu -- 5.1b-rest W9-B5: echter alex-arm aus slim.bobj + vanilla-FP-pose,
# importiert bobj_import) -> fp_channels.py (5.1b-kanaele, lazy aus fp_hand) -> ui.py
# (panel+operators, importiert alle obigen) -> tick.py (der timer, zieht core/world_import/
# actors/film_camera_import/replay_anim/entity_import zusammen -- fp_hand NICHT, das
# braucht keinen tick) -> hier (register/unregister). Frueher EINE datei
# (blender_bbs_bridge.py, siehe backup_pre_split_*.py).
# ============================================================================

import bpy

from . import state
from . import model_adoption
from .model_adoption import glTF2ImportUserExtension
from . import actor_document, actor_leases, actor_authoring, actor_pose_preview, native_controls, camera_preview
from . import fp_hand
from . import limb_select_ui
from . import hud_overlay
from . import light_export
from . import actor_illusion
from . import shot_freeze          # auftrag REDACTED §2: PROP_FROZEN wird unten registriert
from .ui import classes, draw_actions_menu, _on_drive_camera_toggle, _on_drive_timeline_toggle
from .tick import _tick, _on_frame_change_post, _on_playback_pre, _on_playback_post


@bpy.app.handlers.persistent
def _bbs_illusion_load_post(_dummy):
    """Nach jedem .blend-load die illusion-delay-driver-namespace-funktion neu registrieren —
    ein gespeicherter film mit delay-trail hat scripted-driver, die `actor_illusion.bbs_illusion_fc_at`
    beim ersten frame-eval brauchen (der namespace überlebt kein file-load). Idempotent, wirft nie."""
    actor_illusion.register_drivers()


def _id_storage(name, default):
    """Expose the same persisted ID property used by the compositor's drivers."""
    def get_value(scene):
        return scene.get(name, default)
    def set_value(scene, value):
        scene[name] = value
    return {'get': get_value, 'set': set_value}


def register():
    model_adoption.register()
    actor_document.load_post(None)
    actor_authoring.register()
    actor_pose_preview.register()
    native_controls.register()
    limb_select_ui.register()
    camera_preview.register()
    for c in classes:
        bpy.utils.register_class(c)
    bpy.types.VIEW3D_MT_object.append(draw_actions_menu)
    bpy.types.VIEW3D_MT_pose.append(draw_actions_menu)
    bpy.types.Scene.bbsbridge_world_radius = bpy.props.IntProperty(
        name="Radius", default=32, min=1, max=256,
        description="Horizontal radius in blocks around the camera",
    )
    bpy.types.Scene.bbsbridge_import_models = bpy.props.BoolProperty(
        name="Import models", default=True,
        description="Import the actor's real BOBJ model (mesh + rig + skin) instead of "
                    "a placeholder empty. Falls back to an empty when no model is found",
    )
    bpy.types.Scene.bbsbridge_live_actors = bpy.props.BoolProperty(
        name="Live actors (poll fallback)", default=False,
        description="Task 1.6: BBS now PUSHES its actor animation the instant it changes, no "
                    "toggle needed for that. This only enables the OLD 0.5s poll as a fallback, "
                    "used automatically while no push has been seen for a few seconds (older "
                    "BBS build without the push tracker, or the push path stalled). It "
                    "overwrites the actors' F-curves in Blender either way",
    )
    # Auftrag REDACTED §2 — PERSISTIERT bewusst (kein SKIP_SAVE): ein eingefrorener shot
    # bleibt es auch nach speichern/neu oeffnen. Name ueber die konstante, damit
    # shot_freeze/render_bridge/ui nie einen anderen string benutzen (fp_hand-muster).
    setattr(bpy.types.Scene, shot_freeze.PROP_FROZEN, bpy.props.BoolProperty(
        name="Shot einfrieren", default=False,
        description="An: der automatische Render aus BBS importiert NICHTS mehr neu — "
                    "Kamera, Actors und Welt bleiben so, wie sie in dieser Szene stehen "
                    "(Rig, Keys, Material, Compositor). Ein expliziter Import per Knopf "
                    "sichert vorher den Stand als BBS_Backup_<zeit>. Aus (Default): jeder "
                    "Render importiert frisch aus BBS",
    ))
    bpy.types.Scene.bbsbridge_drive_camera = bpy.props.BoolProperty(
        name="Blender-Kamera in BBS nutzen", default=False, options={'SKIP_SAVE'},
        description="While on, this Blender camera's moves push live into BBS' camera (you "
                    "are the writer -- owner-per-field model). Off (default): BBS' camera can "
                    "never be touched from here, no matter what this camera does",
        update=_on_drive_camera_toggle,
    )
    bpy.types.Scene.bbsbridge_drive_timeline = bpy.props.BoolProperty(
        name="Drive timeline", default=False, options={'SKIP_SAVE'},
        description="Local Blender scrubs and playback sync automatically; enable this to keep "
                    "Blender in control until you turn it off (BBS can drive when released)",
        update=_on_drive_timeline_toggle,
    )
    # Task 5.1: fp_hand.py-rig-properties. FLOAT statt bool fuer die beiden toggles (erlaubt
    # spaeter weiches ein/ausblenden statt hartem an/aus, siehe plan-v2 5.3b "hud-ease
    # 0.25/tick") -- setattr mit den fp_hand.PROP_*-STRING-konstanten statt literalen namen,
    # damit die namen hier und die driver-data-paths in fp_hand.py NIE auseinanderlaufen
    # koennen (single-source-of-truth, siehe fp_hand.py-header).
    setattr(bpy.types.Scene, fp_hand.PROP_FOV, bpy.props.FloatProperty(
        **_id_storage(fp_hand.PROP_FOV, fp_hand.DEFAULT_FOV_DEG), name="Hand FOV", default=fp_hand.DEFAULT_FOV_DEG, min=1.0, max=170.0,
        description="Task 5.1: sichtfeld (GRAD, keine 'ANGLE'-subtype -- die wuerde intern in "
                    "radian konvertieren, der driver-ausdruck erwartet aber grad) NUR der "
                    "hand-kamera (BBS_FP_CamB), komplett isoliert von der welt-kamera-FOV. "
                    "Treibt camera.data.lens ueber einen scripted-driver (siehe "
                    "fp_hand._ensure_driver -- NIE .angle, Blender-5.2-falle)."))
    setattr(bpy.types.Scene, fp_hand.PROP_SHOW_HAND, bpy.props.FloatProperty(
        **_id_storage(fp_hand.PROP_SHOW_HAND, 1.0), name="Show hand", default=1.0, min=0.0, max=1.0, subtype='FACTOR',
        description="Task 5.1: alpha-over-factor des hand-compositing-layers (0=aus, 1=an). "
                    "Auf der WELT-szene (nicht der hand-szene) -- treibt den entsprechenden "
                    "AlphaOver-node in fp_hand.COMPOSITE_GROUP_NAME."))
    setattr(bpy.types.Scene, fp_hand.PROP_SHOW_HUD, bpy.props.FloatProperty(
        **_id_storage(fp_hand.PROP_SHOW_HUD, 1.0), name="Show HUD", default=1.0, min=0.0, max=1.0, subtype='FACTOR',
        description="Task 5.1: alpha-over-factor des HUD-compositing-layers (0=aus, 1=an). "
                    "Auf der WELT-szene, analog bbsfp_show_hand."))
    # Task 5.3b (HB-4): hud_overlay.py-composite-properties. Die 5 element-toggles (0..1
    # FLOAT wie die fp_hand-toggles: erlaubt spaeteres weiches ein/ausblenden) treiben je
    # einen AlphaOver-Factor-driver, gui-scale bestimmt die HUD-groesse, actor-uuid waehlt
    # den POV-subjekt. setattr mit den hud_overlay.PROP_*-STRING-konstanten (single-source-
    # of-truth mit den driver-data-paths, wie bei fp_hand).
    for prop_name, label in (
        (hud_overlay.PROP_SHOW_CROSSHAIR, "HUD Crosshair"),
        (hud_overlay.PROP_SHOW_HOTBAR, "HUD Hotbar"),
        (hud_overlay.PROP_SHOW_HEALTH, "HUD Health"),
        (hud_overlay.PROP_SHOW_HUNGER, "HUD Hunger"),
        (hud_overlay.PROP_SHOW_ARMOR, "HUD Armor"),
    ):
        setattr(bpy.types.Scene, prop_name, bpy.props.FloatProperty(
            **_id_storage(prop_name, 1.0), name=label, default=1.0, min=0.0, max=1.0, subtype='FACTOR',
            description="Task 5.3b: alpha-over-factor des %s-HUD-layers (0=aus, 1=an). "
                        "Auf der WELT-szene, treibt den zugehoerigen AlphaOver-node in "
                        "fp_hand.COMPOSITE_GROUP_NAME." % label))
    setattr(bpy.types.Scene, hud_overlay.PROP_GUI_SCALE, bpy.props.IntProperty(
        **_id_storage(hud_overlay.PROP_GUI_SCALE, hud_overlay.DEFAULT_GUI_SCALE), name="HUD GUI Scale", default=hud_overlay.DEFAULT_GUI_SCALE, min=1, max=8,
        description="Task 5.3b: MC-GUI-scale des HUD (vanilla-default auf 1080p = 4). "
                    "Bestimmt die HUD-element-groesse; ein re-build (HUD Build) wendet ihn an."))
    setattr(bpy.types.Scene, hud_overlay.PROP_HUD_ACTOR, bpy.props.StringProperty(
        **_id_storage(hud_overlay.PROP_HUD_ACTOR, ""), name="HUD Actor UUID", default="",
        description="Task 5.3b: bbs_uuid des POV-actors dessen hotbar/health/hunger der "
                    "HUD zeigt. Leer = auto (firstPerson-flag, sonst erster actor)."))
    # Phase 4 (Blender-lichter -> BBS-LightForm): das watt-niveau bei dem ein point/spot/area-
    # licht MC-blocklicht-level 15 (voll) erreicht. Szenen-abhaengig ("was ist hell"), darum
    # property statt konstante. light_export._energy_full_w() liest sie (getattr-fallback haelt
    # das modul auch ohne diese property lauffaehig).
    bpy.types.Scene.bbsbridge_light_energy_full = bpy.props.FloatProperty(
        name="Light energy = level 15", default=light_export.DEFAULT_ENERGY_FULL_W,
        min=1.0, max=1000000.0, subtype='POWER',
        description="Phase 4: die Blender-licht-energie (WATT) bei der ein point/spot/area-licht "
                    "auf MC-blocklicht-level 15 (voll) gemappt wird. Logarithmisches mapping. "
                    "Kleiner = lichter werden in BBS heller. GRENZE: BBS-LightForm traegt NUR "
                    "level(0-15)+enabled -- KEINE farbe/richtung/radius.")
    if not bpy.app.timers.is_registered(_tick):
        bpy.app.timers.register(_tick, first_interval=1.0, persistent=True)
    # Task 1.4: frame_change_post fires on every scrub/playback-advance/frame_set -- see
    # tick._on_frame_change_post for local scrub/playback ownership. Guarded against double-registration
    # (addon disable/enable during live-reload re-runs register()).
    if _on_frame_change_post not in bpy.app.handlers.frame_change_post:
        bpy.app.handlers.frame_change_post.append(_on_frame_change_post)
    if _on_playback_pre not in bpy.app.handlers.animation_playback_pre:
        bpy.app.handlers.animation_playback_pre.append(_on_playback_pre)
    if _on_playback_post not in bpy.app.handlers.animation_playback_post:
        bpy.app.handlers.animation_playback_post.append(_on_playback_post)
    # L4 (2026-07-19): illusion-delay-trail driver-namespace-funktion sofort registrieren (falls
    # schon ein film mit delay geladen ist) + auf jedes künftige file-load hängen (siehe handler).
    actor_illusion.register_drivers()
    if _bbs_illusion_load_post not in bpy.app.handlers.load_post:
        bpy.app.handlers.load_post.append(_bbs_illusion_load_post)
    for handler, function in ((bpy.app.handlers.load_post, actor_document.load_post),
                              (bpy.app.handlers.save_pre, actor_document.save_pre)):
        if function not in handler:
            handler.append(function)
    from . import render_job_ui
    render_job_ui.follow()
    print("[bbs-bridge] registered, tick loop started")


def unregister():
    model_adoption.unregister()
    from . import render_job_ui
    render_job_ui.shutdown()
    from .core import _stop_connection
    actor_authoring.unregister()
    actor_pose_preview.unregister()
    limb_select_ui.unregister()
    native_controls.unregister()
    camera_preview.unregister()
    bpy.types.VIEW3D_MT_object.remove(draw_actions_menu)
    bpy.types.VIEW3D_MT_pose.remove(draw_actions_menu)
    _stop_connection()
    actor_leases.forget()
    for handler, function in ((bpy.app.handlers.load_post, actor_document.load_post),
                              (bpy.app.handlers.save_pre, actor_document.save_pre)):
        if function in handler:
            handler.remove(function)
    actor_document.close()
    if bpy.app.timers.is_registered(_tick):
        bpy.app.timers.unregister(_tick)
    if _on_frame_change_post in bpy.app.handlers.frame_change_post:
        bpy.app.handlers.frame_change_post.remove(_on_frame_change_post)
    if _on_playback_pre in bpy.app.handlers.animation_playback_pre:
        bpy.app.handlers.animation_playback_pre.remove(_on_playback_pre)
    if _on_playback_post in bpy.app.handlers.animation_playback_post:
        bpy.app.handlers.animation_playback_post.remove(_on_playback_post)
    if _bbs_illusion_load_post in bpy.app.handlers.load_post:
        bpy.app.handlers.load_post.remove(_bbs_illusion_load_post)
    try:
        del bpy.types.Scene.bbsbridge_world_radius
    except AttributeError:
        pass
    try:
        del bpy.types.Scene.bbsbridge_import_models
    except AttributeError:
        pass
    try:
        del bpy.types.Scene.bbsbridge_live_actors
    except AttributeError:
        pass
    try:
        delattr(bpy.types.Scene, shot_freeze.PROP_FROZEN)   # auftrag REDACTED §2
    except AttributeError:
        pass
    try:
        del bpy.types.Scene.bbsbridge_drive_camera
    except AttributeError:
        pass
    try:
        del bpy.types.Scene.bbsbridge_drive_timeline
    except AttributeError:
        pass
    try:
        del bpy.types.Scene.bbsbridge_light_energy_full   # phase 4 (Blender-lichter -> BBS)
    except AttributeError:
        pass
    for prop_name in (fp_hand.PROP_FOV, fp_hand.PROP_SHOW_HAND, fp_hand.PROP_SHOW_HUD,
                      hud_overlay.PROP_SHOW_CROSSHAIR, hud_overlay.PROP_SHOW_HOTBAR,
                      hud_overlay.PROP_SHOW_HEALTH, hud_overlay.PROP_SHOW_HUNGER,
                      hud_overlay.PROP_SHOW_ARMOR, hud_overlay.PROP_GUI_SCALE,
                      hud_overlay.PROP_HUD_ACTOR):
        try:
            delattr(bpy.types.Scene, prop_name)
        except AttributeError:
            pass
    for c in reversed(classes):
        bpy.utils.unregister_class(c)


if __name__ == "__main__":
    register()
