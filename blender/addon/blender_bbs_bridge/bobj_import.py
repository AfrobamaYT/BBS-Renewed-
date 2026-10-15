"""BOBJ — McHorse's erweitertes OBJ-format (das echte BBS-modell + rig)

EMPIRISCH aus den echten dateien gelesen (nicht aus doku geraten), verifiziert an
assets/models/emoticons/{alex,steve,alex_simple,steve_simple}/*.bobj:

  v/vt/vn/f      standard-OBJ  -> Blenders OBJ-importer frisst das direkt und
                                  IGNORIERT die unbekannten BOBJ-zeilen stillschweigend.
  arm_bone       das rig       -> siehe _parse_arm_bone()
  vw             vertex weights-> siehe unten, POSITIONSGEBUNDEN
  arm_name/o_arm armature-name -> brauchen wir nicht (der name kommt vom replay)

`vw` IST NICHT self-contained: die zeile bindet an das ZULETZT gelesene `v`.
Der echte datei-aufbau ist ein interleave, KEIN block:
    v  -0.250000 0.750000 0.125000
    vw anchor 0.000000        <- gehoert zum v darueber
    vw body 0.700982          <- dito: mehrere bones pro vertex = mehrere zeilen
    v  ...
Darum: 5585 vw-zeilen fuer 2404 vertices (alex). Syntax ist `vw <bone_name> <weight>`
— ein bone pro zeile, KEIN count-praefix, KEIN bone-index.

Das ganze rig steht in BOBJ-koordinaten (Y-up). Wir muessen exakt dieselbe achsen-
konvertierung fahren wie der OBJ-importer (forward=-Z, up=Y), sonst sitzt die
armature nicht im mesh. Siehe _bobj_vec().
"""

import bpy
import os
import json
import shutil
import tempfile
import time
import zipfile
from mathutils import Matrix, Vector

from . import state
from .world_import import _set_alpha_clip


def _bobj_vec(x, y, z):
    """BOBJ (Y-up) -> Blender (Z-up): bl_x=x, bl_y=-z, bl_z=y.

    Das IST Blenders OBJ-import-konvertierung fuer forward=-Z/up=Y (und exakt die
    kamera-inverse aus dem header). Muss mit _import_bobj_geometry() identisch sein,
    sonst steht das rig neben dem mesh.
    Die transformation ist eine reine rotation (det=+1) -> gilt unveraendert fuer
    PUNKTE und fuer RICHTUNGSVEKTOREN (roll-achse), keine sonderbehandlung noetig.
    """
    return Vector((x, -z, y))


def _parse_arm_bone(line):
    """Eine `arm_bone`-zeile -> (name, parent|None, head, tail, z_axis) in BOBJ-koords.

    ECHTE syntax (aus slim.bobj verifiziert):
        arm_bone <name> [<parent>] <tx> <ty> <tz> <16 matrix-floats>
    Der erste bone (`anchor`) hat KEINEN parent -> 20 tokens; alle anderen 21.
    Robust erkannt ueber die LAENGE: die letzten 19 tokens sind immer floats, alles
    zwischen 'arm_bone' und diesen floats ist name [+ parent].

    ⚠️ FALLE — xyz IST DER TAIL, NICHT DER HEAD (am rig nachgerechnet):
      body     tail=(0,1.125,0) == low_body head -> bones haengen head-an-tail zusammen
      left_arm head=(0,1.375,0) -> tail=(0,1.125,0): der arm zeigt nach UNTEN (korrekt)
    Der HEAD steht in der translations-spalte der matrix. Die 16 floats sind ROW-MAJOR
    (Blender 2.79 matrix_local, zeile fuer zeile):
        m[0] m[1] m[2] m[3]        -> spalte 3 = (m[3],m[7],m[11])  = head
        m[4] m[5] m[6] m[7]        -> spalte 1 = (m[1],m[5],m[9])   = bone-richtung (Y)
        m[8] m[9] m[10] m[11]      -> spalte 2 = (m[2],m[6],m[10])  = bone-Z = ROLL
        m[12] m[13] m[14] m[15]
    Gegenprobe: anchor spalte3=(0,0.75,0)=head, xyz=(0,0.75,0.25)=tail, spalte1=(0,0,1)
    = +Z = exakt die head->tail-richtung. Passt fuer alle 20 bones.
    Wuerde man xyz als head nehmen und den tail raten, stuende das rig falsch.

    Gibt None zurueck wenn die zeile nicht parsebar ist (nie werfen — kaputte zeile
    darf den import nicht killen).
    """
    toks = line.split()
    if len(toks) < 21:            # 'arm_bone' + name + 19 floats
        return None
    n0 = len(toks) - 19
    try:
        nums = [float(t) for t in toks[n0:]]
    except ValueError:
        return None
    head_toks = toks[1:n0]
    if not head_toks:
        return None
    # ⚠️ bone-namen MIT leerzeichen wuerden hier falsch splitten. Das ganze format ist
    # whitespace-getrennt, also koennte BBS' eigener leser das genauso wenig — kein fix
    # moeglich ohne die datei anders zu schreiben. Alle 20 bones nutzen underscores.
    name = head_toks[0]
    parent = head_toks[1] if len(head_toks) > 1 else None
    tail = (nums[0], nums[1], nums[2])
    m = nums[3:19]
    head = (m[3], m[7], m[11])       # translations-spalte = bone-head
    z_axis = (m[2], m[6], m[10])     # bone-lokale Z-achse -> roll
    return name, parent, head, tail, z_axis


def _parse_bobj_extras(path):
    """Die BOBJ-EXTRA-zeilen lesen (die der OBJ-importer wegwirft): rig + weights.

    Gibt (bones, weights, bad) zurueck:
      bones   [(name, parent, head, tail, z_axis), ...] in datei-reihenfolge
      weights [[(bone_name, weight), ...], ...] — index = vertex-index (0-basiert),
              parallel zu den `v`-zeilen. Genau DAS ist die vw-bindung (siehe header).
      bad     anzahl unlesbarer zeilen (nur fuer den report)
    """
    bones = []
    weights = []
    cur = None
    bad = 0
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if line.startswith("v "):
                    cur = []              # ab jetzt gehoeren vw-zeilen zu DIESEM vertex
                    weights.append(cur)
                elif line.startswith("vw "):
                    toks = line.split()
                    if cur is None or len(toks) < 3:
                        bad += 1          # vw vor dem ersten v = kaputte datei
                        continue
                    try:
                        cur.append((toks[1], float(toks[2])))
                    except ValueError:
                        bad += 1
                elif line.startswith("arm_bone "):
                    b = _parse_arm_bone(line)
                    if b is None:
                        bad += 1
                    else:
                        bones.append(b)
    except OSError as e:
        print("[bbs-bridge] cannot read bobj %s: %s" % (path, e))
        return [], [], bad
    return bones, weights, bad


def _resolve_bbs_link(link):
    """BBS-Link -> absoluter pfad unter state.BBS_ASSETS_DIR, oder None.

    Format ist `<source>:<path>` (Link.toString() = source + ':' + path), z.B.
    "assets:models/player/alex/alex.png". Der 'assets'-source IST state.BBS_ASSETS_DIR
    (empirisch: der pfad existiert genau dort).
    """
    if not isinstance(link, str) or not link.strip():
        return None
    rel = link.strip().replace("\\", "/")
    head, sep, tail = rel.partition(":")
    # ':' nur als namespace-trenner werten wenn davor kein pfad steht — sonst wuerde
    # ein absoluter windows-pfad ("C:/...") als namespace "C" zerlegt.
    if sep and "/" not in head and len(head) > 1:
        rel = tail
    rel = rel.lstrip("/")
    if not rel:
        return None
    p = os.path.abspath(os.path.join(state.BBS_ASSETS_DIR, rel))
    # traversal-guard: ein '../..' im link darf nichts ausserhalb des asset-baums laden
    root = os.path.abspath(state.BBS_ASSETS_DIR)
    try:
        if os.path.commonpath([p, root]) != root:
            return None
    except ValueError:      # anderes laufwerk -> per definition ausserhalb
        return None
    return p


def _packaged_bbs_texture(link):
    """Extract a missing built-in skin from the active BBS mod into bridge-owned cache.

    CurseForge's config/bbs/assets overlays packaged assets. BBS reads through its
    provider, but Blender's image loader needs a real file. Keep the package bytes
    outside the overlay so this fallback cannot change what BBS itself loads.
    """
    target = _resolve_bbs_link(link)
    if target is None or os.path.isfile(target):
        return target
    assets = os.path.realpath(state.BBS_ASSETS_DIR)
    target = os.path.realpath(target)
    if os.path.commonpath([target, assets]) != assets:
        return None
    if os.path.basename(assets) != 'assets' or os.path.basename(os.path.dirname(assets)) != 'bbs':
        return None
    game_dir = os.path.dirname(os.path.dirname(os.path.dirname(assets)))
    relative = os.path.relpath(target, assets).replace(os.sep, '/')
    entry = 'assets/bbs/assets/' + relative
    mods = os.path.join(game_dir, 'mods')
    if not os.path.isdir(mods):
        return None
    for name in sorted(os.listdir(mods)):
        if not name.startswith('bbs-') or not name.endswith('.jar'):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, name)) as package:
                info = package.getinfo(entry)
                if info.file_size > 16 * 1024 * 1024:
                    raise ValueError('packaged texture exceeds 16 MiB')
                cache = os.path.join(game_dir, 'blenderbridge_export', 'packaged_assets',
                                     str(os.stat(os.path.join(mods, name)).st_mtime_ns), relative)
                if not os.path.isfile(cache):
                    os.makedirs(os.path.dirname(cache), exist_ok=True)
                    with open(cache, 'wb') as output:
                        output.write(package.read(info))
                return cache
        except (KeyError, OSError, zipfile.BadZipFile, ValueError) as error:
            if not isinstance(error, KeyError):
                print('[bbs-bridge] packaged texture %s: %s' % (entry, error))
    return None


def _find_bobj_for_form(form):
    """<state.BBS_ASSETS_DIR>/models/<key>/*.bobj -> erster treffer, sonst None.

    `key` MUSS der modell-schluessel sein ("emoticons/alex"), also `replay["formModel"]`.
    NICHT `replay["form"]` uebergeben: das ist `Form.getDisplayName()` und liefert den
    user-vergebenen namen sobald einer gesetzt ist ("Bob") — der trifft nie ein verzeichnis,
    und der actor landete still als empty. Java sendet formModel seit 2026-07-16
    (ActorProtocol.REPLAY_FORM_MODEL); 'form' bleibt nur fallback fuer alte payloads.
    """
    if not isinstance(form, str) or not form.strip():
        return None
    d = _resolve_bbs_link("models/" + form.strip().replace("\\", "/").strip("/"))
    if d is None or not os.path.isdir(d):
        return None
    try:
        names = sorted(n for n in os.listdir(d) if n.lower().endswith(".bobj"))
    except OSError:
        return None
    return os.path.join(d, names[0]) if names else None


def _read_bobj_config(bobj_path):
    """config.json NEBEN der .bobj (textur, scale, culling). Fehlt/kaputt -> {}."""
    p = os.path.join(os.path.dirname(bobj_path), "config.json")
    if not os.path.isfile(p):
        return {}
    try:
        with open(p, "r", encoding="utf-8") as fh:
            cfg = json.load(fh)
        return cfg if isinstance(cfg, dict) else {}
    except (OSError, json.JSONDecodeError, UnicodeDecodeError) as e:
        print("[bbs-bridge] bad config.json %s: %s" % (p, e))
        return {}


def _import_bobj_geometry(path):
    """Die standard-OBJ-teile der .bobj ueber Blenders importer holen -> neue objekte.

    Blenders importer entscheidet nach DATEI-ENDUNG -> die .bobj muss als .obj daliegen,
    also kopieren wir sie in ein temp-verzeichnis. Die BOBJ-extra-zeilen (vw/arm_bone/
    arm_name/o_arm) ignoriert der importer stillschweigend — das ist der ganze trick.
    forward/up wie beim welt-import: deckungsgleich mit der gesyncten kamera.
    """
    tmpdir = tempfile.mkdtemp(prefix="bbs_bobj_")
    tmp = os.path.join(tmpdir, os.path.splitext(os.path.basename(path))[0] + ".obj")
    before = {o.name for o in bpy.data.objects}
    try:
        shutil.copyfile(path, tmp)
        bpy.ops.wm.obj_import(filepath=tmp, forward_axis='NEGATIVE_Z', up_axis='Y')
    except (OSError, RuntimeError, TypeError) as e:
        print("[bbs-bridge] bobj geometry import failed (%s): %s" % (path, e))
        return []
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)
    objs = [o for o in bpy.data.objects if o.name not in before]
    # Blenders OBJ-importer drueckt forward/up als OBJEKT-rotation aus (90,0,0) und laesst
    # die vertex-DATEN Y-up. Die bones bekommen die konversion aber via _bobj_vec() in die
    # DATEN — mesh und armature lebten dadurch in verschiedenen raeumen: rig-anzeige stand
    # quer zum mesh, und die actor-rotation kippte die figur (user-beschwerde 2026-07-17).
    # Also die importer-rotation in die vertices einbacken -> daten Z-up, transform identity,
    # exakt der raum von _bobj_vec().
    for o in objs:
        if o.type == 'MESH':
            o.data.transform(o.matrix_world)
            o.matrix_world = Matrix.Identity(4)
    return objs


def _build_bobj_armature(bones, name, coll):
    """Die arm_bone-zeilen als echte Blender-armature bauen -> (armature_obj, name_map).

    name_map: BOBJ-bone-name -> FINALER Blender-bone-name. Blender haengt bei einer
    namens-kollision '.001' an, und die vertex-groups muessen auf den FINALEN namen
    hoeren, sonst deformiert nichts.

    edit_bones gibt es NUR im EDIT-mode -> bpy.ops.object.mode_set ist unvermeidbar.
    Laeuft im timer (main thread), das ist ok — der welt-import nutzt dort auch ops.
    """
    arm_data = bpy.data.armatures.new(name)
    arm_obj = bpy.data.objects.new(name, arm_data)
    # Bones liegen IM mesh — ohne in-front sind sie unsichtbar/unklickbar (user-beschwerde
    # 2026-07-17). STICK statt OCTAHEDRAL: die octaeder fuellen den koerper und verdecken
    # sich gegenseitig, sticks bleiben greifbar duenn.
    arm_obj.show_in_front = True
    arm_data.display_type = 'STICK'
    coll.objects.link(arm_obj)

    view_layer = bpy.context.view_layer
    prev_active = view_layer.objects.active
    name_map = {}
    try:
        # in EDIT-mode wechseln geht nur aus dem OBJECT-mode heraus: haengt der user
        # gerade in edit/pose auf einem anderen objekt, erst dort raus.
        if prev_active is not None and prev_active.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
        view_layer.objects.active = arm_obj
        bpy.ops.object.mode_set(mode='EDIT')

        eb = arm_data.edit_bones
        created = {}
        for bname, _parent, head, tail, z_axis in bones:
            # PRO BONE gekapselt: ein einzelner degenerierter bone darf nicht die
            # restlichen 19 + das parenting mitreissen (sonst haette das mesh ein
            # halbes rig und wuerde still falsch deformieren).
            try:
                b = eb.new(bname)
                b.head = _bobj_vec(*head)
                b.tail = _bobj_vec(*tail)
                if (b.tail - b.head).length < 1e-5:
                    # Blender WIRFT null-lange bones beim verlassen des edit-modes weg ->
                    # der bone waere weg und seine vertex-group ohne wirkung.
                    b.tail = b.head + Vector((0.0, 0.0, 0.01))
                # roll aus der bone-matrix statt default 0: align_roll dreht den bone so,
                # dass seine lokale Z-achse auf den vektor zeigt = exakt die exportierte
                # orientierung. Ohne das rotieren die animationen um die falsche achse.
                # Ein null-vektor (degenerierte matrix) wuerde werfen -> roll bleibt 0.
                if any(abs(c) > 1e-9 for c in z_axis):
                    b.align_roll(_bobj_vec(*z_axis))
                created[bname] = b
                name_map[bname] = b.name
            except (RuntimeError, TypeError, ValueError) as e:
                print("[bbs-bridge] bone %r skipped: %s" % (bname, e))

        # parents ERST im 2. pass: ein parent kann in der datei nach seinem kind stehen.
        for bname, parent, _h, _t, _z in bones:
            if parent and parent in created and bname in created:
                created[bname].parent = created[parent]
                # use_connect bleibt FALSE: kind-head == parent-TAIL gilt hier NICHT
                # durchgehend (body.head sitzt auf anchor.HEAD, nicht auf dessen tail).
                # True wuerde Blender den head ans parent-tail zwingen = rig verbogen.
    except (RuntimeError, TypeError, AttributeError) as e:
        print("[bbs-bridge] armature build failed for %r: %s" % (name, e))
    finally:
        # IMMER zurueck in den OBJECT-mode, sonst haengt Blender im edit-mode fest.
        try:
            bpy.ops.object.mode_set(mode='OBJECT')
        except (RuntimeError, TypeError):
            pass
        try:
            if prev_active is not None:
                view_layer.objects.active = prev_active
        except (ReferenceError, RuntimeError):
            pass
    return arm_obj, name_map


def _apply_bobj_weights(mesh_obj, weights, name_map):
    """Die vw-daten als vertex-groups aufs mesh legen. Gibt (n_assigned, n_groups).

    weights[i] gehoert zu vertex i — das setzt voraus, dass Blenders OBJ-importer die
    `v`-zeilen 1:1 und in reihenfolge uebernimmt. Das tut er (eine mesh-vertex pro
    v-zeile; UV-seams splitten NICHT, weil Blender UVs pro loop speichert). Wir
    VERIFIZIEREN es trotzdem ueber die anzahl und steigen bei abweichung aus, statt
    weights auf die falschen vertices zu schmieren.
    """
    verts = mesh_obj.data.vertices
    if len(verts) != len(weights):
        print("[bbs-bridge] vw skipped: %d mesh verts != %d bobj verts (%s)"
              % (len(verts), len(weights), mesh_obj.name))
        return 0, 0
    groups = {}
    n_assigned = 0
    missing = set()
    for idx, entries in enumerate(weights):
        for bone, w in entries:
            final = name_map.get(bone)
            if final is None:
                missing.add(bone)
                continue
            if w <= 0.0:
                # gewicht 0 = keine wirkung. Die datei listet die bones trotzdem auf
                # (alex: viele 0.000000). Weglassen = identische deformation, aber
                # sauberes weight-painting.
                continue
            vg = groups.get(final)
            if vg is None:
                vg = mesh_obj.vertex_groups.get(final) or mesh_obj.vertex_groups.new(name=final)
                groups[final] = vg
            vg.add([idx], w, 'REPLACE')
            n_assigned += 1
    if missing:
        print("[bbs-bridge] vw references unknown bones (ignored): %s"
              % ", ".join(sorted(missing)))
    return n_assigned, len(groups)


def _bobj_material(img, culling):
    """Material fuer ein BOBJ-modell (pro textur EINS, wiederverwendet).

    Wiederverwendung ist absicht: 10 actors mit demselben skin teilen ein material
    statt 10x 'BBS_MDL_alex.png.001' zu erzeugen.
    """
    mat_name = "BBS_MDL_" + img.name
    mat = bpy.data.materials.get(mat_name)
    if mat is not None:
        return mat
    mat = bpy.data.materials.new(mat_name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = img
    # DER MC-look: skins sind 64x64. Mit Blenders default 'Linear' ist der skin
    # verwaschen/Blender-ig statt pixelig. Gleiche begruendung wie in _apply_mc_look().
    tex.interpolation = 'Closest'
    tex.location = (-340, 260)
    if bsdf is not None:
        nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
        # Skins haben einen echten alpha-kanal (hat-/jacket-layer ist dort transparent).
        # NUR verdrahten wenn die PNG wirklich RGBA ist: bei depth<=24 liefert der
        # Alpha-output die HELLIGKEIT -> dunkle pixel wuerden loecher (dieselbe falle
        # die in _apply_mc_look() die block-seiten zerschossen hat).
        if img.depth > 24 and "Alpha" in tex.outputs:
            nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
            _set_alpha_clip(mat)
        if "Roughness" in bsdf.inputs:
            bsdf.inputs["Roughness"].default_value = 0.9   # skins sind matt, nicht plastik
    if hasattr(mat, "use_backface_culling"):
        mat.use_backface_culling = bool(culling)   # config.json "culling"
    return mat


def _material_ordner_textur(mesh_obj, bobj_path):
    """BOBJ-v2-aufloesung (2026-08-16): neue modelle (rig-pipeline, verity_*) haben KEIN
    config.json mehr — texturen liegen pro MATERIAL in einem ordner
    <modeldir>/textures/<material>/ oder <modeldir>/<material>/, und BBS laedt daraus die
    default-textur (CubicModelLoader.java:132 "Textures live in a folder named after the
    material"). Material = mesh-name ohne Blenders .NNN-duplikat-suffix. Erste bilddatei
    (sortiert) = default — dasselbe, was BBS anzeigt."""
    base = os.path.dirname(bobj_path)
    mat_name = mesh_obj.name
    if len(mat_name) > 4 and mat_name[-4] == '.' and mat_name[-3:].isdigit():
        mat_name = mat_name[:-4]
    for ordner in (os.path.join(base, "textures", mat_name), os.path.join(base, mat_name)):
        if not os.path.isdir(ordner):
            continue
        for fn in sorted(os.listdir(ordner)):
            if fn.lower().endswith((".png", ".jpg", ".jpeg", ".tga")):
                return os.path.join(ordner, fn)
    return None


def _apply_bobj_texture(mesh_obj, cfg, bobj_path):
    """Skin aufs mesh: config.json-link (alt) oder material-ordner (BOBJ v2).
    Gibt eine status-zeile fuer den report zurueck.

    ⚠️ Die textur liegt NICHT neben der .bobj: alex' modell steht in
    models/emoticons/alex/, sein skin aber in models/player/alex/alex.png. Ein
    "such die .png im selben ordner"-fallback findet also GAR NICHTS — config.json
    bzw. der material-ordner sind die verlaesslichen quellen.
    """
    link = cfg.get("texture")
    if link:
        p = _resolve_bbs_link(link)
        if p is None:
            return "texture link outside assets dir: %r" % (link,)
    else:
        p = _material_ordner_textur(mesh_obj, bobj_path)
        if p is None:
            return "no 'texture' in config.json"
    if not os.path.isfile(p):
        packaged = _packaged_bbs_texture(link) if link else None
        if packaged is None or not os.path.isfile(packaged):
            return "texture missing on disk and in BBS package: %s" % p
        p = packaged
    try:
        # check_existing: dieselbe PNG fuer alle actors = EIN image-datablock,
        # kein alex.png.001/.002-muell pro import.
        img = bpy.data.images.load(p, check_existing=True)
    except RuntimeError as e:
        return "texture load failed: %s" % e
    mat = _bobj_material(img, cfg.get("culling", False))
    mesh_obj.data.materials.clear()
    mesh_obj.data.materials.append(mat)
    return None


def _bbs_rotation_modes(arm_obj, path):
    """Drehmodus je Knochen wie BBS ihn auswertet (BOBJBone.eulerOrdnung/drehModus aus
    models/<id>/rig.json, knochen.<name>.dreh_modus); ohne Angabe XYZ. Blenders Vorgabe QUATERNION
    machte aus jedem Euler-Key einen Kreuzfall, der nur an den Keys exakt war (c1: 11 Grad
    dazwischen, gemessen 2026-10-14)."""
    flags = {}
    rig = os.path.join(os.path.dirname(path), "rig.json")
    if os.path.isfile(rig):
        try:
            with open(rig, encoding="utf-8") as f:
                flags = (json.load(f) or {}).get("knochen") or {}
        except (OSError, ValueError) as e:
            print("[bbs-bridge] rig.json unlesbar (%s): %s" % (rig, e))
    for pb in arm_obj.pose.bones:
        mode = str((flags.get(pb.name) or {}).get("dreh_modus") or "XYZ")
        if mode in ('XYZ', 'XZY', 'YXZ', 'YZX', 'ZXY', 'ZYX', 'QUATERNION', 'AXIS_ANGLE'):
            pb.rotation_mode = mode


def _import_bobj(path, coll, name=None):
    """Ein BOBJ-modell komplett importieren -> ROOT-objekt (armature, sonst mesh).

    Root = das objekt, das im actor-system an der stelle des empties steht: es traegt
    die custom-props und die F-curves, das mesh haengt als kind dran.
    Gibt None zurueck wenn nichts brauchbares rauskam (aufrufer faellt auf empty zurueck).
    """
    t0 = time.time()
    label = name or os.path.splitext(os.path.basename(path))[0]
    bones, weights, bad = _parse_bobj_extras(path)
    cfg = _read_bobj_config(path)

    objs = _import_bobj_geometry(path)
    if not objs:
        return None
    for o in objs:
        for c in list(o.users_collection):
            c.objects.unlink(o)
        coll.objects.link(o)
    meshes = [o for o in objs if o.type == 'MESH']
    if not meshes:
        for o in objs:
            bpy.data.objects.remove(o, do_unlink=True)
        print("[bbs-bridge] bobj had no mesh: %s" % path)
        return None

    root = meshes[0]
    n_w = n_g = 0
    if bones:
        arm_obj, name_map = _build_bobj_armature(bones, label, coll)
        _bbs_rotation_modes(arm_obj, path)
        for me in meshes:
            w, g = _apply_bobj_weights(me, weights, name_map)
            n_w += w
            n_g += g
            mod = me.modifiers.new(name="Armature", type='ARMATURE')
            mod.object = arm_obj
            # parenten OHNE ops: die armature steht noch auf identity, also ist die
            # parent-inverse identity -> kein matrix_parent_inverse noetig. Der scale
            # unten wirkt dadurch auf armature UND mesh gemeinsam.
            me.parent = arm_obj
        root = arm_obj
    else:
        print("[bbs-bridge] bobj without arm_bone lines (static model): %s" % path)

    tex_note = None
    for me in meshes:
        tex_note = _apply_bobj_texture(me, cfg, path)

    # config.json "scale" ist das was BBS beim rendern anlegt -> ohne das ist das modell
    # zu gross. Achsen mitdrehen wie _bobj_vec (y<->z), damit auch nicht-uniforme scales
    # stimmen; das vorzeichen ist bei einem scale egal (betrag).
    sc = cfg.get("scale")
    if isinstance(sc, (list, tuple)) and len(sc) == 3:
        try:
            root.scale = (abs(float(sc[0])), abs(float(sc[2])), abs(float(sc[1])))
        except (TypeError, ValueError):
            pass

    print("[bbs-bridge] bobj '%s': %d bones, %d verts, %d weights/%d groups, tex=%s%s (%.2fs)"
          % (label, len(bones), len(weights), n_w, n_g,
             "ok" if tex_note is None else tex_note,
             ", %d bad lines" % bad if bad else "", time.time() - t0))
    return root


def _packaged_model_path(replay):
    """Accept only BBS' content-identified model cache, never an arbitrary peer path."""
    import hashlib
    source = (replay.get('properties') or {}).get('packagedModel')
    if not source:
        return None
    if source.get('model') and source['model'] != replay.get('formModel'):
        return None  # A form variant must not reuse another model's payload.
    if source.get('error'):
        raise RuntimeError('Packaged model export failed: ' + str(source['error']))
    root = os.path.realpath(os.path.join(state.BBS_ASSETS_DIR, '.bridge-models'))
    path = os.path.realpath(str(source.get('bobj') or ''))
    if os.path.commonpath([root, path]) != root or not path.endswith('.bobj'):
        raise ValueError('Packaged model path is outside bridge cache')
    with open(path, 'rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != source.get('sha256'):
        raise ValueError('Packaged model content changed after export')
    return path
