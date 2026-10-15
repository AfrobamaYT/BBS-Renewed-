"""UI-panel + operators: BBSBRIDGE_PT_panel (N-panel), reconnect/import_world/
import_actors/import_film_camera/import_replay_anim/send_actors (W10-B1, mit
bestaetigungs-dialog — schreibt in den user-film)/render_film (W10-B1, offline).
Alle registrierbaren classes stehen in `classes` (siehe __init__.py)."""

import bpy
import math
import time

from . import state
from . import actor_outbox, actor_durable_ui, actor_leases, actor_shared_undo, actor_authoring
from . import fp_hand
from . import hud_overlay
from . import replay_anim
from . import film_camera_import   # W8-B4: new_request_id() fuer die FILM_CAMERA_EXPORT-korrelation
from . import actor_export         # W10-B1: send-button liest _status/_pending via attribut (modul-lokal)
from . import render_drive         # W10-B1: render-button, dito (_status lebt modul-lokal dort)
from . import shot_freeze          # auftrag REDACTED §2: eingefrorener shot + backup vor re-import
from . import recording_live
from . import film_structure
from . import marker_sync
from .core import _get_sync_camera, _connect, _send, _next_seq
from .world_import import _camera_region_fields


# ---- W7-B3: replay-anim helpers (quelle des dropdowns) -------------------------------
# state.py haelt KEINE replay-liste vom letzten ACTORS_EXPORT (gemessen 2026-07-17: die
# payload wird in actors._build_actors konsumiert und lebt danach NUR als custom-props
# auf den actor-root-objekten der MC_Actors-collection weiter: bbs_uuid/bbs_form_type/
# bbs_is_group, siehe actors._make_actor_root). Der dropdown liest deshalb DIESE objekte
# zurueck statt einen neuen state-spiegel einzufuehren — eine quelle, kein drift.

def _iter_actor_roots():
    """Actor-root-objekte des letzten ACTORS_EXPORT, stabil sortiert. Gruppen (BBS-ordner,
    keine eigene performance) fliegen raus; mesh-/armature-KINDER der modell-roots tragen
    kein bbs_uuid und fallen damit von selbst raus."""
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return []
    out = [o for o in coll.objects
           if o.get("bbs_uuid") and not o.get("bbs_is_group")]
    out.sort(key=lambda o: o.name.lower())
    return out


def _replay_length_ticks(actor_obj, scene):
    """Volle replay-laenge in TICKS aus den objekt-level F-curves des actor-roots ableiten
    (actors._build_fcurve schrieb frame = tick * (fps/20) — hier die inverse; keyframe_
    points sind nach fc.update() sortiert, der LETZTE punkt traegt das kurven-ende).
    Kein actor/keine kurven -> scene.frame_end als ehrlicher fallback (dokumentiert, kein
    geratener default). +1 weil java `ticks` als ANZAHL interpretiert (frames = startTick
    .. startTick+ticks-1, ReplayPoseReader-schleife) und der end-tick mit drin sein soll."""
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0
    max_frame = 0.0
    if actor_obj is not None:
        for coll in replay_anim._fcurve_collections(actor_obj):
            for fc in coll:
                pts = fc.keyframe_points
                if len(pts) and pts[-1].co[0] > max_frame:
                    max_frame = pts[-1].co[0]
    if max_frame <= 0.0:
        max_frame = float(scene.frame_end)
    return max(1, int(math.ceil(max_frame / tick_scale)) + 1)


def _on_drive_camera_toggle(self, context):
    """Update-callback von scene.bbsbridge_drive_camera (registriert in __init__.py).

    Owner-pro-feld-modell (2026-07-16, research-bestaetigt, siehe state.cam_owner-header):
    genau EIN schreiber gleichzeitig, NOBODY|BBS|BLENDER -- spiegelt java LiveCameraOverride
    .Owner. Dieser toggle ist der EINZIGE weg wie state.cam_owner je "BLENDER" wird: ohne
    diesen klick bleibt tick.py's hartes push-gate (schritt 2) zu, Blender kann die BBS-
    kamera nie ungefragt anfassen. SKIP_SAVE + kein carry-over bei reconnect (core._connect
    setzt cam_owner bei jeder neuen verbindung auf NOBODY): an/aus ist ein fluechtiger klick,
    kein dauerhafter besitz-anspruch.
    """
    if context.scene.bbsbridge_drive_camera:
        if hasattr(context.scene, 'bbsbridge_render_camera'):
            context.scene.bbsbridge_render_camera = False
        state.cam_owner = "BLENDER"
        _send({"type": "CAMERA_OWNER", "origin": "blender", "uuid": "camera",
               "seq": _next_seq(), "fields": {"owner": "BLENDER"}})
    else:
        state.cam_owner = "NOBODY"
        _send({"type": "CAMERA_OWNER", "origin": "blender", "uuid": "camera",
               "seq": _next_seq(), "fields": {"owner": "NOBODY"}})


def _on_drive_timeline_toggle(self, context):
    """Update-callback von scene.bbsbridge_drive_timeline (task 1.4, analog
    _on_drive_camera_toggle above -- see state.playhead_owner-header for the full owner-model
    writeup). SKIP_SAVE + kein carry-over bei reconnect (core._connect setzt playhead_owner bei
    jeder neuen verbindung auf NOBODY, wie cam_owner).

    Unlike the camera toggle, there is no dedicated PLAYHEAD_OWNER message type (java's
    PlayheadSync folds `owner` into the SAME PLAYHEAD envelope, see its class-doc) -- so BOTH
    branches here send a PLAYHEAD, not a second message type:
      * ON:  owner claim + an IMMEDIATE handshake value (the current frame -> tick), so BBS
             jumps to Blender's current frame the moment the user opts in, not only after the
             next scrub.
      * OFF: a PURE release ping (no "tick" key at all) -- java's handle() applies the owner
             key before it even looks for a tick, so this correctly releases without moving
             BBS' playhead anywhere.
    """
    scene = context.scene
    if scene.bbsbridge_drive_timeline:
        state.playhead_owner = "BLENDER"
        screen = context.screen
        playing = bool(screen is not None and getattr(screen, "is_animation_playing", False))
        from .tick import _push_playhead
        _push_playhead(scene, playing)
    else:
        state.playhead_owner = "NOBODY"
        _send({"type": "PLAYHEAD", "origin": "blender", "uuid": "playhead",
               "seq": _next_seq(), "fields": {"owner": "NOBODY"}})


# ---- UI-panel ----
class BBSBRIDGE_PT_panel(bpy.types.Panel):
    bl_label = "BBS Bridge"
    bl_idname = "BBSBRIDGE_PT_panel"
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'BBS Bridge'

    def draw(self, context):
        layout = self.layout
        layout.label(text="Status: %s" % state._status)
        protocol_row = layout.row()
        protocol_ok = (state.peer_protocol >= state.PROTOCOL_VERSION
                       and "recording_live_v1" in state.peer_capabilities)
        protocol_row.alert = state._connected and not protocol_ok
        protocol_row.label(
            text="BBS protocol %s / v%s" % (state.peer_protocol, state.peer_version or "?"),
            icon='CHECKMARK' if protocol_ok else 'ERROR')
        cam = _get_sync_camera()
        layout.label(text="Sync cam: %s" % (cam.name if cam else "(none)"))
        if hasattr(context.scene, "bbsbridge_drive_camera"):
            layout.prop(context.scene, "bbsbridge_drive_camera", icon='CAMERA_DATA')
            layout.label(text="Owner: %s" % state.cam_owner)
        layout.operator("bbsbridge.reconnect", icon='FILE_REFRESH')

        # ---- Phase 2: welt-import ----
        layout.separator()
        world_row = layout.row()
        # Task 3.2-addon (W6-B3): state._world_incomplete kommt aus world_import.
        # _chunk_coverage_suffix -- nur True wenn W6-B1s chunk-honesty-felder da UND
        # chunksLoaded < chunksTotal. alert=True faerbt die zeile rot (blender-eigenes
        # "warnung"-styling), byte-identisch zu heute wenn die felder fehlen.
        world_row.alert = state._world_incomplete
        world_row.label(text="World: %s" % state._world_status,
                         icon='ERROR' if state._world_incomplete else 'NONE')
        scene = context.scene
        # getattr-guard: beim live-reload kann draw() feuern bevor register() die prop legt
        radius = getattr(scene, "bbsbridge_world_radius", 32)
        if hasattr(scene, "bbsbridge_world_radius"):
            layout.prop(scene, "bbsbridge_world_radius")
        row = layout.row()
        row.enabled = state._connected and not state._world_pending
        op = row.operator("bbsbridge.import_world", icon='WORLD_DATA')
        op.radius = radius

        # ---- Phase 3: actor-import ----
        layout.separator()
        # Task 1.0 (film-identitaet, datenverlust-schutz): gebundener film + wechsel-warnung.
        # film_switch_detected -> rote zeile (BBS zeigt einen ANDEREN film als den gebundenen,
        # actor-sync pausiert bis reconnect/Import Actors neu bindet). Sonst nur die bindung.
        film_row = layout.row()
        film_row.alert = state.film_switch_detected
        if state.film_switch_detected:
            film_row.label(text="⚠ Filmwechsel: '%s' → BBS '%s' (sync pausiert)"
                           % (state.bound_film or "?", state.film_switch_other or "?"),
                           icon='ERROR')
        else:
            film_row.label(text="Film: %s" % (state.bound_film or "(nicht gebunden)"),
                           icon='FILE_MOVIE')
        layout.label(text="Actors: %s" % state._actors_status)
        if hasattr(scene, "bbsbridge_import_models"):
            layout.prop(scene, "bbsbridge_import_models")
        row = layout.row()
        row.enabled = state._connected and not state._actors_pending
        row.operator("bbsbridge.import_actors", icon='OUTLINER_OB_ARMATURE')

        layout.label(text="Film structure: %s" % film_structure._status)
        row = layout.row(align=True)
        row.enabled = (state._connected and not film_structure._pending
                       and "film_structure_write_v1" in state.peer_capabilities)
        row.operator("bbsbridge.structure_merge", icon='ADD')
        row.operator("bbsbridge.structure_replace", icon='FILE_REFRESH')
        row = layout.row(align=True)
        row.operator("bbsbridge.structure_edit", icon='GREASEPENCIL')
        row.operator("bbsbridge.structure_new_id", icon='DUPLICATE')
        up = row.operator("bbsbridge.structure_move", text="Up", icon='TRIA_UP')
        up.delta = -1
        down = row.operator("bbsbridge.structure_move", text="Down", icon='TRIA_DOWN')
        down.delta = 1

        # ---- live-sync: BBS bewegen -> Blender folgt ----
        # Task 1.6: push ist IMMER aktiv sobald verbunden (BBS meldet sich selbst, kein
        # toggle noetig) -- der scene-toggle unten ist seither nur noch der POLL-FALLBACK
        # fuer eine aeltere BBS ohne ActorPushTracker bzw. falls push laenger als
        # state.PUSH_STALE_AFTER ausbleibt. "push"/"poll" hier zeigt, welcher der beiden
        # gerade tatsaechlich die daten liefert (state._push_last_seen ist die messung dafuer).
        layout.separator()
        push_alive = (time.time() - state._push_last_seen) < state.PUSH_STALE_AFTER
        if hasattr(scene, "bbsbridge_live_actors"):
            mode = "push" if push_alive else ("poll" if scene.bbsbridge_live_actors else "aus")
            layout.label(text="Live: %s (%s)" % (state._live_status, mode))
            layout.prop(scene, "bbsbridge_live_actors", icon='REC', text="Poll-Fallback")

        # ---- live player replay recording, controlled from Blender ----
        layout.separator()
        layout.label(text="Replay recording: %s" % recording_live._status, icon='REC')
        row = layout.row(align=True)
        row.enabled = state._connected and not recording_live._control_pending
        row.operator("bbsbridge.record_replay_start", icon='REC')
        row.operator("bbsbridge.record_replay_stop", icon='PAUSE')

        # ---- auftrag REDACTED §2: eingefrorener shot ----
        # An = der automatische render aus BBS importiert nichts mehr; die szene ist die
        # wahrheit. Die import-knoepfe oben bleiben nutzbar, sichern aber vorher den stand.
        if hasattr(scene, shot_freeze.PROP_FROZEN):
            layout.separator()
            box = layout.box()
            box.prop(scene, shot_freeze.PROP_FROZEN, icon='FREEZE')
            if getattr(scene, shot_freeze.PROP_FROZEN):
                box.label(text="Render nimmt die Szene, wie sie ist", icon='CHECKMARK')
            sicherungen = shot_freeze.backups_auflisten()
            if sicherungen:
                box.label(text="%d Sicherung(en), letzte: %s" % (len(sicherungen), sicherungen[-1]),
                          icon='FILE_BACKUP')

        # ---- W10-B1: rueckrichtung — Blender-edits in den BBS-film senden (actor_export) ----
        # Status-zeilen-muster wie world/actors/film-cam; _status/_pending leben MODUL-LOKAL
        # in actor_export (W9-B1, replay_anim-konvention: lesen IMMER via attribut, nie
        # importieren — kein state.py-edit). Kein row.enabled noetig: der operator hat eine
        # poll() (deckt auch F3-suche/skripte ab), das panel graut ueber sie automatisch aus.
        layout.separator()
        if actor_export._request_id:
            layout.label(text="Unconfirmed send (edits kept)", icon='INFO')
        layout.label(text="Durable: %s" % actor_outbox._status)
        from . import model_adoption
        layout.prop(scene, 'bbsbridge_import_new_models')
        layout.operator('bbsbridge.send_new_model')
        layout.label(text=model_adoption._status)
        from . import actor_pose_preview
        layout.prop(scene, actor_pose_preview.PROP)
        layout.label(text=actor_pose_preview._status)
        if not actor_outbox.bound() or not getattr(scene, actor_authoring.PROP, False):
            warning = layout.box()
            warning.alert = True
            warning.label(text="Keyframe-Sync nach BBS: AUS", icon='ERROR')
            if not bpy.data.filepath:
                warning.label(text="Projekt als .blend speichern")
            elif not actor_outbox.bound():
                warning.label(text="Durable Editing aktivieren")
            else:
                warning.label(text="Live Actor Editing einschalten")
        layout.operator("bbsbridge.retry_durable" if actor_outbox.bound() else "bbsbridge.enable_durable")
        if actor_outbox.bound():
            layout.operator('bbsbridge.live_actor_editing', text='Live Actor Editing',
                            depress=getattr(scene, actor_authoring.PROP))
            row = layout.row()
            row.alert = 'paused' in actor_authoring._status or actor_authoring._status.startswith('not queued:')
            row.label(text='Live edits: ' + actor_authoring._status)
            layout.operator("bbsbridge.resync_keep_local")
            layout.label(text="Shared history: %s" % actor_shared_undo._status)
            row = layout.row(align=True)
            row.operator("bbsbridge.shared_undo")
            row.operator("bbsbridge.shared_redo")
            layout.label(text="Property: %s" % actor_leases._status)
            row = layout.row(align=True)
            row.operator("bbsbridge.hold_property")
            row.operator("bbsbridge.release_property")
        layout.label(text="Send to BBS: %s" % actor_export._status)
        layout.operator("bbsbridge.send_actors", icon='EXPORT',
                        text="Recover Pending Send" if actor_export._request_id else "Send Protected Actor Changes")
        if actor_export._request_id:
            layout.operator("bbsbridge.forget_actor_receipt", icon='X')

        # ---- Task 1.3: film-kamera-clips -> F-curves auf BBS_FilmCam ----
        layout.separator()
        layout.label(text="Film cam: %s" % state._film_cam_status)
        row = layout.row()
        row.enabled = state._connected and not state._film_cam_pending
        row.operator("bbsbridge.import_film_camera", icon='CAMERA_DATA')

        # ---- W10-B1: renderfahrt (task 6.1-anschluss) — render_drive.render_film ----
        # Direkt unter dem film-cam-import: der render braucht die gebakte BBS_FilmCam
        # (poll gatet darauf, siehe operator). _status lebt modul-lokal in render_drive.
        # KEIN connect noetig (offline by design, render_drive-docstring) — der button
        # bleibt also auch ohne BBS-verbindung aktiv, nur ohne kamera ist er grau.
        layout.label(text="Local Render — separate snapshot")
        layout.operator("bbsbridge.render_film", icon='RENDER_ANIMATION')
        render_job_ui.draw(layout)

        # ---- W7-B3: replay-anim (mob-replay-pose -> pose-bone-keyframes) ----
        # Gleiche button-paritaet wie world/actors/film cam: status-zeile + EIN request-
        # gegateter knopf; der import selbst passiert im _tick() wenn REPLAY_POSE_EXPORT_
        # RESULT eintrifft (replay_anim._handle_replay_pose_export_result). Der knopf ist
        # ein MENU (dropdown der actors aus dem letzten ACTORS_EXPORT): replay_anim ist
        # MobForm-only (java ReplayPoseReader lehnt ModelForm ab) — model-actors stehen
        # AUSGEGRAUT mit grund im dropdown statt versteckt zu sein (user sieht WARUM
        # sein alex nicht waehlbar ist). pending/status leben modul-lokal in replay_anim
        # (kein state.py-edit, W6-B2-entscheid), lesen IMMER via attribut.
        layout.separator()
        layout.label(text="Replay anim: %s" % replay_anim._status)
        row = layout.row()
        row.enabled = state._connected and not replay_anim._pending
        row.menu(BBSBRIDGE_MT_replay_anim.bl_idname, icon='ARMATURE_DATA',
                 text="Import replay animation")

        # ---- Task 1.4: playhead-sync (owner-modell wie kamera) ----
        if hasattr(scene, "bbsbridge_drive_timeline"):
            layout.separator()
            layout.prop(scene, "bbsbridge_drive_timeline", icon='TIME')
            layout.label(text="Playhead owner: %s (bbs playing: %s)"
                         % (state.playhead_owner, state._playhead_bbs_playing))
            marker_row = layout.row()
            marker_row.alert = ("paused" in state._marker_status
                                or "invalid" in state._marker_status
                                or "required" in state._marker_status)
            marker_row.label(text="Markers: %s" % state._marker_status, icon='MARKER')
            color_row = layout.row()
            color_row.enabled = sum(1 for marker in scene.timeline_markers
                                    if marker.camera is None and marker.select) == 1
            color_row.operator("bbsbridge.marker_color", icon='COLOR')

        # ---- Task 5.1: first-person hand+HUD-compositing-rig (fp_hand.py) ----
        # Rein lokal (kein BBS-request wie world/actors/film-cam import) -- build_rig()/
        # remove_rig() sind synchron und idempotent, siehe fp_hand.py-modul-docstring.
        layout.separator()
        layout.label(text="FP Hand (5.1):")
        row = layout.row(align=True)
        row.operator("bbsbridge.fp_hand_build", icon='CAMERA_DATA')
        row.operator("bbsbridge.fp_hand_remove", icon='TRASH')
        if hasattr(scene, fp_hand.PROP_FOV):
            hand_scene = bpy.data.scenes.get(fp_hand.HAND_SCENE_NAME)
            if hand_scene is not None:
                layout.prop(hand_scene, fp_hand.PROP_FOV, slider=True)
        if hasattr(scene, fp_hand.PROP_SHOW_HAND):
            layout.prop(scene, fp_hand.PROP_SHOW_HAND, slider=True)
        if hasattr(scene, fp_hand.PROP_SHOW_HUD):
            layout.prop(scene, fp_hand.PROP_SHOW_HUD, slider=True)

        # ---- Task 5.3b: HUD-composite (hud_overlay.py) — die echten MC-HUD-elemente ----
        # Ersetzt fp_hands prozeduralen checker-platzhalter durch crosshair/hotbar/health/
        # hunger/armor als einzeln togglebare compositor-layer. Rein lokal (kein BBS-request,
        # wie fp_hand): build_hud()/remove_hud() synchron + idempotent. Der build liest die
        # item-ids + health/hunger/armor am AKTUELLEN frame (re-build nach frame-wechsel
        # aktualisiert den HUD-stand — F1-preset unten schaltet alles aus).
        layout.separator()
        layout.label(text="HUD Composite (5.3b):")
        row = layout.row(align=True)
        row.operator("bbsbridge.hud_build", icon='OVERLAY')
        row.operator("bbsbridge.hud_remove", icon='TRASH')
        row = layout.row(align=True)
        row.operator("bbsbridge.hud_all_off", text="F1: all off", icon='HIDE_ON')
        row.operator("bbsbridge.hud_all_on", text="all on", icon='HIDE_OFF')
        if hasattr(scene, hud_overlay.PROP_GUI_SCALE):
            layout.prop(scene, hud_overlay.PROP_GUI_SCALE)
        for prop_name in (hud_overlay.PROP_SHOW_CROSSHAIR, hud_overlay.PROP_SHOW_HOTBAR,
                          hud_overlay.PROP_SHOW_HEALTH, hud_overlay.PROP_SHOW_HUNGER,
                          hud_overlay.PROP_SHOW_ARMOR):
            if hasattr(scene, prop_name):
                layout.prop(scene, prop_name, slider=True)


class BBSBRIDGE_OT_reconnect(bpy.types.Operator):
    bl_idname = "bbsbridge.reconnect"
    bl_label = "Reconnect to BBS"

    def execute(self, context):
        if state._sock is not None:
            try:
                state._sock.close()
            except OSError:
                pass
        state._sock = None
        state._connected = False
        _connect()
        return {'FINISHED'}


class BBSBRIDGE_OT_marker_color(bpy.types.Operator):
    bl_idname = "bbsbridge.marker_color"
    bl_label = "BBS Marker Color"
    bl_description = ("Edit the BBS ARGB color of the one selected regular timeline marker; "
                      "Blender has no native per-marker color field")

    color: bpy.props.FloatVectorProperty(
        name="Color", subtype='COLOR', size=4, min=0.0, max=1.0,
        default=(0.305, 0.757, 1.0, 1.0),
    )

    @classmethod
    def poll(cls, context):
        try:
            return marker_sync.selected_color(context.scene) is not None
        except (KeyError, TypeError, ValueError):
            return False

    def invoke(self, context, event):
        color = marker_sync.selected_color(context.scene)
        if color is None:
            self.report({'ERROR'}, "Select exactly one synchronized regular timeline marker")
            return {'CANCELLED'}
        self.color = color
        return context.window_manager.invoke_props_dialog(self)

    def execute(self, context):
        try:
            marker_sync.set_selected_color(context.scene, self.color)
        except (KeyError, TypeError, ValueError) as exception:
            self.report({'ERROR'}, str(exception))
            return {'CANCELLED'}
        self.report({'INFO'}, "Marker color queued for BBS")
        return {'FINISHED'}


class BBSBRIDGE_OT_structure_merge(bpy.types.Operator):
    bl_idname = "bbsbridge.structure_merge"
    bl_label = "Merge Structure"
    bl_description = ("Create missing BBS replays and update metadata by UUID. "
                      "Never deletes or reorders existing BBS replays")

    def execute(self, context):
        if not film_structure.send("merge"):
            self.report({'ERROR'}, film_structure._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "safe replay-structure merge requested")
        return {'FINISHED'}


class BBSBRIDGE_OT_structure_replace(bpy.types.Operator):
    bl_idname = "bbsbridge.structure_replace"
    bl_label = "Replace Structure"
    bl_description = ("Make the BBS replay list exactly match MC_Actors: create, update, "
                      "delete and reorder. Animation channels on retained UUIDs stay intact")

    def invoke(self, context, event):
        message = ("BBS replays absent from Blender's MC_Actors collection will be deleted, "
                   "and the remaining list will be reordered. BBS writes a FilmBackup first.")
        try:
            return context.window_manager.invoke_confirm(
                self, event, title="Replace BBS replay structure?", message=message,
                confirm_text="Replace", icon='WARNING')
        except TypeError:
            return context.window_manager.invoke_confirm(self, event)

    def execute(self, context):
        if not film_structure.send("replace"):
            self.report({'ERROR'}, film_structure._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "exact replay-structure replace requested")
        return {'FINISHED'}


class BBSBRIDGE_OT_structure_new_id(bpy.types.Operator):
    bl_idname = "bbsbridge.structure_new_id"
    bl_label = "New Replay ID"
    bl_description = ("Turn the selected duplicated actor root into a new replay identity. "
                      "Use this after duplicating an imported actor in Blender")

    def execute(self, context):
        obj = film_structure.assign_new_uuid(context)
        if obj is None:
            self.report({'ERROR'}, "select an imported actor root or one of its children")
            return {'CANCELLED'}
        self.report({'INFO'}, "new replay UUID assigned to %s" % obj.name)
        return {'FINISHED'}


class BBSBRIDGE_OT_structure_edit(bpy.types.Operator):
    bl_idname = "bbsbridge.structure_edit"
    bl_label = "Edit Replay"
    bl_description = "Edit the selected replay metadata Blender explicitly owns on the next merge"

    label: bpy.props.StringProperty(name="Label")
    form_type: bpy.props.StringProperty(name="Form Type")
    form_model: bpy.props.StringProperty(name="Model Key")
    form_mob: bpy.props.StringProperty(name="Mob ID")
    group: bpy.props.StringProperty(name="Parent Group UUID")
    category: bpy.props.StringProperty(name="Category")
    is_group: bpy.props.BoolProperty(name="Is Group")
    enabled: bpy.props.BoolProperty(name="Enabled", default=True)
    first_person: bpy.props.BoolProperty(name="First Person")

    def invoke(self, context, event):
        obj = film_structure.selected_root(context)
        entry = film_structure.entry_for_object(obj)
        if entry is None:
            self.report({'ERROR'}, "select an imported actor root or one of its children")
            return {'CANCELLED'}
        self.label = entry["label"]
        self.form_type = entry["formType"]
        self.form_model = entry["formModel"]
        self.form_mob = entry["formMob"]
        self.group = entry["group"]
        self.category = entry["category"]
        self.is_group = entry["isGroup"]
        self.enabled = entry["enabled"]
        self.first_person = entry["firstPerson"]
        return context.window_manager.invoke_props_dialog(self, width=430)

    def draw(self, context):
        layout = self.layout
        layout.prop(self, "label")
        layout.prop(self, "is_group")
        if not self.is_group:
            layout.prop(self, "form_type")
            layout.prop(self, "form_model")
            layout.prop(self, "form_mob")
        layout.prop(self, "group")
        layout.prop(self, "category")
        layout.prop(self, "enabled")
        layout.prop(self, "first_person")

    def execute(self, context):
        obj = film_structure.selected_root(context)
        if obj is None:
            return {'CANCELLED'}
        film_structure.apply_overrides(obj, {
            "label": self.label, "formType": self.form_type,
            "formModel": self.form_model, "formMob": self.form_mob,
            "group": self.group, "category": self.category,
            "isGroup": self.is_group, "enabled": self.enabled,
            "firstPerson": self.first_person,
        })
        return {'FINISHED'}


class BBSBRIDGE_OT_structure_move(bpy.types.Operator):
    bl_idname = "bbsbridge.structure_move"
    bl_label = "Move Replay"
    bl_description = "Move the selected actor one slot in the order used by Replace Structure"

    delta: bpy.props.IntProperty(default=0, min=-1, max=1, options={'SKIP_SAVE'})

    def execute(self, context):
        if not film_structure.move_selected(self.delta, context):
            self.report({'WARNING'}, "selected actor cannot move farther in that direction")
            return {'CANCELLED'}
        return {'FINISHED'}


class BBSBRIDGE_OT_record_replay_start(bpy.types.Operator):
    bl_idname = "bbsbridge.record_replay_start"
    bl_label = "Start Replay Recording"
    bl_description = "Start BBS' real player replay recorder for the selected imported actor"

    def execute(self, context):
        replay_uuid = recording_live.selected_replay_uuid(context)
        if not replay_uuid:
            self.report({'ERROR'}, "select an imported BBS actor first")
            return {'CANCELLED'}
        if not recording_live.request_control("start", replay_uuid):
            self.report({'ERROR'}, recording_live._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "replay recording requested")
        return {'FINISHED'}


class BBSBRIDGE_OT_record_replay_stop(bpy.types.Operator):
    bl_idname = "bbsbridge.record_replay_stop"
    bl_label = "Stop Replay Recording"
    bl_description = "Stop BBS recording and commit the authoritative curves back to Blender"

    def execute(self, context):
        if not recording_live.request_control("stop"):
            self.report({'ERROR'}, recording_live._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "stop recording requested")
        return {'FINISHED'}


class BBSBRIDGE_OT_import_world(bpy.types.Operator):
    """Fragt BBS die block-region um die kamera als OBJ an. Der import passiert NICHT
    hier, sondern im _tick() wenn WORLD_EXPORT_RESULT eintrifft (async, ein request)."""
    bl_idname = "bbsbridge.import_world"
    bl_label = "Import MC World"
    bl_description = "Ask BBS to export the block region around the camera as OBJ and import it"

    radius: bpy.props.IntProperty(
        name="Radius", default=32, min=1, max=256,
        description="Horizontal radius in blocks around the camera",
    )
    y_min_offset: bpy.props.IntProperty(
        name="Y Min Offset", default=-16, min=-256, max=0,
        description="Lower Y bound, relative to the camera",
    )
    y_max_offset: bpy.props.IntProperty(
        name="Y Max Offset", default=16, min=0, max=256,
        description="Upper Y bound, relative to the camera",
    )

    def execute(self, context):
        if not state._connected or state._sock is None:
            self.report({'ERROR'}, "not connected to BBS")
            return {'CANCELLED'}
        cam = _get_sync_camera()
        if cam is None:
            self.report({'ERROR'}, "no camera to center the region on")
            return {'CANCELLED'}
        fields = _camera_region_fields(cam, self.radius, self.y_min_offset, self.y_max_offset)
        # REQUEST-KORRELATION (end-review fund 2): eigene id pro request — java echot sie
        # im WORLD_EXPORT_RESULT als fields.requestId zurück, der handler akzeptiert NUR
        # das eigene result (siehe state._world_request_id-header für das failure-szenario:
        # fremder agent-export wird zuerst fertig und kapert sonst das offene 120s-fenster).
        import uuid
        state._world_request_id = uuid.uuid4().hex
        fields["requestId"] = state._world_request_id
        state._world_pending = True
        state._world_deadline = time.time() + state.WORLD_EXPORT_TIMEOUT
        state._world_status = "waiting for export (r=%d)" % self.radius
        _send({"type": "WORLD_EXPORT", "origin": "blender", "uuid": "world",
               "seq": _next_seq(), "fields": fields})
        print("[bbs-bridge] WORLD_EXPORT requested:", fields)
        self.report({'INFO'}, "WORLD_EXPORT requested (radius %d)" % self.radius)
        return {'FINISHED'}


class BBSBRIDGE_OT_import_actors(bpy.types.Operator):
    """Fragt BBS die replays des offenen films an. Der import passiert NICHT hier,
    sondern im _tick() wenn ACTORS_EXPORT_RESULT eintrifft (async, ein request) —
    exakt dasselbe muster wie der welt-import."""
    bl_idname = "bbsbridge.import_actors"
    bl_label = "Import BBS Actors"
    bl_description = "Ask BBS for the open film's actors (replays) and build animated empties"

    def execute(self, context):
        if actor_outbox.bound():
            self.report({'ERROR'}, 'Durable document: use protected resync, not destructive reimport')
            return {'CANCELLED'}
        if not state._connected or state._sock is None:
            self.report({'ERROR'}, "not connected to BBS")
            return {'CANCELLED'}
        # §2: expliziter import ueber einen EINGEFRORENEN shot — erst den stand sichern,
        # dann ueberschreiben. Der user hat den knopf gedrueckt, das ist die entscheidung;
        # das backup ist der rueckweg, falls sie falsch war.
        if shot_freeze.ist_eingefroren(context.scene):
            name, n = shot_freeze.backup_anlegen(context.scene)
            if name:
                self.report({'WARNING'}, "shot eingefroren: stand als '%s' gesichert (%d objekte), "
                                         "import ueberschreibt jetzt" % (name, n))
        state._actors_pending = True
        state._actors_deadline = time.time() + state.ACTORS_EXPORT_TIMEOUT
        state._actors_status = "waiting for export"
        # keine parameter — BBS exportiert den film der im editor offen ist (sonst den
        # gerade spielenden), siehe ActorExporter.resolveOpenFilm().
        _send({"type": "ACTORS_EXPORT", "origin": "blender", "uuid": "actors",
               "seq": _next_seq(), "fields": {}})
        print("[bbs-bridge] ACTORS_EXPORT requested")
        self.report({'INFO'}, "ACTORS_EXPORT requested")
        return {'FINISHED'}


class BBSBRIDGE_OT_import_film_camera(bpy.types.Operator):
    """Fragt BBS die kamera-clip-timeline des offenen films an (dicht pro tick gesampelt,
    siehe FilmCameraExporter). Der import passiert NICHT hier, sondern im _tick() wenn
    FILM_CAMERA_EXPORT_RESULT eintrifft (async, ein request) — exakt dasselbe muster wie
    welt- und actor-import. Baut F-curves auf einer NEUEN kamera "BBS_FilmCam", NIE auf der
    sync-/user-kamera."""
    bl_idname = "bbsbridge.import_film_camera"
    bl_label = "Import Film Camera"
    bl_description = ("Ask BBS for the open film's camera-clip timeline and build F-curves "
                       "on a new 'BBS_FilmCam' camera (graph-editor editable)")

    def execute(self, context):
        if not state._connected or state._sock is None:
            self.report({'ERROR'}, "not connected to BBS")
            return {'CANCELLED'}
        if shot_freeze.ist_eingefroren(context.scene):      # §2, siehe import_actors
            name, n = shot_freeze.backup_anlegen(context.scene)
            if name:
                self.report({'WARNING'}, "shot eingefroren: stand als '%s' gesichert (%d objekte), "
                                         "import ueberschreibt jetzt" % (name, n))
        state._film_cam_pending = True
        state._film_cam_deadline = time.time() + state.FILM_CAM_EXPORT_TIMEOUT
        state._film_cam_status = "waiting for export"
        # einziger parameter: requestId (W8-B4, ce2d139-muster) — java echot sie im result,
        # film_camera_import akzeptiert NUR das eigene result (id lebt modul-lokal DORT,
        # nicht in state.py; siehe _request_id-header in film_camera_import). Sonst keine
        # parameter — BBS exportiert den film der im editor offen ist (sonst den gerade
        # spielenden), siehe FilmCameraExporter.resolveOpenFilm(). Raw string type,
        # kein BridgeProtocol.TYPE_*-pendant noetig (siehe java-seitige klassen-doku).
        _send({"type": "FILM_CAMERA_EXPORT", "origin": "blender", "uuid": "filmCamera",
               "seq": _next_seq(), "fields": {"requestId": film_camera_import.new_request_id()}})
        print("[bbs-bridge] FILM_CAMERA_EXPORT requested")
        self.report({'INFO'}, "FILM_CAMERA_EXPORT requested")
        return {'FINISHED'}


class BBSBRIDGE_MT_replay_anim(bpy.types.Menu):
    """W7-B3: dropdown der actors aus dem letzten ACTORS_EXPORT (quelle: MC_Actors-
    custom-props, siehe _iter_actor_roots-header). Ein bpy.types.Menu statt einer
    dynamischen EnumProperty, aus zwei gruenden: (a) NUR menu-rows koennen pro eintrag
    ausgegraut werden (enum-items kennen kein per-item-enabled — der brief will model-
    replays sichtbar-ausgegraut, nicht versteckt), (b) die bekannte enum-items-callback-
    string-GC-falle (Blender haelt keine referenz auf callback-strings) entfaellt komplett.
    Menus drawen nur beim OEFFNEN — der objekt-scan hier ist klick-getrieben, nicht
    pro panel-redraw."""
    bl_idname = "BBSBRIDGE_MT_replay_anim"
    bl_label = "Import replay animation"

    def draw(self, context):
        layout = self.layout
        actors = _iter_actor_roots()
        if not actors:
            layout.label(text="Keine actors — erst 'Import BBS Actors'", icon='INFO')
            return
        for obj in actors:
            uuid = str(obj.get("bbs_uuid"))
            form_type = str(obj.get("bbs_form_type") or "")
            row = layout.row()
            if form_type != "bbs:mob":
                # MobForm-only: java ReplayPoseReader lehnt ModelForm & co ab ("task 4.3a
                # only reposes mob-rig actors") — ausgegraut MIT grund statt versteckt.
                row.enabled = False
                row.operator(BBSBRIDGE_OT_import_replay_anim.bl_idname, icon='X',
                             text="%s — %s (nur mob-replays)"
                                  % (obj.name, form_type or "form?"))
            elif replay_anim._find_armature_for_replay(uuid) is None:
                # mob-replay ohne armature: actors.py legt heute nur ein platzhalter-EMPTY
                # an (task-4.1-entity-import in arbeit, W7-B1) — replay_anim braucht eine
                # armature mit gestempelter bbs_uuid (siehe replay_anim-modul-header).
                row.enabled = False
                row.operator(BBSBRIDGE_OT_import_replay_anim.bl_idname, icon='ERROR',
                             text="%s (keine armature — erst mob-import, task 4.1)" % obj.name)
            else:
                op = row.operator(BBSBRIDGE_OT_import_replay_anim.bl_idname,
                                  icon='ARMATURE_DATA', text=obj.name)
                op.replay_uuid = uuid


class BBSBRIDGE_OT_import_replay_anim(bpy.types.Operator):
    """W7-B3: EIN mob-replay tick-fuer-tick von BBS posieren lassen und als pose-bone-
    keyframes auf der replay-armature baken (replay_anim.request_replay_pose). Der import
    passiert NICHT hier, sondern im _tick() wenn REPLAY_POSE_EXPORT_RESULT eintrifft —
    exakt dasselbe async-muster wie welt-/actor-/film-kamera-import."""
    bl_idname = "bbsbridge.import_replay_anim"
    bl_label = "Import Replay Animation"
    bl_description = ("Ask BBS to repose the selected MOB replay tick by tick and bake the "
                       "result as pose-bone keyframes on the replay's armature (MobForm only)")

    replay_uuid: bpy.props.StringProperty(
        name="Replay UUID", default="",
        description="bbs_uuid of the mob replay to sample (set by the dropdown menu)",
    )
    ticks: bpy.props.IntProperty(
        name="Ticks", default=0, min=0, max=100000,
        description="How many ticks to sample; 0 = auto (full replay length derived from "
                    "the actor's imported F-curves, fallback: scene end frame)",
    )
    start_tick: bpy.props.IntProperty(
        name="Start Tick", default=0, min=0,
        description="First tick to sample",
    )

    @classmethod
    def poll(cls, context):
        # Spiegelt die row.enabled-logik des panels (belt+braces: der operator ist auch
        # per F3-suche/skript erreichbar, nicht nur uebers ausgegraute menu).
        return state._connected and not replay_anim._pending

    def execute(self, context):
        if not state._connected or state._sock is None:
            self.report({'ERROR'}, "not connected to BBS")
            return {'CANCELLED'}
        if not self.replay_uuid:
            self.report({'ERROR'}, "no replay selected (use the dropdown)")
            return {'CANCELLED'}
        actor = None
        for obj in _iter_actor_roots():
            if str(obj.get("bbs_uuid")) == self.replay_uuid:
                actor = obj
                break
        # defense-in-depth: das menu graut model-actors schon aus, aber skript-aufrufe
        # kommen hier direkt an — frueher + verstaendlicher ablehnen als java's
        # "task 4.3a only reposes mob-rig actors" nach 30s timeout-fenster.
        if actor is not None:
            form_type = str(actor.get("bbs_form_type") or "")
            if form_type != "bbs:mob":
                self.report({'WARNING'}, "replay is '%s' — replay-anim is MobForm-only "
                            "(bbs:mob)" % (form_type or "unknown"))
                return {'CANCELLED'}
        if replay_anim._find_armature_for_replay(self.replay_uuid) is None:
            # Ohne ziel-armature scheitert der result-import GARANTIERT (replay_anim.
            # apply_replay_pose findet kein objekt) — den doomed roundtrip gar nicht
            # erst anstossen, stattdessen sagen was fehlt.
            self.report({'WARNING'}, "no armature with bbs_uuid %s — run the actors "
                        "import (task-4.1 mob import) or stamp one first (see "
                        "replay_anim module doc)" % self.replay_uuid)
            return {'CANCELLED'}
        ticks = self.ticks if self.ticks > 0 else _replay_length_ticks(actor, context.scene)
        if not replay_anim.request_replay_pose(self.replay_uuid, ticks=ticks,
                                               start_tick=self.start_tick):
            self.report({'ERROR'}, "request failed: %s" % replay_anim._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "REPLAY_POSE_EXPORT requested (uuid=%s, %d ticks)"
                    % (self.replay_uuid, ticks))
        return {'FINISHED'}


class BBSBRIDGE_OT_send_actors(bpy.types.Operator):
    """Protected Blender -> BBS actor write.

    Only locally changed root transforms, item channels and bone curves are sent. Every
    path carries the BBS value Blender last imported as a precondition. BBS validates the
    whole batch before the first mutation; a same-path edit made meanwhile in BBS wins
    with a named conflict instead of being overwritten. requestId correlates broadcasts
    and lets a missing result be queried without repeating the write. BBS still takes a FilmBackup before real writes.
    Rig-property curves are a separate unfinished slice and are not advertised here."""
    bl_idname = "bbsbridge.send_actors"
    bl_label = "Send Protected Actor Changes"
    bl_description = ("Write only locally changed actor root, item and bone curves back to BBS. "
                       "Each path is conditional on the BBS value last imported, so concurrent "
                       "same-path edits are rejected instead of overwritten. BBS snapshots the "
                       "film before real writes. A pending send is recovered by querying its receipt, not rewriting")

    @classmethod
    def poll(cls, context):
        # Spiegelt die welle-10-disabled-zustaende: kein connect / send laeuft schon /
        # keine actors importiert. poll statt row.enabled: gilt auch fuer F3-suche und
        # skripte, und poll_message_set erklaert dem user WARUM der button grau ist
        # (dieselbe sichtbar-mit-grund-haltung wie das replay-anim-menu, b788524-lehre).
        if actor_outbox.bound():
            return bool(actor_export._actor_roots())  # durable queue also accepts offline edits
        if not state._connected or not state._peer_ready:
            cls.poll_message_set("waiting for a fresh BBS handshake")
            return False
        if actor_export._pending:
            cls.poll_message_set("previous send still pending (30s window)")
            return False
        capability = "actor_receipt_recovery_v1" if actor_export._request_id else "actor_channel_patch_v1"
        if capability not in state.peer_capabilities:
            cls.poll_message_set("BBS peer lacks protected actor-write capability")
            return False
        if not actor_export._actor_roots():
            cls.poll_message_set("no actors — run 'Import BBS Actors' first")
            return False
        return True

    def invoke(self, context, event):
        if actor_export._request_id:
            return self.execute(context)  # recovery reads a receipt; it cannot change BBS
        # Sicherheits-UX (welle-10-pflicht): dieser klick veraendert den user-film in BBS.
        # invoke_confirm MIT eigenem text (5.2/4.1+-signatur); TypeError-fallback auf die
        # alte 2-arg-form falls eine aeltere Blender-version das addon laedt (bl_info 4.0).
        try:
            return context.window_manager.invoke_confirm(
                self, event, title="Send protected actor changes to BBS?",
                message="Sends only locally changed root, item and bone curves. Each path is "
                        "checked against the BBS value Blender last imported; a concurrent BBS "
                        "edit is kept and reported as a conflict. BBS keeps a FilmBackup snapshot.",
                confirm_text="Send protected changes", icon='WARNING')
        except TypeError:
            return context.window_manager.invoke_confirm(self, event)

    def execute(self, context):
        # send_actors() prueft selbst nochmal connect/pending/leere payload (defense in
        # depth — poll und execute koennen zeitlich auseinanderfallen) und loggt ehrliche
        # degradationen in die konsole; hier nur der report-anschluss.
        if not actor_export.send_actors():
            self.report({'ERROR'}, "not sent: %s" % actor_export._status)
            return {'CANCELLED'}
        self.report({'INFO'}, "ACTORS_IMPORT %s" % actor_export._status)
        return {'FINISHED'}


class BBSBRIDGE_OT_forget_actor_receipt(bpy.types.Operator):
    """Forget correlation only; never discard local edits or claim to cancel a remote write."""
    bl_idname = "bbsbridge.forget_actor_receipt"
    bl_label = "Forget Pending Receipt"
    bl_description = ("Keep local edits and forget only the pending receipt. This does NOT "
                      "cancel or undo a BBS write. Compare both editors before sending again")

    @classmethod
    def poll(cls, context):
        return bool(actor_export._request_id) and not actor_export._pending

    def invoke(self, context, event):
        try:
            return context.window_manager.invoke_confirm(
                self, event, title="Forget pending receipt?",
                message="Local edits are kept. This does NOT cancel or undo a BBS write. "
                        "Compare both editors before sending again.",
                confirm_text="Forget receipt only", icon='WARNING')
        except TypeError:
            return context.window_manager.invoke_confirm(self, event)

    def execute(self, context):
        if not actor_export.forget_actor_receipt():
            return {'CANCELLED'}
        self.report({'INFO'}, actor_export._status)
        return {'FINISHED'}


class BBSBRIDGE_OT_render_film(bpy.types.Operator):
    """Submit a native save-copy to an independent bounded Blender process."""
    bl_idname = "bbsbridge.render_film"
    bl_label = "Render Film Job"
    bl_description = ("Snapshot the film and render in a separate Blender process. "
                      "Editing stays available; inspect, cancel or resume using the job controls")
    start: bpy.props.IntProperty(name="Start Frame", default=-1, min=-1)
    end: bpy.props.IntProperty(name="End Frame", default=-1, min=-1)
    out_dir: bpy.props.StringProperty(name="Output", default="//render_out/", subtype='DIR_PATH',
        description="Each job creates its own render-ID folder; older output is not overwritten")
    samples: bpy.props.IntProperty(name="Samples", default=16, min=1, max=4096)
    memory_mib: bpy.props.IntProperty(name="Memory Limit (MiB)", default=4096, min=1024, max=16384,
        description="The isolated process waits for this budget plus 1 GiB reserve")
    use_scripts: bpy.props.BoolProperty(name="Trust Scripts in This Snapshot", default=False,
        description="Allow embedded Python in the private render copy; enable only for trusted projects")

    @classmethod
    def poll(cls, context):
        cam = bpy.data.objects.get(film_camera_import.BBS_FILM_CAM_NAME)
        if cam is None or cam.type != 'CAMERA':
            cls.poll_message_set("Run Import Film Camera first")
            return False
        return True

    def invoke(self, context, event):
        cam = bpy.data.objects.get(film_camera_import.BBS_FILM_CAM_NAME)
        baked = render_drive.film_frame_range(cam) if cam is not None else None
        if baked is not None:
            if self.start < 0:
                self.start = baked[0]
            if self.end < 0:
                self.end = baked[1]
        return context.window_manager.invoke_props_dialog(self)

    def execute(self, context):
        from . import render_jobs, render_job_ui
        out_dir = (self.out_dir or "").strip()
        if out_dir.startswith("//") and not bpy.data.filepath:
            out_dir = None
        elif out_dir:
            out_dir = bpy.path.abspath(out_dir)
        else:
            out_dir = None
        try:
            result = render_jobs.submit(out_dir=out_dir,
                start=self.start if self.start >= 0 else None,
                end=self.end if self.end >= 0 else None, samples=self.samples,
                memory_mib=self.memory_mib, use_scripts=self.use_scripts)
            render_job_ui.follow()
            self.report({'INFO'}, "Render job %s: %s" % (result['id'], result['phase']))
            return {'FINISHED'}
        except Exception as error:
            self.report({'ERROR'}, str(error))
            return {'CANCELLED'}


class BBSBRIDGE_OT_fp_hand_build(bpy.types.Operator):
    """Baut/aktualisiert den first-person hand+HUD-compositing-rig (task 5.1) -- siehe
    fp_hand.build_rig(). Rein lokal, KEIN BBS-request (anders als import_world/import_actors/
    import_film_camera): der rig braucht nur Blender-eigene daten, darum synchron hier statt
    async ueber den tick()-request/result-zyklus."""
    bl_idname = "bbsbridge.fp_hand_build"
    bl_label = "Build FP Hand Rig"
    bl_description = "Baut/aktualisiert die first-person hand+HUD-compositing-szene (idempotent)"

    def execute(self, context):
        try:
            fp_hand.build_rig(context.scene)
        except Exception as e:   # noqa: BLE001 -- operator darf nicht als python-traceback-popup enden
            self.report({'ERROR'}, "fp_hand.build_rig failed: %s" % e)
            print("[bbs-bridge] fp_hand.build_rig failed:", e)
            return {'CANCELLED'}
        self.report({'INFO'}, "FP hand rig gebaut/aktualisiert")
        return {'FINISHED'}


class BBSBRIDGE_OT_fp_hand_remove(bpy.types.Operator):
    """Entfernt den kompletten rig wieder (task 5.1) -- siehe fp_hand.remove_rig(),
    rueckstandslos nach CLEANUP_MANIFEST (nur BBS_FP_*-datenbloecke + die beiden
    welt-szenen-toggle-properties, users objekte/actions bleiben unberuehrt)."""
    bl_idname = "bbsbridge.fp_hand_remove"
    bl_label = "Remove FP Hand Rig"
    bl_description = "Entfernt die first-person hand+HUD-compositing-szene rueckstandslos"

    def execute(self, context):
        try:
            fp_hand.remove_rig(context.scene)
        except Exception as e:   # noqa: BLE001
            self.report({'ERROR'}, "fp_hand.remove_rig failed: %s" % e)
            print("[bbs-bridge] fp_hand.remove_rig failed:", e)
            return {'CANCELLED'}
        self.report({'INFO'}, "FP hand rig entfernt")
        return {'FINISHED'}


class BBSBRIDGE_OT_hud_build(bpy.types.Operator):
    """Task 5.3b: baut/aktualisiert den echten HUD-composite (hud_overlay.build_hud) —
    ersetzt fp_hands checker-platzhalter durch crosshair/hotbar/health/hunger/armor als
    einzeln togglebare compositor-layer, gemalt am AKTUELLEN frame. Rein lokal, KEIN
    BBS-request (wie fp_hand). Ruft build_rig selbst (idempotent), also ohne vorheriges
    'Build FP Hand' nutzbar."""
    bl_idname = "bbsbridge.hud_build"
    bl_label = "Build HUD Composite"
    bl_description = ("Baut/aktualisiert die echten MC-HUD-elemente (hotbar+items, herzen, "
                       "hunger, ruestung, crosshair) am aktuellen frame; idempotent")

    def execute(self, context):
        try:
            report = hud_overlay.build_hud(context.scene)
        except Exception as e:   # noqa: BLE001 — operator darf nicht als traceback-popup enden
            self.report({'ERROR'}, "hud_overlay.build_hud failed: %s" % e)
            print("[bbs-bridge] hud_overlay.build_hud failed:", e)
            return {'CANCELLED'}
        if report.get("status") != "ok":
            self.report({'ERROR'}, "HUD build: %s" % report.get("message", "unbekannt"))
            return {'CANCELLED'}
        self.report({'INFO'}, "HUD gebaut (actor=%s, health=%s hunger=%s)"
                    % (report.get("actor_uuid") or "(none)", report.get("health"),
                       report.get("hunger")))
        return {'FINISHED'}


class BBSBRIDGE_OT_hud_remove(bpy.types.Operator):
    """Task 5.3b: entfernt den HUD-composite rueckstandslos (hud_overlay.remove_hud) und
    stellt fp_hands schlichten checker-platzhalter wieder her."""
    bl_idname = "bbsbridge.hud_remove"
    bl_label = "Remove HUD Composite"
    bl_description = "Entfernt die HUD-elemente rueckstandslos (fp_hand-platzhalter kehrt zurueck)"

    def execute(self, context):
        try:
            hud_overlay.remove_hud(context.scene)
        except Exception as e:   # noqa: BLE001
            self.report({'ERROR'}, "hud_overlay.remove_hud failed: %s" % e)
            print("[bbs-bridge] hud_overlay.remove_hud failed:", e)
            return {'CANCELLED'}
        self.report({'INFO'}, "HUD-composite entfernt")
        return {'FINISHED'}


class BBSBRIDGE_OT_hud_all_off(bpy.types.Operator):
    """Task 5.3b: F1-preset — ALLE HUD-element-toggles auf 0 (welt-render byte-identisch
    zu ohne-HUD, das 5.1-FOV-isolations-gate). Setzt auch fp_hands show_hud auf 0, damit
    kein rest-layer bleibt. Analog dem vanilla-F1 (HUD ausblenden)."""
    bl_idname = "bbsbridge.hud_all_off"
    bl_label = "HUD F1: all off"
    bl_description = "Alle HUD-element-layer aus (welt byte-identisch zu ohne-HUD)"

    def execute(self, context):
        scene = context.scene
        for prop_name in (hud_overlay.PROP_SHOW_CROSSHAIR, hud_overlay.PROP_SHOW_HOTBAR,
                          hud_overlay.PROP_SHOW_HEALTH, hud_overlay.PROP_SHOW_HUNGER,
                          hud_overlay.PROP_SHOW_ARMOR, fp_hand.PROP_SHOW_HUD):
            if hasattr(scene, prop_name):
                setattr(scene, prop_name, 0.0)
        self.report({'INFO'}, "HUD: alles aus (F1)")
        return {'FINISHED'}


class BBSBRIDGE_OT_hud_all_on(bpy.types.Operator):
    """Task 5.3b: gegenstueck zu F1 — alle HUD-element-toggles zurueck auf 1."""
    bl_idname = "bbsbridge.hud_all_on"
    bl_label = "HUD: all on"
    bl_description = "Alle HUD-element-layer wieder an"

    def execute(self, context):
        scene = context.scene
        for prop_name in (hud_overlay.PROP_SHOW_CROSSHAIR, hud_overlay.PROP_SHOW_HOTBAR,
                          hud_overlay.PROP_SHOW_HEALTH, hud_overlay.PROP_SHOW_HUNGER,
                          hud_overlay.PROP_SHOW_ARMOR, fp_hand.PROP_SHOW_HUD):
            if hasattr(scene, prop_name):
                setattr(scene, prop_name, 1.0)
        self.report({'INFO'}, "HUD: alles an")
        return {'FINISHED'}


class BBSBRIDGE_MT_actions(bpy.types.Menu):
    bl_label = "BBS Bridge"

    def draw(self, context):
        layout = self.layout
        layout.operator_context = 'INVOKE_DEFAULT'
        for operator in ('reconnect', 'import_world', 'import_actors', 'import_film_camera',
                         'send_actors', 'enable_durable', 'live_actor_editing', 'shared_undo', 'shared_redo',
                         'record_replay_start', 'record_replay_stop', 'render_film'):
            layout.operator('bbsbridge.' + operator)


def draw_actions_menu(self, context):
    # Default F3 searches menu entries, not arbitrary buttons in side panels.
    self.layout.menu(BBSBRIDGE_MT_actions.__name__)


classes = (BBSBRIDGE_MT_actions, BBSBRIDGE_PT_panel, BBSBRIDGE_OT_reconnect, BBSBRIDGE_OT_marker_color,
           BBSBRIDGE_OT_structure_merge, BBSBRIDGE_OT_structure_replace,
           BBSBRIDGE_OT_structure_new_id, BBSBRIDGE_OT_structure_edit,
           BBSBRIDGE_OT_structure_move,
           BBSBRIDGE_OT_record_replay_start, BBSBRIDGE_OT_record_replay_stop,
           BBSBRIDGE_OT_import_world,
           BBSBRIDGE_OT_import_actors, BBSBRIDGE_OT_import_film_camera,
           BBSBRIDGE_MT_replay_anim, BBSBRIDGE_OT_import_replay_anim,
           BBSBRIDGE_OT_send_actors, BBSBRIDGE_OT_forget_actor_receipt, BBSBRIDGE_OT_render_film,
           BBSBRIDGE_OT_fp_hand_build, BBSBRIDGE_OT_fp_hand_remove,
           BBSBRIDGE_OT_hud_build, BBSBRIDGE_OT_hud_remove,
           BBSBRIDGE_OT_hud_all_off, BBSBRIDGE_OT_hud_all_on)

classes += actor_durable_ui.classes

classes += actor_leases.classes
classes += actor_authoring.classes

from . import render_job_ui
classes += render_job_ui.classes
