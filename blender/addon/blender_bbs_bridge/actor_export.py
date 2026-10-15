"""W9-B1 — ACTORS_IMPORT-sender: Blender-F-curves ZURUECK in den offenen BBS-film.

Die gegenrichtung zu actors.py ("in Blender animieren, in BBS abspielen"): liest die
objekt-level F-curves der actor-roots in MC_Actors (NUR die kanaele, die actors.py selbst
baut — location/rotation_euler) und seit 2026-09-13 die POSE-BONE-F-curves der BOBJ-Armaturen
(`bones_for_object` -> java PoseBoneImporter -> pose.bones.<knochen>) und schickt sie als
ACTORS_IMPORT an :9880. Java-seitig
war ALLES fertig (ActorImporter.applyChannel z.274ff nimmt interp-key + alle 4 handles an,
FilmBackup snapshottet vor jedem write, ActorPushTracker.markSynced verhindert das echo)
— dieses modul ist der bislang fehlende ABSENDER (kanal-matrix 2026-07-17 §5 luecke 5).

WIRE (aktueller geschuetzter Rueckweg):
  request: ACTORS_IMPORT mit fields.channelPatch=1, requestId, film und replays[].
           Jeder gesendete Root-/Item-/Bone-Pfad traegt unter replays[].expected den
           letzten BBS-Wert, den Blender wirklich importiert hat.
  reply:   ACTORS_IMPORT_RESULT mit {ok,applied,skipped,error,requestId} und optional
           conflict/alreadyApplied/filmMismatch. Fremde Broadcast-Results lassen das
           eigene Pending-Fenster unangetastet.

SEMANTIK:
- Match per replay-UUID, nie Index. Film-ID wird vor jedem Write geprueft.
- Gesendet werden nur lokal veraenderte Root-Transforms, Item-Kanaele und Bone-Kurven.
  Nicht benannte Pfade bleiben in BBS unangetastet.
- BBS prueft ALLE Preconditions des Batches vor dem ersten Write. Ist derselbe Pfad
  inzwischen in BBS veraendert, bleibt der neuere BBS-Wert stehen und `conflict` nennt
  den exakten Pfad. Andere Pfade desselben Actors duerfen parallel weiterlaufen.
- requestId macht exakte Wiederholungen idempotent; eine neue requestId mit bereits
  vorhandenem Zielzustand wird als alreadyApplied bestaetigt.
- Bone-Writes pruefen zusaetzlich den gleichnamigen Pose-Track-Anteil, weil BBS diesen
  beim Ownership-Wechsel entfernt. Rig-Property-Kurven sind noch ein eigener Slice.
- Eine mapped fcurve OHNE Keys wird nicht als Loeschbefehl gesendet; explizite
  Kanal-Loeschung ist weiterhin kein v1-Feature.

RUECKWAERTS-MAPPING (invertiert _ACTOR_CHANNELS, single source of truth — die specs
werden zur laufzeit aus actors.py abgeleitet, nichts doppelt gepflegt):
  vorwaerts ist  bl = bbs*scale + const  ->  rueckwaerts  bbs = (bl - const)/scale.
  tick = frame * 20/fps  (invers zu tick_scale = fps/20; fps inkl. fps_base wie ueberall).
  handles (invers zu actors._apply_kf_shape, das die BezierUtils.get()-semantik traegt):
    handle_left  = (frame - lx*tick_scale, value + ly*scale)  ->  lx=(frame-hl.x)/tick_scale,
                                                                  ly=(hl.y-value)/scale
    handle_right = (frame + rx*tick_scale, value + ry*scale)  ->  rx=(hr.x-frame)/tick_scale,
                                                                  ry=(hr.y-value)/scale
  ly/ry teilen NUR durch scale (const kuerzt sich — dieselbe wert-raum-begruendung wie im
  _ACTOR_CHANNELS-kommentar: handle-offsets sind DIFFERENZEN, auf gespiegelten kanaelen
  (z, pitch, yaw) dreht die division das vorzeichen korrekt zurueck).

WARUM IMMER ALLE 4 HANDLES MITREISEN (auch an nicht-bezier-keys): BBS liest die lx/ly
eines keyframes NUR, wenn der VORGAENGER-key bezier ist, und rx/ry nur, wenn der key
SELBST bezier ist (BezierUtils.get: segment a->b nutzt a.rx/ry + b.lx/ly). Genau in
diesen faellen hat auch Blender dieselben handle-positionen fuer seine eigene auswertung
benutzt — mitgesendete handles sind also exakt dann wertetreu, wenn BBS sie anschaut,
und werden sonst ignoriert (java-default 5/0 waere auch ok, aber ein LINEAR-key vor
einem bezier-key braucht seine rx nicht, wohl aber seine ly/lx fuer das segment DAVOR).

EHRLICHE DEGRADATIONEN (rueckrichtung der matrix-§3.2-vereinfachungen, geloggt pro send):
- BEZIER mit AUTO/AUTO_CLAMPED/VECTOR/ALIGNED-handles -> "bezier" + den ECHTEN handle-
  positionen (nach fc.update() sind das konkrete koordinaten — kurvenform reist exakt,
  nur der Blender-handle-TYP geht verloren; ein re-import macht daraus FREE).
- CONSTANT -> "constant" (ein BBS-"step" der als CONSTANT ankam, geht als "constant"
  zurueck — die N-stufen-treppe war schon vorwaerts weg, args-grenze matrix §3.1).
- easing-familien SINE/QUAD/CUBIC/QUART/QUINT/EXPO/CIRC/BACK/ELASTIC/BOUNCE ->
  "{base}_{in|out|inout}" (beide seiten penner-konvention, matrix §3.2). Blenders
  easing 'AUTO' ist laut Blender-doku "ease in fuer transitional, ease out fuer die
  dynamischen (BACK/BOUNCE/ELASTIC)" — genau so aufgeloest, als degradation geloggt
  (der urspruengliche BBS-key ist nicht mehr rekonstruierbar).
- BBS' nachbar-cubics (hermite/tcb/...) kamen vorwaerts als BEZIER+AUTO_CLAMPED an ->
  gehen als "bezier" zurueck: kurvenFORM bleibt (naeherung der naeherung), key wechselt.

FLOAT32-GRENZE (dokumentiert, kein bug): Blender speichert fcurve-koordinaten als
float32 — werte verlieren gegenueber BBS' double-kanaelen (x/y/z) ab ~7 signifikanten
stellen. Bei koordinaten im hunderter-bereich ist der fehler ~3e-5 (unter der 1e-4-
projekt-toleranz); bei |wert| > ~10^4 waere er groesser. Die film-restore-pfade der
gates umgehen das, indem sie ORIGINAL-export-json verbatim zuruecksenden.

UI: `Send Protected Actor Changes` ruft denselben `send_actors`-Pfad auf. Konsole/EXEC:
    from blender_bbs_bridge import actor_export
    actor_export.send_actors()                      # lokal veraenderte geschuetzte Pfade
    actor_export.send_actors(uuids={"<uuid>"})      # nur bestimmte replays
"""

import bpy
import time
import uuid as uuidlib

import re

from . import state
from . import actor_channel_sync
from .core import _send, _next_seq
from .actors import _ACTOR_CHANNELS, _object_fcurve_collections
from .actor_bones import EULER_MODES

# ---- modul-lokales pending (replay_anim-muster) ----
# Die Java-Bridge broadcastet Results an ALLE Clients. Der geschuetzte Rueckweg gibt jedem
# Request eine requestId; nur genau dieses Result beendet unser Pending-Fenster. Fremde
# ACTORS_IMPORT_RESULTs bleiben folgenlos, bis die eigene Antwort kommt oder der Timeout greift.
_pending = False
_deadline = 0.0
_status = "idle"
_last_result = None     # fields-dict des letzten results (gates lesen das)
_last_payload = None    # zuletzt gesendetes fields-dict (gates vergleichen dagegen)
_request_id = ""       # conditional write correlation; foreign broadcast results stay pending
_last_tick_scale = None # conversion basis of _last_payload for merge-base acknowledgement
_last_local_signatures = {}  # authored state at send time, not at acknowledgement time
_request_context = None  # session-local scene/actor identities and original endpoint
ACTORS_IMPORT_TIMEOUT = 30.0

# ---- rueckwaerts-mappings, zur laufzeit aus actors.py abgeleitet (nie doppelt pflegen) ----
# (data_path, array_index) -> (bbs-kanal, scale, const)
_REVERSE_CHANNELS = {
    (dp, idx): (ch, scale, const)
    for ch, (dp, idx, scale, const) in _ACTOR_CHANNELS.items()
}

# Blender-interpolations-enum -> BBS-easing-basis (invertiert actors._build_interp_map's
# `types`-dict; CIRC->circle und EXPO->exp sind die beiden nicht-identischen namen).
_BLENDER_TO_BBS_BASE = {
    'SINE': "sine", 'CIRC': "circle", 'QUAD': "quad", 'CUBIC': "cubic",
    'QUART': "quart", 'QUINT': "quint", 'EXPO': "exp", 'BACK': "back",
    'ELASTIC': "elastic", 'BOUNCE': "bounce",
}
_EASE_SUFFIX = {'EASE_IN': "in", 'EASE_OUT': "out", 'EASE_IN_OUT': "inout"}
# Blenders 'AUTO'-easing-aufloesung (Blender-doku): dynamische typen easen out, rest in.
_DYNAMIC_TYPES = {'BACK', 'BOUNCE', 'ELASTIC'}


def _interp_for_point(kp, degradations):
    """BBS-interp-key fuer EINEN keyframe-point (rueckrichtung von _apply_kf_shape).

    `degradations` (set) sammelt ehrliche verluste fuer das send-log — nichts wird
    still geschluckt (actors.py-haltung, 'NOT applied'-log dort).
    """
    mode = kp.interpolation
    if mode == 'LINEAR':
        return "linear"
    if mode == 'CONSTANT':
        return "constant"
    if mode == 'BEZIER':
        # handle-TYP (AUTO/AUTO_CLAMPED/...) reist nicht — die positionen tun es.
        if kp.handle_left_type != 'FREE' or kp.handle_right_type != 'FREE':
            degradations.add("bezier-handle-typ %s/%s -> FREE-positionen"
                             % (kp.handle_left_type, kp.handle_right_type))
        return "bezier"
    base = _BLENDER_TO_BBS_BASE.get(mode)
    if base is None:
        # sollte nicht passieren (das enum ist oben komplett abgedeckt) — aber eine
        # neue Blender-version darf den sender nie crashen: ehrlich degradieren.
        degradations.add("%s -> linear (unbekannter interp-typ)" % mode)
        return "linear"
    suffix = _EASE_SUFFIX.get(kp.easing)
    if suffix is None:   # 'AUTO'
        suffix = "out" if mode in _DYNAMIC_TYPES else "in"
        degradations.add("%s easing AUTO -> %s_%s" % (mode, base, suffix))
    return "%s_%s" % (base, suffix)


def _keyframe_dict(kp, scale, const, tick_scale, degradations):
    """EIN keyframe-point -> BBS-keyframe-objekt (tick/value/interp/lx/ly/rx/ry).

    Formeln: modul-docstring "RUECKWAERTS-MAPPING". lx/rx sind tick-ABSTAENDE und
    muessen >=0 sein (BezierUtils normalisiert x1=rx/w bzw. x2=(w-lx)/w — negative
    abstaende gaebe es nur bei haendisch ueber den key gezogenen handles; clamp 0).
    """
    frame = float(kp.co[0])
    bl_val = float(kp.co[1])
    return {
        "tick": frame / tick_scale,
        "value": (bl_val - const) / scale,
        "interp": _interp_for_point(kp, degradations),
        "lx": max(0.0, (frame - float(kp.handle_left[0])) / tick_scale),
        "ly": (float(kp.handle_left[1]) - bl_val) / scale,
        "rx": max(0.0, (float(kp.handle_right[0]) - frame) / tick_scale),
        "ry": (float(kp.handle_right[1]) - bl_val) / scale,
    }


def channels_for_object(obj, tick_scale, degradations=None, include_unkeyed=False):
    """Alle mapped objekt-level fcurves eines actor-roots -> {"x": [kf,...], ...}.

    READ-ONLY am objekt (kein fc.update(), kein schreiben — der sender fasst user-
    daten nie an). Pose-bone-fcurves (replay_anim) und fremde kanaele fallen durch
    das exakte (data_path, index)-matching automatisch raus.
    """
    if degradations is None:
        degradations = set()
    channels = {}
    for coll in _object_fcurve_collections(obj):
        for fc in coll:
            spec = _REVERSE_CHANNELS.get((fc.data_path, fc.array_index))
            if spec is None:
                continue
            ch, scale, const = spec
            # defensiv nach frame sortieren (Blender haelt die punkte sortiert, aber
            # ein script-edit ohne update() koennte sie unsortiert hinterlassen —
            # java's channel.insert() sortiert eh, nur die handle-zuordnung braucht
            # stabile reihenfolge im vergleich).
            pts = sorted(fc.keyframe_points, key=lambda p: p.co[0])
            arr = [_keyframe_dict(kp, scale, const, tick_scale, degradations)
                   for kp in pts]
            if arr:                    # leeres array wuerde den BBS-kanal LEEREN (s.o.)
                channels[ch] = arr
                if ch == "yaw":
                    # BBS rotates the world actor by bodyYaw, while yaw is entity/look facing.
                    # A Blender root rotation drives both so visible body and facing cannot drift.
                    channels["bodyYaw"] = [dict(key) for key in arr]
    if include_unkeyed:
        # Native Undo can remove a newly created curve. Its restored static value
        # must replace the remote animation; absence cannot mean "ignore this path".
        for ch, (path, index, scale, const) in _ACTOR_CHANNELS.items():
            if ch not in channels:
                channels[ch] = [_linear_kf(0.0, (float(getattr(obj, path)[index]) - const) / scale)]
        channels.setdefault('bodyYaw', [dict(key) for key in channels['yaw']])
    return channels


# ---- KNOCHEN-KEYS (2026-09-13): pose.bones["<b>"].<prop> -> BBS-Knochen-Track pose.bones.<b> ----
# Gegenrichtung zu actor_bones.py (Identitaet: knochenlokal, Radiant, BOBJ-Einheiten). BBS haelt je
# Keyframe ALLE Komponenten eines Knochens mit EINER Interpolation; Blender eine Kurve je Komponente.
# Darum: Vereinigung der Frames aller Komponenten, fehlende Komponenten dort AUSGEWERTET (exakter
# Wert, Kurvenform des Segments geht in eine Naeherung — geloggt), Interpolation je Frame aus der
# Rotations-, sonst Orts-, sonst Skalenkurve mit echtem Key dort. Nur die Kurven, die Blender fuer
# den rotation_mode des Knochens wirklich auswertet, reisen (ein Euler-Knochen schickt keine
# Quaternion-Reste). AXIS_ANGLE wird je Frame zur Quaternion.
_BONE_PATH = re.compile(r'^pose\.bones\["(.+)"\]\.(location|rotation_euler|rotation_quaternion|rotation_axis_angle|scale)$')
_T_KEYS = ("tx", "ty", "tz")
_S_KEYS = ("sx", "sy", "sz")
_R_KEYS = ("rx", "ry", "rz")
_Q_KEYS = ("qw", "qx", "qy", "qz")


def _linear_kf(tick, value):
    return {"tick": tick, "value": float(value), "interp": "linear", "lx": 5.0, "ly": 0.0, "rx": 5.0, "ry": 0.0}


def _bone_curve(fc, frames, default, tick_scale, degradations, bone):
    """EINE Komponente an allen Vereinigungs-Frames: echter Key wo vorhanden (mit Interp/Handles),
    sonst der ausgewertete Wert (linear markiert)."""
    if fc is None:
        return [_linear_kf(f / tick_scale, default) for f in frames]
    by_frame = {round(float(kp.co[0]), 4): kp for kp in fc.keyframe_points}
    arr = []
    for f in frames:
        kp = by_frame.get(f)
        if kp is not None:
            arr.append(_keyframe_dict(kp, 1.0, 0.0, tick_scale, degradations))
        else:
            degradations.add("knochen %s: komponenten mit ungleichen key-frames -> fehlende an der "
                             "vereinigung ausgewertet" % bone)
            arr.append(_linear_kf(f / tick_scale, fc.evaluate(f)))
    return arr


def _shape_list(curves, frames, tick_scale):
    """Je Frame die Keyframe-Form (interp/handles) — aus der ersten Kurve in `curves`, die dort einen
    ECHTEN Key hat (Reihenfolge = Prioritaet)."""
    out = []
    for f in frames:
        chosen = None
        for fc in curves:
            if fc is None:
                continue
            for kp in fc.keyframe_points:
                if round(float(kp.co[0]), 4) == f:
                    chosen = kp
                    break
            if chosen is not None:
                break
        if chosen is None:
            out.append(_linear_kf(f / tick_scale, 0.0))
        else:
            d = _keyframe_dict(chosen, 1.0, 0.0, tick_scale, set())
            out.append({"tick": d["tick"], "interp": d["interp"], "lx": d["lx"], "ly": d["ly"], "rx": d["rx"], "ry": d["ry"]})
    return out


def bones_for_object(obj, tick_scale, degradations=None):
    """Alle Pose-Bone-F-Curves einer Armature -> {"<knochen>": {sub-kurven}} (siehe Kopf).
    READ-ONLY am Objekt. Leer fuer Nicht-Armatures oder ohne Knochen-Keys."""
    if degradations is None:
        degradations = set()
    if obj is None or obj.type != 'ARMATURE':
        return {}
    per = {}
    for coll in _object_fcurve_collections(obj):
        for fc in coll:
            m = _BONE_PATH.match(fc.data_path)
            if not m:
                continue
            bone, prop = m.group(1), m.group(2)
            if obj.pose.bones.get(bone) is None:
                continue
            per.setdefault(bone, {}).setdefault(prop, {})[fc.array_index] = fc
    out = {}
    for bone, props in per.items():
        pb = obj.pose.bones[bone]
        loc = props.get("location", {})
        scl = props.get("scale", {})
        mode = pb.rotation_mode
        if mode in EULER_MODES:
            rot_prop, rot = "euler", props.get("rotation_euler", {})
        elif mode == 'QUATERNION':
            rot_prop, rot = "quat", props.get("rotation_quaternion", {})
        else:
            rot_prop, rot = "axis_angle", props.get("rotation_axis_angle", {})
        curves = [c for c in list(rot.values()) + list(loc.values()) + list(scl.values()) if c is not None]
        if not curves:
            continue
        frames = sorted({round(float(kp.co[0]), 4) for fc in curves for kp in fc.keyframe_points})
        if not frames:
            continue
        sub = {}
        for i, k in enumerate(_T_KEYS):
            sub[k] = _bone_curve(loc.get(i), frames, 0.0, tick_scale, degradations, bone)
        for i, k in enumerate(_S_KEYS):
            sub[k] = _bone_curve(scl.get(i), frames, 1.0, tick_scale, degradations, bone)
        if rot_prop == "euler":
            for i, k in enumerate(_R_KEYS):
                sub[k] = _bone_curve(rot.get(i), frames, 0.0, tick_scale, degradations, bone)
            sub["quat"] = False
        elif rot_prop == "quat":
            for i, k in enumerate(_Q_KEYS):
                sub[k] = _bone_curve(rot.get(i), frames, 1.0 if k == "qw" else 0.0, tick_scale, degradations, bone)
            sub["quat"] = True
        else:
            # AXIS_ANGLE: je Frame auswerten und zur Quaternion (BBS kennt keinen Achse-Winkel-Modus).
            from mathutils import Quaternion
            arrs = {k: [] for k in _Q_KEYS}
            for f in frames:
                vals = [rot[i].evaluate(f) if rot.get(i) is not None else (0.0 if i else 0.0) for i in range(4)]
                angle, ax = vals[0], (vals[1], vals[2], vals[3])
                q = Quaternion(ax, angle) if any(abs(v) > 1e-12 for v in ax) else Quaternion((1.0, 0.0, 0.0, 0.0))
                for k, v in zip(_Q_KEYS, (q.w, q.x, q.y, q.z)):
                    arrs[k].append(_linear_kf(f / tick_scale, v))
            sub.update(arrs)
            sub["quat"] = True
            degradations.add("knochen %s: AXIS_ANGLE je frame zur quaternion (linear)" % bone)
        # Interpolation je Keyframe: Rotation zuerst, dann Ort, dann Skala.
        prio = [rot.get(i) for i in sorted(rot)] + [loc.get(i) for i in sorted(loc)] + [scl.get(i) for i in sorted(scl)]
        sub["interp"] = _shape_list(prio, frames, tick_scale)
        out[bone] = sub
    from . import pose_units
    return pose_units.curves(obj, pose_units.remove_rest(obj, out), False)


def _rig_property_paths(obj):
    """Exact custom-property RNA paths, including Blender's own escaping."""
    if obj is None or obj.type != 'ARMATURE':
        return {}
    out = {}
    for pose_bone in obj.pose.bones:
        for prop in pose_bone.keys():
            if prop == "_RNA_UI":
                continue
            value = pose_bone.get(prop)
            if isinstance(value, bool):
                kind = "bool"
            elif isinstance(value, int):
                kind = "int"
            elif isinstance(value, float):
                kind = "float"
            else:
                continue
            bone_id = bpy.utils.escape_identifier(pose_bone.name)
            prop_id = bpy.utils.escape_identifier(prop)
            path = 'pose.bones["%s"]["%s"]' % (bone_id, prop_id)
            metadata = {"kind": kind, "default": value}
            try:
                ui = pose_bone.id_properties_ui(prop).as_dict()
                for key in ("default", "min", "max", "soft_min", "soft_max"):
                    if key in ui and isinstance(ui[key], (bool, int, float)):
                        metadata[key] = ui[key]
            except (KeyError, TypeError, AttributeError, RuntimeError):
                pass
            out[(pose_bone.name, prop)] = (path, metadata)
    return out


def rig_property_for_object(obj, bone, prop, tick_scale, degradations=None):
    """One pose-bone control as BBS schema+keys; unkeyed controls become a constant key."""
    if degradations is None:
        degradations = set()
    found = _rig_property_paths(obj).get((bone, prop))
    if found is None:
        return None
    path, metadata = found
    pose_bone = obj.pose.bones.get(bone)
    fc = next((curve for collection in _object_fcurve_collections(obj) for curve in collection
               if curve.data_path == path), None)
    if fc is None or not fc.keyframe_points:
        keys = [_linear_kf(0.0, float(pose_bone.get(prop)))]
    else:
        keys = [_keyframe_dict(point, 1.0, 0.0, tick_scale, degradations)
                for point in sorted(fc.keyframe_points, key=lambda point: point.co[0])]
    for key in keys:
        if key.get("interp") != "bezier":
            # BBS evaluates handles only for Bezier segments. Blender's automatic LINEAR handles
            # depend on neighboring frame spacing and would create false revisions despite having
            # zero effect; project them to BBS' native ignored defaults.
            key.update(lx=5.0, ly=0.0, rx=5.0, ry=0.0)
    if metadata["kind"] in {"int", "bool"}:
        for key in keys:
            key["interp"] = "constant"
    return {**metadata, "keys": keys}


def rig_properties_for_object(obj, tick_scale, degradations=None, keyed_only=True):
    """Pose-bone controls -> nested wire map. Full snapshots default to keyed controls only."""
    if degradations is None:
        degradations = set()
    paths = _rig_property_paths(obj)
    if not paths:
        return {}
    fcurve_paths = {curve.data_path for collection in _object_fcurve_collections(obj)
                    for curve in collection if curve.keyframe_points}
    out = {}
    for (bone, prop), (path, _metadata) in paths.items():
        if keyed_only and path not in fcurve_paths:
            continue
        spec = rig_property_for_object(obj, bone, prop, tick_scale, degradations)
        if spec is not None:
            out.setdefault(bone, {})[prop] = spec
    return out


def _actor_roots():
    """Alle actor-roots der MC_Actors-collection (parent None + bbs_uuid-prop) —
    dieselbe quelle wie ui.py's replay-anim-dropdown (custom-props, kein state-spiegel)."""
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return []
    return [o for o in coll.objects if o.parent is None and o.get("bbs_uuid")]


def build_payload(uuids=None):
    """Das ACTORS_IMPORT-fields-dict bauen, ohne zu senden (gates inspizieren es so).

    Gibt (fields, skipped_names, degradations) zurueck; replays ohne einen einzigen
    mapped-kanal werden ausgelassen (nichts zu senden = nichts kaputtzumachen).
    """
    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0
    degradations = set()
    replays = []
    skipped = []
    for obj in _actor_roots():
        uuid = obj.get("bbs_uuid")
        if uuids is not None and uuid not in uuids:
            continue
        channels = channels_for_object(obj, tick_scale, degradations)
        bones = bones_for_object(obj, tick_scale, degradations)
        rig_properties = rig_properties_for_object(obj, tick_scale, degradations)
        if not channels and not bones and not rig_properties:
            skipped.append(obj.name)
            continue
        eintrag = {"uuid": uuid, "channels": channels}
        if bones:
            eintrag["bones"] = bones      # java PoseBoneImporter -> pose.bones.<knochen>
        if rig_properties:
            eintrag["rigProperties"] = rig_properties
        replays.append(eintrag)
    return {"replays": replays}, skipped, degradations


def build_conditional_payload(uuids=None, bases=None):
    """Build only locally changed root/item/bone paths with their last BBS values."""
    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0
    degradations = set()
    replays = []
    skipped = []
    wanted = set(uuids) if uuids is not None else None

    for obj in _actor_roots():
        replay_uuid = obj.get("bbs_uuid")
        if wanted is not None and replay_uuid not in wanted:
            continue
        channels = channels_for_object(obj, tick_scale, degradations, include_unkeyed=True)
        strings = actor_channel_sync.strings_for_object(obj, tick_scale)
        bones = bones_for_object(obj, tick_scale, degradations)
        patch = actor_channel_sync.local_patch(
            obj, tick_scale, channels, strings, bones, (bases or {}).get(replay_uuid),
            degradations)
        if patch is not None:
            replays.append(patch)
        else:
            skipped.append(obj.name)

    fields = {"channelPatch": 1, "requestId": uuidlib.uuid4().hex, "replays": replays}
    return fields, skipped, degradations, tick_scale


def send_actors(uuids=None):
    """Send locally changed root/item/bone/ShapeKey curves as one conditional patch."""
    global _pending, _deadline, _status, _last_payload, _request_id, _last_tick_scale, _last_result
    global _last_local_signatures, _request_context
    from . import actor_outbox
    if actor_outbox.bound():
        sent = actor_outbox.queue(uuids)
        _status = actor_outbox._status
        return sent
    _check_timeout()
    if not state._connected or state._sock is None:
        _status = "not connected"
        return False
    if not state._peer_ready:
        _status = "waiting for a fresh BBS handshake"
        return False
    if _pending:
        _status = "import already pending"
        return False
    if _request_id:
        return recover_actor_receipt()
    if not state.bound_film:
        _status = "no bound film; import actors first"
        return False
    if "actor_channel_patch_v1" not in state.peer_capabilities:
        _status = "BBS peer lacks safe actor_channel_patch_v1; write disabled"
        return False
    if state.film_switch_detected:
        _status = ("filmwechsel: gebunden an '%s', BBS zeigt '%s' — erst neu binden "
                   "(return to the bound film or explicitly Import Actors)" % (state.bound_film, state.film_switch_other))
        print("[bbs-bridge] ACTORS_IMPORT geblockt (filmwechsel): %s" % _status)
        return False
    if uuids is not None:
        uuids = set(uuids)
    try:
        fields, skipped, degradations, tick_scale = build_conditional_payload(uuids)
        if (any(patch.get("shapeKeys") for patch in fields["replays"])
                and "actor_shape_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks safe actor_shape_patch_v1; ShapeKey write disabled"
            return False
        if (any(actor_channel_sync.shape_patch_is_structural(patch)
                for patch in fields["replays"])
                and "actor_shape_map_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks safe actor_shape_map_patch_v1; ShapeKey add/remove disabled"
            return False
        if (any(patch.get("shapeTimeline") for patch in fields["replays"])
                and "actor_shape_timeline_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks safe actor_shape_timeline_patch_v1; ShapeKey timeline write disabled"
            return False
        if (any(patch.get("shapeGrid") for patch in fields["replays"])
                and "actor_shape_grid_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks safe actor_shape_grid_patch_v1; ShapeKey key-count write disabled"
            return False
        if (any(actor_channel_sync.shape_patch_uses_easing(patch)
                for patch in fields["replays"])
                and "actor_shape_easing_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks exact ShapeKey easing support"
            return False
        if (any('visibility' in patch for patch in fields['replays'])
                and 'actor_visibility_patch_v1' not in state.peer_capabilities):
            _status = 'BBS peer lacks safe actor visibility writes'
            return False
        if (any(patch.get("rigProperties") for patch in fields["replays"])
                and "actor_rig_property_patch_v1" not in state.peer_capabilities):
            _status = "BBS peer lacks safe actor_rig_property_patch_v1; rig-control write disabled"
            return False
        roots = {str(obj.get("bbs_uuid") or ""): obj for obj in _actor_roots()}
        _last_local_signatures = {
            patch["uuid"]: actor_channel_sync.capture_export_signatures(roots[patch["uuid"]], patch)
            for patch in fields["replays"]}
    except Exception as exc:
        _status = "cannot build protected patch: %s" % exc
        print("[bbs-bridge] ACTORS_IMPORT geblockt: %s" % _status)
        return False
    if not fields["replays"]:
        _status = "no local protected changes to send"
        return False
    if state.bound_film:
        fields["film"] = state.bound_film

    _last_payload = fields
    _request_id = fields["requestId"]
    _last_tick_scale = tick_scale
    _request_context = (bpy.context.scene, state._connected_endpoint,
                        {patch["uuid"]: roots[patch["uuid"]] for patch in fields["replays"]})
    _last_result = None
    _pending = True
    _deadline = time.monotonic() + ACTORS_IMPORT_TIMEOUT
    n_keys = sum(len(arr) for replay in fields["replays"]
                 for section in ("channels", "strings")
                 for arr in (replay.get(section) or {}).values())
    n_keys += sum(len(values.get("interp") or next(
                      (curve for name, curve in values.items() if name != "quat" and isinstance(curve, list)), []))
                  for replay in fields["replays"] for values in (replay.get("bones") or {}).values())
    n_keys += sum(len(curve) for replay in fields["replays"]
                  for curve in (replay.get("shapeKeys") or {}).values() if isinstance(curve, list))
    n_keys += sum(len(replay.get("shapeTimeline") or ()) for replay in fields["replays"])
    n_keys += sum(len(curve) for replay in fields["replays"]
                  for curve in (replay.get("shapeGrid") or {}).values())
    n_keys += sum(len(spec.get("keys") or ()) for replay in fields["replays"]
                  for properties in (replay.get("rigProperties") or {}).values()
                  for spec in properties.values() if isinstance(spec, dict))
    _status = "sent protected %d replays / %d keys" % (len(fields["replays"]), n_keys)
    _send({"type": "ACTORS_IMPORT", "origin": "blender", "uuid": "actors",
           "seq": _next_seq(), "fields": fields})
    print("[bbs-bridge] conditional ACTORS_IMPORT sent: %d replays, %d keys%s"
          % (len(fields["replays"]), n_keys,
             (" (unchanged: %s)" % ", ".join(skipped)) if skipped else ""))
    for degradation in sorted(degradations):
        print("[bbs-bridge] ACTORS_IMPORT degradation: %s" % degradation)
    return True


def _context_is_current():
    """A receipt must never acknowledge replacement actors after import/load/scene switch."""
    if _request_context is None or not _last_payload:
        return False
    try:
        scene, endpoint, sent_roots = _request_context
        roots = {str(obj.get("bbs_uuid") or ""): obj for obj in _actor_roots()}
        return (bpy.context.scene == scene and endpoint == state._connected_endpoint
                and bool(endpoint) and not state.film_switch_detected
                and state.bound_film == _last_payload.get("film")
                and all(roots.get(uid) == obj and actor_channel_sync.read(obj) is not None
                        for uid, obj in sent_roots.items()))
    except (ReferenceError, RuntimeError):
        return False


def recover_actor_receipt():
    """Query the original result, never resubmit the original mutation."""
    global _pending, _deadline, _status
    if _pending or not state._connected or state._sock is None or not state._peer_ready:
        _status = "receipt recovery requires an idle sender and a fresh connected peer"
        return False
    if not _request_id or not _context_is_current():
        _status = "pending send belongs to another film, scene, actors or endpoint; not recovered"
        return False
    if "actor_receipt_recovery_v1" not in state.peer_capabilities:
        _status = "BBS peer cannot recover receipts; outcome unknown, no write repeated"
        return False
    _pending = True
    _deadline = time.monotonic() + ACTORS_IMPORT_TIMEOUT
    _status = "recovering previous receipt (read-only)"
    _send({"type": "ACTORS_IMPORT_RECEIPT", "origin": "blender", "uuid": "actors",
           "seq": _next_seq(), "fields": _last_payload})
    return True


def forget_actor_receipt():
    """Explicitly forget tracking, not the authored edits and not a server-side write."""
    global _request_id, _request_context, _status
    if _pending or not _request_id:
        return False
    _request_id = ""
    _request_context = None
    _status = "receipt tracking forgotten; edits kept, BBS write NOT undone"
    return True


def _handle_actors_import_result(fields):
    """Accept only our exact request, including late receipts after a timeout."""
    global _pending, _status, _last_result, _request_id, _request_context
    if not isinstance(fields, dict) or not _request_id or fields.get("requestId") != _request_id:
        return
    _pending = False
    _last_result = dict(fields)
    if type(fields.get("ok")) is not bool or fields.get("receiptUnknown"):
        _status = "outcome unknown: %s; no write repeated, local edits kept" % (
            fields.get("error") or "unreadable receipt")
        return
    try:
        if fields["ok"]:
            if not _context_is_current():
                _status = "receipt retained: original scene/actors/film/endpoint no longer active"
                return
            roots = _request_context[2]
            for patch in _last_payload["replays"]:
                actor_channel_sync.acknowledge_export(
                    roots[patch["uuid"]], patch, _last_tick_scale, _last_local_signatures[patch["uuid"]])
            suffix = " (recovered previous receipt)" if fields.get("receiptRecovered") else (
                " (already applied)" if fields.get("alreadyApplied") else "")
            _status = "confirmed %s / skipped %s%s" % (
                fields.get("applied", "?"), fields.get("skipped", "?"), suffix)
        elif fields.get("filmMismatch"):
            state.film_switch_detected = True
            state.film_switch_other = str(fields.get("film") or "")
            _status = "film mismatch: write rejected; actor sync paused"
        elif fields.get("conflict"):
            _status = "conflict: %s — BBS kept its newer value" % fields.get("conflict")
        else:
            _status = "import failed: %s" % (fields.get("error") or "unknown")
        _request_id = ""
        _request_context = None
        print("[bbs-bridge] ACTORS_IMPORT result: %s" % _status)
    except Exception as exc:
        _status = "receipt retained after acknowledgement error: %s" % exc
        print("[bbs-bridge] ACTORS_IMPORT result unreadable:", exc)


def _check_timeout():
    """A timeout/closed connection says nothing about whether BBS executed the write."""
    global _pending, _status
    if _pending and (not state._connected or state._sock is None or time.monotonic() > _deadline):
        _pending = False
        _status = "outcome unknown; recover pending send before sending newer edits"
        print("[bbs-bridge] ACTORS_IMPORT receipt missing; request retained")
