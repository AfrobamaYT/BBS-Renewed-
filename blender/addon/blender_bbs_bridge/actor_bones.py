"""Knochen-Pose BBS -> Blender: die Sub-Kurven aus `properties.bones` (Knochen-Tracks
`pose.bones.<knochen>`) und `properties.poseTrack` (der Pose-Track `pose`, je Knochen aufgefaechert;
siehe java PerBoneExporter) als ECHTE Keyframes auf die Pose-Bones der BOBJ-Armature.

=== WARUM NEU (2026-09-13, am laufenden Stack gemessen) ===
Die Fassung davor (PHASE B) suchte `pose:`-Kanaele (gibt es in diesem BBS nicht — der Arm kam nie
an), konjugierte die Rotation in den Rest-Frame des Knochens (P = H^-1 R H), schrieb rotation_euler
in XYZ und ERZWANG rotation_mode XYZ — als kaeme die Rotation im Modellraum an wie bei Vanilla-
ModelParts. BBS legt die Pose eines BOBJ-Knochens aber LOKAL auf die Ruhematrix, in der Drehordnung
des Knochens (BOBJBone.applyTransformations: mat = relBoneMat; translate; kanalDrehung mit
eulerQuaternion in eulerOrdnung; scale) — exakt Blenders chan_mat. Der Rig-Abgleich
(docs/rig-abgleich-2026-09-13, 212 Knochen, <= 0,00014) setzt dieselben knochenlokalen Werte auf
beiden Seiten und trifft. Also IDENTITAET:
    rotation_euler = (rx, ry, rz) in der Ordnung des Knochens   (Radiant beidseitig)
    location       = (tx, ty, tz)   (BOBJ-Einheiten == Blender-Einheiten; die Objektskala aus
                                     bbs.json liegt auf beiden Seiten obendrauf)
    scale          = (sx, sy, sz)

=== ECHTE KEYS STATT DICHTEM BACKEN ===
Der Link ist bidirektional: was hier ankommt, soll in Blender editierbar sein und zurueckreisen
(actor_export.bones_for_object). Darum je Komponente die BBS-Keys mit ihrer Interpolation (volle
Interp-Map aus actors, Bezier-Handles exakt). Dicht gebacken wird nur noch, wenn ein Knochen in
BEIDEN Spuren steht — BBS addiert die Spuren (BOBJModel.applyPose), Blender hat dafuer keine Kurve.

=== ROTATIONSMODUS (nie veraendert — das ist das Rig des Nutzers) ===
- Euler-Knochen + Euler-Keys: rx/ry/rz direkt — exakt, mit Handles.
- Quaternion-Knochen + Quaternion-Keys (payload quat=true): qw/qx/qy/qz direkt. BBS interpoliert
  Quaternion-Keys komponentenweise und normalisiert (Transform.interpolateQuat), wie Blenders
  Quaternion-F-Curves — exakt, mit Handles.
- Kreuzfaelle (Euler-Key auf Quaternion-Knochen: BBS' Euler ist dann XYZ; Quaternion-Key auf
  Euler-Knochen; AXIS_ANGLE): je Key umgerechnet, Kurvenform Bezier-auto — an den Keys exakt,
  dazwischen Naeherung. Wird geloggt.

=== CHIRURGIE ===
Re-Import ersetzt nur die eigenen Pfade der Knochen im Payload (location, rotation_euler,
rotation_quaternion, rotation_axis_angle, scale). model_pose (head/body aus headYaw/bodyYaw) laeuft
vorher; ein Knochen im Payload gewinnt (expliziter Key schlaegt prozeduralen Default).
WIRFT NIE (Tick-Regel) — jeder Fehlerpfad loggt.
"""

import math

import bpy
from mathutils import Euler, Quaternion, Vector

from .actors import _build_fcurve, _find_fcurve, _apply_kf_shape, _kf_float, _exact_hermite

EULER_MODES = ('XYZ', 'XZY', 'YXZ', 'YZX', 'ZXY', 'ZYX')
_T_KEYS = ("tx", "ty", "tz")
_S_KEYS = ("sx", "sy", "sz")
_R_KEYS = ("rx", "ry", "rz")
_Q_KEYS = ("qw", "qx", "qy", "qz")          # Blender-Reihenfolge (w, x, y, z)
_OWNED = ("location", "rotation_euler", "rotation_quaternion", "rotation_axis_angle", "scale")

# Haerte-Kappe fuer das dichte Backen (Knochen in beiden Spuren)
MAX_TICKS = 20000


# ---------------------------------------------------------------------------------------------
# Payload / F-Curve-Hilfen
# ---------------------------------------------------------------------------------------------

def _payload(replay, key):
    """`properties.<key>` als dict, sonst {}."""
    if not isinstance(replay, dict):
        return {}
    props = replay.get("properties")
    if not isinstance(props, dict):
        return {}
    val = props.get(key)
    return val if isinstance(val, dict) else {}


def _keys(sub, name):
    """Die Key-Liste einer Sub-Kurve (nur dicts), sonst []."""
    val = sub.get(name) if isinstance(sub, dict) else None
    return [kf for kf in val if isinstance(kf, dict)] if isinstance(val, list) else []


def _nontrivial(keys, ref):
    return any(abs(_kf_float(kf, "value", ref) - ref) > 1e-9 for kf in keys)


def _ticks(sub, names):
    """Vereinigung der Ticks ueber die genannten Sub-Kurven, sortiert."""
    out = set()
    for n in names:
        for kf in _keys(sub, n):
            out.add(round(_kf_float(kf, "tick", 0.0), 4))
    return sorted(out)


def _value_at(keys, tick, default):
    for kf in keys:
        if abs(_kf_float(kf, "tick", 0.0) - tick) <= 1e-3:
            return _kf_float(kf, "value", default)
    return default


def _interp_at(sub, tick, names):
    """Interp-Key des ersten Keys an diesem Tick in den genannten Sub-Kurven; Bezier wird zu
    'hermite' (Bezier-auto), weil Handles eine nichtlineare Umrechnung nicht ueberleben."""
    for n in names:
        for kf in _keys(sub, n):
            if abs(_kf_float(kf, "tick", 0.0) - tick) <= 1e-3:
                key = str(kf.get("interp") or "linear").lower()
                return "hermite" if key == "bezier" else key
    return "linear"


def _fcurve_collections(obj):
    """Alle F-Curve-Container der Objekt-Action (5.2 slotted actions: channelbag zuerst,
    legacy-fallback)."""
    from . import pose_overlays
    return pose_overlays.collections(obj)


def _owned_paths(bone_names):
    return {'pose.bones["%s"].%s' % (n, p) for n in bone_names for p in _OWNED}


def _remove_owned_fcurves(arm_obj, bone_names):
    """NUR die eigenen Pfade der genannten Knochen entfernen — einzeln mit Re-Scan (remove
    invalidiert Referenzen)."""
    owned = _owned_paths(bone_names)
    removed = 0
    for coll in _fcurve_collections(arm_obj):
        while True:
            victim = None
            for fc in coll:
                if fc.data_path in owned:
                    victim = fc
                    break
            if victim is None:
                break
            try:
                # Removing an F-curve leaves its last evaluated RNA value behind.
                # Reset only the owned component: a neutral incoming key may be
                # omitted below, and must not retain a cancelled preview value.
                path, index = victim.data_path, victim.array_index
                prop = path.rsplit('.', 1)[-1]
                default = (1.0 if prop == 'scale'
                           or (prop == 'rotation_quaternion' and index == 0)
                           or (prop == 'rotation_axis_angle' and index == 2) else 0.0)
                coll.remove(victim)
                arm_obj.path_resolve(path)[index] = default
                removed += 1
            except (RuntimeError, ReferenceError):
                break
    return removed


def reset_pose(obj):
    """A deleted whole Pose track also clears unkeyed transform remnants."""
    if obj.type != 'ARMATURE':
        return
    _remove_owned_fcurves(obj, obj.pose.bones.keys())
    for bone in obj.pose.bones:
        bone.location = (0, 0, 0)
        bone.rotation_euler = (0, 0, 0)
        bone.rotation_quaternion = (1, 0, 0, 0)
        bone.rotation_axis_angle = (0, 0, 1, 0)
        bone.scale = (1, 1, 1)


def _rig_property_path(bone, prop):
    return 'pose.bones["%s"]["%s"]' % (
        bpy.utils.escape_identifier(bone), bpy.utils.escape_identifier(prop))


def _remove_rig_property_fcurve(arm_obj, path):
    removed = 0
    for coll in _fcurve_collections(arm_obj):
        while True:
            victim = next((fc for fc in coll if fc.data_path == path), None)
            if victim is None:
                break
            try:
                coll.remove(victim)
                removed += 1
            except (RuntimeError, ReferenceError):
                break
    return removed


def _cast_rig_value(value, kind):
    if kind == "bool":
        return bool(round(value))
    if kind == "int":
        return int(round(value))
    return float(value)


def _rig_kind(value):
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, int):
        return "int"
    return "float"


def _ensure_rig_property(pb, prop, spec, first_value):
    kind = str(spec.get("kind") or "float").lower()
    if kind not in {"float", "int", "bool"}:
        kind = "float"
    if prop in pb:
        kind = _rig_kind(pb.get(prop))
    else:
        pb[prop] = _cast_rig_value(spec.get("default", first_value), kind)
    try:
        ui = pb.id_properties_ui(prop)
        kwargs = {}
        for key in ("default", "min", "max", "soft_min", "soft_max"):
            value = spec.get(key)
            if isinstance(value, (bool, int, float)):
                kwargs[key] = _cast_rig_value(value, kind)
        if kwargs:
            ui.update(**kwargs)
    except (KeyError, TypeError, AttributeError, RuntimeError):
        pass
    return kind


def _build_rig_property_fcurve(obj, pb, prop, spec, tick_scale):
    keys = spec.get("keys") if isinstance(spec, dict) else None
    if not isinstance(keys, list):
        return 0
    pts = []
    seen = set()
    for kf in sorted((key for key in keys if isinstance(key, dict)),
                     key=lambda key: _kf_float(key, "tick", 0.0)):
        frame = _kf_float(kf, "tick", 0.0) * tick_scale
        marker = round(frame, 4)
        if marker in seen:
            continue
        seen.add(marker)
        pts.append((frame, _kf_float(kf, "value", 0.0), kf))
    if not pts:
        return 0

    path = _rig_property_path(pb.name, prop)
    _remove_rig_property_fcurve(obj, path)
    first_frame, first_value, first_kf = pts[0]
    kind = _ensure_rig_property(pb, prop, spec, first_value)
    try:
        pb[prop] = _cast_rig_value(first_value, kind)
        if not obj.keyframe_insert(data_path=path, frame=first_frame):
            return 0
    except (RuntimeError, TypeError, AttributeError, ValueError) as exception:
        print("[bbs-bridge] actor_bones: rig-property %s keyframe_insert fehlgeschlagen: %s"
              % (path, exception))
        return 0

    fc = _find_fcurve(obj, path, 0)
    if fc is None:
        return 0
    first = fc.keyframe_points[0]
    first.co = (first_frame, first_value)
    _apply_kf_shape(first, first_kf, first_frame, first_value, 1.0, tick_scale)
    if kind in {"int", "bool"}:
        first.interpolation = 'CONSTANT'
    for frame, value, kf in pts[1:]:
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        _apply_kf_shape(kp, kf, frame, value, 1.0, tick_scale)
        if kind in {"int", "bool"}:
            kp.interpolation = 'CONSTANT'
    fc.update()
    return len(pts)


def _apply_rig_properties(obj, replay, tick_scale, affected=None):
    rig = _payload(replay, "rigProperties")
    paths = ({(bone, prop) for bone, props in rig.items() if isinstance(props, dict)
              for prop in props} if affected is None else set(affected))
    if not paths:
        return 0
    n_keys = 0
    n_props = 0
    missing = []
    for bone, prop in sorted(paths):
        pb = obj.pose.bones.get(bone) if isinstance(bone, str) else None
        if pb is None:
            missing.append(str(bone))
            continue
        path = _rig_property_path(bone, prop)
        _remove_rig_property_fcurve(obj, path)
        spec = rig.get(bone, {}).get(prop) if isinstance(rig.get(bone), dict) else None
        if not isinstance(spec, dict):
            continue
        count = _build_rig_property_fcurve(obj, pb, prop, spec, tick_scale)
        if count:
            n_props += 1
            n_keys += count
    if missing:
        print("[bbs-bridge] actor_bones: rig-property bones ohne Treffer (skip): %s"
              % ", ".join(sorted(set(missing))))
    if n_keys:
        print("[bbs-bridge] actor_bones: %d rig-property keys auf %d Reglern von '%s'"
              % (n_keys, n_props, obj.name))
    return n_keys


def _build_bone_fcurve(obj, keyframes, data_path, index, tick_scale):
    """Wie actors._build_fcurve, aber fuer verschachtelte Pfade (pose.bones["x"].location):
    setzen ueber path_resolve, anlegen ueber keyframe_insert am OBJEKT (5.2-korrekte Action + Slot),
    dann direkt fuellen. scale 1 / const 0: F-Curve-Wert == Kanalwert. Gibt die Key-Zahl zurueck."""
    pts = []
    seen = set()
    for kf in sorted(keyframes, key=lambda k: _kf_float(k, "tick", 0.0)):
        frame = _kf_float(kf, "tick", 0.0) * tick_scale
        marker = round(frame, 4)
        if marker in seen:
            continue
        seen.add(marker)
        pts.append((frame, _kf_float(kf, "value", 0.0), kf))
    if not pts:
        return 0
    first_frame, first_value, _ = pts[0]
    try:
        obj.path_resolve(data_path)[index] = first_value
        if not obj.keyframe_insert(data_path=data_path, index=index, frame=first_frame):
            return 0
    except (RuntimeError, TypeError, AttributeError, ValueError) as e:
        print("[bbs-bridge] actor_bones: keyframe_insert %s[%d] fehlgeschlagen: %s" % (data_path, index, e))
        return 0
    fc = _find_fcurve(obj, data_path, index)
    if fc is None:
        return 0
    for frame, value, kf in pts:
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        _apply_kf_shape(kp, kf, frame, value, 1.0, tick_scale)
    fc.update()
    _exact_hermite(fc, pts)
    return len(pts)


def _sub_evaluator(sub_keys, obj, spec, tick_scale):
    """Eine Sub-Kurve auf ein Wegwerf-Objekt legen (actors._build_fcurve, volle Interp-Map) und
    einen (frame)->Wert-Evaluator zurueckgeben — fuer das dichte Backen."""
    if not sub_keys:
        return None
    n, _mf = _build_fcurve(obj, sub_keys, spec, tick_scale)
    if not n:
        return None
    fc = _find_fcurve(obj, spec[0], spec[1])
    if fc is None:
        return None
    return lambda f, _fc=fc: _fc.evaluate(f)


# ---------------------------------------------------------------------------------------------
# Einstieg
# ---------------------------------------------------------------------------------------------

def apply_bone_pose(obj, replay, tick_scale, affected_bones=None, affected_rig=None):
    """Apply explicit bone curves.

    Full imports pass no subset. Live sync passes exactly the bones whose remote
    properties changed, so sibling authored curves never get rebuilt from a full snapshot.
    """
    try:
        return _apply(obj, replay, tick_scale, affected_bones, affected_rig)
    except Exception as e:   # noqa: BLE001 — ein Pose-Bau darf Import/Tick nie killen
        print("[bbs-bridge] actor_bones pose failed (%s): %s" % (getattr(obj, "name", "?"), e))
        return 0


def _apply(obj, replay, tick_scale, affected_bones=None, affected_rig=None):
    if obj is None or obj.type != 'ARMATURE':
        return 0
    rig_keys = _apply_rig_properties(obj, replay, tick_scale, affected_rig)
    bones = _payload(replay, "bones")
    track = _payload(replay, "poseTrack")
    from . import pose_units
    bones = pose_units.curves(obj, bones, True)
    track = pose_units.curves(obj, track, True)
    bones, track = pose_units.add_rest(obj, bones, track)
    pose_units.apply_rest_base(obj)
    names = set(bones) | set(track) if affected_bones is None else set(affected_bones)
    if not names:
        return rig_keys

    present, missing = [], []
    for name in sorted(names):
        if obj.pose.bones.get(name) is not None and obj.data.bones.get(name) is not None:
            present.append(name)
        else:
            missing.append(name)
    if not present:
        print("[bbs-bridge] actor_bones: keine Payload-Knochen in Armature '%s' (payload: %s)"
              % (obj.name, ", ".join(sorted(names))))
        return rig_keys

    _remove_owned_fcurves(obj, present)
    log = set()
    n_keys = 0
    dicht = []
    for name in present:
        pb = obj.pose.bones[name]
        srcs = [s for s in (bones.get(name), track.get(name)) if isinstance(s, dict)]
        if not srcs:
            continue
        if len(srcs) == 1:
            n_keys += _apply_source(obj, pb, srcs[0], tick_scale, log)
        else:
            n_keys += _apply_sum_dense(obj, pb, srcs, tick_scale, log)
            dicht.append(name)

    if missing:
        print("[bbs-bridge] actor_bones: Payload-Knochen ohne Armature-Treffer (skip): %s" % ", ".join(missing))
    if dicht:
        print("[bbs-bridge] actor_bones: Knochen in Pose-Track UND Knochen-Track, dicht je Tick summiert: %s"
              % ", ".join(dicht))
    for zeile in sorted(log):
        print("[bbs-bridge] actor_bones: %s" % zeile)
    if n_keys:
        print("[bbs-bridge] actor_bones: %d keys auf %d Knochen von '%s' (%s)"
              % (n_keys, len(present), obj.name, ", ".join(present)))
    return n_keys + rig_keys


# ---------------------------------------------------------------------------------------------
# Eine Quelle: echte Keys
# ---------------------------------------------------------------------------------------------

def _apply_source(obj, pb, sub, tick_scale, log):
    path = 'pose.bones["%s"]' % pb.name
    n = 0
    for i, k in enumerate(_T_KEYS):
        keys = _keys(sub, k)
        if keys and _nontrivial(keys, 0.0):
            n += _build_bone_fcurve(obj, keys, path + ".location", i, tick_scale)
    for i, k in enumerate(_S_KEYS):
        keys = _keys(sub, k)
        if keys and _nontrivial(keys, 1.0):
            n += _build_bone_fcurve(obj, keys, path + ".scale", i, tick_scale)

    quat_keys = bool(sub.get("quat")) and all(_keys(sub, k) for k in _Q_KEYS)
    mode = pb.rotation_mode
    if mode in EULER_MODES and not quat_keys:
        for i, k in enumerate(_R_KEYS):
            keys = _keys(sub, k)
            if keys and _nontrivial(keys, 0.0):
                n += _build_bone_fcurve(obj, keys, path + ".rotation_euler", i, tick_scale)
    elif mode == 'QUATERNION' and quat_keys:
        if any(_nontrivial(_keys(sub, k), 1.0 if k == "qw" else 0.0) for k in _Q_KEYS):
            for i, k in enumerate(_Q_KEYS):
                n += _build_bone_fcurve(obj, _keys(sub, k), path + ".rotation_quaternion", i, tick_scale)
    else:
        n += _convert_rotation(obj, pb, sub, tick_scale, quat_keys, log)
    return n


def _rotation_at(sub, tick, quat_keys):
    """Die BBS-Rotation an einem Tick als Quaternion (Quaternion-Keys direkt, Euler als XYZ —
    BBS' Ordnung fuer Knochen ohne Euler-Modus)."""
    if quat_keys:
        return Quaternion((_value_at(_keys(sub, "qw"), tick, 1.0), _value_at(_keys(sub, "qx"), tick, 0.0),
                           _value_at(_keys(sub, "qy"), tick, 0.0), _value_at(_keys(sub, "qz"), tick, 0.0))).normalized()
    return Euler((_value_at(_keys(sub, "rx"), tick, 0.0), _value_at(_keys(sub, "ry"), tick, 0.0),
                  _value_at(_keys(sub, "rz"), tick, 0.0)), 'XYZ').to_quaternion()


def _convert_rotation(obj, pb, sub, tick_scale, quat_keys, log):
    """Kreuzfall: Rotation je Key in den Modus des Knochens umrechnen (exakt an den Keys, Bezier-auto
    dazwischen). Quelle: Quaternion-Keys oder Euler-Keys (XYZ)."""
    names = _Q_KEYS if quat_keys else _R_KEYS
    ticks = _ticks(sub, names)
    if not ticks:
        return 0
    trivial = all(abs(_rotation_at(sub, t, quat_keys).angle) < 1e-9 for t in ticks)
    if trivial:
        return 0
    path = 'pose.bones["%s"]' % pb.name
    mode = pb.rotation_mode
    comps = {}
    prev = None
    for t in ticks:
        q = _rotation_at(sub, t, quat_keys)
        interp = _interp_at(sub, t, names)
        if mode in EULER_MODES:
            e = q.to_euler(mode, prev) if prev is not None else q.to_euler(mode)
            prev = e
            vals = ("rotation_euler", (e.x, e.y, e.z))
        elif mode == 'QUATERNION':
            vals = ("rotation_quaternion", (q.w, q.x, q.y, q.z))
        else:
            axis, angle = q.to_axis_angle()
            vals = ("rotation_axis_angle", (angle, axis.x, axis.y, axis.z))
        for i, v in enumerate(vals[1]):
            comps.setdefault((vals[0], i), []).append({"tick": t, "value": float(v), "interp": interp})
    log.add("%s: Rotation je Key umgerechnet (%s-Keys auf %s-Knochen) — an den Keys exakt, "
            "dazwischen Bezier-auto statt BBS-Kurve" % (pb.name, "Quaternion" if quat_keys else "Euler", mode))
    n = 0
    for (prop, i), keys in comps.items():
        n += _build_bone_fcurve(obj, keys, path + "." + prop, i, tick_scale)
    return n


# ---------------------------------------------------------------------------------------------
# Zwei Quellen: dicht je Tick summiert (BBS addiert Pose-Track und Knochen-Track)
# ---------------------------------------------------------------------------------------------

def _apply_sum_dense(obj, pb, srcs, tick_scale, log):
    max_tick = 0.0
    for s in srcs:
        for k in _T_KEYS + _S_KEYS + _R_KEYS + _Q_KEYS:
            for kf in _keys(s, k):
                max_tick = max(max_tick, _kf_float(kf, "tick", 0.0))
    n_ticks = min(int(math.ceil(max_tick)), MAX_TICKS)
    tmp_objs = []

    def _mk(tag):
        o = bpy.data.objects.new("BBSBRIDGE_actorbones_%s" % tag, None)
        tmp_objs.append(o)
        return o

    try:
        evs = []
        for si, s in enumerate(srcs):
            o = _mk("%s_%d" % (pb.name, si))
            oq = _mk("%s_%d_q" % (pb.name, si))
            ev = {}
            for i, k in enumerate(_T_KEYS):
                ev[k] = _sub_evaluator(_keys(s, k), o, ("location", i, 1.0, 0.0), tick_scale)
            for i, k in enumerate(_S_KEYS):
                ev[k] = _sub_evaluator(_keys(s, k), o, ("scale", i, 1.0, 0.0), tick_scale)
            for i, k in enumerate(_R_KEYS):
                ev[k] = _sub_evaluator(_keys(s, k), o, ("rotation_euler", i, 1.0, 0.0), tick_scale)
            for i, k in enumerate(_Q_KEYS):
                ev[k] = _sub_evaluator(_keys(s, k), oq, ("rotation_quaternion", i, 1.0, 0.0), tick_scale)
            ev["quat"] = bool(s.get("quat"))
            evs.append(ev)

        def _v(ev, key, f, default):
            fn = ev.get(key)
            return fn(f) if fn is not None else default

        mode = pb.rotation_mode
        comps = {}
        prev = None
        for t in range(n_ticks + 1):
            f = float(t) * tick_scale
            loc = Vector((0.0, 0.0, 0.0))
            scl = Vector((1.0, 1.0, 1.0))
            eul = Vector((0.0, 0.0, 0.0))
            q = Quaternion((1.0, 0.0, 0.0, 0.0))
            any_quat = False
            for ev in evs:
                loc += Vector((_v(ev, "tx", f, 0.0), _v(ev, "ty", f, 0.0), _v(ev, "tz", f, 0.0)))
                scl += Vector((_v(ev, "sx", f, 1.0) - 1.0, _v(ev, "sy", f, 1.0) - 1.0, _v(ev, "sz", f, 1.0) - 1.0))
                if ev["quat"]:
                    any_quat = True
                    q = q @ Quaternion((_v(ev, "qw", f, 1.0), _v(ev, "qx", f, 0.0), _v(ev, "qy", f, 0.0), _v(ev, "qz", f, 0.0))).normalized()
                else:
                    e = Vector((_v(ev, "rx", f, 0.0), _v(ev, "ry", f, 0.0), _v(ev, "rz", f, 0.0)))
                    eul += e
                    q = q @ Euler(tuple(e), 'XYZ').to_quaternion()
            for i in range(3):
                comps.setdefault(("location", i), []).append({"tick": t, "value": loc[i], "interp": "linear"})
                comps.setdefault(("scale", i), []).append({"tick": t, "value": scl[i], "interp": "linear"})
            if mode in EULER_MODES and not any_quat and mode == 'XYZ':
                # reine Euler-Summe wie BBS (rotate.add), gleiche Ordnung
                for i in range(3):
                    comps.setdefault(("rotation_euler", i), []).append({"tick": t, "value": eul[i], "interp": "linear"})
            elif mode in EULER_MODES:
                e = q.to_euler(mode, prev) if prev is not None else q.to_euler(mode)
                prev = e
                for i, v in enumerate((e.x, e.y, e.z)):
                    comps.setdefault(("rotation_euler", i), []).append({"tick": t, "value": v, "interp": "linear"})
            elif mode == 'QUATERNION':
                for i, v in enumerate((q.w, q.x, q.y, q.z)):
                    comps.setdefault(("rotation_quaternion", i), []).append({"tick": t, "value": v, "interp": "linear"})
            else:
                axis, angle = q.to_axis_angle()
                for i, v in enumerate((angle, axis.x, axis.y, axis.z)):
                    comps.setdefault(("rotation_axis_angle", i), []).append({"tick": t, "value": v, "interp": "linear"})
    finally:
        for o in tmp_objs:
            act = o.animation_data.action if o.animation_data else None
            try:
                bpy.data.objects.remove(o)
            except (ReferenceError, RuntimeError):
                pass
            if act is not None:
                try:
                    act.use_fake_user = False
                    if act.users == 0:
                        bpy.data.actions.remove(act)
                except (ReferenceError, RuntimeError):
                    pass

    log.add("%s: zwei Spuren (pose + pose.bones) dicht je Tick summiert, %d Ticks" % (pb.name, n_ticks + 1))
    path = 'pose.bones["%s"]' % pb.name
    n = 0
    for (prop, i), keys in comps.items():
        ref = 1.0 if (prop == "scale" or (prop == "rotation_quaternion" and i == 0)) else 0.0
        if prop == "rotation_axis_angle" and i > 0:
            ref = None
        if ref is not None and not _nontrivial(keys, ref):
            continue
        n += _build_bone_fcurve(obj, keys, path + "." + prop, i, tick_scale)
    return n
