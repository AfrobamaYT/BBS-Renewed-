"""PHASE B4 — per-bone MATERIAL: BBS pose:<bone> material-subkurven -> Blender per-bone
material-slots. Der teuerste teil des material-sync (masterplan) — die addon-hälfte, die der
java-export (`PerBoneExporter`, commit 0a82905) offenließ.

=== DATENQUELLE (java PerBoneExporter, B4) ===
Der ACTORS-payload trägt unter `replay["properties"]["bones"]["<bone>"]` pro bone der von den
material-defaults ABWEICHT (byte-sparsam — ein reiner transform-bone trägt keine material-keys)
je subkurven:

    color: cr/cg/cb/ca   (PoseTransform.color rgba,   default white 1/1/1/1)
    paint: pr/pg/pb/pi   (paintColor r/g/b + a=intensität, default 1/1/1/0 = kein overlay)
    glow:  gr/gg/gb/gi   (glowingColor r/g/b + glowIntensity, default 1/1/1 + 0 = kein glow)

Jedes kf = {"tick","value","interp","lx","ly","rx","ry"} (dieselbe shape wie alle anderen
subkurven — WIR nutzen actor_material._key_socket_component/_apply_interp für exakte interp-treue).

=== WAS DIESES MODUL TUT ===
Whole-actor color/glow/paint (actor_material.py + actor_paint.py) färbt das GANZE mesh über EIN
material. Per-bone-material braucht dagegen, dass jeder bone eine EIGENE farbe/emission/paint
tragen kann. In Blender ist die granularität dafür der MATERIAL-SLOT: ein mesh kann mehrere slots
haben, jede FACE zeigt auf genau einen slot-index. Wir weisen deshalb die faces der VERTEX-GRUPPE
eines bones (bei BOBJ-skinning ist der bone-name == vertex-group-name) einem eigenen slot
`<basis>__bone_<name>` zu (via bmesh, idempotent) und keyframen dessen node-kette.

=== KOEXISTENZ whole-actor + per-bone auf DEMSELBEN mesh (L6, 2026-07-19 — BBS-GEMESSEN) ===
Ein bone-slot ist ein EIGENER material-datablock (kopie), also sieht er den whole-actor-tint auf
slot[0] von natur aus NICHT. B4-v1 ließ es dabei — die bone-faces verloren den whole-actor-tint.
Das ist FALSCH: BBS wendet whole-form UND per-bone ZUSAMMEN an, aber je kanal MIT ANDERER regel.
GEMESSEN am BBS-renderer (cml-fresh, drei schichten belegt):
  - COLOR = MULTIPLIKATIV. `CubicCubeRenderer.writeVertex` z.247:
        .color(this.r * group.color.r, this.g * group.color.g, this.b * group.color.b, ...)
    `this.r` = whole-form (`ModelFormRenderer` z.390 `color.mul(form.color)`), `group.color` =
    per-bone (`poseTransform.color`, `applyPose` z.267 `poseTransform.color.mul(value.color)`).
    → der bone-slot MUSS den whole-form-color-tint ERBEN und den per-bone-color DARÜBER MULTIPLIZIEREN.
  - GLOW = OVERRIDE-MIT-FALLBACK. `CubicCubeRenderer.resolveEffectiveGlow*` z.266-304:
        if (group.glowIntensity != 0F) return group.glowingColor.r;  else return getBaseGlowingR();
    hat der bone eigenen glow → bone-glow; SONST → whole-form-glow (die "base"-werte).
    → bone-slot ERBT whole-form-glow als fallback; per-bone-glow ERSETZT ihn wenn vorhanden.
  - PAINT = OVERRIDE-MIT-FALLBACK. `resolveEffectivePaint*` z.306-344, identisches muster wie glow
    (bedingung `group.paintColor.a != 0F`).
    → bone-slot ERBT whole-form-paint als fallback; per-bone-paint ERSETZT ihn wenn vorhanden.
Die java-`FormProperties.applyProperties` "which override standard properties" (z.133) meint die
limb-track- vs standard-POSE-kanäle (`pose` vs `pose:bone`), NICHT die color/glow/paint-whole-vs-
bone-frage — die entscheidet EINZIG der renderer oben. Deshalb erben wir hier je kanal nach genau
dieser gemessenen regel (`_whole_*`-payload aus `properties.color/glow/paint`).

    1. slot-split (bmesh): faces deren verts überwiegend zur bone-vertex-gruppe gehören → neuer
       slot `<basis>__bone_<name>` (kopie des basis-materials, damit textur/UV erhalten bleibt).
    2. node-kette pro slot: actor_material._ensure_nodes (tint-Mix + alpha-Math + glow-Mix) —
       DIESELBE kette wie whole-actor, nur pro-slot-material statt slot[0].
    3. keyframe: color -> tint.inputs[7] (rgb) + alpha-Math.inputs[1] (a); glow -> glow-Mix.inputs[7]
       (rgb) + bsdf["Emission Strength"] (intensity); paint -> BBS_Paint-Mix (via actor_paint-muster).

=== IDEMPOTENZ (re-apply darf nicht duplizieren) ===
- Slots werden über den namen `<basis>__bone_<name>` wiedergefunden (existiert der slot schon,
  wird er NICHT neu angelegt — kein slot-wachstum je poll).
- Die face-index-zuweisung ist deterministisch (dieselben verts → derselbe slot) → re-apply setzt
  denselben index nochmal (kein-op).
- Die keyframes werden VOR dem neu-legen chirurgisch entfernt (nur die eigenen slot-material-
  node-tree-fcurves), damit re-apply die kurve ersetzt statt sie zu verdoppeln.

=== GRENZEN (dokumentiert, wirft nie — Regel 0) ===
- Bone ohne vertex-gruppe / mesh ohne bone-weights → SKIP + log (kein throw). Das ist der
  häufige fall bei statischen mesh-actors ohne skinning.
- Vertex-gruppen die sich überlappen (ein vert in mehreren bone-gruppen): eine face landet im
  slot des bones mit dem HÖCHSTEN summierten gewicht über ihre verts (dominanz, wie fp_arm).
  Eine face kann in Blender nur EINEN slot zeigen — das ist eine echte grenze der granularität
  (ein per-VERT-material gibt es nicht), dokumentiert, nicht kaschiert.
- Paint: teilt die vorzeichen-grenze von actor_paint (ein Mix-node = ein blend-modus).

WIRFT NIE (timer-regel) — jeder fehlerpfad loggt.
"""
import bpy

try:
    import bmesh
except ImportError:
    bmesh = None

from . import actor_material

# subkurven-gruppen die einen bone material-tragend machen (spiegel PerBoneExporter B4-selektion).
_COLOR_KEYS = ("cr", "cg", "cb", "ca")
_PAINT_KEYS = ("pr", "pg", "pb", "pi")
_GLOW_KEYS = ("gr", "gg", "gb", "gi")

# slot-namens-schema: <basis-material>__bone_<bone> (idempotent wiederfindbar). "__bone_" trennt
# vom whole-actor-suffix "__<uuid8>" (actor_material) — die zwei kollidieren nie.
_BONE_SLOT_INFIX = "__bone_"

# paint-node-name (spiegel actor_paint._PAINT_NODE — eigener namespace, wir bauen ihn hier
# selbst statt actor_paint zu importieren, weil actor_paint auf slot[0] hart verdrahtet).
_PAINT_NODE = "BBS_Paint"

# KOEXISTENZ (L6): der whole-form-color-tint sitzt auf actor_material._TINT_NODE ("BBS_Tint") /
# ._ALPHA_NODE ("BBS_AlphaMul") — die ERBEN wir. Der PER-BONE-color multipliziert DARÜBER (BBS:
# this.r * group.color.r) → ein ZWEITER MULTIPLY-Mix hinter BBS_Tint. Eigene node-namen, damit
# _apply_interp beide sauber trennen kann (path_from_id-scoping).
_BONE_TINT_NODE = "BBS_BoneTint"       # per-bone color (MULTIPLY, hinter BBS_Tint = whole-form)
_BONE_ALPHA_NODE = "BBS_BoneAlphaMul"  # per-bone alpha (MULTIPLY, hinter BBS_AlphaMul = whole-form)

# whole-form-payload-schlüssel (spiegel actor_material.has_material_channels / actor_paint) —
# properties.color (r/g/b/a), .glow (r/g/b/intensity), .paint (r/g/b/intensity/shaderShadow).
_WHOLE_COLOR_KEYS = ("r", "g", "b", "a")
_WHOLE_GLOW_KEYS = ("r", "g", "b", "intensity")
_WHOLE_PAINT_KEYS = ("r", "g", "b", "intensity")


def has_bone_material(replay):
    """Trägt IRGENDEIN bone im `bones`-payload material-subkurven (color/paint/glow)? Wenn nicht,
    ist dieses modul ein reiner no-op (kein slot-split, kein bmesh-anfassen)."""
    bones = _bones_payload(replay)
    if not bones:
        return False
    for subs in bones.values():
        if not isinstance(subs, dict):
            continue
        if any(subs.get(k) for k in _COLOR_KEYS + _PAINT_KEYS + _GLOW_KEYS):
            return True
    return False


def _bones_payload(replay):
    """Das `bones`-objekt aus dem replay ziehen (properties.bones), oder {} wenn keins."""
    if not isinstance(replay, dict):
        return {}
    props = replay.get("properties")
    if not isinstance(props, dict):
        return {}
    bones = props.get("bones")
    return bones if isinstance(bones, dict) else {}


def _bones_with_material(replay):
    """{bone_name: {gruppe: {komponente: kf-liste}}} für alle bones mit material-subkurven.
    Ein bone ohne material-subkurve (reiner transform) fällt raus (kein slot-split für ihn)."""
    out = {}
    for bone_name, subs in _bones_payload(replay).items():
        if not isinstance(subs, dict):
            continue
        entry = {}
        color = {k: subs.get(k) for k in _COLOR_KEYS if subs.get(k)}
        paint = {k: subs.get(k) for k in _PAINT_KEYS if subs.get(k)}
        glow = {k: subs.get(k) for k in _GLOW_KEYS if subs.get(k)}
        if color:
            entry["color"] = color
        if paint:
            entry["paint"] = paint
        if glow:
            entry["glow"] = glow
        if entry:
            out[bone_name] = entry
    return out


def _whole_form_material(replay):
    """Die whole-form color/glow/paint-subkurven aus `properties` ziehen (die actor_material/
    actor_paint auf slot[0] anwenden) — DIE erben die bone-slots hier je nach kanal-regel.
    Gibt {"color":{r,g,b,a}, "glow":{r,g,b,intensity}, "paint":{r,g,b,intensity}} (nur nicht-leere
    komponenten je gruppe; fehlt eine gruppe ganz, fehlt der key). Reine payload-lese, wirft nie."""
    props = replay.get("properties") if isinstance(replay, dict) else None
    if not isinstance(props, dict):
        return {}
    out = {}
    color = props.get("color")
    if isinstance(color, dict):
        c = {k: color.get(k) for k in _WHOLE_COLOR_KEYS if color.get(k)}
        if c:
            out["color"] = c
    glow = props.get("glow")
    if isinstance(glow, dict):
        g = {k: glow.get(k) for k in _WHOLE_GLOW_KEYS if glow.get(k)}
        if g:
            out["glow"] = g
    paint = props.get("paint")
    if isinstance(paint, dict):
        p = {k: paint.get(k) for k in _WHOLE_PAINT_KEYS if paint.get(k)}
        if p:
            out["paint"] = p
    return out


def apply_bone_material(obj, replay, tick_scale):
    """Anschluss-punkt (aus actors._build_actors + _live_sync_actors, NACH apply_bone_pose).
    Weist pro bone mit material-props die faces seiner vertex-gruppe einem eigenen slot zu und
    keyframet dessen color/glow/paint-node-kette. Wirft NIE (timer-regel). Gibt anzahl gesetzter
    keys zurück (0 == nichts zu tun/übersprungen)."""
    try:
        return _apply_bone_material(obj, replay, tick_scale)
    except Exception as e:   # noqa: BLE001 — ein material-bau darf den import/tick nie killen
        print("[bbs-bridge] actor_bone_material failed (%s): %s"
              % (getattr(obj, "name", "?"), e))
        return 0


def _apply_bone_material(obj, replay, tick_scale):
    if obj is None:
        return 0
    bone_mats = _bones_with_material(replay)
    if not bone_mats:
        return 0   # kein bone mit material-subkurve: byte-identisch zu vor diesem modul

    # DER material-TRÄGER: bei gerigten BOBJ-actors ist `obj` die armature (0 slots), das mesh
    # lebt auf dem child (actor_material._material_holder-lehre).
    holder = actor_material._material_holder(obj)
    if holder is None or holder.type != 'MESH':
        print("[bbs-bridge] actor_bone_material: kein mesh-material-träger für '%s' — per-bone-"
              "material übersprungen (dokumentierte grenze: actor ohne mesh/material)"
              % getattr(obj, "name", "?"))
        return 0
    mesh = holder.data
    if not mesh.materials:
        print("[bbs-bridge] actor_bone_material: mesh '%s' hat kein basis-material — übersprungen"
              % holder.name)
        return 0

    # welche der payload-bones haben ÜBERHAUPT eine vertex-gruppe auf dem mesh? (bone-name ==
    # vertex-group-name bei BOBJ-skinning). Fehlt sie / hat das mesh keine gewichte -> skip + log.
    vgroups = holder.vertex_groups
    present = {}
    missing = []
    for bone_name, mats in bone_mats.items():
        vg = vgroups.get(bone_name)
        if vg is None:
            missing.append(bone_name)
            continue
        present[bone_name] = (vg.index, mats)
    if missing:
        print("[bbs-bridge] actor_bone_material: bones ohne vertex-gruppe auf '%s' (skip, "
              "dokumentierte grenze — kein bone-weight): %s"
              % (holder.name, ", ".join(sorted(missing))))
    if not present:
        print("[bbs-bridge] actor_bone_material: keine payload-bone hat eine vertex-gruppe auf "
              "'%s' (mesh ohne passende bone-weights) — per-bone-material übersprungen"
              % holder.name)
        return 0

    # -- 1. slot-split: pro bone einen eigenen slot <basis>__bone_<name> anlegen (idempotent) und
    #       die faces seiner vertex-gruppe darauf zeigen lassen. --
    base_mat = mesh.materials[0]
    base_name = base_mat.name if base_mat is not None else "BBS_BoneMat"
    slot_mat = {}          # bone_name -> das per-slot material
    slot_index = {}        # bone_name -> slot-index im mesh
    for bone_name in present:
        owned_name = base_name + _BONE_SLOT_INFIX + bone_name
        mat = _ensure_slot_material(mesh, base_mat, owned_name)
        if mat is None:
            continue
        idx = _slot_index_of(mesh, mat)
        if idx < 0:
            continue
        slot_mat[bone_name] = mat
        slot_index[bone_name] = idx

    if not slot_mat:
        print("[bbs-bridge] actor_bone_material: slot-anlage schlug für alle bones fehl auf '%s'"
              % holder.name)
        return 0

    # face-index-zuweisung (bmesh, dominanz pro face). Wirft nie (eigener guard).
    assigned = _assign_faces_to_slots(holder, mesh,
                                      {b: vg_idx for b, (vg_idx, _m) in present.items()
                                       if b in slot_index},
                                      slot_index)

    # -- 2/3. pro slot die node-kette bauen + keyframen (reuse actor_material). --
    # KOEXISTENZ (L6): der whole-form-tint/glow/paint aus properties.* wird je nach kanal-regel
    # in JEDEN bone-slot geerbt (color multiplikativ, glow/paint override-mit-fallback — BBS-mess).
    whole = _whole_form_material(replay)
    n_keys = 0
    for bone_name, mat in slot_mat.items():
        mats = present[bone_name][1]
        actor_material._ensure_nodes(mat)          # tint + alpha + glow (WIRE-IDEMPOTENT)
        n_keys += _key_slot_material(mat, mats, whole, tick_scale, bone_name, obj)

    print("[bbs-bridge] actor_bone_material: %d bone-slot(s) auf '%s' (%s), %d faces zugewiesen, "
          "%d keys" % (len(slot_mat), holder.name, ", ".join(sorted(slot_mat.keys())),
                       assigned, n_keys))
    return n_keys


def _ensure_slot_material(mesh, base_mat, owned_name):
    """Ein per-bone slot-material `owned_name` sicherstellen (kopie des basis-materials, damit
    textur/UV erhalten bleiben) und als slot am mesh anhängen falls noch nicht da. IDEMPOTENT —
    existiert der slot schon, wird er zurückgegeben (kein neuer slot, kein name-wachstum). Gibt
    das material oder None."""
    # schon als slot am mesh? (idempotenz — poll ruft das je welle)
    for m in mesh.materials:
        if m is not None and m.name == owned_name:
            return m
    # material-datablock wiederverwenden falls vorhanden (mehrere meshes/re-import), sonst kopieren
    mat = bpy.data.materials.get(owned_name)
    if mat is None:
        if base_mat is None:
            return None
        mat = base_mat.copy()
        mat.name = owned_name
    mesh.materials.append(mat)
    return mat


def _slot_index_of(mesh, mat):
    """Slot-index eines materials im mesh (-1 wenn nicht drin)."""
    for i, m in enumerate(mesh.materials):
        if m is mat:
            return i
    return -1


def _assign_faces_to_slots(obj, mesh, bone_to_vg, bone_to_slot):
    """Pro face den slot des DOMINANTEN bones setzen (summe der vert-gewichte über die face-verts;
    fp_arm-dominanz-muster statt binär "hat gewicht"). Faces ohne bone-gewicht bleiben auf slot 0
    (basis). Via bmesh (idempotent — deterministische zuweisung). Gibt zahl gesetzter faces.
    Wirft nie (eigener guard, bmesh-cleanup im finally)."""
    if bmesh is None:
        print("[bbs-bridge] actor_bone_material: bmesh nicht verfügbar — face-zuweisung "
              "übersprungen (slots existieren, aber keine face zeigt drauf)")
        return 0
    # vg-index -> slot-index (nur die bones die wirklich einen slot haben)
    vg_to_slot = {bone_to_vg[b]: bone_to_slot[b] for b in bone_to_vg if b in bone_to_slot}
    if not vg_to_slot:
        return 0
    bm = None
    assigned = 0
    try:
        bm = bmesh.new()
        bm.from_mesh(mesh)
        deform = bm.verts.layers.deform.active
        if deform is None:
            # mesh trägt keine vertex-gewichte -> nichts zuzuweisen (dokumentierte grenze)
            print("[bbs-bridge] actor_bone_material: mesh '%s' hat keine deform-gewichte "
                  "(deform-layer fehlt) — face-zuweisung übersprungen" % mesh.name)
            return 0
        bm.faces.ensure_lookup_table()
        for face in bm.faces:
            # dominanz: summiere je vg-index die gewichte über alle face-verts, wähle das maximum
            weights = {}
            for v in face.verts:
                dv = v[deform]
                for vg_idx in vg_to_slot:
                    w = dv.get(vg_idx, 0.0)
                    if w > 0.0:
                        weights[vg_idx] = weights.get(vg_idx, 0.0) + w
            if not weights:
                continue   # face ohne bone-gewicht -> bleibt auf ihrem aktuellen slot (basis 0)
            best_vg = max(weights, key=weights.get)
            new_idx = vg_to_slot[best_vg]
            if face.material_index != new_idx:
                face.material_index = new_idx
                assigned += 1
            elif face.material_index == new_idx:
                # idempotenz: schon korrekt zugewiesen (re-apply) — zählt nicht als neue zuweisung
                pass
        bm.to_mesh(mesh)
        mesh.update()
    except Exception as e:   # noqa: BLE001
        print("[bbs-bridge] actor_bone_material: bmesh face-zuweisung fehlgeschlagen (%s): %s"
              % (mesh.name, e))
    finally:
        if bm is not None:
            try:
                bm.free()
            except (ReferenceError, RuntimeError):
                pass
    return assigned


def _key_slot_material(mat, mats, whole, tick_scale, bone_name, obj):
    """Die color/glow/paint-subkurven eines bones auf die node-kette SEINES slot-materials keyframen
    (reuse actor_material._key_socket_component/_apply_interp). KOEXISTENZ (L6): der whole-form-tint
    (`whole` aus properties.*) wird je kanal nach der BBS-gemessenen regel MITGEBAKEN:
      - COLOR: whole-form auf BBS_Tint (erbt), per-bone auf BBS_BoneTint (MULTIPLY darüber) — beide
        F-curves überleben, das produkt whole*bone landet im bild (BBS: this.r * group.color.r).
      - GLOW/PAINT: override-mit-fallback — per-bone WENN vorhanden, SONST whole-form, auf DENSELBEN
        sockets (BBS: resolveEffectiveGlow/Paint fällt auf die base-werte zurück).
    Wirft nie. Gibt zahl geschriebener keys."""
    nt = getattr(mat, "node_tree", None)
    if nt is None:
        return 0
    whole = whole if isinstance(whole, dict) else {}
    n = 0
    # -- COLOR (MULTIPLIKATIV) -- whole-form -> BBS_Tint (geerbt), per-bone -> BBS_BoneTint (darüber).
    color = mats.get("color")
    whole_color = whole.get("color")
    if color or whole_color:
        tint = nt.nodes.get(actor_material._TINT_NODE)
        amul = nt.nodes.get(actor_material._ALPHA_NODE)
        # whole-form-color/alpha auf die geerbten actor_material-nodes (falls whole-form welche hat).
        # actor_material.apply_color_channels schreibt sie auf slot[0]; DIESER slot ist eine kopie,
        # die den link mitnimmt aber nicht die keyframes → wir baken sie hier selbst auf den slot.
        if whole_color:
            if tint is not None:
                for comp, idx in (("r", 0), ("g", 1), ("b", 2)):
                    n += actor_material._key_socket_component(
                        tint.inputs[7], whole_color.get(comp), idx, tick_scale)
            if amul is not None:
                n += actor_material._key_socket_component(
                    amul.inputs[1], whole_color.get("a"), 0, tick_scale)
        # per-bone-color -> BBS_BoneTint (MULTIPLY hinter BBS_Tint) + per-bone-alpha -> BBS_BoneAlphaMul.
        if color:
            bone_tint = _ensure_bone_tint(nt)
            if bone_tint is not None:
                for comp, idx in (("cr", 0), ("cg", 1), ("cb", 2)):
                    n += actor_material._key_socket_component(
                        bone_tint.inputs[7], color.get(comp), idx, tick_scale)
            bone_amul = _ensure_bone_alpha(nt)
            if bone_amul is not None:
                n += actor_material._key_socket_component(
                    bone_amul.inputs[1], color.get("ca"), 0, tick_scale)
    # -- GLOW (OVERRIDE-MIT-FALLBACK) -> glow-Mix.inputs[7] (rgb) + bsdf["Emission Strength"] --
    # BBS resolveEffectiveGlow* gatet ALL-OR-NOTHING auf `group.glowIntensity != 0` (nicht je
    # komponente): hat der bone glow → GANZE bone-glow; sonst GANZE whole-form-glow (die base-werte).
    # Wir spiegeln das: liegt EINE bone-glow-subkurve vor, gewinnt die bone-glow-gruppe komplett.
    glow = mats.get("glow")
    whole_glow = whole.get("glow")
    if glow or whole_glow:
        glow_node = nt.nodes.get(actor_material._GLOW_NODE)
        bsdf = next((nd for nd in nt.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
        use_bone_glow = bool(glow)   # bone hat eigenen glow → override; sonst whole-form-fallback
        rgb_src = (("gr", "gg", "gb") if use_bone_glow else ("r", "g", "b"))
        glow_src = glow if use_bone_glow else whole_glow
        if glow_node is not None and glow_src:
            for comp, idx in zip(rgb_src, (0, 1, 2)):
                n += actor_material._key_socket_component(
                    glow_node.inputs[7], glow_src.get(comp), idx, tick_scale)
        if bsdf is not None and "Emission Strength" in bsdf.inputs and glow_src:
            raw_i = glow_src.get("gi" if use_bone_glow else "intensity")
            intensity_kfs = actor_material._clamp_glow_intensity(raw_i, obj)
            n += actor_material._key_socket_component(
                bsdf.inputs["Emission Strength"], intensity_kfs, 0, tick_scale)
    # -- PAINT (OVERRIDE-MIT-FALLBACK) -> eigener BBS_Paint-Mix zwischen tint-kette und BSDF --
    paint = mats.get("paint")
    whole_paint = whole.get("paint")
    if paint or whole_paint:
        n += _key_paint(nt, paint, whole_paint, tick_scale, bone_name, obj)
    return n


def _ensure_bone_tint(nt):
    """Den per-bone-color-Mix `BBS_BoneTint` (MULTIPLY) HINTER actor_material._TINT_NODE einbauen +
    die links IMMER sicherstellen (WIRE-IDEMPOTENT wie actor_material/actor_paint). Kette wird:
    textur -> BBS_Tint(whole) -> BBS_BoneTint(per-bone) -> [paint...] -> BSDF.Base Color. Der
    per-bone-color MULTIPLIZIERT über den whole-form-tint (BBS: this.r * group.color.r). Gibt den
    node oder None (keine tint-kette da). Wirft nie (guard)."""
    try:
        bsdf = next((nd for nd in nt.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
        tint = nt.nodes.get(actor_material._TINT_NODE)
        if bsdf is None or tint is None:
            return None
        base_color = bsdf.inputs["Base Color"]
        tint_result = tint.outputs[2]
        bone_tint = nt.nodes.get(_BONE_TINT_NODE)
        if bone_tint is None:
            bone_tint = nt.nodes.new("ShaderNodeMix")
            bone_tint.name = _BONE_TINT_NODE
            bone_tint.data_type = 'RGBA'
            bone_tint.blend_type = 'MULTIPLY'
            bone_tint.inputs[0].default_value = 1.0                     # Factor 1 = voller MULTIPLY
            bone_tint.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)    # B = per-bone-color (start weiß)
        # A (inputs[6]) = BBS_Tint.Result (whole-form): IMMER sicherstellen
        if not any(l.to_socket == bone_tint.inputs[6] and l.from_socket == tint_result
                   for l in nt.links):
            nt.links.new(tint_result, bone_tint.inputs[6])
        # den DIREKTEN BBS_Tint->BaseColor-link auflösen (actor_material._ensure_nodes legt ihn neu),
        # BBS_BoneTint sitzt jetzt dazwischen. NUR den direkten tint->base entfernen — NICHT den
        # bone_tint->base (der bleibt) und NICHT den tint->bone_tint (das ist die neue A-quelle).
        for l in list(nt.links):
            if l.to_socket == base_color and l.from_socket == tint_result:
                nt.links.remove(l)
        # BBS_BoneTint.Result -> Base Color: IMMER sicherstellen (paint routet später ggf. um —
        # actor_paint/_key_paint löst tint->base auf, hier ist es bone_tint->base; _key_paint muss
        # die tint-kette ab BBS_BoneTint abgreifen, siehe _key_paint tint_result-wahl).
        if not any(l.to_socket == base_color and l.from_socket == bone_tint.outputs[2]
                   for l in nt.links):
            nt.links.new(bone_tint.outputs[2], base_color)
        return bone_tint
    except Exception as e:   # noqa: BLE001 — never-throws-contract
        print("[bbs-bridge] actor_bone_material: BBS_BoneTint-wire fehlgeschlagen: %s" % e)
        return None


def _ensure_bone_alpha(nt):
    """Den per-bone-alpha-Math `BBS_BoneAlphaMul` (MULTIPLY) HINTER actor_material._ALPHA_NODE
    einbauen: whole-form-alpha (BBS_AlphaMul) * per-bone-alpha (BBS_BoneAlphaMul) -> BSDF.Alpha
    (BBS: this.a * group.color.a). Links WIRE-IDEMPOTENT. Gibt node oder None. Wirft nie."""
    try:
        bsdf = next((nd for nd in nt.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
        amul = nt.nodes.get(actor_material._ALPHA_NODE)
        if bsdf is None or amul is None:
            return None
        bsdf_alpha = bsdf.inputs["Alpha"]
        amul_result = amul.outputs[0]
        bone_amul = nt.nodes.get(_BONE_ALPHA_NODE)
        if bone_amul is None:
            bone_amul = nt.nodes.new("ShaderNodeMath")
            bone_amul.name = _BONE_ALPHA_NODE
            bone_amul.operation = 'MULTIPLY'
            bone_amul.inputs[1].default_value = 1.0                     # per-bone opacity-faktor (start 1)
        # input[0] = whole-form-alpha (BBS_AlphaMul.Result): IMMER sicherstellen
        if not any(l.to_socket == bone_amul.inputs[0] and l.from_socket == amul_result
                   for l in nt.links):
            nt.links.new(amul_result, bone_amul.inputs[0])
        # den direkten BBS_AlphaMul->BSDF.Alpha-link auflösen, BBS_BoneAlphaMul dazwischen
        for l in list(nt.links):
            if l.to_socket == bsdf_alpha and l.from_socket == amul_result:
                nt.links.remove(l)
        if not any(l.to_socket == bsdf_alpha and l.from_socket == bone_amul.outputs[0]
                   for l in nt.links):
            nt.links.new(bone_amul.outputs[0], bsdf_alpha)
        return bone_amul
    except Exception as e:   # noqa: BLE001
        print("[bbs-bridge] actor_bone_material: BBS_BoneAlphaMul-wire fehlgeschlagen: %s" % e)
        return None


def _key_paint(nt, paint, whole_paint, tick_scale, bone_name, obj):
    """Paint-overlay auf DIESEM slot-material (spiegel actor_paint, aber auf dem per-bone-slot-
    node-tree statt slot[0]). Baut den BBS_Paint-Mix HINTER die color-tint-kette (nach BBS_BoneTint
    falls da, sonst BBS_Tint) und keyframet rgb -> B-socket, |intensity| -> Factor.
    KOEXISTENZ (L6): OVERRIDE-MIT-FALLBACK — per-bone-paint (pr/pg/pb/pi) WENN vorhanden, SONST
    whole-form-paint (r/g/b/intensity) auf denselben sockets (BBS resolveEffectivePaint*-fallback).
    Wirft nie. Gibt zahl geschriebener keys."""
    paint = paint if isinstance(paint, dict) else {}
    whole_paint = whole_paint if isinstance(whole_paint, dict) else {}
    bsdf = next((nd for nd in nt.nodes if nd.type == 'BSDF_PRINCIPLED'), None)
    tint = nt.nodes.get(actor_material._TINT_NODE)
    if bsdf is None or tint is None:
        return 0
    base_color = bsdf.inputs["Base Color"]
    # die color-tint-kette endet bei BBS_BoneTint (per-bone-color multipliziert) wenn es den gibt,
    # sonst bei BBS_Tint (whole-form). Paint MUSS DA abgreifen (nach beiden color-multiplikationen).
    color_out_node = nt.nodes.get(_BONE_TINT_NODE) or tint
    tint_result = color_out_node.outputs[2]
    # BBS resolveEffectivePaint* gatet ALL-OR-NOTHING auf `group.paintColor.a != 0` — hat der bone
    # paint → GANZE bone-paint; sonst GANZE whole-form-paint (die base-werte). Kein komponenten-mix.
    use_bone_paint = bool(paint)
    p_src = paint if use_bone_paint else whole_paint
    rgb_keys = (("pr", "pg", "pb") if use_bone_paint else ("r", "g", "b"))
    int_key = "pi" if use_bone_paint else "intensity"
    eff_intensity = p_src.get(int_key)
    blend_type, mixed = _dominant_blend_type(eff_intensity)
    paint_node = nt.nodes.get(_PAINT_NODE)
    if paint_node is None:
        paint_node = nt.nodes.new("ShaderNodeMix")
        paint_node.name = _PAINT_NODE
        paint_node.data_type = 'RGBA'
        paint_node.inputs[0].default_value = 0.0                    # Factor 0 = kein paint
        paint_node.inputs[7].default_value = (1.0, 1.0, 1.0, 1.0)   # B = paint-farbe (start weiß)
    try:
        paint_node.blend_type = blend_type
    except (TypeError, ValueError):
        pass
    # A = ende der color-tint-kette (BBS_BoneTint oder BBS_Tint): IMMER sicherstellen (WIRE-IDEMPOTENZ).
    # zuerst ALTE A-links auf inputs[6] die von einem ANDEREN quell-socket kommen entfernen (falls die
    # bone-tint-kette erst nach dem ersten paint-bau dazu kam, hing A noch an BBS_Tint).
    for l in list(nt.links):
        if l.to_socket == paint_node.inputs[6] and l.from_socket != tint_result:
            nt.links.remove(l)
    if not any(l.to_socket == paint_node.inputs[6] and l.from_socket == tint_result
               for l in nt.links):
        nt.links.new(tint_result, paint_node.inputs[6])
    # den DIREKTEN color-out->BaseColor-link auflösen (actor_material/_ensure_bone_tint legt ihn neu),
    # paint sitzt jetzt dazwischen
    for l in list(nt.links):
        if l.to_socket == base_color and l.from_socket == tint_result:
            nt.links.remove(l)
    # BBS_Paint.Result -> Base Color: IMMER sicherstellen
    if not any(l.to_socket == base_color and l.from_socket == paint_node.outputs[2]
               for l in nt.links):
        nt.links.new(paint_node.outputs[2], base_color)
    if mixed:
        print("[bbs-bridge] actor_bone_material: paint-intensität für bone %r wechselt das "
              "vorzeichen (overlay<->darken) — ein Mix-node = ein blend-modus, gewählt: %s "
              "(dokumentierte grenze)" % (bone_name, blend_type))
    n = 0
    for comp, idx in zip(rgb_keys, (0, 1, 2)):
        n += actor_material._key_socket_component(
            paint_node.inputs[7], p_src.get(comp), idx, tick_scale)
    factor_kfs = _factor_from_intensity(eff_intensity)
    n += actor_material._key_socket_component(paint_node.inputs[0], factor_kfs, 0, tick_scale)
    return n


def _dominant_blend_type(intensity_kfs):
    """Blend-modus aus dem paint-intensity-verlauf (spiegel actor_paint._dominant_blend_type):
    überwiegen negative werte -> DARKEN (MULTIPLY), sonst overlay-tint (MIX). Gibt
    (blend_type, mixed_sign)."""
    if not intensity_kfs or not isinstance(intensity_kfs, (list, tuple)):
        return 'MIX', False
    neg = pos = 0
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
    return ('MULTIPLY' if neg > pos else 'MIX'), (neg > 0 and pos > 0)


def _factor_from_intensity(keyframes):
    """paint-intensity -> Mix-Factor: |intensity| auf 0..1 geclampt (spiegel actor_paint). Gibt eine
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
