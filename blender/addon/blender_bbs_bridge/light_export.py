"""PHASE 4 — LIGHTS_IMPORT-sender: Blender-LICHTER als BBS-LightForm-aktoren in den offenen film.

Der neue portierbare Blender->BBS-sync (die RUECKRICHTUNG, matrix
docs/brain/research/2026-07-18-blender-nach-bbs-sync-matrix.md §PORTIERBAR-MIT-GRENZE):
der user setzt in Blender lichter (point/sun/area/spot), dieses modul liest deren POSITION +
energie und schickt sie als LIGHTS_IMPORT an :9880. Java (LightImporter) legt pro licht EINEN
replay an, dessen form eine `LightForm` (`bbs:light`) ist, positioniert ihn ueber die x/y/z-
keyframe-kanaele des replays und spawnt ihn (ActorEntity) — LambDynLights macht den ActorEntity
dann leuchten (getLightLevelFromForm, gemessen an LambDynLightsIntegration.java). So sieht der
user seine Blender-licht-POSITIONEN als echte MC-blocklicht-beleuchtung in BBS.

⛔ DIE EHRLICHE GRENZE (Regel 0, im UI + doc + hier dokumentiert):
  BBS' `LightForm` hat NUR `enabled`(bool) + `level`(int 0-15 MC-blocklicht) — KEINE farbe, KEIN
  radius, KEINE richtung, KEIN kegel/spot. Ein Blender-licht -> BBS = ein grober HELLIGKEITS-PUNKT
  an der position, level aus der energie gemappt. Das ist KEIN 1:1-licht. Farbe/richtung/radius
  gehen NICHT und werden NICHT gefaked. Der ehrliche mehrwert: die licht-POSITIONEN + ungefaehre
  helligkeit reisen (lichter setzt man in Blender bequemer als im BBS-editor). Sun-lichter haben
  keine sinnvolle position (richtungslicht) — ihre POSITION reist trotzdem als punkt, mit
  degradations-hinweis (der user hat das empty ja irgendwo hingestellt).

WIRE (aus LightImporter/BlenderBridgeClient.dispatch gebaut, spiegel von camera_clip_write.py):
  request: {"type":"LIGHTS_IMPORT","origin":"blender","uuid":"lights","seq":N,
            "fields":{"requestId":"<hex>",
                      "lights":[{"name":"<blender-name>","level":<0-15>,
                                 "x":<bbs>,"y":<bbs>,"z":<bbs>,"enabled":<bool>}, ...],
                      "replaceManaged":true}}   # replaceManaged: alte bridge-lichter erst raeumen
  reply:   LIGHTS_IMPORT_RESULT, fields =
           {ok,film,source,created,updated,removed,total,backup,error,requestId}

KOORDINATEN (COORDINATE_CONTRACT.md, Blender->BBS):
  bbs_x = bl_x   bbs_y = bl_z   bbs_z = -bl_y     (Blender Z-up -> MC Y-up)
Position = das WELT-transform des licht-objekts (matrix_world), NICHT die lokale location — ein
licht das in einer geparenteten collection sitzt soll dort landen wo es SICHT-bar ist.

ENERGIE -> LEVEL (0-15, die ehrliche naeherung — kein physikalisch korrektes photometrie-mapping,
das BBS' level-only-form gar nicht tragen koennte):
  POINT/SPOT/AREA: Blender-`energy` ist in WATT. MC-blocklicht ist eine 0-15-stufe (15=fackel-hell
    ~voll). Eine fackel ist ~14, glowstone 15. Ein Blender-point-licht das eine kleine szene
    ausleuchtet liegt grob bei 100-1000 W. Wir mappen LOGARITHMISCH (wahrnehmung ist log, und watt
    spannen groessenordnungen): level = round(clamp(log10(max(W,1))/log10(ENERGY_FULL) * 15, 0,15)).
    ENERGY_FULL (default 1000 W) = das watt bei dem level 15 erreicht wird — als scene-property
    einstellbar (bbsbridge_light_energy_full), weil "was ist hell" szenen-abhaengig ist.
  SUN: `energy` ist in W/m^2 (bestrahlungsstaerke, voellig andere skala, ~1-10 fuer tageslicht).
    Ein sun-licht IST kein punktlicht — wir geben ihm level 15 (voll) wenn energy>=SUN_FULL_LX
    sonst linear, und markieren die degradation. (Die sonne als MC-blocklicht-punkt ist ohnehin
    eine grobe kruecke — der user will meist die POSITION sehen, nicht die photometrie.)
  Ist das licht `data.use` = ... (Blender hat kein per-licht enable-flag am data-block); wir nehmen
  das objekt: hide_render == True -> enabled=False (im render aus = in BBS aus, level bleibt aber
  gemappt fuer den fall dass der user es wieder anschaltet).

MANAGED-LICHTER (idempotenz + rueckstandslosigkeit, invariante 5):
  Jeder replay den DIESES modul anlegt bekommt java-seitig ein prefix `BBS_Light_` im label + einen
  gruppen-tag, damit ein RE-import die eigenen alten lichter findet + ersetzt statt zu duplizieren,
  und NIE einen echten user-actor anfasst. `replaceManaged=True` (default) = java raeumt zuerst alle
  `BBS_Light_`-replays des films, dann legt es die aktuelle liste neu an. Der user kann seine
  Blender-lichter also loeschen/verschieben und re-importieren — BBS spiegelt exakt den Blender-stand.

KEIN eigener socket-status-spiegel in state.py (fremdes territorium) — modul-lokales `_pending`
(camera_clip_write/actor_export/replay_anim-muster). Java echot requestId, das result-gate
vergleicht sie (ce2d139-korrelation).

KEIN UI-BUTTON in diesem modul (ui.py-anschluss = leader-/main-absprache; aufruf aus konsole/EXEC):
    from blender_bbs_bridge import light_export
    light_export.send_lights()                       # alle lichter der szene/collection
    light_export.send_lights(collection="MC_Lights") # nur aus einer collection
"""

import bpy
import math
import time
import uuid

from . import state
from .core import _send, _next_seq

# ---- mapping-konstanten (defaults; szenen-property ueberschreibt ENERGY_FULL zur laufzeit) ----
DEFAULT_ENERGY_FULL_W = 1000.0    # watt bei dem ein point/spot/area-licht level 15 erreicht
SUN_FULL_IRRADIANCE = 5.0         # W/m^2 ab dem ein sun-licht level 15 gibt (tageslicht-groessenordnung)
LIGHTS_IMPORT_TIMEOUT = 30.0

# lichter die diese collection tragen (default None = ganze szene). Als convenience — der user
# kann seine bridge-lichter in eine eigene collection legen, dann fasst der export nur die an.
DEFAULT_LIGHT_COLLECTION = None

# ---- modul-lokales pending (KEIN state.py-edit; camera_clip_write/actor_export-muster) ----
_pending = False
_deadline = 0.0
_status = "idle"
_last_result = None      # fields-dict des letzten results (gates lesen das)
_last_payload = None     # zuletzt gesendetes fields-dict (gates vergleichen dagegen)
_request_id = ""         # ce2d139-korrelation (java echot sie)


def _energy_full_w():
    """Das watt-niveau fuer level 15 — scene-property wenn gesetzt, sonst default. Als property
    weil 'was ist hell' szenen-abhaengig ist (eine innenraum-szene ist bei 200 W schon voll,
    eine aussen-szene braucht mehr). Property wird von ui.py/register angelegt (optional —
    getattr-fallback haelt das modul eigenstaendig lauffaehig ohne den ui-teil)."""
    scene = bpy.context.scene
    val = getattr(scene, "bbsbridge_light_energy_full", None)
    try:
        val = float(val)
    except (TypeError, ValueError):
        val = 0.0
    return val if val > 0.0 else DEFAULT_ENERGY_FULL_W


def _level_for_light(light_data, degradations):
    """Blender-licht-energie -> MC-blocklicht-level 0-15 (die ehrliche naeherung, s. modul-header).

    `degradations` (set) sammelt ehrliche verluste fuer das send-log — NICHTS wird still
    geschluckt (actors.py/actor_export.py-haltung). Farbe/radius/richtung tauchen hier NICHT auf,
    weil sie nicht mal versucht werden (die form kann sie nicht halten) — sie werden EINMALIG
    im send-log als generische grenze vermerkt, nicht pro licht."""
    energy = float(getattr(light_data, "energy", 0.0) or 0.0)
    ltype = getattr(light_data, "type", 'POINT')

    if ltype == 'SUN':
        # W/m^2, voellig andere skala als watt — linear gegen SUN_FULL_IRRADIANCE.
        frac = energy / SUN_FULL_IRRADIANCE if SUN_FULL_IRRADIANCE > 0 else 1.0
        degradations.add("SUN-licht: richtungslicht als punkt an der empty-position, "
                         "energie(W/m^2) linear gegen %.1f -> level" % SUN_FULL_IRRADIANCE)
    else:
        # POINT/SPOT/AREA: watt, logarithmisch (wahrnehmung + watt spannt groessenordnungen).
        full = _energy_full_w()
        # log10(1)=0 -> ganz dunkle lichter fallen sauber auf 0; log10(full) ist der 15-punkt.
        denom = math.log10(full) if full > 1.0 else 1.0
        frac = math.log10(max(energy, 1.0)) / denom if denom > 0 else 0.0
        if ltype == 'SPOT':
            degradations.add("SPOT-licht: kegel/richtung reist nicht (LightForm ist punkt), "
                             "nur position+helligkeit")
        elif ltype == 'AREA':
            degradations.add("AREA-licht: flaeche/groesse reist nicht (LightForm ist punkt), "
                             "nur position+helligkeit")

    level = int(round(max(0.0, min(1.0, frac)) * 15.0))
    return max(0, min(15, level))


def _iter_lights(collection=None):
    """Alle licht-OBJEKTE (obj.type=='LIGHT') der szene bzw. einer benannten collection.

    Objekt-ebene (nicht data-ebene): position kommt vom objekt-welt-transform, und ein data-block
    kann von mehreren objekten geteilt werden — wir wollen pro PLATZIERUNG ein BBS-licht."""
    if collection is not None:
        coll = bpy.data.collections.get(collection)
        source = (coll.all_objects if coll is not None else [])
    else:
        source = bpy.context.scene.objects
    return [o for o in source if getattr(o, "type", None) == 'LIGHT'
            and o.data is not None and not _is_imported_light(o)]


def _is_imported_light(obj):
    """Generated previews are not independently authored outbound lights.

    The parent check also recognizes scenes saved before the ownership tag existed.
    Names alone never establish ownership of a user's light.
    """
    if obj.get("bbs_light_owner"):
        return True
    parent = obj.parent
    while parent is not None:
        if parent.get("bbs_uuid") and parent.get("bbs_form_type") == "bbs:light":
            return True
        parent = parent.parent
    return False


def build_payload(collection=None, replace_managed=True):
    """Das LIGHTS_IMPORT-fields-dict bauen, ohne zu senden (gates inspizieren es so).

    Gibt (fields, degradations, reason) zurueck; fields=None wenn nichts zu senden (grund in
    `reason`). Position = matrix_world-translation (COORDINATE_CONTRACT.md Blender->BBS)."""
    if collection is None:
        collection = DEFAULT_LIGHT_COLLECTION

    lights = []
    degradations = set()
    if collection is not None and bpy.data.collections.get(collection) is None:
        return None, degradations, "light collection not found: %s" % collection
    for obj in _iter_lights(collection):
        loc = obj.matrix_world.to_translation()
        bbs_x = loc.x
        bbs_y = loc.z      # Blender-Z ist MC-hoehe
        bbs_z = -loc.y     # Blender-Y negiert ist MC-Z
        level = _level_for_light(obj.data, degradations)
        # hide_render == True -> im Blender-render aus -> in BBS aus (level bleibt fuer spaeteres
        # wieder-anschalten gemappt; enabled ist der schalter, level die helligkeit).
        enabled = not bool(getattr(obj, "hide_render", False))
        lights.append({
            "name": obj.name,
            "level": level,
            "x": round(bbs_x, 6),
            "y": round(bbs_y, 6),
            "z": round(bbs_z, 6),
            "enabled": enabled,
        })

    if not lights and not replace_managed:
        where = ("collection '%s'" % collection) if collection else "der szene"
        return None, degradations, "keine Blender-lichter in %s gefunden" % where

    # Die generische form-grenze EINMAL vermerken (nicht pro licht) — der user muss wissen, dass
    # farbe/radius/richtung prinzipbedingt nicht reisen (LightForm = enabled+level, kein bug).
    degradations.add("LightForm-grenze: NUR position + helligkeit(level 0-15) reisen — "
                     "farbe/radius/richtung/kegel gehen NICHT (BBS-form kann sie nicht halten)")

    fields = {"lights": lights, "replaceManaged": bool(replace_managed)}
    return fields, degradations, None


def send_lights(collection=None, replace_managed=True):
    """Blender-lichter als LIGHTS_IMPORT nach BBS schicken (Blender->BBS-rueckweg, phase 4).

    `collection`: optionaler collection-name (None = ganze szene / DEFAULT_LIGHT_COLLECTION).
    `replace_managed`: True (default) = java raeumt zuerst die alten BBS_Light_-replays und legt
      die aktuelle liste neu an (idempotenter spiegel des Blender-stands). False = nur anlegen/
      updaten, alte bridge-lichter die in Blender geloescht wurden bleiben in BBS stehen.

    True = request raus (antwort async ueber tick.py -> _handle_lights_import_result, status in
    light_export._status). False = nicht gesendet (grund in _status)."""
    global _pending, _deadline, _status, _last_payload, _request_id
    if not state._connected or state._sock is None:
        _status = "not connected"
        return False
    if _pending:
        # ein fenster zur zeit — sonst ist das result nicht mehr zuordenbar (requestId-gate haelt
        # zwar fremde ab, aber zwei eigene gleichzeitig waeren nicht unterscheidbar).
        _status = "import already pending"
        return False
    # filmwechsel-schutz wie actor_export: ginge der write in den falschen film, lehnt java ihn
    # zwar ab (LightImporter spiegelt ActorImporters film-guard), aber wir sparen den roundtrip
    # und sagen WARUM. Erst neu binden (reconnect) hebt die pause auf.
    if state.film_switch_detected:
        _status = ("filmwechsel: gebunden an '%s', BBS zeigt '%s' — erst neu binden (reconnect)"
                   % (state.bound_film, state.film_switch_other))
        print("[bbs-bridge] LIGHTS_IMPORT geblockt (filmwechsel): %s" % _status)
        return False

    fields, degradations, reason = build_payload(collection=collection,
                                                 replace_managed=replace_managed)
    if fields is None:
        _status = reason or "nothing to send"
        print("[bbs-bridge] LIGHTS_IMPORT: %s" % _status)
        return False

    # den write mit der gebundenen film-id taggen (datenverlust-seal, spiegel actor_export).
    # java behandelt "" als "kein tag" = schreibt in den offenen film (backward-compatible).
    if state.bound_film:
        fields["film"] = state.bound_film

    _request_id = uuid.uuid4().hex
    fields["requestId"] = _request_id
    _last_payload = fields
    _pending = True
    _deadline = time.time() + LIGHTS_IMPORT_TIMEOUT
    _status = "sent %d lights" % len(fields["lights"])
    _send({"type": "LIGHTS_IMPORT", "origin": "blender", "uuid": "lights",
           "seq": _next_seq(), "fields": fields})
    print("[bbs-bridge] LIGHTS_IMPORT sent: %d lights (replaceManaged=%s)"
          % (len(fields["lights"]), replace_managed))
    for d in sorted(degradations):
        print("[bbs-bridge] LIGHTS_IMPORT degradation: %s" % d)
    return True


def _handle_lights_import_result(fields):
    """Antwort auf UNSEREN send (tick.py ruft das nur bei _pending=True — solicited-gate). Nie
    werfen: der tick-loop darf an keinem result sterben. requestId-gate wie camera_clip_write/
    world_import (ce2d139): fremde id -> ignorieren, pending+deadline unangetastet."""
    global _pending, _status, _last_result, _request_id
    if not _pending:
        print("[bbs-bridge] unsolicited LIGHTS_IMPORT_RESULT ignored (no request pending)")
        return
    rid = fields.get("requestId") if isinstance(fields, dict) else None
    if _request_id and isinstance(rid, str) and rid and rid != _request_id:
        print("[bbs-bridge] fremdes LIGHTS_IMPORT_RESULT ignoriert (requestId %s != unser %s)"
              % (rid[:12], _request_id[:12]))
        return
    _pending = False
    _request_id = ""
    _last_result = dict(fields) if isinstance(fields, dict) else {}
    try:
        if fields.get("ok"):
            _status = ("created %s / updated %s / removed %s -> %s lights in film '%s'%s"
                       % (fields.get("created", "?"), fields.get("updated", "?"),
                          fields.get("removed", "?"), fields.get("total", "?"),
                          fields.get("film", "?"),
                          (" | backup %s" % fields.get("backup")) if fields.get("backup") else ""))
        elif fields.get("filmMismatch"):
            # java hat den write verworfen weil BBS inzwischen einen anderen film offen hat
            # (spiegel ActorImporter.filmMismatch). Lokalen pause-zustand setzen, damit weitere
            # sends geblockt werden (reconnect/rebind hebt es auf).
            open_film = str(fields.get("film") or "")
            state.film_switch_detected = True
            state.film_switch_other = open_film
            _status = ("film mismatch: write war fuer '%s', BBS zeigt '%s' — verworfen "
                       "(sync pausiert)" % (state.bound_film, open_film))
        else:
            _status = "import failed: %s" % (fields.get("error") or "unknown")
        print("[bbs-bridge] LIGHTS_IMPORT result: %s" % _status)
    except Exception as e:   # noqa: BLE001 — timer-regel
        _status = "result unlesbar: %s" % e
        print("[bbs-bridge] LIGHTS_IMPORT result unlesbar:", e)


def _check_timeout():
    """Von tick.py gerufen (neben den anderen deadlines) — ein verlorenes result darf das
    pending-fenster nicht fuer immer blockieren."""
    global _pending, _status
    if _pending and time.time() > _deadline:
        _pending = False
        _status = "import timed out"
        print("[bbs-bridge] LIGHTS_IMPORT timed out (no result after %ds)"
              % LIGHTS_IMPORT_TIMEOUT)
