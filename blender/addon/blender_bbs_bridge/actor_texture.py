"""Phase T (+ nische-N-swap 2026-07-18): BBS TEXTURE-SWAP / CROSSFADE + PBR-intensity als Blender-nodes.

Der BBS-form-`texture`-kanal ist eine folge von textur-Links (jeder keyframe = EINE textur). BBS
blendet zur laufzeit zwischen zwei benachbarten keyframes (`FormProperties.applyTextureBlend`:
from = keyframe a, to = keyframe b, factor = segment-position 0..1). Java (`TextureExporter`) schickt
das als:

    properties.texture = {
        "keys":  [[tick, "source:path", interp], ...],   # geordnete textur-Links (ALLE keys)
        "blend": [{tick,value,interp,lx,ly,rx,ry}, ...],  # 0..1 crossfade-faktor (subkurve)
    }
    properties.pbrNormal   = [ {tick,value,...}, ... ]    # ModelForm.pbrNormalIntensity 0..4
    properties.pbrSpecular = [ {tick,value,...}, ... ]    # ModelForm.pbrSpecularIntensity 0..4

N-SWAP (nische 2, 2026-07-18): der kanal transportiert ALLE keys. Bei GENAU 2 keys (from/to) ist es
ein einzel-crossfade (ein BBS_TexBlend-Mix). Bei N>2 keys bauen wir eine KETTE aus N Image-Texture-
nodes + (N-1) Mix-nodes: Mix_1 blendet tex0→tex1, Mix_2 blendet (Mix_1.Result)→tex2, … Mix_(N-1)
→tex(N-1). Jeder Mix trägt eine EIGENE segment-faktor-F-curve, aus den keyframe-TICKS abgeleitet:
Mix_i ist 0 vor tick[i-1], rampt 0→1 zwischen tick[i-1] und tick[i], bleibt 1 danach — das gibt
den sequenziellen A→B→C→…-verlauf, den BBS zwischen benachbarten keyframes fährt. Das ergebnis des
letzten Mix speist die tint-kette (base-color-quelle) wie beim einzel-crossfade.

MUSTER (v1 color, exakt gespiegelt, Regel 6 "nichts neues erfinden"):
- `_ensure_texture_nodes`/`_ensure_texture_chain` WIRE-IDEMPOTENT (node bei bedarf, links IMMER
  sicherstellen — die base.copy()-falle von actor_material gilt hier genauso).
- `apply_texture_channels` keyframet die Mix-Factor(en) via actor_material._key_socket_component +
  _apply_interp (EIN keyframe-parser fuers ganze addon, inkl. der array_index/data_path-scope-falle).

FALLE (dokumentierte grenze, Regel 0 — flaggen statt faken): der textur-swap transportiert den
ASSET-PFAD. Ist das PNG in Blender nicht aufloesbar (weder unter BBS_ASSETS_DIR noch in der
client-jar), bleibt der Image-Texture-node OHNE bild + ein log — kein throw, der import laeuft
weiter. Der crossfade-verlauf zwischen den keyframes folgt der `blend`-subkurve (2-key) bzw. den
segment-faktoren (N-key); die per-segment-interp der N-key-kette ist v1 LINEAR (die hermite-handles
der `blend`-subkurve werden im N-key-fall nicht pro segment rekonstruiert — dokumentierte grenze).

PBR-NORMAL-MAP-TEXTUR (L7, 2026-07-19 — die offene grenze aus Phase T geschlossen):
Bis hierher transportierte pbrNormal nur die Strength-KURVE (F-curve auf BBS_NormalMap.Strength),
aber die normal-map-TEXTUR (das relief-bild) selbst reiste nicht — der Normal-Map-node hatte keine
Color-quelle, also blieb die oberflaeche flach egal was Strength tat. Jetzt laden wir das relief-bild.

Wie BBS die normal-map aufloest (GROUND TRUTH, nicht geraten): `IrisTextureWrapperLoader.createPrefixedCopy`
leitet den normal-map-pfad KONVENTIONELL aus dem base-textur-pfad ab — extension weg, etwaiges
`_n`/`_s`-suffix normalisieren, dann `_n.png` anhaengen (specular = `_s.png`). Das ist die
LabPBR/Iris-resource-pack-konvention. Es gibt KEIN separates normal-map-Link-feld in ModelForm
(`pbrNormalIntensity` ist nur ein ValueFloat 0..4) — die textur wird IMMER aus dem base-pfad
abgeleitet. Darum braucht der java-exporter KEINE aenderung: der base-textur-pfad erreicht das addon
schon (swap-`keys` bzw. die base-color-image die bobj_import geladen hat), wir spiegeln nur
BBS' `createPrefixedCopy` hier (`_normal_map_png`).

ENCODING (gemessen an `IrisPBRTextureProcessor.processNormalPixels`): BBS liest STANDARD
tangent-space RGB-normalmaps — `n = rgb*2-1` ueber ALLE DREI kanaele (kein LabPBR-2-kanal-encoding
mit rekonstruiertem Z). Darum ist der Blender-standardweg exakt richtig: bild NON-COLOR laden →
Normal-Map-node → BSDF.Normal. KEIN decode noetig. (Die pbrNormalIntensity-KURVE landet auf
Normal-Map.Strength und steuert die staerke — java skaliert damit nx/ny, wir mit Strength: gleiche
wirkung, "flacher/tiefer".)

GRENZE (Regel 0): findet sich keine `_n.png` neben der base-textur (der normalfall ohne LabPBR-pack),
bleibt der Normal-Map-node OHNE Color-bild — die Strength-kurve reist weiter (harmlos, kein relief),
1× geloggt. LabPBR-spezifika (der 2-kanal-encoding-fall) bauen wir NICHT nach: BBS selbst dekodiert
ihn nicht (processNormalPixels liest RGB direkt), also gibt es hier nichts zu dekodieren.
"""
import os

import bpy

from . import actor_material
from . import state


# node-namen (stabil, zum wiederfinden — praefix wie BBS_Tint/BBS_Glow)
_TEX_FROM_NODE = "BBS_TexFrom"
_TEX_TO_NODE = "BBS_TexTo"
_TEX_BLEND_NODE = "BBS_TexBlend"
_NORMAL_MAP_NODE = "BBS_NormalMap"
_NORMAL_TEX_NODE = "BBS_NormalTex"   # Image-Texture-node (NON-COLOR) der das `_n.png`-relief traegt
# N-swap-kette: pro key ein Image-node BBS_TexN_<i>, pro segment ein Mix-node BBS_TexMix_<i>
_TEX_N_NODE = "BBS_TexN_"
_TEX_MIX_NODE = "BBS_TexMix_"


def has_texture_channels(replay):
    """Traegt der replay einen textur-swap ODER eine pbr-intensitaet? (Phase T.)"""
    props = replay.get("properties") if isinstance(replay, dict) else None
    if not isinstance(props, dict):
        return False
    if isinstance(props.get('formTexture'), str) and props['formTexture']:
        return True
    tex = props.get("texture")
    if isinstance(tex, dict):
        keys = tex.get("keys")
        blend = tex.get("blend")
        if isinstance(keys, (list, tuple)) and len(keys) >= 1 and \
           isinstance(blend, (list, tuple)) and len(blend) >= 1:
            return True
    if isinstance(props.get("pbrNormal"), (list, tuple)) and props["pbrNormal"]:
        return True
    if isinstance(props.get("pbrSpecular"), (list, tuple)) and props["pbrSpecular"]:
        return True
    return False


def _resolve_texture_png(link):
    """Ein BBS-Link-string ("source:path") -> lokaler PNG-pfad, oder None (tolerant).

    ZWEI quellen, in reihenfolge: (1) BBS-asset unter state.BBS_ASSETS_DIR (wie
    bobj_import._resolve_bbs_link — der 'assets'-source IST dieses verzeichnis); (2) vanilla-textur
    aus der client-jar via HB-1s item_assets-loader (fuer minecraft:-texturen). Wirft nie."""
    if not isinstance(link, str) or not link.strip():
        return None
    rel = link.strip().replace("\\", "/")
    head, sep, tail = rel.partition(":")
    src = head if (sep and "/" not in head and len(head) > 1) else None
    path = tail if src is not None else rel
    path = path.lstrip("/")
    if not path:
        return None

    # (2) vanilla client-jar: "minecraft:textures/..." oder "minecraft:block/foo" -> jar-lookup
    if src == "minecraft":
        inner = path
        if not inner.startswith("textures/"):
            inner = "textures/" + inner
        if not inner.endswith(".png"):
            inner = inner + ".png"
        try:
            from . import item_assets
            p = item_assets.extract_png_to_cache("assets/minecraft/" + inner)
            if p and os.path.isfile(p):
                return p
        except Exception as e:  # noqa: BLE001 — asset-quelle darf den import nie killen
            print("[bbs-bridge] texture: vanilla-lookup fehlgeschlagen (%s): %s" % (link, e))

    # (1) BBS-asset unter BBS_ASSETS_DIR (traversal-guard wie bobj_import)
    root = os.path.abspath(state.BBS_ASSETS_DIR)
    p = os.path.abspath(os.path.join(root, path))
    try:
        if os.path.commonpath([p, root]) != root:
            return None
    except ValueError:
        return None
    if os.path.isfile(p):
        return p
    if src in (None, 'assets'):
        from .bobj_import import _packaged_bbs_texture
        return _packaged_bbs_texture(link)
    return None


def _load_image(png_path):
    """PNG -> Blender-image-datablock (check_existing = EIN datablock je PNG, kein .001-muell).
    None wenn pfad fehlt/laden scheitert. MC-look: Closest setzt der VERBRAUCHENDE tex-node."""
    if not png_path or not os.path.isfile(png_path):
        return None
    try:
        return bpy.data.images.load(png_path, check_existing=True)
    except RuntimeError as e:
        print("[bbs-bridge] texture: image-load fehlgeschlagen (%s): %s" % (png_path, e))
        return None


def _load_noncolor_image(png_path):
    """Wie _load_image, aber setzt das image auf NON-COLOR — PFLICHT fuer normal-maps: als sRGB
    geladen wuerden die kanaele gamma-verzerrt und die normalen falsch (n=rgb*2-1 braucht den rohen
    puffer). Wirft nie (colorspace-set ist tolerant: ein placeholder-image hat colorspace-name ""
    und wirft beim schreiben — world_anim-falle; wir fangen es)."""
    img = _load_image(png_path)
    if img is None:
        return None
    try:
        if img.colorspace_settings.name != 'Non-Color':
            img.colorspace_settings.name = 'Non-Color'
    except (TypeError, ValueError) as e:  # placeholder/undecodierbares PNG (colorspace "")
        print("[bbs-bridge] texture: normal-map colorspace-set fehlgeschlagen (%s): %s" % (png_path, e))
    return img


def _normal_map_png(base_png):
    """Aus einem base-textur-PNG-pfad den `_n.png`-normal-map-pfad ableiten — SPIEGELT BBS'
    `IrisTextureWrapperLoader.createPrefixedCopy` (ground truth): extension weg, etwaiges bereits
    vorhandenes `_n`/`_s`-suffix normalisieren, dann `_n.png` anhaengen. Gibt den pfad ODER None
    (kein base-pfad / datei existiert nicht neben der base-textur). Wirft nie.

    Beispiel: .../steve/steve.png -> .../steve/steve_n.png ; .../foo_s.png -> .../foo_n.png."""
    if not base_png or not isinstance(base_png, str):
        return None
    d = os.path.dirname(base_png)
    stem = os.path.splitext(os.path.basename(base_png))[0]
    # bereits suffigiert (user hat *_n/_s.png als base gepickt) -> auf albedo-basis zurueck (wie BBS)
    if stem.endswith("_n") or stem.endswith("_s"):
        stem = stem[:-2]
    cand = os.path.join(d, stem + "_n.png")
    return cand if os.path.isfile(cand) else None


def _base_color_png(nt):
    """Den on-disk-pfad der BASE-COLOR-textur des materials finden — quelle fuer `_normal_map_png`.
    Reihenfolge: (1) swap-from-/erster-kettenglied-node (BBS_TexFrom / BBS_TexN_0), (2) sonst der
    erste TEX_IMAGE-node MIT bild (statischer BOBJ-skin, von bobj_import geladen). Gibt den absoluten
    PNG-pfad (aus image.filepath) oder None. Wirft nie."""
    def _fp(node):
        img = getattr(node, "image", None)
        if img is None:
            return None
        try:
            fp = bpy.path.abspath(img.filepath) if img.filepath else None
        except (AttributeError, RuntimeError):
            fp = None
        return fp if (fp and os.path.isfile(fp)) else None

    for name in (_TEX_FROM_NODE, _TEX_N_NODE + "0"):
        node = nt.nodes.get(name)
        if node is not None:
            fp = _fp(node)
            if fp:
                return fp
    for node in nt.nodes:
        if node.type == 'TEX_IMAGE' and not node.name.startswith(("BBS_Tex", _NORMAL_MAP_NODE)):
            fp = _fp(node)
            if fp:
                return fp
    return None


def _texture_links(tex):
    """Aus dem `keys`-array die geordnete liste von (tick, Link-string) ziehen — ALLE keys.
    Gibt eine liste [(tick_float, link_str_or_None), ...] in keyframe-reihenfolge."""
    keys = tex.get("keys") if isinstance(tex, dict) else None
    if not isinstance(keys, (list, tuple)) or not keys:
        return []
    out = []
    for entry in keys:
        if isinstance(entry, (list, tuple)) and len(entry) >= 2:
            try:
                tick = float(entry[0])
            except (TypeError, ValueError):
                tick = 0.0
            v = entry[1]
            link = v if (isinstance(v, str) and v.strip()) else None
            out.append((tick, link))
    return out


def _texture_endpoints(tex):
    """Aus dem `keys`-array die from- und to-Link-strings ziehen (erster + letzter key).
    v1-crossfade zwischen erstem und letztem key (nur der 2-key-pfad). Gibt (from_link, to_link)."""
    links = _texture_links(tex)
    if not links:
        return None, None
    from_link = links[0][1]
    to_link = links[-1][1] if len(links) > 1 else None
    return from_link, to_link


def _clear_nswap_chain(nt):
    """Etwaige N-swap-kette (BBS_TexN_*/BBS_TexMix_*) aus einem tree entfernen (idempotenter
    wechsel N→2 keys). Wirft nie."""
    for node in list(nt.nodes):
        if node.name.startswith(_TEX_N_NODE) or node.name.startswith(_TEX_MIX_NODE):
            try:
                nt.nodes.remove(node)
            except (RuntimeError, ReferenceError):
                pass


def _ensure_texture_nodes(mat, from_img, to_img):
    """Zwei Image-Texture-nodes (from/to) + `BBS_TexBlend`-Mix (RGBA MIX) einbauen und den
    Mix-Result als base-color-QUELLE in actor_materials tint-node (BBS_Tint.inputs[6]=A) speisen.
    Zusaetzlich einen Normal-Map-node fuer die pbr-normal-strength. IDEMPOTENT + WIRE-IDEMPOTENT:
    node bei bedarf, links IMMER sicherstellen (base.copy()-falle wie in actor_material). Wirft nie
    im aufrufer (der ruft es unter try). Gibt None. (Der 2-key/einzel-crossfade-pfad.)"""
    if not mat.use_nodes:
        mat.use_nodes = True
    nt = mat.node_tree
    # falls vorher eine N-swap-kette lief → weg (idempotenter wechsel)
    _clear_nswap_chain(nt)
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tint = nt.nodes.get(actor_material._TINT_NODE)
    if bsdf is None or tint is None:
        # ohne die tint-kette (actor_material._ensure_nodes) gibt es keine base-color-quelle
        # zum umhaengen — dann ist texture-swap ein no-op (dokumentierte reihenfolge-abhaengigkeit).
        return

    # --- from-textur ---
    tex_from = nt.nodes.get(_TEX_FROM_NODE)
    if tex_from is None:
        tex_from = nt.nodes.new("ShaderNodeTexImage")
        tex_from.name = _TEX_FROM_NODE
        tex_from.interpolation = 'Closest'   # MC-look (pixelig), kein resample
    if from_img is not None:
        tex_from.image = from_img

    # --- to-textur ---
    tex_to = nt.nodes.get(_TEX_TO_NODE)
    if tex_to is None:
        tex_to = nt.nodes.new("ShaderNodeTexImage")
        tex_to.name = _TEX_TO_NODE
        tex_to.interpolation = 'Closest'
    if to_img is not None:
        tex_to.image = to_img

    # --- crossfade-Mix (RGBA MIX): A=from.Color, B=to.Color, Factor keyframt der apply-pfad ---
    blend = nt.nodes.get(_TEX_BLEND_NODE)
    if blend is None:
        blend = nt.nodes.new("ShaderNodeMix")
        blend.name = _TEX_BLEND_NODE
        blend.data_type = 'RGBA'
        blend.blend_type = 'MIX'
        blend.inputs[0].default_value = 0.0                    # Factor (0 = voll from), keyframt spaeter
        blend.inputs[6].default_value = (1.0, 1.0, 1.0, 1.0)   # A = from-farbe (start weiss = no-op)
        blend.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)   # B = to-farbe
    # A (inputs[6]) <- from-tex.Color, B (inputs[7]) <- to-tex.Color: links IMMER sicherstellen
    if "Color" in tex_from.outputs and not any(
            l.to_socket == blend.inputs[6] and l.from_socket == tex_from.outputs["Color"]
            for l in nt.links):
        nt.links.new(tex_from.outputs["Color"], blend.inputs[6])
    if "Color" in tex_to.outputs and not any(
            l.to_socket == blend.inputs[7] and l.from_socket == tex_to.outputs["Color"]
            for l in nt.links):
        nt.links.new(tex_to.outputs["Color"], blend.inputs[7])
    # Result (outputs[2]) -> tint.inputs[6] (A = base-color-quelle): IMMER sicherstellen. Das haengt
    # die crossfade-farbe VOR den tint/color-mix — die color/glow-kette wirkt danach unveraendert.
    _wire_source_to_tint(nt, tint, blend.outputs[2])

    _ensure_normal_map(nt, bsdf)


def _ensure_texture_chain(mat, images):
    """N-swap-kette: N Image-Texture-nodes (BBS_TexN_<i>) + (N-1) Mix-nodes (BBS_TexMix_<i>) so
    dass Mix_1 tex0→tex1, Mix_2 (Mix_1)→tex2, … blendet. Result des letzten Mix speist die tint-
    kette. `images` = liste von (Blender-image-oder-None) in keyframe-reihenfolge (len == N, N>=2).
    IDEMPOTENT + WIRE-IDEMPOTENT. Wirft nie im aufrufer. Gibt die liste der Mix-nodes (segment i
    = mix[i], blendet auf key i+1) oder [] wenn die tint-kette fehlt."""
    if not mat.use_nodes:
        mat.use_nodes = True
    nt = mat.node_tree
    # etwaigen 2-key-crossfade-node weg (idempotenter wechsel 2→N)
    for stale in (_TEX_FROM_NODE, _TEX_TO_NODE, _TEX_BLEND_NODE):
        node = nt.nodes.get(stale)
        if node is not None:
            try:
                nt.nodes.remove(node)
            except (RuntimeError, ReferenceError):
                pass
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tint = nt.nodes.get(actor_material._TINT_NODE)
    if bsdf is None or tint is None:
        return []

    n = len(images)
    # Image-nodes je key
    tex_nodes = []
    for i in range(n):
        name = _TEX_N_NODE + str(i)
        node = nt.nodes.get(name)
        if node is None:
            node = nt.nodes.new("ShaderNodeTexImage")
            node.name = name
            node.interpolation = 'Closest'
        if images[i] is not None:
            node.image = images[i]
        tex_nodes.append(node)

    # Mix-kette: mix[i] blendet (vorheriger result / tex0) -> tex(i+1)
    mix_nodes = []
    prev_output = tex_nodes[0].outputs["Color"]
    for i in range(n - 1):
        name = _TEX_MIX_NODE + str(i)
        mix = nt.nodes.get(name)
        if mix is None:
            mix = nt.nodes.new("ShaderNodeMix")
            mix.name = name
            mix.data_type = 'RGBA'
            mix.blend_type = 'MIX'
            mix.inputs[0].default_value = 0.0                     # Factor, keyframt spaeter pro segment
            mix.inputs[6].default_value = (1.0, 1.0, 1.0, 1.0)
            mix.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)
        # A (inputs[6]) <- prev_output (tex0 fuer mix0, sonst mix(i-1).Result)
        if not any(l.to_socket == mix.inputs[6] and l.from_socket == prev_output for l in nt.links):
            for l in list(nt.links):
                if l.to_socket == mix.inputs[6]:
                    nt.links.remove(l)
            nt.links.new(prev_output, mix.inputs[6])
        # B (inputs[7]) <- tex(i+1).Color
        b_src = tex_nodes[i + 1].outputs["Color"]
        if not any(l.to_socket == mix.inputs[7] and l.from_socket == b_src for l in nt.links):
            for l in list(nt.links):
                if l.to_socket == mix.inputs[7]:
                    nt.links.remove(l)
            nt.links.new(b_src, mix.inputs[7])
        mix_nodes.append(mix)
        prev_output = mix.outputs[2]

    # letzter Mix-Result -> tint.inputs[6]
    _wire_source_to_tint(nt, tint, prev_output)

    _ensure_normal_map(nt, bsdf)
    return mix_nodes


def _wire_source_to_tint(nt, tint, source_output):
    """`source_output` (irgendein RGBA-output) als base-color-quelle in tint.inputs[6] haengen.
    Loest etwaigen bestehenden link auf tint.inputs[6] (alte textur/kette) und legt den neuen.
    Link-guarded/idempotent."""
    if any(l.to_socket == tint.inputs[6] and l.from_socket == source_output for l in nt.links):
        return
    for l in list(nt.links):
        if l.to_socket == tint.inputs[6]:
            nt.links.remove(l)
    nt.links.new(source_output, tint.inputs[6])


def _ensure_normal_map(nt, bsdf):
    """pbr-normal: Normal-Map-node (Strength = pbrNormal, keyframt der apply-pfad) + das RELIEF-BILD
    (L7): ein NON-COLOR Image-Texture-node (`BBS_NormalTex`) traegt die `_n.png` neben der
    base-textur und speist Normal-Map.Color. WIRE-IDEMPOTENT (node bei bedarf, links IMMER
    sicherstellen — die base.copy()-falle gilt hier wie fuer tint/glow).

    Findet sich keine `_n.png` (normalfall ohne LabPBR-pack), bleibt der Color-slot leer +
    1× log — die Strength-kurve reist trotzdem (harmlos flach, dokumentierte grenze Regel 0)."""
    if bsdf is None or "Normal" not in bsdf.inputs:
        return
    nmap = nt.nodes.get(_NORMAL_MAP_NODE)
    if nmap is None:
        nmap = nt.nodes.new("ShaderNodeNormalMap")
        nmap.name = _NORMAL_MAP_NODE
        nmap.inputs["Strength"].default_value = 1.0

    # --- relief-bild: `_n.png` neben der base-textur (NON-COLOR!) -> Normal-Map.Color ---
    ntex = nt.nodes.get(_NORMAL_TEX_NODE)
    if ntex is None:
        ntex = nt.nodes.new("ShaderNodeTexImage")
        ntex.name = _NORMAL_TEX_NODE
        ntex.interpolation = 'Closest'   # MC-look, kein resample (wie base-textur)
    if ntex.image is None:
        base_png = _base_color_png(nt)
        nmap_png = _normal_map_png(base_png) if base_png else None
        if nmap_png:
            ntex.image = _load_noncolor_image(nmap_png)
        elif base_png:
            print("[bbs-bridge] texture: keine normal-map (_n.png) neben %r — Normal-Map ohne relief, "
                  "nur Strength-kurve (dokumentierte grenze, kein LabPBR-pack)" % (base_png,))
    # colorspace auch bei einer VORHER (evtl. als sRGB) geladenen image absichern (idempotent)
    if ntex.image is not None:
        try:
            if ntex.image.colorspace_settings.name != 'Non-Color':
                ntex.image.colorspace_settings.name = 'Non-Color'
        except (TypeError, ValueError):
            pass
        # Color (nur wenn ein relief-bild da ist — sonst wuerde ein flaches default-bild die
        # normalen ins nichts ziehen; ohne Color rechnet der node die geometrie-normale durch)
        if "Color" in ntex.outputs and not any(
                l.to_socket == nmap.inputs["Color"] and l.from_socket == ntex.outputs["Color"]
                for l in nt.links):
            nt.links.new(ntex.outputs["Color"], nmap.inputs["Color"])

    if not any(l.to_socket == bsdf.inputs["Normal"] and l.from_socket == nmap.outputs["Normal"]
               for l in nt.links):
        nt.links.new(nmap.outputs["Normal"], bsdf.inputs["Normal"])


def _segment_factor_keyframes(links, seg_index):
    """Fuer segment `seg_index` (Mix zwischen key seg_index und key seg_index+1) eine faktor-
    keyframe-liste bauen: 0 bis tick[seg_index], ramp 0→1 zwischen tick[seg_index] und
    tick[seg_index+1], 1 danach. Gibt keyframe-dicts im `_key_socket_component`-format
    ({tick,value,interp}). LINEAR (v1: per-segment hermite-handles werden nicht rekonstruiert)."""
    t0 = links[seg_index][0]
    t1 = links[seg_index + 1][0]
    if t1 <= t0:
        # entartetes segment (gleiche/rueckwaerts-ticks) → harter cut bei t1
        return [{"tick": t1, "value": 1.0, "interp": "constant"}]
    kfs = [{"tick": t0, "value": 0.0, "interp": "linear"},
           {"tick": t1, "value": 1.0, "interp": "linear"}]
    return kfs


def apply_texture_channels(obj, replay, tick_scale):
    """Die texture-swap/crossfade + pbr-intensitaet als Blender-nodes + F-curves (Phase T):
      - 2 keys:  texture.keys[0]/[-1] -> from/to Image-nodes, texture.blend -> BBS_TexBlend.Factor
      - N keys:  texture.keys[*] -> BBS_TexN_<i> Image-nodes + BBS_TexMix_<i>-kette, pro segment
                 eine aus den keyframe-ticks abgeleitete faktor-F-curve
      - pbrNormal            -> BBS_NormalMap.inputs["Strength"] (skalar)
      - pbrSpecular          -> bsdf.inputs["Specular IOR Level"] (skalar)
    Teilt die PER-ACTOR material-kopie mit color/glow (KEINE zweite kopie — ensure_owned_material ist
    idempotent). Gibt die zahl geschriebener keys. Wirft nie (ein kaputter kanal darf den import nicht
    killen)."""
    if not has_texture_channels(replay):
        return 0
    props = replay.get("properties", {})
    if not isinstance(props, dict):
        return 0

    mat = actor_material.ensure_owned_material(obj, actor_material._uuid8(replay))
    if mat is None:
        return 0

    tex = props.get("texture") if isinstance(props.get("texture"), dict) else {}
    links = _texture_links(tex)
    nt = mat.node_tree
    n = 0

    static_link = props.get("formTexture")
    if isinstance(static_link, str) and static_link:
        image = _load_image(_resolve_texture_png(static_link))
        if image is None:
            obj['bbs_texture_error'] = 'Static form texture missing: ' + static_link
            print('[bbs-bridge] texture: statische Form-Textur fehlt: %s' % static_link)
        else:
            base = next((node for node in nt.nodes if node.type == 'TEX_IMAGE'
                         and not node.name.startswith('BBS_Tex')
                         and node.name != _NORMAL_TEX_NODE), None)
            if base is None:
                obj['bbs_texture_error'] = 'No base image node for ' + static_link
                print('[bbs-bridge] texture: kein Basis-Bildknoten fuer %s' % static_link)
            else:
                base.image = image
                base.interpolation = 'Closest'
                if 'bbs_texture_error' in obj:
                    del obj['bbs_texture_error']

    if len(links) > 2:
        # --- N-SWAP-KETTE ---
        images = []
        for tick, link in links:
            img = _load_image(_resolve_texture_png(link)) if link else None
            if link and img is None:
                print("[bbs-bridge] texture: N-swap-textur nicht aufloesbar (%r) — node ohne bild "
                      "(dokumentierte grenze)" % (link,))
            images.append(img)
        mix_nodes = _ensure_texture_chain(mat, images)
        # pro segment die faktor-kurve legen (segment i steuert mix_nodes[i])
        for i, mix in enumerate(mix_nodes):
            seg_kfs = _segment_factor_keyframes(links, i)
            n += actor_material._key_socket_component(mix.inputs[0], seg_kfs, 0, tick_scale)
    elif len(links) == 2:
        # --- 2-KEY EINZEL-CROSSFADE (unveraendert) ---
        from_link, to_link = _texture_endpoints(tex)
        from_img = _load_image(_resolve_texture_png(from_link)) if from_link else None
        to_img = _load_image(_resolve_texture_png(to_link)) if to_link else None
        if from_link and from_img is None:
            print("[bbs-bridge] texture: from-textur nicht aufloesbar (%r) — node ohne bild "
                  "(dokumentierte grenze)" % (from_link,))
        if to_link and to_img is None and to_link != from_link:
            print("[bbs-bridge] texture: to-textur nicht aufloesbar (%r) — node ohne bild "
                  "(dokumentierte grenze)" % (to_link,))
        _ensure_texture_nodes(mat, from_img, to_img)
        blend_node = nt.nodes.get(_TEX_BLEND_NODE)
        blend_kfs = tex.get("blend")
        if blend_node is not None and isinstance(blend_kfs, (list, tuple)):
            n += actor_material._key_socket_component(blend_node.inputs[0], blend_kfs, 0, tick_scale)
    # len(links) <= 1: KEIN swap (0 = nur pbr, 1 = statische einzeltextur) — NICHT die base-color
    # ueber einen leeren crossfade-Mix umhaengen (das wuerde den skin weissen). Die normal-map/pbr
    # unten laeuft trotzdem: `_ensure_normal_map` baut den node direkt auf der bestehenden base-textur.

    # --- pbr (beide pfade teilen die BSDF/Normal-Map-nodes) ---
    bsdf = next((nd for nd in nt.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
    # normal-map-node/relief SICHERSTELLEN — auch ohne swap (pbrNormal ohne textur-swap ist gueltig;
    # die swap-pfade riefen es schon, der no-swap-pfad braucht es explizit).
    _ensure_normal_map(nt, bsdf)
    nmap = nt.nodes.get(_NORMAL_MAP_NODE)

    pbr_normal = props.get("pbrNormal")
    if nmap is not None and isinstance(pbr_normal, (list, tuple)):
        n += actor_material._key_socket_component(nmap.inputs["Strength"], pbr_normal, 0, tick_scale)

    pbr_specular = props.get("pbrSpecular")
    if bsdf is not None and isinstance(pbr_specular, (list, tuple)):
        spec_socket = None
        for name in ("Specular IOR Level", "Specular"):
            if name in bsdf.inputs:
                spec_socket = bsdf.inputs[name]
                break
        if spec_socket is not None:
            n += actor_material._key_socket_component(spec_socket, pbr_specular, 0, tick_scale)

    return n
