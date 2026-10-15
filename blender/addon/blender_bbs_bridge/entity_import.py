"""TASK 4.1 (W7-B1) — mob-entities nach Blender: ENTITY_EXPORT(_RESULT)-consumer.

Mob-replays (formType "bbs:mob") hatten bis 4.1 nur ein platzhalter-EMPTY (actors.py) —
dieses modul holt das ECHTE modell: java `com.blenderbridge.entity.EntityExportBridge`
treibt den bewiesenen `EntityExporter` (bind-OBJ+MTL+layer-komposit-PNG) und schreibt
zusaetzlich `rig.json` (part-baum: pfad/name/objGroup/parent/pivotPx/bindRotation/visible)
neben die OBJ — disk-basiert wie world_meta.json, NICHT ueber den draht (haus-muster).
Hier wird daraus: mesh (vertex-groups aus den OBJ-`g`-gruppen) + echte armature
(bone pro ModelPart) + Armature-modifier — der actor-root fuer actors.py, und das
ziel-rig fuer replay_anim.py (mob-replay -> echte bones -> pose-animation, task-kreis zu).

=== RAUM (lektion 949f9de: EIN daten-raum!) ===
`entity.obj` steht in JAVA-MODELL-KOORDINATEN: Y-DOWN, block-einheiten (gemessen am
villager-export: kopf y=-0.625..0, fuesse +1.5 — replay_anim-docstring "POSE MATH" (2)).
Der OBJ-importer nimmt Y-UP an — seine objekt-rotation ist fuer diese datei schlicht
FALSCH und wird VERWORFEN: die daten werden stattdessen direkt mit `_JAVA_TO_ARM`
(replay_anim, gemessene konstante: javaX->blX, javaY(unten)->bl-Z... die figur steht
danach aufrecht) transformiert PLUS dem fuss-lift `ENTITY_FOOT_LIFT` (vanilla-konstante
1.501, doc am wert: erster :9977-gate-lauf mass fuesse bei z=-1.500 — der actor stuende
ohne lift 1.5 bloecke unter seinen x/y/z-kanaelen), objekt-transform identity. Bones
bekommen DIESELBE konversion — mesh und rig leben in EINEM raum, bobj_import-lehre.

=== ZERO-ROTATION-CAPTURE (warum replay_anim hier OHNE bind-kompensation exakt ist) ===
Java captured die wire-exports mit GENULLTEN part-rotationen (pivots intakt,
`EntityExportTarget`-klassendoc "WHY ZERO ROTATIONS"): pose-results
(REPLAY_POSE_EXPORT_RESULT) tragen ABSOLUTE part-rotationen — auf eine echt-bind-gebackene
geometrie (villager-arme -0.75 rad gefaltet) angewandt wuerden gefaltete arme DOPPELT
rotieren. Mit R_bind==0 ueberall ist replay_anims konjugation P = H^-1 @ R_java @ H fuer
JEDE bone-rest-orientierung und JEDE baumtiefe exakt (die dortige "bei echtem 4.1-rig
R_bind gegenrechnen"-notiz ist damit GEGENSTANDSLOS: es gibt nichts gegenzurechnen,
replay_anim-mathe bleibt unangetastet). `rig.json.bindRotation` ist reine referenz.

=== BONES ===
- head = akkumulierte pivot-summe (pivotPx sind ROHE ModelPart-pixel, PARENT-relativ,
  ein forward-pass reicht: rig.json ist pre-order, parents zuerst — java-doc) / 16
  (block-einheiten, exakt vanillas `ModelPart.rotate`-konvention), dann `_JAVA_TO_ARM`.
- tail = head + (0,0,-BONE_LEN) blender, roll so dass bone-Z -> +Y — d.h. JEDE bone-
  rest-orientierung == `_JAVA_TO_ARM` (H==I in replay_anims formel; wegen zero-rotation-
  capture waere auch jede andere wahl korrekt, diese ist die einfachste + MC-typisch
  "glieder zeigen nach unten" wie beim alex-BOBJ).
- gebaut via bobj_import._build_bobj_armature (bewiesener code: mode-juggling, null-
  laengen-guard, 2-pass-parenting, name_map) — dessen input ist BOBJ-raum (Y-up), unsere
  bones werden deshalb mit Rx180 ((x,y,z)->(x,-y,-z)) VOR-konvertiert: _bobj_vec(Rx180 @ v)
  == M_bobj @ Rx180 @ v == _JAVA_TO_ARM @ v. Kein neuer armature-code.
- bone-NAME = letztes pfad-segment (== replay_anim._bone_name_for_path == ModelPart-
  feldname); kollision (zwei parts, gleicher feldname, andere eltern) -> voller objGroup-
  name als fallback + log (solche bones matcht replay_anim dann nicht — ehrliche grenze,
  villager hat keine kollision).

=== VERTEX-GROUPS (⚠️ importer-messfund 2026-07-17) ===
`wm.obj_import(import_vertex_groups=True)` legt in DIESEM Blender (5.2) zwar die
gruppen-datablocks an, weist aber KEINE vertices zu (live in :9976 gemessen: alle
v.groups leer) — die zuweisung passiert deshalb SELBST: `_parse_obj_groups` liest die
`g`/`f`-zeilen der OBJ (gruppe gilt fuer folgende faces, java schreibt pro face 4 eigene
vertices in reihenfolge), `_bind_mesh_to_bones` legt vertex-groups direkt unter den
FINALEN bone-namen an (name_map wie bobj: '.001'-sicher) und setzt gewicht 1.0
(MC-parts sind starr). Vertex-zaehl-guard wie bobj_import._apply_bobj_weights:
weicht die mesh-vertex-zahl von den `v`-zeilen ab, wird NICHT geschmiert, nur gemeldet.

=== SOLICITED-KORRELATION (dossier-merkregel: JEDER result-handler braucht sie) ===
Neuer kanal => requestId-echo von ANFANG AN (java echot fields.requestId — die
world_import-lehre ce2d139 direkt verbaut statt nachgeruestet): der handler akzeptiert
NUR das result mit der EIGENEN id; fremde results (java broadcastet an ALLE clients)
lassen pending+deadline UNANGETASTET; ohne laufenden request wird ALLES ignoriert
(java pusht nie unsolicited). Anders als world_import gibt es KEINEN id-losen fallback:
diesen kanal gab es vor der korrelation nie, ein result ohne id ist per definition fremd.

=== IDEMPOTENZ / ACTOR-LIFECYCLE ===
Result-pfad: platzhalter-EMPTY des replays (bbs_uuid) -> armature uebernimmt custom-props,
action (objekt-level actor-fcurves! slot-handles sind ueber IDs teilbar) und hide-state,
empty wird geloescht. Ist der actor SCHON ein modell: nur cache-update, kein zweit-import
(keine dubletten). Kein empty da: standalone-armature in MC_Actors, uuid gestempelt.
Der actors.py-mob-zweig baut ab dann bei JEDEM vollimport aus dem cache (_model_cache,
session-lokal — nach Blender-neustart einmal neu exportieren, dokumentierte grenze).

=== BEKANNTE GRENZEN (dokumentiert, nicht versteckt) ===
- EIN export-verzeichnis pro entity-TYP (java-doc "KNOWN LIMIT"): zwei villager-replays
  mit verschiedenen professions teilen textur/rig, letzter export gewinnt.
- non-SinglePartEntityModel-mobs: java antwortet sauber ok:false (EntityExporter-grenze).
"""

import bpy
import json
import os
import time
import uuid as uuid_module

from mathutils import Matrix

from . import state
from .core import _send, _next_seq
from .world_import import _set_alpha_clip
# konstante LESEN (kein write — die state.py-regel betrifft geschriebene werte).
# Import-kette entity_import -> replay_anim -> actors ist azyklisch, weil actors.py
# dieses modul nur LAZY (im funktions-koerper) importiert — siehe _make_actor_root.
from .replay_anim import _JAVA_TO_ARM
from .bobj_import import _build_bobj_armature

# ---- modul-lokales pending (KEIN state.py-edit, replay_anim-muster) ----
_pending_id = ""          # uuid4-hex des laufenden requests; "" = idle
_deadline = 0.0
_status = "idle"
ENTITY_EXPORT_TIMEOUT = 30.0

# mobId ("minecraft:villager") -> (obj_path, rig_path). Session-lokal (siehe docstring).
_model_cache = {}

BONE_LEN = 0.25           # block-einheiten; rein kosmetisch (rest-orientierung zaehlt)

# Fuß-lift: java-modell-ursprung liegt NICHT an den fuessen (villager gemessen: fuesse
# java y=+1.5 -> nach _JAVA_TO_ARM bl z=-1.5, figur haengt unter dem ursprung). Vanilla
# rendert deshalb mit `matrices.translate(0, -1.501, 0)` NACH dem aufricht-flip
# (LivingEntityRenderer.render, der beruehmte 1.501-offset) == +1.501 welt-hoch. BBS-
# actor-x/y/z-kanaele positionieren den root an der ENTITY-position (fuesse) — ohne den
# lift stuende jeder mob 1.5 bloecke zu tief. Mesh UND bones werden gleich verschoben
# (reine translation, replay_anims rotations-mathe unberuehrt).
ENTITY_FOOT_LIFT = 1.501


def request_entity_export(replay_uuid=None, entity_uuid=None, mob_id=None, target_uuid=None):
    """ENTITY_EXPORT anfragen (solicited, requestId-korreliert — siehe modul-header).
    replay_uuid/entity_uuid sind die normalen Wege; mob_id+target_uuid baut einen
    wegwerfbaren Live-Morph-Preview. Aufrufbar aus EXEC/konsole; der UI-operator ist
    fremdes territorium (W7-B3)."""
    global _pending_id, _deadline, _status
    if not state._connected or state._sock is None:
        _status = "not connected"
        return False
    if _pending_id:
        _status = "another entity export is still pending"
        return False
    rid = uuid_module.uuid4().hex
    fields = {"requestId": rid}
    if replay_uuid:
        fields["replayUuid"] = str(replay_uuid)
    elif entity_uuid:
        fields["entityUuid"] = str(entity_uuid)
    elif mob_id:
        fields["mobId"] = str(mob_id)
        fields["targetUuid"] = str(target_uuid or "")
    _pending_id = rid
    _deadline = time.time() + ENTITY_EXPORT_TIMEOUT
    _status = "waiting for entity export (%s)" % (
        replay_uuid or entity_uuid or mob_id or "nearest villager")
    _send({"type": "ENTITY_EXPORT", "origin": "blender", "uuid": "entity-export",
           "seq": _next_seq(), "fields": fields})
    print("[bbs-bridge] ENTITY_EXPORT requested: replayUuid=%s entity/mob=%s requestId=%s"
          % (replay_uuid, entity_uuid or mob_id, rid))
    return True


def _check_timeout():
    """Von tick.py gerufen (wie replay_anim._check_timeout) — ein verlorener export darf
    das pending nicht fuer immer blockieren."""
    global _pending_id, _status
    if _pending_id and time.time() > _deadline:
        _pending_id = ""
        _status = "export timed out"
        print("[bbs-bridge] ENTITY_EXPORT timed out (no result after %ds)" % ENTITY_EXPORT_TIMEOUT)


def _handle_entity_export_result(fields):
    """ENTITY_EXPORT_RESULT — korrelations-gate IM modul (nicht in tick.py wie bei
    replay_anim, weil der id-vergleich die fields braucht): nur das result mit der
    EIGENEN requestId wird verarbeitet, fremde lassen pending+deadline unangetastet,
    ohne laufenden request wird alles ignoriert (modul-header 'SOLICITED-KORRELATION')."""
    global _pending_id, _status
    if not _pending_id:
        return
    rid = str(fields.get("requestId") or "")
    if rid != _pending_id:
        print("[bbs-bridge] ENTITY_EXPORT_RESULT ignored (foreign requestId %r, own request "
              "stays pending)" % (rid,))
        return
    _pending_id = ""
    try:
        _apply_entity_export(fields)
    except Exception as e:   # noqa: BLE001 — der tick-loop darf an KEINEM import sterben
        _status = "import failed: %s" % e
        print("[bbs-bridge] entity import failed:", e)


def cached_entity_model(mob_id):
    """(obj_path, rig_path) eines frueheren exports fuer diese mob-id — oder None.
    Validiert die dateien (ein geloeschtes export-verzeichnis faellt sauber raus)."""
    entry = _model_cache.get(mob_id)
    if entry is None:
        return None
    obj_path, rig_path = entry
    if os.path.isfile(obj_path) and os.path.isfile(rig_path):
        return entry
    _model_cache.pop(mob_id, None)
    return None


# ======================================================================
#  result -> szene
# ======================================================================

def _apply_entity_export(fields):
    """Result anwenden: cache fuellen + den actor des replays upgraden (oder standalone
    bauen). Jeder fehlerpfad loggt + kehrt zurueck (world_import/actors-haltung)."""
    global _status
    if not fields.get("ok", False):
        _status = fields.get("error") or "export failed"
        print("[bbs-bridge] entity export failed:", _status)
        return
    obj_path = str(fields.get("objPath") or "")
    rig_path = str(fields.get("rigPath") or "")
    if not (obj_path and rig_path and os.path.isfile(obj_path) and os.path.isfile(rig_path)):
        _status = "export files missing on disk (obj=%r rig=%r)" % (obj_path, rig_path)
        print("[bbs-bridge] entity import: %s" % _status)
        return

    mob_id = str(fields.get("mobId") or "")
    if mob_id:
        _model_cache[mob_id] = (obj_path, rig_path)

    replay_uuid = str(fields.get("replayUuid") or "")
    existing = _find_root_by_uuid(replay_uuid) if replay_uuid else None
    if existing is not None and existing.type != 'EMPTY':
        # schon ein echtes modell (2. request / re-export): NUR cache aktualisiert, kein
        # zweit-import — dubletten-schutz. Neu bauen = actors-vollimport (zieht den cache).
        _status = "cache updated for %s (actor already a model)" % mob_id
        print("[bbs-bridge] entity import: %s" % _status)
        return

    coll = None
    if existing is not None and existing.users_collection:
        coll = existing.users_collection[0]
    if coll is None:
        coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
        if coll is None:
            coll = bpy.data.collections.new(state.ACTORS_COLLECTION)
            bpy.context.scene.collection.children.link(coll)

    label = (existing.name if existing is not None
             else str(fields.get("entityType") or "entity"))
    arm_obj = build_entity_model(obj_path, rig_path, coll, name=label)
    if arm_obj is None:
        _status = "model build failed (see console)"
        return

    arm_obj.rotation_mode = 'XYZ'      # MUSS vor rotation_euler-keyframes stehen
    if existing is not None:
        _takeover_from_empty(existing, arm_obj)
    elif replay_uuid:
        arm_obj["bbs_uuid"] = replay_uuid

    if replay_uuid.startswith("__bbs_live_morph__:"):
        from . import recording_morph
        recording_morph.adopt_entity_preview(arm_obj, replay_uuid)

    n_bones = len(arm_obj.pose.bones) if arm_obj.type == 'ARMATURE' else 0
    _status = "%s: %d bones (%s)" % (arm_obj.name, n_bones, mob_id)
    print("[bbs-bridge] entity import done: %s -> '%s' (%d bones, replayUuid=%s, %s)"
          % (mob_id, arm_obj.name, n_bones, replay_uuid or "-",
             "upgraded empty" if existing is not None else "standalone"))


def _find_root_by_uuid(replay_uuid):
    """Actor-ROOT (parent None) mit diesem bbs_uuid — szenenweit wie replay_anim.
    Bewusst NICHT actors._find_actor_by_uuid (zirkulaerer import, siehe docstring)."""
    for obj in bpy.data.objects:
        if obj.parent is None and obj.get("bbs_uuid") == replay_uuid:
            return obj
    return None


def _takeover_from_empty(empty, arm_obj):
    """Platzhalter-EMPTY -> armature: custom-props, action (objekt-level actor-fcurves),
    hide-state uebernehmen, empty loeschen. Action-handover statt fcurve-kopie: slot-
    handles einer action sind ueber mehrere IDs gleichen typs teilbar (beide OBJECT) —
    replay_anims pose-keys landen spaeter auf DERSELBEN action, exakt das dokumentierte
    actors-root-szenario seines docstrings."""
    for key in ("bbs_uuid", "bbs_id", "bbs_form", "bbs_form_type", "bbs_group", "bbs_is_group"):
        if key in empty:
            arm_obj[key] = empty[key]
    ad_src = empty.animation_data
    if ad_src is not None and ad_src.action is not None:
        act = ad_src.action
        slot = getattr(ad_src, "action_slot", None)
        try:
            ad_dst = arm_obj.animation_data_create()
            ad_dst.action = act
            if slot is not None:
                ad_dst.action_slot = slot
        except (RuntimeError, TypeError, AttributeError) as e:
            print("[bbs-bridge] entity import: action handover failed (%s) — "
                  "re-run actors import to rebuild the f-curves" % e)
    if empty.hide_viewport:
        for o in [arm_obj] + list(arm_obj.children):
            o.hide_viewport = True
            o.hide_render = True
    old_name = empty.name
    bpy.data.objects.remove(empty, do_unlink=True)
    try:
        # kosmetik: das empty hielt den actor-namen besetzt, die armature bekam '.001' —
        # nach dem loeschen den echten namen zurueckholen (uuid bleibt das stabile band).
        arm_obj.name = old_name
    except (RuntimeError, TypeError):
        pass


# ======================================================================
#  modell-bau (auch vom actors.py-mob-zweig gerufen, cache-pfad)
# ======================================================================

def build_entity_model(obj_path, rig_path, coll, name=None):
    """entity.obj + rig.json -> armature-ROOT (mesh als kind, vertex-groups auf bones).
    Contract wie bobj_import._import_bobj: root traegt custom-props/F-curves des actors.
    Gibt None zurueck wenn nichts brauchbares rauskam (aufrufer faellt auf empty zurueck)."""
    t0 = time.time()
    rig = _read_rig(rig_path)
    bones = rig.get("bones") if isinstance(rig, dict) else None
    if not bones:
        print("[bbs-bridge] entity rig unreadable/empty: %s" % rig_path)
        return None

    meshes = _import_entity_mesh(obj_path)
    if not meshes:
        return None
    for o in meshes:
        for c in list(o.users_collection):
            c.objects.unlink(o)
        coll.objects.link(o)

    label = name or str(rig.get("entityType") or "entity")

    bone_tuples, path_to_name, group_to_path = _rig_to_bobj_bones(bones)
    arm_obj, name_map = _build_bobj_armature(bone_tuples, label, coll)

    obj_groups, n_obj_verts = _parse_obj_groups(obj_path)
    n_groups = 0
    for me in meshes:
        n_groups += _bind_mesh_to_bones(me, obj_groups, n_obj_verts,
                                        group_to_path, path_to_name, name_map)
        mod = me.modifiers.new(name="Armature", type='ARMATURE')
        mod.object = arm_obj
        # parenten OHNE ops — armature steht auf identity, parent-inverse identity
        # (dieselbe begruendung wie in bobj_import._import_bobj).
        me.parent = arm_obj
        _fix_entity_materials(me, str(rig.get("entityType") or "entity"))

    print("[bbs-bridge] entity model '%s': %d bones, %d meshes, %d vertex-group-bindings, "
          "zeroRotation=%s (%.2fs)"
          % (label, len(bone_tuples), len(meshes), n_groups,
             rig.get("zeroRotation"), time.time() - t0))
    return arm_obj


def _read_rig(rig_path):
    """rig.json lesen — fehlt/kaputt -> {} (nie werfen)."""
    try:
        with open(rig_path, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError, UnicodeDecodeError) as e:
        print("[bbs-bridge] bad rig.json %s: %s" % (rig_path, e))
        return {}


def _rx180(v):
    """Java-raum -> BOBJ-raum-vorkonvertierung (docstring 'BONES'): (x,y,z)->(x,-y,-z),
    damit bobj_import._bobj_vec daraus exakt _JAVA_TO_ARM @ v macht."""
    return (v[0], -v[1], -v[2])


def _rig_to_bobj_bones(bones):
    """rig.json-bones -> (_build_bobj_armature-tupel, path->bone-name, objGroup->path).

    Heads: forward-pass pivot-akkumulation (pre-order garantiert, java-doc), pixel/16.
    Namen: letztes pfad-segment; kollision -> objGroup-fallback + log (docstring 'BONES').
    """
    heads_px = {}          # path -> (x,y,z) akkumuliert, java-pixel
    path_to_name = {}
    group_to_path = {}
    used_names = set()
    out = []
    for b in bones:
        if not isinstance(b, dict):
            continue
        path = str(b.get("path") or "root")
        parent_path = str(b.get("parent") or "")
        pivot = b.get("pivotPx") or (0.0, 0.0, 0.0)
        try:
            px, py, pz = (float(pivot[0]), float(pivot[1]), float(pivot[2]))
        except (TypeError, ValueError, IndexError):
            px = py = pz = 0.0
        base = heads_px.get(parent_path, (0.0, 0.0, 0.0))
        head_px = (base[0] + px, base[1] + py, base[2] + pz)
        heads_px[path] = head_px

        bname = str(b.get("name") or "root")
        if bname in used_names:
            fallback = str(b.get("objGroup") or path)
            print("[bbs-bridge] entity rig: duplicate part name %r -> bone %r "
                  "(replay_anim matcht nur eindeutige feldnamen)" % (bname, fallback))
            bname = fallback
        used_names.add(bname)
        path_to_name[path] = bname
        group = str(b.get("objGroup") or "")
        if group:
            group_to_path[group] = path

        head_bobj = _rx180((head_px[0] / 16.0, head_px[1] / 16.0, head_px[2] / 16.0))
        # fuss-lift (ENTITY_FOOT_LIFT-doc): bl (0,0,+lift) == bobj (0,+lift,0), weil
        # _bobj_vec (x,y,z)->(x,-z,y).
        head_bobj = (head_bobj[0], head_bobj[1] + ENTITY_FOOT_LIFT, head_bobj[2])
        # tail (0,-L,0) im BOBJ-raum == (0,0,-L) blender; z-achse (0,0,-1) == +Y blender
        # -> rest-orientierung jeder bone == _JAVA_TO_ARM (docstring 'BONES').
        tail_bobj = (head_bobj[0], head_bobj[1] - BONE_LEN, head_bobj[2])
        parent_name = path_to_name.get(parent_path) if parent_path else None
        out.append((bname, parent_name, head_bobj, tail_bobj, (0.0, 0.0, -1.0)))
    return out, path_to_name, group_to_path


def _parse_obj_groups(obj_path):
    """OBJ-`g`-gruppen -> vertex-index-mengen (0-basiert) + gesamt-`v`-zahl.

    SELBST geparst, weil der 5.2-importer mit import_vertex_groups=True nur leere
    gruppen anlegt (docstring 'VERTEX-GROUPS', live gemessen). `g` gilt fuer die
    folgenden `f`-zeilen; face-tokens sind `v[/vt[/vn]]`, indices 1-basiert (der
    java-writer schreibt absolute positive; negative = relativ, mit abgedeckt)."""
    groups = {}
    current = None
    n_verts = 0
    try:
        with open(obj_path, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                if line.startswith("v "):
                    n_verts += 1
                elif line.startswith("g "):
                    current = line[2:].strip() or None
                elif line.startswith("f ") and current is not None:
                    idxs = groups.setdefault(current, set())
                    for tok in line.split()[1:]:
                        head = tok.split("/", 1)[0]
                        try:
                            i = int(head)
                        except ValueError:
                            continue
                        idxs.add(i - 1 if i > 0 else n_verts + i)
    except OSError as e:
        print("[bbs-bridge] cannot read obj groups %s: %s" % (obj_path, e))
        return {}, 0
    return groups, n_verts


def _bind_mesh_to_bones(me_obj, obj_groups, n_obj_verts, group_to_path, path_to_name, name_map):
    """Geparste OBJ-gruppen als vertex-groups unter den FINALEN bone-namen anlegen
    (gewicht 1.0, starre MC-parts). Gibt anzahl gebundener gruppen zurueck.

    Zaehl-guard wie bobj_import._apply_bobj_weights: setzt voraus dass der importer
    die `v`-zeilen 1:1 und in reihenfolge uebernimmt (dort verifizierte annahme) —
    bei abweichung wird NICHT auf falsche vertices geschmiert, nur laut gemeldet."""
    verts = me_obj.data.vertices
    if n_obj_verts and len(verts) != n_obj_verts:
        print("[bbs-bridge] entity mesh: %d mesh verts != %d obj verts (%s) — "
              "vertex-groups uebersprungen, mesh bleibt starr"
              % (len(verts), n_obj_verts, me_obj.name))
        return 0
    n = 0
    for group_name, idxs in obj_groups.items():
        path = group_to_path.get(group_name)
        if path is None:
            print("[bbs-bridge] entity mesh: obj group %r not in rig.json (ignored)" % group_name)
            continue
        final = name_map.get(path_to_name.get(path, ""), None)
        if final is None or not idxs:
            continue
        valid = [i for i in sorted(idxs) if 0 <= i < len(verts)]
        if not valid:
            continue
        try:
            vg = me_obj.vertex_groups.get(final) or me_obj.vertex_groups.new(name=final)
            vg.add(valid, 1.0, 'REPLACE')
            n += 1
        except (RuntimeError, TypeError) as e:
            print("[bbs-bridge] entity mesh: vertex group %r failed: %s" % (final, e))
    return n


def _import_entity_mesh(obj_path):
    """entity.obj holen, dann den raum RICHTEN: importer-objekt-rotation VERWERFEN
    (Y-up-annahme, fuer diese Y-DOWN-datei falsch) und die daten direkt mit
    _JAVA_TO_ARM transformieren (docstring 'RAUM'). Vertex-groups kommen NICHT vom
    importer (leere gruppen, messfund) — _bind_mesh_to_bones legt sie selbst an."""
    before = set(bpy.data.objects)
    try:
        bpy.ops.wm.obj_import(filepath=obj_path, forward_axis='NEGATIVE_Z', up_axis='Y')
    except (RuntimeError, OSError) as e:
        print("[bbs-bridge] entity obj import failed (%s): %s" % (obj_path, e))
        return []
    new = [o for o in bpy.data.objects if o not in before]
    meshes = [o for o in new if o.type == 'MESH']
    # raum-konversion + fuss-lift in EINEM daten-transform (ENTITY_FOOT_LIFT-doc oben)
    conv = Matrix.Translation((0.0, 0.0, ENTITY_FOOT_LIFT)) @ _JAVA_TO_ARM.to_4x4()
    for o in new:
        if o.type != 'MESH':
            bpy.data.objects.remove(o, do_unlink=True)
            continue
        o.data.transform(conv)                 # daten: java -> armature-raum (EIN raum)
        o.matrix_world = Matrix.Identity(4)    # importer-rotation bewusst verworfen
    return meshes


def _fix_entity_materials(me_obj, entity_type):
    """Importer-material ('base') -> 'BBS_ENT_<typ>' + MC-look (Closest, alpha-clip bei
    echtem RGBA, matte roughness — dieselben gruende wie bobj_import._bobj_material).
    Wiederverwendung: zweiter actor desselben typs teilt das material, der importer-dup
    wird entsorgt. ⚠️ colorspace NIE anfassen (world_anim-falle, dossier)."""
    target = "BBS_ENT_" + entity_type
    for slot in me_obj.material_slots:
        mat = slot.material
        if mat is None:
            continue
        existing = bpy.data.materials.get(target)
        if existing is not None and existing is not mat:
            slot.material = existing
            _remove_orphan_material(mat)
            continue
        try:
            mat.name = target
        except (RuntimeError, TypeError):
            pass
        if not mat.use_nodes or mat.node_tree is None:
            continue
        img = None
        for node in mat.node_tree.nodes:
            if node.type == 'TEX_IMAGE':
                node.interpolation = 'Closest'
                if node.image is not None:
                    img = node.image
            elif node.type == 'BSDF_PRINCIPLED' and "Roughness" in node.inputs:
                node.inputs["Roughness"].default_value = 0.9
        if img is not None and img.depth > 24:
            _set_alpha_clip(mat)


def _remove_orphan_material(mat):
    """Verwaistes importer-dup-material + dessen (dann verwaiste) bilder entsorgen —
    dieselbe users==0-vorsicht wie actors._clear_actors_collection."""
    imgs = set()
    try:
        if mat.use_nodes and mat.node_tree is not None:
            for node in mat.node_tree.nodes:
                if node.type == 'TEX_IMAGE' and node.image is not None:
                    imgs.add(node.image)
        if mat.users == 0:
            bpy.data.materials.remove(mat)
    except (ReferenceError, RuntimeError):
        return
    for img in imgs:
        try:
            if img.users == 0:
                bpy.data.images.remove(img)
        except (ReferenceError, RuntimeError):
            pass
