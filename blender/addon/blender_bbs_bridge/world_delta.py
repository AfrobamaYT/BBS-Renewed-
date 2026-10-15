"""Task 3.6 (live-block-delta, das USP-novum): BLOCK_DELTA-consumer — ein block aendert sich
im laufenden MC; schnelle Patch-Vorschau und nachfolgender nativer Neuaufbau in Blender.

Java-seite: com.blenderbridge.world.BlockDeltaTracker (budgetierter snapshot-diff-sweep ueber
die region des letzten erfolgreichen exports, ~1s voll-sweep) pusht UNSOLICITED::

    {"type":"BLOCK_DELTA","fields":{
        "region":[minX,minY,minZ,maxX,maxY,maxZ],
        "count":N, "reExport":bool,
        "changes":["x y z minecraft:stone minecraft_block_stone", ...]}}

Jeder change: 3 ints (MC-blockkoordinaten) + block-id + OPTIONALER 5. token = material-key-
hint (safeName des particle-sprites, passt im untinted-normalfall exakt auf ein material im
importierten MC_World-pool). Schema ist mit BlockDeltaTracker.java abgestimmt (dort "WIRE
FORMAT") — aenderung nur nach ruecksprache.

PUSH, NICHT SOLICITED: anders als WORLD_EXPORT_RESULT gibt es hier keinen eigenen request,
also kein pending-gate (dossier-merkregel: push vs. solicited). Der schutz gegen fremde
pushes (java broadcastet an ALLE clients, und ein ANDERER agent-export re-targetet den
java-tracker auf SEINE region) ist stattdessen das REGION-GATE: wir wenden nur pushes an,
deren region EXAKT die region unseres EIGENEN letzten welt-imports ist (`arm_from_meta`,
aufgerufen aus world_import._import_world_obj — der sitzt hinter dem solicited+requestId-
gate, ist also verlaesslich "unser" import). Nicht armiert / fremde region / position
ausserhalb -> ignorieren + zaehlen, nie anwenden.

V1-MESH-UPDATE (bewusste grenzen, dokumentiert statt kaschiert):
- Geaenderter/neuer block -> EIN einzelwuerfel-patch-objekt (``BBS_Delta_x_y_z``) in der
  eigenen collection ``MC_World_Delta`` mit dem material aus dem bestehenden pool (hint ->
  heuristik -> grauer ``BBS_Delta_Fallback``). Ein wuerfel = 6 volle faces mit 0..1-UV auf
  EINEM material — korrekt fuer full-cube-bloecke, repraesentativ (particle-sprite) fuer
  multi-textur-bloecke wie grass_block. Kein culling gegen nachbarn (v1).
- Block entfernt (air) -> unser Patch-Wuerfel wird entfernt. world_refresh ersetzt
  danach das unveraenderte Basis-Mesh durch einen korrelierten nativen Neu-Export.
  Dadurch verschwinden auch urspruenglich importierte Bloecke samt Nachbar-Culling.
  Eingefrorene oder lokal bearbeitete MC_World-Daten pausieren den Neuaufbau sichtbar.
- reExport:true (batch > java-BATCH_CAP, WorldEdit-groessenordnung) -> KEINE cubes, nur
  log + status "re-export empfohlen" — hunderte einzel-patches waeren langsamer UND
  haesslicher als ein sauberer re-import.

TIMER-REGEL (dossier tick.py): jede entkommene exception killt den timer STILL. Der
dispatch-zweig in tick.py ruft deshalb NUR :func:`handle_block_delta`, und DIESE funktion
faengt intern alles (log + weiterlaufen), wie die actors-handler.

Invariante 5 (users instanz): alles prefixed (``BBS_Delta_*``, ``MC_World_Delta``),
:func:`clear` entfernt rueckstandslos. Kein UI-eintrag hier (ui.py = fremdes territorium;
sichtbarkeit = viewport selbst + print-log; UI-status ist ein folge-task).
"""

import bpy
import json
import os


DELTA_COLLECTION = "MC_World_Delta"
FALLBACK_MATERIAL = "BBS_Delta_Fallback"

# ids die "block weg" bedeuten (java sendet die echte id; state.isAir() deckt genau diese 3)
_AIR_IDS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

# ---- modul-lokaler state (kein state.py-edit noetig — replay_anim-muster) ----
_armed_region = None      # tuple(minX,minY,minZ,maxX,maxY,maxZ) unseres EIGENEN letzten imports
_apply_count = 0          # gates: erzeugte/ersetzte patch-wuerfel
_remove_count = 0         # gates: entfernte patch-wuerfel (block -> air)
_noop_count = 0           # gates: idempotente skips (gleicher block schon gepatcht)
_ignore_count = 0         # gates: verworfen (nicht armiert / fremde region / pos ausserhalb)
_reexport_count = 0       # gates: empfangene re-export-empfehlungen
_last_status = "idle"


def arm_from_meta(obj_path):
    """Nach einem EIGENEN erfolgreichen welt-import: region aus world_meta.json (liegt neben
    der OBJ, feld "region": [6 ints] — beide java-pfade schreiben es) uebernehmen und alte
    patches raeumen (neuer export = neue baseline, alle bisherigen deltas sind jetzt im
    basis-mesh eingebacken). Fehlt das manifest (aelterer java-stand): DISARM + log — lieber
    ehrlich kein live-delta als deltas gegen eine unbekannte region anwenden."""
    global _armed_region, _last_status
    clear()   # alte patches sind mit dem frischen import obsolet — weg, bevor was schiefgeht
    meta_path = os.path.join(os.path.dirname(obj_path), "world_meta.json")
    region = None
    try:
        with open(meta_path, "r", encoding="utf-8") as fh:
            region = json.load(fh).get("region")
    except (OSError, ValueError) as e:
        print("[bbs-bridge] world_delta: world_meta.json unlesbar (%s) — live-delta AUS" % e)
    if (not isinstance(region, list) or len(region) != 6
            or not all(isinstance(v, int) for v in region)):
        _armed_region = None
        _last_status = "disarmed (kein region-manifest)"
        return None
    _armed_region = tuple(region)
    _last_status = "armed %s" % (_armed_region,)
    print("[bbs-bridge] world_delta armed:", _armed_region)
    return _armed_region


def disarm():
    global _armed_region, _last_status
    _armed_region = None
    _last_status = "disarmed"


def clear():
    """Rueckstandslos (invariante 5): alle patch-objekte + meshes + collection + fallback-
    material (wenn unbenutzt) entfernen. Reihenfolge mesh-vor-material wie world_import.
    _clear_world_collection (users==0 sonst nie erreicht)."""
    coll = bpy.data.collections.get(DELTA_COLLECTION)
    if coll is not None:
        meshes = set()
        for obj in list(coll.objects):
            if obj.data is not None:
                meshes.add(obj.data)
            bpy.data.objects.remove(obj, do_unlink=True)
        for me in meshes:
            try:
                if me.users == 0 and isinstance(me, bpy.types.Mesh):
                    bpy.data.meshes.remove(me)
            except (ReferenceError, RuntimeError):
                pass
        try:
            bpy.data.collections.remove(coll)
        except (ReferenceError, RuntimeError):
            pass
    mat = bpy.data.materials.get(FALLBACK_MATERIAL)
    if mat is not None and mat.users == 0:
        try:
            bpy.data.materials.remove(mat)
        except (ReferenceError, RuntimeError):
            pass


def handle_block_delta(fields):
    """Dispatch-einstieg (tick.py, unsolicited PUSH). Faengt intern JEDE exception —
    timer-regel, siehe modul-doc."""
    global _apply_count, _remove_count, _noop_count, _ignore_count, _reexport_count, _last_status
    try:
        region = fields.get("region")
        if _armed_region is None or not isinstance(region, list) or tuple(region) != _armed_region:
            # nicht unser import (fremder agent-export hat den java-tracker re-targetet)
            # oder wir haben nie importiert -> nie anwenden. Region-gate, siehe modul-doc.
            _ignore_count += 1
            return
        from . import world_refresh, shot_freeze
        if shot_freeze.ist_eingefroren():
            world_refresh.changed()
            _last_status = 'world delta paused: shot is frozen'
            return
        if fields.get("reExport"):
            world_refresh.changed()
            _reexport_count += 1
            _last_status = "re-export empfohlen (%s bloecke geaendert)" % fields.get("count", "?")
            print("[bbs-bridge] world_delta: %s — grosse aenderung, patch-pfad uebersprungen"
                  % _last_status)
            return
        changes = fields.get("changes")
        if not isinstance(changes, list):
            _ignore_count += 1
            return
        applied = removed = 0
        for raw in changes:
            kind = _apply_change(raw)
            if kind != "ignore":
                world_refresh.changed()
            if kind == "apply":
                applied += 1
            elif kind == "remove":
                removed += 1
        if applied or removed:
            _last_status = "+%d/-%d patches" % (applied, removed)
            print("[bbs-bridge] world_delta: %d patch(es) gesetzt, %d entfernt" % (applied, removed))
    except Exception as e:  # noqa: BLE001 — timer-schutz (siehe modul-doc), nie eskalieren
        print("[bbs-bridge] WARN: world_delta-handler fehlgeschlagen (bridge laeuft weiter): %s: %s"
              % (type(e).__name__, e))


def _apply_change(raw):
    """Einen wire-eintrag "x y z block-id [mat-key]" anwenden. Rueckgabe: "apply"/"remove"/
    "noop"/"ignore" (fuer die zaehler des aufrufers/der gates)."""
    global _apply_count, _remove_count, _noop_count, _ignore_count
    if not isinstance(raw, str):
        _ignore_count += 1
        return "ignore"
    parts = raw.split(" ")
    if len(parts) < 4:
        _ignore_count += 1
        return "ignore"
    try:
        x, y, z = int(parts[0]), int(parts[1]), int(parts[2])
    except ValueError:
        _ignore_count += 1
        return "ignore"
    block_id = parts[3]
    mat_key = parts[4] if len(parts) > 4 else ""
    # DEFENSE-IN-DEPTH: java scannt nur die armierte region, aber ein position-check hier
    # kostet nichts und haelt einen skew (java neu, addon alt o.ae.) aus der szene.
    x0, y0, z0, x1, y1, z1 = _armed_region
    if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
        _ignore_count += 1
        return "ignore"
    name = "BBS_Delta_%d_%d_%d" % (x, y, z)
    existing = bpy.data.objects.get(name)
    if block_id in _AIR_IDS:
        if existing is not None:
            _remove_obj(existing)
            _remove_count += 1
            return "remove"
        # original-block abgebaut, kein patch von uns vorhanden: das basis-mesh behaelt
        # sein altes face — BEKANNTE V1-GRENZE (modul-doc), bewusst kein stiller fake-fix.
        _noop_count += 1
        return "noop"
    if existing is not None:
        if existing.get("bbs_block_id") == block_id:
            _noop_count += 1   # idempotenz: exakt dieser block steht schon als patch da
            return "noop"
        _remove_obj(existing)  # block ERSETZT (stone -> gold): alten patch weg, neuen bauen
    _build_cube(name, x, y, z, block_id, mat_key)
    _apply_count += 1
    return "apply"


def _remove_obj(obj):
    me = obj.data
    bpy.data.objects.remove(obj, do_unlink=True)
    try:
        if me is not None and me.users == 0 and isinstance(me, bpy.types.Mesh):
            bpy.data.meshes.remove(me)
    except (ReferenceError, RuntimeError):
        pass


def _ensure_collection():
    coll = bpy.data.collections.get(DELTA_COLLECTION)
    if coll is None:
        coll = bpy.data.collections.new(DELTA_COLLECTION)
        bpy.context.scene.collection.children.link(coll)
    return coll


def _build_cube(name, x, y, z, block_id, mat_key):
    """Einzelwuerfel-patch fuer MC-block (x,y,z). KOORDINATEN-CONTRACT (verbindlich,
    COORDINATE_CONTRACT.md): bl_x=mc_x, bl_y=-mc_z, bl_z=mc_y. Der block belegt MC
    [x..x+1]x[y..y+1]x[z..z+1] -> Blender X:[x,x+1], Y:[-(z+1),-z], Z:[y,y+1] — exakt
    deckungsgleich mit dem importierten welt-mesh (das schreibt rohe MC-koordinaten, der
    OBJ-importer macht dieselbe rotation). Direkt via from_pydata (KEIN bpy.ops im timer —
    ops braucht context, dossier-falle)."""
    bx0, bx1 = float(x), float(x + 1)
    by0, by1 = float(-(z + 1)), float(-z)
    bz0, bz1 = float(y), float(y + 1)
    verts = [
        (bx0, by0, bz0), (bx1, by0, bz0), (bx1, by1, bz0), (bx0, by1, bz0),
        (bx0, by0, bz1), (bx1, by0, bz1), (bx1, by1, bz1), (bx0, by1, bz1),
    ]
    # quads mit AUSSEN-normalen (CCW von aussen gesehen)
    faces = [
        (0, 3, 2, 1),   # boden  (-Z)
        (4, 5, 6, 7),   # deckel (+Z)
        (0, 1, 5, 4),   # -Y
        (2, 3, 7, 6),   # +Y
        (0, 4, 7, 3),   # -X
        (1, 2, 6, 5),   # +X
    ]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.validate()
    uv = me.uv_layers.new(name="UVMap")
    # volle textur pro face (0..1) — sprite-PNGs des pools sind einzel-texturen, ein
    # full-quad-mapping ist der korrekte block-look (interpolation='Closest' steckt schon
    # im pool-material aus _apply_mc_look).
    quad_uv = ((0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0))
    for poly in me.polygons:
        for i, loop_idx in enumerate(poly.loop_indices):
            uv.data[loop_idx].uv = quad_uv[i % 4]
    mat = _resolve_material(mat_key, block_id)
    if mat is not None:
        me.materials.append(mat)
    obj = bpy.data.objects.new(name, me)
    obj["bbs_block_id"] = block_id
    obj["bbs_delta"] = True
    _ensure_collection().objects.link(obj)
    return obj


def _resolve_material(mat_key, block_id):
    """Material aus dem BESTEHENDEN pool: (1) java-hint (particle-sprite-safeName — matcht
    den untinted-normalfall exakt), (2) heuristik minecraft:stone -> minecraft_block_stone
    (sprite-name == block-name fuer die meisten full-cubes), (3) grauer fallback (prefixed,
    invariante 5). NIE ein pool-material veraendern — nur referenzieren."""
    if mat_key:
        mat = bpy.data.materials.get(mat_key)
        if mat is not None:
            return mat
    if ":" in block_id:
        ns, path = block_id.split(":", 1)
    else:
        ns, path = "minecraft", block_id
    mat = bpy.data.materials.get("%s_block_%s" % (ns, path.replace("/", "_")))
    if mat is not None:
        return mat
    mat = bpy.data.materials.get(FALLBACK_MATERIAL)
    if mat is None:
        mat = bpy.data.materials.new(FALLBACK_MATERIAL)
        mat.use_nodes = True
        for n in mat.node_tree.nodes:
            if n.type == 'BSDF_PRINCIPLED':
                n.inputs["Base Color"].default_value = (0.5, 0.5, 0.5, 1.0)
                break
        print("[bbs-bridge] world_delta: kein pool-material fuer '%s' (hint '%s') — fallback"
              % (block_id, mat_key))
    return mat
