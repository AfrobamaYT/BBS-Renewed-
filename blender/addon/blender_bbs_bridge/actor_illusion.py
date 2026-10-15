"""PHASE V1 (+ nische-gradual 2026-07-18, + nische-delay/distort 2026-07-19 = L4)
— Illusion → Blender-duplikate (array-modifier ODER geometry-nodes-instancer).

Ein BBS-form kann ILLUSIONEN tragen (`Form.illusion`, jeder form): rein visuelle kopien die sich in
gepickten richtungen vom modell weg staffeln (keine echten entities). Im payload:
`properties.illusion = {"count":int, "spread":float, "spacing":float, "directions":int-bitflags,
"offset":float, "opacity":float, "uniform":bool, "invert":bool, "glow":float, "gradual":bool,
"gradualInvert":bool, "delay":float, "distort":float, "distortUniform":bool, "distortInvert":bool,
"transform":{"tx/ty/tz","sx/sy/sz","rx/ry/rz"}}` (java `IllusionExporter`).

ZWEI PFADE — je nachdem ob DELAY/DISTORT aktiv sind:

(A) BASIS-PFAD (array-modifier `BBS_Illusion`, wenn delay==0 UND distort==0): legt einen ARRAY-
modifier auf das MESH-child des actors (bei einem gerigten BOBJ-actor ist `obj` die armature, das
mesh hängt als kind — wie in actor_material._material_holder). Der modifier erzeugt `count` kopien
mit einem konstanten offset entlang der aus `directions` abgeleiteten richtung:

    directions bitflags:  FRONT=1  BACK=2  LEFT=4  RIGHT=8  UP=16  DOWN=32
    → richtungs-vektor im BLENDER-raum (contract: bbs_y=bl_z, bbs_z=-bl_y);
      FRONT/BACK = ±bl_y (bbs-z), LEFT/RIGHT = ±bl_x, UP/DOWN = ±bl_z (bbs-y)
    offset-länge pro kopie = max(spacing, spread, 1.0)  (einer der drei abstands-werte)

  GRADUAL (nische 2026-07-18, MACHBAR via array-objekt-offset): wenn `gradual` gesetzt UND ein nicht-
  default `transform` mitreist, akkumuliert jede kopie einen BRUCHTEIL des transforms → volle
  wirkung an der LETZTEN kopie (hidden empty `BBS_IllusionOffset_<uuid8>` als `offset_object`).

(B) GEO-NODES-PFAD (`BBS_IllusionGeo`-modifier + node-gruppe `BBS_IllusionGeo_<uuid8>`, wenn delay>0
ODER distort>0): der array-modifier ist STATISCH — jede kopie zeigt denselben frame + kann nicht
per-kopie deformiert werden. Für delay/distort bauen wir stattdessen einen INSTANCE-ON-POINTS-graph
(instance_illusion_geo unten), der die basis-staffelung (count·richtung·step) SELBST übernimmt und die
beiden nischen erledigt. Der array-modifier wird dann deaktiviert (sonst doppelte kopien). Details:

  DELAY (motion-trail, L4 2026-07-19): kopie N zeigt den actor-TRANSFORM von frame−N·delay (der
  geister-trail der hinter der bewegung nachzieht). Da BBS' `delay` die ganze illusion um N·delay
  ticks nach-hinken lässt, sampeln wir die actor-ROOT-F-curves (location/rotation_euler, dieselben
  die actors.py legt) an der VERGANGENEN frame-position und geben jeder instanz einen per-index-
  zeitversatz. Technik: `count` modifier-freie IndexSwitch-vektoren (translate + rotate), je ghost
  ein driver der `bbs_illusion_fc_at(root, kanal, frame−N·delay) − bbs_illusion_fc_at(root, kanal,
  frame)` auswertet → weltraum-delta gegen die aktuelle actor-position. SCRUB-SAFE + deterministisch
  (der driver liest die F-curve JEDEN frame neu, kein bake, kein simulation-zone-state). GRENZE
  (ehrlich, Regel 0): getrailt wird der TRANSFORM (position/rotation der ganzen kopie) — NICHT die
  pro-kopie-POSE-deformation (arm/bein-stellung von frame−N·delay). Letzteres bräuchte per-instance-
  sampling eines armature-deformierten mesh in geo-nodes, das Blender NICHT kann (recherchiert:
  instanzen teilen die depsgraph-zeit, kein per-instance "evaluate deformed mesh at past frame").
  Der dominante motion-trail-look (kopien entlang des bewegungspfades verschmiert) IST der transform-
  trail — das ist gebaut. Die pose-deformations-schicht bleibt die dokumentierte rest-grenze.

  DISTORT (zerfall, L4 2026-07-19): per-kopie noise-displacement das mit dem instanz-index zunimmt
  (die letzten kopien lösen sich auf). Instance-index als attribut speichern → realize instances →
  `Set Position` mit `noise(position)·factor`, factor = (index/(count−1))·distort. `distortUniform`
  → factor = distort für ALLE kopien (kein index-ramp). `distortInvert` → factor invertieren
  ((count−1−index)/(count−1)) so dass die ERSTE kopie am stärksten zerfällt. Gemessen (render-gate
  :9976): monotone displacement-rampe (inst0=0.0, wachsend bis inst_last), erste kopie unverzerrt.

GRENZEN (Regel 0 — ehrlich, NICHT falsch gemappt):
- delay POSE-schicht: siehe oben — transform-trail gebaut, pose-deformation-trail = echte Blender-
  grenze (kein per-instance-zeit-sampling deformierter mesh), dokumentiert, NICHT kaschiert.
- gradual + geo-nodes-pfad: `gradual`/`transform` wirken nur im basis-array-pfad. Bei aktivem
  delay/distort (geo-pfad) ist gradual v1 NICHT kombiniert (getrennte techniken) — dokumentiert.
- real (welt-kollision): gameplay, kein render-attribut. NICHT gebaut, nicht gesendet.
- per-kopie-texturen (textures/randomTextures): NICHT gebaut, nicht gesendet.
- opacity/glow: als custom-prop am modifier-träger transportiert (v1 kein per-instance-alpha/emission
  ohne aufwändigen shader-graph — dokumentierte grenze; die kopien werden gezeigt, die staffelung von
  alpha/emission über die kopien ist v2).
- illusionOverlay + additionalIllusions (weitere schichten): v2.

IDEMPOTENT: erneuter aufruf findet den passenden modifier + das offset-empty + die geo-gruppe wieder
und baut sie neu. count<=1 / kein payload → beide modifier + empty + gruppe entfernt (sauber). Der
NICHT-aktive pfad wird immer abgebaut (kein delay mehr → geo-modifier weg, array zurück). Wirft nie."""
import math

import bpy

_ILLUSION_MOD = "BBS_Illusion"
_OFFSET_EMPTY = "BBS_IllusionOffset_"
_OPACITY_PROP = "bbs_illusion_opacity"
_GLOW_PROP = "bbs_illusion_glow"

# --- geo-nodes-pfad (delay/distort, L4 2026-07-19) ---
_GEO_MOD = "BBS_IllusionGeo"              # NODES-modifier-name am mesh
_GEO_GROUP_PREFIX = "BBS_IllusionGeo_"    # + uuid8 = node-gruppen-name
_GI_ATTR = "bbs_illusion_gi"              # per-instanz-index als named attribute (distort-rampe)
# driver-namespace-funktion (die actor-fcurve an einem vergangenen frame sampeln, für den delay-trail)
_DRIVER_FN = "bbs_illusion_fc_at"

# richtungs-bitflags (spiegel Illusion.java:20-25)
_FRONT = 1
_BACK = 2
_LEFT = 4
_RIGHT = 8
_UP = 16
_DOWN = 32


# ============================================================================ #
#  DELAY-driver-namespace (L4 2026-07-19): den motion-trail scrub-safe machen.
#  Ein driver-ausdruck kann eine funktion aus bpy.app.driver_namespace rufen — wir
#  registrieren eine, die den wert einer objekt-F-curve an einem VERGANGENEN frame
#  liefert. So liest jeder ghost-driver JEDEN frame die actor-motion an frame−N·delay
#  frisch aus der F-curve (kein bake, kein simulation-zone-state → deterministisch,
#  scrubbar, render-order-unabhängig — wichtig für die determinismus-gates des projekts).
# ============================================================================ #

def _find_actor_fcurve(obj, data_path, index):
    """Die F-curve (data_path, array_index) eines objekts finden. 5.2 slotted actions: über
    layer→strip→channelbag; legacy `action.fcurves` als fallback. Gibt None wenn keine."""
    ad = getattr(obj, "animation_data", None)
    if ad is None or ad.action is None:
        return None
    act = ad.action
    # 5.2 slotted-action-pfad (das paket-dossier: action.fcurves existiert hier gar nicht mehr)
    try:
        slot = getattr(ad, "action_slot", None)
        for layer in act.layers:
            for strip in layer.strips:
                cbag = strip.channelbag(slot) if (slot is not None and hasattr(strip, "channelbag")) else None
                if cbag is None:
                    continue
                for fc in cbag.fcurves:
                    if fc.data_path == data_path and fc.array_index == index:
                        return fc
    except (AttributeError, RuntimeError):
        pass
    # legacy-fallback (ältere Blender / nicht-slotted actions)
    try:
        for fc in act.fcurves:
            if fc.data_path == data_path and fc.array_index == index:
                return fc
    except (AttributeError, RuntimeError):
        pass
    return None


def _illusion_fc_at(obj_name, data_path, index, past_frame, fallback):
    """Driver-namespace-funktion: wert der actor-F-curve (obj_name, data_path, index) an `past_frame`.
    Wirft NIE (ein driver der wirft, deaktiviert sich still) — kein objekt/keine kurve → `fallback`.
    Registriert unter `_DRIVER_FN` in bpy.app.driver_namespace (siehe register_drivers)."""
    try:
        obj = bpy.data.objects.get(obj_name)
        if obj is None:
            return fallback
        fc = _find_actor_fcurve(obj, data_path, index)
        if fc is None:
            return fallback
        return float(fc.evaluate(past_frame))
    except Exception:  # noqa: BLE001 — ein werfender driver killt sich selbst; nie werfen
        return fallback


def register_drivers():
    """Die delay-trail-driver-funktion in den namespace hängen. Idempotent, wirft nie. Muss laufen
    BEVOR ein delay-trail-driver evaluiert (apply_illusion ruft es selbst; __init__.register kann es
    zusätzlich früh aufrufen, damit ein bereits gespeicherter .blend-driver nach dem laden auflöst)."""
    try:
        bpy.app.driver_namespace[_DRIVER_FN] = _illusion_fc_at
    except Exception:  # noqa: BLE001
        pass


def _illusion_data(replay):
    if not isinstance(replay, dict):
        return None
    props = replay.get("properties")
    if not isinstance(props, dict):
        return None
    ill = props.get("illusion")
    return ill if isinstance(ill, dict) else None


def has_illusion(replay):
    return _illusion_data(replay) is not None


def _uuid8(replay):
    u = str(replay.get("uuid") or replay.get("id") or "actor")
    return u.replace("-", "")[:8] or "actor"


def _mesh_holder(obj):
    """Das mesh finden auf das der array-modifier gehört. Bei statischem mesh-actor = obj selbst,
    bei gerigtem BOBJ-actor = mesh-child (obj ist dann die armature). Spiegel von
    actor_material._material_holder."""
    if obj.type == 'MESH':
        return obj
    for child in obj.children:
        if child.type == 'MESH':
            return child
    return None


def _direction_vector(directions, step):
    """directions-bitflags → EIN Blender-raum-versatz-vektor (summe der gesetzten richtungen,
    normalisiert auf schrittweite `step`). contract: bbs_y=bl_z, bbs_z=-bl_y; MC-FRONT ist +bbs_z
    → -bl_y, LEFT/RIGHT über bl_x, UP/DOWN über bl_z (bbs_y)."""
    dx = dy = dz = 0.0
    if directions & _RIGHT:
        dx += 1.0
    if directions & _LEFT:
        dx -= 1.0
    if directions & _FRONT:
        dy -= 1.0    # +bbs_z = -bl_y
    if directions & _BACK:
        dy += 1.0
    if directions & _UP:
        dz += 1.0    # +bbs_y = +bl_z
    if directions & _DOWN:
        dz -= 1.0
    # keine richtung gesetzt → default entlang +bl_x (die kopien müssen IRGENDWO hin)
    if dx == 0.0 and dy == 0.0 and dz == 0.0:
        dx = 1.0
    length = (dx * dx + dy * dy + dz * dz) ** 0.5
    if length == 0.0:
        return (step, 0.0, 0.0)
    return (dx / length * step, dy / length * step, dz / length * step)


def _transform_to_blender(t):
    """Das BBS-`transform`-dict (tx/ty/tz, sx/sy/sz, rx/ry/rz — rotate RADIAN) in Blender-raum
    umrechnen. contract bbs_y=bl_z, bbs_z=-bl_y (wie _direction_vector):
      translate: bl=(tx, -tz, ty)
      scale:     komponenten-getauscht (sy↔sz), aber scale ist vorzeichenlos → bl=(sx, sz, sy)
      rotate:    euler-achsen getauscht wie translate (rx, -rz, ry)
    Gibt (translate3, scale3, rotate3). Fehlende/kaputte werte → identität-komponente."""
    def _f(key, default):
        try:
            return float(t.get(key, default))
        except (TypeError, ValueError):
            return default
    tx, ty, tz = _f("tx", 0.0), _f("ty", 0.0), _f("tz", 0.0)
    sx, sy, sz = _f("sx", 1.0), _f("sy", 1.0), _f("sz", 1.0)
    rx, ry, rz = _f("rx", 0.0), _f("ry", 0.0), _f("rz", 0.0)
    translate = (tx, -tz, ty)
    scale = (sx, sz, sy)
    rotate = (rx, -rz, ry)
    return translate, scale, rotate


def _find_offset_empty(name):
    obj = bpy.data.objects.get(name)
    return obj if (obj is not None and obj.type == 'EMPTY') else None


def _remove_offset_empty(name):
    obj = bpy.data.objects.get(name)
    if obj is not None:
        try:
            bpy.data.objects.remove(obj, do_unlink=True)
        except (RuntimeError, ReferenceError):
            pass


def _apply_gradual(holder, mod, ill, count, uuid8, coll):
    """gradual-rampe über den array-objekt-offset einrichten (oder abbauen). Baut/aktualisiert das
    hidden empty `BBS_IllusionOffset_<uuid8>` mit der PER-SCHRITT-transform (scale^(1/count),
    rotate/count, translate/count) und bindet es als `mod.offset_object`. Gibt True wenn gradual
    aktiv ist. Wirft nie im aufrufer."""
    empty_name = _OFFSET_EMPTY + uuid8
    gradual = bool(ill.get("gradual", False))
    transform = ill.get("transform")
    if not gradual or not isinstance(transform, dict) or count <= 0:
        # kein gradual → objekt-offset aus + empty weg (sauber)
        mod.use_object_offset = False
        mod.offset_object = None
        _remove_offset_empty(empty_name)
        return False

    translate, scale, rotate = _transform_to_blender(transform)
    gradual_invert = bool(ill.get("gradualInvert", False))

    # per-schritt-anteil: der array akkumuliert die empty-matrix pro kopie. Nach `count` schritten
    # (letzte kopie) soll die volle transform erreicht sein → jeder schritt trägt 1/count.
    n = float(count)
    step_scale = tuple(_nth_root(s, n) for s in scale)          # geometrisch (multiplikativ)
    step_rot = tuple(r / n for r in rotate)                     # linear (additiv im winkel)
    step_trans = tuple(t / n for t in translate)               # linear

    if gradual_invert:
        # invertiert: voll am modell, identität an der letzten kopie → per-schritt-INVERSE
        step_scale = tuple((1.0 / s if s != 0.0 else 1.0) for s in step_scale)
        step_rot = tuple(-r for r in step_rot)
        step_trans = tuple(-t for t in step_trans)

    empty = _find_offset_empty(empty_name)
    if empty is None:
        empty = bpy.data.objects.new(empty_name, None)
        empty.empty_display_type = 'PLAIN_AXES'
        empty.empty_display_size = 0.2
        try:
            coll.objects.link(empty)
        except RuntimeError:
            pass
    # das offset-empty gehört uns (invariante 5, prefixed + rückstandslos) — im viewport verstecken,
    # es ist reine steuer-geometrie, keine sichtbare kopie
    empty.hide_render = True
    empty.hide_viewport = True
    empty.parent = None
    empty.location = step_trans
    empty.rotation_mode = 'XYZ'
    empty.rotation_euler = step_rot
    empty.scale = step_scale

    mod.use_object_offset = True
    mod.offset_object = empty
    return True


def _nth_root(value, n):
    """value^(1/n) mit vorzeichen-erhalt für scale (scale sollte ≥0 sein, aber defensiv)."""
    if n <= 0:
        return value
    try:
        if value < 0:
            return -((-value) ** (1.0 / n))
        return value ** (1.0 / n)
    except (ValueError, OverflowError):
        return 1.0


# ============================================================================ #
#  GEO-NODES-PFAD (delay/distort, L4 2026-07-19)
#  Instance-on-points statt array-modifier: der array kann keine per-kopie-
#  zeitverschiebung (delay) und keine per-kopie-deformation (distort). Der graph
#  hier übernimmt AUCH die basis-staffelung (count·richtung·step), damit er den
#  array voll ersetzt (kein doppel-modifier).
# ============================================================================ #

def _remove_geo_group(uuid8):
    """Die illusion-geo-node-gruppe rückstandslos entfernen (wenn ungenutzt). Wirft nie."""
    grp = bpy.data.node_groups.get(_GEO_GROUP_PREFIX + uuid8)
    if grp is not None and grp.users == 0:
        try:
            bpy.data.node_groups.remove(grp)
        except (RuntimeError, ReferenceError):
            pass


def _remove_geo_modifier(holder, uuid8):
    """Den illusion-geo-modifier + seine (getriebenen) inputs + die gruppe entfernen. Wirft nie.
    Die driver liegen auf den node-socket-default_values der gruppe → mit der gruppe weg."""
    try:
        mod = holder.modifiers.get(_GEO_MOD)
        if mod is not None:
            holder.modifiers.remove(mod)
    except (RuntimeError, ReferenceError):
        pass
    _remove_geo_group(uuid8)


def _clear_geo_interface(group):
    """Alle interface-sockets einer geo-node-gruppe entfernen (5.2 interface-API). Wirft nie."""
    try:
        for item in list(group.interface.items_tree):
            group.interface.remove(item)
    except (RuntimeError, AttributeError):
        pass


def _drive_index_switch(sw, i, root_name, specs, delay):
    """Ein IndexSwitch-vektor-item (ghost i) treiben: jede komponente liest die actor-F-curve an
    frame−i·delay MINUS dem wert an frame-jetzt (weltraum-delta gegen die aktuelle actor-position,
    da die instanz bereits dort steht). `specs` = [(data_path, actor_index, bl_component), ...].
    Wirft nie (ein driver der wirft deaktiviert sich still — apply fängt den bau)."""
    sock = sw.inputs[1 + i]   # inputs[0]=Index, inputs[1..N]=die vektor-items
    past = "(frame - %d*%s)" % (i, repr(float(delay)))
    for data_path, aidx, comp in specs:
        try:
            drv = sock.driver_add("default_value", comp).driver
        except (RuntimeError, TypeError):
            continue
        drv.type = 'SCRIPTED'
        # bbs_illusion_fc_at(root, path, aidx, past_frame, fallback) − …(…, frame, …)
        drv.expression = (
            "%s('%s','%s',%d,%s,0.0) - %s('%s','%s',%d,frame,0.0)"
            % (_DRIVER_FN, root_name, data_path, aidx, past,
               _DRIVER_FN, root_name, data_path, aidx)
        )


# actor-root-kanäle → (data_path, actor_index, blender_translate_component) für den delay-trail.
# spiegel actors._ACTOR_CHANNELS: location x=0→bl0, y=2→bl1(=bl_y aus bbs_z), z=1→bl2; die skalen
# (1/-1) heben sich im DELTA (fc(past)−fc(now)) NICHT auf, aber ein negatives vorzeichen kehrt beide
# summanden gleich um → das delta behält das richtige vorzeichen OHNE dass wir es hier führen müssen
# (der offset ist die weltraum-differenz der actor-position, die F-curve trägt das vorzeichen selbst).
_DELAY_TRANS = [("location", 0, 0), ("location", 2, 1), ("location", 1, 2)]
# rotation: pitch=euler[0], yaw=euler[2]; RotateInstances nimmt die euler DELTA relativ zur instanz.
_DELAY_ROT = [("rotation_euler", 0, 0), ("rotation_euler", 1, 1), ("rotation_euler", 2, 2)]


def instance_illusion_geo(holder, root_obj, ill, count, uuid8, dvec, delay, distort,
                          distort_uniform, distort_invert):
    """Den illusion-geo-nodes-graph (neu-)bauen + als `BBS_IllusionGeo`-modifier ans mesh hängen.

      count punkte (Mesh Line entlang `dvec` = basis-staffelung)
        → Instance on Points (die eingangs-geometrie = das actor-mesh)
        → [delay>0] Translate/Rotate Instances mit per-index IndexSwitch-vektoren (getrieben, s.o.)
        → [distort>0] index-attribut speichern → Realize → Set Position (noise·index-rampe)

    `root_obj` = das objekt das die location/rotation-F-curves trägt (die ARMATURE-root bei gerigten
    actors, sonst `holder` selbst) — der delay-trail sampelt DESSEN motion. Idempotent (gruppe wird
    geleert + neu gefüllt). Wirft nie im aufrufer (apply fängt)."""
    register_drivers()   # sicherstellen dass die namespace-funktion da ist, bevor ein driver evaluiert

    group_name = _GEO_GROUP_PREFIX + uuid8
    group = bpy.data.node_groups.get(group_name)
    if group is None:
        group = bpy.data.node_groups.new(group_name, "GeometryNodeTree")
    else:
        group.nodes.clear()
        _clear_geo_interface(group)

    group.interface.new_socket(name="Geometry", in_out='INPUT', socket_type='NodeSocketGeometry')
    group.interface.new_socket(name="Geometry", in_out='OUTPUT', socket_type='NodeSocketGeometry')

    nt = group.nodes
    L = group.links
    g_in = nt.new("NodeGroupInput"); g_in.location = (-1200.0, 0.0)
    g_out = nt.new("NodeGroupOutput"); g_out.location = (1200.0, 0.0)

    # --- basis-staffelung: count punkte entlang dvec (= array-konstant-offset-äquivalent) ---
    line = nt.new("GeometryNodeMeshLine"); line.location = (-1000.0, -250.0)
    line.mode = 'OFFSET'
    line.inputs["Count"].default_value = count + 1     # original + count kopien (wie array_count)
    line.inputs["Offset"].default_value = dvec
    iop = nt.new("GeometryNodeInstanceOnPoints"); iop.location = (-800.0, 0.0)
    L.new(line.outputs["Mesh"], iop.inputs["Points"])
    L.new(g_in.outputs[0], iop.inputs["Instance"])

    geo_socket = iop.outputs["Instances"]

    # --- DELAY: per-index-zeitversatz (translate + rotate der instanzen) ---
    if delay > 0.0:
        idxn = nt.new("GeometryNodeInputIndex"); idxn.location = (-800.0, 320.0)
        tsw = nt.new("GeometryNodeIndexSwitch"); tsw.data_type = 'VECTOR'; tsw.location = (-540.0, 320.0)
        rsw = nt.new("GeometryNodeIndexSwitch"); rsw.data_type = 'VECTOR'; rsw.location = (-540.0, -160.0)
        # IndexSwitch hat default 2 items → auf (count+1) erweitern
        for _ in range((count + 1) - 2):
            tsw.index_switch_items.new()
            rsw.index_switch_items.new()
        L.new(idxn.outputs[0], tsw.inputs[0])
        L.new(idxn.outputs[0], rsw.inputs[0])

        trans = nt.new("GeometryNodeTranslateInstances"); trans.location = (-260.0, 160.0)
        trans.inputs["Local Space"].default_value = False   # weltraum-delta (siehe _DELAY_TRANS)
        L.new(geo_socket, trans.inputs["Instances"])
        L.new(tsw.outputs[0], trans.inputs["Translation"])

        rot = nt.new("GeometryNodeRotateInstances"); rot.location = (0.0, 100.0)
        L.new(trans.outputs["Instances"], rot.inputs["Instances"])
        L.new(rsw.outputs[0], rot.inputs["Rotation"])   # VECTOR→ROTATION implizit (euler, 5.2)
        geo_socket = rot.outputs["Instances"]

        root_name = root_obj.name
        for i in range(count + 1):
            _drive_index_switch(tsw, i, root_name, _DELAY_TRANS, delay)
            _drive_index_switch(rsw, i, root_name, _DELAY_ROT, delay)

    # --- DISTORT: per-instanz noise-displacement das mit dem index zunimmt ---
    if distort > 0.0:
        n = max(1, count)   # count kopien → rampe über count schritte (index 0..count)

        # index als attribut speichern (INSTANCE-domain), DANN realize (sonst geht der index verloren)
        idx2 = nt.new("GeometryNodeInputIndex"); idx2.location = (200.0, 320.0)
        store = nt.new("GeometryNodeStoreNamedAttribute"); store.location = (260.0, 0.0)
        store.data_type = 'FLOAT'; store.domain = 'INSTANCE'
        store.inputs["Name"].default_value = _GI_ATTR
        L.new(geo_socket, store.inputs["Geometry"])
        L.new(idx2.outputs[0], store.inputs["Value"])   # INT→FLOAT implizit

        realize = nt.new("GeometryNodeRealizeInstances"); realize.location = (460.0, 0.0)
        L.new(store.outputs["Geometry"], realize.inputs["Geometry"])

        readattr = nt.new("GeometryNodeInputNamedAttribute"); readattr.location = (460.0, 300.0)
        readattr.data_type = 'FLOAT'
        readattr.inputs["Name"].default_value = _GI_ATTR

        # factor = rampe(index) · distort. uniform → distort für alle; invert → (n−index)/n.
        if distort_uniform:
            factor_node = nt.new("ShaderNodeValue"); factor_node.location = (700.0, 300.0)
            factor_node.outputs[0].default_value = distort
            factor_socket = factor_node.outputs[0]
        else:
            div = nt.new("ShaderNodeMath"); div.location = (660.0, 340.0); div.operation = 'DIVIDE'
            L.new(readattr.outputs["Attribute"], div.inputs[0])
            div.inputs[1].default_value = float(n)
            ramp_socket = div.outputs[0]
            if distort_invert:
                # (n − index)/n = 1 − index/n → erste kopie am stärksten
                inv = nt.new("ShaderNodeMath"); inv.location = (760.0, 400.0); inv.operation = 'SUBTRACT'
                inv.inputs[0].default_value = 1.0
                L.new(div.outputs[0], inv.inputs[1])
                ramp_socket = inv.outputs[0]
            mul = nt.new("ShaderNodeMath"); mul.location = (860.0, 340.0); mul.operation = 'MULTIPLY'
            L.new(ramp_socket, mul.inputs[0])
            mul.inputs[1].default_value = distort
            factor_socket = mul.outputs[0]

        # noise(position) zentriert um 0 → offset = (noise−0.5)·factor entlang jeder achse
        pos = nt.new("GeometryNodeInputPosition"); pos.location = (660.0, -220.0)
        noise = nt.new("ShaderNodeTexNoise"); noise.location = (760.0, -120.0)
        noise.noise_dimensions = '3D'
        noise.inputs["Scale"].default_value = 3.0
        L.new(pos.outputs[0], noise.inputs["Vector"])
        centre = nt.new("ShaderNodeVectorMath"); centre.location = (940.0, -120.0)
        centre.operation = 'SUBTRACT'
        L.new(noise.outputs["Color"], centre.inputs[0])
        centre.inputs[1].default_value = (0.5, 0.5, 0.5)
        scale = nt.new("ShaderNodeVectorMath"); scale.location = (1000.0, 120.0)
        scale.operation = 'SCALE'
        L.new(centre.outputs[0], scale.inputs[0])
        L.new(factor_socket, scale.inputs["Scale"])

        setpos = nt.new("GeometryNodeSetPosition"); setpos.location = (1040.0, 0.0)
        L.new(realize.outputs["Geometry"], setpos.inputs["Geometry"])
        L.new(scale.outputs[0], setpos.inputs["Offset"])
        geo_socket = setpos.outputs["Geometry"]

    L.new(geo_socket, g_out.inputs[0])

    # modifier (idempotent) ans mesh hängen
    mod = holder.modifiers.get(_GEO_MOD)
    if mod is None or mod.type != 'NODES':
        if mod is not None:
            try:
                holder.modifiers.remove(mod)
            except (RuntimeError, ReferenceError):
                pass
        mod = holder.modifiers.new(_GEO_MOD, 'NODES')
    mod.node_group = group
    return True


def _illusion_float(ill, key, default):
    """Robust einen float aus dem illusion-dict ziehen (fehlt/kaputt → default). Wirft nie."""
    try:
        return float(ill.get(key, default))
    except (TypeError, ValueError):
        return default


def apply_illusion(obj, replay):
    """Illusion-kopien auf das mesh des actors. Idempotent, wirft nie. Wählt den pfad:
      - delay>0 ODER distort>0  → GEO-NODES-pfad (BBS_IllusionGeo), array wird abgebaut
      - sonst                    → ARRAY-pfad (BBS_Illusion, inkl. gradual), geo wird abgebaut
    no-op (beide pfade + empty + gruppe weg) wenn kein illusion-payload / count<=1."""
    holder = _mesh_holder(obj)
    if holder is None:
        return False

    ill = _illusion_data(replay)
    uuid8 = _uuid8(replay)
    empty_name = _OFFSET_EMPTY + uuid8

    # bestehenden array-modifier finden (idempotenz)
    mod = holder.modifiers.get(_ILLUSION_MOD)

    try:
        count = int(ill.get("count", 0)) if ill else 0
    except (TypeError, ValueError):
        count = 0

    # count zählt die ZUSÄTZLICHEN kopien; array-count = original + kopien
    array_count = count + 1

    if ill is None or count <= 0:
        # kein/leerer illusion-payload → BEIDE pfade weg (sauber)
        if mod is not None:
            holder.modifiers.remove(mod)
        _remove_offset_empty(empty_name)
        _remove_geo_modifier(holder, uuid8)
        holder.pop(_OPACITY_PROP, None)
        holder.pop(_GLOW_PROP, None)
        return False

    # abstand pro kopie: einer von spacing/spread (der größere), mindestens 1 block sichtbar-versatz
    spacing = _illusion_float(ill, "spacing", 0.0)
    spread = _illusion_float(ill, "spread", 0.0)
    step = max(spacing, spread, 1.0)

    try:
        directions = int(ill.get("directions", 0))
    except (TypeError, ValueError):
        directions = 0

    dvec = _direction_vector(directions, step)

    # delay/distort entscheiden über den pfad (nische L4 2026-07-19)
    delay = max(0.0, _illusion_float(ill, "delay", 0.0))
    distort = max(0.0, _illusion_float(ill, "distort", 0.0))
    distort_uniform = bool(ill.get("distortUniform", False))
    distort_invert = bool(ill.get("distortInvert", False))

    if delay > 0.0 or distort > 0.0:
        # GEO-NODES-PFAD: array-modifier + gradual-empty abbauen (sonst doppelte kopien), geo bauen.
        if mod is not None:
            try:
                holder.modifiers.remove(mod)
            except (RuntimeError, ReferenceError):
                pass
        _remove_offset_empty(empty_name)
        try:
            instance_illusion_geo(holder, obj, ill, count, uuid8, dvec, delay, distort,
                                  distort_uniform, distort_invert)
        except Exception as e:  # noqa: BLE001 — der geo-pfad darf den import nie killen
            print("[bbs-bridge] illusion: geo-pfad (delay/distort) fehlgeschlagen: %s" % (e,))
            _remove_geo_modifier(holder, uuid8)
        # opacity/glow-transport bleibt gleich (v1-grenze)
        try:
            holder[_OPACITY_PROP] = float(ill.get("opacity", 1.0))
            holder[_GLOW_PROP] = float(ill.get("glow", 0.0))
        except (TypeError, ValueError):
            pass
        return True

    # ARRAY-PFAD (delay==0 UND distort==0): geo-pfad abbauen falls er vorher aktiv war.
    _remove_geo_modifier(holder, uuid8)

    if mod is None:
        mod = holder.modifiers.new(_ILLUSION_MOD, 'ARRAY')

    mod.count = array_count
    mod.use_relative_offset = False      # wir setzen einen KONSTANTEN weltschritt (blöcke), kein bbox-vielfaches
    mod.use_constant_offset = True
    mod.constant_offset_displace = dvec
    mod.use_merge_vertices = False

    # gradual-rampe (nische 2026-07-18) — objekt-offset NEBEN dem konstanten richtungs-offset.
    # array_count-1 = die zahl der SCHRITTE bis zur letzten kopie (count = zusätzliche kopien).
    coll = holder.users_collection[0] if holder.users_collection else bpy.context.scene.collection
    try:
        _apply_gradual(holder, mod, ill, count, uuid8, coll)
    except Exception as e:  # noqa: BLE001 — gradual darf den array-basis-pfad nie killen
        print("[bbs-bridge] illusion: gradual-rampe fehlgeschlagen (%s) — array ohne rampe" % (e,))
        try:
            mod.use_object_offset = False
            mod.offset_object = None
        except (AttributeError, ReferenceError):
            pass

    # opacity/glow als custom-prop transportieren (v1-grenze: kein per-instance-alpha ohne geo-nodes)
    try:
        holder[_OPACITY_PROP] = float(ill.get("opacity", 1.0))
        holder[_GLOW_PROP] = float(ill.get("glow", 0.0))
    except (TypeError, ValueError):
        pass

    return True
