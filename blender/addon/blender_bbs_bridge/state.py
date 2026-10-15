"""Modul-globals & konstanten fuer BBS Live Bridge. NUR daten, keine funktionen.

Jeder andere modul-teil importiert dieses modul via `from . import state` und
liest/schreibt `state.<name>` -- NIEMALS `from .state import <name>` fuer werte
die geschrieben werden (das bindet den namen lokal, updates aus anderen modulen
werden dann unsichtbar). Siehe die einzelnen module fuer die `global`->`state.X`
umstellung.
"""

BBS_HOST = "127.0.0.1"
BBS_PORT = 9880

# Wurzel des BBS-asset-baums = der 'assets'-namespace aus BBS' Link-format
# ("assets:models/player/alex/alex.png" -> <BBS_ASSETS_DIR>/models/player/alex/alex.png).
# Nicht hardcodable in den code-pfad gestreut: NUR hier anfassen wenn der checkout umzieht.
# 2026-08-16 Linux-Umzug: war der Windows-cml-fresh-Pfad (build/resources = nur die
# EINGEBAUTEN Mod-Assets). Jetzt der Spiel-Asset-Baum von INSTANZ 2 (bbs-neu2/run_2) —
# dort liegen auch die Nutzer-Modelle (verity_*, rigneu_*). Der Pfad ist instanzgebunden:
# andere Instanz → anderes run_N/config/bbs/assets.
BBS_ASSETS_DIR = __import__("os").environ.get("BBS_ASSETS_DIR", "")  # assets folder of your BBS instance

# ---- verbindungs-state ----
_sock = None
_recv_buf = b""
_connected = False
_peer_ready = False        # a new socket is not a completed HELLO exchange
_context_probe_next = 0.0  # read-only discovery while a reconnecting BBS loads its world
_connected_endpoint = None # endpoint of the actual socket, not editable preferences
# laufender verbindungs-thread (core._connect) — connect blockt NIE den main-thread
# (1-fps-bug 2026-07-17: create_connection im tick fror die UI bei totem BBS ein).
_connect_thread = None
_applying_remote = False   # echo-schutz: True während wir remote-daten in Blender anwenden
_seq_out = 0               # monoton steigender ausgeh-zähler
_last_seq_in = {}          # uuid -> letzte angewandte seq (LWW)
_last_cam_sent = None      # dirty-check: letzter gesendeter kamera-zustand
_cam_adopted = False       # siehe _tick: erst BBS' kamera uebernehmen, DANN erst pushen
_initial_camera_pending = True  # erster BBS-Snapshot auch bei separater Renderkamera
_status = "disconnected"

# Versioned feature negotiation. New channels are used only after both sides have named support;
# older peers remain connectable and report protocol 0 / an empty capability set.
PROTOCOL_VERSION = 2
ADDON_VERSION = "0.34"
LOCAL_CAPABILITIES = (
    "camera", "camera_owner", "playhead", "actors_pull", "actors_push", "actors_write",
    "actor_rig_property_pull_v1",
    "actor_rig_property_patch_v1",
    "actor_rig_property_lease_v1",
    "actor_rig_property_durable_v1",
    "actor_channel_patch_v1", "actor_shape_patch_v1", "actor_shape_map_patch_v1",
    "actor_shape_timeline_patch_v1", "actor_receipt_recovery_v1",
    "shape_bezier_eval_v1",
    "actor_shape_timeline_transform_v1", "actor_first_shape_v1",
    "actor_shape_timeline_durable_transform_v1",
    "actor_shape_grid_patch_v1",
    "actor_shape_grid_durable_v1",
    "actor_first_shape_durable_v1",
    "actor_scene_timing_durable_v1",
    "actor_root_scale_v1",
    "actor_shape_easing_patch_v1",
    "actor_visibility_patch_v1", "actor_visibility_durable_v1",
    "recording_live_v1", "recording_morph_preview_v1",
    "film_structure_write_v1", "markers_pull_v1", "markers_write_v1",
    "world_export", "world_delta", "film_camera",
    "camera_clip_write", "lights_write", "render_composite",
)
peer_protocol = 0
peer_version = ""
peer_project = ""
peer_engine = ""
peer_capabilities = set()

# Disposable recording-preview transport statistics. core._poll_recv coalesces a run of
# REPLAY_RECORD_FRAME messages to its newest sample before the main-thread tick sees it; reliable
# Start/Control/Stop/Actor-bake envelopes are never coalesced or reordered.
_record_frames_received = 0
_record_frames_coalesced = 0

# ---- kamera-BESITZER (owner-pro-feld-modell, LWW nur beim besitzwechsel, 2026-07-16) ----
# Genau EIN schreiber gleichzeitig: NOBODY | BBS | BLENDER. Spiegel von java
# LiveCameraOverride.Owner (com.blenderbridge, cml-fresh). Blender darf die BBS-kamera NUR
# pushen wenn dieser wert "BLENDER" ist -- hartes gate in tick.py, schritt 2. Damit kann
# Blender die kamera NIE mehr ungefragt anfassen, unabhaengig davon ob/wann die java-seite
# CAMERA_OWNER auswertet. Wechselt bei jedem (re)connect zurueck auf NOBODY (siehe
# core._connect) -- kein besitz-carry-over ueber verbindungen hinweg. Einziger schreiber
# dieses werts von der Blender-seite: ui._on_drive_camera_toggle (der "Drive BBS camera"-
# toggle); tick.py schreibt es nur auf inbound CAMERA_OWNER-nachrichten.
cam_owner = "NOBODY"

# ---- welt-import-state (Phase 2) ----
WORLD_COLLECTION = "MC_World"     # re-import ersetzt diese collection komplett
WORLD_EXPORT_TIMEOUT = 120.0      # sek ohne _RESULT -> request gilt als verloren
MC_Y_MIN, MC_Y_MAX = -64, 319     # welt-höhe 1.21 — region-clamp gegen unsinns-requests
_world_pending = False            # request raus, antwort steht aus (button grau)
_world_deadline = 0.0
_world_status = "idle"
# REQUEST-KORRELATION (end-review fund 2, 2026-07-17): das bloße pending-flag korrelierte
# nur "irgendein result kam" — die java-bridge broadcastet aber an ALLE clients, und
# WÄHREND unser eigener request lief, war das gate für FREMDE results offen (agent-export
# wird zuerst fertig -> user-instanz importiert die falsche region UND verbraucht ihr
# pending; das eigene result wird danach als "fremd" verworfen — gate invertiert). Fix:
# ui.py generiert pro request eine uuid4-hex und schickt sie als fields.requestId mit;
# java echot sie im result zurück (handleWorldExport alt-pfad, ExportCorrelation/
# sendResult streaming-pfad); world_import._handle_world_export_result akzeptiert bei
# gesetztem _world_request_id NUR das result mit DERSELBEN id — fremde lassen pending +
# deadline UNANGETASTET (timeout-pfad intakt). Result OHNE requestId-key = älterer
# java-stand -> alter pending-only-fallback (dokumentierte lücke, kein crash).
_world_request_id = ""
# Task 3.2-addon (W6-B3, chunk-ehrlichkeit): True wenn der letzte import weniger chunks
# geladen hatte als die region umfasst (chunksLoaded < chunksTotal, siehe world_import.
# _chunk_coverage_suffix) -- panel-zeile faerbt sich damit rot (icon='ERROR'). Fehlen die
# felder (aelterer java-stand ohne W6-B1s chunk-honesty-erweiterung), bleibt das False,
# byte-identisch zum verhalten vor diesem task.
_world_incomplete = False

# ---- FILM-IDENTITAET (task 1.0: datenverlust-schutz bei filmwechsel) ----
# Die STABILE persistente id des films, an den diese Blender-sitzung gebunden ist (java
# Film.getId() — BBS' eigene film-identitaet, keine erfundene uuid/sidecar: BaseManager
# .create ruft setId(id), getId()==repository-key). Gebunden beim (re)connect aus der
# HELLO-antwort (core._connect/_handle_hello) UND aus jedem ACTORS_EXPORT_RESULT/-PUSH
# (die tragen `film` schon lange). "" = noch nichts gebunden (dashboard zu / ungespeicherter
# film) — dann kein mismatch-verdacht, die naechste export-antwort bindet nach.
bound_film = ""
# True sobald ein poll/push/HELLO einen ANDEREN film meldet als `bound_film`: BBS hat den
# film gewechselt waehrend Blender verbunden war. Der live-sync PAUSIERT dann (baut keine
# actors mehr in die szene des alten films und stempelt keine writes auf den falschen film)
# bis der user bewusst neu importiert oder BBS wieder den gebundenen Film zeigt.
# Reiner schutz-zustand: die kamera/welt bleiben unberuehrt, NUR der actor-write/-sync haelt.
film_switch_detected = False
# Der zuletzt GEMELDETE fremde film (fuer die panel-warnung: "gebunden an X, BBS zeigt Y").
film_switch_other = ""

ACTORS_COLLECTION = "MC_Actors"    # re-import ersetzt diese collection komplett
ACTORS_EXPORT_TIMEOUT = 30.0       # sek ohne _RESULT -> request gilt als verloren
_actors_pending = False            # request raus, antwort steht aus (button grau)
# Task 5.3b (HB-4): der HUD-composite (hud_overlay.py) braucht die NUMERISCHEN kanaele
# health/hunger/armor pro actor am gebundenen frame. Die reisen im ACTORS-payload
# (java KeyframeFactories.DOUBLE), werden von actors.py aber NICHT als fcurve konsumiert
# (landen in `ignored`). Statt einen zweiten konsum-pfad in actors.py zu bauen, cachet
# _build_actors/_live_sync_actors den ROH-payload leichtgewichtig hierhin: {uuid: replay-
# dict}. Nur die letzte welle, wird bei jedem import ueberschrieben (kein drift-spiegel).
# Leer bis zum ersten actor-import; hud_overlay._numeric_channel_at_frame liest daraus.
_last_actors_payload = None

# ---- LIVE-ACTORS (poll statt push) ----
# BBS hat KEINEN actor-push-loop: der export ist ein pull auf anfrage. Ein push haette in
# BlenderBridgeClients tick-handler gemusst = geaenderte java-klasse = neustart. Der ist
# verboten (hotswap-regel), also pollt Blender. Gleicher effekt fuer den user, kein java.
LIVE_ACTORS_INTERVAL = 0.5         # sek zwischen polls; 20tps-BBS aendert sich nicht schneller als der user tippt
_live_next_poll = 0.0              # naechster faelliger poll (monotone zeit)
_live_fingerprint = None           # letzter kanal-hash: nur bei echter aenderung F-curves bauen
_live_status = "off"
# Unterscheidet, WER den laufenden export angefordert hat: unser poll (-> live-sync, modelle
# bleiben stehen) oder der user per button (-> vollimport mit modellen). Ohne das wuerde der
# poll den button-import kapern und die modelle nie laden.
_live_polling = False
_actors_deadline = 0.0
_actors_status = "idle"
#: {replay-uuid: (name, grund)} — actors des letzten imports, die BBS nicht 1:1 abbilden. Ein
#: einzelner nicht baubarer actor bricht den film-import nicht mehr ab, steht aber hier und in der
#: statuszeile (pose_overlays.importing setzt beides).
_import_incomplete = {}

# ---- ACTOR-PUSH (task 1.6, 2026-07-16: BBS pusht jetzt SELBST statt dass Blender pollt) ----
# java-seitiges ActorPushTracker haelt pro tick einen billigen fingerprint des offenen films
# und schickt bei aenderung ein UNSOLICITED ACTORS_EXPORT_RESULT (kein request lief -> tick.py
# erkennt das an state._actors_pending/_live_polling == False und routet es an
# actors._handle_actors_push, NIE an den vollimport-zweig). Der alte 0.5s-poll oben lebt nur
# noch als FALLBACK fuer eine aeltere BBS ohne den tracker bzw. wenn push laenger als
# PUSH_STALE_AFTER ausbleibt -- siehe tick.py schritt 3 und ui.py.
PUSH_STALE_AFTER = 3.0     # sek ohne push -> gilt als tot, tick.py faellt auf den poll zurueck
_push_last_seen = 0.0      # time.time() des letzten empfangenen pushes; 0.0 = noch nie einer
_push_count = 0            # gesamtzahl empfangener pushes (ECHO-GATE liest das vorher/nachher)

# ---- FILM-KAMERA-EXPORT (task 1.3: BBS -> Blender F-curves auf BBS_FilmCam) ----
# Anfrage/antwort-request wie WORLD_EXPORT/ACTORS_EXPORT (EIN button, EINE antwort, kein
# poll/push -- eine kamera-clip-timeline aendert sich nicht waehrend der export laeuft).
FILM_CAM_EXPORT_TIMEOUT = 30.0   # sek ohne _RESULT -> request gilt als verloren
_film_cam_pending = False        # request raus, antwort steht aus (button grau)
_film_cam_deadline = 0.0
_film_cam_status = "idle"

# ---- PLAYHEAD (task 1.4: BBS <-> Blender timeline-sync, owner-modell wie kamera) ----
# Spiegel von java com.blenderbridge.film.PlayheadSync.owner. NOBODY default: BBS' eigener
# playhead ist dann die live-preview-wahrheit, die tick.py in scene.frame_set uebernimmt.
# Lokales Scrubben/Playback leiht BLENDER den Besitz nur bis kurz nach der letzten Eingabe;
# der "Drive timeline"-Schalter haelt ihn stattdessen bis zum Ausschalten. Beide Wege senden
# beim Freigeben owner=NOBODY ohne tick, damit BBS die Gegenrichtung sofort wieder steuert.
# Bei jedem (re)connect zurueck auf NOBODY (siehe core._connect) -- kein besitz-carry-over,
# spiegelbild von cam_owner.
playhead_owner = "NOBODY"
_playhead_applying_remote = False   # echo-schutz: True waehrend wir ein inbound PLAYHEAD in
                                     # scene.frame_set anwenden (siehe tick.py) -- ohne das wuerde
                                     # der eigene frame_change_post-handler den soeben angewandten
                                     # wert sofort wieder zurueckpushen
_playhead_last_remote_frame = None  # frame_change_post kann erst NACH frame_set feuern
_playhead_apply_count = 0           # ECHO-GATE-messung (task 1.4 beweis d): zaehlt inbound-applies
_playhead_push_count = 0            # zaehlt eigene pushes (frame_change_post-handler, toggle an)
_playhead_bbs_playing = False        # zuletzt von BBS gemeldeter/angewandter playback-status
_playhead_bbs_loop = (False, 0, 0)   # enabled, tick-min, tick-max
_playhead_last_preview_sent = None

# ---- PERSISTENTE FILM-MARKER ----
_marker_revision = ""
_marker_status = "waiting for BBS marker snapshot"
_marker_apply_count = 0
_markers_applying_remote = False
_marker_lifetime = 0
