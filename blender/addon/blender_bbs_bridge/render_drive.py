"""TASK 6.1 (W8-B1) — renderfahrt weg A: den gebundenen film als PNG-sequenz aus BLENDER
rendern + optional ffmpeg-mux zu mp4. `render_film()` ist der eine einstiegspunkt (spaeter
der ui.py-button, anbindung nach absprache — ui.py ist fremdes flotten-territorium).

OFFLINE BY DESIGN: die kamera-fahrt liegt als GEBAKTE fcurves auf `BBS_FilmCam`
(film_camera_import, task 1.3 — dense pro-tick-LINEAR-keys) und die actors sind ebenso
gebakt (actors.py / replay_anim.py). `scene.frame_set(f)` pro frame IST deshalb die ganze
playhead-kopplung — es gibt KEINEN live-zugriff auf BBS waehrend des renders (kein request,
kein playhead-push noetig; CameraAuthority-renderfahrt-flag ungenutzt, das ist weg B /
task 6.2). Wuerde dieser pfad doch live-daten ziehen, waere das ein BUG (brief-invariante);
`render_film` misst das selbst nach (state._seq_out vorher/nachher, siehe unten).

BAUSTEIN-HERKUNFT (orchestriert, nicht neu erfunden):
- frame-range = min/max keyframe-x ueber ALLE gebakten fcurves (objekt-action UND
  kamera-daten-action, `lens` liegt auf obj.data!) via actors._object_fcurve_collections
  (der 5.2-slotted-actions-sichere container-walk — action.fcurves existiert nicht mehr).
- EEVEE-preset: engine 'BLENDER_EEVEE' (5.2-enum-falle: NICHT '_NEXT'),
  view_transform='Standard' (MC-look, AgX entsaettigt — tools/render_animation.py-erbe).
  AUFLOESUNG kommt AUS DER SCENE (brief: "film-aufloesung aus scene") — bewusst NICHT
  angefasst.
- pro frame `bpy.ops.render.render(write_still=True)` mit filepath je frame —
  RENDER-RESULT-FALLE (paket-dossier/fp_hand): `bpy.data.images["Render Result"].pixels`
  ist ohne offenen Image-Editor leer; write_still+datei ist der EINZIG verlaessliche weg.
- ffmpeg-suche: winget-package zuerst, dann WinGet/Links, dann PATH — identisches muster
  wie ~/.claude/tools/flight.mjs / watch_screen.mjs (dort live bewiesen).

NICHT-DESTRUKTIV (invariante 5/5b): alles was der preset anfasst (engine, view_transform,
file_format, filepath, scene.camera, frame_current, optional taa_render_samples) wird
VOR dem render gesnapshottet und im finally RESTAURIERT — auch bei exception/abbruch
bleibt die instanz exakt wie vorgefunden. Es werden NIE szenen-objekte angelegt/geloescht.

SYNCHRON/FREEZE: render_film blockt den aufrufenden thread fuer die GANZE sequenz
(N frames x s/frame — messreihe: EEVEE ~1.8s/frame @640x360 auf der RTX 3080, skaliert
mit aufloesung). In einer sichtbaren instanz ist das ein ehrlicher UI-freeze, KEIN hang —
fuer den ui.py-button spaeter ggf. modal/timer-haeppchen (absprache main). Der addon-tick
pausiert solange mit; inbound-pushes puffern im socket und laufen danach normal weiter.

Read-only evaluation is scoped explicitly: internal frame changes during a render
or cache check never claim or push the BBS playhead, even with Drive timeline enabled.
The previous owner is not reassigned. Normal local scrubbing resumes after the scope.

Status lebt MODUL-LOKAL (`_status`/`_last_result`, konvention replay_anim._pending:
state.py ist fremdes territorium; lesen von state.X ist ok, schreiben nicht).
"""

import math
import os
import subprocess
import tempfile
import time

import bpy

from . import state
from .evaluation_scope import quiet
from .actors import _object_fcurve_collections
from .film_camera_import import BBS_FILM_CAM_NAME

#: dateiname-schema der sequenz — %04d haelt ffmpeg's image2-demuxer und windows-sortierung
#: deckungsgleich; render_film haengt KEINE extension an (use_file_extension macht .png draus)
FRAME_PATTERN = "frame_%04d"
MUX_OUT_NAME = "film.mp4"
FFMPEG_MUX_TIMEOUT = 600.0   # sek — 48 frames muxen dauert <1s, das cap faengt nur haenger

_status = "idle"      # panel-tauglicher einzeiler (ui.py liest spaeter render_drive._status)
_last_result = None   # dict des letzten render_film-laufs (gates/konsole)


def _fcurve_x_range(fc):
    """(min_x, max_x) einer fcurve. fc.range() ist der billige API-weg; fallback manuell
    ueber keyframe_points (range() koennte auf exotischen curves leer/kaputt sein —
    defensiv, nie werfen: ein leerer kanal darf die range-suche nicht killen)."""
    try:
        a, b = fc.range()
        return float(a), float(b)
    except (AttributeError, TypeError, RuntimeError):
        pass
    xs = [kp.co[0] for kp in fc.keyframe_points]
    if not xs:
        return None
    return min(xs), max(xs)


def film_frame_range(cam_obj):
    """Frame-range (start, end als INT, inklusiv) aus den GEBAKTEN fcurves der film-kamera —
    objekt-seite (location/rotation_euler) UND daten-seite (lens auf obj.data, eigener
    animation_data-holder!). None wenn gar keine keys da sind (= film nie importiert).

    Rundung: erst auf 4 nachkommastellen (dieselbe toleranz wie film_camera_imports
    dedup-marker), DANN floor/ceil — tick_scale-arithmetik (z.b. 24/20=1.2) produziert
    sonst 35.999999x/36.000000x-float-kippler an den raendern."""
    lo = None
    hi = None
    for holder in (cam_obj, getattr(cam_obj, "data", None)):
        if holder is None:
            continue
        for coll in _object_fcurve_collections(holder):
            for fc in coll:
                if len(fc.keyframe_points) == 0:
                    continue
                rng = _fcurve_x_range(fc)
                if rng is None:
                    continue
                lo = rng[0] if lo is None else min(lo, rng[0])
                hi = rng[1] if hi is None else max(hi, rng[1])
    if lo is None or hi is None:
        return None
    return int(math.floor(round(lo, 4))), int(math.ceil(round(hi, 4)))


def find_ffmpeg():
    """ffmpeg.exe auffinden: winget-PACKAGE-dir zuerst (frischeste echte binary), dann der
    WinGet/Links-shim, dann nackt 'ffmpeg' (PATH). Muster 1:1 aus ~/.claude/tools/flight.mjs
    (dort kommentiert 'identisch zu watch_screen.mjs'). Jeder kandidat wird mit -version
    PROBIERT statt nur auf existenz geprueft (ein kaputter shim faellt so durch).
    None wenn nichts laeuft — der aufrufer degradiert ehrlich (PNGs bleiben gueltig)."""
    cands = []
    lad = os.environ.get("LOCALAPPDATA", "")
    pkg_root = os.path.join(lad, "Microsoft", "WinGet", "Packages")
    try:
        for pkg in os.listdir(pkg_root):
            if not pkg.startswith("Gyan.FFmpeg"):
                continue
            pkg_dir = os.path.join(pkg_root, pkg)
            for sub in os.listdir(pkg_dir):
                if sub.startswith("ffmpeg-"):
                    cands.append(os.path.join(pkg_dir, sub, "bin", "ffmpeg.exe"))
    except OSError:
        pass   # kein winget-package — Links/PATH probieren
    if lad:
        cands.append(os.path.join(lad, "Microsoft", "WinGet", "Links", "ffmpeg.exe"))
    cands.append("ffmpeg")
    for cand in cands:
        try:
            probe = subprocess.run([cand, "-version"], capture_output=True, timeout=15)
            if probe.returncode == 0:
                return cand
        except (OSError, subprocess.SubprocessError):
            continue
    return None


def mux_sequence(out_dir, start, count, fps, out_name=MUX_OUT_NAME):
    """PNG-sequenz -> mp4 (H.264/yuv420p = ueberall abspielbar). Gibt (mp4_pfad, None)
    oder (None, grund) zurueck — ffmpeg-fehlen/-fehler degradiert ehrlich statt zu werfen.

    -start_number/-frames:v nageln den mux EXAKT auf unsere range fest — stale frames
    eines aelteren laufs im selben dir koennen sich nicht einschleichen (statt sie zu
    LOESCHEN: nicht-destruktiv auch gegenueber fremden dateien).
    -vf scale=trunc(.../2)*2: yuv420p verlangt GERADE dimensionen — die scene-aufloesung
    kommt vom user und darf ungerade sein; 1px runden statt hart fehlschlagen."""
    ffmpeg = find_ffmpeg()
    if ffmpeg is None:
        return None, "ffmpeg nicht gefunden (winget install Gyan.FFmpeg) — PNG-sequenz bleibt gueltig"
    mp4 = os.path.join(out_dir, out_name)
    args = [
        ffmpeg, "-y",
        "-framerate", "%g" % fps,
        "-start_number", str(start),
        "-i", os.path.join(out_dir, FRAME_PATTERN + ".png"),
        "-frames:v", str(count),
        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
        "-c:v", "libx264", "-pix_fmt", "yuv420p",
        mp4,
    ]
    try:
        run = subprocess.run(args, capture_output=True, timeout=FFMPEG_MUX_TIMEOUT)
    except (OSError, subprocess.SubprocessError) as e:
        return None, "ffmpeg-aufruf fehlgeschlagen: %s" % e
    if run.returncode != 0:
        tail = (run.stderr or b"").decode("utf-8", "replace").strip().splitlines()[-3:]
        return None, "ffmpeg exit %d: %s" % (run.returncode, " | ".join(tail))
    if not os.path.isfile(mp4) or os.path.getsize(mp4) == 0:
        return None, "ffmpeg meldete erfolg, aber %s fehlt/leer" % out_name
    return mp4, None


def _snapshot_render_settings(scene):
    """Alles merken was preset+drive anfassen — und NUR das (kein blind-kopieren des
    ganzen render-blocks: was wir nicht aendern, muessen wir nicht anfassen)."""
    return {
        "engine": scene.render.engine,
        "view_transform": scene.view_settings.view_transform,
        "file_format": scene.render.image_settings.file_format,
        "filepath": scene.render.filepath,
        "camera": scene.camera,
        "frame_current": scene.frame_current,
        "taa": getattr(scene.eevee, "taa_render_samples", None),
    }


def _restore_render_settings(scene, snap):
    """Snapshot zuruecksetzen — jeder schritt einzeln gefangen: ein restore-fehler darf
    die uebrigen restores nicht verhindern (sonst hinterlaesst ein crash HALB restauriert)."""
    for setter in (
        lambda: setattr(scene.render, "engine", snap["engine"]),
        lambda: setattr(scene.view_settings, "view_transform", snap["view_transform"]),
        lambda: setattr(scene.render.image_settings, "file_format", snap["file_format"]),
        lambda: setattr(scene.render, "filepath", snap["filepath"]),
        lambda: setattr(scene, "camera", snap["camera"]),
        lambda: scene.frame_set(snap["frame_current"]),
        lambda: (snap["taa"] is not None
                 and setattr(scene.eevee, "taa_render_samples", snap["taa"])),
    ):
        try:
            setter()
        except (AttributeError, TypeError, RuntimeError, ReferenceError):
            pass


def _apply_eevee_preset(scene, samples, engine='BLENDER_EEVEE'):
    """Render-preset (brief 6.1, seit 2026-08-16 engine-waehlbar): default 'BLENDER_EEVEE'
    (5.2-enum! '_NEXT' gibt es nicht mehr, world_light-dossier), alternativ 'CYCLES'
    (P7 engine-wahl im BBS-seitenpanel). view_transform 'Standard' (MC-look — AgX macht
    die MC-farben milchig), PNG als frame-format. AUFLOESUNG BLEIBT UNANGETASTET.
    samples=None laesst die sample-zahl wie vorgefunden (determinismus-freundlich);
    bei CYCLES zaehlt samples auf scene.cycles.samples.

    CYCLES-GPU: best effort — wenn die cycles-prefs eine GPU kennen, wird sie benutzt,
    sonst CPU mit lautem log (nie still langsam)."""
    scene.render.engine = engine
    scene.view_settings.view_transform = 'Standard'
    scene.render.image_settings.file_format = 'PNG'
    if engine == 'CYCLES':
        if samples is not None and hasattr(scene, "cycles"):
            scene.cycles.samples = samples
        try:
            import bpy as _bpy
            prefs = _bpy.context.preferences.addons['cycles'].preferences
            for typ in ('OPTIX', 'CUDA'):
                try:
                    prefs.compute_device_type = typ
                    prefs.get_devices()
                    geraete = [d for d in prefs.devices if d.type == typ]
                    if geraete:
                        for d in prefs.devices:
                            d.use = (d.type == typ or d.type == 'CPU')
                        scene.cycles.device = 'GPU'
                        print("[bbs-bridge] cycles: GPU (%s, %s)" % (typ, geraete[0].name))
                        break
                except Exception:
                    continue
            else:
                scene.cycles.device = 'CPU'
                print("[bbs-bridge] cycles: KEINE GPU in den prefs — CPU (deutlich langsamer)")
        except Exception as e:
            print("[bbs-bridge] cycles: geraete-wahl fehlgeschlagen (%s) — engine-default" % e)
    elif samples is not None and hasattr(scene.eevee, "taa_render_samples"):
        scene.eevee.taa_render_samples = samples


#: Scene-prop, in der sim_baken den fingerabdruck des gebackenen sim-stands hinterlegt.
SIM_BAKE_PROP = "bbsbridge_sim_bake_stand"


@quiet()
def sim_fingerprint(scene):
    """Kennzahl ueber ALLES, was das ergebnis einer simulation aendert: bereich, gravitation,
    solver, und je objekt lage (matrix_basis zu frame_start) + parameter + vertexzahl fuer
    rigid body, fluid-domain/-flow/-effector, cloth, soft body. Gleiche kennzahl = gleicher
    bake-stand.

    ⛔ matrix_basis, NICHT matrix_world: bei gebackenem cache steht in matrix_world der stand
    aus dem CACHE — verschiebt der user den wuerfel, aendert sich matrix_world nicht, die
    lage-eigenschaft (basis) schon. Genau die aenderung soll hier auffallen."""
    import hashlib
    teile = ["range:%d..%d" % (scene.frame_start, scene.frame_end),
             "gravity:%s" % (tuple(round(g, 4) for g in scene.gravity),)]
    rbw = getattr(scene, "rigidbody_world", None)
    if rbw is not None:
        teile.append("rbw:%d/%d" % (rbw.substeps_per_frame, rbw.solver_iterations))
    f_alt = scene.frame_current
    try:
        scene.frame_set(scene.frame_start)
        for o in sorted(scene.objects, key=lambda x: x.name):
            m = tuple(round(v, 4) for row in o.matrix_basis for v in row)
            nv = len(o.data.vertices) if o.type == 'MESH' and o.data is not None else 0
            rb = getattr(o, "rigid_body", None)
            if o.type == 'MESH' and (rb is not None or any(
                    modifier.type in {'FLUID', 'CLOTH', 'SOFT_BODY', 'COLLISION'}
                    for modifier in o.modifiers)):
                teile.append("mesh:%s:%s" % (o.name, _mesh_fingerprint(o.data)))
            if rb is not None:
                teile.append("rb:%s:%s:%s:%.4f:%.4f:%.4f:%s:%s:%d:%s" % (
                    o.name, rb.type, rb.collision_shape, rb.mass, rb.friction, rb.restitution,
                    rb.enabled, rb.kinematic, nv, m))
            for mod in o.modifiers:
                if mod.type == 'FLUID' and mod.fluid_type == 'DOMAIN' and mod.domain_settings is not None:
                    ds = mod.domain_settings
                    teile.append("fd:%s:%s:%d:%d..%d:%s:%d:%s" % (
                        o.name, ds.domain_type, ds.resolution_max, ds.cache_frame_start,
                        ds.cache_frame_end, tuple(round(v, 3) for v in ds.gravity), nv, m))
                elif mod.type == 'FLUID' and mod.fluid_type == 'FLOW' and mod.flow_settings is not None:
                    fs = mod.flow_settings
                    teile.append("ff:%s:%s:%s:%.3f:%.3f:%.3f:%d:%s" % (
                        o.name, fs.flow_type, fs.flow_behavior, fs.density, fs.temperature,
                        fs.surface_distance, nv, m))
                elif mod.type == 'FLUID' and mod.fluid_type == 'EFFECTOR':
                    teile.append("fe:%s:%d:%s" % (o.name, nv, m))
                elif mod.type == 'CLOTH' and mod.settings is not None:
                    cs = mod.settings
                    teile.append("cl:%s:%.4f:%.4f:%.4f:%d:%s" % (
                        o.name, cs.mass, cs.tension_stiffness, cs.bending_stiffness, nv, m))
                elif mod.type == 'SOFT_BODY' and mod.settings is not None:
                    ss = mod.settings
                    teile.append("sb:%s:%.4f:%.4f:%d:%s" % (o.name, ss.mass, ss.friction, nv, m))
    finally:
        scene.frame_set(f_alt)
    return hashlib.sha1("\n".join(teile).encode("utf-8")).hexdigest()[:16]


@quiet()
def sim_baken(scene=None):
    """Alles baken (ptcache: rigid body/cloth/soft body/partikel; jede fluid-domain) und den
    stand als fingerabdruck in der szene hinterlegen. DER weg, ueber den der bridge-render
    einen cache als gueltig erkennt — cache_pruefen vergleicht gegen genau diesen wert.
    Ein bake ueber Blenders eigene knoepfe hinterlegt ihn NICHT; der render sagt das dann."""
    scene = scene or bpy.context.scene
    fehler = []
    with bpy.context.temp_override(scene=scene):
        try:
            bpy.ops.ptcache.bake_all(bake=True)
        except Exception as e:   # noqa: BLE001
            fehler.append("ptcache: %r" % (e,))
        for o in scene.objects:
            for mod in o.modifiers:
                if mod.type == 'FLUID' and mod.fluid_type == 'DOMAIN':
                    try:
                        with bpy.context.temp_override(object=o, active_object=o, selected_objects=[o]):
                            bpy.ops.fluid.bake_all()
                    except Exception as e:   # noqa: BLE001
                        fehler.append("fluid %s: %r" % (o.name, e))
    scene[SIM_BAKE_PROP] = sim_fingerprint(scene)
    scene[SIM_BAKE_PROP + "_zeit"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    return {"ok": not fehler, "fehler": fehler, "fingerprint": scene[SIM_BAKE_PROP]}


def cache_pruefen(scene, start, end):
    """Alle simulations-caches, die der render braucht: gebacken, zur szene passend, deckt
    start..end? Liefert befunde (leer = frei). Auftrag REDACTED §5: ein fehlender oder
    veralteter cache muss den render scheitern lassen — Blender selbst rendert sonst still
    den stand des caches, wie er ist, oder simuliert ab frame 1 neu und liefert ab der mitte
    unsinn.

    API dieser version (gemessen 2026-09-13, Blender 5.2.1): PointCache hat is_baked /
    is_outdated / frame_start / frame_end; FluidDomainSettings hat has_cache_baked_data +
    cache_frame_start/end und KEIN is_outdated.
    ⛔ is_outdated taugt bei GEBACKENEM cache nicht: Blender setzt es nach dem laden eines
    .blend ohnehin (gemessen: baked=True, outdated=True direkt nach dem oeffnen, ohne jede
    aenderung) und ignoriert es fuer gebackene caches. "Veraltet" erkennt darum der
    fingerabdruck aus sim_baken — und der faengt auch fluid-domain und -flow."""
    befunde = []
    caches = 0

    def pc(name, cache):
        nonlocal caches
        if cache is None:
            return
        caches += 1
        if not cache.is_baked:
            befunde.append("%s: nicht gebacken (ein render ab frame %d wuerde ab frame 1 neu "
                           "simulieren)" % (name, start))
        elif not (cache.frame_start <= start and cache.frame_end >= end):
            befunde.append("%s: cache deckt %d..%d, render will %d..%d"
                           % (name, cache.frame_start, cache.frame_end, start, end))

    rbw = getattr(scene, "rigidbody_world", None)
    if rbw is not None and any(getattr(o, "rigid_body", None) for o in scene.objects):
        pc("rigid body", rbw.point_cache)
    for o in scene.objects:
        for m in o.modifiers:
            if m.type in ('CLOTH', 'SOFT_BODY'):
                pc("%s/%s" % (o.name, m.name), getattr(m, "point_cache", None))
            elif m.type == 'FLUID' and m.fluid_type == 'DOMAIN' and m.domain_settings is not None:
                caches += 1
                ds = m.domain_settings
                if not ds.has_cache_baked_data:
                    befunde.append("%s/%s: fluid-cache nicht gebacken" % (o.name, m.name))
                elif not (ds.cache_frame_start <= start and ds.cache_frame_end >= end):
                    befunde.append("%s/%s: fluid-cache deckt %d..%d, render will %d..%d"
                                   % (o.name, m.name, ds.cache_frame_start, ds.cache_frame_end, start, end))
        for ps in getattr(o, "particle_systems", []) or []:
            pc("%s/%s" % (o.name, ps.name), ps.point_cache)

    if caches and not befunde:
        hinterlegt = scene.get(SIM_BAKE_PROP)
        if not hinterlegt:
            befunde.append("kein bake-stand hinterlegt — mit render_drive.sim_baken() baken; sonst "
                           "ist nicht pruefbar, ob der cache noch zur szene passt")
        else:
            jetzt = sim_fingerprint(scene)
            if jetzt != hinterlegt:
                befunde.append("cache VERALTET: szene seit dem bake geaendert (bake-stand %s, "
                               "jetzt %s) — neu baken" % (hinterlegt, jetzt))
    return befunde


class RenderCancelled(RuntimeError):
    """A render job was explicitly cancelled between native frame operations."""


@quiet()
def render_film(out_dir=None, start=None, end=None, samples=None, mux=True, fps=None,
                scene=None, cam_name=BBS_FILM_CAM_NAME, progress=None, cancelled=None,
                completed_frames=()):
    """Den film als PNG-sequenz rendern (+ mux). Rueckgabe: result-dict (auch in
    _last_result), status-einzeiler in _status. Blockiert bis fertig (docstring oben).

    start/end default = film_frame_range (die gebakten fcurves SIND die wahrheit ueber
    die filmlaenge); explizite werte erlauben teil-renders (crash-resume, profi-workflow
    aus tools/render_animation.py: kurze shots, bei crash an frame N weitermachen)."""
    global _status, _last_result
    scene = scene or bpy.context.scene
    t_wall = time.time()

    cam = bpy.data.objects.get(cam_name)
    if cam is None or cam.type != 'CAMERA' or cam.data is None:
        _status = "keine film-kamera '%s' — erst film-kamera importieren" % cam_name
        _last_result = {"ok": False, "error": _status}
        print("[bbs-render] " + _status)
        return _last_result

    baked = film_frame_range(cam)
    if baked is None and (start is None or end is None):
        _status = "'%s' hat keine gebakten fcurves — erst film-kamera importieren" % cam_name
        _last_result = {"ok": False, "error": _status}
        print("[bbs-render] " + _status)
        return _last_result
    if start is None:
        start = baked[0]
    if end is None:
        end = baked[1]
    if end < start:
        _status = "leere frame-range (%d..%d)" % (start, end)
        _last_result = {"ok": False, "error": _status}
        print("[bbs-render] " + _status)
        return _last_result

    # §5: caches VOR dem ersten frame pruefen — ein render, der mit veraltetem oder
    # fehlendem cache durchlaeuft, liefert ein bild, das nichts bedeutet.
    cache_befunde = cache_pruefen(scene, start, end)
    if cache_befunde:
        _status = "cache-pruefung: " + "; ".join(cache_befunde)
        _last_result = {"ok": False, "error": _status, "cache": cache_befunde}
        print("[bbs-render] " + _status)
        return _last_result

    if out_dir is None:
        out_dir = os.path.join(tempfile.gettempdir(),
                               "bbs_film_render_" + time.strftime("%Y%m%d_%H%M%S"))
    os.makedirs(out_dir, exist_ok=True)

    # ⛔ ZEITBASIS (auftrag REDACTED §3): die keys auf BBS_FilmCam liegen bei frame = tick *
    # (bake_fps/20) — mit der szenen-fps, die BEIM IMPORT galt. Gemuxt werden muss mit
    # genau dieser fps, sonst laeuft der film schneller oder langsamer als der BBS-film.
    # Gemessen 2026-09-13 (abnahme 4): keys bei fps 20 gebakt, szene stand nach einem
    # weg-A-render wieder auf Blenders default 24 -> 41 frames in 1,708 s statt 2,05 s,
    # 20 % zu schnell. Die bake-fps hinterlegt film_camera_import auf der kamera.
    bake_fps = cam.get("bbs_fps_bake")
    szene_fps = scene.render.fps / (scene.render.fps_base or 1.0)
    if fps is None:
        fps = float(bake_fps) if bake_fps else szene_fps
        if bake_fps and abs(float(bake_fps) - szene_fps) > 1e-6:
            print("[bbs-render] WARNUNG: szene steht auf %.3f fps, die film-keys wurden bei %.3f fps "
                  "gebakt — gemuxt wird mit %.3f (frame == key), sonst laeuft der film falsch schnell"
                  % (szene_fps, float(bake_fps), fps))
        elif not bake_fps:
            print("[bbs-render] hinweis: BBS_FilmCam traegt keine bake-fps (aelterer import) — "
                  "mux mit szenen-fps %.3f" % szene_fps)

    # OFFLINE-BEWEIS-ZAEHLER: der render darf NICHTS senden. _seq_out zaehlt jede
    # ausgehende nachricht (state.py) — vorher/nachher-vergleich unten. Lesen ist ok
    # (state.py-konvention verbietet nur schreiben/lokal-binden von werten).
    seq_before = getattr(state, "_seq_out", None)

    n_total = end - start + 1
    _status = "rendering %d frames..." % n_total
    snap = _snapshot_render_settings(scene)
    frame_secs = []
    missing = []
    # ⛔ WEG B DARF NICHT DURCH DEN WEG-A-COMPOSITE LAUFEN (auftrag REDACTED §4/§5). Nach
    # einem weg-A-render haengt BBS_Model_Composite an der szene: BBS-hintergrund-PNG plus
    # modell-layer. render_film rendert dann NICHT die blender-szene, sondern mischt alte
    # BBS-bilder — gemessen 2026-09-13 (abnahme 5): tunnel und welle fehlten, das "weg-B"-
    # bild war der letzte BBS-frame. Fuer diesen render abhaengen, danach zurueck; der
    # compositor des shot-autors (jede ANDERE gruppe) bleibt unberuehrt.
    weg_a_gruppe = None
    try:
        from . import model_composite as _mc
        ng_akt = getattr(scene, "compositing_node_group", None)
        if ng_akt is not None and ng_akt.name == _mc.COMPOSITE_GROUP_NAME:
            weg_a_gruppe = ng_akt
    except Exception:   # noqa: BLE001
        pass
    if weg_a_gruppe is not None:
        print("[bbs-render] weg B: weg-A-composite '%s' fuer diesen render abgehaengt (sonst "
              "kaemen BBS-hintergrundbilder statt der blender-szene)" % weg_a_gruppe.name)
        scene.compositing_node_group = None
    try:
        _apply_eevee_preset(scene, samples)
        scene.camera = cam
        completed = frozenset(completed_frames)
        if not completed.issubset(range(start, end + 1)):
            raise ValueError('Completed frames are outside the requested range')
        for f in range(start, end + 1):
            if cancelled is not None and cancelled():
                raise RenderCancelled('Cancelled before frame %d' % f)
            t0 = time.time()
            path = os.path.join(out_dir, (FRAME_PATTERN % f) + '.png')
            if f not in completed:
                scene.frame_set(f)
                scene.render.filepath = os.path.join(out_dir, FRAME_PATTERN % f)
                if bpy.ops.render.render(write_still=True) != {'FINISHED'}:
                    raise RuntimeError('Native render did not finish frame %d' % f)
            if not os.path.isfile(path) or os.path.getsize(path) == 0:
                raise RuntimeError('Native frame is missing or empty: ' + path)
            dt = time.time() - t0
            frame_secs.append(dt)
            if progress is not None:
                progress(f, n_total, path, f in completed)
            print("[bbs-render] frame %d/%d (%.2fs)" % (f - start + 1, n_total, dt))
    finally:
        _restore_render_settings(scene, snap)
        if weg_a_gruppe is not None:
            scene.compositing_node_group = weg_a_gruppe

    for f in range(start, end + 1):
        if not os.path.isfile(os.path.join(out_dir, (FRAME_PATTERN % f) + ".png")):
            missing.append(f)

    seq_after = getattr(state, "_seq_out", None)
    if seq_before is not None and seq_after != seq_before:
        # Unexpected output violates the read-only render contract.
        print("[bbs-render] WARNUNG: waehrend des renders gingen %s nachrichten raus "
              "(_seq_out %s->%s) — offline-render darf NICHTS senden"
              % (seq_after - seq_before, seq_before, seq_after))

    if cancelled is not None and cancelled():
        raise RenderCancelled("Cancelled before mux")
    mp4 = None
    mux_err = None
    if missing:
        _status = "render unvollstaendig: %d/%d frames fehlen (z.b. %s)" % (
            len(missing), n_total, missing[:5])
    elif mux:
        mp4, mux_err = mux_sequence(out_dir, start, n_total, fps)

    wall = time.time() - t_wall
    ok = not missing and (mp4 is not None or not mux or mux_err is None)
    if not missing:
        _status = "%d frames in %.1fs (%.2fs/frame) -> %s%s" % (
            n_total, wall, sum(frame_secs) / max(len(frame_secs), 1), out_dir,
            (" | mux: " + (os.path.basename(mp4) if mp4 else "FEHLER %s" % mux_err))
            if mux else "")
    _last_result = {
        "ok": ok, "out_dir": out_dir, "start": start, "end": end, "frames": n_total,
        "missing": missing, "mp4": mp4, "mux_error": mux_err, "fps": fps,
        "wall_s": round(wall, 3),
        "s_per_frame": round(sum(frame_secs) / max(len(frame_secs), 1), 3),
        "seq_out_delta": (None if seq_before is None else seq_after - seq_before),
    }
    print("[bbs-render] " + _status)
    return _last_result


def _mesh_fingerprint(mesh):
    """Hash actual native geometry/topology, including equal-count edits."""
    import array
    import hashlib
    import sys
    digest = hashlib.sha256()
    for label, collection, attribute, width, kind in (
            ('vertices', mesh.vertices, 'co', 3, 'f'),
            ('edges', mesh.edges, 'vertices', 2, 'i'),
            ('loops', mesh.loops, 'vertex_index', 1, 'i'),
            ('faces-start', mesh.polygons, 'loop_start', 1, 'i'),
            ('faces-size', mesh.polygons, 'loop_total', 1, 'i')):
        values = array.array(kind, [0]) * (len(collection) * width)
        collection.foreach_get(attribute, values)
        if sys.byteorder != 'little':
            values.byteswap()
        digest.update(('%s:%d:' % (label, len(collection))).encode('ascii'))
        digest.update(values.tobytes())
    return digest.hexdigest()
