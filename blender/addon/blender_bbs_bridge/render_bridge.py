"""WEG A (P7) — RENDER-consumer MIT SELBSTEINRICHTUNG: BBS stoesst an, Blender richtet sich
selbst her und rendert. Der nutzer soll NICHTS vorbereiten muessen (user-direktive 2026-08-16
"das sollte deutlich einfacher gehen"): kein fps-setzen, kein import-klicken.

ABLAUF (zustandsmaschine ueber ticks — die imports sind async request/response durch DENSELBEN
tick-loop, ein blockierendes warten im handler waere ein deadlock mit sich selbst):
  RENDER eintreffend (handle_render):
    - fps -> 20 setzen (frame==tick; WARUM 20: der film ist in MC-ticks definiert, welt-frames
      existieren nur an ganzen ticks — andere fps spaeter via sub-tick-fahrt, P7-liste)
    - film-kamera-import anfordern -> phase 'filmcam'
  fortschritt() (je tick aus tick.py):
    'filmcam' fertig -> actors-import anfordern; welt-import NUR wenn MC_World leer/fehlt
      (region um die film-kamera: scene.camera voruebergehend auf BBS_FilmCam) -> 'imports'
    'imports' fertig -> build_composite + render_composite (blockt, dokumentiert) ->
      RENDER_RESULT -> aufraeumen (scene.camera zurueck)
  jede phase hat eine deadline — haengt ein import, kommt ein EHRLICHER fehler statt stille.

KAMERA/ACTORS WERDEN IMMER FRISCH GEBAKT (nicht nur beim ersten mal): der film kann sich seit
dem letzten import geaendert haben, und ein fps-wechsel entwertet alte bakes (keys liegen auf
alten frame-positionen). Kostet ~1s, kauft korrektheit. Die WELT bleibt stehen wenn vorhanden
(schwer, fps-unabhaengig) — loeschen der MC_World-collection erzwingt einen neu-import.

WIRE (spiegel von RenderComposite.java):
  request: {"type":"RENDER","fields":{"requestId","film","start","end",
            "bbsFrameDir","bbsFramePattern","outDir","mux","width","height"}}
  reply:   RENDER_RESULT, fields = {"requestId","ok","frames","outDir","mp4","sPerFrame","error"}

Nie werfen — timer-regel; jeder pfad antwortet.
"""

import os
import re
import time
import traceback

import bpy

from . import state
from . import shot_freeze
from .core import _send, _next_seq

WELT_RADIUS = 32          # auto-welt-import um die film-kamera (v1-default)
PHASEN_TIMEOUT = 60.0     # sek je phase, dann ehrlicher fehler

# laufender auftrag oder None. Genau EINER zur zeit (zweiter RENDER -> busy-fehler).
_job = None


def _antwort(request_id, felder):
    """RENDER_RESULT senden — ein ort, damit jeder pfad (erfolg wie fehler) antwortet."""
    felder = dict(felder)
    felder["requestId"] = request_id
    _send({"type": "RENDER_RESULT", "origin": "blender", "uuid": "render",
           "seq": _next_seq(), "fields": felder})
    print("[bbs-bridge] RENDER_RESULT:", felder)


#: Was die selbsteinrichtung am szenen-zustand des SHOT-AUTORS verstellt. Jeder eintrag
#: hier MUSS in _szene_sichern/_szene_zurueck auftauchen — sonst bleibt er nach dem
#: render stehen. (Auftrag REDACTED live-link §2: "Geaenderten Szenenzustand bei Erfolg,
#: Fehler und Abbruch restaurieren"; das addon laeuft in der sitzung des users, siehe
#: CLAUDE.md-kopf "nichts an seinen objekten anfassen".)
_SZENEN_FELDER = (
    ("render", "fps"),
    ("render", "fps_base"),
    ("render", "resolution_x"),
    ("render", "resolution_y"),
    ("render", "resolution_percentage"),
)


def _szene_sichern(scene):
    """Vor dem verstellen. Gibt ein dict, das _szene_zurueck versteht."""
    snap = {}
    for block, feld in _SZENEN_FELDER:
        try:
            snap[(block, feld)] = getattr(getattr(scene, block), feld)
        except Exception:   # noqa: BLE001 — timer-regel: sichern darf nie werfen
            pass
    return snap


def _szene_zurueck(scene, snap):
    """Jedes feld einzeln — ein fehlschlag darf die uebrigen nicht mitnehmen."""
    for (block, feld), wert in (snap or {}).items():
        try:
            setattr(getattr(scene, block), feld, wert)
        except Exception:   # noqa: BLE001
            traceback.print_exc()


def _bbs_frame_groesse(fields, start):
    """(breite, hoehe) des ERSTEN BBS-hintergrundframes, oder None.

    Die WeltbildFahrt hat die frames geschrieben, BEVOR der RENDER-auftrag rausging
    (ablauf im modul-header) — sie liegen also schon da, wenn wir hier ankommen.
    Liest nur den PNG-header (24 bytes), kein bild-import: das laeuft im tick, und
    ein bpy.data.images.load je auftrag wuerde datenbloecke ansammeln."""
    d = str((fields or {}).get("bbsFrameDir") or "")
    muster = str((fields or {}).get("bbsFramePattern") or "")
    if not d or not muster:
        return None
    try:
        pfad = os.path.join(d, muster % int(start))
        with open(pfad, "rb") as f:
            kopf = f.read(24)
        # PNG: 8 byte signatur, dann IHDR-laenge(4)+typ(4), dann breite(4)+hoehe(4) big-endian
        if len(kopf) < 24 or kopf[:8] != b"\x89PNG\r\n\x1a\n" or kopf[12:16] != b"IHDR":
            return None
        breite = int.from_bytes(kopf[16:20], "big")
        hoehe = int.from_bytes(kopf[20:24], "big")
        return (breite, hoehe) if breite > 0 and hoehe > 0 else None
    except Exception:   # noqa: BLE001 — timer-regel; fallback ist das auftragsfeld
        return None


def _aufraeumen():
    """Job beenden: ALLES zurueckstellen, was die selbsteinrichtung verstellt hat.

    ⛔ Laeuft auf JEDEM ausgang — erfolg, fehler und abbruch. Vorher stand hier nur die
    kamera; fps (hart auf 20), fps_base und die drei aufloesungs-felder blieben danach
    in der szene des shot-autors stehen."""
    global _job
    if _job:
        scene = None
        try:
            scene = bpy.context.scene
        except Exception:   # noqa: BLE001
            pass
        if scene is not None:
            if _job.get("cam_vorher_gesetzt"):
                try:
                    scene.camera = _job.get("cam_vorher")
                except Exception:   # noqa: BLE001
                    traceback.print_exc()
            _szene_zurueck(scene, _job.get("szene_vorher"))
    _job = None


def handle_render(fields):
    """Inbound RENDER (tick.py-dispatch): auftrag annehmen + selbsteinrichtung starten."""
    global _job
    request_id = str((fields or {}).get("requestId") or "")

    try:
        if _job is not None:
            _antwort(request_id, {"ok": False, "error": "ein render laeuft schon (requestId=%s)"
                                  % _job.get("requestId", "?")})
            return

        start = int(fields.get("start", 0))
        end = int(fields.get("end", -1))
        if end < start:
            _antwort(request_id, {"ok": False, "error": "start/end unbrauchbar (%s..%s)" % (start, end)})
            return

        scene = bpy.context.scene

        # ⛔ ERST SICHERN, DANN VERSTELLEN — und das _job SOFORT anlegen, noch vor der
        # ersten aenderung: der except-zweig unten ruft _aufraeumen(), und der findet den
        # snapshot nur, wenn _job da ist. Wird das job erst nach der selbsteinrichtung
        # gebaut, bleibt bei einem fehler dazwischen alles verstellt stehen.
        # ⛔ FILM-ZUORDNUNG (§2): der auftrag nennt seinen film, blender ist an einen
        # gebunden. Weichen die ab, ist JEDER import eine schreiboperation in den
        # falschen film — sofort ablehnen, nicht erst beim ersten result stolpern.
        film_auftrag = str(fields.get("film") or "")
        if film_auftrag and state.bound_film and film_auftrag != state.bound_film:
            _antwort(request_id, {"ok": False, "error": "auftrag fuer film %r, blender ist an %r "
                                  "gebunden — reconnect oder film wechseln" % (film_auftrag, state.bound_film)})
            return
        eingefroren = shot_freeze.ist_eingefroren(scene)

        _job = {"requestId": request_id, "fields": dict(fields),
                "phase": "imports" if eingefroren else "filmcam",
                "deadline": time.time() + PHASEN_TIMEOUT,
                "cam_vorher": scene.camera, "cam_vorher_gesetzt": False,
                "szene_vorher": _szene_sichern(scene), "eingefroren": eingefroren,
                "film": film_auftrag or state.bound_film}

        # SELBSTEINRICHTUNG 1 (§3 zeitbasis): ausgabe-fps AUS DEM AUFTRAG. 20 = frame == tick
        # (der bisherige stand). Anderes fps: kamera/actors werden bei dieser fps importiert
        # (keys bei frame = tick*fps/20, dazwischen linear — wie das spiel zwischen zwei
        # ticks), und BBS liefert die hintergrundframes zum selben zeitpunkt ueber die
        # sub-tick-fahrt (WeltbildFahrt mit fps). Frame-nummern sind ab hier AUSGABE-frames,
        # keine ticks: bei 20 fps dasselbe, sonst round(tick*fps/20).
        fps = float(fields.get("fps") or 20.0)
        if fps <= 0 or abs(fps - round(fps)) > 1e-6:
            _antwort(request_id, {"ok": False, "error": "fps %r nicht unterstuetzt — ganzzahlig; "
                                  "fps_base (23.976 usw.) ist noch nicht verdrahtet" % fps})
            _aufraeumen()
            return
        scene.render.fps = int(round(fps))
        scene.render.fps_base = 1.0
        f_start = int(fields.get("frameStart", round(start * fps / 20.0)))
        f_end = int(fields.get("frameEnd", round(end * fps / 20.0)))
        _job["fps"] = fps
        _job["frame_start"] = f_start
        _job["frame_end"] = f_end
        if eingefroren:
            cam_alt = bpy.data.objects.get("BBS_FilmCam")
            bake = cam_alt.get("bbs_fps_bake") if cam_alt is not None else None
            if bake is not None and abs(float(bake) - fps) > 1e-6:
                _antwort(request_id, {"ok": False, "error": "shot ist eingefroren mit keys bei %.0f fps, "
                                      "auftrag will %.0f fps — auftauen und bei %.0f fps neu importieren"
                                      % (float(bake), fps, fps)})
                _aufraeumen()
                return

        # SELBSTEINRICHTUNG 2: aufloesung an die BBS-welt-frames (sonst compositet der image-node
        # versetzt).
        #
        # ⛔ DIE QUELLE IST DER FRAME SELBST, NICHT DAS AUFTRAGSFELD. Java schickt
        # `mc.getWindow().getWidth()/getHeight()` — das ist die FENSTER-groesse, und die ist
        # nicht die groesse, in der WeltbildFahrt die hintergrund-frames schreibt.
        # Gemessen 2026-09-13: auftrag sagte 1808x1018, die frames waren 1384x620. Der
        # composite rendert dann ueber die volle flaeche, der MC-hintergrund fuellt nur
        # einen ausschnitt, und die figur ragt oben heraus — sichtbar im kontaktbogen,
        # ohne eine einzige fehlermeldung.
        #
        # Zwei beschreibungen derselben groesse driften (rules/werkzeuge.md: "EINE quelle,
        # zwei verbraucher"). Der geschriebene frame IST die wahrheit, das auftragsfeld
        # bleibt fallback fuer den fall dass der frame (noch) nicht lesbar ist.
        breite = int(fields.get("width", 0))
        hoehe = int(fields.get("height", 0))
        gemessen = _bbs_frame_groesse(fields, f_start)
        if gemessen:
            if (breite, hoehe) != gemessen:
                print("[bbs-bridge] aufloesung: auftrag sagt %dx%d, der BBS-frame ist %dx%d "
                      "-> frame gewinnt" % (breite, hoehe, gemessen[0], gemessen[1]))
            breite, hoehe = gemessen
        if breite > 0 and hoehe > 0:
            scene.render.resolution_x = breite
            scene.render.resolution_y = hoehe
            scene.render.resolution_percentage = 100

        if eingefroren:
            # EINGEFRORENER SHOT (§2): NICHTS importieren. Was in der szene steht, wird
            # gerendert — dafuer muss es aber da sein, sonst rendert der composite leer.
            cam = bpy.data.objects.get("BBS_FilmCam")
            actors = bpy.data.collections.get(state.ACTORS_COLLECTION)
            fehlt = []
            if cam is None:
                fehlt.append("BBS_FilmCam")
            if actors is None or len(actors.all_objects) == 0:
                fehlt.append(state.ACTORS_COLLECTION)
            if fehlt:
                _antwort(request_id, {"ok": False, "error": "shot ist eingefroren, aber es fehlt: %s "
                                      "— auftauen oder explizit importieren" % ", ".join(fehlt)})
                _aufraeumen()
                return
            print("[bbs-bridge] RENDER angenommen: ticks %d..%d, shot EINGEFROREN — kein import"
                  % (start, end))
        else:
            # SELBSTEINRICHTUNG 3: film-kamera IMMER frisch baken (film kann sich geaendert haben).
            bpy.ops.bbsbridge.import_film_camera()
            print("[bbs-bridge] RENDER angenommen: ticks %d..%d, selbsteinrichtung laeuft" % (start, end))
    except Exception as e:   # noqa: BLE001 — timer-regel
        traceback.print_exc()
        _antwort(request_id, {"ok": False, "error": "annahme fehlgeschlagen: %s" % e})
        _aufraeumen()


def fortschritt():
    """Je tick aus tick.py gerufen: phasen vorantreiben. Nie werfen."""
    global _job
    if _job is None:
        return

    try:
        _fortschritt()
    except Exception as e:   # noqa: BLE001
        traceback.print_exc()
        _antwort(_job.get("requestId", ""), {"ok": False, "error": "selbsteinrichtung: %s" % e})
        _aufraeumen()


def _fortschritt():
    global _job
    rid = _job["requestId"]

    if time.time() > _job["deadline"]:
        _antwort(rid, {"ok": False, "error": "phase '%s' ohne antwort nach %ds — import haengt?"
                       % (_job["phase"], PHASEN_TIMEOUT)})
        _aufraeumen()
        return

    # ⛔ FILMWECHSEL WAEHREND DES JOBS (§2): actors.check_film setzt das flag, sobald ein
    # result einen anderen film nennt als den gebundenen. Ab da darf kein import mehr
    # in die szene und kein render mehr raus — der stand waere ein gemisch aus zwei filmen.
    if state.film_switch_detected:
        _antwort(rid, {"ok": False, "error": "filmwechsel waehrend des jobs: gebunden an %r, BBS "
                       "meldet %r — abgebrochen, nichts geschrieben"
                       % (state.bound_film, state.film_switch_other)})
        _aufraeumen()
        return

    if _job["phase"] == "filmcam":
        if state._film_cam_pending:
            return

        # ⛔ ERFOLG, NICHT EXISTENZ (§2, auftrags-befund): BBS_FilmCam kann von einem
        # aelteren auftrag stammen. Der beweis, dass DIESER import geklappt hat, ist
        # der status in zaehlerform — "54 frames / 378 keys -> BBS_FilmCam" — den
        # film_camera_import.py:201 nur auf dem erfolgspfad schreibt. Timeout, "import
        # failed", "film has no camera frames" faellt alles hier durch.
        cam = bpy.data.objects.get("BBS_FilmCam")
        if cam is None or not _import_erfolgreich(state._film_cam_status, "frames"):
            _antwort(rid, {"ok": False, "error": "film-kamera-import NICHT erfolgreich (status: %s)"
                           % state._film_cam_status})
            _aufraeumen()
            return

        # actors IMMER frisch (herleitung im header) ...
        bpy.ops.bbsbridge.import_actors()

        # ... welt NUR wenn sie fehlt. Region um die film-kamera am start-tick: scene.camera
        # voruebergehend auf BBS_FilmCam (import_world zentriert auf die sync-kamera).
        welt = bpy.data.collections.get(state.WORLD_COLLECTION)
        if welt is None or len(welt.all_objects) == 0:
            scene = bpy.context.scene
            scene.frame_set(int(_job.get("frame_start", _job["fields"].get("start", 0))))
            bpy.context.view_layer.update()
            _job["cam_vorher"] = scene.camera
            scene.camera = cam
            _job["cam_vorher_gesetzt"] = True
            _job["welt_angefordert"] = True
            bpy.ops.bbsbridge.import_world(radius=WELT_RADIUS)

        _job["phase"] = "imports"
        _job["deadline"] = time.time() + PHASEN_TIMEOUT
        return

    if _job["phase"] == "imports":
        if state._actors_pending or state._world_pending:
            return

        # scene.camera zurueck BEVOR gerendert wird (composite-szenen haben ihre eigene kamera).
        if _job.get("cam_vorher_gesetzt"):
            bpy.context.scene.camera = _job.get("cam_vorher")
            _job["cam_vorher_gesetzt"] = False

        # ⛔ Auch hier ERFOLG statt "pending ist weg" (§2): "export timed out" nimmt das
        # pending-flag genauso zurueck wie ein erfolg. Nur beim NICHT eingefrorenen shot —
        # eingefroren wurde nichts angefordert, die status-strings sind dann alt.
        if not _job.get("eingefroren"):
            if not _import_erfolgreich(state._actors_status, "actors"):
                _antwort(rid, {"ok": False, "error": "actor-import NICHT erfolgreich (status: %s)"
                               % state._actors_status})
                _aufraeumen()
                return
            welt = bpy.data.collections.get(state.WORLD_COLLECTION)
            if _job.get("welt_angefordert") and not _import_erfolgreich(state._world_status, "blocks"):
                _antwort(rid, {"ok": False, "error": "welt-import NICHT erfolgreich (status: %s)"
                               % state._world_status})
                _aufraeumen()
                return

        _rendern()


#: Erfolgsform der status-strings, wie die importer sie NUR auf dem erfolgspfad schreiben:
#:   film_camera_import.py:201  "%d frames / %d keys -> BBS_FilmCam"
#:   actors.py:849              "%d actors / %d models / %d keys (...)"
#:   world_import.py:484        "%s blocks / %s faces (...)"
#: Jeder fehlerpfad schreibt prosa ("export timed out", "import failed", "no actors in film",
#: "film has no camera frames", "export failed", ...) — nie zahl+wort+slash.
_ERFOLGSFORM = re.compile(r"^\d+ (frames|actors|blocks) /")


def _import_erfolgreich(status, wort):
    m = _ERFOLGSFORM.match(str(status or ""))
    return bool(m) and m.group(1) == wort and "model unavailable" not in str(status)


def _rendern():
    """Alle eingaenge da: composite bauen, rendern, antworten, aufraeumen."""
    global _job
    from . import model_composite

    fields = _job["fields"]
    rid = _job["requestId"]
    # AUSGABE-frames (§3): bei 20 fps == ticks, sonst round(tick*fps/20) — siehe handle_render.
    start = int(_job.get("frame_start", fields.get("start", 0)))
    end = int(_job.get("frame_end", fields.get("end", -1)))

    frame_dir = str(fields.get("bbsFrameDir") or "")
    pattern = str(fields.get("bbsFramePattern") or "bbs_frame_%04d.png")
    erster = os.path.join(frame_dir, pattern % start)
    if not os.path.isfile(erster):
        _antwort(rid, {"ok": False, "error": "erster welt-frame fehlt: %s" % erster})
        _aufraeumen()
        return

    welt = bpy.data.collections.get(state.WORLD_COLLECTION)
    occlusion = welt is not None and len(welt.all_objects) > 0
    model_composite.build_composite(bbs_frame=erster, occlusion=occlusion)

    # engine-wahl aus BBS (P7-seitenpanel): "EEVEE" (default) oder "CYCLES". Samples nur
    # bei CYCLES vorgeben (64 — vorhersehbare zeit statt blender-default-tausender);
    # Eevee behaelt seine szene-samples (determinismus-freundlich, preset-doku).
    engine_wunsch = str(fields.get("engine") or "EEVEE").upper()
    engine = 'CYCLES' if engine_wunsch == 'CYCLES' else 'BLENDER_EEVEE'

    # ⛔ EIGENES JOB-VERZEICHNIS (§2/§6): bisher rendertete jeder auftrag in DENSELBEN
    # ordner. Ein frame, der in diesem lauf fehlschlaegt, lag dort noch vom vorigen —
    # die vollstaendigkeitspruefung fand ihn und der lauf hiess komplett. Jetzt liegt
    # jeder auftrag unter outDir/<requestId>, mit job.json daneben.
    out_wurzel = str(fields.get("outDir") or "") or None
    out_dir = os.path.join(out_wurzel, rid[:12]) if out_wurzel else None

    res = model_composite.render_composite(
        out_dir=out_dir, start=start, end=end,
        mux=bool(fields.get("mux", True)),
        bbs_frame_dir=frame_dir, bbs_frame_pattern=pattern,
        engine=engine, samples=64 if engine == 'CYCLES' else None)

    antwort = {
        "ok": bool(res.get("ok")),
        "frames": res.get("frames", 0),
        "outDir": res.get("out_dir"),
        "mp4": res.get("mp4"),
        "sPerFrame": res.get("s_per_frame"),
        "error": res.get("error") or res.get("mux_error"),
        "film": _job.get("film"),
        "eingefroren": bool(_job.get("eingefroren")),
        "fps": _job.get("fps"),
        "frameStart": start,
        "frameEnd": end,
    }
    _manifest_schreiben(res.get("out_dir"), rid, fields, antwort, res)
    _antwort(rid, antwort)
    _aufraeumen()


def _manifest_schreiben(out_dir, rid, fields, antwort, res):
    """job.json neben die bilder: was gerendert wurde, womit, und ob es geklappt hat.
    Nie werfen — ein manifest-fehler darf kein ergebnis kippen, er wird geloggt."""
    if not out_dir:
        return
    try:
        import json
        scene = bpy.context.scene
        daten = {
            "requestId": rid, "film": antwort.get("film"), "ok": antwort.get("ok"),
            "error": antwort.get("error"),
            "start": int(fields.get("start", 0)), "end": int(fields.get("end", -1)),
            "frames": antwort.get("frames"), "missing": res.get("missing"),
            "framesWithoutBbsBackground": res.get("frames_without_bbs_bg"),
            "fps": scene.render.fps, "fpsBase": scene.render.fps_base,
            "resolution": [scene.render.resolution_x, scene.render.resolution_y,
                           scene.render.resolution_percentage],
            "engine": str(fields.get("engine") or "EEVEE"),
            "eingefroren": antwort.get("eingefroren"),
            "bbsFrameDir": str(fields.get("bbsFrameDir") or ""),
            "mp4": antwort.get("mp4"), "blend": bpy.data.filepath or None,
            "geschrieben": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }
        with open(os.path.join(out_dir, "job.json"), "w") as f:
            json.dump(daten, f, indent=2)
    except Exception:   # noqa: BLE001
        traceback.print_exc()
