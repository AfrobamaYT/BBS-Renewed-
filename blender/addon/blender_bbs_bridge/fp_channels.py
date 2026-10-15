"""TASK 5.1b-VORARBEIT — hand-KANAL-transport: bob / sprint-FOV / show-ease aus den
BBS-replay-kanaelen in das fp_hand-rig baken. OFFLINE (V1): kein eigener BBS-request,
kein tick.py-zweig — die kanaele kommen aus einem bereits vorliegenden ACTORS-payload
(oder synthetisch), die formeln werden Blender-seitig NACHGERECHNET und als F-curves
gebacken. Referenz: docs/superpowers/specs/2026-07-16-trackerclientclip-semantik-referenz.md
+ die java-quelle selbst (TrackerClientClip.java — "bei zweifel dort nachlesen").

## GEMESSENE QUELLEN-LAGE (2026-07-17, film 'f' am laufenden game :9880):
  - FILM_CAMERA_EXPORT traegt KEINE tracker-kanaele: frame-keys sind exakt
    {tick,x,y,z,pitch,yaw,roll,fov}, fov konstant (kein sprint-boost). Strukturell:
    `TrackerClientClip.applyClip` returned bei leerem `CameraClipContext.entities`
    (zeile 1040) BEVOR position/fov geschrieben werden — der offline-sampler
    (FilmCameraExporter) befuellt entities nie. Ein tracker-kanal-export waere
    java-arbeit (applyClip-hook, siehe referenz §5) — NICHT hier, gemeldet an main.
  - ACTORS_EXPORT liefert ALLE gebrauchten kanaele numerisch am replay:
    vX/vZ/sprinting/show_hand/show_hud/sneaking/x/z (39 kanaele gemessen). DAS ist
    die V1-quelle: deterministisch nachrechnen, kein neuer transport noetig
    (referenz "Konsequenz fuer v2 task 5.x", punkt 2).

## ABWEICHUNGEN VON DER REFERENZ-DOC (an der java-QUELLE gemessen, nicht geraten):
  (a) referenz §2 sagt "tickSpeed = sqrt(vX²+vZ²)/20" — FALSCH: `updateBob` bekommt
      pro-tick-POSITIONS-DELTAS (CameraWorkCameraController zeile 65-67: prevX/curX
      des entities, das im playback von den x/z-KANAELEN getrieben wird), und
      `entity.getVelocity()` (quelle der vX-kanaele, ReplayKeyframes zeile 352) ist
      in MC bloecke/TICK, nicht bloecke/sekunde. Kein /20. Primaerquelle hier:
      positions-deltas der x/z-kanaele (exakt das was das game sieht), vX/vZ nur
      fallback wenn x/z fehlen.
  (b) referenz §3 sagt sprint-ramp 0.2 — das ist der UN-gemixte BBS-upstream.
      `TrackerSprintRampMixin` (bbstools, aktiv im laufenden game) CANCELT
      updateSprintFov am HEAD und faehrt vanilla-ramp 0.5 (gleiches target 1.15,
      gleiche feld-writes). Default hier: 0.5 (= gemessenes in-game-verhalten),
      0.2 als konstante fuer den mixin-losen fall dokumentiert.

## FRAME-INTERPOLATIONS-KONVENTION (ein-tick-shift, aus der quelle abgeleitet):
Java rechnet pro FRAME `wert = lerp(last<X>, <X>, tickDelta)` wobei last<X> im
tick-update auf den vorgaenger-wert gesetzt wird (bob zeile 250, sprint zeile 289,
ease zeile 211/216). Der gerenderte verlauf ist also die zustandsfolge um EINEN tick
verzoegert, linear interpoliert. Bake-regel: key auf frame(t) = zustand(t-1),
LINEAR — Blenders lerp zwischen keys reproduziert dann EXAKT den in-game-frame-wert.
EINZIGE ausnahme: bob-phase g EXTRAPOLIERT vorwaerts (applyBob zeile 231-232:
g = -(walk + (walk - lastWalk)*td)) statt zu verzoegern — darum bakt der bob mit
`samples_per_tick` STUETZPUNKTEN pro tick, jeder mit der exakten java-formel bei
fraktionalem td berechnet (die sin/cos-kruemmung innerhalb eines ticks ist sonst
bis ~4% amplitude daneben; an tick-grenzen glaettet LINEAR den echten (auch in-game
vorhandenen) mini-sprung ueber das letzte sample-intervall — dokumentierte v1-grenze).

## WAS WOHIN GEBACKEN WIRD (alles fp_hand-rig, users objekte NIE):
  - bob         -> BBS_FP_HandBob-empty (fp_hand.BOB_OBJ_NAME, kind der hand-kamera,
                   arm haengt dran): location x/y + rotation_euler x/z, kamera-lokal.
                   MC-hand-render-raum und Blender-kamera-raum sind BEIDE x-rechts/
                   y-hoch/-z-vorwaerts, rechtshaendig -> 1:1-mapping ohne umrechnung.
                   matrix-stack-ordnung translate->rotZ->rotX == Blender euler 'XYZ'
                   (R = Rz@Ry@Rx, ry=0) mit location — exakt dieselbe komposition.
  - sprint-FOV  -> hand-szene-custom-prop fp_hand.PROP_SPRINT_FACTOR; der lens-driver
                   der hand-kamera (fp_hand._get_or_create_hand_scene) liest ihn als
                   `sfac` und rechnet lens aus fov*sfac — welt-szene bleibt unberuehrt
                   (FOV-isolations-gate!). Vanilla boostet AUCH die welt-FOV — die
                   welt-kamera gehoert dem user/task-1.3, bewusst NICHT angefasst
                   (dokumentierte grenze, gehoert zu 5.1b-rest/5.4).
  - show-ease   -> welt-szenen-props fp_hand.PROP_SHOW_HAND/PROP_SHOW_HUD (die
                   AlphaOver-Factor-driver existieren seit 5.1 genau dafuer:
                   "bewusst FLOAT statt bool"). CHIRURGISCH: nur unsere zwei
                   data-paths, die welt-szene kann eigene user-animation tragen.
                   ⚠️ KONSUM-SEMANTIK (W7-B6 code-verifikation 2026-07-17, dossier
                   com/afrobama/CLAUDE.md): im game ist der progress KEIN alpha-fade —
                   hand: multipliziert die EQUIP-progress-felder (hand faehrt von unten
                   hoch), HUD: vertikaler slide y=(1-progress)*scaledHeight. Der WERT
                   der kurve ist identisch (dieselbe clamp/snap-formel), nur der
                   verbraucher ist im rig heute der AlphaOver-faktor (v1-platzhalter-
                   konsum, sichtbar+togglebar). Raise-pose (hand) und slide (HUD)
                   sind 5.1b-rest/5.3b — der transport hier bleibt dann unveraendert.

## V1-GRENZEN (dokumentiert, nicht versteckt):
  - kanal-gate = `show_hand`-KANAL > 0. Die tracker-CLIP-property (default false) ist
    heute nicht transportiert — effektive sichtbarkeit waere clip AND kanal (task 5.4).
  - kein POV-uid-lookup hier: der aufrufer waehlt das replay (H33/POV-dropdown ist
    ui.py = W7-B3-territorium). `channels_from_actors_payload` nimmt einen index/uuid.
  - kanaele werden an ganzzahligen ticks LINEAR gesampelt — recorded kanaele sind
    dicht (key pro tick), da ist das exakt; sparse handgesetzte keyframes mit
    bezier-interp weichen ab (BBS interpoliert dann nicht-linear).
  - sprint-FOV-parity ist NAEHERUNG: in-game wirkt der faktor auf die WELT-fov
    (PositionFovMixin auf Position.apply) und die hand kriegt eine tan-ratio-
    korrektur die IMMER auf 70-grad-basis rechnet (adjustFovForSprint ist toter
    code, lastBaseFov bleibt 70 — W7-B6-befund). Hier: faktor multiplikativ auf
    die HAND-fov, exakt-abgleich haengt an der 5.0-fov-messkurve (plan-task 5.0).
"""

import bpy
import math

from .actors import _build_fcurve, _find_fcurve, _kf_float

# ---- konstanten, 1:1 aus TrackerClientClip.java (zeilen-verweise stand 2026-07-17) ----
TICKS_PER_SECOND = 20.0          # MC-takt (wie actors._build_actors / FilmCameraExporter)
BOB_TARGET_CAP = 0.1             # updateBob z.264: target = min(0.1, tickSpeed)
BOB_APPROACH = 0.4               # updateBob z.265: bob += (target - bob) * 0.4
BOB_WALK_SCALE = 0.6             # updateBob z.266: walk += tickSpeed * 0.6
BOB_TELEPORT_GUARD = 2.0         # updateBob z.257: tickSpeed > 2 -> bob hart 0
BOB_MIN_VISIBLE = 0.002          # applyBob z.229: h < 0.002 -> gar kein transform
SPRINT_FOV_TARGET = 1.15         # updateSprintFov z.292 / mixin z.44
SPRINT_RAMP_INGAME = 0.5         # TrackerSprintRampMixin z.45 (CANCELT original, gemessen)
SPRINT_RAMP_UPSTREAM = 0.2       # updateSprintFov z.293 (nur OHNE bbstools-mixin aktiv)
EASE_HAND_RATE = 0.2             # advanceShowTransitions z.213 (5 ticks 0->1)
EASE_HUD_RATE = 0.25             # advanceShowTransitions z.218 (4 ticks 0->1)
EASE_SNAP = 0.01                 # advanceShowTransitions z.214/219: snap auf target

# default-stuetzpunkte pro tick fuer den bob (siehe docstring "FRAME-INTERPOLATION")
BOB_SAMPLES_PER_TICK = 4


# ============================================================================
# formel-kern: REINE mathematik, kein bpy — jede funktion ist gegen die java-
# zeilen oben pruefbar (und wird im gate gegen eine UNABHAENGIGE nachrechnung
# der formeln verglichen, toleranz 1e-4)
# ============================================================================

def sample_channel(keyframes, tick, default=0.0):
    """Einen BBS-kanal ([{tick,value,...},...]) an einem ganzzahligen tick LINEAR
    auswerten. Vor dem ersten key = erster wert, nach dem letzten = letzter wert
    (BBS' KeyframeChannel.interpolate haelt genauso an den raendern)."""
    pts = sorted(
        ((_kf_float(kf, "tick", 0.0), _kf_float(kf, "value", default))
         for kf in keyframes if isinstance(kf, dict)),
        key=lambda p: p[0],
    )
    if not pts:
        return default
    if tick <= pts[0][0]:
        return pts[0][1]
    if tick >= pts[-1][0]:
        return pts[-1][1]
    for i in range(1, len(pts)):
        t0, v0 = pts[i - 1]
        t1, v1 = pts[i]
        if tick <= t1:
            if t1 == t0:
                return v1
            f = (tick - t0) / (t1 - t0)
            return v0 + (v1 - v0) * f
    return pts[-1][1]


def tick_speeds_from_positions(x_kfs, z_kfs, duration):
    """tickSpeed pro tick aus den x/z-POSITIONS-kanaelen: sqrt(dx²+dz²) mit
    dx = x(t)-x(t-1) — exakt was updateBob im playback sieht (das replay-entity
    wird von diesen kanaelen positioniert). tick 0 hat kein prev -> 0."""
    speeds = [0.0]
    px = sample_channel(x_kfs, 0)
    pz = sample_channel(z_kfs, 0)
    for t in range(1, duration + 1):
        cx = sample_channel(x_kfs, t)
        cz = sample_channel(z_kfs, t)
        speeds.append(math.hypot(cx - px, cz - pz))
        px, pz = cx, cz
    return speeds


def tick_speeds_from_velocity(vx_kfs, vz_kfs, duration):
    """Fallback wenn x/z fehlen: |v| pro tick aus vX/vZ. Einheit ist bloecke/TICK
    (MC getVelocity — MESSFUND (a) im docstring: KEIN /20, anders als referenz-doc §2)."""
    return [math.hypot(sample_channel(vx_kfs, t), sample_channel(vz_kfs, t))
            for t in range(duration + 1)]


def bob_states(tick_speeds, active_flags):
    """updateBob/clearBobState pro tick nachgerechnet.
    active_flags[t] = renderHand UND NICHT sneaking (CameraWorkCameraController z.63:
    sonst clearBobState — bob UND walk springen auf 0, kein ausklingen).
    Gibt liste (bob_amount, walk_distance) je tick, index == tick."""
    states = []
    bob = 0.0
    walk = 0.0
    for t, speed in enumerate(tick_speeds):
        if not active_flags[t]:
            bob = 0.0
            walk = 0.0                       # clearBobState z.271-273: ALLES auf 0
        elif speed > BOB_TELEPORT_GUARD:
            bob = 0.0                        # teleport-guard z.257-261 (walk bleibt!)
        else:
            target = min(BOB_TARGET_CAP, speed)
            bob += (target - bob) * BOB_APPROACH
            walk += speed * BOB_WALK_SCALE
        states.append((bob, walk))
    return states


def bob_offsets(states, tick, tick_delta):
    """applyBob an zeitpunkt tick+tick_delta: (tx, ty, tz, rx_rad, rz_rad) fuer den
    bob-empty. h verzoegert (lerp prev->cur), g extrapoliert (docstring-konvention).
    h < 0.002 -> identitaet (java macht dann GAR keinen transform)."""
    cur_bob, cur_walk = states[tick]
    prev_bob, prev_walk = states[tick - 1] if tick > 0 else (0.0, 0.0)
    h = prev_bob + (cur_bob - prev_bob) * tick_delta
    if h < BOB_MIN_VISIBLE:
        return (0.0, 0.0, 0.0, 0.0, 0.0)
    g = -(cur_walk + (cur_walk - prev_walk) * tick_delta)
    sin_g = math.sin(g * math.pi)
    cos_g = math.cos(g * math.pi)
    tx = sin_g * h * 0.5
    ty = -abs(cos_g * h)
    rz = math.radians(sin_g * h * 3.0)
    rx = math.radians(abs(math.cos(g * math.pi - 0.2) * h) * 5.0)
    return (tx, ty, 0.0, rx, rz)


def sprint_factor_states(sprinting_flags, ramp=SPRINT_RAMP_INGAME):
    """updateSprintFov pro tick: f += (target - f) * ramp, target 1.15/1.0.
    ramp default 0.5 = gemessenes in-game-verhalten (mixin), 0.2 = upstream-BBS."""
    states = []
    f = 1.0
    for sprinting in sprinting_flags:
        target = SPRINT_FOV_TARGET if sprinting else 1.0
        f += (target - f) * ramp
        states.append(f)
    return states


def show_progress_states(targets, rate):
    """advanceShowTransitions pro tick fuer EINEN kanal (hand rate=0.2, hud rate=0.25).
    targets[t] = render-flag (bool). Gibt die PREV-werte zurueck (zustand der im frame
    von tick t als startwert gerendert wird, z.211/216) — das ist per konvention der
    key auf frame(t). Einschalt-sprung: progress springt VOR dem prev-capture auf 0
    (z.208-209), der einschalt-tick keyed also exakt 0."""
    keys = []
    p = 0.0
    prev_target = False
    for target in targets:
        if target and not prev_target:
            p = 0.0                          # neu-einblenden startet IMMER bei 0
        keys.append(p)                       # prev<X>ShowProgress dieses ticks
        goal = 1.0 if target else 0.0
        p += max(-rate, min(rate, goal - p))
        if abs(p - goal) < EASE_SNAP:
            p = goal
        prev_target = bool(target)
    return keys


# ============================================================================
# payload-zugriff
# ============================================================================

def channels_from_actors_payload(fields, replay=0):
    """Die kanal-dict EINES replays aus einem ACTORS_EXPORT_RESULT-fields-dict ziehen.
    `replay`: index ODER uuid-string (POV-uid-aufloesung/panel = 5.1b-rest, W7-B3)."""
    replays = fields.get("replays") or []
    if isinstance(replay, str):
        for r in replays:
            if r.get("uuid") == replay or str(r.get("id")) == replay:
                return r.get("channels") or {}
        return {}
    try:
        return replays[replay].get("channels") or {}
    except (IndexError, TypeError, AttributeError):
        return {}


def _channel_duration(channels):
    """Hoechster tick ueber alle relevanten kanaele (bake-bereich 0..duration)."""
    duration = 0
    for kfs in channels.values():
        for kf in kfs if isinstance(kfs, list) else []:
            if isinstance(kf, dict):
                duration = max(duration, int(_kf_float(kf, "tick", 0.0)))
    return duration


# ============================================================================
# bake-schicht (bpy) — alles landet AUSSCHLIESSLICH auf fp_hand-rig-datenbloecken
# bzw. den zwei welt-szenen-props die fp_hand angelegt hat
# ============================================================================

def _build_idprop_fcurve(id_data, prop, keyframes, tick_scale):
    """Scalar-bake auf eine ID-custom-property ('["prop"]'-data-path). Gleiches muster
    wie film_camera_import._build_scalar_fcurve, nur mit subscript-pfad (setattr geht
    bei custom-props nicht — item-assignment + keyframe_insert)."""
    data_path = '["%s"]' % prop
    pts = []
    seen = set()
    for kf in sorted(keyframes, key=lambda k: _kf_float(k, "tick", 0.0)):
        frame = _kf_float(kf, "tick", 0.0) * tick_scale
        marker = round(frame, 4)
        if marker in seen:
            continue
        seen.add(marker)
        pts.append((frame, _kf_float(kf, "value", 0.0)))
    if not pts:
        return 0
    first_frame, first_value = pts[0]
    try:
        id_data[prop] = first_value
        if not id_data.keyframe_insert(data_path=data_path, frame=first_frame):
            return 0
    except (RuntimeError, TypeError, AttributeError) as e:
        print("[bbs-bridge] fp_channels: keyframe_insert %s fehlgeschlagen: %s" % (data_path, e))
        return 0
    fc = _find_fcurve(id_data, data_path, 0)
    if fc is None:
        print("[bbs-bridge] fp_channels: fcurve %s nach insert nicht gefunden" % data_path)
        return 0
    for frame, value in pts:
        kp = fc.keyframe_points.insert(frame, value, options={'FAST'})
        kp.interpolation = 'LINEAR'
    fc.update()
    return len(pts)


def _remove_fcurve(id_data, data_path, index=0):
    """EINE fcurve chirurgisch aus der action eines ID entfernen (5.2-slotted-pfad,
    wie _find_fcurve nur mit remove). Fremde fcurves (user-animation auf derselben
    szene!) bleiben unangetastet. True wenn etwas entfernt wurde."""
    ad = getattr(id_data, "animation_data", None)
    if ad is None or ad.action is None:
        return False
    act = ad.action
    slot = getattr(ad, "action_slot", None)
    removed = False
    if slot is not None:
        try:
            for layer in act.layers:
                for strip in layer.strips:
                    bag = strip.channelbag(slot)
                    if bag is None:
                        continue
                    fc = bag.fcurves.find(data_path, index=index)
                    if fc is not None:
                        bag.fcurves.remove(fc)
                        removed = True
        except (AttributeError, TypeError, RuntimeError):
            pass
    if not removed:
        try:
            fc = act.fcurves.find(data_path, index=index)
            if fc is not None:
                act.fcurves.remove(fc)
                removed = True
        except (AttributeError, TypeError, RuntimeError):
            pass
    return removed


def _prune_empty_action(id_data):
    """Nach chirurgischem entfernen: ist die action des ID jetzt KOMPLETT leer (alle
    layer/strips/channelbags ALLER slots, nicht nur unserer) und haengt nur an diesem
    ID, war UNSER bake ihr einziger inhalt -> action mit weg (sonst bliebe eine leere
    'SceneAction' als rueckstand auf der user-szene). Traegt sie noch IRGENDEINE fremde
    fcurve oder haengt sie an mehreren IDs (5.2-slot-sharing) -> nicht anfassen."""
    ad = getattr(id_data, "animation_data", None)
    if ad is None or ad.action is None:
        return
    act = ad.action
    try:
        for layer in act.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    if len(bag.fcurves) > 0:
                        return
    except (AttributeError, TypeError, RuntimeError):
        return
    if act.users > 1:
        return
    ad.action = None
    try:
        act.use_fake_user = False
        if act.users == 0:
            bpy.data.actions.remove(act)
    except (ReferenceError, RuntimeError):
        pass


def clear_object_animation(id_data):
    """Action eines KOMPLETT uns gehoerenden datenblocks (bob-empty, hand-szene)
    restlos weg — fake-user-hygiene wie film_camera_import._clear_film_cam_animation.
    NIE fuer die welt-szene benutzen (dort nur _remove_fcurve, chirurgisch)."""
    ad = getattr(id_data, "animation_data", None)
    if ad is None or ad.action is None:
        return
    act = ad.action
    id_data.animation_data_clear()
    try:
        act.use_fake_user = False
        if act.users == 0:
            bpy.data.actions.remove(act)
    except (ReferenceError, RuntimeError):
        pass


def apply_channels(channels, world_scene=None, ramp=SPRINT_RAMP_INGAME,
                   samples_per_tick=BOB_SAMPLES_PER_TICK, duration=None):
    """Bob + sprint-FOV + show-ease aus einem kanal-dict ins fp_hand-rig baken.

    `channels`: dict kanal-name -> [{tick,value,...},...] (ACTORS-payload-shape,
    siehe channels_from_actors_payload; synthetische dicts gehen genauso).
    Das rig muss stehen (fp_hand.build_rig) — fehlt es, wird es NICHT still gebaut
    (kein versteckter seiteneffekt), sondern mit fehlermeldung abgebrochen.
    Re-apply ERSETZT (idempotent): vorher werden exakt unsere fcurves entfernt.
    Gibt dict mit key-zaehlern je ziel zurueck (fuer gates/logs)."""
    from . import fp_hand   # lokal: fp_hand importiert uns in remove_rig (kein zyklus)

    world_scene = world_scene if world_scene is not None else bpy.context.scene
    hand_scene = bpy.data.scenes.get(fp_hand.HAND_SCENE_NAME)
    bob_obj = bpy.data.objects.get(fp_hand.BOB_OBJ_NAME)
    if hand_scene is None or bob_obj is None:
        print("[bbs-bridge] fp_channels.apply_channels: fp_hand-rig fehlt (build_rig zuerst)")
        return None

    if duration is None:
        duration = _channel_duration(channels)
    fps = world_scene.render.fps / (world_scene.render.fps_base or 1.0)
    tick_scale = fps / TICKS_PER_SECOND

    clear_channels(world_scene)   # re-apply ersetzt, stapelt nie

    ticks = range(duration + 1)
    show_hand = [sample_channel(channels.get("show_hand", []), t) > 0.0 for t in ticks]
    show_hud = [sample_channel(channels.get("show_hud", []), t) > 0.0 for t in ticks]
    sneaking = [sample_channel(channels.get("sneaking", []), t) > 0.0 for t in ticks]
    sprinting = [sample_channel(channels.get("sprinting", []), t) > 0.0 for t in ticks]

    # --- bob: tick-speeds (positions-deltas primaer, vX/vZ fallback) -> zustaende ---
    if channels.get("x") and channels.get("z"):
        speeds = tick_speeds_from_positions(channels["x"], channels["z"], duration)
    else:
        speeds = tick_speeds_from_velocity(
            channels.get("vX", []), channels.get("vZ", []), duration)
    bob_active = [show_hand[t] and not sneaking[t] for t in ticks]
    states = bob_states(speeds, bob_active)

    sub = max(1, int(samples_per_tick))
    bob_kfs = {"tx": [], "ty": [], "rx": [], "rz": []}
    for t in ticks:
        for s in range(sub):
            td = s / float(sub)
            if t == duration and s > 0:
                break                      # nicht ueber das kanal-ende extrapolieren
            tx, ty, _tz, rx, rz = bob_offsets(states, t, td)
            tick_f = t + td
            bob_kfs["tx"].append({"tick": tick_f, "value": tx, "interp": "linear"})
            bob_kfs["ty"].append({"tick": tick_f, "value": ty, "interp": "linear"})
            bob_kfs["rx"].append({"tick": tick_f, "value": rx, "interp": "linear"})
            bob_kfs["rz"].append({"tick": tick_f, "value": rz, "interp": "linear"})

    counts = {}
    bob_obj.rotation_mode = 'XYZ'   # matrix-stack-ordnungs-argument im docstring haengt dran
    n = 0
    n += _build_fcurve(bob_obj, bob_kfs["tx"], ("location", 0, 1.0, 0.0), tick_scale)[0]
    n += _build_fcurve(bob_obj, bob_kfs["ty"], ("location", 1, 1.0, 0.0), tick_scale)[0]
    n += _build_fcurve(bob_obj, bob_kfs["rx"], ("rotation_euler", 0, 1.0, 0.0), tick_scale)[0]
    n += _build_fcurve(bob_obj, bob_kfs["rz"], ("rotation_euler", 2, 1.0, 0.0), tick_scale)[0]
    counts["bob"] = n

    # --- sprint-FOV-faktor -> hand-szenen-prop (ein-tick-shift: key(t)=zustand(t-1)) ---
    factors = sprint_factor_states(sprinting, ramp=ramp)
    sprint_kfs = [{"tick": t, "value": factors[t - 1] if t > 0 else 1.0} for t in ticks]
    counts["sprint"] = _build_idprop_fcurve(
        hand_scene, fp_hand.PROP_SPRINT_FACTOR, sprint_kfs, tick_scale)

    # --- show-ease -> welt-szenen-props (progress IST der mix-faktor, referenz §1) ---
    hand_keys = show_progress_states(show_hand, EASE_HAND_RATE)
    hud_keys = show_progress_states(show_hud, EASE_HUD_RATE)
    counts["show_hand"] = _build_idprop_fcurve(
        world_scene, fp_hand.PROP_SHOW_HAND,
        [{"tick": t, "value": hand_keys[t]} for t in ticks], tick_scale)
    counts["show_hud"] = _build_idprop_fcurve(
        world_scene, fp_hand.PROP_SHOW_HUD,
        [{"tick": t, "value": hud_keys[t]} for t in ticks], tick_scale)

    print("[bbs-bridge] fp_channels.apply_channels: duration=%d ticks, keys=%s "
          "(ramp=%.2f, %d samples/tick, quelle=%s)"
          % (duration, counts, ramp, sub,
             "x/z-deltas" if channels.get("x") and channels.get("z") else "vX/vZ"))
    return counts


def clear_channels(world_scene=None):
    """Alle von apply_channels gebackenen kurven entfernen. Bob-empty + hand-szene
    gehoeren KOMPLETT uns -> action restlos weg; welt-szene NUR chirurgisch (genau
    unsere zwei prop-fcurves), user-animation bleibt. Werte: sprint-faktor zurueck
    auf 1.0 (neutral), show-props behalten ihren letzten stand (user-toggle)."""
    from . import fp_hand

    world_scene = world_scene if world_scene is not None else bpy.context.scene
    bob_obj = bpy.data.objects.get(fp_hand.BOB_OBJ_NAME)
    if bob_obj is not None:
        clear_object_animation(bob_obj)
        bob_obj.location = (0.0, 0.0, 0.0)
        bob_obj.rotation_euler = (0.0, 0.0, 0.0)
    hand_scene = bpy.data.scenes.get(fp_hand.HAND_SCENE_NAME)
    if hand_scene is not None:
        clear_object_animation(hand_scene)
        if fp_hand.PROP_SPRINT_FACTOR in hand_scene.keys():
            hand_scene[fp_hand.PROP_SPRINT_FACTOR] = 1.0
    for prop in (fp_hand.PROP_SHOW_HAND, fp_hand.PROP_SHOW_HUD):
        _remove_fcurve(world_scene, '["%s"]' % prop, 0)
    _prune_empty_action(world_scene)   # war unser bake der einzige inhalt -> keine leere
                                        # action als rueckstand (user-fcurves schuetzen davor)
