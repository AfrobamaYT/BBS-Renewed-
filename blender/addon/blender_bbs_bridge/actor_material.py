"""Material-sync: BBS color/opacity (replay.properties) als Blender-material-F-curve.

Der BBS-`color`-kanal (whole-actor tint, floats r/g/b/a 0..1) kommt im ACTORS-payload unter
`properties.color` als vier subkurven (r,g,b,a — java ActorPropertiesExporter zerlegt den Color-
value, weil Blender skalare F-curves animiert). Diese datei baut die material-node-kette (tint-Mix
MULTIPLY + alpha-Math MULTIPLY) und keyframet ihre sockets.

FALLEN (aus Blender-5.2-research, nicht geraten):
- animation_data liegt auf mat.NODE_TREE, nicht mat. keyframe_insert IMMER auf dem SOCKET
  ("default_value") — auf dem node/tree mit inputs-data_path wirft "inputs not animatable".
- ShaderNodeMix (RGBA) hat DUPLIKAT-socket-namen -> index-zugriff pflicht: inputs[0]=Factor,
  inputs[6]=A, inputs[7]=B, outputs[2]=Result.
- gelinkter Alpha-socket ignoriert default_value -> alpha-multiplikator MUSS ein Math-node sein.
- fcurves lesen in 5.2: anim_utils.action_get_channelbag_for_slot(ad.action, ad.action_slot).
- GETEILTES material: _bobj_material teilt ein material pro textur. Wir kopieren es pro actor,
  sonst färben sich alle gleich. Kopie-name = <basis>__<uuid8> (idempotent).
"""
import bpy

try:
    from bpy_extras import anim_utils
except ImportError:
    anim_utils = None

# node-namen die wir anlegen (stabil, zum wiederfinden)
_TINT_NODE = "BBS_Tint"
_ALPHA_NODE = "BBS_AlphaMul"
# PHASE G: emission-color-Mix (rgb-kanal des glow). Der intensity-wert geht DIREKT auf
# bsdf.inputs["Emission Strength"] (skalar-socket wie alpha-Math.inputs[1]) — kein eigener node.
_GLOW_NODE = "BBS_Glow"


def has_material_channels(replay):
    """Trägt der replay einen nicht-leeren color- ODER glow-kanal? (Phase G: + glow.)"""
    props = replay.get("properties") if isinstance(replay, dict) else None
    if not isinstance(props, dict):
        return False
    color = props.get("color")
    if isinstance(color, dict) and any(color.get(c) for c in ("r", "g", "b", "a")):
        return True
    glow = props.get("glow")
    if isinstance(glow, dict) and any(glow.get(c) for c in ("r", "g", "b", "intensity")):
        return True
    return False


def _uuid8(replay):
    u = str(replay.get("uuid") or replay.get("id") or "actor")
    return u.replace("-", "")[:8] or "actor"


def _material_holder(obj):
    """Den echten material-TRÄGER finden. Bei einem STATISCHEN mesh-actor ist das `obj` selbst.
    Bei einem GERIGTEN BOBJ-actor ist `obj` die ARMATURE (0 material_slots) — das material lebt
    auf dem MESH-child. (ECHT-TEST-LÜCKE 2026-07-18: bis hier wurde nur `obj` geprüft → whole-form
    color/glow/paint erreichte gerigte actors NIE, weil die material-applies auf den slot-losen
    armature-root gingen. Fixtures verdeckten das mit statischen mesh-roots.)
    Gibt das erste objekt mit material_slots zurück (obj selbst bevorzugt), sonst None."""
    if obj.material_slots:
        return obj
    # mesh-child mit material suchen (BOBJ-armature → parented mesh)
    for child in obj.children:
        if child.type == 'MESH' and child.material_slots:
            return child
    return None


def ensure_owned_material(obj, uuid8):
    """Dem actor eine EIGENE material-kopie geben (falls er noch das geteilte trägt) + die
    tint/alpha-node-kette einbauen. Idempotent — mehrfach-aufruf gibt dasselbe material.
    Findet den material-TRÄGER (obj ODER mesh-child bei gerigten actors, siehe _material_holder).
    Gibt das material oder None (kein slot/material)."""
    holder = _material_holder(obj)
    if holder is None:
        return None
    slot = holder.material_slots[0]
    base = slot.material
    if base is None:
        return None
    suffix = "__" + uuid8
    # bereits im besitz (poll ruft das je 0.5s) -> NICHT nochmal kopieren, sonst waechst der
    # name je aufruf (...__uuid__uuid...) und der slot zeigt jede welle auf eine frische kopie.
    if base.name.endswith(suffix):
        _ensure_nodes(base)
        return base
    owned_name = base.name + suffix
    owned = bpy.data.materials.get(owned_name)
    if owned is None:
        owned = base.copy()
        owned.name = owned_name
    slot.material = owned
    _ensure_nodes(owned)
    return owned


def _ensure_nodes(mat):
    """Tint-Mix (MULTIPLY, zwischen textur.Color und BSDF.Base Color) + alpha-Math (MULTIPLY,
    zwischen textur.Alpha und BSDF.Alpha) EINMAL einbauen. Idempotent (per node-name)."""
    if not mat.use_nodes:
        mat.use_nodes = True
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tex = next((n for n in nt.nodes if n.type == 'TEX_IMAGE'), None)
    if bsdf is None:
        return
    # ---- TINT ----
    # ⚠️ WIRE-IDEMPOTENZ (2026-07-18, render-bug gefunden): das gate darf NUR den node-NEUBAU
    # überspringen, NICHT die verdrahtung. base.copy() (per-actor-material) nimmt den tint-node mit,
    # aber die Result->Base-Color-verbindung geht dabei verloren + die textur hängt wieder direkt an
    # Base Color → tint wird berechnet aber nie ins bild geleitet → render WEISS egal welche farbe.
    # Deshalb: node bei bedarf bauen, aber die links IMMER sicherstellen (link-guarded, idempotent).
    tint = nt.nodes.get(_TINT_NODE)
    if tint is None:
        tint = nt.nodes.new("ShaderNodeMix")
        tint.name = _TINT_NODE
        tint.data_type = 'RGBA'
        tint.blend_type = 'MULTIPLY'
        tint.inputs[0].default_value = 1.0                       # Factor
        tint.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)      # B = tint (start weiß = no-op)
    # A (inputs[6]) = base-color-quelle: textur.Color falls da (und noch nicht so verlinkt), sonst weiß
    if tex is not None and "Color" in tex.outputs:
        if not any(l.to_socket == tint.inputs[6] and l.from_socket == tex.outputs["Color"]
                   for l in nt.links):
            nt.links.new(tex.outputs["Color"], tint.inputs[6])
    elif not any(l.to_socket == tint.inputs[6] for l in nt.links):
        tint.inputs[6].default_value = (1.0, 1.0, 1.0, 1.0)
    # Result -> Base Color: IMMER sicherstellen (der eigentliche fix — nie nur beim node-neubau)
    if not any(l.to_socket == bsdf.inputs["Base Color"] and l.from_socket == tint.outputs[2]
               for l in nt.links):
        nt.links.new(tint.outputs[2], bsdf.inputs["Base Color"])
    # ---- ALPHA ---- (gleiche wire-idempotenz)
    amul = nt.nodes.get(_ALPHA_NODE)
    if amul is None:
        amul = nt.nodes.new("ShaderNodeMath")
        amul.name = _ALPHA_NODE
        amul.operation = 'MULTIPLY'
        amul.inputs[1].default_value = 1.0                        # globaler opacity-faktor
    # input[0] = textur-alpha falls vorhanden (und noch nicht so verlinkt), sonst 1
    if tex is not None and "Alpha" in tex.outputs:
        if not any(l.to_socket == amul.inputs[0] and l.from_socket == tex.outputs["Alpha"]
                   for l in nt.links):
            nt.links.new(tex.outputs["Alpha"], amul.inputs[0])
    elif not any(l.to_socket == amul.inputs[0] for l in nt.links):
        amul.inputs[0].default_value = 1.0
    # Result -> BSDF.Alpha: IMMER sicherstellen
    if not any(l.to_socket == bsdf.inputs["Alpha"] and l.from_socket == amul.outputs[0]
               for l in nt.links):
        nt.links.new(amul.outputs[0], bsdf.inputs["Alpha"])
    # ---- GLOW / EMISSION ---- (Phase G, gleiche WIRE-IDEMPOTENZ wie tint/alpha)
    # EIN Mix-node (RGBA) traegt die emission-farbe: A=schwarz, B=glow-rgb, Factor=1.0, blend MIX
    # -> Result == B == glow-farbe (die rgb-subkurven keyframen B/inputs[7]). Result geht auf
    # bsdf.inputs["Emission Color"]. Die STAERKE (intensity) laeuft NICHT durch diesen node,
    # sondern direkt als skalar auf bsdf.inputs["Emission Strength"] (default 0 = glow AUS, bis
    # apply_glow_channels sie keyframet). Wie bei tint: node bei bedarf, links IMMER sicherstellen
    # (base.copy() nimmt den node mit, verliert aber die Emission-Color-verbindung -> ohne den
    # link-guard leuchtet nichts trotz gesetzter farbe).
    if "Emission Color" in bsdf.inputs:
        glow = nt.nodes.get(_GLOW_NODE)
        if glow is None:
            glow = nt.nodes.new("ShaderNodeMix")
            glow.name = _GLOW_NODE
            glow.data_type = 'RGBA'
            glow.blend_type = 'MIX'
            glow.inputs[0].default_value = 1.0                     # Factor -> Result == B
            glow.inputs[6].default_value = (0.0, 0.0, 0.0, 1.0)    # A = schwarz (unbenutzt bei fac=1)
            glow.inputs[7].default_value = (0.0, 0.0, 0.0, 1.0)    # B = glow-farbe (start schwarz = aus)
        # Result -> Emission Color: IMMER sicherstellen (der eigentliche fix, nie nur beim neubau)
        if not any(l.to_socket == bsdf.inputs["Emission Color"] and l.from_socket == glow.outputs[2]
                   for l in nt.links):
            nt.links.new(glow.outputs[2], bsdf.inputs["Emission Color"])
    # MC-Skins haben ausgeschnittene, weitgehend binaere Alpha-Pixel. BLENDED
    # sortiert nur das gesamte Actor-Mesh und laesst dadurch kameraseitige
    # Flaechen hohl wirken; DITHERED schreibt die sichtbaren Pixel in die Tiefe.
    # Den aelteren Alpha-Hashed-Modus nur benutzen, wenn die neue API fehlt.
    if hasattr(mat, "blend_method"):
        try:
            mat.blend_method = 'HASHED'
        except TypeError:
            pass
    if hasattr(mat, "surface_render_method"):
        try:
            mat.surface_render_method = 'DITHERED'
        except TypeError:
            pass


def apply_color_channels(obj, replay, tick_scale):
    """Die color-subkurven (r,g,b,a) als F-curve auf tint-Mix.inputs[7] (rgb) + alpha-Math.inputs[1].
    Gibt die zahl geschriebener keys. Wirft nie (ein kaputter kanal darf den import nicht killen)."""
    if not has_material_channels(replay):
        return 0
    mat = ensure_owned_material(obj, _uuid8(replay))
    if mat is None:
        return 0
    nt = mat.node_tree
    tint = nt.nodes.get(_TINT_NODE)
    amul = nt.nodes.get(_ALPHA_NODE)
    color = replay["properties"]["color"]
    n = 0
    # rgb -> tint B-socket (inputs[7]), je komponente ein array_index
    if tint is not None:
        for comp, idx in (("r", 0), ("g", 1), ("b", 2)):
            n += _key_socket_component(tint.inputs[7], color.get(comp), idx, tick_scale)
    # a -> alpha-multiplikator (Math inputs[1], skalar array_index 0)
    if amul is not None:
        n += _key_socket_component(amul.inputs[1], color.get("a"), 0, tick_scale)
    return n


def apply_glow_channels(obj, replay, tick_scale):
    """Die glow-subkurven (r,g,b,intensity) als F-curve legen (Phase G):
      - rgb  -> BBS_Glow-Mix.inputs[7] (B-socket, emission-farbe), je komponente ein array_index
      - intensity -> bsdf.inputs["Emission Strength"] (skalar-socket wie alpha-Math.inputs[1])
    Glow teilt die PER-ACTOR material-kopie mit color (KEINE zweite kopie — ensure_owned_material
    ist idempotent, gibt dasselbe material). Gibt die zahl geschriebener keys. Wirft nie.

    GLOW-FALLE (dokumentierte grenze): BBS' emission-intensity kann NEGATIV sein (dunkelt die
    oberflaeche). Blender/EEVEE clampt Emission Strength auf ≥0 — ein negativer wert wuerde still
    verschluckt. Wir clampen ihn EXPLIZIT auf 0 und FLAGGEN das (log), statt es zu verstecken
    (Regel 0: kein stiller cut). Der rohe negative wert reist korrekt bis hier (java), die grenze
    ist rein die Blender-render-seite."""
    if not has_material_channels(replay):
        return 0
    glow = replay.get("properties", {}).get("glow")
    if not isinstance(glow, dict):
        return 0
    mat = ensure_owned_material(obj, _uuid8(replay))
    if mat is None:
        return 0
    nt = mat.node_tree
    glow_node = nt.nodes.get(_GLOW_NODE)
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    n = 0
    # rgb -> BBS_Glow B-socket (inputs[7]), je komponente ein array_index (wie tint)
    if glow_node is not None:
        for comp, idx in (("r", 0), ("g", 1), ("b", 2)):
            n += _key_socket_component(glow_node.inputs[7], glow.get(comp), idx, tick_scale)
    # intensity -> Emission Strength (skalar array_index 0, negative auf 0 geclampt + geflaggt)
    if bsdf is not None and "Emission Strength" in bsdf.inputs:
        intensity_kfs = _clamp_glow_intensity(glow.get("intensity"), obj)
        n += _key_socket_component(bsdf.inputs["Emission Strength"], intensity_kfs, 0, tick_scale)
    return n


def _clamp_glow_intensity(keyframes, obj):
    """Emission-strength-keyframes auf ≥0 clampen (BBS erlaubt negativ, Blender nicht). Gibt eine
    NEUE keyframe-liste (die original-payload nicht mutieren) mit geclampten values zurueck; ein
    negativer wert wird 1× pro actor geloggt (dokumentierte grenze, kein stiller cut). Wirft nie."""
    if not keyframes or not isinstance(keyframes, (list, tuple)):
        return keyframes
    out = []
    clamped = False
    for kf in keyframes:
        if not isinstance(kf, dict):
            out.append(kf)
            continue
        try:
            v = float(kf.get("value", 0.0))
        except (TypeError, ValueError):
            out.append(kf)
            continue
        if v < 0.0:
            clamped = True
            kf = dict(kf)
            kf["value"] = 0.0
        out.append(kf)
    if clamped:
        try:
            name = obj.get("bbs_uuid") or obj.name
        except (AttributeError, ReferenceError):
            name = "?"
        print("[bbs-bridge] glow: negative emission-intensity fuer actor %r auf 0 geclampt "
              "(BBS dunkelt damit, Blender clampt >=0 — dokumentierte grenze)" % (name,))
    return out


def _key_socket_component(socket, keyframes, array_index, tick_scale):
    """Eine keyframe-liste auf EINEN array_index eines sockets legen (default_value). Setzt interp
    + bezier-handles wie die transform-F-curves. Gibt zahl geschriebener keys."""
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
        # skalar-socket (Math.inputs[1]): default_value ist ein FLOAT -> keyframe_insert MUSS ohne
        # index laufen (5.2 wirft "index N was given while property is not an array", swallow-safe
        # aber = kein key). is_array merkt sich welcher fall vorliegt.
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
    """Interp/handles auf die eben eingefügten keys (channelbag-lesen, 5.2-weg). Wirft nie."""
    if anim_utils is None:
        return
    # das material-node_tree finden über den socket -> node -> id_data
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
    # FALLE (2026-07-18): tint-R (BBS_Tint.inputs[7], array_index 0) UND alpha (BBS_AlphaMul.inputs[1],
    # array_index 0) liegen auf DEMSELBEN node_tree/channelbag und teilen array_index 0 -> ein reiner
    # array_index-filter matcht die FREMDE kurve mit und ueberschreibt ihre interp (cross-contamination,
    # reviewer-gemessen :9976). Zusaetzlich auf den EIGENEN data_path des sockets scopen.
    try:
        target_path = socket.path_from_id() + ".default_value"
    except (AttributeError, RuntimeError):
        target_path = None  # never-throws-contract: kein path -> path-filter ueberspringen (alt-verhalten)
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
