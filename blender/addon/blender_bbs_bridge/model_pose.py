"""W9-B2 (task 2.6-rest) + W10-B2o (task 2.6b sneak) — headYaw/bodyYaw/pitch -> head/body-
pose-bones UND sneaking -> geduckte-pose auf ModelForm-actors (alex-BOBJ-armature), rein
Blender-seitig aus dem SCHON reisenden ACTORS-payload. Kein java, kein neuer message-type,
kein request.

=== SNEAK (task 2.6b, W10-B2o) — kurz; details am _SNEAK_POSE-block ===
Wie headYaw ist sneaking ein parity-UPGRADE: BBS rendert am nackten alex (plain Animator,
keine anim-dateien) beim ducken NICHTS (die crouch-ACTION ist null; die sneak-POSE liefe nur
ueber den ProceduralAnimator, den alex nie nutzt). Wir bauen die engine-konvention
(`ProceduralPoseDefaults.createStandardSneakingPose`, vanilla-steve-crouch) Blender-seitig:
statischer per-bone-delta (kopf faellt, arme/torso lehnen vor, beine vor), hart on/off am
sneaking-kanal (`interpolate(tick)!=0`, kein fade — haus-regel). Kopf/arme/beine EXAKT
(live gegen unabhaengige BBS-mathe <1.2e-7); torso-vorlehne = dokumentierte v1-grenze
(alex-BOBJ hat keinen torso-bone, siehe _SNEAK_POSE-block). Raum: _MODEL_TO_ARM (Ry180@J2A,
NICHT _JAVA_TO_ARM — die sneak-zahlen leben im bbs.json-Y-up-ModelPart-raum). Chirurgie/
idempotenz/read-only wie headYaw; head traegt rotation(headYaw)+location(sneak) unabhaengig.

=== WARUM DIESES MODUL (kanal-matrix docs/brain/research/2026-07-17-kanal-matrix.md §2/P) ===
headYaw/bodyYaw reisen im ACTORS-payload, wurden aber nur fuer MobForm konsumiert
(REPLAY_POSE_EXPORT/replay_anim.py — java lehnt ModelForm ab: "task 4.3a only reposes
mob-rig actors"). alex-actors hatten deshalb NIE kopf-/koerperdrehung. Dieses modul
konsumiert die kanaele direkt aus dem payload fuer `formType=="bbs:model"`-armatures.
`bbs:mob` bleibt AUSDRUECKLICH beim replay_anim-pfad (java-gemessen, inkl. walk/
prozedural-pose) — beide wuerden sonst dieselben bone-pfade bekriegen.

=== DIE KONVENTION (GEMESSEN am java-code 2026-07-17, nicht geraten) ===
Alle vier java-verbraucher sind sich einig — replay-kanaele sind ABSOLUTE grad-werte,
die relativierung passiert GENAU EINMAL beim verbraucher (081fd12-doppel-subtraktions-
falle: wer vorher schon subtrahiert, subtrahiert doppelt — live gemessen 110 statt 70 grad):

  (a) GANZES modell dreht mit bodyYaw, NICHT mit yaw:
      `BaseFilmController.getMatrixForRenderWithRotation` (zeile 1396-1399):
      `matrix.rotateY(toRad(-bodyYaw))` — der EINZIGE rotationsanteil der form-matrix.
      KEIN pitch auf dem modell (das game kippt die figur nie).
  (b) head = (headYaw - bodyYaw) + pitch, RELATIV zum koerper:
      `EntityExporter.poseEntity` z.466-473: `headYaw = headYawAbs - bodyYaw`,
      `setAngles(..., headYaw, headPitch)` · `MolangHelper.setMolangVariables` z.52-59:
      `query.head_yaw = yawHead - bodyYaw`, `query.head_pitch = pitch` ·
      `MobFormRenderer.applyMorphRotation` z.755-768: morph kriegt bodyYaw=0 +
      relativen headYaw (die form-matrix traegt bodyYaw schon) ·
      `ProceduralAnimator` z.156-158/277-291: `yaw = headYaw - bodyYaw` auf den head-bone.
  (c) der `yaw`-kanal steuert im game KEINE sichtbare modell-rotation einer form
      (nur blick-/velocity-richtung der entity).

ModelForm-sonderfall (gemessen): alex (`emoticons/alex/config.json`) hat KEIN
`"procedural": true` und KEINE animations-dateien -> im game zeigt der nackte alex
gar keine kopfdrehung (plain `Animator` spielt nur actions; head-look kaeme erst aus
molang-animationen via query.head_yaw). Wir bauen Blender-seitig die ENGINE-konvention
(a)+(b) nach — das ist ein bewusstes upgrade auf das, was jede prozedurale/mob-form
im game tut, nicht die nachbildung des nackten alex.

=== POSE MATH (aufbauend auf replay_anim 957dd2b, dort live bewiesen 2.2e-07 auf alex) ===
Der actor-ROOT traegt BBS-paritaetisch NUR bodyYaw (Fallback fuer alte Payloads: yaw),
nie pitch. Das folgt direkt BaseFilmController.getMatrixForRenderWithRotation. pitch und
headYaw bleiben Pose-Daten. Der body-bone traegt nur ein eventuelles Residuum zwischen
Objekt-Ist und Game-Soll (bei normalem bodyYaw-root ist es Identitaet):

  R_obj(f)   = objekt-euler aus den EIGENEN fcurves (die wahrheit dessen was das objekt
               tut — normalerweise Rz(-bodyYaw))
  A_res(f)   = R_obj^-1 @ Rz(-bodyYaw_rad)          (armature-raum-residuum)
  B(body)    = Lb^-1 @ A_res @ Lb                   (konjugation in den bone-rest-frame,
               Lb = data.bones["body"].matrix_local.to_3x3())
  l(body)    = Lb^-1 @ (A_res @ p - p)              (translations-kompensation, p = body-
               bone-pivot: A_res dreht um den bone-PIVOT, das objekt um den OBJEKT-URSPRUNG
               (nur fuer Legacy-/fremde Objektkurven relevant; im Normalfall exakt 0)
  =>  gesamt-koerper = obj @ body-bone = T(obj_loc) @ Rz(-bodyYaw)  == game (a), exakt.

  head-bone (ModelPart-konvention wie replay_anim, dort fuer DIESE armature bewiesen):
  R_head_m   = Euler((pitch_rad, (headYaw-bodyYaw)_rad, 0), 'XYZ')   (java-modell-achsen)
  B(head)    = Hh^-1 @ R_head_m @ Hh,  Hh = _ARM_TO_JAVA @ Lh
  Das nesting rechnet sich exakt heraus (head unter low_body unter body; ungekeyte
  zwischen-bones kuerzen sich: PM(head) = PM(body) @ Lb^-1 @ Lh @ B(head)) — head erbt
  die body-korrektur und liefert dann NUR den relativ-anteil, wie im game. Liegt "body"
  NICHT in der eltern-kette von "head" (fremde rigs), wird A_res dem head vorgeschaltet.
  pitch kommt direkt aus dem Replay-Kanal. Nur der Kopf kippt; die Figur bleibt wie in BBS
  aufrecht. Das beseitigt den frueheren Root-pitch, der bei Blick nach unten den ganzen Actor
  horizontal legte.

=== INTERPOLATION (kanal-matrix §3.3-haltung: dense-bake) ===
headYaw/bodyYaw werden ueber actors._build_fcurve auf ein WEGWERF-objekt uebersetzt
(volle 40-key-interp-map, dieselbe treue wie die objekt-kanaele — KEINE eigene interp-
nachbau-mathematik) und pro tick ausgewertet; die bones kriegen dense LINEAR-keys pro
tick (replay_anim/film_camera_import-haltung: sample-rate traegt die kurvenform).
Auswertung NACH dem letzten key haelt den letzten wert (Blender CONSTANT-extrapolation
== BBS KeyframeChannel-verhalten; film 'f': bodyYaw endet tick 4, headYaw laeuft bis 7).

=== FALLBACKS (dokumentiert, kein stilles raten) ===
- bodyYaw-kanal leer  -> bodyYaw := objekt-yaw (Legacy-Payload)
- headYaw-kanal leer  -> headYaw := bodyYaw (kein kopfdreh)
- BEIDE leer          -> modul tut NICHTS (kein entfernen, kein keyen — aeltere filme
  ohne head/body-daten sehen exakt aus wie vor diesem modul)
- bone fehlt          -> uebersprungen + log (head-only/body-only rigs gehen teilweise;
  translations-kompensation existiert NUR auf dem body-bone — dokumentierte v1-grenze)

=== IDEMPOTENZ / CHIRURGIE (replay_anim-haltung) ===
Re-apply entfernt vorher NUR die eigenen kanaele (pose.bones["head"].rotation_euler,
pose.bones["body"].rotation_euler+location) — objekt-level-fcurves und fremde bone-keys
bleiben unangetastet. Der live-sync-clear (actors._clear_actor_object_fcurves) entfernt
nur objekt-level pfade -> unsere bone-keys ueberleben den push und werden danach vom
anschluss-punkt neu aufgebaut. head/body-kanaele sind auf bbs:model-actors damit
ADDON-VERWALTET sobald der payload head/body-daten traegt (wie die objekt-kanaele);
haendische keys dort ueberschreibt der naechste sync — der rueckweg ist W9-B1s
ACTORS_IMPORT-sender, nicht stilles behalten.
"""

import bpy
import math

from mathutils import Euler, Matrix, Vector

from .replay_anim import _ARM_TO_JAVA, _JAVA_TO_ARM, _fcurve_collections, _fill_linear_fcurve
from .actors import _build_fcurve, _find_fcurve

_DEG = math.pi / 180.0

# haerte-kappe gegen pathologische payloads (20000 ticks ~ 16min film): drueber wird
# abgeschnitten + geloggt, nie still ein multi-millionen-key-import versucht.
MAX_TICKS = 20000

# BBS-model-units -> Blender-bone-units. Die BOBJ-bones (und damit A_rest =
# data.bones[b].matrix_local) sind in bbs.json-einheiten/16 (am rig gemessen:
# alex left_arm origin (-6,22,0) in bbs.json -> BOBJ head (0,1.375,0) = 22/16).
# Die sneak-pose-translate-werte stehen in bbs.json-model-units -> /16 fuer Blender.
_MODEL_UNIT = 1.0 / 16.0

# bbs.json/ModelPart-model-raum (Y-UP) -> Blender-armature-raum. NICHT _JAVA_TO_ARM!
# _JAVA_TO_ARM stammt aus dem EntityExporter-Y-DOWN-CAPTURE (mob-rigs, replay_anim) und
# traegt einen Rx180-aufricht-flip. Die sneak-pose-zahlen (createStandardSneakingPose) stehen
# dagegen in bbs.json/ModelPart-koords (Y=up: head y=24, fuss y=0) — die reisen ueber
# _bobj_vec in den rig, was einem ZUSAETZLICHEN Ry180 gegenueber _JAVA_TO_ARM entspricht.
# LIVE GEMESSEN (2026-07-18): _MODEL_TO_ARM = Ry180 @ _JAVA_TO_ARM bildet die bbs.json-origins
# ALLER sneak-bones EXAKT auf die rig-bone-heads ab (head/arme/beine match <1e-4), _JAVA_TO_ARM
# allein flippte X+Z (arme-rotation 1.61 daneben). D.h. netto: javaX->bl-X, javaY(up)->bl+Z,
# javaZ->bl-Y. Das ist der raum in dem der crouch-delta (rotation UND translate) korrekt liegt.
_MODEL_TO_ARM = Matrix.Rotation(math.pi, 3, 'Y') @ _JAVA_TO_ARM
_ARM_TO_MODEL = _MODEL_TO_ARM.inverted()

# === SNEAK-POSE (task 2.6b) — engine-GEMESSENE crouch-konvention, kein raten ===
# BBS zeigt am NACKTEN alex (ModelForm, plain Animator, keine anim-dateien) beim sneak
# NICHTS: der Animator waehlt die crouching/crouchingIdle-ACTION, die bei alex null ist
# (createAction -> null wenn animation fehlt, alex.bbs.json "animations":{}), setActiveAction
# (null)=no-op. Die eigentliche sneak-POSE wird NUR vom ProceduralAnimator angewandt
# (`model.applyPose(armature.getSneakingPose())`, ProceduralAnimator.java:219-222) — den ein
# nackter alex NIE nutzt (config.json ohne "procedural"). Das ist EXAKT die head-look-lage:
# der kanal reist, das form-render ignoriert ihn. Wir bauen darum die ENGINE-konvention
# Blender-seitig nach (parity-upgrade wie headYaw/bodyYaw), belegbar aus:
#   `ProceduralPoseDefaults.createStandardSneakingPose()` (vanilla-steve-crouch, model-space),
#   angewandt als ADDITIVER per-bone-local-delta (`ModelGroup.applyPose` z.163-171:
#   current.translate += pose.translate ; current.rotate += deg(pose.rotate)),
#   matrix aus `Transform.setupMatrix` (z.111-134): T(translate)·T(pivot)·Rz·Ry·Rx·S·T(-pivot),
#   rotation Euler-ZYX. Der delta ist eine rotation um den bone-PIVOT (= origin) PLUS eine
#   translation — deshalb keine reine `R_java`-konjugation wie bei headYaw, sondern die volle
#   4x4-parent-frame-transformation D (rotation UND location pro bone gekeyt).
# Schwelle exakt wie java: `sneaking.interpolate(tick) != 0D` (ReplayKeyframes.java:582;
# keyframes sind 0.0/1.0, z.359). Hart on/off — der ProceduralAnimator faded die pose NICHT
# (kein eigener smooth-blend hier: haus-regel, keine erfundene animations-mathematik).
#
# ⚠️ TORSO-LEAN = DOKUMENTIERTE V1-GRENZE (nicht kaschiert). Der EXPORTIERTE alex-rig
# (emoticons/alex/slim.bobj) hat KEINEN `torso`-bone — die chest-geometrie sitzt auf `body`
# (live gemessen: bones head/body/low_body/{left,right}_{arm,leg}, KEIN torso; mesh-weights
# auf `body`). Die vanilla-chest-vorlehne (-0.5rad) hat auf alex also kein eigenes ziel:
#   - `body` waere der GeckoLimbRole.TORSO-treffer (scout-belegt: resolve() matcht "body",
#     ProceduralPoseDefaults.buildRoleBoneMap first-match-wins), ABER `body` traegt in diesem
#     rig SCHON den bodyYaw-residuum (task 2.6-rest, rotation_euler+location) UND ist der
#     hierarchie-PARENT von low_body(->kopf,arme) + beinen -> ein lean auf `body` wuerde
#     (a) mit bodyYaw auf DERSELBEN fcurve kollidieren und (b) auf arme/kopf/beine kaskadieren,
#     die zusaetzlich ihren eigenen per-part-delta kriegen = doppel-kippung, NICHT vanilla-crouch.
#   Darum: torso-eintrag bleibt in der tabelle (rigs MIT eigenem torso-bone kriegen ihn korrekt),
#   aber auf alex ist er ein no-op (bone fehlt -> _sneak_deltas ueberspringt still). Kopf/arme/
#   beine (die existieren) werden EXAKT gepost (live gegen unabhaengige BBS-mathe: <1.2e-7).
#   Der bewusst offene teil ist NUR die chest-vorlehne — dokumentiert, kein stilles fehlen.
#
# Pro eintrag: bone-name -> (rotate_rad_xyz (model-achsen), translate_model_units_xyz).
# bone-namen sind die ModelPart-standard-namen; fremde rigs ohne diese bones ueberspringen
# still den eintrag (head/arm/leg traegt alex 1:1, torso siehe grenze oben).
_SNEAK_POSE = {
    "head":      ((0.0, 0.0, 0.0),          (0.0, -4.2, 0.0)),
    "left_arm":  ((-0.39999452, 0.0, 0.0),  (0.0, -3.2, 0.0)),
    "right_arm": ((-0.39999452, 0.0, 0.0),  (0.0, -3.2, 0.0)),
    "torso":     ((-0.50000197, 0.0, 0.0),  (0.0, -3.2, 0.0)),
    "left_leg":  ((0.0, 0.0, 0.0),          (0.0, -0.2, 4.0)),
    "right_leg": ((0.0, 0.0, 0.0),          (0.0, -0.2, 4.0)),
}
# ordnung der bone-namen fuer stabile logs/pfad-listen
_SNEAK_BONES = ("head", "left_arm", "right_arm", "torso", "left_leg", "right_leg")

# die EINZIGEN pfade, die dieses modul jemals anlegt/entfernt (chirurgie-vertrag).
# head/body = headYaw/bodyYaw (task 2.6-rest); die sneak-bones (task 2.6b) tragen
# rotation_euler (torso/arme) bzw. location (alle) — head.location ist sneak-OWNED,
# head.rotation_euler ist headYaw-OWNED (verschiedene fcurves, kollidieren nie).
_OWNED_PATHS = (
    'pose.bones["head"].rotation_euler',
    'pose.bones["body"].rotation_euler',
    'pose.bones["body"].location',
) + tuple(
    'pose.bones["%s"].%s' % (b, ch)
    for b in _SNEAK_BONES
    for ch in ("rotation_euler", "location")
)

# raw-kanaele auf dem wegwerf-objekt: location[0]=headYaw, location[1]=bodyYaw,
# location[2]=sneaking, rotation_euler[0]=pitch. scale=1.0/const=0.0 -> fcurve-wert == kanal-wert (grad bei
# headYaw/bodyYaw, roh-double 0/1 bei sneaking; auch die bezier-handles ly/ry skalieren
# dann korrekt 1:1, siehe actors._apply_kf_shape). sneaking laeuft durch DIESELBE
# interp-map wie die anderen kanaele -> die schwelle greift auf dem echt-interpolierten
# verlauf (bei linear/constant = hart, wie java `interpolate(tick) != 0`).
_TMP_SPECS = {
    "headYaw":  ("location", 0, 1.0, 0.0),
    "bodyYaw":  ("location", 1, 1.0, 0.0),
    "sneaking": ("location", 2, 1.0, 0.0),
    "pitch":    ("rotation_euler", 0, 1.0, 0.0),
}

# schwelle fuer isSneaking: java testet `sneaking.interpolate(tick) != 0D` (keyframes
# 0.0/1.0). 1e-6 statt exakt 0 nur gegen float32-fcurve-rauschen an den flanken —
# semantisch identisch (der verlauf ist 0 oder 1, dazwischen nur der interp-uebergang).
_SNEAK_ON = 1e-6


def _max_tick(channels, names):
    """Letzter keyframe-tick ueber die genannten kanaele (0.0 wenn keine keys)."""
    mt = 0.0
    for name in names:
        for kf in channels.get(name) or []:
            try:
                t = float(kf.get("tick", 0.0))
            except (TypeError, ValueError, AttributeError):
                continue
            if t > mt:
                mt = t
    return mt


def _remove_owned_fcurves(arm_obj):
    """NUR die _OWNED_PATHS-fcurves entfernen — einzeln mit re-scan statt ueber eine
    kopierte liste (remove invalidiert referenzen, world_anim/replay_anim-lehre).
    BEWUSST nicht replay_anim._remove_owned_fcurves wiederverwendet: das entfernt
    rotation+SCALE beliebiger bones; wir besitzen rotation(head/body)+location(body)."""
    removed = 0
    for coll in _fcurve_collections(arm_obj):
        while True:
            victim = None
            for fc in coll:
                if fc.data_path in _OWNED_PATHS:
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


def _channel_evaluator(replay, tick_scale):
    """(head_fn, body_fn, pitch_fn, sneak_fn, cleanup_fn) — Wert je Blender-Frame, oder None wenn der
    jeweilige kanal leer ist. Uebersetzung ueber actors._build_fcurve auf ein NIE
    verlinktes wegwerf-objekt (volle interp-map-treue, siehe modul-docstring); das
    objekt taucht in keiner szene/collection auf. cleanup_fn IMMER rufen (finally)."""
    channels = replay.get("channels") or {}
    tmp = bpy.data.objects.new("BBSBRIDGE_modelpose_eval", None)
    fns = {}
    for name, spec in _TMP_SPECS.items():
        kfs = channels.get(name) or []
        if not kfs:
            fns[name] = None
            continue
        n, _mf = _build_fcurve(tmp, kfs, spec, tick_scale)
        fc = _find_fcurve(tmp, spec[0], spec[1]) if n else None
        fns[name] = (lambda f, _fc=fc: _fc.evaluate(f)) if fc is not None else None

    def cleanup():
        act = tmp.animation_data.action if tmp.animation_data else None
        try:
            bpy.data.objects.remove(tmp)
        except (ReferenceError, RuntimeError):
            pass
        if act is not None:
            try:
                act.use_fake_user = False
                if act.users == 0:
                    bpy.data.actions.remove(act)
            except (ReferenceError, RuntimeError):
                pass

    return fns["headYaw"], fns["bodyYaw"], fns["pitch"], fns["sneaking"], cleanup


def _sneak_deltas(obj):
    """Fuer jeden vorhandenen sneak-bone der armature: (euler_xyz, location_vector) des
    statischen crouch-deltas im bone-rest-frame. Bones die das rig nicht hat -> ausgelassen.
    Einmal pro import berechnet (statisch, tick-unabhaengig)."""
    data_bones = obj.data.bones
    out = {}
    for name in _SNEAK_BONES:
        db = data_bones.get(name)
        pb = obj.pose.bones.get(name)
        if db is None or pb is None:
            continue
        rot_rad, t_model = _SNEAK_POSE[name]
        a_rest = db.matrix_local            # 4x4 bone-rest im armature-raum (Blender-units)
        origin = a_rest.translation.copy()  # bone-HEAD = BBS-pivot=origin, schon in bl-units
                                            # (LIVE bewiesen == _MODEL_TO_ARM @ bbs.json-origin*MU)

        # BBS `setupMatrix`-delta EXAKT nachgebaut (nicht hand-vereinfacht — die vereinfachung
        # T(t)·T(o)·R·T(-o) war 0.53 daneben auf rotierenden bones): applyPose macht
        # `current.translate += pose.translate` -> setupMatrix baut T(translate)·T(pivot)·R·T(-pivot)
        # mit translate=origin+t, pivot=origin. Also (in armature-raum, o=origin=rig-pivot):
        #   M_posed = T(o+t)·T(o)·R·T(-o)  ;  M_rest = T(o)  (rotate=0)  ;  D = M_posed·M_rest⁻¹
        # Rotation/translate ueber _MODEL_TO_ARM in den armature-raum (NICHT _JAVA_TO_ARM: die
        # sneak-zahlen leben im bbs.json-Y-up-raum, siehe _MODEL_TO_ARM-doc). Gegen die
        # unabhaengige bbs.json+setupMatrix-herleitung live geprueft: ~1e-7 (arme UND kopf).
        r_arm = (_MODEL_TO_ARM
                 @ Euler((rot_rad[0], rot_rad[1], rot_rad[2]), 'XYZ').to_matrix()
                 @ _ARM_TO_MODEL).to_4x4()
        t_arm = (_MODEL_TO_ARM @ Vector((t_model[0], t_model[1], t_model[2]))) * _MODEL_UNIT
        tr = Matrix.Translation
        m_posed = tr(origin + t_arm) @ tr(origin) @ r_arm @ tr(-origin)
        m_rest = tr(origin)
        d_arm = m_posed @ m_rest.inverted()

        basis = a_rest.inverted() @ d_arm @ a_rest
        out[name] = (basis.to_euler('XYZ'), basis.translation.copy())
    return out


def _object_euler_evaluator(obj):
    """Euler-triple des objekts je frame, aus dessen EIGENEN fcurves (kanal fehlt ->
    statischer ist-wert des objekts). Das liest die wahrheit dessen, was das objekt
    tut — inklusive der actors-spec-konvertierung, ohne sie nachzubauen."""
    fcs = [_find_fcurve(obj, "rotation_euler", i) for i in range(3)]
    statics = [float(obj.rotation_euler[i]) for i in range(3)]

    def ev(frame):
        return tuple(
            fcs[i].evaluate(frame) if fcs[i] is not None else statics[i]
            for i in range(3)
        )
    return ev


def owned_bones(obj):
    """Conservative output set of the head/body/sneak curve rebuild."""
    if obj is None or obj.type != 'ARMATURE':
        return set()
    return {name for name in ('head', 'body', *_SNEAK_BONES) if name in obj.pose.bones}


def apply_actor_pose(obj, replay, tick_scale, transform_obj=None):
    """Anschluss-punkt (aus actors._build_actors + _live_sync_actors, je NACH dem bau
    der objekt-fcurves gerufen). Wirft NIE (timer-regel) — jeder fehlerpfad loggt.
    Gibt anzahl gesetzter keys zurueck (0 == nichts zu tun/uebersprungen)."""
    try:
        return _apply_actor_pose(obj, replay, tick_scale, transform_obj)
    except Exception as e:   # noqa: BLE001 — ein pose-bau darf den import/tick nie killen
        print("[bbs-bridge] model pose failed (%s): %s" % (getattr(obj, "name", "?"), e))
        return 0


def apply_live_actor_pose(obj, values):
    """Apply one disposable recording-preview pose without authoring F-curves.

    The durable post-recording ACTORS_EXPORT_RESULT rebuilds the real curves. This function only
    makes the currently recorded player visibly follow head/body/sneak state in Blender now.
    """
    if obj is None or obj.type != 'ARMATURE' or str(obj.get("bbs_form_type") or "") != "bbs:model":
        return False

    def number(name, fallback):
        try:
            return float(values.get(name, fallback))
        except (TypeError, ValueError, AttributeError):
            return fallback

    body_r = number("bodyYaw", number("yaw", 0.0)) * _DEG
    head_r = number("headYaw", body_r / _DEG) * _DEG
    pitch_r = number("pitch", 0.0) * _DEG
    r_obj = obj.rotation_euler.to_matrix()
    a_res = r_obj.inverted() @ Matrix.Rotation(-body_r, 3, 'Z')

    pb_body = obj.pose.bones.get("body")
    pb_head = obj.pose.bones.get("head")
    if pb_body is not None:
        lb4 = obj.data.bones["body"].matrix_local
        lb = lb4.to_3x3()
        lb_inv = lb.inverted()
        pivot = lb4.translation.copy()
        pb_body.rotation_mode = 'XYZ'
        pb_body.rotation_euler = (lb_inv @ a_res @ lb).to_euler('XYZ')
        pb_body.location = lb_inv @ (a_res @ pivot - pivot)

    if pb_head is not None:
        hh = _ARM_TO_JAVA @ obj.data.bones["head"].matrix_local.to_3x3()
        r_head_m = Euler((pitch_r, head_r - body_r, 0.0), 'XYZ').to_matrix()
        pb_head.rotation_mode = 'XYZ'
        pb_head.rotation_euler = (hh.inverted() @ r_head_m @ hh).to_euler('XYZ')

    sneak_on = abs(number("sneaking", 0.0)) > _SNEAK_ON
    for name, (rotation, location) in _sneak_deltas(obj).items():
        pb = obj.pose.bones.get(name)
        if pb is None:
            continue
        if name not in {"head", "body"}:
            pb.rotation_mode = 'XYZ'
            pb.rotation_euler = rotation if sneak_on else (0.0, 0.0, 0.0)
        pb.location = location if sneak_on else (0.0, 0.0, 0.0)
    return True


def _apply_actor_pose(obj, replay, tick_scale, transform_obj=None):
    if obj is None or obj.type != 'ARMATURE':
        return 0
    if str(replay.get("formType") or "") != "bbs:model":
        # bbs:mob = replay_anim-territorium (java-gemessene pose inkl. walk) — beide
        # wuerden sonst dieselben head/body-bone-pfade bekriegen. Siehe modul-docstring.
        return 0
    channels = replay.get("channels") or {}
    has_head = bool(channels.get("headYaw"))
    has_body = bool(channels.get("bodyYaw"))
    has_sneak = bool(channels.get("sneaking"))
    if not has_head and not has_body and not has_sneak:
        return 0    # alte filme ohne head/body/sneak-daten: exakt wie vor diesem modul

    # head/body-pose (headYaw/bodyYaw) NUR wenn einer der beiden kanaele da ist — sonst
    # wuerde ein sneak-NUR-film ueber den objekt-pitch/yaw-fallback spurious head/body-keys
    # setzen (head_r-body_r=0, aber pitch_r ginge auf die head-rotation). Gated -> ein film
    # OHNE head/body-kanal laesst head/body byte-unangetastet wie vor dem modul.
    do_headbody = has_head or has_body
    pb_head = obj.pose.bones.get("head") if do_headbody else None
    pb_body = obj.pose.bones.get("body") if do_headbody else None
    # sneak-deltas (statisch, pro rig): NUR bones die das rig hat; leer wenn keiner da.
    sneak = _sneak_deltas(obj) if has_sneak else {}
    if pb_head is None and pb_body is None and not sneak:
        print("[bbs-bridge] model pose: '%s' hat weder head/body- noch sneak-bones -> skip"
              % obj.name)
        return 0

    n_ticks = int(math.ceil(_max_tick(
        channels, ("headYaw", "bodyYaw", "sneaking", "yaw", "pitch"))))
    if n_ticks > MAX_TICKS:
        print("[bbs-bridge] model pose: %d ticks > kappe %d -> abgeschnitten"
              % (n_ticks, MAX_TICKS))
        n_ticks = MAX_TICKS

    head_fn, body_fn, pitch_fn, sneak_fn, cleanup = _channel_evaluator(replay, tick_scale)
    try:
        obj_euler = _object_euler_evaluator(transform_obj or obj)

        # rest-frames (einmal, nicht pro tick)
        body_in_head_chain = False
        if pb_body is not None:
            lb4 = obj.data.bones["body"].matrix_local
            lb = lb4.to_3x3()
            lb_inv = lb.inverted()
            p = lb4.translation.copy()
        if pb_head is not None:
            hh = _ARM_TO_JAVA @ obj.data.bones["head"].matrix_local.to_3x3()
            hh_inv = hh.inverted()
            parent = pb_head.parent
            while parent is not None:
                if parent.name == "body":
                    body_in_head_chain = pb_body is not None
                    break
                parent = parent.parent

        body_rot_pts = [[], [], []]
        body_loc_pts = [[], [], []]
        head_rot_pts = [[], [], []]
        # sneak: pro bone rot/loc-punkt-listen (nur fuer bones in `sneak`)
        sneak_rot_pts = {b: [[], [], []] for b in sneak}
        sneak_loc_pts = {b: [[], [], []] for b in sneak}
        prev_body = None
        prev_head = None
        for t in range(n_ticks + 1):
            f = float(t) * tick_scale
            ex, ey, ez = obj_euler(f)
            r_obj = Euler((ex, ey, ez), 'XYZ').to_matrix()
            # fallback-kette siehe modul-docstring: kanal leer -> objekt-yaw bzw. bodyYaw
            body_r = (body_fn(f) * _DEG) if body_fn is not None else -ez
            head_r = (head_fn(f) * _DEG) if head_fn is not None else body_r
            pitch_r = ((pitch_fn(f) * _DEG) if pitch_fn is not None else -ex)
            a_res = r_obj.inverted() @ Matrix.Rotation(-body_r, 3, 'Z')

            if pb_body is not None:
                b_body = lb_inv @ a_res @ lb
                eul = (b_body.to_euler('XYZ', prev_body) if prev_body is not None
                       else b_body.to_euler('XYZ'))
                prev_body = eul
                loc = lb_inv @ (a_res @ p - p)
                for i in range(3):
                    body_rot_pts[i].append((f, eul[i]))
                    body_loc_pts[i].append((f, loc[i]))

            if pb_head is not None:
                r_head_m = Euler((pitch_r, head_r - body_r, 0.0), 'XYZ').to_matrix()
                if not body_in_head_chain:
                    # kein body-bone in der eltern-kette -> head muss das residuum
                    # selbst tragen (in java-achsen vorgeschaltet); ohne translations-
                    # kompensation (v1-grenze, docstring "FALLBACKS")
                    r_head_m = (_ARM_TO_JAVA.inverted() @ a_res @ _ARM_TO_JAVA) @ r_head_m
                b_head = hh_inv @ r_head_m @ hh
                eul = (b_head.to_euler('XYZ', prev_head) if prev_head is not None
                       else b_head.to_euler('XYZ'))
                prev_head = eul
                for i in range(3):
                    head_rot_pts[i].append((f, eul[i]))

            # -- SNEAK: hart on/off (java `interpolate(tick) != 0`), delta ist statisch --
            # ist der sneak-kanal an diesem tick aktiv? sneak_fn None kann NICHT sein wenn
            # `sneak` befuellt ist (has_sneak gate), aber defensiv trotzdem gepr.
            sneak_on = (sneak_fn is not None and abs(sneak_fn(f)) > _SNEAK_ON)
            for b, (d_eul, d_loc) in sneak.items():
                rp = sneak_rot_pts[b]
                lp = sneak_loc_pts[b]
                for i in range(3):
                    rp[i].append((f, d_eul[i] if sneak_on else 0.0))
                    lp[i].append((f, d_loc[i] if sneak_on else 0.0))
    finally:
        cleanup()

    # -- re-apply ersetzt chirurgisch (immer, auch wenn unten nichts gekeyt wird:
    #    alte eigene keys zu einem inzwischen anderen payload waeren stale) --
    _remove_owned_fcurves(obj)

    def _nontrivial(pts_xyz):
        return any(abs(v) > 1e-9 for pts in pts_xyz for _f, v in pts)

    n_keys = 0
    if pb_body is not None and (_nontrivial(body_rot_pts) or _nontrivial(body_loc_pts)):
        if pb_body.rotation_mode != 'XYZ':
            pb_body.rotation_mode = 'XYZ'   # MUSS vor rotation_euler-keys (default QUATERNION)
        for i in range(3):
            n_keys += _fill_linear_fcurve(obj, pb_body, 'rotation_euler', i, body_rot_pts[i])
        if _nontrivial(body_loc_pts):
            for i in range(3):
                n_keys += _fill_linear_fcurve(obj, pb_body, 'location', i, body_loc_pts[i])
    if pb_head is not None and _nontrivial(head_rot_pts):
        if pb_head.rotation_mode != 'XYZ':
            pb_head.rotation_mode = 'XYZ'
        for i in range(3):
            n_keys += _fill_linear_fcurve(obj, pb_head, 'rotation_euler', i, head_rot_pts[i])

    # -- SNEAK-keys: pro bone rotation_euler (nur bei nicht-trivialem delta, d.h. torso/arme)
    #    und location (alle sneak-bones, auch head — head.location kollidiert NICHT mit dem
    #    head.rotation_euler oben, eigene fcurve). head-sneak-rotation ist immer 0 -> das
    #    _nontrivial-gate laesst dessen rotation_euler weg (kein redundanter identity-key). --
    n_sneak = 0
    for b in _SNEAK_BONES:
        if b not in sneak:
            continue
        pb = obj.pose.bones.get(b)
        if pb is None:
            continue
        rp = sneak_rot_pts[b]
        lp = sneak_loc_pts[b]
        if _nontrivial(rp):
            if pb.rotation_mode != 'XYZ':
                pb.rotation_mode = 'XYZ'
            for i in range(3):
                n_sneak += _fill_linear_fcurve(obj, pb, 'rotation_euler', i, rp[i])
        if _nontrivial(lp):
            for i in range(3):
                n_sneak += _fill_linear_fcurve(obj, pb, 'location', i, lp[i])
    n_keys += n_sneak

    if n_keys:
        print("[bbs-bridge] model pose: %d ticks -> %d keys auf head/body/sneak von '%s' "
              "(headYaw=%s bodyYaw=%s sneak=%s bones=%d)"
              % (n_ticks + 1, n_keys, obj.name,
                 "kanal" if has_head else "fallback", "kanal" if has_body else "fallback",
                 "kanal(%d keys)" % n_sneak if has_sneak else "aus", len(sneak)))
    return n_keys
