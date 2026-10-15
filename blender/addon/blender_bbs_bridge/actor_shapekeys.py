"""PHASE F — Form-Transform + ShapeKeys (audit §1/§8) auf der Blender-seite.

Zwei kanäle aus dem ACTORS-payload (`replay["properties"]`, java `FormTransformExporter`):

- `shapeKeys`  = {morph-name: [kf...]} — 1:1 auf Blender-shape-keys. Pro morph-name den
  `obj.data.shape_keys.key_blocks["<name>"].value` keyframen (data_path
  `key_blocks["<name>"].value` auf shape_keys.animation_data). Der DIREKTESTE mapping überhaupt.
- `formTransform` (+ `formTransformOverlay`) = 15 skalar-subkurven (tx/ty/tz, sx/sy/sz, rx/ry/rz,
  r2x/r2y/r2z, px/py/pz) einer BBS-`Transform`. Das ist eine ZUSÄTZLICHE transform-ebene NEBEN der
  objekt-position (die die transform-kanäle x/y/z/yaw/pitch schon syncen). Wir legen sie auf ein
  ZWISCHEN-EMPTY `BBS_FormXform_<uuid8>`, das zwischen dem actor-root und seine mesh-/armature-
  kinder gehängt wird — so überschreiben wir NICHT die objekt-position, sondern komponieren die
  form-transform darunter (objekt-matrix · form-transform · mesh), genau wie BBS.

FALLEN (aus dem paket-dossier, nicht geraten):
- shape-keys existieren nur wenn das MESH sie trägt (`obj.data.shape_keys`). Fehlt ein key →
  tolerant skippen + loggen (dokumentierte grenze, kein throw — Regel 0: kein stiller falsch-sync).
- node-/shape-key-fcurves adressieren IMMER relativ zum ID-block (shape_keys), nicht zum key_block:
  data_path = `key_blocks["<name>"].value`. animation_data liegt auf shape_keys, nicht auf mesh/obj.
- KEIN `animation_data_clear()` auf mesh/obj — der actor-root teilt seine action mit den objekt-
  level x/y/z-fcurves (actors.py) und pose-bone-keys (model_pose/replay_anim). Chirurgisch räumen.
- form-transform-empty ist EIGENER zustand (prefix `BBS_FormXform_`, invariante 5) — idempotent
  angelegt/wiederverwendet, rückstandslos beim orphan-cleanup (hängt am root, stirbt mit ihm).
- rotation: BBS liefert RADIAN roh. BBS komponiert `rotateZ(r.z)·rotateY(r.y)·rotateX(r.x)` gefolgt
  von rotate2 in derselben ZYX-ordnung (Transform.setupMatrix). Blenders `rotation_euler` mit
  `rotation_mode='XYZ'` wendet in der reihenfolge X,Y,Z an (matrix = Rz·Ry·Rx) = dieselbe matrix
  wie BBS' rotate-block. rotate2 ist eine ZWEITE ZYX-rotation danach → eigenes verschachteltes
  empty `BBS_FormXform2_<uuid8>` (die kette rotate·rotate2 wird durch parenting komponiert).
- coordinate-contract: BBS-form-transform ist im FORM-lokalraum (das mesh ist schon in Blender-
  raum importiert). Wir wenden translate/scale/rotate im OBJEKT-lokalraum des zwischen-empties an;
  achsen-swap (bbs_y=bl_z, bbs_z=-bl_y) gilt für WELT-positionen, nicht für die lokale
  form-skalierung/rotation eines schon-konvertierten mesh — v1 wendet sie 1:1 im lokalraum an
  (scale ist achsen-agnostisch bei uniform; rotation im lokalraum dokumentierte v1-grenze bei
  stark asymmetrischer form-rotation, siehe render-beweis-note).
"""
import math

import bpy

try:
    from bpy_extras import anim_utils
except ImportError:
    anim_utils = None

_FORM_XFORM_PREFIX = "BBS_FormXform_"     # rotate + translate + scale
_FORM_XFORM2_PREFIX = "BBS_FormXform2_"   # rotate2 (zweite ZYX-rotation, verschachtelt)
_SHAPE_TIMELINE_PROP = "bbs_shape_timeline"


def has_form_channels(replay):
    """Trägt der replay einen nicht-leeren form-transform- ODER shape-keys-kanal?"""
    props = replay.get("properties") if isinstance(replay, dict) else None
    if not isinstance(props, dict):
        return False
    for key in ("formTransform", "formTransformOverlay"):
        ft = props.get(key)
        if isinstance(ft, dict) and any(ft.get(c) for c in _TRANSFORM_SUBCURVES):
            return True
    sk = props.get("shapeKeys")
    if isinstance(sk, dict) and any(sk.get(name) for name in sk):
        return True
    return False


def apply_form_channels(obj, replay, tick_scale, shape_names=None, apply_transform=True,
                        apply_shape_timeline=True):
    """Apply form transform and shape-key curves.

    Full import leaves shape_names unset. Live sync passes the exact changed
    morph names so a full actor snapshot cannot touch authored sibling morphs.
    The shared ShapeKey timing/interpolation metadata has its own Key-ID FCurve.
    """
    if not isinstance(replay, dict):
        return 0
    props = replay.get("properties")
    if not isinstance(props, dict):
        return 0

    n = 0
    try:
        n += _apply_shape_keys(
            obj, props.get("shapeKeys"), tick_scale, shape_names, apply_shape_timeline)
    except Exception as e:   # noqa: BLE001 — timer-regel: nie den import-loop killen
        print("[bbs-bridge] shape-keys fuer actor uebersprungen: %s" % e)
    if apply_transform:
        try:
            n += _apply_form_transform(obj, props, replay, tick_scale)
        except Exception as e:   # noqa: BLE001
            print("[bbs-bridge] form-transform fuer actor uebersprungen: %s" % e)
    return n


# --------------------------------------------------------------------------- shape keys

def shape_path(name):
    return 'key_blocks["%s"].value' % bpy.utils.escape_identifier(name)


def shape_timeline_path():
    return '["%s"]' % _SHAPE_TIMELINE_PROP


def _shape_channel_bag(key_data):
    if key_data is None or anim_utils is None:
        return None
    ad = key_data.animation_data
    if ad is None or ad.action is None:
        return None
    try:
        return anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot)
    except (AttributeError, TypeError, RuntimeError):
        return None


def find_shape_curve(obj, name):
    mesh = _mesh_with_shape_keys(obj)
    if mesh is None:
        return None
    bag = _shape_channel_bag(mesh.data.shape_keys)
    if bag is None:
        return None
    path = shape_path(name)
    return next((fc for fc in bag.fcurves
                 if fc.data_path == path and fc.array_index == 0), None)


def find_shape_timeline_curve(obj):
    mesh = _mesh_with_shape_keys(obj)
    if mesh is None:
        return None
    bag = _shape_channel_bag(mesh.data.shape_keys)
    if bag is None:
        return None
    path = shape_timeline_path()
    return next((fc for fc in bag.fcurves
                 if fc.data_path == path and fc.array_index == 0), None)


def shape_timeline(shape_keys):
    """Return BBS' single shared ShapeKey timing/interpolation metadata."""
    shapes = shape_keys if isinstance(shape_keys, dict) else {}
    timeline = None
    for name in sorted(shapes):
        curve = shapes.get(name)
        if not isinstance(curve, (list, tuple)) or not curve:
            continue
        current = []
        for raw in curve:
            if not isinstance(raw, dict):
                raise RuntimeError('ShapeKey timeline contains a non-object key')
            current.append({
                'tick': float(raw.get('tick', 0.0)),
                'interp': str(raw.get('interp') or 'linear').lower(),
                'lx': float(raw.get('lx', 5.0)),
                'ly': float(raw.get('ly', 0.0)),
                'rx': float(raw.get('rx', 5.0)),
                'ry': float(raw.get('ry', 0.0)),
            })
        if timeline is None:
            timeline = current
        elif current != timeline:
            raise RuntimeError('ShapeKey morphs disagree on the shared BBS timeline')
    return timeline or []


def _remove_shape_path_curve(key_data, path):
    bag = _shape_channel_bag(key_data)
    if bag is None:
        return False
    curve = next((fc for fc in bag.fcurves
                  if fc.data_path == path and fc.array_index == 0), None)
    if curve is None:
        return False
    bag.fcurves.remove(curve)
    return True


def _remove_shape_curve(key_data, name):
    return _remove_shape_path_curve(key_data, shape_path(name))


def _remove_shape_timeline_curve(key_data):
    return _remove_shape_path_curve(key_data, shape_timeline_path())


def _apply_shape_timeline(key_data, timeline, tick_scale):
    """Store BBS' shared ShapeKey grid as metadata FCurve on the Key datablock."""
    _remove_shape_timeline_curve(key_data)
    if not timeline:
        key_data.pop(_SHAPE_TIMELINE_PROP, None)
        return 0

    key_data[_SHAPE_TIMELINE_PROP] = 0.0
    try:
        key_data.id_properties_ui(_SHAPE_TIMELINE_PROP).update(
            description='BBS shared ShapeKey timing/interpolation metadata')
    except (AttributeError, TypeError, RuntimeError):
        pass

    path = shape_timeline_path()
    written = 0
    for raw in timeline:
        frame = float(raw['tick']) * tick_scale
        key_data[_SHAPE_TIMELINE_PROP] = 0.0
        try:
            if key_data.keyframe_insert(data_path=path, frame=frame):
                written += 1
        except (RuntimeError, TypeError):
            pass

    bag = _shape_channel_bag(key_data)
    curve = None if bag is None else next(
        (fc for fc in bag.fcurves if fc.data_path == path and fc.array_index == 0), None)
    if curve is None:
        return written

    from . import actors
    by_frame = {round(float(raw['tick']) * tick_scale, 4): raw for raw in timeline}
    for point in curve.keyframe_points:
        frame = float(point.co.x)
        raw = by_frame.get(round(frame, 4))
        if raw is not None:
            actors._apply_kf_shape(point, raw, frame, 0.0, 1.0, tick_scale)
    curve.update()
    return written


def apply_shape_timeline_interpolation(
        obj, timeline, tick_scale, names, sent_signatures=None, full=False):
    """Materialize shared ticks/handles/interpolation without overwriting later morph edits."""
    from . import actor_channel_sync as sync

    applied = 0
    sent_signatures = sent_signatures or {}
    for name in sorted(set(names or ())):
        curve = find_shape_curve(obj, name)
        sent = sent_signatures.get(name)
        if curve is None or sent is None or len(curve.keyframe_points) != len(timeline):
            continue
        points = list(curve.keyframe_points)
        sent_keys = sent.get('keys') or []
        if len(sent_keys) != len(points):
            continue
        if any(
                abs(float(point.co.x) - float(saved[0][0])) > 1e-5
                or abs((float(point.handle_left.x) - float(point.co.x))
                       - (float(saved[1][0]) - float(saved[0][0]))) > 1e-5
                or abs((float(point.handle_left.y) - float(point.co.y))
                       - (float(saved[1][1]) - float(saved[0][1]))) > 1e-6
                or abs((float(point.handle_right.x) - float(point.co.x))
                       - (float(saved[2][0]) - float(saved[0][0]))) > 1e-5
                or abs((float(point.handle_right.y) - float(point.co.y))
                       - (float(saved[2][1]) - float(saved[0][1]))) > 1e-6
                or point.interpolation != saved[3]
                or point.handle_left_type != saved[5]
                or point.handle_right_type != saved[6]
                for point, saved in zip(points, sent_keys)):
            continue
        for point, raw in zip(points, timeline):
            value = float(point.co.y)
            frame = float(raw['tick']) * tick_scale
            interp = str(raw.get('interp') or 'linear').lower()
            mode, easing = sync.shape_blender_interpolation(interp)
            if full:
                point.co.x = frame
            point.interpolation = mode
            point.easing = easing
            if full:
                point.handle_left_type = point.handle_right_type = 'FREE'
                point.handle_left = (frame - float(raw.get('lx', 5.0)) * tick_scale,
                                     value + float(raw.get('ly', 0.0)))
                point.handle_right = (frame + float(raw.get('rx', 5.0)) * tick_scale,
                                      value + float(raw.get('ry', 0.0)))
        curve.update()
        applied += 1
    return applied


def _apply_shape_keys(obj, shape_keys, tick_scale, shape_names=None, apply_timeline=True):
    """Replace named morph curves and, when requested, the shared BBS timeline metadata."""
    shape_keys = shape_keys if isinstance(shape_keys, dict) else {}
    names = set(shape_keys) if shape_names is None else set(shape_names)
    mesh = _mesh_with_shape_keys(obj)
    if mesh is None:
        if names:
            print("[bbs-bridge] shape-keys: actor %r hat kein mesh mit shape-keys (payload traegt "
                  "%d morph(s) %r) -> uebersprungen (mesh muss die morphs tragen)"
                  % (_name(obj), len(shape_keys), sorted(names)[:6]))
        return 0

    key_data = mesh.data.shape_keys
    key_blocks = key_data.key_blocks
    n = _apply_shape_timeline(key_data, shape_timeline(shape_keys), tick_scale) if apply_timeline else 0
    if not names:
        return n
    missing = []
    for name in sorted(names):
        block = key_blocks.get(name)
        if block is None:
            missing.append(name)
            continue
        _remove_shape_curve(key_data, name)
        keyframes = shape_keys.get(name)
        if isinstance(keyframes, (list, tuple)) and keyframes:
            n += _key_shape_block(key_data, block, keyframes, tick_scale)
        else:
            block.value = 0.0
    if missing:
        print("[bbs-bridge] shape-keys: morph(s) %r fehlen im mesh von actor %r -> uebersprungen "
              "(dokumentierte grenze, mesh traegt sie nicht)" % (sorted(missing)[:6], _name(obj)))
    return n


def _key_shape_block(key_data, block, keyframes, tick_scale):
    """Eine keyframe-liste auf `block.value` legen (data_path key_blocks["name"].value, skalar).
    Setzt interp/handles wie die material-/transform-fcurves. Gibt zahl geschriebener keys."""
    if not keyframes or not isinstance(keyframes, (list, tuple)):
        return 0
    data_path = 'key_blocks["%s"].value' % bpy.utils.escape_identifier(block.name)
    written = 0
    for kf in keyframes:
        if not isinstance(kf, dict):
            continue
        try:
            tick = float(kf.get("tick", 0.0))
            value = float(kf.get("value", 0.0))
        except (TypeError, ValueError):
            continue
        frame = tick * tick_scale
        try:
            block.value = value
            # animation_data liegt auf shape_keys (dem ID-block), keyframe_insert auf block.value
            key_data.keyframe_insert(data_path, frame=frame)
            written += 1
        except (RuntimeError, TypeError):
            pass
    _apply_interp_id(key_data, data_path, 0, keyframes, tick_scale)
    return written


def _mesh_with_shape_keys(obj):
    """Das mesh-objekt mit shape-keys finden: der root selbst wenn er ein mesh mit key ist, sonst
    ein direktes/enkel mesh-kind (BOBJ: mesh haengt unter der armature). None wenn keins."""
    if obj is None:
        return None
    if _obj_has_keys(obj):
        return obj
    # kinder + enkel durchsuchen (armature-root -> mesh-kind)
    for child in _iter_descendants(obj):
        if _obj_has_keys(child):
            return child
    return None


def _obj_has_keys(obj):
    try:
        return (obj is not None and obj.type == 'MESH'
                and obj.data is not None and obj.data.shape_keys is not None
                and len(obj.data.shape_keys.key_blocks) > 0)
    except (AttributeError, ReferenceError):
        return False


def _iter_descendants(obj):
    for child in getattr(obj, "children", ()) or ():
        yield child
        yield from _iter_descendants(child)


# --------------------------------------------------------------------------- form transform

_TRANSFORM_SUBCURVES = ("tx", "ty", "tz", "sx", "sy", "sz",
                        "rx", "ry", "rz", "r2x", "r2y", "r2z",
                        "px", "py", "pz")


def _apply_form_transform(obj, props, replay, tick_scale):
    """Die form-transform-subkurven auf ein zwischen-empty legen. Das empty haengt zwischen dem
    actor-root (obj) und dessen bisherigen mesh-/armature-kindern (objekt-matrix · form-transform ·
    mesh). rotate landet auf dem empty selbst, rotate2 auf einem zweiten verschachtelten empty,
    translate/scale auf dem ersten. Byte-identisch fuer actors ohne form-transform (kein empty)."""
    ft = _combined_form_transform(props)
    if ft is None:
        return 0
    if obj.type == 'ARMATURE':
        return _apply_form_transform_armature(obj, ft, tick_scale)

    uuid8 = _uuid8(replay)
    empty1, empty2 = _ensure_form_empties(obj, uuid8)
    if empty1 is None:
        return 0

    n = 0
    # translate -> empty1.location. BBS-formraum ist Y-oben, das empty liegt im Z-oben-raum des
    # actor-roots: (tx, ty, tz) -> (tx, -tz, ty). Gemessen 2026-10-14 an Moggityfirst: die box
    # (overlay t=-5.81/0/-4.49, bodyYaw -2700.82) steht in BBS bei -878.27/-375.12; 1:1 lag sie
    # 4.5 bloecke unter dem boden.
    n += _key_object_vec(empty1, "location", (ft.get("tx"), _negated(ft.get("tz")), ft.get("ty")), tick_scale)
    # scale -> empty1.scale (gleiche achsen-vertauschung)
    n += _key_object_vec(empty1, "scale", (ft.get("sx"), ft.get("sz"), ft.get("sy")), tick_scale)
    # rotate (erste ZYX) -> empty1.rotation_euler (XYZ-mode = Rz·Ry·Rx = BBS rotate-block)
    n += _key_object_vec(empty1, "rotation_euler", (ft.get("rx"), ft.get("ry"), ft.get("rz")), tick_scale)
    # rotate2 (zweite ZYX) -> empty2.rotation_euler (verschachtelt = komponiert rotate·rotate2)
    if empty2 is not None:
        n += _key_object_vec(empty2, "rotation_euler", (ft.get("r2x"), ft.get("r2y"), ft.get("r2z")), tick_scale)
    return n


_FORM_T_PROP = "bbs_form_translate"


def _apply_form_transform_armature(obj, ft, tick_scale):
    """Form-Transform eines GERIGGTEN actors. BBS rechnet entity · form · knochen; ein empty
    zwischen armatur und mesh laege dagegen UNTER der knochen-verformung (armature-modifier) und
    wuerde mit der pose mitgedreht (gemessen: box 7.3 bloecke daneben). Darum auf die armatur
    selbst, ueber Blenders delta-transforms: delta_location = R(yaw) · S · t per treiber,
    damit location/rotation_euler (die BBS-kanaele x/y/z/yaw) unangetastet bleiben."""
    n = 0
    obj[_FORM_T_PROP] = [0.0, 0.0, 0.0]
    for coll in pose_overlays_collections(obj):
        while True:
            victim = next((fc for fc in coll if fc.data_path in (
                '["%s"]' % _FORM_T_PROP, "delta_scale", "delta_rotation_euler")), None)
            if victim is None:
                break
            coll.remove(victim)
    for i, keys in enumerate((ft.get("tx"), _negated(ft.get("tz")), ft.get("ty"))):
        for kf in keys or ():
            obj[_FORM_T_PROP][i] = float(kf.get("value", 0.0))
            obj.keyframe_insert('["%s"]' % _FORM_T_PROP, index=i,
                                frame=float(kf.get("tick", 0.0)) * tick_scale)
            n += 1
    n += _key_object_vec(obj, "delta_scale", (ft.get("sx"), ft.get("sz"), ft.get("sy")), tick_scale)
    n += _key_object_vec(obj, "delta_rotation_euler", (ft.get("rx"), ft.get("ry"), ft.get("rz")), tick_scale)
    exprs = ("(sx*tx)*cos(rz) - (sy*ty)*sin(rz)", "(sx*tx)*sin(rz) + (sy*ty)*cos(rz)", "sz*tz")
    for i, expr in enumerate(exprs):
        obj.driver_remove("delta_location", i)
        drv = obj.driver_add("delta_location", i).driver
        drv.type = 'SCRIPTED'
        drv.expression = expr
        for name, path in (("rz", "rotation_euler[2]"), ("sx", "scale[0]"), ("sy", "scale[1]"),
                           ("sz", "scale[2]"), ("tx", '["%s"][0]' % _FORM_T_PROP),
                           ("ty", '["%s"][1]' % _FORM_T_PROP), ("tz", '["%s"][2]' % _FORM_T_PROP)):
            var = drv.variables.new()
            var.name = name
            var.targets[0].id = obj
            var.targets[0].data_path = path
    return n


def pose_overlays_collections(obj):
    from . import pose_overlays
    return pose_overlays.collections(obj)


def _combined_form_transform(props):
    """formTransform + formTransformOverlay so zusammensetzen wie BBS (FormRenderer.applyTransform):
    translate und rotate addieren, scale = a + b - 1. Haben beide kanaele keys, wird auf der
    vereinigung der ticks linear ausgewertet (die kurvenform zwischen den keys geht verloren)."""
    def valid(d):
        return isinstance(d, dict) and any(d.get(c) for c in _TRANSFORM_SUBCURVES)
    base, over = props.get("formTransform"), props.get("formTransformOverlay")
    if not valid(over):
        return base if valid(base) else None
    if not valid(base):
        return over
    out = {}
    for c in _TRANSFORM_SUBCURVES:
        a, b = base.get(c) or [], over.get(c) or []
        if not a or not b:
            out[c] = a or b
            continue
        neutral = 1.0 if c in ("sx", "sy", "sz") else 0.0
        ticks = sorted({float(k["tick"]) for k in a + b})
        out[c] = [{"tick": t, "value": _eval_linear(a, t) + _eval_linear(b, t) - neutral,
                   "interp": "linear"} for t in ticks]
    return out


def _eval_linear(keys, tick):
    pts = sorted((float(k["tick"]), float(k["value"])) for k in keys)
    if tick <= pts[0][0]:
        return pts[0][1]
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        if tick <= t1:
            return v0 + (v1 - v0) * (tick - t0) / ((t1 - t0) or 1.0)
    return pts[-1][1]


def _negated(keys):
    if not keys:
        return keys
    return [dict(k, value=-float(k.get("value", 0.0)), ly=-float(k.get("ly", 0.0)),
                 ry=-float(k.get("ry", 0.0))) for k in keys]


def _ensure_form_empties(obj, uuid8):
    """Das/die zwischen-empty(s) anlegen (idempotent) und die bisherigen mesh-/armature-kinder von
    obj darunter umhaengen. Gibt (empty1, empty2). empty2 traegt rotate2, empty1 den rest; die
    mesh-kinder haengen unter empty2 (bzw. empty1 wenn kein empty2)."""
    coll = _first_collection(obj)
    if coll is None:
        return None, None
    name1 = _FORM_XFORM_PREFIX + uuid8
    name2 = _FORM_XFORM2_PREFIX + uuid8

    empty1 = bpy.data.objects.get(name1)
    if empty1 is None:
        empty1 = bpy.data.objects.new(name1, None)
        empty1.empty_display_type = 'PLAIN_AXES'
        empty1.empty_display_size = 0.25
        coll.objects.link(empty1)
        empty1.parent = obj
    empty1.rotation_mode = 'XYZ'

    empty2 = bpy.data.objects.get(name2)
    if empty2 is None:
        empty2 = bpy.data.objects.new(name2, None)
        empty2.empty_display_type = 'PLAIN_AXES'
        empty2.empty_display_size = 0.25
        coll.objects.link(empty2)
        empty2.parent = empty1
    empty2.rotation_mode = 'XYZ'

    # bisherige echte kinder von obj (mesh/armature, NICHT unsere eigenen empties) unter empty2
    # umhaengen — matrix_world erhalten, damit sie optisch an ort bleiben bevor die form-transform
    # animiert. Idempotent: schon-umgehaengte werden nicht angefasst.
    for child in list(getattr(obj, "children", ()) or ()):
        if child is empty1 or child.name.startswith(_FORM_XFORM_PREFIX) \
                or child.name.startswith(_FORM_XFORM2_PREFIX):
            continue
        _reparent_keep_world(child, empty2)
    return empty1, empty2


def _reparent_keep_world(child, new_parent):
    """child unter new_parent haengen, ohne dass es optisch springt (matrix_world erhalten)."""
    try:
        mw = child.matrix_world.copy()
        child.parent = new_parent
        # frisch angelegte empties tragen sonst eine veraltete matrix_world (einheitsmatrix):
        # die kinder landeten mit ihrer weltlage als LOKALER lage doppelt verschoben
        # (gemessen 2026-10-14: box bei -1269/1270 statt -878/375).
        bpy.context.view_layer.update()
        child.matrix_world = mw
    except (AttributeError, ReferenceError, RuntimeError):
        pass


def _key_object_vec(obj, data_path, subcurves, tick_scale):
    """Drei subkurven (x,y,z) auf ein 3-float-property des OBJEKTS legen (location/scale/
    rotation_euler). Gibt zahl geschriebener keys. Setzt interp/handles wie die material-fcurves."""
    n = 0
    for idx, keyframes in enumerate(subcurves):
        if not keyframes or not isinstance(keyframes, (list, tuple)):
            continue
        for kf in keyframes:
            if not isinstance(kf, dict):
                continue
            try:
                tick = float(kf.get("tick", 0.0))
                value = float(kf.get("value", 0.0))
            except (TypeError, ValueError):
                continue
            frame = tick * tick_scale
            try:
                vec = getattr(obj, data_path)
                vec[idx] = value
                obj.keyframe_insert(data_path, frame=frame, index=idx)
                n += 1
            except (RuntimeError, TypeError, AttributeError, IndexError):
                pass
        _apply_interp_id(obj, data_path, idx, keyframes, tick_scale)
    return n


# --------------------------------------------------------------------------- shared helpers

def _apply_interp_id(id_block, data_path, array_index, keyframes, tick_scale):
    """Interp/handles auf die eben eingefuegten keys eines ID-blocks (obj ODER shape_keys) setzen.
    5.2-weg: animation_data.action -> channelbag -> fcurve mit passendem data_path+array_index.
    Wirft nie."""
    if anim_utils is None:
        return
    ad = getattr(id_block, "animation_data", None)
    if ad is None or ad.action is None:
        return
    try:
        cbag = anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot)
    except (AttributeError, TypeError, RuntimeError):
        return
    if cbag is None:
        return
    by_frame = {}
    for kf in keyframes:
        if isinstance(kf, dict):
            try:
                by_frame[round(float(kf["tick"]) * tick_scale, 4)] = kf
            except (TypeError, ValueError, KeyError):
                pass
    for fc in cbag.fcurves:
        # scopen auf den EIGENEN data_path UND array_index (mehrere subkurven teilen die action;
        # location/scale/rotation_euler haben je 3 kurven, shape-keys teilen sich key_blocks[...])
        if fc.data_path != data_path or fc.array_index != array_index:
            continue
        from . import actors
        for kp in fc.keyframe_points:
            src = by_frame.get(round(kp.co.x, 4))
            if not src:
                continue
            actors._apply_kf_shape(
                kp, src, float(kp.co.x), float(kp.co.y), 1.0, tick_scale)
        try:
            fc.update()
        except RuntimeError:
            pass


def _uuid8(replay):
    u = str(replay.get("uuid") or replay.get("id") or "actor")
    return u.replace("-", "")[:8] or "actor"


def _first_collection(obj):
    try:
        cols = obj.users_collection
        return cols[0] if cols else None
    except (AttributeError, ReferenceError, IndexError):
        return None


def _name(obj):
    try:
        return obj.get("bbs_uuid") or obj.name
    except (AttributeError, ReferenceError):
        return "?"
