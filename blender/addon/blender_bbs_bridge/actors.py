"""PHASE 3 — actor-import (ACTORS_EXPORT -> ACTORS_EXPORT_RESULT -> animierte objekte)

BBS schickt pro replay (= eine actor-performance) rohe kanaele (x/y/z/pitch/yaw/...)
mit keyframes in TICKS und BBS-einheiten. Wir bauen pro replay ein Empty (oder ein
echtes BOBJ-modell, siehe bobj_import.py) und uebersetzen die kanaele in F-curves.
Umrechnung = COORDINATE_CONTRACT.md, exakt dieselbe inverse wie bei der kamera
(bl_x=bbs_x, bl_y=-bbs_z, bl_z=bbs_y).
"""

import bpy
import math
import time
import json
import hashlib

from . import state
from . import actor_channel_sync
from . import shot_freeze   # auftrag REDACTED §2: live-sync stoppt bei eingefrorenem shot
from . import actor_material
from . import actor_texture
from . import actor_paint
from . import actor_shapekeys
from . import actor_shapenodes
from . import native_model
from . import pose_overlays
from .bobj_import import _find_bobj_for_form, _import_bobj, _read_bobj_config, _packaged_model_path

_DEG = math.pi / 180.0

# Last applied remote snapshot, not ownership of the actor. A stable persisted digest
# prevents an unchanged snapshot from discarding local work after reload. Empty means
# a known bridge object still needs an apply; absent means an unknown legacy baseline.
_ACTOR_SNAPSHOT_PROP = "bbs_actor_snapshot_v1"
_deferred_snapshot = None
_deferred_retry_after = 0.0

# BBS-kanal -> (data_path, index, scale, const)  mit  bl_wert = bbs_wert * scale + const.
# Die zerlegung in scale/const ist NICHT kosmetik: handle-offsets (ly/ry) sind DIFFERENZEN
# im wert-raum, muessen also nur mit `scale` transformiert werden (const faellt weg).
# Ohne das zeigen die bezier-handles auf gespiegelten kanaelen (z, yaw) falsch.
#   bl_x = bbs_x | bl_y = -bbs_z | bl_z = bbs_y  (COORDINATE_CONTRACT.md)
#   bl_body_yaw_deg = -bbs_bodyYaw (fallback: yaw)
# BBS' echte Weltmatrix rotiert den GANZEN Actor nur um bodyYaw (BaseFilmController.
# getMatrixForRenderWithRotation), niemals um pitch. pitch/headYaw werden auf ModelForm-rigs
# von model_pose als Kopfpose konsumiert. Ein Root-pitch legte die ganze Figur bei Blick nach
# unten horizontal; das war keine Achsenkonversion, sondern die falsche Render-Semantik.
_ACTOR_CHANNELS = {
    "x":     ("location",       0,  1.0,   0.0),
    "y":     ("location",       2,  1.0,   0.0),
    "z":     ("location",       1, -1.0,   0.0),
    "yaw":   ("rotation_euler", 2, -_DEG,  0.0),
    "scale_x": ("scale", 0, 1.0, 0.0),
    "scale_y": ("scale", 2, 1.0, 0.0),
    "scale_z": ("scale", 1, 1.0, 0.0),
}
# headYaw/pitch sind Pose-Daten; bodyYaw nutzt denselben Root-Spec wie der Legacy-yaw-Fallback
# und reist zusaetzlich zu model_pose fuer das Residuum. bbs:mob-Pose -> replay_anim.py;
# bbs:model-armatures -> model_pose.py (Anschlusspunkte unten).


def _actor_channel_spec(channels, ch_id):
    """World-transform mapping matching BBS' render matrix.

    bodyYaw owns the root rotation when present. Legacy payloads without it fall back to yaw;
    pitch is deliberately pose-only and therefore never an object transform.
    """
    if ch_id == "bodyYaw":
        return _ACTOR_CHANNELS["yaw"]
    if ch_id == "yaw" and channels.get("bodyYaw"):
        return None
    return _ACTOR_CHANNELS.get(ch_id)

# ---- ITEM-KANAELE (task 5.2, HB-3): die 15 ItemStack-kanaele reisen NICHT als zahlen, sondern
# als item-registry-ids im payload-feld "strings" (java StringChannels). Sie sind KEINE F-curve
# im _ACTOR_CHANNELS-sinn (kein location/rotation) — ein item ist eine STUFENFUNKTION (du haeltst
# es oder nicht), keine kurve. Konsum-form fuer den HUD-compositor (HB-4, task 5.3b):
#   1. custom-prop `bbs_items` am actor-root = die VOLLE tick->id-tabelle als JSON-string
#      (verlustfrei, HB-4 kann bei JEDEM frame die exakte id nachschlagen), plus je kanal
#   2. ein INTEGER-index-F-curve (`bbs_item_<kanal>`, CONSTANT-interp) + id-tabelle
#      (`bbs_item_<kanal>_ids`, JSON-array) — Blender-NATIV per frame abfragbar (icon-treiber),
#      ohne die JSON-tabelle selbst parsen zu muessen.
# "minecraft:air" = leere hand (equip->unequip ist echte animation, kein weglassen). Naming
# `bbs_item_*` prefixed (invariante 5) und liegt NICHT in _ACTOR_DATA_PATHS -> ueberlebt den
# chirurgischen live-sync-clear; _apply_item_channels raeumt die eigenen fcurves selbst.
_ITEM_CHANNELS = (
    "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet",
    "hotbar_slot_1", "hotbar_slot_2", "hotbar_slot_3", "hotbar_slot_4", "hotbar_slot_5",
    "hotbar_slot_6", "hotbar_slot_7", "hotbar_slot_8", "hotbar_slot_9",
)

#: data_path-praefix der item-index-fcurves (custom-prop = `["name"]`-syntax im data_path).
_ITEM_PROP_PREFIX = "bbs_item_"

#: custom-prop am actor-root: die volle {kanal: [[tick,id],...]}-tabelle als JSON-string.
_ITEM_TABLE_PROP = "bbs_items"


def _has_active_riding(replays):
    """Traegt IRGENDEIN replay im payload einen AKTIVEN `riding`-kanal (Double ≠0)?

    `riding` (reiter-seite) ist ein numerischer Double-kanal und reist SCHON im
    ACTORS-payload (generischer Number-transport, `ActorExporter.exportChannel`) — er wird
    aber NICHT konsumiert (kanal-matrix §2 gruppe S: "transportiert, konsumlos"). Das
    GEGENSTUECK `ridden` (MountLink, WER auf mir sitzt) ist nicht-numerisch und landet in
    `skippedChannels` — die follow-kopplung reist also gar nicht (mount-link-doc §1).

    BEWUSSTE GRENZE (mount als nicht-ziel v1, mount-link-doc option (c) + kanal-matrix §5.2):
    ein uneditierter aufgenommener mount-film sieht in Blender nahezu korrekt aus, weil die
    x/y/z/yaw-kanaele des reiters die echten welt-positionen der fahrt tragen — die Blender-
    vorschau zeigt den AUFNAHME-stand, koppelt den reiter aber nicht an den mount. Editiert
    man den mount, folgt der reiter in Blender NICHT (er bliebe auf den absolut-kurven). Der
    hinweis macht genau das sichtbar, statt es zu verstecken (Regel 0: keine versteckte
    unfertigkeit) — er ist der billige ehrlichkeits-teil, KEINE follow-implementierung.

    ≠0 = aktiv (mount-link-doc §1: `resolveRiding` — reiter aktiv wenn `riding≠0`). Ein
    keyframe-value ist `_kf_float`-tolerant gelesen (null/fehlend/muell -> 0.0 = inaktiv).
    Rein lesend, wirft nie — reine payload-analyse wie `world_import._chunk_coverage_suffix`.
    """
    try:
        for replay in replays:
            if not isinstance(replay, dict):
                continue
            keyframes = (replay.get("channels") or {}).get("riding")
            if not keyframes:
                continue
            for kf in keyframes:
                if isinstance(kf, dict) and _kf_float(kf, "value", 0.0) != 0.0:
                    return True
    except (TypeError, AttributeError):
        pass
    return False


#: An den status-string angehaengt, wenn ein importierter film ein aktives `riding` traegt.
#: Ein-zeilen-ehrlichkeit (mount-link-doc option (c)): Blender zeigt den aufnahme-stand,
#: koppelt den reiter aber nicht an den mount. Konsument ist ui.py (rendert _actors_status/
#: _live_status unveraendert weiter — der hinweis reist IM string, kein ui.py-edit).
_MOUNT_SCENE_SUFFIX = " ⚠ mount-szene: Blender zeigt aufnahme-stand, keine follow-kopplung"


def _mount_scene_suffix(replays):
    """Der mount-hinweis-suffix (oder "") fuer den status-string. Reine payload-funktion
    wie `world_import._chunk_coverage_suffix` — kein Blender-zustand, wirft nie."""
    return _MOUNT_SCENE_SUFFIX if _has_active_riding(replays) else ""


# ---- Task 6.3: BBS-ONLY-EFFEKTE (parity-warnung) --------------------------------------
# Manche BBS-effekte sind im Blender-render-pfad PRINZIPBEDINGT unsichtbar: es gibt keinen
# importer, der sie in Blender-geometrie/-VFX uebersetzt (parity-matrix docs/brain/research/
# 2026-07-18-render-parity-matrix.md, NICHT-ZIELE v1 3+4). Der ehrlichste v1-umgang ist NICHT
# sie zu verstecken (Regel 0: keine versteckte unfertigkeit), sondern sie SICHTBAR zu machen
# und den richtigen pfad zu empfehlen: fuer diese abschnitte in BBS rendern (task 6.2), nicht
# in Blender.
#
# WAS AM ACTORS-PAYLOAD ERKENNBAR IST — und was NICHT (bewusste grenze, dokumentiert):
#   * `particles` (Double, ReplayKeyframes:62/367 = `entity.isParticlesEnabled()`) und
#     `fire` (Double, brennt/steht in flammen) reisen als NUMERISCHE kanaele im payload und
#     sind pro replay direkt lesbar — sie sind der zuverlaessige v1-detektor fuer
#     "dieser film zeigt in BBS effekte, die Blender nicht rendert".
#   * Crowd (CrowdMass), Fracture-debris und Ragdolls sind KEINE replay-kanaele — crowd ist
#     ein render-only-command (kein entity/replay, plan-task 4.5 GESTRICHEN b4bb291), fracture/
#     ragdoll haengen an camera-ACTION-clips die im clean rebuild als STUBS unregistriert sind
#     (0/13 action-factories, com/afrobama-dossier). Sie tauchen im ACTORS_EXPORT also gar
#     nicht auf — der ACTORS-payload ist der falsche ort, sie zu erkennen. Ehrliche
#     v1-grenze: die warnung deckt den kanal-basierten fall (particles/fire) ab; crowd/
#     fracture/ragdoll bleiben in der matrix als BBS-render-only dokumentiert, ohne
#     automatische UI-warnung (kein signal im payload, das wir lesen — nicht raten).
#: Numerische replay-kanaele, deren AKTIVITAET (interpolierter wert ≠0 an IRGENDEINEM key)
#: einen im Blender-pfad unsichtbaren BBS-effekt bedeutet. Reihenfolge = anzeige-reihenfolge.
_BBS_ONLY_EFFECT_CHANNELS = ("particles", "fire")

#: Angehaengt an den status-string, wenn der film BBS-only-effekt-kanaele traegt. Konsument
#: ist ui.py (rendert _actors_status/_live_status unveraendert weiter — reist IM string, KEIN
#: ui.py-edit, exakt das muster von _MOUNT_SCENE_SUFFIX / _chunk_coverage_suffix).
_BBS_ONLY_EFFECT_SUFFIX = " ⚠ enthaelt BBS-only-effekte (%s) → in BBS rendern (task 6.2)"


def _active_bbs_only_effects(replays):
    """Liste der BBS-only-effekt-kanaele (aus `_BBS_ONLY_EFFECT_CHANNELS`), die in IRGENDEINEM
    replay einen AKTIVEN keyframe (wert ≠0) tragen — anzeige-reihenfolge, ohne duplikate.

    Rein lesend, wirft nie — reine payload-analyse wie `_has_active_riding` /
    `world_import._chunk_coverage_suffix`. Ein keyframe-value ist `_kf_float`-tolerant
    (null/fehlend/muell -> 0.0 = inaktiv)."""
    found = []
    try:
        for ch in _BBS_ONLY_EFFECT_CHANNELS:
            active = False
            for replay in replays:
                if not isinstance(replay, dict):
                    continue
                keyframes = (replay.get("channels") or {}).get(ch)
                if not keyframes:
                    continue
                for kf in keyframes:
                    if isinstance(kf, dict) and _kf_float(kf, "value", 0.0) != 0.0:
                        active = True
                        break
                if active:
                    break
            if active:
                found.append(ch)
    except (TypeError, AttributeError):
        pass
    return found


def _bbs_only_effects_suffix(replays):
    """Der BBS-only-effekt-hinweis-suffix (oder "") fuer den status-string. Reine payload-
    funktion wie `_mount_scene_suffix` — kein Blender-zustand, wirft nie."""
    effects = _active_bbs_only_effects(replays)
    return _BBS_ONLY_EFFECT_SUFFIX % ", ".join(effects) if effects else ""


# ---- FILM-IDENTITAET (task 1.0: datenverlust-schutz bei filmwechsel) ------------------
# BBS bindet Blender IMPLIZIT an "den film der gerade offen ist". Wechselt der user in BBS
# den film waehrend Blender live-synct, wuerde ein ACTORS_IMPORT (Blender->BBS-write) in den
# FALSCHEN film schreiben = verlorene arbeit in einem film den der user nicht mal ansah. Der
# schutz: jede export-antwort/HELLO traegt die STABILE film-id (java Film.getId()); wir binden
# uns an die erste und ERKENNEN einen wechsel, bevor wir irgendwas in die szene bauen oder
# einen write rausschicken. java lehnt zusaetzlich einen fremd-getaggten write ab (ActorImporter
# .filmMismatch) — doppelte absicherung, java ist die letzte instanz.


def bind_film(film_id):
    """Explicit actor import may rebind to a new film. HELLO/reconnect uses check_film
    instead: reconnecting alone must not relabel actors from the previously bound film.
    An empty id leaves the existing binding intact."""
    fid = str(film_id or "")
    state.film_switch_detected = False
    state.film_switch_other = ""
    if fid:
        state.bound_film = fid
    # leere id NICHT ueberschreiben: eine schon bestehende bindung (aus einem frueheren export)
    # ueberlebt eine HELLO die zufaellig bei geschlossenem dashboard ankam.


def check_film(film_id):
    """Pruefen ob eine eingehende export-antwort/push zum gebundenen film gehoert (task 1.0).

    Rueckgabe True = weiter syncen (gleicher film, oder erst-bindung), False = PAUSE
    (BBS zeigt einen ANDEREN film als den gebundenen — nicht in die szene des alten films
    bauen). Reiner zustands-uebergang, wirft nie.

    - leere eingehende id (kein film offen / ok:false): KEIN mismatch (der aufrufer behandelt
      "kein film" schon selbst) -> True, bindung unangetastet.
    - keine bindung + nicht-leere id: erst-bindung -> True (das ist der normale start, wenn
      das dashboard bei der HELLO noch zu war).
    - bindung == eingehende id: gleicher film, evtl. rueckkehr nach einem wechsel -> mismatch
      loeschen, True.
    - bindung != eingehende id (beide nicht-leer): FILMWECHSEL -> mismatch setzen, False.
    """
    fid = str(film_id or "")
    if not fid:
        return True
    if not state.bound_film:
        state.bound_film = fid
        state.film_switch_detected = False
        state.film_switch_other = ""
        return True
    if fid == state.bound_film:
        if state.film_switch_detected:
            print("[bbs-bridge] film wieder '%s' — sync-pause aufgehoben" % fid)
        state.film_switch_detected = False
        state.film_switch_other = ""
        return True
    # anderer film als gebunden -> datenverlust-gefahr, pausieren
    if not state.film_switch_detected or state.film_switch_other != fid:
        print("[bbs-bridge] FILMWECHSEL erkannt: gebunden an '%s', BBS zeigt '%s' -> "
              "actor-sync PAUSIERT (kein write in den falschen film)"
              % (state.bound_film, fid))
    state.film_switch_detected = True
    state.film_switch_other = fid
    return False


def _film_switch_status():
    """Der status-string fuer die pausierte sync (panel-warnung). ⚠-praefix damit ui.py die
    zeile rot faerben kann (row.alert liest state.film_switch_detected)."""
    return ("⚠ FILMWECHSEL: gebunden an '%s', BBS zeigt '%s' — sync pausiert "
            "(zum gebundenen Film zurueckkehren oder bewusst 'Import BBS Actors' ausfuehren)"
            % (state.bound_film, state.film_switch_other))


def _build_interp_map():
    """BBS-interp-key -> (Blender-interpolation, Blender-easing|None).

    Quelle: mchorse.bbs_mod.utils.interps.Interpolations — deckt alle 40 keys der MAP
    ab (gegen die java-datei diffed, kein fehlender/erfundener key).
    Blenders keyframe-interpolation kennt SINE/QUAD/CUBIC/QUART/QUINT/EXPO/CIRC/BACK/
    BOUNCE/ELASTIC + easing-richtung — das deckt BBS' easing-familie 1:1 ab.
    """
    m = {
        "linear":   ('LINEAR', None),
        "constant": ('CONSTANT', None),
        # VEREINFACHUNG: BBS' "step" ist eine N-stufen-treppe (args.v1 = stufenzahl), kein
        # reines halten. Blender hat dafuer nichts natives -> CONSTANT (= 1 stufe).
        "step":     ('CONSTANT', None),
        # Die EINZIGE BBS-interp die lx/ly/rx/ry wirklich auswertet (FloatKeyframeFactory
        # .interpolate -> BezierUtils.get). Nur hier setzen wir echte handles.
        "bezier":   ('BEZIER', None),
        # Nachbar-basierte cubics: BBS leitet die kurve aus preA/postB ab (Lerps.cubicHermite
        # /cubic/bspline/...), es GIBT keine handle-daten. -> BEZIER + AUTO_CLAMPED = Blenders
        # "glatt durch die punkte". Naeherung der kurvenform, nicht bit-genau. VEREINFACHUNG.
        "cubic":    ('BEZIER', None),
        "hermite":  ('BEZIER', None),
        "bspline":  ('BEZIER', None),
        "akima":    ('BEZIER', None),
        "tcb":      ('BEZIER', None),
        "nurbs":    ('BEZIER', None),
    }
    types = {
        "sine": 'SINE', "circle": 'CIRC', "quad": 'QUAD', "cubic": 'CUBIC',
        "quart": 'QUART', "quint": 'QUINT', "exp": 'EXPO', "back": 'BACK',
        "elastic": 'ELASTIC', "bounce": 'BOUNCE',
    }
    dirs = {"in": 'EASE_IN', "out": 'EASE_OUT', "inout": 'EASE_IN_OUT'}
    for base, bl_type in types.items():
        for suffix, bl_dir in dirs.items():
            # "cubic_in" ueberschreibt NICHT das eigenstaendige "cubic" oben — anderer key.
            m["%s_%s" % (base, suffix)] = (bl_type, bl_dir)
    return m


_INTERP_MAP = _build_interp_map()
_INTERP_FALLBACK = ('BEZIER', None)   # unbekannter key -> glatt, nie crashen


def _set_enum(owner, prop, value):
    """Enum-property setzen, aber nur wenn DIESE Blender-version den wert kennt.
    Ein hart geratener enum-name wirft sonst TypeError und killt den ganzen import
    (dieselbe falle wie bei mat.blend_method, siehe _set_alpha_clip)."""
    try:
        items = owner.bl_rna.properties[prop].enum_items.keys()
    except (KeyError, AttributeError):
        return False
    if value not in items:
        return False
    try:
        setattr(owner, prop, value)
        return True
    except (TypeError, AttributeError):
        return False


def _kf_float(kf, key, default):
    """Zahl aus einem keyframe-dict, tolerant gegen null/fehlend/müll."""
    try:
        v = kf.get(key, default)
        return default if v is None else float(v)
    except (TypeError, ValueError, AttributeError):
        return default


def _clear_actors_collection():
    """Alte MC_Actors-collection samt objekten + deren daten weg, damit ein re-import
    ERSETZT statt zu stapeln.

    Seit dem BOBJ-import haengen hier NICHT mehr nur empties: meshes, armatures,
    materials und bilder muessen mit weg — sonst sammelt jeder re-import einen satz
    'body.001/BBS_MDL_alex.png.001'-leichen an (genau das problem, gegen das
    _clear_world_collection geschrieben wurde). Reihenfolge wie dort: objekte ->
    meshes/armatures -> materials -> images, weil jede stufe die naechste referenziert
    und users sonst nie auf 0 faellt.
    """
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return
    actions, meshes, arms, lights, mats = set(), set(), set(), set(), set()
    for obj in list(coll.objects):
        ad = obj.animation_data
        if ad is not None and ad.action is not None:
            actions.add(ad.action)
        for slot in obj.material_slots:
            if slot.material is not None:
                mats.add(slot.material)
        data = obj.data
        if isinstance(data, bpy.types.Mesh):
            meshes.add(data)
        elif isinstance(data, bpy.types.Armature):
            arms.add(data)
        elif isinstance(data, bpy.types.Light):
            lights.add(data)
        bpy.data.objects.remove(obj, do_unlink=True)
    for act in actions:
        try:
            # fake-user MUSS weg, sonst ist users nie 0 und die alten actions bleiben
            # als .001-müll liegen (Blender setzt ihn bei neuen actions je nach prefs).
            act.use_fake_user = False
            if act.users == 0:
                bpy.data.actions.remove(act)
        except (ReferenceError, RuntimeError):
            pass
    for me in meshes:
        try:
            if me.users == 0:
                bpy.data.meshes.remove(me)
        except (ReferenceError, RuntimeError):
            pass
    for arm in arms:
        try:
            if arm.users == 0:
                bpy.data.armatures.remove(arm)
        except (ReferenceError, RuntimeError):
            pass
    for light in lights:
        try:
            if light.users == 0:
                bpy.data.lights.remove(light)
        except (ReferenceError, RuntimeError):
            pass
    imgs = set()
    for mat in mats:
        try:
            if mat.use_nodes and mat.node_tree is not None:
                for node in mat.node_tree.nodes:
                    if node.type == 'TEX_IMAGE' and node.image is not None:
                        imgs.add(node.image)
            if mat.users == 0:
                bpy.data.materials.remove(mat)
        except (ReferenceError, RuntimeError):
            pass
    for img in imgs:
        try:
            if img.users == 0:
                bpy.data.images.remove(img)
        except (ReferenceError, RuntimeError):
            pass
    try:
        bpy.data.collections.remove(coll)
    except (ReferenceError, RuntimeError):
        pass


def _find_fcurve(obj, data_path, index):
    """Die F-curve eines eben per keyframe_insert() angelegten kanals finden.

    ZWEI wege, weil Blender 4.4 die actions umgebaut hat ("slotted actions"): seither
    haengen die F-curves in layers[].strips[].channelbag(slot), nicht mehr direkt in
    action.fcurves. Der legacy-wrapper action.fcurves lebt in 4.4 noch (bei einem slot),
    ist aber deprecated -> erst der neue weg, dann der alte. Deckt 4.0 bis 4.5+ ab.
    """
    for collection in _object_fcurve_collections(obj):
        fc = collection.find(data_path, index=index)
        if fc is not None:
            return fc
    return None


def _apply_kf_shape(kp, kf, frame, value, scale, tick_scale):
    """Interpolation + handles EINES keyframe-points setzen.

    Handle-semantik ist aus BBS' BezierUtils.get() abgelesen, nicht geraten:
      x1 = aRx / w        -> rechter handle von a liegt bei (aTick + a.rx, aVal + a.ry)
      x2 = (w - bLx) / w  -> linker  handle von b liegt bei (bTick - b.lx, bVal + b.ly)
    (w = tick-abstand, h = wert-differenz; die normalisierung kuerzt sich exakt so raus.)
    Das ist 1:1 Blenders handle_right/handle_left. lx/rx sind TICKS -> mit tick_scale in
    frames; ly/ry sind WERT-offsets -> nur mit `scale` (kein const, siehe _ACTOR_CHANNELS).
    """
    key = str(kf.get("interp") or "linear").lower()
    mode, easing = _INTERP_MAP.get(key, _INTERP_FALLBACK)
    _set_enum(kp, "interpolation", mode)
    if easing is not None:
        _set_enum(kp, "easing", easing)
    if mode != 'BEZIER':
        return
    if key == "bezier":
        # typ VOR position setzen: bei AUTO/AUTO_CLAMPED wuerde fcurve.update() unsere
        # position wieder ueberschreiben, bei FREE bleibt sie stehen.
        kp.handle_left_type = 'FREE'
        kp.handle_right_type = 'FREE'
        lx = _kf_float(kf, "lx", 5.0) * tick_scale
        ly = _kf_float(kf, "ly", 0.0) * scale
        rx = _kf_float(kf, "rx", 5.0) * tick_scale
        ry = _kf_float(kf, "ry", 0.0) * scale
        kp.handle_left = (frame - lx, value + ly)
        kp.handle_right = (frame + rx, value + ry)
    else:
        # hermite/cubic/bspline/akima/tcb/nurbs -> keine handle-daten vorhanden.
        kp.handle_left_type = 'AUTO_CLAMPED'
        kp.handle_right_type = 'AUTO_CLAMPED'


def _build_fcurve(obj, keyframes, spec, tick_scale):
    """Einen BBS-kanal als F-curve auf das objekt legen. Gibt (n_keys, max_frame) zurueck."""
    data_path, index, scale, const = spec
    pts = []
    seen = set()
    # nach tick sortieren: wir inserten mit 'FAST' (kein re-sort) und BBS' reihenfolge
    # ist nicht garantiert. Dubletten auf demselben frame raus — sonst wuerde der zweite
    # den ersten still ueberschreiben und die handle-zuordnung waere raterei.
    for kf in sorted(keyframes, key=lambda k: _kf_float(k, "tick", 0.0)):
        if not isinstance(kf, dict):
            continue
        frame = _kf_float(kf, "tick", 0.0) * tick_scale
        marker = round(frame, 4)
        if marker in seen:
            continue
        seen.add(marker)
        pts.append((frame, _kf_float(kf, "value", 0.0) * scale + const, kf))
    if not pts:
        return 0, 0.0

    # Die F-curve ueber keyframe_insert() am OBJEKT anlegen, NICHT ueber action.fcurves
    # .new(): nur der objekt-weg legt action + slot versions-korrekt an (4.4 slotted
    # actions). Danach fuellen wir die kurve direkt — das ist um laengen schneller als
    # pro keyframe ein keyframe_insert().
    first_frame, first_value, _ = pts[0]
    try:
        getattr(obj, data_path)[index] = first_value
        if not obj.keyframe_insert(data_path=data_path, index=index, frame=first_frame):
            return 0, 0.0
    except (RuntimeError, TypeError, AttributeError) as e:
        print("[bbs-bridge] keyframe_insert failed for %s[%d]: %s" % (data_path, index, e))
        return 0, 0.0
    fc = _find_fcurve(obj, data_path, index)
    if fc is None:
        print("[bbs-bridge] could not find fcurve %s[%d] after insert" % (data_path, index))
        return 0, 0.0

    max_frame = first_frame
    for frame, value, kf in pts:
        # insert() gibt den point zurueck -> sofort formen. Die referenz NICHT ueber den
        # naechsten insert hinaus halten: 'FAST' kann das point-array reallozieren.
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        _apply_kf_shape(kp, kf, frame, value, scale, tick_scale)
        if frame > max_frame:
            max_frame = frame
    fc.update()   # sortiert + rechnet die AUTO_CLAMPED-handles aus (FREE bleibt unangetastet)
    _exact_hermite(fc, pts)
    return len(pts), max_frame


def _exact_hermite(fc, pts):
    """BBS' "hermite" ist Catmull-Rom je Segment (Lerps.cubicHermite, Parameter x linear in der
    Zeit): Tangente am Segmentanfang (y2 - y0) / 2, am Ende (y3 - y1) / 2, mit preA = a und
    postB = b an den Kanalenden (KeyframeSegment). Ein kubisches Hermite-Segment IST ein Bezier mit
    Handles bei 1/3 und 2/3 der Segmentzeit, also exakt statt AUTO_CLAMPED-Naeherung.
    `pts` = [(frame, value, kf)] sortiert und ohne Dubletten, wie eingefuegt."""
    if len(pts) < 2:
        return
    points = {round(kp.co[0], 4): kp for kp in fc.keyframe_points}
    kps = [points.get(round(frame, 4)) for frame, _, _ in pts]
    if any(kp is None for kp in kps):
        return
    values = [kp.co[1] for kp in kps]
    last = len(pts) - 1
    for i in range(last):
        if str(pts[i][2].get("interp") or "linear").lower() != "hermite":
            continue
        y0, y1, y2 = values[max(i - 1, 0)], values[i], values[i + 1]
        y3 = values[min(i + 2, last)]
        third = (kps[i + 1].co[0] - kps[i].co[0]) / 3.0
        a, b = kps[i], kps[i + 1]
        a.handle_right_type = 'FREE'
        b.handle_left_type = 'FREE'
        a.handle_right = (a.co[0] + third, y1 + (y2 - y0) / 6.0)
        b.handle_left = (b.co[0] - third, y2 - (y3 - y1) / 6.0)


def _make_actor_root(replay, coll, want_models=True):
    """Root-objekt einer replay: das ECHTE BOBJ-modell wenn wir eins finden, sonst das
    platzhalter-empty wie bisher.

    Beide wege liefern EIN objekt mit denselben custom-props und rotation_mode — der
    animations-code darunter (_build_fcurve) sieht keinen unterschied und bleibt
    unveraendert. Beim modell ist der root die armature, das mesh haengt als kind dran.
    Jeder fehlerpfad faellt auf das empty zurueck, nie crashen.
    """
    name = replay.get("name") or replay.get("label") or replay.get("form") or "actor"
    obj = None
    form_type = str(replay.get("formType") or "")
    if want_models and form_type == "bbs:mob":
        # Task 4.1 (W7-B1): mob-replays kriegen die ECHTE entity-armature (mesh+bones+
        # vertex-groups) aus dem ENTITY_EXPORT-cache statt eines empties. LAZY-import:
        # entity_import -> replay_anim -> actors waere sonst ein zirkulaerer top-level-
        # import (siehe entity_import-docstring). 'formMob' = registry-id des mobs
        # (java ActorExporter, raw key — 'form' ist nur der anzeige-name, dieselbe
        # falle wie bei formModel). Kein cache-eintrag -> empty wie bisher + hinweis;
        # der export selbst ist ein request/response (entity_import.request_entity_export),
        # NICHT hier im import-pfad ausgeloest (ein vollimport mit N mob-replays wuerde
        # sonst N parallele exports feuern).
        from . import entity_import
        mob_id = str(replay.get("formMob") or "")
        cached = entity_import.cached_entity_model(mob_id) if mob_id else None
        if cached:
            try:
                obj = entity_import.build_entity_model(cached[0], cached[1], coll, name=name)
            except Exception as e:   # noqa: BLE001 — kaputtes modell darf den
                # gesamten actor-import nicht killen: empty ist der sichere fallback.
                print("[bbs-bridge] entity model import failed (%s) -> empty: %s" % (mob_id, e))
                obj = None
        else:
            print("[bbs-bridge] no ENTITY_EXPORT cache for mob %r -> empty "
                  "(entity_import.request_entity_export(<replay-uuid>) fuellt ihn)" % (mob_id,))
    elif want_models and form_type == "bbs:model":
        # 'formModel' ist der echte modell-schluessel ("emoticons/alex"). 'form' ist NUR ein
        # anzeige-name — sobald der user den actor umbenennt steht da "Bob" und wir fanden kein
        # modell (stiller empty-fallback). Java sendet formModel seit 2026-07-16; die 'form'-
        # kette bleibt als fallback fuer aeltere payloads.
        key = replay.get("formModel") or replay.get("form")
        path = _find_bobj_for_form(key) or _packaged_model_path(replay)
        cubic = (replay.get('properties') or {}).get('cubicModel') or {}
        if not path and cubic:
            if cubic.get('error'):
                # Ein nicht baubares modell kostet nur diesen actor (empty), nie den film-import;
                # laut gemeldet ueber state._import_incomplete und die statuszeile.
                state._import_incomplete[replay.get('uuid', '')] = (name, 'Cubic-Modell: ' + str(cubic['error']))
                print("[bbs-bridge] UNVOLLSTAENDIG %s: Cubic-Modell nicht baubar (%s) -> empty" % (name, cubic['error']))
            else:
                path = cubic.get('bobj')
        if path:
            try:
                # Hybrid-exact path: a model package may carry a compact native .blend
                # library beside its BOBJ.  It preserves the authored materials, drivers,
                # corrective shapes and Preserve Volume modifier.  BOBJ remains the normal
                # interactive fallback for every model without blender_source in config.json.
                cfg = _read_bobj_config(path)
                obj = native_model.import_for_bobj(path, cfg, coll, name)
                if obj is None:
                    obj = _import_bobj(path, coll, name=name)
                if obj is not None and cubic.get('translationScale'):
                    obj['bbs_pose_translation_scale'] = cubic['translationScale']
                if obj is not None and cubic.get('restRotations'):
                    obj['bbs_pose_rest_rotation'] = json.dumps(cubic['restRotations'])
            except native_model.NativeModelError as e:
                # Explicit native source is an exact-render contract.  Falling back would
                # return a plausible but materially different successful render.
                raise RuntimeError("Blender-native Quelle fuer %r unbrauchbar: %s" % (key, e)) from e
            except Exception as e:   # noqa: BLE001 — ein kaputtes modell darf den
                # gesamten actor-import nicht killen: empty ist der sichere fallback.
                print("[bbs-bridge] bobj import failed (%s) -> empty: %s" % (path, e))
                obj = None
        else:
            print("[bbs-bridge] no .bobj for form %r -> empty" % (key,))
    if obj is None:
        obj = bpy.data.objects.new(name, None)
        obj.empty_display_type = 'ARROWS'
        obj.empty_display_size = 0.5
        coll.objects.link(obj)
        if want_models and form_type == 'bbs:model':
            obj['bbs_model_error'] = 'model unavailable: ' + str(replay.get('formModel') or name)
    obj.rotation_mode = 'XYZ'      # MUSS vor rotation_euler-keyframes stehen
    obj["bbs_uuid"] = replay.get("uuid", "")
    obj["bbs_id"] = str(replay.get("id", ""))
    obj["bbs_form"] = replay.get("form", "")
    obj["bbs_form_type"] = replay.get("formType", "")
    # group = uuid des eltern-replays (BBS-ordner). NUR notiert, NICHT als Blender-parenting
    # umgesetzt: parenting wuerde die kind-transformation relativ machen, waehrend BBS die
    # kanaele absolut liefert -> doppel-transform sobald eine gruppe doch kanaele hat.
    obj["bbs_group"] = replay.get("group", "")
    obj["bbs_is_group"] = bool(replay.get("isGroup", False))
    if want_models and form_type == 'bbs:billboard':
        from . import actor_billboard
        actor_billboard.apply(obj, replay)
    # linken passiert oben (beide zweige) — das modell muss schon in der collection
    # haengen, bevor _build_bobj_armature den edit-mode betritt.
    # Global viewport + render flags are per object and do not inherit through parenting.
    actor_channel_sync.apply_visibility(obj, replay.get("enabled", True))
    return obj


def _needs_model_upgrade(obj, replay):
    return (obj is not None and obj.type == 'EMPTY'
            and replay.get('formType') == 'bbs:model'
            and bool(replay.get('formModel') or replay.get('form'))
            and getattr(bpy.context.scene, 'bbsbridge_import_models', True)
            and not any(child.type in ('MESH', 'ARMATURE') for child in obj.children_recursive))


def _ensure_actor_root(replay):
    """Reuse the recording preview's single-actor creation path for live snapshots.

    Existing actors and unrelated collection contents are never rebuilt. Model importers
    may change selection internally; restore the user's selection after creating the root.
    """
    uid = str((replay or {}).get("uuid") or "")
    obj = _find_actor_by_uuid(uid)
    placeholder = obj if _needs_model_upgrade(obj, replay) else None
    if (obj is not None and placeholder is None) or not uid:
        return obj
    if not _can_create_actor():
        return None
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is not None and any(candidate != placeholder and str(candidate.get("bbs_uuid") or "") == uid
                                for candidate in coll.all_objects):
        # A user-parented root is not a missing actor. Do not create a second UUID.
        raise RuntimeError("Actor-Hierarchie fuer '%s' geaendert; Live-Anlage pausiert" % uid)
    if coll is None:
        coll = bpy.data.collections.new(state.ACTORS_COLLECTION)
        bpy.context.scene.collection.children.link(coll)
    selected = list(bpy.context.selected_objects)
    active = bpy.context.view_layer.objects.active
    mode = active.mode if active is not None else 'OBJECT'
    pose_selection = {
        actor: ({bone.name for bone in actor.pose.bones if bone.select},
                actor.data.bones.active.name if actor.data.bones.active else None)
        for actor in selected if actor.type == 'ARMATURE' and actor.mode == 'POSE'}
    want_models = getattr(bpy.context.scene, "bbsbridge_import_models", True)
    try:
        if mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
        try:
            obj = _make_actor_root(replay, coll, want_models)
        except (RuntimeError, ValueError, OSError) as exc:
            if placeholder is None:
                raise
            placeholder['bbs_model_error'] = 'model unavailable: ' + str(exc)
            return placeholder
        if placeholder is not None:
            if obj.type != 'ARMATURE':
                placeholder['bbs_model_error'] = str(obj.get('bbs_model_error') or 'model unavailable')
                _remove_actor_root(obj)
                return placeholder
            from . import actor_placeholder
            actor_placeholder.transfer(placeholder, obj)
            if 'bbs_model_error' in obj:
                del obj['bbs_model_error']
            selected = [obj if actor == placeholder else actor for actor in selected]
            if active == placeholder:
                active = obj
            bpy.data.objects.remove(placeholder, do_unlink=True)
        # Known new bridge object; its initial authoritative curves are still pending.
        obj[_ACTOR_SNAPSHOT_PROP] = ""
        return obj
    finally:
        if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
            bpy.ops.object.mode_set(mode='OBJECT')
        for candidate in list(bpy.context.selected_objects):
            candidate.select_set(False)
        for candidate in selected:
            candidate.select_set(True)
        bpy.context.view_layer.objects.active = active
        if active is not None and mode != 'OBJECT':
            bpy.ops.object.mode_set(mode=mode)
        for actor, (names, active_bone) in pose_selection.items():
            for bone in actor.pose.bones:
                bone.select = bone.name in names
            if active_bone is not None:
                actor.data.bones.active = actor.data.bones.get(active_bone)


def _can_create_actor():
    return bpy.context.mode in ('OBJECT', 'POSE') and not any(
        window.modal_operators for window in bpy.context.window_manager.windows)


def _pump_deferred_actors():
    if (_deferred_snapshot is not None and _can_create_actor()
            and time.monotonic() >= _deferred_retry_after):
        _apply_live_snapshot(_deferred_snapshot, 'deferred')


def _clear_item_fcurves(obj, only=None):
    """Die eigenen item-index-fcurves (`["bbs_item_*"]`) eines actor-roots entfernen — spiegel
    von _clear_actor_object_fcurves, aber fuer die custom-prop-kanaele dieses moduls (task 5.2).
    Damit ein live-sync die item-animation ERSETZT statt zu stapeln. Rueckwaerts-scan per re-find
    (remove invalidiert referenzen, world_anim-lehre). Wirft nie."""
    for coll in _object_fcurve_collections(obj):
        while True:
            victim = None
            for fc in coll:
                # data_path einer custom-prop-fcurve ist '["bbs_item_main_hand"]'
                if (_ITEM_PROP_PREFIX in fc.data_path and fc.data_path.startswith('["')
                        and (only is None or fc.data_path in {
                            '["%s%s"]' % (_ITEM_PROP_PREFIX, key) for key in only})):
                    victim = fc
                    break
            if victim is None:
                break
            try:
                coll.remove(victim)
            except (RuntimeError, ReferenceError):
                break


def _apply_item_channels(obj, replay, tick_scale, only=None):
    """Die 15 ItemStack-kanaele (payload-feld "strings", java StringChannels) auf den actor-root
    legen — als HB-4-konsumierbarer HUD-item-transport (task 5.2). Zwei formen (siehe _ITEM_CHANNELS
    -doku): (1) volle tick->id-tabelle als JSON custom-prop `bbs_items`, (2) je kanal ein INTEGER-
    index-fcurve `bbs_item_<kanal>` (CONSTANT) + id-tabelle `bbs_item_<kanal>_ids` (JSON-array),
    per frame Blender-nativ abfragbar. LEER/fehlend -> alle eigenen props/fcurves weg (idempotenz).
    Laeuft im timer = main-thread; wirft nie (ein kaputter kanal darf den import nicht killen).
    Gibt die zahl der geschriebenen item-keys zurueck."""
    if only is not None and not only:
        return 0
    strings = replay.get("strings") or {}
    selected = set(_ITEM_CHANNELS) if only is None else set(only)
    table = json.loads(obj.get(_ITEM_TABLE_PROP, "{}")) if only is not None else {}
    _clear_item_fcurves(obj, selected)
    for ch_id in selected:
        obj.pop(_ITEM_PROP_PREFIX + ch_id, None)
        obj.pop(_ITEM_PROP_PREFIX + ch_id + "_ids", None)
        table.pop(ch_id, None)
        if strings.get(ch_id):
            table[ch_id] = strings[ch_id]
    strings = {key: value for key, value in strings.items() if key in selected}
    obj[_ITEM_TABLE_PROP] = json.dumps(table, separators=(",", ":"))
    if not strings:
        return 0

    # (1) Volle tabelle verlustfrei ablegen — HB-4 kann daraus bei jedem frame die exakte id
    # lesen, auch fuer kanaele die (theoretisch) nur EINEN eintrag haben.
    try:
        obj[_ITEM_TABLE_PROP] = json.dumps(table, separators=(",", ":"))
    except (TypeError, ValueError):
        pass

    n_keys = 0
    for ch_id, kfs in strings.items():
        if ch_id not in _ITEM_CHANNELS or not isinstance(kfs, (list, tuple)) or not kfs:
            continue
        # tick-sortiert + je id EIN index; id-tabelle = die distinct-ids in erst-auftritts-reihenfolge.
        pairs = []
        for kf in kfs:
            if not isinstance(kf, (list, tuple)) or len(kf) < 2:
                continue
            try:
                tick = float(kf[0])
            except (TypeError, ValueError):
                continue
            pairs.append((tick, str(kf[1])))
        if not pairs:
            continue
        pairs.sort(key=lambda p: p[0])
        ids = []
        prop = _ITEM_PROP_PREFIX + ch_id
        # Custom-prop MUSS existieren bevor keyframe_insert sie animieren kann (int-typ).
        obj[prop] = 0
        wrote = False
        seen = set()
        for tick, item_id in pairs:
            if item_id not in ids:
                ids.append(item_id)
            idx = ids.index(item_id)
            frame = tick * tick_scale
            marker = round(frame, 4)
            if marker in seen:
                continue          # dublette auf demselben frame -> erste gewinnt (wie _build_fcurve)
            seen.add(marker)
            obj[prop] = idx
            try:
                if obj.keyframe_insert(data_path='["%s"]' % prop, frame=frame):
                    wrote = True
                    n_keys += 1
            except (RuntimeError, TypeError):
                pass
        # id-tabelle: index -> id (index i entspricht ids[i], die index-fcurve zeigt darauf).
        obj[prop + "_ids"] = json.dumps(ids, separators=(",", ":"))
        # Index-fcurve CONSTANT machen: ein item wechselt HART, kein interpolieren zwischen ids.
        if wrote:
            fc = _find_fcurve_by_path(obj, '["%s"]' % prop)
            if fc is not None:
                for kp in fc.keyframe_points:
                    kp.interpolation = 'CONSTANT'
                fc.update()
    return n_keys


def _find_fcurve_by_path(obj, data_path):
    """Wie _find_fcurve, aber fuer einen custom-prop-data_path OHNE index (skalar). 5.2 slotted
    actions: channelbag zuerst, legacy-wrapper als fallback. Wirft nie."""
    for collection in _object_fcurve_collections(obj):
        fc = collection.find(data_path)
        if fc is not None:
            return fc
    return None


@pose_overlays.importing
def _build_actors(replays, film, source):
    """Alle replays als animierte empties bauen. Laeuft im timer = main-thread."""
    t0 = time.time()
    _clear_actors_collection()
    scene = bpy.context.scene
    coll = bpy.data.collections.new(state.ACTORS_COLLECTION)
    scene.collection.children.link(coll)

    # MC laeuft 20 ticks/s -> frame = tick * (fps / 20). fps_base ist Blenders krummer
    # divisor (23.976 = 24 / 1.001) — ohne den driftet der import bei NTSC-raten.
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0

    # getattr-guard: beim live-reload kann der import feuern bevor register() die prop legt
    want_models = getattr(scene, "bbsbridge_import_models", True)

    n_keys = 0
    n_models = 0
    max_frame = 0.0
    ignored = set()      # numerische kanaele die wir (noch) nicht abbilden
    skipped = set()      # kanaele die BBS gar nicht erst als zahlen schicken konnte
    for replay in replays:
        if not isinstance(replay, dict):
            continue
        obj = _make_actor_root(replay, coll, want_models)
        if obj.type != 'EMPTY':      # armature (rigged) ODER mesh (statisches modell)
            n_models += 1
        channels = replay.get("channels") or {}
        for ch_id, keyframes in channels.items():
            spec = _actor_channel_spec(channels, ch_id)
            if spec is None:
                if (ch_id not in {"pitch", "headYaw", "bodyYaw", "sneaking"}
                        and not (ch_id == "yaw" and channels.get("bodyYaw"))):
                    ignored.add(ch_id)
                continue
            if not keyframes:
                continue
            k, mf = _build_fcurve(obj, keyframes, spec, tick_scale)
            n_keys += k
            if mf > max_frame:
                max_frame = mf
        for ch_id in (replay.get("skippedChannels") or []):
            skipped.add(str(ch_id))
        # W9-B2 (task 2.6): headYaw/bodyYaw/pitch -> head/body-pose-bones auf bbs:model-
        # armatures (NACH den objekt-fcurves — model_pose liest sie). Lazy-import wie
        # entity_import (zirkulaer sonst); wirft nie, import-guard gegen deploy-schieflage.
        try:
            from . import model_pose
            model_pose.apply_actor_pose(obj, replay, tick_scale)
        except ImportError as e:
            print("[bbs-bridge] model_pose nicht ladbar (deploy?): %s" % e)
        # PHASE B (per-bone pose, 2026-07-18): explizite pose:<bone>-transforms aus dem payload
        # auf die armature-bones. NACH model_pose (das head/body/sneak-defaults setzt) — actor_bones
        # GEWINNT auf bones die einen expliziten pose:<bone>-kanal tragen. VOR den material-applies
        # (die per-bone-material die bones referenzieren). Macht aus dem starren actor animierte glieder.
        try:
            from . import actor_bones
            actor_bones.apply_bone_pose(obj, replay, tick_scale)
        except ImportError as e:
            print("[bbs-bridge] actor_bones nicht ladbar (deploy?): %s" % e)
        try:
            from . import actor_form_switch
            actor_form_switch.apply(obj, replay, tick_scale)
        except Exception as e:
            print("[bbs-bridge] form-switch nicht anwendbar: %s" % e)
        # B4 (per-bone MATERIAL): color/glow/paint pro bone auf eigene material-slots (vertex-group-
        # split am mesh-child). NACH bone-pose (die bones stehen), reuse actor_material-muster.
        try:
            from . import actor_bone_material
            actor_bone_material.apply_bone_material(obj, replay, tick_scale)
        except ImportError as e:
            print("[bbs-bridge] actor_bone_material nicht ladbar (deploy?): %s" % e)
        # task 5.2 (HB-3): item-kanaele (payload "strings") als HUD-item-transport auf den root.
        # Wirft nie (defensiv in der funktion selbst).
        n_keys += _apply_item_channels(obj, replay, tick_scale)
        # Task 5 (material-sync): BBS color/opacity aufs eigene material (per-actor-kopie in
        # actor_material). NACH dem transform/model_pose-bau, mit demselben tick_scale — dieselbe
        # zeit-basis. Empties (kein material_slot) sind ein no-op, apply_color_channels wirft nie.
        try:
            actor_material.apply_color_channels(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] material-sync fuer actor uebersprungen: %s" % _e)
        # PHASE G (glow/emission): BBS glow-kanal -> BSDF Emission Color + Strength. Teilt die
        # per-actor material-kopie mit color (apply_glow_channels ruft dasselbe idempotente
        # ensure_owned_material). NACH color, mit demselben tick_scale. Empty = no-op, wirft nie.
        try:
            actor_material.apply_glow_channels(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] glow-sync fuer actor uebersprungen: %s" % _e)
        # WELLE 1 (2026-07-18, 4 parallel-worker): weitere material-kanaele. REIHENFOLGE-PFLICHT —
        # paint routet den BBS_Tint.Result->BaseColor-link auf tint->paint->BSDF um, muss also NACH
        # color/glow (die die tint-kette bauen) laufen. texture speist die base-color-quelle. Jeder
        # aufruf ist idempotent + wirft nie (empty = no-op).
        try:
            actor_texture.apply_texture_channels(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] texture-sync fuer actor uebersprungen: %s" % _e)
        try:
            actor_paint.apply_paint_channels(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] paint-sync fuer actor uebersprungen: %s" % _e)
        try:
            actor_shapekeys.apply_form_channels(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] form/shapekey-sync fuer actor uebersprungen: %s" % _e)
        try:
            actor_shapenodes.apply_shape_graph(obj, replay)
        except Exception as _e:
            print("[bbs-bridge] shapegraph-sync fuer actor uebersprungen: %s" % _e)
        # PHASE V: form-typ-spezifika. LightForm→kind-lampe, LabelForm→text-kind, Illusion→array-
        # modifier. Statische form-werte (kein tick_scale). Jede apply no-oppt still auf fremdem
        # form-typ (wird auf JEDEM actor gerufen). Additiv (kein eingriff in _make_actor_root).
        try:
            from . import actor_light
            actor_light.apply_light(obj, replay)
        except Exception as _e:
            print("[bbs-bridge] light-sync fuer actor uebersprungen: %s" % _e)
        try:
            from . import actor_label
            actor_label.apply_label(obj, replay)
        except Exception as _e:
            print("[bbs-bridge] label-sync fuer actor uebersprungen: %s" % _e)
        try:
            from . import actor_illusion
            actor_illusion.apply_illusion(obj, replay)
        except Exception as _e:
            print("[bbs-bridge] illusion-sync fuer actor uebersprungen: %s" % _e)

        # BBS visible-kanal -> hide_viewport/hide_render-keys (CONSTANT), als LETZTES: kinder, die
        # oben erst entstehen (form-empties, illusionen), muessen mitgekeyt werden. Ohne ihn blieb ein in
        # BBS ausgeblendeter actor in Blender in allen frames sichtbar.
        try:
            n_keys += actor_channel_sync.apply_visible_keys(obj, replay, tick_scale)
        except Exception as _e:
            print("[bbs-bridge] visible-sync fuer actor uebersprungen: %s" % _e)

        fingerprint = _snapshot_fingerprint([replay], tick_scale)
        if fingerprint is not None:
            obj[_ACTOR_SNAPSHOT_PROP] = fingerprint
        actor_channel_sync.remember(obj, replay, tick_scale)
        from . import actor_pose_preview
        actor_pose_preview.adopt(obj)

    # task 5.3b (HB-4): roh-payload leichtgewichtig cachen, damit der HUD-composite die
    # numerischen kanaele (health/hunger/armor) am gebundenen frame lesen kann (siehe
    # state._last_actors_payload-doku). Nur die channels behalten (nicht die schweren
    # modell-felder) — reicht dem HUD und haelt den cache klein. Wirft nie.
    _cache_actors_payload(replays)

    # frame_end nur ANHEBEN, nie senken — sonst kuerzt ein kleiner actor-import die szene.
    end = int(math.ceil(max_frame))
    if end > scene.frame_end:
        scene.frame_end = end

    dt = time.time() - t0
    state._actors_status = "%d actors / %d models / %d keys (%.1fs)%s%s" % (
        len(replays), n_models, n_keys, dt,
        _mount_scene_suffix(replays), _bbs_only_effects_suffix(replays))
    unavailable = sum(bool(obj.get('bbs_model_error')) for obj in coll.objects)
    if unavailable:
        state._actors_status += ' [model unavailable: %d]' % unavailable
    print("[bbs-bridge] actors import done: %d replays (%d real models, %d empties), "
          "%d keyframes -> '%s' in %.2fs (film='%s', source=%s, %.3f frames/tick)"
          % (len(replays), n_models, len(replays) - n_models, n_keys,
             state.ACTORS_COLLECTION, dt, film, source, tick_scale))
    if ignored:
        print("[bbs-bridge] channels NOT applied (no mapping yet): %s" % ", ".join(sorted(ignored)))
    if skipped:
        # Die 15 item-kanaele stehen weiter in skippedChannels (java-seite unveraendert), reisen
        # aber seit task 5.2 ZUSAETZLICH als ids im "strings"-feld und liegen jetzt als
        # bbs_item_*-props/fcurves am root (siehe _apply_item_channels). Der rest von skipped
        # (z.b. ridden/MountLink) reist wirklich nicht.
        items_here = sorted(s for s in skipped if s in _ITEM_CHANNELS)
        rest = sorted(s for s in skipped if s not in _ITEM_CHANNELS)
        if items_here:
            print("[bbs-bridge] item channels transported as ids (task 5.2, bbs_item_*): %s"
                  % ", ".join(items_here))
        if rest:
            print("[bbs-bridge] channels BBS could not send: %s" % ", ".join(rest))


#: Die objekt-level data_paths, die _build_fcurve je bauen kann ({"location",
#: "rotation_euler"}) — und damit die EINZIGEN, die der live-sync ersetzen darf.
_ACTOR_DATA_PATHS = {spec[0] for spec in _ACTOR_CHANNELS.values()}


def _object_fcurve_collections(obj):
    """Alle fcurve-container der objekt-action (5.2 slotted actions: channelbag zuerst,
    legacy-wrapper als fallback — dieselben zwei wege wie _find_fcurve, nur als
    container-liste statt einzel-lookup)."""
    return pose_overlays.collections(obj)


def _clear_actor_object_fcurves(obj):
    """NUR die objekt-level actor-kanaele (location/rotation_euler) entfernen — CHIRURGISCH
    statt animation_data_clear() (task 4.1, W7-B1): der actor-root kann eine armature sein,
    die ZUSAETZLICH pose-bone-fcurves von replay_anim.py traegt (mob-replay-pose auf
    DERSELBEN action, siehe replay_anim-docstring "IDEMPOTENZ") — ein wholesale-clear
    loeschte die bei jedem BBS-edit/push mit. Galt vorher schon fuer die alex-BOBJ-armature,
    wurde mit den echten mob-rigs zum feature-killer. Entfernen einzeln mit re-scan statt
    ueber eine kopierte liste (remove invalidiert referenzen — world_anim-lehre)."""
    removed = 0
    for coll in _object_fcurve_collections(obj):
        while True:
            victim = None
            for fc in coll:
                if fc.data_path in _ACTOR_DATA_PATHS:
                    victim = fc
                    break
            if victim is None:
                break
            try:
                coll.remove(victim)
                removed += 1
            except (RuntimeError, ReferenceError):
                break
    return removed


def _find_actor_by_uuid(uuid):
    """Actor-root aus der MC_Actors-collection ueber sein bbs_uuid-property finden.

    Ueber die uuid, NIE ueber den namen: Blender haengt bei namenskollision ein '.001' an
    (unser alex hiess nach dem re-import 'body.001'), und BBS-actors darf der user frei
    umbenennen. Die uuid ist das einzige stabile band zwischen den beiden welten.
    """
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return None
    for o in coll.objects:
        if o.parent is None and o.get("bbs_uuid") == uuid:
            return o
    return None


def _channels_fingerprint(replays):
    """Billiger vergleichswert ueber ALLE kanaele: aendert sich nur, wenn ein keyframe
    (tick/wert/interp) wirklich anders ist. Verhindert, dass der live-loop 2x/sekunde
    F-curves neu baut, obwohl der user gar nichts editiert hat.

    Die uuid ist BEWUSST teil des hashes: ein GELOESCHTES replay aendert damit den
    fingerprint auch wenn kein einziger keyframe angefasst wurde — sonst wuerde der
    dedup-skip in _handle_actors_push/_handle_actors_export_result den geister-fix
    (_remove_orphan_actors) aushungern und das objekt bliebe bis zum naechsten echten
    keyframe-edit stehen (SYMPTOME "geister-objekt", W10-B4).

    task 5.2 (HB-3): `strings` (item-kanaele) MUSS mit im hash — sonst deduped der live-
    loop einen reinen item-wechsel (user tauscht das hand-item, aber KEIN numerischer
    kanal aendert sich) weg und die HUD-item-animation bliebe stale (gleiche falle wie
    die geister-uuid oben, nur fuer items).

    2026-07-19 (praxis-fund): `properties` MUSS ebenfalls mit im hash — das ist der
    material/per-bone-topf (color/glow/paint/bones). Die java-seite (ActorPushTracker)
    foldet properties seit 2026-07-18, das Blender-gegenstueck hier NICHT → ein reiner
    MATERIAL-edit (user keyframet nur eine farbe, kein transform) wurde vom live-loop
    WEG-DEDUPLIZIERT: der push kam an, aber der fingerprint war identisch → kein re-build.
    Selbe falle wie strings/items, nur fuer material. Ohne dies syncen material-only-edits
    im echten setup NICHT live (gemessen)."""
    try:
        payload = json.dumps(
            [[r.get("uuid"), r.get("enabled", True), r.get("channels"),
              r.get("strings"), r.get("properties")]
             for r in replays],
            sort_keys=True, separators=(",", ":"), allow_nan=False)
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()
    except (TypeError, ValueError, AttributeError):
        return None


def _snapshot_fingerprint(replays, tick_scale):
    """Include the conversion basis; changing FPS requires rebuilding remote curves."""
    digest = _channels_fingerprint(replays)
    return "v1:%s:%s" % (float(tick_scale).hex(), digest) if digest is not None else None


def _validate_live_replays(replays):
    """Reject malformed snapshots before any mutation, especially orphan deletion."""
    if not isinstance(replays, list):
        raise ValueError("actor snapshot must contain a replays list")
    seen = set()
    for replay in replays:
        uid = replay.get("uuid") if isinstance(replay, dict) else None
        if not isinstance(uid, str) or not uid.strip() or uid in seen:
            raise ValueError("actor snapshot contains a missing or duplicate UUID")
        seen.add(uid)
        for field in ("channels", "strings", "properties"):
            value = replay.get(field)
            if value is not None and not isinstance(value, dict):
                raise ValueError("actor %s: %s must be an object" % (uid, field))
        actor_channel_sync.validate(replay)
    if _channels_fingerprint(replays) is None:
        raise ValueError("actor snapshot contains non-JSON or non-finite data")


def _remove_actor_root(obj):
    """EINEN actor-root SAMT kindern + nun-unbenutzten daten entfernen (geister-fix W10-B4).

    Pro-objekt-ausgabe von _clear_actors_collection: dieselbe hygiene-reihenfolge
    (objekte -> actions -> meshes/armatures -> materials -> images) und dieselbe
    fake-user-regel — nur auf EIN replay begrenzt, weil der live-sync nie die ganze
    collection wegwerfen darf (auswahl/edit-state der VERBLIEBENEN actors bleibt stehen,
    siehe _live_sync_actors-doc). Geteilte daten (z.b. das alex-material eines zweiten
    actors) ueberleben automatisch: entfernt wird nur was nach dem objekt-remove
    users == 0 hat.
    """
    if obj.get("bbs_uuid"):
        try:
            from . import actor_form_switch
            actor_form_switch.remove_for_actor(obj)
        except Exception as exc:
            print("[bbs-bridge] form-switch cleanup uebersprungen: %s" % exc)
    actions, meshes, arms, lights, mats = set(), set(), set(), set(), set()
    victims = [obj] + list(obj.children_recursive)
    for o in victims:
        ad = o.animation_data
        if ad is not None and ad.action is not None:
            actions.add(ad.action)
        for slot in o.material_slots:
            if slot.material is not None:
                mats.add(slot.material)
        data = o.data
        if isinstance(data, bpy.types.Mesh):
            meshes.add(data)
        elif isinstance(data, bpy.types.Armature):
            arms.add(data)
        elif isinstance(data, bpy.types.Light):
            lights.add(data)
    for o in victims:
        try:
            bpy.data.objects.remove(o, do_unlink=True)
        except (ReferenceError, RuntimeError):
            pass
    for act in actions:
        try:
            # fake-user MUSS weg, sonst ist users nie 0 (gleiche begruendung wie in
            # _clear_actors_collection — die action gehoert komplett diesem actor:
            # objekt-kanaele UND pose-bone-kanaele liegen auf DERSELBEN action).
            act.use_fake_user = False
            if act.users == 0:
                bpy.data.actions.remove(act)
        except (ReferenceError, RuntimeError):
            pass
    for me in meshes:
        try:
            if me.users == 0:
                bpy.data.meshes.remove(me)
        except (ReferenceError, RuntimeError):
            pass
    for arm in arms:
        try:
            if arm.users == 0:
                bpy.data.armatures.remove(arm)
        except (ReferenceError, RuntimeError):
            pass
    for light in lights:
        try:
            if light.users == 0:
                bpy.data.lights.remove(light)
        except (ReferenceError, RuntimeError):
            pass
    imgs = set()
    for mat in mats:
        try:
            if mat.use_nodes and mat.node_tree is not None:
                for node in mat.node_tree.nodes:
                    if node.type == 'TEX_IMAGE' and node.image is not None:
                        imgs.add(node.image)
            if mat.users == 0:
                bpy.data.materials.remove(mat)
        except (ReferenceError, RuntimeError):
            pass
    for img in imgs:
        try:
            if img.users == 0:
                bpy.data.images.remove(img)
        except (ReferenceError, RuntimeError):
            pass


def _remove_orphan_actors(replays):
    """Geister-fix (W10-B4, SYMPTOME 393fdad): actor-objekte entfernen, deren replay aus
    dem film VERSCHWUNDEN ist. Der payload ist die wahrheit ueber den film — ein objekt
    mit bbs_uuid-property, dessen uuid nicht mehr im payload steht, ist eine leiche des
    geloeschten replays (vorher raeumte das erst der naechste vollimport via _clear).

    NUR objekte MIT bbs_uuid-property werden angefasst: alles was der user selbst in die
    collection gezogen hat, bleibt unberuehrt (invariante 5). Kinder (mesh unter der
    armature) sterben mit ihrem root, darum laufen hier nur parent-lose objekte.
    Gibt die zahl der entfernten roots zurueck."""
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return 0
    known = {str(r.get("uuid", "")) for r in replays if isinstance(r, dict)}
    orphans = []
    for o in list(coll.objects):
        if o.parent is not None:
            continue
        uid = o.get("bbs_uuid")
        if uid is None or (o.get('bbs_model_import_awaiting_echo') and str(uid) not in known):
            continue
        if o.get('bbs_model_import_awaiting_echo'):
            del o['bbs_model_import_awaiting_echo']
        if str(uid) not in known:
            orphans.append(o)
    for o in orphans:
        name = o.name
        _remove_actor_root(o)
        print("[bbs-bridge] live-sync: geister-actor %r entfernt "
              "(replay nicht mehr im film)" % name)
    return len(orphans)


def _live_sync_actors(replays):
    """Apply a validated snapshot without rebuilding the actor collection.

    Create only missing roots through the shared recording/import helper. An unchanged
    remote actor leaves local animation and model edits untouched, including after reload.
    Root and item channels use separate merge bases and preflight conflict checks.
    Bone/material properties still use grouped importers, not general transactions.

    Only a valid, unfrozen snapshot may remove absent actors. A closed film is filtered
    by the caller; an open film with an explicitly empty list removes its actor roots.
    """
    if shot_freeze.ist_eingefroren():
        return False
    from . import actor_document as document
    if document.binding() and not document._restoring:
        document.activate()
        if any(record.get('sceneTimingAfter') is not None or record.get('sceneTiming') is not None
               for record in document.records()):
            raise RuntimeError('Actor snapshot waits for the pending durable FPS change')
    _validate_live_replays(replays)
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is not None:
        for root in coll.objects:
            if root.parent is None and root.get("bbs_uuid") is not None:
                if _ACTOR_SNAPSHOT_PROP not in root:
                    raise RuntimeError("Sync-Basis fehlt fuer '%s'; Szene sichern und "
                                       "expliziten Import verwenden" % root.name)
    scene = bpy.context.scene
    fps = scene.render.fps / (scene.render.fps_base or 1.0)
    tick_scale = fps / 20.0
    # Check every existing actor before writing any of the batch's channels.
    plans = {}
    for replay in replays:
        root = _find_actor_by_uuid(replay["uuid"])
        fingerprint = _snapshot_fingerprint([replay], tick_scale)
        if root is not None and root.get(_ACTOR_SNAPSHOT_PROP) != fingerprint:
            plans[replay["uuid"]] = actor_channel_sync.prepare(root, replay, tick_scale)
    from . import actor_history_observations
    return actor_history_observations.apply(replays, plans, tick_scale, _apply_live_actor_snapshot)


@pose_overlays.importing
def _apply_live_actor_snapshot(replays, plans, tick_scale, strict_history):
    """Apply preflighted native channels inside the shared-history rollback boundary."""
    scene = bpy.context.scene
    n_keys = 0
    max_frame = 0.0
    applied_history = []
    for replay in replays:
        if not isinstance(replay, dict):
            continue
        fingerprint = _snapshot_fingerprint([replay], tick_scale)
        upgrade = _needs_model_upgrade(_find_actor_by_uuid(replay['uuid']), replay)
        obj = _ensure_actor_root(replay)
        if obj is None:
            continue
        if fingerprint is not None and obj.get(_ACTOR_SNAPSHOT_PROP) == fingerprint:
            continue
        plan = plans.get(replay["uuid"])
        if plan is None:
            plan = actor_channel_sync.prepare(obj, replay, tick_scale)
        if upgrade:
            from . import model_pose
            plan['pose'] = plan['model_pose'] = plan['properties'] = True
            plan['derived_bones'] = model_pose.owned_bones(obj)
            explicit = actor_channel_sync._property_groups(replay)
            plan['bone_changes'] = set(explicit['bones'])
            plan['pose_track_changes'] = set(explicit['poseTrack'])
            plan['changes']['properties'].update((replay.get('properties') or {}).keys())
        obj[_ACTOR_SNAPSHOT_PROP] = ""
        k, mf = actor_channel_sync.apply_roots(obj, replay, tick_scale, plan)
        n_keys += k
        max_frame = max(max_frame, mf)
        if plan.get('visibility_changed'):
            # apply_visible_keys statt apply_visibility: raeumt die visible-keys, wenn der replay
            # deaktiviert wird (sonst wuerden die kurven das statische hide ueberschreiben).
            actor_channel_sync.apply_visible_keys(obj, replay, tick_scale)
        if plan['pose']:
            if plan.get('pose_reset'):
                from . import actor_bones
                actor_bones.reset_pose(obj)
            # W9-B2: bone-pose nachziehen (gleicher anschluss wie in _build_actors — die
            # pose-keys ueberleben das chirurgische clear oben, waeren aber jetzt stale
            # gegen die frischen objekt-kurven/kanaele). Wirft nie.
            if plan.get('model_pose'):
                try:
                    from . import model_pose
                    model_pose.apply_actor_pose(obj, replay, tick_scale)
                except ImportError as e:
                    if strict_history:
                        raise
                    print("[bbs-bridge] model_pose nicht ladbar (deploy?): %s" % e)
            # PHASE B: only remote-changed bones may replace authored Blender curves.
            changed_bones = set(plan.get('bone_changes', ())) | set(
                plan.get('pose_track_changes', ()))
            explicit = actor_channel_sync._property_groups(replay)
            changed_bones |= set(plan.get('derived_bones', ())) & (
                set(explicit['bones']) | set(explicit['poseTrack']))
            if plan.get('pose_reset'):
                changed_bones = set(explicit['bones']) | set(explicit['poseTrack'])
            changed_rig = set(plan.get('rig_changes', ()))
            if changed_bones or changed_rig:
                try:
                    from . import actor_bones
                    actor_bones.apply_bone_pose(
                        obj, replay, tick_scale, changed_bones, changed_rig)
                except ImportError as e:
                    if strict_history:
                        raise
                    print("[bbs-bridge] actor_bones nicht ladbar (deploy?): %s" % e)
            try:
                from . import actor_form_switch
                actor_form_switch.apply(obj, replay, tick_scale)
            except Exception as e:
                print("[bbs-bridge] form-switch live-sync uebersprungen: %s" % e)
            # B4 (per-bone MATERIAL): color/glow/paint pro bone auf eigene material-slots (vertex-group-
            # split am mesh-child). NACH bone-pose (die bones stehen), reuse actor_material-muster.
            try:
                from . import actor_bone_material
                actor_bone_material.apply_bone_material(obj, replay, tick_scale)
            except ImportError as e:
                print("[bbs-bridge] actor_bone_material nicht ladbar (deploy?): %s" % e)
        # task 5.2 (HB-3): item-kanaele nachziehen. _apply_item_channels raeumt die eigenen
        # item-fcurves/props selbst (das chirurgische _clear oben fasst sie nicht an — sie
        # liegen NICHT in _ACTOR_DATA_PATHS). Wirft nie.
        n_keys += _apply_item_channels(obj, replay, tick_scale, plan['changes']['strings'])
        if plan['properties']:
            if replay.get('formType') == 'bbs:billboard':
                from . import actor_billboard
                actor_billboard.apply(obj, replay)
            # Task 5 (material-sync): color/opacity im live-pfad frisch halten. ensure_owned_material
            # ist idempotent (endet der material-name schon auf __<uuid8>, wird NICHT neu kopiert),
            # apply_color_channels legt die color-fcurves neu (ersetzt); wirft nie. Empty = no-op.
            try:
                actor_material.apply_color_channels(obj, replay, tick_scale)
            except Exception as _e:
                print("[bbs-bridge] material-sync fuer actor uebersprungen: %s" % _e)
            # PHASE G (glow): emission im live-pfad frisch halten (gleicher anschluss wie
            # _build_actors — idempotentes ensure_owned_material, apply_glow_channels ersetzt die
            # emission-fcurves). Empty = no-op, wirft nie.
            try:
                actor_material.apply_glow_channels(obj, replay, tick_scale)
            except Exception as _e:
                print("[bbs-bridge] glow-sync fuer actor uebersprungen: %s" % _e)
            try:
                actor_channel_sync.apply_visible_keys(obj, replay, tick_scale)
            except Exception as _e:
                print("[bbs-bridge] visible-sync fuer actor uebersprungen: %s" % _e)
            # WELLE 1: texture/paint/form/shapegraph im live-pfad frisch halten (reihenfolge wie
            # _build_actors — paint NACH color/glow). Alle idempotent + werfen nie.
            try:
                actor_texture.apply_texture_channels(obj, replay, tick_scale)
            except Exception as _e:
                print("[bbs-bridge] texture-sync fuer actor uebersprungen: %s" % _e)
            try:
                actor_paint.apply_paint_channels(obj, replay, tick_scale)
            except Exception as _e:
                print("[bbs-bridge] paint-sync fuer actor uebersprungen: %s" % _e)
            try:
                changed_props = set(plan['changes']['properties'])
                shape_names = (plan.get('shape_changes', set())
                               if 'shapeKeys' in changed_props else set())
                transform_changed = bool(
                    changed_props & {'formTransform', 'formTransformOverlay'})
                actor_shapekeys.apply_form_channels(
                    obj, replay, tick_scale, shape_names, transform_changed,
                    plan.get('shape_timeline_changed', False))
            except Exception as _e:
                if strict_history and (plan.get('shape_changes') or plan.get('shape_timeline_changed')):
                    raise
                print("[bbs-bridge] form/shapekey-sync fuer actor uebersprungen: %s" % _e)
            try:
                actor_shapenodes.apply_shape_graph(obj, replay)
            except Exception as _e:
                print("[bbs-bridge] shapegraph-sync fuer actor uebersprungen: %s" % _e)
            # PHASE V: form-typ-spezifika. LightForm→kind-lampe, LabelForm→text-kind, Illusion→array-
            # modifier. Statische form-werte (kein tick_scale). Jede apply no-oppt still auf fremdem
            # form-typ (wird auf JEDEM actor gerufen). Additiv (kein eingriff in _make_actor_root).
            try:
                from . import actor_light
                actor_light.apply_light(obj, replay)
            except Exception as _e:
                print("[bbs-bridge] light-sync fuer actor uebersprungen: %s" % _e)
            try:
                from . import actor_label
                actor_label.apply_label(obj, replay)
            except Exception as _e:
                print("[bbs-bridge] label-sync fuer actor uebersprungen: %s" % _e)
            try:
                from . import actor_illusion
                actor_illusion.apply_illusion(obj, replay)
            except Exception as _e:
                print("[bbs-bridge] illusion-sync fuer actor uebersprungen: %s" % _e)
        actor_channel_sync.remember(obj, replay, tick_scale, plan)
        from . import actor_pose_preview
        actor_pose_preview.adopt(obj, None if plan.get('pose_reset') else set(plan.get('bone_changes', ())) | set(plan.get('pose_track_changes', ())) | set(plan.get('derived_bones', ())))
        applied_history.append((obj, replay, plan))
        if fingerprint is not None:
            obj[_ACTOR_SNAPSHOT_PROP] = fingerprint
    # NACH dem update-durchlauf: leichen geloeschter replays weg (doc oben). Der
    # dedup-skip der aufrufer verhungert diesen pfad nicht — _channels_fingerprint
    # hasht die uuids mit, ein entferntes replay AENDERT den fingerprint garantiert.
    _remove_orphan_actors(replays)
    # task 5.3b (HB-4): HUD-payload-cache auch im live-pfad frisch halten (siehe
    # _build_actors + state._last_actors_payload-doku). Wirft nie.
    _cache_actors_payload(replays)
    end = int(math.ceil(max_frame))
    if end > scene.frame_end:
        scene.frame_end = end
    return applied_history


#: die numerischen kanaele die der HUD-composite (HB-4) braucht — health/hunger/armor.
#: Nur DIESE cachen (nicht die schweren modell-felder), reicht dem HUD, haelt es klein.
_HUD_NUMERIC_CHANNELS = ("health", "hunger", "armor")


def _cache_actors_payload(replays):
    """Task 5.3b (HB-4): den roh-payload leichtgewichtig nach state._last_actors_payload
    cachen ({uuid: {"channels": {health/hunger/armor: [...]}}}) — quelle der numerischen
    HUD-kanaele, die actors.py nicht als fcurve konsumiert (landen in `ignored`). NUR die
    HUD-relevanten kanaele behalten. Ueberschreibt den cache komplett (nur letzte welle,
    kein drift). Wirft nie (defensiv — cache-fehler darf den import nie killen)."""
    try:
        cache = {}
        for replay in replays:
            if not isinstance(replay, dict):
                continue
            uuid = str(replay.get("uuid", "") or "")
            if not uuid:
                continue
            channels = replay.get("channels") or {}
            kept = {ch: channels[ch] for ch in _HUD_NUMERIC_CHANNELS
                    if channels.get(ch)}
            cache[uuid] = {"channels": kept}
        state._last_actors_payload = cache
    except (TypeError, AttributeError, KeyError) as e:
        print("[bbs-bridge] _cache_actors_payload uebersprungen: %s" % e)


def _apply_live_snapshot(f, prefix):
    """One guarded apply path for push and poll, including an empty open film."""
    global _deferred_snapshot, _deferred_retry_after
    _deferred_snapshot = None
    _deferred_retry_after = 0.0
    try:
        if not isinstance(f, dict) or f.get("ok") is not True:
            state._live_status = "%s: kein film offen" % prefix
            return
        if not check_film(f.get("film")):
            state._live_status = _film_switch_status()
            return
        if shot_freeze.ist_eingefroren():
            state._live_status = "%s: shot eingefroren" % prefix
            return
        # Missing/null is NOT an explicit empty film. Validate before deletion or writes.
        replays = f.get("replays")
        _validate_live_replays(replays)
        scene = bpy.context.scene
        tick_scale = scene.render.fps / (scene.render.fps_base or 1.0) / 20.0
        fingerprint = _snapshot_fingerprint(replays, tick_scale)
        if not _live_sync_actors(replays):
            return
        # Commit only after application. Failed or frozen snapshots remain retryable.
        state._live_fingerprint = fingerprint
        suffix = _mount_scene_suffix(replays) + _bbs_only_effects_suffix(replays)
        state._live_status = ("%s: %d actors%s" % (prefix, len(replays), suffix)
                              if replays else "%s: film leer (0 actors)" % prefix)
        pending = sum(_find_actor_by_uuid(replay['uuid']) is None
                      or _needs_model_upgrade(_find_actor_by_uuid(replay['uuid']), replay)
                      for replay in replays)
        if pending:
            _deferred_snapshot = f
            waiting_for_edit = not _can_create_actor()
            _deferred_retry_after = 0.0 if waiting_for_edit else time.monotonic() + 1.0
            state._live_status = '%s: %d actors updated, %d %s' % (
                prefix, len(replays) - pending, pending,
                'awaiting end of edit' if waiting_for_edit else 'model unavailable')
    except Exception as exc:  # A malformed update must not kill Blender's timer.
        state._live_status = "%s-fehler: %s" % (prefix, exc)
        print("[bbs-bridge] %s-sync failed: %s" % (prefix, exc))


def _handle_actors_export_result(f):
    """Antwort auf unseren ACTORS_EXPORT-request. Jeder fehlerpfad loggt + laeuft weiter,
    nie crashen — ein kaputter export darf den tick-loop nicht killen."""
    state._actors_pending = False

    # --- LIVE-modus: kam der export von unserem poll, nicht vom button? ---
    if state._live_polling:
        _apply_live_snapshot(f, "live")
        return
    if not f.get("ok"):
        err = f.get("error") or "unknown error"
        state._actors_status = err
        print("[bbs-bridge] actors export failed:", err)
        return
    # task 1.0: ein MANUELLER import (button) ist eine bewusste user-aktion gegen den film der
    # JETZT offen ist -> er (RE)BINDET auf diesen film. Ein evtl. vorheriger wechsel-verdacht ist
    # damit aufgeloest (der user hat sich neu entschieden). bind_film loescht die pause-flags.
    global _deferred_snapshot
    replays = f.get("replays")
    try:
        _validate_live_replays(replays)
    except (TypeError, ValueError) as exc:
        state._actors_status = "invalid actor snapshot: %s" % exc
        print("[bbs-bridge] " + state._actors_status)
        return
    bind_film(f.get("film"))
    _deferred_snapshot = None
    state._live_fingerprint = None
    state._live_status = "explicit import: %d actors" % len(replays)
    print("[bbs-bridge] actors export ok: %d replays (film='%s', source=%s)"
          % (len(replays), f.get("film"), f.get("source")))
    try:
        _build_actors(replays, f.get("film", "?"), f.get("source", "?"))
    except Exception as e:   # noqa: BLE001 — der tick-loop darf an KEINEM import sterben
        state._actors_status = "import failed"
        print("[bbs-bridge] actors import failed:", e)


def _handle_actors_push(f):
    """Unsolicited snapshot from ActorPushTracker; share the guarded poll path."""
    state._push_count += 1
    state._push_last_seen = time.time()
    _apply_live_snapshot(f, "push")
