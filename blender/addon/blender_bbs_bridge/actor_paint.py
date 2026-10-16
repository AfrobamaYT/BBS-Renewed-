"""PHASE P — Paint: BBS paint-kanal (replay.properties.paint) als Blender oberflaechen-overlay.

Der BBS-`paint`-kanal (whole-actor, PaintSettings floats r/g/b default 1, intensity UNBOUNDED/
negativ=abdunkeln, shaderShadow flag default 1) kommt im ACTORS-payload unter `properties.paint`
als FUENF subkurven (r,g,b,intensity,shaderShadow — java PaintExporter zerlegt den PaintSettings-
value, weil Blender skalare F-curves animiert).

Dieses modul baut EINEN zweiten Mix-layer `BBS_Paint` (overlay-tint/darken) IN DIE base-color-kette
NACH dem color-tint (kette: textur -> BBS_Tint -> BBS_Paint -> BSDF.Base Color) und keyframet seine
sockets. Es fasst `actor_material.py` NICHT an — es teilt sich die per-actor material-KOPIE
(`actor_material.ensure_owned_material`, idempotent) mit color/glow, KEINE zweite kopie.

WIE PAINT GERENDERT WIRD (masterplan Phase P, PaintSettings.java):
- intensity >= 0 -> OVERLAY-tint: die paint-farbe wird ueber die base-color gemischt, factor =
  min(intensity, 1). intensity 0 = kein effekt (factor 0 = Result == A == color-kette).
- intensity < 0  -> DARKEN: MULTIPLY der paint-farbe auf die base-color, factor = min(|intensity|,1)
  (BBS dunkelt mit negativer intensity ab). blend_type MULTIPLY, farbe wirkt als multiplikator.
- Da ein Mix-node EINEN blend_type hat und intensity ueber die zeit das VORZEICHEN wechseln kann,
  waehlen wir den blend_type per-actor aus dem intensity-VERLAUF (ueberwiegend negativ -> MULTIPLY,
  sonst MIX/overlay) und keyframen den Factor aus |intensity| (0..1 geclampt). Vorzeichen-wechsel
  INNERHALB eines actors ist eine dokumentierte grenze (ein node = ein blend-modus) — geloggt.
- shaderShadow: KEIN sauberes Blender-AO-mapping (masterplan: SCHWER, dokumentierte grenze). Der
  wert reist korrekt bis hier und wird als custom-prop `bbs_shader_shadow` am material abgelegt +
  1x geloggt (Regel 0: ehrlich flaggen, kein falscher AO-sync). Blender-AO-mapping ist Phase-S.

FALLEN (aus actor_material.py-v1 uebernommen, nicht geraten):
- WIRE-IDEMPOTENZ: base.copy() (per-actor-material) nimmt die node MIT, verliert aber die links.
  Ausserdem re-assertet actor_material._ensure_nodes bei JEDEM poll `BBS_Tint.Result -> Base Color`
  direkt — wir muessen diesen direkt-link ENTFERNEN und durch tint->paint->BaseColor ersetzen,
  bei JEDEM aufruf (idempotent), sonst haengt paint im leeren.
- ShaderNodeMix (RGBA): index-zugriff pflicht (inputs[0]=Factor, inputs[6]=A, inputs[7]=B,
  outputs[2]=Result) — dieselben duplikat-socket-namen wie BBS_Tint.
- _apply_interp MUSS auf socket.path_from_id()+".default_value" scopen: paint-r (BBS_Paint.inputs[7]
  array_index 0) und tint-r / alpha teilen array_index 0 auf DEMSELBEN node_tree/channelbag ->
  reiner array_index-filter kontaminiert die fremde kurve (v1-gemessene falle).
"""
import bpy

try:
    from bpy_extras import anim_utils
except ImportError:
    anim_utils = None

from . import actor_material

# node-name (stabil, zum wiederfinden) — eigener namespace, kollidiert nicht mit actor_material
_PAINT_NODE = "BBS_Paint"
# custom-prop am material fuer die dokumentierte-grenze shaderShadow (kein render, nur transport)
_SHADER_SHADOW_PROP = "bbs_shader_shadow"


def has_paint_channels(replay):
    """Traegt der replay einen nicht-leeren paint-kanal? (r/g/b/intensity/shaderShadow)."""
    props = replay.get("properties") if isinstance(replay, dict) else None
    if not isinstance(props, dict):
        return False
    paint = props.get("paint")
    if isinstance(paint, dict) and any(
        paint.get(c) for c in ("r", "g", "b", "intensity", "shaderShadow")
    ):
        return True
    return False


def _dominant_blend_type(intensity_kfs):
    """Aus dem intensity-VERLAUF den Mix-blend-modus waehlen: ueberwiegen negative werte, ist es
    ein DARKEN (MULTIPLY), sonst ein overlay-tint (MIX). Gibt (blend_type, mixed_sign) zurueck —
    mixed_sign=True wenn beide vorzeichen vorkommen (dokumentierte grenze, ein node=ein modus)."""
    if not intensity_kfs or not isinstance(intensity_kfs, (list, tuple)):
        return 'MIX', False
    neg = 0
    pos = 0
    for kf in intensity_kfs:
        if not isinstance(kf, dict):
            continue
        try:
            v = float(kf.get("value", 0.0))
        except (TypeError, ValueError):
            continue
        if v < 0.0:
            neg += 1
        elif v > 0.0:
            pos += 1
    mixed = neg > 0 and pos > 0
    return ('MULTIPLY' if neg > pos else 'MIX'), mixed


def _ensure_paint_nodes(mat, blend_type):
    """Den BBS_Paint-Mix EINMAL in die base-color-kette NACH BBS_Tint einbauen + die links IMMER
    sicherstellen (WIRE-IDEMPOTENT). Kette: textur -> BBS_Tint -> BBS_Paint -> BSDF.Base Color.

    ⚠️ Das gate darf NUR den node-NEUBAU ueberspringen, NICHT die verdrahtung — actor_material
    re-assertet bei jedem poll `BBS_Tint.Result -> Base Color` DIREKT, wir muessen diesen
    direkt-link jedes mal aufloesen und durch tint->paint->BaseColor ersetzen (sonst haengt paint
    im leeren = render zeigt kein paint trotz gesetzter farbe). Gibt den paint-node oder None."""
    if not getattr(mat, "use_nodes", False):
        return None
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tint = nt.nodes.get(actor_material._TINT_NODE)
    if bsdf is None or tint is None:
        # ohne die color-tint-kette (die actor_material.ensure_owned_material baut) gibt es keinen
        # A-eingang fuer paint — kein no-op-schaden, aber nichts zu tun.
        return None
    base_color = bsdf.inputs["Base Color"]
    tint_result = tint.outputs[2]
    paint = nt.nodes.get(_PAINT_NODE)
    if paint is None:
        paint = nt.nodes.new("ShaderNodeMix")
        paint.name = _PAINT_NODE
        paint.data_type = 'RGBA'
        paint.inputs[0].default_value = 0.0                       # Factor start 0 = Result == A (kein paint)
        paint.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)      # B = paint-farbe (start weiss = no-op)
    # blend_type per-actor aus dem intensity-verlauf (overlay MIX vs darken MULTIPLY)
    try:
        paint.blend_type = blend_type
    except (TypeError, ValueError):
        pass
    # A (inputs[6]) = base-color-quelle = BBS_Tint.Result: IMMER sicherstellen
    if not any(l.to_socket == paint.inputs[6] and l.from_socket == tint_result for l in nt.links):
        nt.links.new(tint_result, paint.inputs[6])
    # den DIREKTEN tint->BaseColor-link aufloesen (actor_material legt ihn bei jedem poll neu) —
    # paint sitzt jetzt dazwischen. Nur den tint->BaseColor entfernen, nicht paint->BaseColor.
    for l in list(nt.links):
        if l.to_socket == base_color and l.from_socket == tint_result:
            nt.links.remove(l)
    # BBS_Paint.Result -> BSDF.Base Color: IMMER sicherstellen (der eigentliche fix)
    if not any(l.to_socket == base_color and l.from_socket == paint.outputs[2] for l in nt.links):
        nt.links.new(paint.outputs[2], base_color)
    return paint


def _store_shader_shadow(mat, shader_shadow_kfs, obj):
    """shaderShadow als custom-prop am material ablegen (letzter wert) + 1x flaggen. KEIN render-
    mapping (masterplan: SCHWER, Blender-AO ist Phase-S) — ehrlich transportieren statt falsch
    mappen (Regel 0). Wirft nie."""
    if not shader_shadow_kfs or not isinstance(shader_shadow_kfs, (list, tuple)):
        return
    last = None
    for kf in shader_shadow_kfs:
        if isinstance(kf, dict):
            try:
                last = float(kf.get("value", last if last is not None else 1.0))
            except (TypeError, ValueError):
                pass
    if last is None:
        return
    try:
        mat[_SHADER_SHADOW_PROP] = last
    except (TypeError, AttributeError):
        return
    try:
        name = obj.get("bbs_uuid") or obj.name
    except (AttributeError, ReferenceError):
        name = "?"
    print("[bbs-bridge] paint: shaderShadow=%.4f fuer actor %r als custom-prop %r transportiert "
          "(Blender-AO-mapping ist dokumentierte grenze / Phase-S, kein render-effekt v1)"
          % (last, name, _SHADER_SHADOW_PROP))


def apply_paint_channels(obj, replay, tick_scale):
    """Die paint-subkurven auf den BBS_Paint-Mix legen (Phase P):
      - rgb       -> BBS_Paint.inputs[7] (B-socket, paint-farbe), je komponente ein array_index
      - intensity -> BBS_Paint.inputs[0] (Factor, |intensity| auf 0..1 geclampt)
      - shaderShadow -> custom-prop bbs_shader_shadow am material (dokumentierte grenze, kein render)
    Paint teilt die PER-ACTOR material-kopie mit color/glow (KEINE zweite kopie —
    ensure_owned_material ist idempotent). Gibt die zahl geschriebener keys. Wirft nie (ein
    kaputter kanal darf den import nicht killen)."""
    if not has_paint_channels(replay):
        return 0
    paint = replay.get("properties", {}).get("paint")
    if not isinstance(paint, dict):
        return 0
    # dieselbe idempotente per-actor material-kopie wie color/glow (baut auch die tint-kette).
    mat = actor_material.ensure_owned_material(obj, actor_material._uuid8(replay))
    if mat is None:
        return 0
    blend_type, mixed = _dominant_blend_type(paint.get("intensity"))
    if mixed:
        try:
            name = obj.get("bbs_uuid") or obj.name
        except (AttributeError, ReferenceError):
            name = "?"
        print("[bbs-bridge] paint: intensity fuer actor %r wechselt das vorzeichen (overlay<->darken) "
              "— ein Mix-node hat EINEN blend-modus, gewaehlt: %s (dokumentierte grenze)"
              % (name, blend_type))
    paint_node = _ensure_paint_nodes(mat, blend_type)
    if paint_node is None:
        return 0
    nt = mat.node_tree
    n = 0
    # rgb -> BBS_Paint B-socket (inputs[7]), je komponente ein array_index (wie tint/glow)
    for comp, idx in (("r", 0), ("g", 1), ("b", 2)):
        n += _key_socket_component(paint_node.inputs[7], paint.get(comp), idx, tick_scale)
    # intensity -> Factor (inputs[0], skalar), auf |intensity| geclampt 0..1
    factor_kfs = _factor_from_intensity(paint.get("intensity"))
    n += _key_socket_component(paint_node.inputs[0], factor_kfs, 0, tick_scale)
    # shaderShadow -> custom-prop + flag (kein render)
    _store_shader_shadow(mat, paint.get("shaderShadow"), obj)
    return n


def _factor_from_intensity(keyframes):
    """intensity-keyframes -> Mix-Factor-keyframes: |intensity| auf 0..1 geclampt (BBS' intensity ist
    unbounded; der Mix-Factor lebt in 0..1, vorzeichen ist schon im blend_type gekapselt). Gibt eine
    NEUE keyframe-liste (original-payload nicht mutieren). Wirft nie."""
    if not keyframes or not isinstance(keyframes, (list, tuple)):
        return keyframes
    out = []
    for kf in keyframes:
        if not isinstance(kf, dict):
            out.append(kf)
            continue
        try:
            v = float(kf.get("value", 0.0))
        except (TypeError, ValueError):
            out.append(kf)
            continue
        kf = dict(kf)
        kf["value"] = min(abs(v), 1.0)
        out.append(kf)
    return out


def _key_socket_component(socket, keyframes, array_index, tick_scale):
    """Eine keyframe-liste auf EINEN array_index eines sockets legen (default_value). Setzt interp +
    bezier-handles wie die transform/color-F-curves. Gibt zahl geschriebener keys. (Struktur-spiegel
    von actor_material._key_socket_component — eigenstaendig gehalten, kein cross-modul-import einer
    privaten funktion.)"""
    if not keyframes or not isinstance(keyframes, (list, tuple)):
        return 0
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
        # RGBA-socket ist ein 4-float-array -> die komponente setzen, dann diesen index keyen.
        # skalar-socket (Factor inputs[0]): default_value ist ein FLOAT -> keyframe_insert MUSS ohne
        # index laufen (5.2 wirft sonst "index N was given while property is not an array").
        is_array = True
        try:
            socket.default_value[array_index] = value
        except (TypeError, IndexError):
            is_array = False
            try:
                socket.default_value = value
            except (TypeError, ValueError):
                continue
        try:
            if is_array:
                socket.keyframe_insert("default_value", frame=frame, index=array_index)
            else:
                socket.keyframe_insert("default_value", frame=frame)
            written += 1
        except (RuntimeError, TypeError):
            pass
    _apply_interp(socket, array_index, keyframes, tick_scale)
    return written


def _apply_interp(socket, array_index, keyframes, tick_scale):
    """Interp/handles auf die eben eingefuegten keys (channelbag-lesen, 5.2-weg). Wirft nie.

    FALLE (v1-gemessen): paint-r (BBS_Paint.inputs[7], array_index 0), Factor (inputs[0],
    array_index 0), tint-r und alpha liegen ALLE auf DEMSELBEN node_tree/channelbag und teilen
    array_index 0 -> ein reiner array_index-filter matcht die FREMDE kurve mit und ueberschreibt
    ihre interp (cross-contamination). Deshalb zusaetzlich auf den EIGENEN data_path des sockets
    scopen (socket.path_from_id())."""
    if anim_utils is None:
        return
    nt = socket.id_data
    ad = getattr(nt, "animation_data", None)
    if ad is None or ad.action is None:
        return
    try:
        cbag = anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot)
    except (AttributeError, TypeError, RuntimeError):
        return
    if cbag is None:
        return
    try:
        target_path = socket.path_from_id() + ".default_value"
    except (AttributeError, RuntimeError):
        target_path = None  # never-throws-contract: kein path -> path-filter ueberspringen
    by_frame = {}
    for kf in keyframes:
        if isinstance(kf, dict):
            try:
                by_frame[round(float(kf["tick"]) * tick_scale, 4)] = kf
            except (TypeError, ValueError, KeyError):
                pass
    for fc in cbag.fcurves:
        if fc.array_index != array_index:
            continue
        if target_path is not None and fc.data_path != target_path:
            continue
        for kp in fc.keyframe_points:
            src = by_frame.get(round(kp.co.x, 4))
            if not src:
                continue
            interp = str(src.get("interp") or "linear").lower()
            kp.interpolation = 'CONSTANT' if interp == 'constant' else (
                'BEZIER' if interp in ('bezier', 'hermite') else 'LINEAR')
        try:
            fc.update()
        except RuntimeError:
            pass
