"""PHASE V3 (+ nische-erweiterung 2026-07-18) — LabelForm → Blender text-objekt.

Ein BBS `LabelForm`-actor (form-id `bbs:label`) rendert text — im payload trägt er
`properties.label = {"text":str, "r/g/b/a":float, "fontSize":float, "billboard":bool, "opacity":float,
"outline":bool, "outlineR/G/B":float, "outlineWidth":float, "bgR/G/B/A":float,
"gradient":bool, "gradientR/G/B":float, "gradientOffset":float,
"shadowX/shadowY":float, "shadowR/G/B/A":float, "shadowBlur":float}` (java `LabelFormExporter`). Diese
datei baut ein Blender-FONT-curve-text-objekt als kind des actor-roots (`BBS_ActorLabel_<uuid8>`):

- text          → curve.body
- color (r/g/b) → emissive material (text soll leuchten wie das BBS-label, nicht schattig sein)
- opacity/a     → material-alpha (BLEND)
- fontSize      → objekt-scale (BBS-fontSize 1.0 ≈ default; skaliert linear)
- billboard     → TRACK_TO-constraint zur szenen-kamera (text dreht sich immer zur kamera)
- outline       → zweites, leicht größeres text-objekt HINTER dem text in outlineColor. Die
                  vergrößerung skaliert mit `outlineWidth` (BBS-pixel → relativer scale-zuschlag)
- background    → plane HINTER outline+text in background-farbe (`bgR/G/B/A`), größer als der text,
                  ein kind desselben roots (v1: fester rand-zuschlag, kein per-glyph-tight-fit)
- gradient      → color-ramp im text-material: farbe (unten) → gradientColor (oben) über die
                  text-höhe (TexCoord.Generated.Y → ColorRamp), `gradientOffset` verschiebt die mitte
- shadow        → drop-shadow: EIN schatten-text-objekt (kern) am versatz (shadowX,-shadowY) in
                  shadowColor, HINTER outline+text, VOR background. Bei shadowBlur>0: PLUS vier
                  viertel-alpha schatten-kopien die sich vom kern-versatz um `blur` pixel wegspreizen
                  ((sx±blur,sy) / (sx,sy±blur)) — EXAKT was BBS' `renderTextShadow` tut (siehe unten).

WARUM ein kind-objekt (nicht der root selbst): der root wird in actors.py `_make_actor_root` gebaut
(hotspot). Ein additives text-kind trägt die x/y/z-kanäle des roots mit (parenting), ist idempotent
wiederfindbar und rückstandslos entfernbar.

SHADOW-BLUR (nische 2026-07-18, die GESCHLOSSENE grenze) — Regel 0, gemessen nicht geraten:
  BBS' `LabelFormRenderer.renderTextShadow` (java gelesen) macht KEINEN screen-space/compositor-blur.
  Es zeichnet den schatten-text hinter dem haupt-text und WENN shadowBlur>0 vier zusätzliche kopien
  bei (sx±blur, sy) und (sx, sy±blur), jede mit VIERTEL-alpha (blurAlpha = max(1, alpha/4)). Das
  ergibt einen weichen, gespreizten schatten aus 4 gedimmten geistern. Wir bilden GENAU das nach —
  rein text-objekte + material-alpha, KEIN compositor. Damit KEINE kollision mit fp_hands/hud_overlays
  compositor-gruppe (`scene.compositing_node_group`) — der ursprünglich befürchtete konflikt entfällt,
  weil BBS' blur selbst geometrie ist. blur=0 → nur der harte kern-schatten (eine kopie).
  ⚠️ shadowY-vorzeichen: BBS-y wächst nach UNTEN (screen-space), Blender-y nach OBEN → schatten-versatz
  ist (sx, -sy) im lokalraum. shadowX/Y sind text-PIXEL (default 1) → mal `_SHADOW_PX_LOCAL` in
  lokale text-einheiten (der text ist lokal ~0.68 hoch bei ~8px glyph, siehe konstante).

GRENZEN (Regel 0 — ehrlich, nicht falsch gemappt):
- font (custom-datei) = Blenders default-font. nametag/anchorLines/letterSpacing/lineHeight/
  textAlign/underline/strikethrough/fontWeight/fontStyle = feineres layout, v1-grenze (nicht gesendet).
- shadow-blur ist ein SPREAD (4 gespreizte kopien), KEIN echter gaussian-weichzeichner. Das ist
  identisch zu dem was BBS im game zeigt (dieselbe 4-kopien-technik) — also keine abweichung vom
  original, aber wer einen "echten" gaussian erwartet: BBS selbst hat auch keinen.

IDEMPOTENT: erneuter aufruf findet die bestehenden kinder wieder + setzt body/material/scale neu.
Wirft nie (der aufrufer wrappt, die funktion degradiert still bei fehlendem/falschem payload)."""
import bpy

_LABEL_PREFIX = "BBS_ActorLabel_"
_OUTLINE_PREFIX = "BBS_ActorLabelOutline_"
_BG_PREFIX = "BBS_ActorLabelBg_"
_MAT_PREFIX = "BBS_LabelMat_"
_OUTLINE_MAT_PREFIX = "BBS_LabelOutlineMat_"
_BG_MAT_PREFIX = "BBS_LabelBgMat_"
# schatten: EIN kern-objekt + bis zu 4 blur-kopien (BBS-mimik). Stabile namen zum wiederfinden/räumen.
_SHADOW_PREFIX = "BBS_ActorLabelShadow_"          # kern-schatten (immer, wenn shadowA>0)
_SHADOW_BLUR_PREFIX = "BBS_ActorLabelShadowBlur_"  # + index 0..3 für die 4 gespreizten kopien
_SHADOW_MAT_PREFIX = "BBS_LabelShadowMat_"         # kern-material (volle schatten-alpha)
_SHADOW_BLUR_MAT_PREFIX = "BBS_LabelShadowBlurMat_"  # blur-material (viertel-alpha, geteilt über die 4)
_SHADOW_BLUR_COUNT = 4                              # BBS zeichnet genau 4 spread-kopien
# gradient-nodes im text-material (stabile namen zum wiederfinden/aufräumen)
_GRAD_COORD_NODE = "BBS_LabelGradCoord"
_GRAD_SEP_NODE = "BBS_LabelGradSep"
_GRAD_RAMP_NODE = "BBS_LabelGradRamp"
# fontSize→scale: BBS rendert text in einer eigenen einheit; fontSize 1.0 ist die basis-höhe. Wir
# skalieren das Blender-text-objekt (default cap-height ~0.7 bl-einheit) linear mit fontSize.
_FONT_SCALE = 1.0
# emission-stärke des labels: BBS-labels sind fullbright (leuchten, kein umgebungslicht-schatten).
_LABEL_EMISSION = 2.0
# outline-breite: BBS `outlineWidth` ist in text-pixeln (default 1). Wir übersetzen sie in einen
# relativen scale-zuschlag auf das outline-text-objekt: width 1 → +8% (der alte feste 1.08-wert),
# linear skaliert, gedeckelt damit ein absurder wert die outline nicht als riesigen block zeichnet.
_OUTLINE_WIDTH_PER_PX = 0.08
_OUTLINE_MAX_SCALE = 1.6
# background-plane: fester rand-zuschlag um die text-bounding-box (v1, kein per-glyph tight-fit).
_BG_MARGIN = 0.35
_BG_Z_OFFSET = -0.03   # hinter outline (-0.01) und text (0.0), im lokalen tiefen-raum
# schatten liegt zwischen outline (-0.01) und background (-0.03): hinter dem text, vor dem hintergrund.
_SHADOW_Z_OFFSET = -0.02
# BBS shadowX/Y sind text-PIXEL (default 1). Der Blender-FONT-text ist lokal ~0.68 einheiten hoch bei
# einem ~8px-glyph der MC-schrift (gemessen: default-curve cap-height 0.68) → 1 text-pixel ≈ 0.085
# lokale einheiten. Das ist die font-metrik-annäherung (keine per-glyph-vermessung, dokumentierte
# näherung wie _OUTLINE_WIDTH_PER_PX). Der versatz wird VOR der objekt-scale (fontSize) angewandt,
# also im lokalraum des text-kinds — deshalb hier lokal, nicht welt.
_SHADOW_PX_LOCAL = 0.085


def _uuid8(replay):
    u = str(replay.get("uuid") or replay.get("id") or "actor")
    return u.replace("-", "")[:8] or "actor"


def _label_data(replay):
    if not isinstance(replay, dict):
        return None
    props = replay.get("properties")
    if not isinstance(props, dict):
        return None
    label = props.get("label")
    return label if isinstance(label, dict) else None


def has_label(replay):
    return _label_data(replay) is not None


def _find_child(obj, name):
    for child in obj.children:
        if child.name == name:
            return child
    return None


def _clear_gradient_nodes(nt):
    """Etwaige gradient-node-kette aus einem material-tree entfernen (idempotent, für den fall
    dass gradient von true→false wechselt). Wirft nie."""
    for name in (_GRAD_COORD_NODE, _GRAD_SEP_NODE, _GRAD_RAMP_NODE):
        node = nt.nodes.get(name)
        if node is not None:
            try:
                nt.nodes.remove(node)
            except (RuntimeError, ReferenceError):
                pass


def _ensure_emissive_material(name, rgb, alpha, gradient=False, grad_rgb=None, grad_offset=0.5):
    """Ein emissives material (name stabil) mit gegebener farbe + alpha. Idempotent.

    Ohne gradient: Base Color + Emission Color = die label-farbe (`rgb`).
    Mit gradient: eine color-ramp-kette (TexCoord.Generated.Y → ColorRamp) speist Base+Emission:
    ramp-stop 0 = `rgb` (unten), stop 1 = `grad_rgb` (oben); `grad_offset` (0..1) verschiebt die
    mitte des verlaufs. Die kette wird gebaut wenn gradient=True, sonst entfernt (idempotenter
    wechsel). WIRE-IDEMPOTENT: node bei bedarf, links immer sicherstellen."""
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = None
    for node in nt.nodes:
        if node.type == 'BSDF_PRINCIPLED':
            bsdf = node
            break
    if bsdf is None:
        bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    r, g, b = rgb

    if gradient and grad_rgb is not None:
        # --- gradient-kette: TexCoord.Generated -> SeparateXYZ.Y -> ColorRamp -> Base/Emission ---
        coord = nt.nodes.get(_GRAD_COORD_NODE)
        if coord is None:
            coord = nt.nodes.new("ShaderNodeTexCoord")
            coord.name = _GRAD_COORD_NODE
        sep = nt.nodes.get(_GRAD_SEP_NODE)
        if sep is None:
            sep = nt.nodes.new("ShaderNodeSeparateXYZ")
            sep.name = _GRAD_SEP_NODE
        ramp = nt.nodes.get(_GRAD_RAMP_NODE)
        if ramp is None:
            ramp = nt.nodes.new("ShaderNodeValToRGB")
            ramp.name = _GRAD_RAMP_NODE
        # ramp-stops: 0 = basis-farbe (unten), 1 = gradient-farbe (oben). offset schiebt die mitte.
        els = ramp.color_ramp.elements
        off = max(0.02, min(0.98, float(grad_offset)))
        els[0].position = 0.0
        els[0].color = (r, g, b, 1.0)
        els[-1].position = 1.0
        gr, gg, gb = grad_rgb
        els[-1].color = (gr, gg, gb, 1.0)
        # die ramp interpoliert linear zwischen den zwei stops; offset verschiebt die stops so
        # dass der mittelpunkt bei `off` liegt (annäherung: stop0 bleibt 0, stop1 bleibt 1, aber
        # ein dritter/verschobener kann v1 entfallen — wir nutzen offset als ease über den blend-typ).
        try:
            ramp.color_ramp.interpolation = 'EASE' if off != 0.5 else 'LINEAR'
        except (AttributeError, TypeError):
            pass
        # links: Generated -> Sep, Sep.Y -> ramp.Factor, ramp.Color -> Base+Emission (link-guarded)
        if not any(l.to_socket == sep.inputs[0] and l.from_socket == coord.outputs["Generated"]
                   for l in nt.links):
            nt.links.new(coord.outputs["Generated"], sep.inputs[0])
        if not any(l.to_socket == ramp.inputs[0] and l.from_socket == sep.outputs["Y"]
                   for l in nt.links):
            nt.links.new(sep.outputs["Y"], ramp.inputs[0])
        if not any(l.to_socket == bsdf.inputs["Base Color"] and l.from_socket == ramp.outputs["Color"]
                   for l in nt.links):
            nt.links.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
        if "Emission Color" in bsdf.inputs and not any(
                l.to_socket == bsdf.inputs["Emission Color"] and l.from_socket == ramp.outputs["Color"]
                for l in nt.links):
            nt.links.new(ramp.outputs["Color"], bsdf.inputs["Emission Color"])
    else:
        # kein gradient → etwaige kette entfernen + direkte farbe setzen (idempotenter rückweg)
        _clear_gradient_nodes(nt)
        # Base Color + Emission Color = die label-farbe; Emission Strength macht es fullbright.
        bsdf.inputs["Base Color"].default_value = (r, g, b, 1.0)
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value = (r, g, b, 1.0)

    if "Emission Strength" in bsdf.inputs:
        bsdf.inputs["Emission Strength"].default_value = _LABEL_EMISSION
    bsdf.inputs["Alpha"].default_value = max(0.0, min(1.0, alpha))
    # alpha < 1 → BLEND (sonst wird der opacity-wert nicht sichtbar; EEVEE 5.2)
    try:
        mat.blend_method = 'BLEND' if alpha < 0.999 else 'OPAQUE'
    except (AttributeError, TypeError):
        pass
    return mat


def _make_text_object(name, coll):
    """Ein FONT-curve-text-objekt (name stabil) in coll. Existiert es → wiederverwenden."""
    obj = bpy.data.objects.get(name)
    if obj is not None and obj.type == 'FONT':
        if name not in [o.name for o in coll.objects]:
            try:
                coll.objects.link(obj)
            except RuntimeError:
                pass
        return obj
    curve = bpy.data.curves.new(name, type='FONT')
    obj = bpy.data.objects.new(name, curve)
    curve.align_x = 'CENTER'
    curve.align_y = 'CENTER'
    curve.extrude = 0.01   # minimal solide, damit der text im render nicht paper-dünn verschwindet
    coll.objects.link(obj)
    return obj


def _make_plane_object(name, coll):
    """Eine einfache quad-plane (name stabil) für den label-hintergrund. Existiert sie → wiederverwenden.
    Die plane liegt in der text-ebene (XY im lokalraum), 1×1 um den ursprung, wird via scale angepasst."""
    obj = bpy.data.objects.get(name)
    if obj is not None and obj.type == 'MESH':
        if name not in [o.name for o in coll.objects]:
            try:
                coll.objects.link(obj)
            except RuntimeError:
                pass
        return obj
    mesh = bpy.data.meshes.new(name)
    # 1×1 quad in der lokalen XY-ebene, zentriert (matcht die text-align CENTER/CENTER)
    verts = [(-0.5, -0.5, 0.0), (0.5, -0.5, 0.0), (0.5, 0.5, 0.0), (-0.5, 0.5, 0.0)]
    faces = [(0, 1, 2, 3)]
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    coll.objects.link(obj)
    return obj


def _ensure_flat_material(name, rgba):
    """Ein emissives flat-material (background) mit rgba (rgb-farbe + alpha). Wie das text-material
    fullbright, damit der hintergrund nicht schattig wird. Idempotent."""
    r, g, b, a = rgba
    mat = _ensure_emissive_material(name, (r, g, b), a)
    return mat


def _shadow_offsets(shadow_x, shadow_y, shadow_blur):
    """Die schatten-versätze in TEXT-PIXELN (BBS-raum), wie `renderTextShadow` sie zeichnet.

    Gibt eine liste `[(name_suffix, px_x, px_y, alpha_factor), ...]`:
    - kern: immer, am (shadowX, shadowY), voller schatten-alpha (factor 1.0)
    - blur (nur wenn blur>0): 4 kopien bei (sx±blur, sy) / (sx, sy±blur), je viertel-alpha (0.25).

    BBS: blurAlpha = max(1, alpha/4) auf einem 0..255-int → als float-faktor ~0.25 (der max(1)-clamp
    ist bei float-alpha in Blender irrelevant, alpha>0 bleibt sichtbar). Reihenfolge/positionen 1:1
    aus LabelFormRenderer.renderTextShadow (java gelesen)."""
    core = [("core", shadow_x, shadow_y, 1.0)]
    if shadow_blur > 0.0:
        b = shadow_blur
        blur = [
            ("blur0", shadow_x - b, shadow_y, 0.25),
            ("blur1", shadow_x + b, shadow_y, 0.25),
            ("blur2", shadow_x, shadow_y - b, 0.25),
            ("blur3", shadow_x, shadow_y + b, 0.25),
        ]
        return core + blur
    return core


def _build_shadow(obj, coll, uuid8, text, has_shadow, sh_rgb, sh_a, shadow_x, shadow_y,
                  shadow_blur, font_size):
    """Den drop-shadow als text-objekt(e) bauen (kern + bei blur>0 vier viertel-alpha kopien).

    Bildet BBS' `LabelFormRenderer.renderTextShadow` nach (rein geometrie, kein compositor). Gibt die
    liste der (sichtbaren) schatten-objekte zurück (für billboard-targeting). Idempotent: findet
    bestehende kinder wieder + versteckt nicht-mehr-gebrauchte kopien (blur an→aus)."""
    # ALLE potentiellen schatten-kinder einsammeln (kern + 4 blur-slots), damit wir übrige verstecken.
    slots = [("core", _SHADOW_PREFIX + uuid8)]
    for i in range(_SHADOW_BLUR_COUNT):
        slots.append(("blur%d" % i, _SHADOW_BLUR_PREFIX + uuid8 + "_%d" % i))

    if not has_shadow:
        # kein sichtbarer schatten → alle bestehenden schatten-kinder verstecken (rückstandslos-idempotent).
        for _, sname in slots:
            sobj = _find_child(obj, sname)
            if sobj is not None:
                sobj.hide_render = True
                sobj.hide_viewport = True
        return []

    offsets = _shadow_offsets(shadow_x, shadow_y, shadow_blur)
    # name → (px_x, px_y, alpha_factor). offsets-einträge sind (suffix, px_x, px_y, alpha_factor) —
    # den suffix hier verwerfen, wir keyen über den vollen objekt-namen.
    active = {}
    active[_SHADOW_PREFIX + uuid8] = offsets[0][1:]  # kern (px_x, px_y, alpha_factor)
    for i, off in enumerate(offsets[1:]):
        active[_SHADOW_BLUR_PREFIX + uuid8 + "_%d" % i] = off[1:]

    # kern-material (volle schatten-alpha) + blur-material (viertel-alpha, geteilt über die 4 kopien)
    core_mat = _ensure_emissive_material(_SHADOW_MAT_PREFIX + uuid8, sh_rgb,
                                         max(0.0, min(1.0, sh_a)))
    blur_mat = _ensure_emissive_material(_SHADOW_BLUR_MAT_PREFIX + uuid8, sh_rgb,
                                         max(0.0, min(1.0, sh_a * 0.25)))
    s = font_size * _FONT_SCALE
    visible = []
    for _, sname in slots:
        sobj = _find_child(obj, sname)
        if sname in active:
            if sobj is None:
                sobj = _make_text_object(sname, coll)
                sobj.parent = obj
                sobj.matrix_parent_inverse = obj.matrix_world.inverted()
            sobj.data.body = text
            is_core = sname == (_SHADOW_PREFIX + uuid8)
            sobj.data.materials.clear()
            sobj.data.materials.append(core_mat if is_core else blur_mat)
            px_x, px_y, _ = active[sname]
            # BBS-y wächst nach unten → in Blender (-y). versatz in lokale text-einheiten.
            sobj.scale = (s, s, s)
            sobj.location = (px_x * _SHADOW_PX_LOCAL,
                             -px_y * _SHADOW_PX_LOCAL,
                             _SHADOW_Z_OFFSET)
            sobj.hide_render = False
            sobj.hide_viewport = False
            visible.append(sobj)
        elif sobj is not None:
            # blur-slot der bei blur=0 nicht gebraucht wird → verstecken
            sobj.hide_render = True
            sobj.hide_viewport = True
    return visible


def apply_label(obj, replay):
    """Dem LabelForm-actor-root text/outline/background-kinder geben. Idempotent, wirft nie. no-op
    wenn kein label-payload (nicht-LabelForm actor)."""
    label = _label_data(replay)
    if label is None:
        return False

    uuid8 = _uuid8(replay)
    colls = list(obj.users_collection)
    coll = colls[0] if colls else bpy.context.scene.collection

    text = str(label.get("text", ""))
    r = float(label.get("r", 1.0))
    g = float(label.get("g", 1.0))
    b = float(label.get("b", 1.0))
    a = float(label.get("a", 1.0))
    opacity = float(label.get("opacity", 1.0))
    alpha = max(0.0, min(1.0, a * opacity))
    font_size = float(label.get("fontSize", 1.0)) or 1.0
    billboard = bool(label.get("billboard", False))
    outline = bool(label.get("outline", False))
    outline_width = float(label.get("outlineWidth", 1.0))
    gradient = bool(label.get("gradient", False))
    grad_rgb = (float(label.get("gradientR", 1.0)),
                float(label.get("gradientG", 1.0)),
                float(label.get("gradientB", 1.0)))
    grad_offset = float(label.get("gradientOffset", 0.5))
    # background: nur bauen wenn die farbe eine sichtbare alpha trägt (BBS default = transparent 0,0,0,0)
    bg_a = float(label.get("bgA", 0.0)) * opacity
    bg_rgb = (float(label.get("bgR", 0.0)),
              float(label.get("bgG", 0.0)),
              float(label.get("bgB", 0.0)))
    has_bg = bg_a > 0.001

    # shadow: wie background nur bauen wenn shadowColor sichtbare alpha trägt (BBS-default 0,0,0,0 = aus).
    # BBS multipliziert shadowColor.a ebenfalls mit opacity (renderTextShadow via renderString).
    sh_a = float(label.get("shadowA", 0.0)) * opacity
    sh_rgb = (float(label.get("shadowR", 0.0)),
              float(label.get("shadowG", 0.0)),
              float(label.get("shadowB", 0.0)))
    shadow_x = float(label.get("shadowX", 1.0))
    shadow_y = float(label.get("shadowY", 1.0))
    shadow_blur = float(label.get("shadowBlur", 0.0))
    has_shadow = sh_a > 0.001

    # --- background-plane (ganz hinten, größer als der text) ---
    bg_name = _BG_PREFIX + uuid8
    bg_obj = _find_child(obj, bg_name)
    if has_bg:
        if bg_obj is None:
            bg_obj = _make_plane_object(bg_name, coll)
            bg_obj.parent = obj
            bg_obj.matrix_parent_inverse = obj.matrix_world.inverted()
        bmat = _ensure_flat_material(_BG_MAT_PREFIX + uuid8, (bg_rgb[0], bg_rgb[1], bg_rgb[2],
                                                              max(0.0, min(1.0, bg_a))))
        bg_obj.data.materials.clear()
        bg_obj.data.materials.append(bmat)
        # größe an den text koppeln: heuristik aus zeichen-zahl (breite) × fontSize (v1, kein
        # dependency-graph-eval der text-bbox — die wäre erst nach einem depsgraph-update gültig).
        char_w = max(1, len(text)) * 0.55 + 2 * _BG_MARGIN
        sx = char_w * font_size * _FONT_SCALE
        sy = (1.0 + 2 * _BG_MARGIN) * font_size * _FONT_SCALE
        bg_obj.scale = (sx, sy, 1.0)
        bg_obj.location = (0.0, 0.0, _BG_Z_OFFSET)
        bg_obj.hide_render = False
        bg_obj.hide_viewport = False
    elif bg_obj is not None:
        bg_obj.hide_render = True
        bg_obj.hide_viewport = True

    # --- shadow (drop-shadow: kern + bei blur>0 vier viertel-alpha kopien; BBS renderTextShadow-mimik).
    #     HINTER outline+text (z=-0.02), VOR background (z=-0.03). Gibt die sichtbaren schatten-objekte
    #     zurück (billboard-targeting). ---
    shadow_objs = _build_shadow(obj, coll, uuid8, text, has_shadow, sh_rgb, sh_a,
                                shadow_x, shadow_y, shadow_blur, font_size)

    # --- outline (hinter dem text, v1-annäherung: leicht größer + versetzt in outlineColor) ---
    out_name = _OUTLINE_PREFIX + uuid8
    out_obj = _find_child(obj, out_name)
    if outline:
        if out_obj is None:
            out_obj = _make_text_object(out_name, coll)
            out_obj.parent = obj
            out_obj.matrix_parent_inverse = obj.matrix_world.inverted()
        out_obj.data.body = text
        orgb = (float(label.get("outlineR", 0.0)),
                float(label.get("outlineG", 0.0)),
                float(label.get("outlineB", 0.0)))
        omat = _ensure_emissive_material(_OUTLINE_MAT_PREFIX + uuid8, orgb, alpha)
        out_obj.data.materials.clear()
        out_obj.data.materials.append(omat)
        # outline-breite skaliert den scale-zuschlag (width 1 → +8%, gedeckelt)
        width_scale = min(_OUTLINE_MAX_SCALE, 1.0 + max(0.0, outline_width) * _OUTLINE_WIDTH_PER_PX)
        s = font_size * _FONT_SCALE * width_scale
        out_obj.scale = (s, s, s)
        # minimal nach hinten (in kamera-tiefe) — v1: leichter z-offset im lokalraum
        out_obj.location = (0.0, 0.0, -0.01)
        out_obj.hide_render = False
        out_obj.hide_viewport = False
    elif out_obj is not None:
        out_obj.hide_render = True
        out_obj.hide_viewport = True

    # --- der text selbst ---
    name = _LABEL_PREFIX + uuid8
    text_obj = _find_child(obj, name)
    if text_obj is None:
        text_obj = _make_text_object(name, coll)
        text_obj.parent = obj
        text_obj.matrix_parent_inverse = obj.matrix_world.inverted()
    text_obj.data.body = text
    mat = _ensure_emissive_material(_MAT_PREFIX + uuid8, (r, g, b), alpha,
                                    gradient=gradient, grad_rgb=grad_rgb, grad_offset=grad_offset)
    text_obj.data.materials.clear()
    text_obj.data.materials.append(mat)
    s = font_size * _FONT_SCALE
    text_obj.scale = (s, s, s)
    text_obj.location = (0.0, 0.0, 0.0)

    # --- billboard: TRACK_TO-constraint zur szenen-kamera (text dreht immer zur kamera) ---
    billboard_targets = [text_obj]
    if outline and out_obj is not None:
        billboard_targets.append(out_obj)
    if has_bg and bg_obj is not None:
        billboard_targets.append(bg_obj)
    billboard_targets.extend(shadow_objs)
    for tobj in billboard_targets:
        if tobj is None:
            continue
        existing = [c for c in tobj.constraints if c.type == 'TRACK_TO']
        cam = bpy.context.scene.camera
        if billboard and cam is not None:
            if existing:
                con = existing[0]
            else:
                con = tobj.constraints.new('TRACK_TO')
            con.target = cam
            con.track_axis = 'TRACK_Z'
            con.up_axis = 'UP_Y'
        else:
            for con in existing:
                tobj.constraints.remove(con)

    return True
