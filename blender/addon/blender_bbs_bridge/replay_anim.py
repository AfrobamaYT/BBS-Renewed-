"""TASK 4.3a-rest — replay-pose -> Blender pose-bone-keyframes auf einer BOBJ-armature.

BBS' java-seitiger `com.blenderbridge.entity.ReplayPoseReader` liest die keyframe-kanaele
EINES replays (headYaw/bodyYaw/pitch via `KeyframeChannel.interpolate()` — kein tick-sampling-
job, die ganze performance existiert schon als kanaele) und posiert damit EINMAL PRO
angefragtem tick ein wegwerf-mob-rig (nie gespawnt/geticked), liest danach dessen
`ModelPart`-transforms zurueck (`EntityExporter.capturePose`). Der reply
(`REPLAY_POSE_EXPORT_RESULT`) ist ABSICHTLICH byte-identisch zu `ENTITY_BAKE_RESULT`s schema:

    {ok, fps, frames:[{tick, "<replayUuid>": {bonePath: [9 floats], ...}}, ...], error}

9 floats == [pivotX,pivotY,pivotZ, pitch,yaw,roll, xScale,yScale,zScale] EINES `ModelPart`s
(java-modell-lokal, winkel in RADIANS — `EntityProtocol`-klassendoc: "Angles are RADIANS
(raw ModelPart.pitch/yaw/roll)"; die GRAD-konvention der replay-KANAELE ist eine reine
java-interna, `poseEntity` konsumiert sie dort, hier kommen fertige part-radians an).
`bonePath` ist der `forEachCuboid`-pfad ("" == wurzel, von java schon als literal "root"
verschickt, sonst "/parent/child").

NUR MobForm-replays: `ReplayPoseReader.read()` lehnt `ModelForm` ab ("task 4.3a only reposes
mob-rig actors") — alex/steve (ModelForm, actors.py-kanal-pfad) sind NICHT das ziel.
WICHTIG DESHALB: fuer ein mob-replay legt actors.py heute nur ein platzhalter-EMPTY an
(kein .bobj fuer `bbs:mob`-forms, task 4.1s entity-import-pfad existiert noch nicht) —
dieses modul posiert deshalb JEDE armature, die das `bbs_uuid`-custom-prop des replays
traegt. Bis 4.1 landet heisst das: armature selbst bereitstellen/stempeln (gate: die
BOBJ-alex-armature mit gestempelter uuid); danach uebernimmt der 4.1-import das stempeln.

=== POSE MATH (GEMESSEN 2026-07-17, nicht geraten — jede konstante hat einen messbeleg) ===
Der vorgaenger-WIP nahm EINE globale euler-formel fuer alle bones an ((pitch,-roll,yaw) in
'XZY') — das stimmt NUR fuer bones, deren rest-frame zufaellig achsen-ausgerichtet ist
(head), und ist fuer body/arms/legs falsch (live an der alex-armature gemessen: head hat
Z->-Y, body hat X=-1/Z->+Y, arme/beine zeigen nach unten — die rest-frames sind PRO BONE
verschieden, eine globale formel KANN nicht stimmen). Korrekt ist die per-bone-konjugation:

  (1) java-rotation: `ModelPart.rotate` multipliziert quaternion rotationZYX(roll,yaw,pitch)
      == Rz(roll)@Ry(yaw)@Rx(pitch) — auf vektoren wirkt pitch INNERST. Das IST exakt
      mathutils `Euler((pitch,yaw,roll),'XYZ').to_matrix()`. Keine vorzeichen-anpassung.
  (2) java-modell-raum ist Y-DOWN: `EntityExporter` captured mit IDENTITY-MatrixStack (kein
      renderer-flip), GEMESSEN am existierenden villager-export
      run/blenderbridge_export/entity_minecraft_villager/entity.obj: head-vertices
      y=-0.625..0, fuesse y=+1.5. Vanillas aufricht-transform (`LivingEntityRenderer`:
      rotate180 ∘ scale(-1,-1,1)) ist als linearteil diag(1,-1,-1) == Rx(180°), det=+1.
  (3) aufrechter Y-up-raum -> Blender-armature-raum: `bobj_import._bobj_vec` (x,y,z)->(x,-z,y)
      (rig-raum-fix 949f9de, figur steht aufrecht — gemessene historie).
      Gesamt: _JAVA_TO_ARM = M_bobj @ Rx180 (javaX->blX, javaY(unten)->bl-Z, javaZ->bl+Y).
  (4) pose-bone-rotationen leben im BONE-REST-FRAME (matrix_basis). Mit A(b) =
      `data.bones[b].matrix_local.to_3x3()` (rest-frame im armature-raum) und
      H(b) = _JAVA_TO_ARM^-1 @ A(b) (rest-frame in java-modell-achsen ausgedrueckt) gilt:
          P(b) = H(b)^-1 @ R_java @ H(b)
      ANNAHME dahinter (dokumentiert, fuer die gate-bones exakt erfuellt): die java-BIND-
      rotation des parts ist 0 (villager head/body/legs: ja). Parts mit bind-rotation != 0
      (villager "arms", pitch -0.75 rad) haben auf der alex-armature keinen namensvetter und
      werden uebersprungen; wenn task 4.1 echte mob-armatures baut, muss deren bind-rotation
      hier gegengerechnet werden (dann: P = H^-1 @ R_bind^-1 @ R_java @ H) — HEUTE nicht
      erfunden, weil es kein solches rig zum messen gibt.
  euler-schreibweise: P.to_euler('XYZ', prev) mit dem VORHERIGEN frame-euler als compat —
  verhindert 360-grad-spruenge zwischen dichten frames (mathutils waehlt sonst frei).

pivot(x,y,z) wird bewusst NICHT gelesen: `ModelPart.pivot` ist eine statische tree-
eigenschaft (aendert sich zwischen bind- und posierter pose nie, nur pitch/yaw/roll/scale
via `setAngles`), die armature hat ihre bind-geometrie schon aus den arm_bone-zeilen.
scale: nur gekeyframet wenn irgendein wert von 1.0 abweicht (sonst muell-kanaele);
direkt (sx,sy,sz)->bone-scale — exakt fuer UNIFORME scales (baby-mobs), bei nicht-
uniformen scales waere die achs-permutation pro bone noetig -> geloggt, nicht geraten.

=== BONE-NAMEN ===
`bonePath`-LETZTES segment == `ModelPart`-feldname (villager: head/body/left_leg/right_leg/
nose/hat/...). Matching gegen `pose.bones` per name; fehlende bones werden uebersprungen
und EINMAL geloggt (auf der alex-armature matchen head/body/left_leg/right_leg). "root"
existiert als bone normalerweise nicht -> gleicher skip-pfad, kein sonderfall.

=== READ-ONLY (gate "film 'f' danach unveraendert") ===
Dieses modul sendet ausschliesslich REPLAY_POSE_EXPORT-requests (java-seite ist ein reiner
leser, klassendoc "read-only, main-thread-only") und schreibt NUR pose-bone-keyframes auf
das ziel-armature-objekt. Kein schreibpfad zurueck nach BBS existiert.

=== IDEMPOTENZ (gate "2x import, keine dubletten") ===
Re-import ERSETZT chirurgisch: nur die fcurves der bones, die im NEUEN result vorkommen
(`pose.bones["<name>"].rotation_euler/scale`), werden vorher entfernt.
⚠️ NIE `animation_data_clear()`/action-remove auf dem armature-objekt: das objekt kann
zugleich actor-ROOT sein (actors.py legt objekt-level location/rotation-fcurves aus den
x/y/z/yaw-kanaelen auf dieselbe action) — der vorgaenger-WIP haette die kommentarlos
mitgeloescht (gefundener WIP-bug, nicht uebernommen).
"""

import bpy
import math
import time

from mathutils import Euler, Matrix

from . import state
from .core import _send, _next_seq
from .actors import _find_fcurve

# ---- eigenes, modul-lokales pending-flag (KEIN state.py-edit noetig) ----
# Solicited-entscheid (dossier-merkregel: JEDER result-handler braucht ihn):
# REPLAY_POSE_EXPORT_RESULT ist IMMER antwort auf GENAU EINEN eigenen request, java pusht
# nie unsolicited — trotzdem gate via _pending (tick.py prueft es VOR dem dispatch), weil
# die java-bridge results an ALLE clients broadcastet (world_import-lehre bb20355): ohne
# flag wuerde ein fremder agent-request unsere szene mit SEINEM replay ueberschreiben.
_pending = False
_deadline = 0.0
_status = "idle"
REPLAY_POSE_EXPORT_TIMEOUT = 30.0

# ---- raum-konstanten (herleitung + messbelege im modul-docstring "POSE MATH") ----
# _bobj_vec als matrix: (x,y,z) -> (x,-z,y)  (bobj_import, rig-raum-fix 949f9de)
_M_BOBJ_TO_BL = Matrix(((1.0, 0.0, 0.0), (0.0, 0.0, -1.0), (0.0, 1.0, 0.0)))
# vanilla-aufricht-flip: modell-raum (Y-down, entity.obj-messung) -> Y-up == Rx(180 grad)
_RX180 = Matrix(((1.0, 0.0, 0.0), (0.0, -1.0, 0.0), (0.0, 0.0, -1.0)))
# java-modell-achsen -> Blender-armature-achsen (javaX->blX, javaY->bl-Z, javaZ->bl+Y)
_JAVA_TO_ARM = _M_BOBJ_TO_BL @ _RX180
_ARM_TO_JAVA = _JAVA_TO_ARM.inverted()


def request_replay_pose(replay_uuid, ticks=20, start_tick=0):
    """REPLAY_POSE_EXPORT anfragen (solicited — siehe modul-header). Aufrufbar aus EXEC/
    konsole fuer gates; kein UI-operator hier (ui.py ist fremdes territorium, welle-6-brief)."""
    global _pending, _deadline, _status
    if not state._connected or state._sock is None:
        _status = "not connected"
        return False
    _pending = True
    _deadline = time.time() + REPLAY_POSE_EXPORT_TIMEOUT
    _status = "waiting for replay pose export (uuid=%s)" % replay_uuid
    _send({"type": "REPLAY_POSE_EXPORT", "origin": "blender", "uuid": "replay-pose",
           "seq": _next_seq(),
           "fields": {"replayUuid": replay_uuid, "ticks": ticks, "startTick": start_tick}})
    print("[bbs-bridge] REPLAY_POSE_EXPORT requested: uuid=%s ticks=%d startTick=%d"
          % (replay_uuid, ticks, start_tick))
    return True


def _check_timeout():
    """Von tick.py gerufen (wie die world/actors-deadlines dort) — ein verlorener export
    darf das pending-flag nicht fuer immer blockieren."""
    global _pending, _status
    if _pending and time.time() > _deadline:
        _pending = False
        _status = "export timed out"
        print("[bbs-bridge] REPLAY_POSE_EXPORT timed out (no result after %ds)"
              % REPLAY_POSE_EXPORT_TIMEOUT)


def _bone_name_for_path(bone_path):
    """`bonePath` -> letztes pfad-segment == ModelPart-feldname == pose-bone-kandidat.
    Wurzel kommt von java schon als "root" (ReplayPoseReader.frame)."""
    if bone_path in ("", "root"):
        return "root"
    return bone_path.rsplit("/", 1)[-1]


def _find_armature_for_replay(replay_uuid):
    """Die ziel-armature einer replay-uuid finden — gleiche uuid-konvention wie
    actors._find_actor_by_uuid (bbs_uuid custom-prop), aber szenenweit und auf
    TYPE=='ARMATURE' gefiltert: das mob-replay-root aus actors.py ist heute ein EMPTY
    (kein entity-import-pfad, siehe modul-header) — die armature traegt die uuid
    gestempelt, bis task 4.1 sie selbst baut."""
    for obj in bpy.data.objects:
        if obj.type == 'ARMATURE' and obj.get("bbs_uuid") == replay_uuid:
            return obj
    return None


def _fcurve_collections(obj):
    """Alle fcurve-container der objekt-action (5.2 slotted actions: channelbag zuerst,
    legacy-wrapper als fallback — gleiche zwei wege wie actors._find_fcurve)."""
    ad = obj.animation_data
    if ad is None or ad.action is None:
        return []
    act = ad.action
    out = []
    slot = getattr(ad, "action_slot", None)
    if slot is not None:
        try:
            for layer in act.layers:
                for strip in layer.strips:
                    bag = strip.channelbag(slot)
                    if bag is not None:
                        out.append(bag.fcurves)
        except (AttributeError, TypeError, RuntimeError):
            pass
    if not out:
        legacy = getattr(act, "fcurves", None)
        if legacy is not None:
            out.append(legacy)
    return out


def _remove_owned_fcurves(arm_obj, bone_names):
    """Chirurgische idempotenz: NUR unsere kanaele (rotation_euler+scale der genannten
    bones) entfernen — objekt-level actor-fcurves (actors.py) bleiben unangetastet
    (siehe modul-header "IDEMPOTENZ"). Entfernen einzeln mit re-scan statt ueber eine
    kopierte liste zu iterieren (remove invalidiert referenzen — world_anim-lehre,
    dort bei keyframe_points, hier dieselbe vorsicht bei fcurves)."""
    owned = set()
    for n in bone_names:
        owned.add('pose.bones["%s"].rotation_euler' % n)
        owned.add('pose.bones["%s"].scale' % n)
    removed = 0
    for coll in _fcurve_collections(arm_obj):
        while True:
            victim = None
            for fc in coll:
                if fc.data_path in owned:
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


def _fill_linear_fcurve(arm_obj, pose_bone, channel, index, pts):
    """EINEN kanal dicht mit LINEAR-keys fuellen (dense-bake: die pro-tick-sample-rate
    traegt die kurvenform, film_camera_import-haltung). Anlage der kurve ueber
    keyframe_insert am OBJEKT (nur der weg legt action+slot 5.2-korrekt an,
    actors._build_fcurve-lehre), dann direktes keyframe_points-fuellen."""
    if not pts:
        return 0
    data_path = 'pose.bones["%s"].%s' % (pose_bone.name, channel)
    first_frame, first_value = pts[0]
    try:
        getattr(pose_bone, channel)[index] = first_value
        if not arm_obj.keyframe_insert(data_path=data_path, index=index, frame=first_frame):
            return 0
    except (RuntimeError, TypeError, AttributeError) as e:
        print("[bbs-bridge] replay pose: keyframe_insert failed for %s[%d]: %s"
              % (data_path, index, e))
        return 0
    fc = _find_fcurve(arm_obj, data_path, index)
    if fc is None:
        print("[bbs-bridge] replay pose: no fcurve %s[%d] after insert" % (data_path, index))
        return 0
    n = 0
    for frame, value in pts:
        # referenz NICHT ueber den naechsten insert halten ('FAST' kann reallozieren).
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        kp.interpolation = 'LINEAR'
        n += 1
    fc.update()   # sortiert die 'FAST'-inserts
    return n


def apply_replay_pose(fields):
    """`REPLAY_POSE_EXPORT_RESULT.fields` -> pose-bone-keyframes auf der replay-armature.

    Genau EIN replay pro request -> jeder frame-dict traegt genau EINEN uuid-key neben
    "tick"; wir lesen ihn generisch (keys minus "tick") statt den request-uuid
    nachzubauen (ein echo-tippfehler waere sonst ein stiller miss).
    Jeder fehlerpfad loggt + kehrt zurueck, nie werfen — ein kaputter import darf den
    tick-loop nicht killen (world_import/actors-haltung)."""
    global _status
    frames = fields.get("frames") or []
    if not fields.get("ok", False) or not frames:
        _status = fields.get("error") or "no frames in result"
        print("[bbs-bridge] replay pose export failed/empty:", _status)
        return

    scene = bpy.context.scene
    scene_fps = scene.render.fps / (scene.render.fps_base or 1.0)
    result_fps = float(fields.get("fps") or 20.0)
    tick_scale = scene_fps / result_fps

    first = frames[0]
    uuid_keys = [k for k in first.keys() if k != "tick"]
    if not uuid_keys:
        _status = "result frame has no replay-uuid key"
        print("[bbs-bridge] replay pose import: %s" % _status)
        return
    replay_uuid = uuid_keys[0]

    arm_obj = _find_armature_for_replay(replay_uuid)
    if arm_obj is None:
        _status = "no armature with bbs_uuid %s (see module doc: stamp/import one first)" % replay_uuid
        print("[bbs-bridge] replay pose import: %s" % _status)
        return

    # -- 1. sammeln: bone -> zeitlich sortierte (frame, rotation-rad, scale) samples --
    per_bone = {}
    missing = set()
    max_frame = 0.0
    for frame_entry in frames:
        tick = frame_entry.get("tick", 0)
        bones = frame_entry.get(replay_uuid)
        if not isinstance(bones, dict):
            continue
        bl_frame = float(tick) * tick_scale
        if bl_frame > max_frame:
            max_frame = bl_frame
        for bone_path, nine in bones.items():
            if not isinstance(nine, (list, tuple)) or len(nine) != 9:
                continue
            name = _bone_name_for_path(bone_path)
            if arm_obj.pose.bones.get(name) is None:
                missing.add(name)
                continue
            v = [float(x) for x in nine]
            per_bone.setdefault(name, []).append(
                (bl_frame, (v[3], v[4], v[5]), (v[6], v[7], v[8])))

    if not per_bone:
        _status = "no result bone matches armature '%s'" % arm_obj.name
        print("[bbs-bridge] replay pose import: %s (result bones: %s)"
              % (_status, ", ".join(sorted(missing))))
        return

    # -- 2. re-import ersetzt (chirurgisch, siehe docstring) --
    _remove_owned_fcurves(arm_obj, per_bone.keys())

    # -- 3. pro bone: per-bone-konjugation + dense LINEAR-fcurves --
    n_keys = 0
    nonuniform = set()
    for name, samples in sorted(per_bone.items()):
        pb = arm_obj.pose.bones[name]
        samples.sort(key=lambda s: s[0])
        a_rest = arm_obj.data.bones[name].matrix_local.to_3x3()
        h = _ARM_TO_JAVA @ a_rest          # bone-rest-frame in java-modell-achsen
        h_inv = h.inverted()
        eulers = []
        prev = None
        for bl_frame, (pitch, yaw, roll), _sc in samples:
            p = h_inv @ Euler((pitch, yaw, roll), 'XYZ').to_matrix() @ h
            eul = p.to_euler('XYZ', prev) if prev is not None else p.to_euler('XYZ')
            eulers.append((bl_frame, eul))
            prev = eul
        if pb.rotation_mode != 'XYZ':
            pb.rotation_mode = 'XYZ'       # MUSS vor rotation_euler-keyframes stehen
        for axis in range(3):
            n_keys += _fill_linear_fcurve(
                arm_obj, pb, 'rotation_euler', axis,
                [(f, e[axis]) for f, e in eulers])
        if any(abs(s - 1.0) > 1e-6 for _f, _r, sc in samples for s in sc):
            if any(max(sc) - min(sc) > 1e-6 for _f, _r, sc in samples):
                nonuniform.add(name)       # siehe docstring: nur uniform exakt
            for axis in range(3):
                n_keys += _fill_linear_fcurve(
                    arm_obj, pb, 'scale', axis,
                    [(f, sc[axis]) for f, _r, sc in samples])

    end = int(math.ceil(max_frame))
    if end > scene.frame_end:
        scene.frame_end = end

    _status = "%d frames -> %d keys on %d bones of '%s'" % (
        len(frames), n_keys, len(per_bone), arm_obj.name)
    print("[bbs-bridge] replay pose import done: replayUuid=%s %d frames -> %d keyframes "
          "on %d bones of '%s' (%.3f frames/tick, fps=%s)"
          % (replay_uuid, len(frames), n_keys, len(per_bone), arm_obj.name,
             tick_scale, fields.get("fps")))
    if missing:
        print("[bbs-bridge] replay pose: result bones without armature match (skipped): %s"
              % ", ".join(sorted(missing)))
    if nonuniform:
        print("[bbs-bridge] replay pose: NON-uniform scale on %s — direct xyz-mapping "
              "is only exact for uniform scale (see module doc)" % ", ".join(sorted(nonuniform)))


def _handle_replay_pose_export_result(fields):
    """Antwort auf unseren request (solicited — tick.py ruft das NUR wenn `_pending`
    True war, gleiches dispatch-gate wie world_import._world_pending)."""
    global _pending, _status
    _pending = False
    try:
        apply_replay_pose(fields)
    except Exception as e:   # noqa: BLE001 — der tick-loop darf an KEINEM import sterben
        _status = "import failed: %s" % e
        print("[bbs-bridge] replay pose import failed:", e)
