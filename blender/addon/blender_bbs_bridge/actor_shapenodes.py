"""Phase S — ShapeForm node-graph -> Blender shader-node-tree (~1:1 topologie-uebersetzung).

Ein BBS `ShapeForm` traegt einen prozeduralen CPU-node-graph (24 node-typen in
`forms/forms/shape/nodes/*`, evaluator `ShapeGraphEvaluator`). Der java `ShapeGraphExporter`
serialisiert die TOPOLOGIE (nicht pro-frame-werte — der graph ist statisch, `TimeNode` animiert
Blender-seitig via #frame-driver) unter `properties.shapeGraph`:

    {
      "nodes": [{"id":2,"type":"noise","seed":7,"x":..,"y":..}, {"id":1,"type":"output"}, ...],
      "connections": [{"outId":2,"outIdx":0,"inId":1,"inIdx":0}, ...],
      "output": {"id":1,"result":2,"color":3,"material":-1}
    }

Diese datei baut den entsprechenden Blender-shader-node-tree nach: pro BBS-node ein Blender-node
(mapping-tabelle `_NODE_BUILDERS`), verbindungen nachziehen, das ergebnis an die Base Color des
per-actor-materials haengen. Das ist mehr TOPOLOGIE als F-curve (keine keyframes hier).

MAPPING-EHRLICHKEIT (Regel 0 — kein stiller skip):
- 1:1-bare typen (noise/voronoi/math/vector_math/mix_color/combine_color/split_color/color/value/
  clamp/remap/invert/coordinate/output) werden gebaut.
- java flaggt bereits `skip:"stub"` (bump) / `skip:"render-only"` (iris_*) — die bauen wir NICHT,
  sie landen in `_flagged`.
- PHASE S / S2 hatten mehrere typen approximiert; PHASE S(L5, 2026-07-19) hat die machbaren
  PRAEZISIERT (gegen den java ShapeGraphEvaluator gemessen, nicht geraten):
    * smoothstep -> EXAKT (MapRange SMOOTHSTEP == BBS' t*t*(3-2t)), jetzt "built".
    * time       -> #frame-driver in SEKUNDEN (frame/fps), trifft BBS' wall-clock-zeit-skala.
    * flow_noise -> voll nachgebaut: noise(x*s, y*s - time*speed, z*s) + y*taper (scroll + taper),
                    single-octave (Detail=0) wie BBS' Perlin; jetzt "built".
    * coordinate -> TexCoord `Object` (objekt-lokal) statt `Generated`-0..1-box; BBS speist die
                    roh-mesh-position (±0.5), Object trifft das (Generated tat es nicht).
    * trigger    -> mode 0/1 (greater/less) EXAKT (Math GREATER_THAN/LESS_THAN), jetzt "built".
    * noise      -> Detail=0 (single-octave) fuers gleiche frequenz-bild wie BBS.
- ECHTE GRENZEN, ehrlich approximiert + geloggt (`_approximated`), NICHT still uebergangen:
    * math op 8 (Molang-custom-expression) — Molang ist eine ausdrucks-sprache, Blender hat keinen
      evaluator dafuer; ADD-passthrough als platzhalter. NICHT loesbar ohne Molang->node-compiler.
    * trigger mode 2/3/4 (equal/not_equal band, pulse) — |input-target|<range bzw. modulo-zeit-puls,
      kein statisches einzelknoten-aequivalent.
    * texture — bild-asset (link-string -> PNG) noch nicht aufgeloest -> node ohne bild + log.
- `comment` = reiner UI-node in BBS, kein evaluator-effekt -> still uebersprungen (kein datenverlust).

WHY eigenes modul (Regel 10, eine sache pro datei): der graph-bau hat nichts mit den color/glow/
paint-F-curves (`actor_material.py`) zu tun — eigenes lexikon-kapitel. Teilt aber die per-actor-
material-kopie mit actor_material (`ensure_owned_material`) und haengt seinen graph-output VOR die
tint-kette an die Base Color (der graph ERSETZT die textur-farbe, tint/glow bleiben obendrauf).

Blender-5.2-fallen (aus dem paket-dossier, nicht geraten):
- ShaderNodeMix (RGBA) hat DUPLIKAT-socket-namen -> index-zugriff: inputs[0]=Factor, inputs[6]=A,
  inputs[7]=B, outputs[2]=Result (wie actor_material).
- node-input auf ID-block-ebene adressieren; hier keine keyframes (statischer graph), also entfaellt
  die channelbag-falle — reine default_value/link-arbeit.
- Noise/Voronoi out-socket heisst `Factor`/`Distance` (NICHT `Fac`) in 5.2.

DISPLACEMENT (Phase S2, OutputNode.result -> GEOMETRY-NODES vertex-displacement):
Der OutputNode hat DREI eingaenge: input0=result=VERTEX-DISPLACEMENT, input1=color, input2=material.
color/material gehen als SHADER-node-tree an die Base Color (oben). Displacement ist ein ZWEITER,
GETRENNTER pfad: Blenders shader-nodes koennen KEINE geometrie verschieben — displacement muss ein
GEOMETRY-NODES-modifier auf dem mesh sein (`Set Position`-node). `apply_shape_displacement` baut die
node-gruppe `BBS_SG_Displace_<uuid8>` (`GeometryNodeSetPosition`, Offset = `Normal` × displacement-
wert via `ShaderNodeVectorMath`-SCALE) + haengt sie als NODES-modifier `BBS_SG_Displace` ans mesh.
Die nodes DIE IN output.result MUENDEN (der displacement-teilgraph) werden im geo-tree NACHGEBAUT —
`_build_node(geo=True)` baut fuer die shader-only-typen (coordinate/color/combine/split/invert) das
geo-node-aequivalent, der rest (noise/voronoi/math/vector_math/value/mix/clamp/remap/smoothstep) teilt
sich denselben builder (diese typen existieren in BEIDEN tree-arten, live geprueft :9976).

V1-GRENZEN (ehrlich, Regel 0 — gemessen im render-gate, nicht kaschiert):
- SKALAR-vs-VEKTOR-kollaps: BBS verdrahtet CoordinateNode PER KOMPONENTE (x=out0 -> noise.x=in0),
  Blenders Noise/Voronoi haben aber EINEN Vector-input. Ein einzelner skalar (coord.x) auf den
  Vector-socket wird von Blender auf alle 3 achsen gebroadcastet -> streifen-muster statt echtem
  3D-noise-feld (render-gate: variance sichtbar, aber gebändert). Volle per-achse-rekonstruktion
  (x/y/z -> CombineXYZ -> Vector) ist ein spaeterer schritt.
- VectorMathNode: BBS-skalar-eingaenge (ax..bz) kollabieren auf die Blender-Vector-sockets A/B
  (approximiert).
- DISPLACEMENT-GEO-GRENZEN: der displacement-teilgraph wird als geo-nodes nachgebaut, aber
  (a) `coordinate` im geo-tree = `GeometryNodeInputPosition` (OBJEKT-lokale vertex-position) —
      das TRIFFT BBS' domaene (der evaluator speist die roh-mesh-position, objekt-lokal, ±0.5),
      genau wie der shader-pfad seit L5 TexCoord `Object` nutzt; beide pfade sind jetzt im selben
      raum. u/v (out 3/4) fallen auf x/y der position zurueck (BBS liefert u/v==0 im mesh-pfad).
  (b) `color`/`combine_color`/`split_color` speisen displacement praktisch nie (displacement ist
      skalar) — im geo-tree gibt es KEIN `ShaderNodeRGB`; ein color-node der DOCH ins displacement
      muendet wird als konstant-`ShaderNodeValue` approximiert (nimmt die color-helligkeit), geloggt.
  (c) `texture`/`iris_*`/`bump` speisen im geo-tree nichts sinnvolles -> approximiert/geflaggt wie
      im shader-pfad. displacement-wert wird als SKALAR aus output-0 des feeders gelesen.
- NACH L5 noch approximiert + im report gezaehlt (echte grenzen, s. oben): Molang-MathNode (op 8),
  TriggerNode mode 2/3/4 (equal/not_equal/pulse), TextureNode-ohne-bild. FlowNoise/Smoothstep/Time/
  Trigger-0/1 sind jetzt "built" (praezise), coordinate nutzt objekt-raum (BBS-domaene).
"""
import bpy

try:
    from . import actor_material
except ImportError:  # pragma: no cover — headless/standalone import
    import actor_material

# stabiler prefix, damit wir unseren eigenen graph wiederfinden + rueckstandslos raeumen koennen.
_NODE_PREFIX = "BBS_SG_"
# displacement (Phase S2): geo-nodes-modifier + node-gruppen-namen (eigene, rueckstandslos raeumbar).
_DISP_MOD = "BBS_SG_Displace"          # NODES-modifier-name am mesh
_DISP_GROUP_PREFIX = "BBS_SG_Displace_"  # + uuid8 = node-gruppen-name


def has_shape_graph(replay):
    """Traegt der replay einen nicht-leeren shape-graph (properties.shapeGraph mit nodes)?"""
    if not isinstance(replay, dict):
        return False
    props = replay.get("properties")
    if not isinstance(props, dict):
        return False
    sg = props.get("shapeGraph")
    if not isinstance(sg, dict):
        return False
    nodes = sg.get("nodes")
    return isinstance(nodes, (list, tuple)) and len(nodes) > 0


# ---- ARGB-int (BBS ColorNode.c) -> Blender-RGBA (0..1, linear-erwartet vom socket) ----
def _argb_to_rgba(argb):
    """Signed 32-bit ARGB int -> (r,g,b,a) floats 0..1. BBS packt A<<24|R<<16|G<<8|B."""
    try:
        v = int(argb) & 0xFFFFFFFF
    except (TypeError, ValueError):
        return (1.0, 1.0, 1.0, 1.0)
    a = ((v >> 24) & 0xFF) / 255.0
    r = ((v >> 16) & 0xFF) / 255.0
    g = ((v >> 8) & 0xFF) / 255.0
    b = (v & 0xFF) / 255.0
    return (r, g, b, a)


# ---- MathNode.operation (int) -> Blender ShaderNodeMath.operation (enum) ----
# reihenfolge exakt aus ShapeGraphEvaluator: 0 add,1 sub,2 mul,3 div,4 mod,5 min,6 max,7 pow,8 custom
_MATH_OPS = {
    0: 'ADD', 1: 'SUBTRACT', 2: 'MULTIPLY', 3: 'DIVIDE', 4: 'MODULO',
    5: 'MINIMUM', 6: 'MAXIMUM', 7: 'POWER',
    # 8 = custom Molang-expression -> kein Blender-aequivalent, wird approximiert (ADD passthrough)
}

# ---- VectorMathNode.operation (int) -> Blender ShaderNodeVectorMath.operation ----
# aus ShapeGraphEvaluator: 0 add..22 tan
_VECTOR_MATH_OPS = {
    0: 'ADD', 1: 'SUBTRACT', 2: 'MULTIPLY', 3: 'DIVIDE', 4: 'CROSS_PRODUCT',
    5: 'PROJECT', 6: 'REFLECT', 7: 'DOT_PRODUCT', 8: 'DISTANCE', 9: 'LENGTH',
    10: 'SCALE', 11: 'NORMALIZE', 12: 'ABSOLUTE', 13: 'MINIMUM', 14: 'MAXIMUM',
    15: 'FLOOR', 16: 'CEIL', 17: 'FRACTION', 18: 'MODULO', 19: 'SNAP',
    20: 'SINE', 21: 'COSINE', 22: 'TANGENT',
}


# ============================================================================ #
#  Node-fabrik: pro BBS-typ ein builder, der EINEN Blender-node baut und
#  ein "port-tabellen"-dict {out_index: socket, in_index: socket} zurueckgibt,
#  damit `_wire` die verbindungen nach BBS-index nachziehen kann.
# ============================================================================ #

def _new(nt, kind, bbs_id, suffix=""):
    """Einen Blender-node anlegen UND ihn sofort mit unserem prefix benennen (auch helfer-nodes wie
    SeparateXYZ/CombineXYZ) — damit `_clear_graph` ALLES raeumt, kein leck. Wirft nie am namen."""
    n = nt.nodes.new(kind)
    try:
        n.name = _NODE_PREFIX + str(bbs_id) + suffix
    except Exception:  # noqa: BLE001
        pass
    return n


def _set_default(sockets, key, value):
    """Einen socket-default (input ODER output, adressiert per name-string oder int-index) setzen.
    Wirft nie (fehlender socket/enum-mismatch = still ueberspringen)."""
    try:
        sockets[key].default_value = value
    except (KeyError, IndexError, TypeError, AttributeError, RuntimeError):
        pass


def _build_node(nt, bbs, geo=False):
    """Einen BBS-node -> Blender-node. Gibt (node, out_sockets, in_sockets, status) zurueck.
    status in {"built","approximated","flagged","skipped"}. node kann None sein (skip).
    Alle erzeugten nodes (inkl. helfer) tragen den BBS_SG_-prefix (cleanup-idempotenz).

    `geo=True` -> die nodes werden in einem GEOMETRY-NODES-tree gebaut (displacement-pfad). Die
    meisten typen (noise/voronoi/math/vector_math/value/mix/clamp/remap/smoothstep/combine/split-XYZ)
    existieren in BEIDEN tree-arten und teilen sich diesen builder; NUR die shader-only-typen
    (coordinate=TexCoord, color=RGB, combine_color/split_color, invert=color-invert, texture) haben
    im geo-tree kein 1:1-aequivalent und bekommen hier ihre geo-variante (live geprueft :9976)."""
    t = bbs.get("type")
    nid = bbs.get("id")

    if bbs.get("skip"):
        # java hat es geflaggt (bump=stub, iris_*=render-only) -> NICHT bauen.
        return None, {}, {}, "flagged"

    if t == "comment":
        # reiner UI-node, kein evaluator-effekt -> still ueberspringen (kein datenverlust).
        return None, {}, {}, "skipped"

    if t == "output":
        # OutputNode ist KEIN echter Blender-node — result/color/material werden ueber das
        # payload-`output`-dict aufgeloest und in apply an die Base Color gehaengt. Kein node hier.
        return None, {}, {}, "skipped"

    if t == "value":
        n = _new(nt, "ShaderNodeValue", nid)
        try:
            n.outputs[0].default_value = float(bbs.get("val", 0.0))
        except (TypeError, ValueError):
            pass
        return n, {0: n.outputs[0]}, {}, "built"

    if t == "time":
        # TimeNode -> Value-node mit #frame-driver. BBS' `time` ist WALL-CLOCK-SEKUNDEN
        # (ShapeFormRenderer: `time = (System.currentTimeMillis() % 200000) / 1000F`, laeuft
        # 1.0 pro echter sekunde), NICHT ticks/frames. Der driver rechnet frame->sekunden mit
        # der scene-fps (frame N bei fps F = N/F sekunden), damit die zeit-SKALA von BBS getroffen
        # wird — ein roher `frame`-driver lief um den fps-faktor (24x) zu schnell.
        n = _new(nt, "ShaderNodeValue", nid)
        _add_frame_driver(n.outputs[0], seconds=True)
        return n, {0: n.outputs[0]}, {}, "built"

    if t == "color":
        r, g, b, a = _argb_to_rgba(bbs.get("c", -1))
        if geo:
            # geo-tree hat KEIN ShaderNodeRGB. Ein color-node der ins DISPLACEMENT muendet ist
            # ungewoehnlich (displacement ist skalar) -> als konstant-Value approximieren, wert =
            # helligkeit der farbe (luma). Geloggt vom aufrufer via status="approximated".
            n = _new(nt, "ShaderNodeValue", nid)
            try:
                n.outputs[0].default_value = 0.2126 * r + 0.7152 * g + 0.0722 * b
            except (TypeError, ValueError):
                pass
            return n, {0: n.outputs[0]}, {}, "approximated"
        n = _new(nt, "ShaderNodeRGB", nid)
        try:
            n.outputs[0].default_value = (r, g, b, a)
        except (TypeError, ValueError):
            pass
        return n, {0: n.outputs[0]}, {}, "built"

    if t == "coordinate":
        # CoordinateNode gibt x/y/z (out 0/1/2) + u/v (3/4).
        if geo:
            # geo-tree: KEIN ShaderNodeTexCoord ("Not a shader node tree", live geprueft). Quelle
            # ist die OBJEKT-lokale vertex-position (GeometryNodeInputPosition) -> SeparateXYZ.
            # ⚠️ das ist objekt-raum (nicht die shader-`Generated`-0..1-box) — noise-scale wirkt
            # in objekt-einheiten; u/v auf x/y (naeherung wie im shader-pfad). Dokumentierte grenze.
            pos = _new(nt, "GeometryNodeInputPosition", nid, "_pos")
            sep = _new(nt, "ShaderNodeSeparateXYZ", nid)
            nt.links.new(pos.outputs[0], sep.inputs[0])
            outs = {0: sep.outputs[0], 1: sep.outputs[1], 2: sep.outputs[2],
                    3: sep.outputs[0], 4: sep.outputs[1]}
            return sep, outs, {}, "built"
        # BBS-CoordinateNode gibt die OBJEKT-LOKALEN mesh-koordinaten zurueck (ShapeGraphEvaluator:
        # x/y/z == die roh-vertex-position, box-extents ±0.5) — NICHT eine Generated-0..1-box. Darum
        # der TexCoord-`Object`-output (objekt-raum), nicht `Generated` (bounding-box-normalisiert 0..1):
        # `Object` trifft die BBS-domaene, `Generated` haette den noise-scale um faktor ~1/groesse
        # verschoben + den ursprung nach (0.5,0.5,0.5) statt (0,0,0) gelegt. Deckt sich jetzt mit dem
        # displacement/geo-pfad (GeometryNodeInputPosition = ebenfalls objekt-lokal).
        tc = _new(nt, "ShaderNodeTexCoord", nid, "_tc")
        sep = _new(nt, "ShaderNodeSeparateXYZ", nid)
        nt.links.new(tc.outputs["Object"], sep.inputs[0])
        # u/v (3/4): auf x/y der objekt-position abbilden (naeherung — BBS liefert u/v==0 fuer den
        # mesh-pfad, x/y ist der brauchbarere fallback wie im geo-pfad). Dokumentierte grenze.
        outs = {0: sep.outputs[0], 1: sep.outputs[1], 2: sep.outputs[2],
                3: sep.outputs[0], 4: sep.outputs[1]}
        return sep, outs, {}, "built"

    if t == "math":
        n = _new(nt, "ShaderNodeMath", nid)
        op_i = int(bbs.get("op", 0) or 0)
        op = _MATH_OPS.get(op_i)
        status = "built"
        if op is None:  # op 8 = custom Molang -> approximieren
            op = 'ADD'
            status = "approximated"
        n.operation = op
        return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1]}, status

    if t == "vector_math":
        n = _new(nt, "ShaderNodeVectorMath", nid)
        op_i = int(bbs.get("op", 0) or 0)
        op = _VECTOR_MATH_OPS.get(op_i, 'ADD')
        n.operation = op
        # BBS-in: ax,ay,az,bx,by,bz (skalar-sockets 0..5). Zwei CombineXYZ-helfer bauen die beiden
        # Blender-Vektoren A(0)/B(1) aus den einzel-achsen auf -> die per-komponente-verdrahtung
        # bleibt exakt (kein skalar-broadcast-kollaps wie in einem frueheren V1-versuch).
        ca = _new(nt, "ShaderNodeCombineXYZ", nid, "_a")
        cb = _new(nt, "ShaderNodeCombineXYZ", nid, "_b")
        nt.links.new(ca.outputs[0], n.inputs[0])
        nt.links.new(cb.outputs[0], n.inputs[1])
        ins = {0: ca.inputs[0], 1: ca.inputs[1], 2: ca.inputs[2],
               3: cb.inputs[0], 4: cb.inputs[1], 5: cb.inputs[2]}
        return n, {0: n.outputs[0], 1: n.outputs[0], 2: n.outputs[0]}, ins, "built"

    if t == "mix_color":
        n = _new(nt, "ShaderNodeMix", nid)
        n.data_type = 'RGBA'
        n.blend_type = 'MIX'
        # MixColorNode-in: a(0),b(1),factor(2). ShaderNodeMix: Factor=inputs[0],A=inputs[6],B=inputs[7]
        return n, {0: n.outputs[2]}, {0: n.inputs[6], 1: n.inputs[7], 2: n.inputs[0]}, "built"

    if t == "combine_color":
        # in: r(0),g(1),b(2),a(3). geo-tree: FunctionNodeCombineColor (ShaderNodeCombineColor ist
        # shader-only, live geprueft). Beide haben mode='RGB' + R/G/B-inputs (kein A) -> a ignoriert.
        n = _new(nt, "FunctionNodeCombineColor" if geo else "ShaderNodeCombineColor", nid)
        n.mode = 'RGB'
        return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1], 2: n.inputs[2]}, "built"

    if t == "split_color":
        # in: rgba(0); out: r(0),g(1),b(2),a(3). geo-tree: FunctionNodeSeparateColor.
        n = _new(nt, "FunctionNodeSeparateColor" if geo else "ShaderNodeSeparateColor", nid)
        n.mode = 'RGB'
        return n, {0: n.outputs[0], 1: n.outputs[1], 2: n.outputs[2]}, {0: n.inputs[0]}, "built"

    if t == "invert":
        # BBS invert scalar (mode 0) = 1-value; color (mode 1) = RGB-invert.
        if int(bbs.get("mode", 0) or 0) == 1 and not geo:
            n = _new(nt, "ShaderNodeInvert", nid)  # color invert (shader-only)
            return n, {0: n.outputs[0]}, {0: n.inputs["Color"]}, "built"
        # scalar-invert ODER geo-tree (kein ShaderNodeInvert dort) -> Math SUBTRACT (1 - value)
        n = _new(nt, "ShaderNodeMath", nid)
        n.operation = 'SUBTRACT'
        n.inputs[0].default_value = 1.0
        return n, {0: n.outputs[0]}, {0: n.inputs[1]}, "built"

    if t == "clamp":
        n = _new(nt, "ShaderNodeClamp", nid)
        # in: value(0),min(1),max(2)
        return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1], 2: n.inputs[2]}, "built"

    if t == "remap":
        n = _new(nt, "ShaderNodeMapRange", nid)
        n.interpolation_type = 'LINEAR'
        # BBS remap in: value(0),from_min(1),from_max(2),to_min(3),to_max(4)
        # MapRange in: Value(0),From Min(1),From Max(2),To Min(3),To Max(4) — exakt gleich!
        return n, {0: n.outputs[0]}, {i: n.inputs[i] for i in range(5)}, "built"

    if t == "smoothstep":
        # BBS SmoothstepNode: t = clamp((x-edge0)/(edge1-edge0)); result = t*t*(3-2*t). Das ist
        # EXAKT Blenders MapRange SMOOTHSTEP (Value=x, From Min=edge0, From Max=edge1, To 0..1 default
        # -> Hermite t*t*(3-2t), clamp inklusive). Also KEINE naeherung: status "built".
        # in: edge0(0),edge1(1),x(2). edge1-default 1 (BBS: unverbunden -> 1, s. ShapeGraphEvaluator).
        n = _new(nt, "ShaderNodeMapRange", nid)
        n.interpolation_type = 'SMOOTHSTEP'
        _set_default(n.inputs, "From Max", 1.0)
        return n, {0: n.outputs[0]}, {0: n.inputs[1], 1: n.inputs[2], 2: n.inputs[0]}, "built"

    if t == "noise":
        # x(0),y(1),z(2) -> CombineXYZ -> Noise.Vector (per-achse exakt, kein broadcast); scale(3).
        # DETAIL=0: BBS' `Noise.noise` ist SINGLE-OCTAVE klassisches Perlin (fade=6t^5-15t^4+10t^3);
        # Blenders default-detail 2.0 stapelt 3 fBM-oktaven = ein FEINERES muster als BBS. Detail 0
        # = eine oktave = strukturell dieselbe frequenz wie BBS (naeher am original, gemessen).
        n = _new(nt, "ShaderNodeTexNoise", nid)
        _set_default(n.inputs, "Detail", 0.0)
        c = _new(nt, "ShaderNodeCombineXYZ", nid, "_xyz")
        nt.links.new(c.outputs[0], n.inputs["Vector"])
        ins = {0: c.inputs[0], 1: c.inputs[1], 2: c.inputs[2], 3: n.inputs["Scale"]}
        return n, {0: n.outputs["Factor"]}, ins, "built"

    if t == "voronoi":
        n = _new(nt, "ShaderNodeTexVoronoi", nid)
        c = _new(nt, "ShaderNodeCombineXYZ", nid, "_xyz")
        nt.links.new(c.outputs[0], n.inputs["Vector"])
        ins = {0: c.inputs[0], 1: c.inputs[1], 2: c.inputs[2], 3: n.inputs["Scale"]}
        return n, {0: n.outputs["Distance"]}, ins, "built"

    if t == "flow_noise":
        # BBS FlowNoiseNode (ShapeGraphEvaluator, exakt nachgebaut):
        #     result = noise(x*scale, y*scale - time*speed, z*scale) + y*taper
        # == dieselbe single-octave Perlin-noise wie NoiseNode, aber (a) die Y-achse SCROLLT mit
        # der zeit (`-time*speed`, das "flow"), (b) ein linearer taper-term `+y*taper` obendrauf.
        # Der fruehere stub warf speed UND taper weg (bare Noise-Texture) = statisch + kein taper.
        # AUFBAU (per-achse-mathe, kein skalar-broadcast): wir bauen den GESKALIERTEN vektor selbst
        # (Noise.Scale=1), damit der zeit-scroll im selben raum wie `y*scale` sitzt:
        #   sx = x*scale ; sy = y*scale - time*speed ; sz = z*scale
        #   out = noise((sx,sy,sz)) + y*taper
        # time = #frame-driver in SEKUNDEN (BBS-zeit-skala, s. TimeNode/_add_frame_driver).
        n = _new(nt, "ShaderNodeTexNoise", nid)
        _set_default(n.inputs, "Detail", 0.0)          # single-octave wie BBS (s. noise-node)
        _set_default(n.inputs, "Scale", 1.0)           # skalierung machen wir selbst (unten)

        c = _new(nt, "ShaderNodeCombineXYZ", nid, "_xyz")
        nt.links.new(c.outputs[0], n.inputs["Vector"])

        # per-achse x*scale, y*scale, z*scale (drei MULTIPLY); scale speist alle drei.
        mx = _new(nt, "ShaderNodeMath", nid, "_sx"); mx.operation = 'MULTIPLY'
        my = _new(nt, "ShaderNodeMath", nid, "_sy"); my.operation = 'MULTIPLY'
        mz = _new(nt, "ShaderNodeMath", nid, "_sz"); mz.operation = 'MULTIPLY'
        # zeit-scroll: time*speed, dann sy = y*scale - (time*speed)
        tsp = _new(nt, "ShaderNodeMath", nid, "_tsp"); tsp.operation = 'MULTIPLY'
        tim = _new(nt, "ShaderNodeValue", nid, "_time")
        _add_frame_driver(tim.outputs[0], seconds=True)
        nt.links.new(tim.outputs[0], tsp.inputs[0])    # tsp.in1 = speed (BBS input 4)
        sub = _new(nt, "ShaderNodeMath", nid, "_ysub"); sub.operation = 'SUBTRACT'
        nt.links.new(my.outputs[0], sub.inputs[0])     # y*scale
        nt.links.new(tsp.outputs[0], sub.inputs[1])    # - time*speed
        nt.links.new(mx.outputs[0], c.inputs[0])       # sx -> vector.x
        nt.links.new(sub.outputs[0], c.inputs[1])      # sy -> vector.y
        nt.links.new(mz.outputs[0], c.inputs[2])       # sz -> vector.z
        # taper: y*taper, out = noise + y*taper
        mt = _new(nt, "ShaderNodeMath", nid, "_taper"); mt.operation = 'MULTIPLY'
        add = _new(nt, "ShaderNodeMath", nid, "_out"); add.operation = 'ADD'
        nt.links.new(n.outputs["Factor"], add.inputs[0])
        nt.links.new(mt.outputs[0], add.inputs[1])

        # BBS-eingaenge auf die richtigen sockets: x(0),y(1),z(2),scale(3),speed(4),taper(5).
        # x/y/z speisen JE ihren scale-multiply (in0); fuer y ausserdem den taper-multiply, und
        # scale speist alle drei scale-multiplies. Ein BBS-input hat aber in der port-tabelle GENAU
        # EINEN ziel-socket. Wo ein wert an ZWEI stellen gebraucht wird (y -> my.in0 & mt.in0;
        # scale -> mx/my/mz.in1), setzen wir einen passthrough (Math ADD +0, hat inputs -> anders als
        # ShaderNodeValue) als fan-out-knoten und verlinken dessen output an beide/alle consumer.
        yfan = _new(nt, "ShaderNodeMath", nid, "_yfan"); yfan.operation = 'ADD'
        yfan.inputs[1].default_value = 0.0
        nt.links.new(yfan.outputs[0], my.inputs[0])
        nt.links.new(yfan.outputs[0], mt.inputs[0])
        sfan = _new(nt, "ShaderNodeMath", nid, "_sfan"); sfan.operation = 'ADD'
        sfan.inputs[0].default_value = 1.0             # scale-default 1 (BBS: unverbunden -> 1)
        sfan.inputs[1].default_value = 0.0
        nt.links.new(sfan.outputs[0], mx.inputs[1])
        nt.links.new(sfan.outputs[0], my.inputs[1])
        nt.links.new(sfan.outputs[0], mz.inputs[1])
        ins = {0: mx.inputs[0], 1: yfan.inputs[0], 2: mz.inputs[0],
               3: sfan.inputs[0], 4: tsp.inputs[1], 5: mt.inputs[1]}
        return add, {0: add.outputs[0]}, ins, "built"

    if t == "trigger":
        # BBS TriggerNode, mode-abhaengig (ShapeGraphEvaluator). in: input(0),target(1),range(2).
        #   mode 0 GREATER: input>target ? 1:0   -> Blender Math GREATER_THAN (EXAKT)
        #   mode 1 LESS:    input<target ? 1:0   -> Blender Math LESS_THAN    (EXAKT)
        #   mode 2 EQUAL / 3 NOT_EQUAL: |input-target| < range (band) -> KEIN 1-node-Blender-aequiv.
        #   mode 4 PULSE:   (input%target) < range -> zeit-getriebenes rechteck, kein statisches aequiv.
        # Modi 0/1 werden EXAKT gebaut ("built"), 2/3/4 bleiben approximiert (GREATER_THAN als
        # platzhalter) + ehrliche grenze. `input` unverbunden faellt in BBS auf `time` zurueck — das
        # bilden wir NICHT nach (input-socket bleibt default), dokumentierte grenze bei allen modi.
        mode = int(bbs.get("mode", 0) or 0)
        n = _new(nt, "ShaderNodeMath", nid)
        if mode == 0:
            n.operation = 'GREATER_THAN'
            return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1]}, "built"
        if mode == 1:
            n.operation = 'LESS_THAN'
            return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1]}, "built"
        # mode 2/3/4 -> keine exakte einzelknoten-entsprechung, geloggt als approximation.
        n.operation = 'GREATER_THAN'
        return n, {0: n.outputs[0]}, {0: n.inputs[0], 1: n.inputs[1]}, "approximated"

    if t == "texture":
        if geo:
            # geo-tree hat KEIN ShaderNodeTexImage (shader-only) -> als konstant-Value approximieren
            # (bild-asset ist ohnehin nicht aufgeloest). Displacement-feed ist skalar.
            n = _new(nt, "ShaderNodeValue", nid)
            return n, {0: n.outputs[0], 4: n.outputs[0]}, {}, "approximated"
        # TextureNode: bild-asset auf der Blender-seite (link-string -> PNG) ist noch nicht aufgeloest
        # -> node OHNE bild + log (dokumentierte grenze, kein throw). in: u(0),v(1); out: r/g/b/a/rgba.
        n = _new(nt, "ShaderNodeTexImage", nid)
        return n, {0: n.outputs["Color"], 4: n.outputs["Color"]}, {}, "approximated"

    # unbekannter typ -> approximieren als passthrough-Value, ehrlich geloggt
    n = _new(nt, "ShaderNodeValue", nid)
    return n, {0: n.outputs[0]}, {}, "approximated"


def _scene_fps():
    """Die aktuelle scene-fps (frames/sekunde) als float. fps/fps_base ist die ECHTE rate
    (fps_base != 1 bei NTSC-raten wie 29.97). Fallback 24.0 (Blenders default). Wirft nie."""
    try:
        r = bpy.context.scene.render
        base = r.fps_base or 1.0
        return float(r.fps) / float(base) if base else float(r.fps)
    except (AttributeError, TypeError, ZeroDivisionError):
        return 24.0


def _add_frame_driver(socket, seconds=False):
    """#frame-driver auf einen skalar-socket (TimeNode/FlowNoise -> laeuft mit der zeit). Wirft nie.
    `seconds=True` -> frame in SEKUNDEN umrechnen (frame/fps), damit die BBS-zeit-skala (wall-clock
    sekunden, s. TimeNode) getroffen wird; sonst roher frame-zaehler. Die fps wird EINMAL beim bau
    in den ausdruck gebacken (driver koennen scene.render.fps nicht selbst lesen ohne extra-var) —
    aendert der user die scene-fps, den graph neu importieren (dokumentierte grenze, wie #frame selbst)."""
    try:
        d = socket.driver_add("default_value").driver
        d.type = 'SCRIPTED'
        if seconds:
            fps = _scene_fps()
            d.expression = "frame / %r" % (fps if fps else 24.0)
        else:
            d.expression = "frame"
    except (RuntimeError, TypeError, AttributeError):
        pass


def apply_shape_graph(obj, replay):
    """Den BBS-shape-graph als Blender-shader-node-tree im per-actor-material nachbauen und an
    Base Color haengen. Gibt ein report-dict {built, approximated, flagged, skipped, wired}.
    Wirft nie (ein kaputter graph darf den import nicht killen)."""
    report = {"built": 0, "approximated": 0, "flagged": 0, "skipped": 0, "wired": 0,
              "approx_types": [], "flagged_types": []}
    if not has_shape_graph(replay):
        return report

    mat = actor_material.ensure_owned_material(obj, actor_material._uuid8(replay))
    if mat is None:
        return report
    nt = mat.node_tree
    sg = replay["properties"]["shapeGraph"]

    # alten graph raeumen (idempotent — poll/re-import baut sonst dubletten)
    _clear_graph(nt)

    # 1) pro BBS-node einen Blender-node bauen; port-tabellen je BBS-id merken
    built = {}   # bbs_id -> {"node":.., "out":{idx:socket}, "in":{idx:socket}}
    x_off = -1400.0
    y_off = 400.0
    for i, bbs in enumerate(sg.get("nodes", [])):
        if not isinstance(bbs, dict):
            continue
        bbs_id = bbs.get("id")
        try:
            node, outs, ins, status = _build_node(nt, bbs)
        except Exception as e:  # noqa: BLE001 — ein einzelner node darf den rest nicht killen
            print("[bbs-bridge] shape-graph: node %r bau-fehler: %s" % (bbs.get("type"), e))
            continue
        report[status] = report.get(status, 0) + 1
        if status == "approximated":
            report["approx_types"].append(bbs.get("type"))
        elif status == "flagged":
            report["flagged_types"].append(bbs.get("type"))
        if node is None:
            continue
        # eindeutig prefixen + grob layouten
        node.name = _NODE_PREFIX + str(bbs_id) + "_" + str(bbs.get("type"))
        node.label = str(bbs.get("type"))
        node.location = (x_off + (i % 6) * 220.0, y_off - (i // 6) * 240.0)
        built[bbs_id] = {"node": node, "out": outs, "in": ins}

    # 2) verbindungen nachziehen (BBS out-index -> Blender out-socket, BBS in-index -> in-socket)
    for c in sg.get("connections", []):
        if not isinstance(c, dict):
            continue
        src = built.get(c.get("outId"))
        dst = built.get(c.get("inId"))
        if not src or not dst:
            continue  # ein ende geflaggt/geskippt (output/bump/iris) -> keine Blender-verbindung
        out_sock = src["out"].get(c.get("outIdx"))
        in_sock = dst["in"].get(c.get("inIdx"))
        if out_sock is None or in_sock is None:
            continue
        try:
            nt.links.new(out_sock, in_sock)
            report["wired"] += 1
        except (RuntimeError, TypeError):
            pass

    # 3) den output an Base Color haengen: output.result (displacement) faellt in V1 weg
    #    (geometry-nodes-displacement ist ein spaeterer schritt), output.color -> vertex-farbe,
    #    output.material -> textur/material. Wir nehmen den ERSTEN verbundenen von (color, material,
    #    result) als sichtbare farbe an die Base Color (der graph ERSETZT die textur-farbe).
    out_info = sg.get("output") or {}
    base_color_src = None
    for key in ("color", "material", "result"):
        feeder = out_info.get(key, -1)
        node_info = built.get(feeder)
        if node_info and node_info["out"].get(0) is not None:
            base_color_src = node_info["out"][0]
            break
    if base_color_src is not None:
        _plug_into_base_color(nt, base_color_src)
        report["wired"] += 1

    # 4) DISPLACEMENT (Phase S2): output.result -> geometry-nodes-modifier (separater pfad, NICHT
    #    im shader-tree oben — shader kann keine geometrie verschieben). Wirft nie.
    try:
        disp = apply_shape_displacement(obj, replay)
        report["displaced"] = disp.get("displaced", False)
        report["disp_built"] = disp.get("built", 0)
        report["disp_approximated"] = disp.get("approximated", 0)
    except Exception as e:  # noqa: BLE001
        print("[bbs-bridge] shape-graph: displacement uebersprungen: %s" % (e,))
        report["displaced"] = False

    return report


def _plug_into_base_color(nt, src_socket):
    """Den graph-output vor die tint-kette an die Base Color legen. Wenn actor_materials BBS_Tint
    existiert, geht der graph auf dessen A-socket (inputs[6]) — sonst direkt auf BSDF.Base Color.
    So bleibt der whole-actor-tint (color/glow-kanaele) obendrauf multiplikativ wirksam. Wirft nie."""
    try:
        tint = nt.nodes.get(actor_material._TINT_NODE)
        if tint is not None:
            # A-socket = base-color-quelle (actor_material dokumentiert inputs[6]=A)
            _replace_link(nt, tint.inputs[6], src_socket)
            return
        bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is not None and "Base Color" in bsdf.inputs:
            _replace_link(nt, bsdf.inputs["Base Color"], src_socket)
    except Exception as e:  # noqa: BLE001
        print("[bbs-bridge] shape-graph: base-color-anschluss fehlgeschlagen: %s" % (e,))


def _replace_link(nt, dst_socket, src_socket):
    """Alle eingehenden links auf dst_socket entfernen, dann src->dst neu. Idempotent."""
    for link in [l for l in nt.links if l.to_socket == dst_socket]:
        nt.links.remove(link)
    nt.links.new(src_socket, dst_socket)


def _clear_graph(nt):
    """Alle BBS_SG_*-nodes (unseren graph) entfernen — idempotenz fuer re-import/poll. Wirft nie."""
    try:
        doomed = [n for n in nt.nodes if n.name.startswith(_NODE_PREFIX)]
        for n in doomed:
            nt.nodes.remove(n)
    except (RuntimeError, ReferenceError):
        pass


# ============================================================================ #
#  DISPLACEMENT (Phase S2) — output.result -> GEOMETRY-NODES vertex-displacement
#  Ein GETRENNTER pfad vom shader-tree oben: Blender-shader-nodes verschieben KEINE
#  geometrie. Der displacement-teilgraph wird als geo-nodes nachgebaut, sein skalar-
#  ergebnis skaliert die vertex-Normale, das ergebnis geht in `Set Position.Offset`.
# ============================================================================ #

def _mesh_holder(obj):
    """Das MESH-objekt finden das den geo-nodes-modifier tragen soll. Bei statischem mesh-actor ist
    das `obj` selbst; bei gerigtem BOBJ-actor (obj=armature) das mesh-child (spiegel zu
    actor_material._material_holder, aber fuer den modifier-traeger). Gibt None wenn kein mesh."""
    if getattr(obj, "type", None) == 'MESH':
        return obj
    for child in getattr(obj, "children", []):
        if getattr(child, "type", None) == 'MESH':
            return child
    return None


def _collect_disp_subgraph(sg, feeder_id):
    """Rueckwaerts von feeder_id durch die connections laufen und die menge der node-ids sammeln
    die den displacement-wert speisen (transitiver eingangs-kegel). So bauen wir NUR den relevanten
    teilgraph im geo-tree nach (nicht den ganzen color/material-graph). Gibt ein set von node-ids."""
    by_in = {}   # inId -> [connection, ...] (welche kanten speisen diesen node)
    for c in sg.get("connections", []):
        if isinstance(c, dict):
            by_in.setdefault(c.get("inId"), []).append(c)
    needed = set()
    stack = [feeder_id]
    while stack:
        nid = stack.pop()
        if nid in needed or nid is None or nid < 0:
            continue
        needed.add(nid)
        for c in by_in.get(nid, []):
            src = c.get("outId")
            if src is not None and src >= 0 and src not in needed:
                stack.append(src)
    return needed


def apply_shape_displacement(obj, replay):
    """output.result (der displacement-feeder) -> geometry-nodes-modifier auf dem mesh, der die
    vertices entlang ihrer Normale um den displacement-wert verschiebt (Set Position).
    Gibt {displaced: bool, built: int, approximated: int, wired: int}. Wirft nie (defensiv wie
    apply_shape_graph — ein kaputter displacement-graph darf den import nie killen)."""
    report = {"displaced": False, "built": 0, "approximated": 0, "wired": 0}
    if not has_shape_graph(replay):
        return report
    sg = replay["properties"]["shapeGraph"]
    out_info = sg.get("output") or {}
    feeder_id = out_info.get("result", -1)

    mesh_obj = _mesh_holder(obj)
    if mesh_obj is None:
        return report

    # feeder < 0 -> KEIN displacement verdrahtet: bestehenden modifier entfernen (idempotenz —
    # ein frueher displacement-graph, jetzt abgeklemmt, darf nicht als geist stehen bleiben).
    if not isinstance(feeder_id, int) or feeder_id < 0:
        _remove_displace_modifier(mesh_obj)
        return report

    uuid8 = actor_material._uuid8(replay)
    group = _build_displace_group(sg, feeder_id, uuid8, report)
    if group is None:
        _remove_displace_modifier(mesh_obj)
        return report

    _attach_displace_modifier(mesh_obj, group)
    report["displaced"] = True
    return report


def _build_displace_group(sg, feeder_id, uuid8, report):
    """Die geo-node-gruppe `BBS_SG_Displace_<uuid8>` (neu-)bauen: den displacement-teilgraph als
    geo-nodes nachbauen, sein output-0 (skalar) skaliert die Normale, Set Position schiebt die
    vertices. Idempotent — eine bestehende gruppe wird geleert + neu gefuellt (poll/re-import).
    Gibt die node-gruppe oder None (feeder nicht baubar)."""
    group_name = _DISP_GROUP_PREFIX + uuid8
    group = bpy.data.node_groups.get(group_name)
    if group is None:
        group = bpy.data.node_groups.new(group_name, "GeometryNodeTree")
    else:
        # bestehende gruppe leeren (idempotenz) — nodes + interface-sockets weg, neu aufbauen.
        group.nodes.clear()
        _clear_interface(group)

    # interface: EIN Geometry-input + EIN Geometry-output (der modifier fuettert/liest sie).
    group.interface.new_socket(name="Geometry", in_out='INPUT', socket_type='NodeSocketGeometry')
    group.interface.new_socket(name="Geometry", in_out='OUTPUT', socket_type='NodeSocketGeometry')

    nt = group.nodes
    g_in = nt.new("NodeGroupInput")
    g_in.location = (-800.0, 0.0)
    g_out = nt.new("NodeGroupOutput")
    g_out.location = (400.0, 0.0)

    # 1) displacement-teilgraph (nur der feeder-kegel) als geo-nodes nachbauen
    needed = _collect_disp_subgraph(sg, feeder_id)
    built = {}   # bbs_id -> {"node":, "out":{idx:socket}, "in":{idx:socket}}
    y = 300.0
    for bbs in sg.get("nodes", []):
        if not isinstance(bbs, dict) or bbs.get("id") not in needed:
            continue
        try:
            node, outs, ins, status = _build_node(group, bbs, geo=True)
        except Exception as e:  # noqa: BLE001
            print("[bbs-bridge] displacement: node %r bau-fehler: %s" % (bbs.get("type"), e))
            continue
        if status == "built":
            report["built"] += 1
        elif status == "approximated":
            report["approximated"] += 1
        if node is None:
            continue
        node.location = (-500.0, y)
        y -= 200.0
        built[bbs.get("id")] = {"node": node, "out": outs, "in": ins}

    # 2) verbindungen innerhalb des teilgraphs nachziehen
    for c in sg.get("connections", []):
        if not isinstance(c, dict):
            continue
        src = built.get(c.get("outId"))
        dst = built.get(c.get("inId"))
        if not src or not dst:
            continue
        out_sock = src["out"].get(c.get("outIdx"))
        in_sock = dst["in"].get(c.get("inIdx"))
        if out_sock is None or in_sock is None:
            continue
        try:
            group.links.new(out_sock, in_sock)
            report["wired"] += 1
        except (RuntimeError, TypeError):
            pass

    # 3) den displacement-wert (feeder output-0, skalar) holen
    feeder = built.get(feeder_id)
    disp_socket = feeder["out"].get(0) if feeder else None

    # 4) Normale × displacement-wert -> Set Position.Offset
    set_pos = nt.new("GeometryNodeSetPosition")
    set_pos.name = _NODE_PREFIX + "setpos"
    set_pos.location = (100.0, 0.0)
    normal = nt.new("GeometryNodeInputNormal")
    normal.name = _NODE_PREFIX + "normal"
    normal.location = (-300.0, -250.0)
    scale = nt.new("ShaderNodeVectorMath")
    scale.name = _NODE_PREFIX + "dispscale"
    scale.operation = 'SCALE'   # out = Vector * Scale(value)
    scale.location = (-100.0, -150.0)

    # Normal (VectorMath.inputs[0]) * displacement-wert (Scale = inputs[3]) -> Offset
    group.links.new(normal.outputs["Normal"], scale.inputs[0])
    if disp_socket is not None:
        try:
            group.links.new(disp_socket, scale.inputs[3])  # Scale-socket
        except (RuntimeError, TypeError):
            pass
    else:
        # feeder nicht baubar (z.b. geflaggt) -> kein displacement, gruppe passthrough. Ehrlich:
        # kein wert = 0-offset = mesh unveraendert (kein geraten). Report bleibt displaced (modifier
        # existiert), aber offset 0. Wir loggen es.
        print("[bbs-bridge] displacement: feeder-node %r hat keinen baubaren skalar-ausgang "
              "(geflaggt/leer) -> offset 0" % (feeder_id,))

    # kette: g_in.geometry -> set_pos.geometry -> g_out; scale.vector -> set_pos.offset
    group.links.new(g_in.outputs[0], set_pos.inputs[0])   # Geometry
    group.links.new(scale.outputs[0], set_pos.inputs[3])  # Offset (VECTOR)
    group.links.new(set_pos.outputs[0], g_out.inputs[0])  # Geometry out
    return group


def _clear_interface(group):
    """Alle interface-sockets einer geo-node-gruppe entfernen (5.2 interface-API). Wirft nie."""
    try:
        for item in list(group.interface.items_tree):
            group.interface.remove(item)
    except (RuntimeError, AttributeError):
        pass


def _attach_displace_modifier(mesh_obj, group):
    """Den NODES-modifier `BBS_SG_Displace` (idempotent) am mesh anlegen/wiederverwenden und auf die
    gruppe zeigen lassen. Wirft nie."""
    mod = mesh_obj.modifiers.get(_DISP_MOD)
    if mod is None or mod.type != 'NODES':
        if mod is not None:
            try:
                mesh_obj.modifiers.remove(mod)
            except (RuntimeError, ReferenceError):
                pass
        mod = mesh_obj.modifiers.new(_DISP_MOD, 'NODES')
    mod.node_group = group


def _remove_displace_modifier(mesh_obj):
    """Den displacement-modifier + seine gruppe rueckstandslos entfernen (kein displacement mehr).
    Wirft nie."""
    try:
        mod = mesh_obj.modifiers.get(_DISP_MOD)
        if mod is not None:
            grp = getattr(mod, "node_group", None)
            mesh_obj.modifiers.remove(mod)
            if grp is not None and grp.name.startswith(_DISP_GROUP_PREFIX) and grp.users == 0:
                bpy.data.node_groups.remove(grp)
    except (RuntimeError, ReferenceError):
        pass
