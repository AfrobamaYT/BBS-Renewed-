"""Task 5.3b (HB-4) — HUD-composite: die echten MC-HUD-elemente als eigene compositor-
layer ueber die welt-szene, jedes einzeln togglebar.

ZWECK / PLATZ IN DER KETTE (plan-v2 5.3b, der integrator):
  fp_hand.py (task 5.1) baute die compositing-BUEHNE: eine node-GRUPPE
  `BBS_FP_Composite` auf der WELT-szene (`scene.compositing_node_group`), in der
  `Render-Layers(welt)` --AlphaOver-- `Render-Layers(hand)` liegt, gefolgt von EINEM
  prozeduralen HUD-platzhalter (9-streifen-checker, `bbs_item_*`-los). DIESES modul
  ERSETZT genau diesen platzhalter durch die ECHTEN HUD-elemente aus HB-1 (item-icons)
  und HB-2 (gui-sprites), gespeist von HB-3s item- id-kanaelen — NICHT ein neues rig.

  Jedes element ist ein EIGENER compositor-layer (Image-node -> AlphaOver-node), einzeln
  togglebar ueber eine WELT-szenen-custom-prop, die den AlphaOver-`Factor` per scripted-
  driver treibt (identisches muster wie fp_hands PROP_SHOW_HAND/HUD). Reihenfolge:

    welt/hand-composite (aus fp_hand, `_N_AO_HAND`)
      --AlphaOver(crosshair-toggle)-->
      --AlphaOver(hotbar-toggle)-->     (hotbar-sprite + 9 item-icons + selektions-rahmen)
      --AlphaOver(health-toggle)-->     (herzen aus health-kanal)
      --AlphaOver(hunger-toggle)-->     (schinken aus hunger-kanal)
      --AlphaOver(armor-toggle)-->      (ruestung aus armor-punkten)
      -> NodeGroupOutput

## WARUM PRO ELEMENT EIN BILD, NICHT PRO ELEMENT EIN SCALE/TRANSLATE-NODE (5.1-falle)
  Jedes element-bild wird auf voller HUD-canvas-groesse (== welt-render-aufloesung)
  gemalt: das sprite/icon steht schon an seiner VANILLA-bildschirm-position im alpha-
  bild, der rest ist transparent. So braucht KEIN element einen Scale-/Translate-node
  (die 5.1-HUD-platzhalter-kette scale+translate entfaellt) — der compositor-layer ist
  nur `Image -> AlphaOver`. Das ist der FOV-ISOLATIONS-schutz (5.1-gate RMS=0.0): der
  welt-render ist immer der AlphaOver-BACKGROUND und wird NIE skaliert/verschoben; die
  HUD-alpha-bilder liegen nur drueber. Toggle-aus (Factor=0) laesst den background
  BYTE-IDENTISCH durch (AlphaOver Over mit factor 0 = reiner background). F1=alles-aus
  == welt ohne HUD, pixel-genau.

## MC-LOOK: gemalt in MC-SCALED-koordinaten, dann integer-nearest hoch (pixel-scharf)
  Vanilla rendert die HUD in "scaled"-koordinaten (GUI-scale). Bei 1080p/GUI-scale 4 ist
  die scaled-flaeche 480x270. Wir malen die elemente in DIESER scaled-flaeche an ihren
  exakten vanilla-pixel-offsets (InGameHud, 1.20+ stabil) und skalieren das ganze bild
  per numpy-pixel-replikation (integer-nearest, KEIN interpolieren) auf die render-
  aufloesung hoch — exakt gui_assets.build_collage's pixel-treue. So bleibt jeder
  original-sprite-pixel scharf (kein EEVEE-resample, kein blur), wie world_import's
  'Closest'.

## VANILLA-HUD-OFFSETS (decompiled InGameHud, 1.20+; scaled-koordinaten, ursprung oben-links)
  hotbar (182x22):      x = W/2 - 91,           y = H - 22           (bottom-center)
  selektions-rahmen(24): x = W/2 - 91 - 1 + s*20, y = H - 22 - 1      (s = 0..8 aktiver slot)
  item-icon (16x16):     x = W/2 - 90 + s*20 + 2, y = H - 22 + 3      (in der slot-zelle)
  herz-reihe (9x9/8px):  x = W/2 - 91,            y = H - 39, links->rechts, 10 stueck
  hunger-reihe:          x = W/2 + 91 - 9,        y = H - 39, rechts->links, 10 stueck
  ruestungs-reihe:       x = W/2 - 91,            y = herz_y - 10, links->rechts (nur wenn >0)
  crosshair (15x15):     x = W/2 - 7,             y = H/2 - 7          (bildmitte)

## KANAL-QUELLEN (HB-1/HB-2/HB-3, nie selbst im jar suchen)
  - item-icons:  item_assets.get_icon(id)  (HB-1, 5.2b)
  - gui-sprites: gui_assets.get_gui_icon(name)  (HB-2, 5.3a) — hotbar/selection/heart/food/armor/crosshair
  - hotbar-slot-ids: pro slot aus dem actor-root (HB-3, 5.2): entweder die
    `bbs_item_hotbar_slot_N`-index-fcurve@frame + `_ids`-tabelle, oder direkt die
    `bbs_items`-JSON-tabelle nachgeschlagen. `channel_id_at_frame` deckt beides ab.
  - health/hunger: numerische kanaele (`channels["health"]`/`["hunger"]`) — sie REISEN
    schon im ACTORS-payload (java KeyframeFactories.DOUBLE, ActorExporter serialisiert
    sie), werden aber von actors.py nicht als fcurve konsumiert (landen in `ignored`).
    `bind_actor` liest sie EINMALIG aus dem letzten payload-cache (state._last_actors_
    payload, von actors.py gefuellt) an den gebundenen frame und legt sie als HUD-wert
    ab. Kein actors.py-edit (HB-4-territorium ist hud_overlay + ui).

## BINDUNG (welcher actor = der POV/first-person-subjekt?)
  Der HUD gehoert zu EINEM actor (dem first-person-subjekt). `bind_actor(uuid_or_obj)`
  waehlt ihn; default = der erste actor mit `firstPerson`-flag, sonst der erste actor-
  root. Der gewaehlte uuid wird als WELT-szenen-prop `PROP_HUD_ACTOR` gemerkt, damit ein
  re-build/re-render denselben subjekt nimmt. Ohne actor: alle daten-getriebenen
  elemente (hotbar-items/health/hunger/armor) sind leer, crosshair + leere hotbar-sprite
  bleiben (statischer HUD).

## STATE / IDEMPOTENZ
  Rein lokal, KEIN tick/socket/register (wie fp_hand, bobj_check). `build_hud(scene)`
  ist idempotent: erkennt die BBS_HUD_*-nodes/-bilder per name, malt sie neu, dupliziert
  nichts. `remove_hud(scene)` raeumt NUR die BBS_HUD_*-datenbloecke + die eigenen toggle-
  props und stellt fp_hands schlichten platzhalter-layer wieder her (ruft fp_hand.build_
  rig, das den checker neu haengt). Malt bei JEDEM build aus den aktuellen kanalwerten @
  scene.frame_current — ein re-build nach einem frame-wechsel zeigt die HUD dieses frames.
"""

import json

import bpy

from . import fp_hand


# ---- namen: ALLE datenbloecke die dieses modul anlegt sind BBS_HUD_-prefixed (paket-regel,
# invariante 5: users objekte nie anfassen, alles eigene rueckstandslos entfernbar). ----

# EIN alpha-bild pro element (auf voller render-aufloesung, sprite an vanilla-position).
IMG_PREFIX = "BBS_HUD_"                        # + <element>  -> bpy.data.images-name

# compositor-nodes (innerhalb von fp_hand.COMPOSITE_GROUP_NAME) — pro element ein
# Image-node + ein AlphaOver-node, plus die driver-getriebenen toggle-props.
_NODE_IMG_PREFIX = "BBS_HUD_Img_"              # + <element>
_NODE_AO_PREFIX = "BBS_HUD_AO_"                # + <element>

# WELT-szenen-custom-props (0..1 FLOAT, wie fp_hand.PROP_SHOW_*): der aktive HUD-subjekt-
# uuid + die 5 element-toggles. In __init__.py als bpy.props registriert (single-source-
# of-truth mit den driver-data-paths hier, wie bei fp_hand).
PROP_HUD_ACTOR = "bbshud_actor_uuid"           # STRING (welcher actor = POV)
PROP_SHOW_CROSSHAIR = "bbshud_show_crosshair"
PROP_SHOW_HOTBAR = "bbshud_show_hotbar"
PROP_SHOW_HEALTH = "bbshud_show_health"
PROP_SHOW_HUNGER = "bbshud_show_hunger"
PROP_SHOW_ARMOR = "bbshud_show_armor"

#: element-name -> toggle-prop (reihenfolge = compositor-layer-stapelung, unten zuerst).
ELEMENTS = (
    ("crosshair", PROP_SHOW_CROSSHAIR),
    ("hotbar", PROP_SHOW_HOTBAR),
    ("health", PROP_SHOW_HEALTH),
    ("hunger", PROP_SHOW_HUNGER),
    ("armor", PROP_SHOW_ARMOR),
)

# GUI-scale: vanilla-default "auto" ist auf 1080p = 4 (scaled 480x270). Als WELT-szenen-
# prop editierbar (ui.py-slider); der wert bestimmt die scaled-canvas-groesse.
PROP_GUI_SCALE = "bbshud_gui_scale"
DEFAULT_GUI_SCALE = 4

# render-aufloesung der HUD-canvas == welt-render-aufloesung (der compositor mischt sie
# auf dieser groesse; fp_hand rendert 1920x1080, s. HAND_RENDER_RES — die welt-szene kann
# aber abweichen, darum lesen wir sie in build_hud aus scene.render).
_FALLBACK_RES = (1920, 1080)

# vanilla item-slot-geometrie (scaled px, InGameHud): 9 slots je 20px breit, slot-inneres
# 16x16-icon 2px vom rand. Der hotbar-sprite selbst ist 182x22.
_HOTBAR_W = 182
_HOTBAR_H = 22
_SLOTS = 9
_SLOT_STRIDE = 20
_ICON = 16
_HEART_STRIDE = 8      # herz/schinken/ruestung: 8px raster, sprite selbst 9x9
_HEART_SPRITE = 9
_ROW_ICONS = 10        # 10 herzen = 20 HP, 10 schinken = 20 hunger, 10 ruestung = 20 armor


# ============================================================================
# numpy-mal-helfer: sprites an vanilla-positionen in EINE scaled-canvas bliten
# ============================================================================

def _load_rgba(img):
    """Ein bpy-image als HxWx4 float-array (zeile 0 = OBEN, bild-orientierung).
    Blender-pixels sind flat RGBA mit zeile 0 UNTEN -> vertikal flippen. None-sicher."""
    import numpy as np
    if img is None:
        return None
    w, h = img.size
    if w == 0 or h == 0:
        return None
    # frisch gerenderte icons (block-icon) brauchen evtl. reload damit has_data steht
    # (item_assets dokumentiert den quirk) — defensiv, wirft nie.
    if not img.has_data:
        try:
            img.reload()
        except RuntimeError:
            pass
    try:
        px = np.array(img.pixels[:], dtype=np.float32)
    except (RuntimeError, ValueError):
        return None
    if px.size != w * h * 4:
        return None
    return px.reshape(h, w, 4)[::-1, :, :]


def _blit(dst, src, x, y):
    """`src` (HxWx4, oben-links-ursprung) alpha-over auf `dst` an pixel (x,y), geclippt.
    Kein resample: 1:1 pixel-kopie (MC-look). x/y sind scaled-canvas-koordinaten."""
    if src is None:
        return
    dh, dw = dst.shape[0], dst.shape[1]
    sh, sw = src.shape[0], src.shape[1]
    x, y = int(round(x)), int(round(y))
    # ziel-fenster clippen
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(dw, x + sw), min(dh, y + sh)
    if x1 <= x0 or y1 <= y0:
        return
    sx0, sy0 = x0 - x, y0 - y
    s = src[sy0:sy0 + (y1 - y0), sx0:sx0 + (x1 - x0), :]
    d = dst[y0:y1, x0:x1, :]
    a = s[:, :, 3:4]
    d[:, :, :3] = s[:, :, :3] * a + d[:, :, :3] * (1.0 - a)
    d[:, :, 3:4] = a + d[:, :, 3:4] * (1.0 - a)


def _new_canvas(cw, ch):
    import numpy as np
    return np.zeros((ch, cw, 4), dtype=np.float32)


def _gui_icon(name):
    """HB-2s gui-sprite als HxWx4 (oder None). Lazy-import (deploy-order-sicher)."""
    try:
        from . import gui_assets
    except Exception:  # noqa: BLE001
        return None
    return _load_rgba(gui_assets.get_gui_icon(name))


def _item_icon(item_id):
    """HB-1s item-icon als HxWx4 (oder None). air/leer -> None (kein icon)."""
    if not item_id or item_id in ("minecraft:air", "air"):
        return None
    try:
        from . import item_assets
    except Exception:  # noqa: BLE001
        return None
    try:
        return _load_rgba(item_assets.get_icon(item_id))
    except Exception:  # noqa: BLE001 — asset-fabrik darf den HUD-bau nie killen
        return None


# ============================================================================
# element-canvas-maler (je: scaled-canvas -> sprites an vanilla-positionen)
# ============================================================================

def _center_x(cw):
    return cw // 2


def _paint_crosshair(cw, ch):
    canvas = _new_canvas(cw, ch)
    cr = _gui_icon("crosshair")
    if cr is not None:
        sh, sw = cr.shape[0], cr.shape[1]
        _blit(canvas, cr, _center_x(cw) - sw // 2, ch // 2 - sh // 2)
    return canvas


def _paint_hotbar(cw, ch, slot_ids, active_slot):
    """Hotbar-sprite (bottom-center) + 9 item-icons + selektions-rahmen am aktiven slot."""
    canvas = _new_canvas(cw, ch)
    cx = _center_x(cw)
    left = cx - _HOTBAR_W // 2           # 91 links der mitte
    top = ch - _HOTBAR_H                 # bottom-edge

    hotbar = _gui_icon("hotbar")
    if hotbar is not None:
        _blit(canvas, hotbar, left, top)

    # item-icons in die slot-zellen (2px inset, 3px vom oberen sprite-rand — vanilla)
    for s in range(_SLOTS):
        item_id = slot_ids[s] if s < len(slot_ids) else None
        icon = _item_icon(item_id)
        if icon is None:
            continue
        ix = left + 3 + s * _SLOT_STRIDE
        iy = top + 3
        _blit(canvas, icon, ix, iy)

    # selektions-rahmen (24x24, ueberlappt die slot-zelle um je 1px) — NACH den icons,
    # damit der rahmen-rand oben liegt (vanilla-reihenfolge).
    if 0 <= active_slot < _SLOTS:
        sel = _gui_icon("hotbar_selection")
        if sel is not None:
            sw = sel.shape[1]
            # zentrum der 24px-auswahl auf das zentrum der 20px-slot-zelle
            sx = left - 1 + active_slot * _SLOT_STRIDE
            sy = top - 1
            # falls sprite breiter/schmaler: an der slot-mitte ausrichten
            sx += (_SLOT_STRIDE + 2 - sw) // 2 if sw != 24 else 0
            _blit(canvas, sel, sx, sy)
    return canvas


def _paint_stat_row(cw, ch, full_icon, half_icon, container_icon, points, y,
                    right_to_left=False):
    """Eine 10-icon-statuszeile (herzen/schinken/ruestung). `points` = anzahl halber
    icons (0..20: 2 pro voll-icon). full/half/container aus gui_assets. Vanilla:
    erst alle container (hintergrund), dann full/half drueber."""
    canvas = _new_canvas(cw, ch)
    cx = _center_x(cw)
    left = cx - _HOTBAR_W // 2           # zeilen sind an der hotbar-linkskante ausgerichtet
    points = max(0, min(2 * _ROW_ICONS, int(round(points))))

    def _x(i):
        if right_to_left:
            # hunger: rechtsbuendig an W/2+91, nach links gefuellt
            right = cx + _HOTBAR_W // 2 - _HEART_SPRITE
            return right - i * _HEART_STRIDE
        return left + i * _HEART_STRIDE

    # hintergrund-container (nur wenn vorhanden — ruestung hat kein container-sprite)
    if container_icon is not None:
        for i in range(_ROW_ICONS):
            _blit(canvas, container_icon, _x(i), y)
    # gefuellte + halbe icons
    full = points // 2
    has_half = (points % 2) == 1
    for i in range(full):
        _blit(canvas, full_icon, _x(i), y)
    if has_half and half_icon is not None and full < _ROW_ICONS:
        _blit(canvas, half_icon, _x(full), y)
    return canvas


def _paint_health(cw, ch, health):
    """`health` in HP (0..20). Herzen: container + full/half, links->rechts, ueber hotbar."""
    y = ch - 39
    return _paint_stat_row(
        cw, ch, _gui_icon("heart_full"), _gui_icon("heart_half"),
        _gui_icon("heart_container"), health, y, right_to_left=False)


def _paint_hunger(cw, ch, hunger):
    """`hunger` in punkten (0..20). Schinken: rechtsbuendig an W/2+91, rechts->links."""
    y = ch - 39
    return _paint_stat_row(
        cw, ch, _gui_icon("food_full"), _gui_icon("food_half"),
        _gui_icon("food_empty"), hunger, y, right_to_left=True)


def _paint_armor(cw, ch, armor):
    """`armor` in ruestungs-punkten (0..20). Nur gezeichnet wenn >0 (vanilla). Ueber den
    herzen. Kein container-sprite (armor_empty ist der leere-zustand, kein hintergrund)."""
    canvas = _new_canvas(cw, ch)
    if armor <= 0:
        return canvas
    y = ch - 49                          # 10px ueber der herz-reihe
    return _paint_stat_row(
        cw, ch, _gui_icon("armor_full"), _gui_icon("armor_half"),
        None, armor, y, right_to_left=False)


# ============================================================================
# kanal-abfrage: item-id / numerischer wert @ frame vom actor-root
# ============================================================================

def _actor_roots(scene):
    """Alle actor-root-objekte (MC_Actors-collection, bbs_uuid, nicht group). Wie
    ui._iter_actor_roots, aber ohne ui-import (territorium)."""
    from . import state
    coll = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if coll is None:
        return []
    out = [o for o in coll.objects if o.get("bbs_uuid") and not o.get("bbs_is_group")]
    out.sort(key=lambda o: o.name.lower())
    return out


def _pick_actor(scene):
    """Den HUD-subjekt-actor-root waehlen: erst der in PROP_HUD_ACTOR gemerkte uuid,
    sonst der erste mit firstPerson-flag, sonst der erste actor-root. None wenn keiner."""
    roots = _actor_roots(scene)
    if not roots:
        return None
    want = scene.get(PROP_HUD_ACTOR)
    if want:
        for o in roots:
            if str(o.get("bbs_uuid")) == str(want):
                return o
    for o in roots:
        if o.get("bbs_first_person") or o.get("bbs_fp"):
            return o
    return roots[0]


def channel_id_at_frame(obj, channel, frame):
    """Die item-id eines item-kanals (`hotbar_slot_1`, `item_main_hand`, ...) am `frame`.

    ZWEI wege (HB-3, actors._apply_item_channels legt beide an):
      1. INDEX-fcurve `["bbs_item_<kanal>"]` (CONSTANT) @ frame -> index; +
         `bbs_item_<kanal>_ids`-JSON-tabelle -> ids[index]. (Blender-nativ, bevorzugt.)
      2. fallback: die volle `bbs_items`-JSON-tabelle -> letzter [tick,id]-eintrag mit
         tick*tick_scale <= frame. (verlustfrei, wenn die fcurve fehlt.)
    None wenn der kanal am actor fehlt / leer / air. Wirft nie."""
    if obj is None:
        return None
    prop = "bbs_item_" + channel
    # weg 1: index-fcurve + ids-tabelle
    ids_raw = obj.get(prop + "_ids")
    if ids_raw:
        try:
            ids = json.loads(ids_raw)
        except (ValueError, TypeError):
            ids = None
        if ids:
            idx = _eval_scalar_fcurve(obj, '["%s"]' % prop, frame)
            if idx is None:
                # keine fcurve (nur 1 keyframe): der prop-wert selbst ist der index
                idx = obj.get(prop)
            try:
                i = int(round(float(idx)))
            except (TypeError, ValueError):
                i = 0
            if 0 <= i < len(ids):
                item_id = ids[i]
                return None if item_id in ("minecraft:air", "air") else item_id
    # weg 2: volle tabelle
    table_raw = obj.get("bbs_items")
    if table_raw:
        try:
            table = json.loads(table_raw)
        except (ValueError, TypeError):
            table = None
        if isinstance(table, dict):
            entries = table.get(channel)
            if entries:
                item_id = _table_id_at_frame(obj, entries, frame)
                return None if item_id in ("minecraft:air", "air") else item_id
    return None


def _table_id_at_frame(obj, entries, frame):
    """Aus einer [[tick,id],...]-tabelle die id am `frame` (stufenfunktion, letzter
    tick <= frame). tick->frame via denselben tick_scale den actors nutzt."""
    ts = _tick_scale(obj)
    best_id = None
    best_frame = None
    for e in entries:
        if not isinstance(e, (list, tuple)) or len(e) < 2:
            continue
        try:
            f = float(e[0]) * ts
        except (TypeError, ValueError):
            continue
        if f <= frame + 1e-6 and (best_frame is None or f > best_frame):
            best_frame = f
            best_id = str(e[1])
    if best_id is None and entries:
        # frame vor dem ersten key -> ersten nehmen (vanilla: item ab tick 0)
        try:
            best_id = str(entries[0][1])
        except (IndexError, TypeError):
            best_id = None
    return best_id


def _tick_scale(obj):
    scene = bpy.context.scene
    try:
        fps = scene.render.fps / (scene.render.fps_base or 1.0)
        return fps / 20.0
    except (AttributeError, ZeroDivisionError):
        return 1.0


def _eval_scalar_fcurve(obj, data_path, frame):
    """Eine skalare custom-prop-fcurve @ frame auswerten (fcurve.evaluate). None wenn
    keine fcurve. Nutzt actors._object_fcurve_collections (5.2 slotted-safe)."""
    from . import actors
    for coll in actors._object_fcurve_collections(obj):
        try:
            for fc in coll:
                if fc.data_path == data_path:
                    return fc.evaluate(frame)
        except (AttributeError, TypeError, RuntimeError):
            continue
    return None


def _active_hotbar_slot(obj, frame):
    """Der aktive hotbar-slot (0..8) am frame — aus dem `item_main_hand`-item verglichen
    mit den 9 slot-items. Vanilla haelt die main-hand == aktiver slot. Kein match -> 0."""
    main = channel_id_at_frame(obj, "item_main_hand", frame)
    if main is None:
        return 0
    for s in range(_SLOTS):
        if channel_id_at_frame(obj, "hotbar_slot_%d" % (s + 1), frame) == main:
            return s
    return 0


# ============================================================================
# numerische kanaele (health/hunger/armor) — reisen im payload, actors.py konsumiert
# sie NICHT (landen in `ignored`). Wir lesen sie aus dem letzten payload-cache.
# ============================================================================

def _numeric_channel_at_frame(uuid, channel, frame, default):
    """Wert eines numerischen kanals (`health`/`hunger`/`armor`) des actors `uuid` @ frame.

    QUELLE: der letzte ACTORS-payload, den actors.py als `state._last_actors_payload`
    ablegt (leichtgewichtiger cache, nur die roh-channels — kein neuer state-spiegel mit
    drift-risiko, wird bei jedem import ueberschrieben). Lineare interpolation zwischen
    den keyframes (tick->frame via tick_scale). Fehlt cache/kanal -> `default`."""
    from . import state
    payload = getattr(state, "_last_actors_payload", None)
    if not payload:
        return default
    replay = payload.get(str(uuid))
    if not isinstance(replay, dict):
        return default
    channels = replay.get("channels") or {}
    kfs = channels.get(channel)
    if not kfs:
        return default
    ts = _tick_scale_ctx()
    pts = []
    for kf in kfs:
        if not isinstance(kf, dict):
            continue
        try:
            f = float(kf.get("tick", 0.0)) * ts
            v = float(kf.get("value", 0.0))
        except (TypeError, ValueError):
            continue
        pts.append((f, v))
    if not pts:
        return default
    pts.sort(key=lambda p: p[0])
    if frame <= pts[0][0]:
        return pts[0][1]
    if frame >= pts[-1][0]:
        return pts[-1][1]
    for i in range(1, len(pts)):
        f0, v0 = pts[i - 1]
        f1, v1 = pts[i]
        if f0 <= frame <= f1:
            if f1 == f0:
                return v1
            t = (frame - f0) / (f1 - f0)
            return v0 + (v1 - v0) * t
    return pts[-1][1]


def _tick_scale_ctx():
    scene = bpy.context.scene
    try:
        fps = scene.render.fps / (scene.render.fps_base or 1.0)
        return fps / 20.0
    except (AttributeError, ZeroDivisionError):
        return 1.0


# ============================================================================
# canvas -> bpy-image (integer-nearest hoch, pixel-scharf)
# ============================================================================

def _canvas_to_image(name, canvas, out_w, out_h):
    """Eine scaled-canvas (HxWx4, oben-links) auf out_w x out_h hochskalieren (integer-
    nearest pixel-replikation, KEIN interpolieren — pixel-scharf) und in einen
    BBS_HUD_-image-datenblock schreiben (idempotent per name). Gibt das image."""
    import numpy as np
    ch, cw = canvas.shape[0], canvas.shape[1]
    # ganzzahliger scale-faktor der noch ins ziel passt (nearest, kein blur). Beide
    # achsen gleich, damit das aspect stimmt (canvas ist schon im ziel-aspect gewaehlt).
    sx = max(1, out_w // cw)
    sy = max(1, out_h // ch)
    s = min(sx, sy)
    big = np.repeat(np.repeat(canvas, s, axis=0), s, axis=1)
    # in ein volles out_w x out_h-bild einbetten (unten-links-buendig wie MC-HUD-raum:
    # die canvas nutzt schon oben-links, wir zentrieren horizontal + haengen unten an).
    bh, bw = big.shape[0], big.shape[1]
    full = np.zeros((out_h, out_w, 4), dtype=np.float32)
    ox = max(0, (out_w - bw) // 2)
    oy = max(0, out_h - bh)              # unten-buendig (HUD sitzt am bild-unterrand)
    x1 = min(out_w, ox + bw)
    y1 = min(out_h, oy + bh)
    full[oy:y1, ox:x1, :] = big[:y1 - oy, :x1 - ox, :]

    img = bpy.data.images.get(name)
    if img is not None and (img.size[0] != out_w or img.size[1] != out_h):
        bpy.data.images.remove(img)
        img = None
    if img is None:
        img = bpy.data.images.new(name, width=out_w, height=out_h, alpha=True)
    # Blender-pixels: zeile 0 UNTEN -> unser array (zeile 0 oben) vertikal flippen.
    img.pixels[:] = full[::-1, :, :].reshape(-1)
    img.pack()
    return img


# ============================================================================
# compositor-integration: fp_hands HUD-platzhalter-kette ERSETZEN
# ============================================================================

# fp_hands prozedurale platzhalter-nodes (die wir ausbauen und durch die element-kette
# ersetzen). Namen aus fp_hand.py (single source dort, hier gespiegelt zum entfernen).
_FP_PLACEHOLDER_NODES = (
    fp_hand._N_IMG_HUD, fp_hand._N_SCALE_HUD, fp_hand._N_TRANSLATE_HUD, fp_hand._N_AO_HUD,
)


def _remove_placeholder(ng):
    """fp_hands 4 platzhalter-nodes aus der gruppe raeumen (falls da). Der checker-image-
    datenblock (fp_hand.HUD_IMG_NAME) bleibt — fp_hand.remove_rig raeumt ihn."""
    for nm in _FP_PLACEHOLDER_NODES:
        n = ng.nodes.get(nm)
        if n is not None:
            try:
                ng.nodes.remove(n)
            except (RuntimeError, ReferenceError):
                pass


def _build_element_chain(scene, ng, images):
    """Die element-kette in die fp_hand-node-gruppe bauen: pro element Image->AlphaOver,
    gestapelt auf `_N_AO_HAND`s output, driver-getoggelt, dann in NodeGroupOutput. Ersetzt
    fp_hands `_N_AO_HUD` (der schon in NodeGroupOutput ging).

    `images` = {element: bpy.image}. Idempotent (get_or_create per name, links.new
    ersetzt vorhandene single-link-inputs)."""
    out = fp_hand._get_or_create_node(ng, fp_hand._N_OUT, 'NodeGroupOutput')
    ao_hand = ng.nodes.get(fp_hand._N_AO_HAND)   # welt+hand-composite (fp_hand baut ihn)
    if ao_hand is None:
        # sollte nie passieren (build_rig lief vorher) — dann von der welt-RLayers starten
        prev_out = ng.nodes.get(fp_hand._N_RL_WORLD)
        prev_socket = prev_out.outputs["Image"] if prev_out else None
    else:
        prev_socket = ao_hand.outputs["Image"]

    x = -100
    for element, toggle_prop in ELEMENTS:
        img_node = fp_hand._get_or_create_node(ng, _NODE_IMG_PREFIX + element,
                                               'CompositorNodeImage')
        img_node.image = images.get(element)
        img_node.location = (x, -300)

        ao = fp_hand._get_or_create_node(ng, _NODE_AO_PREFIX + element,
                                         'CompositorNodeAlphaOver')
        ao.inputs["Type"].default_value = 'Over'
        ao.inputs["Straight Alpha"].default_value = False
        ao.location = (x + 150, 0)

        if prev_socket is not None:
            ng.links.new(prev_socket, ao.inputs["Background"])
        ng.links.new(img_node.outputs["Image"], ao.inputs["Foreground"])

        # TOGGLE-driver: Factor (inputs[2]) <- welt-szenen-prop (identisch fp_hand-muster).
        fp_hand._ensure_driver(
            ng, 'nodes["%s"].inputs[2].default_value' % (_NODE_AO_PREFIX + element), 0,
            "var", [("var", "SCENE", scene, '["%s"]' % toggle_prop)])

        prev_socket = ao.outputs["Image"]
        x += 300

    # kette in den gruppen-output
    if prev_socket is not None:
        ng.links.new(prev_socket, out.inputs["Image"])
    out.location = (x + 100, 0)


# ============================================================================
# public API
# ============================================================================

def _ensure_props(scene):
    """Die eigenen WELT-szenen-props anlegen falls die register()-props (__init__.py)
    noch nicht da sind bzw. beim ersten build defaults setzen. Toggles: default 1.0 (an).
    Actor-uuid: leer (bind_actor/_pick_actor waehlt). gui-scale: default 4."""
    if PROP_SHOW_CROSSHAIR not in scene.keys():
        scene[PROP_SHOW_CROSSHAIR] = 1.0
    if PROP_SHOW_HOTBAR not in scene.keys():
        scene[PROP_SHOW_HOTBAR] = 1.0
    if PROP_SHOW_HEALTH not in scene.keys():
        scene[PROP_SHOW_HEALTH] = 1.0
    if PROP_SHOW_HUNGER not in scene.keys():
        scene[PROP_SHOW_HUNGER] = 1.0
    if PROP_SHOW_ARMOR not in scene.keys():
        scene[PROP_SHOW_ARMOR] = 1.0
    if PROP_GUI_SCALE not in scene.keys():
        scene[PROP_GUI_SCALE] = DEFAULT_GUI_SCALE
    if PROP_HUD_ACTOR not in scene.keys():
        scene[PROP_HUD_ACTOR] = ""


def bind_actor(uuid_or_obj, scene=None):
    """Den HUD-subjekt (POV-actor) festlegen. `uuid_or_obj` = ein bbs_uuid-string ODER
    ein actor-root-objekt ODER None (auto: firstPerson-flag, sonst erster). Merkt den
    uuid in PROP_HUD_ACTOR — build_hud liest ihn. Gibt den gewaehlten uuid (oder "")."""
    world_scene = scene if scene is not None else bpy.context.scene
    _ensure_props(world_scene)
    if uuid_or_obj is None:
        obj = _pick_actor(world_scene)
        uuid = str(obj.get("bbs_uuid")) if obj is not None else ""
    elif isinstance(uuid_or_obj, str):
        uuid = uuid_or_obj
    else:
        uuid = str(uuid_or_obj.get("bbs_uuid") or "")
    world_scene[PROP_HUD_ACTOR] = uuid
    return uuid


def build_hud(scene=None, frame=None):
    """Den echten HUD-composite bauen/aktualisieren — ERSETZT fp_hands prozeduralen
    checker-platzhalter durch die 5 echten element-layer (crosshair/hotbar/health/hunger/
    armor), jeder einzeln togglebar. IDEMPOTENT.

    Ablauf:
      1. sicherstellen dass fp_hands rig+compositor stehen (build_rig, idempotent) —
         wir haengen uns in DESSEN node-gruppe (BBS_FP_Composite) auf der welt-szene ein.
      2. HUD-subjekt-actor waehlen (PROP_HUD_ACTOR / firstPerson / erster).
      3. pro element ein alpha-bild @ frame malen (item-ids + health/hunger/armor aus den
         kanaelen des subjekts, sprites aus HB-1/HB-2).
      4. fp_hands platzhalter-nodes raus, die element-kette (Image->AlphaOver je element,
         driver-getoggelt) rein, in NodeGroupOutput.

    `frame` = der HUD-frame (default scene.frame_current) — ein re-build nach frame-wechsel
    zeigt die HUD DIESES frames (der compositor selbst ist statisch je bild, der HUD-
    zustand aendert sich mit dem gemalten frame). Gibt ein report-dict.
    """
    world_scene = scene if scene is not None else bpy.context.scene
    if frame is None:
        frame = world_scene.frame_current
    _ensure_props(world_scene)

    # 1. fp_hand-buehne sicherstellen (idempotent; legt hand-szene + compositor-gruppe an)
    fp_hand.build_rig(world_scene)
    ng = bpy.data.node_groups.get(fp_hand.COMPOSITE_GROUP_NAME)
    if ng is None:
        return {"status": "error", "message": "fp_hand compositor-gruppe fehlt"}

    # 2. HUD-subjekt
    actor = _pick_actor(world_scene)
    uuid = str(actor.get("bbs_uuid")) if actor is not None else ""
    if actor is not None and not world_scene.get(PROP_HUD_ACTOR):
        world_scene[PROP_HUD_ACTOR] = uuid

    # render-aufloesung (welt-szene, worauf der compositor mischt)
    try:
        out_w = world_scene.render.resolution_x or _FALLBACK_RES[0]
        out_h = world_scene.render.resolution_y or _FALLBACK_RES[1]
    except AttributeError:
        out_w, out_h = _FALLBACK_RES
    gui_scale = int(world_scene.get(PROP_GUI_SCALE, DEFAULT_GUI_SCALE)) or DEFAULT_GUI_SCALE
    # scaled-canvas = render / gui_scale (vanilla-verhaeltnis), mind. 1
    cw = max(1, out_w // gui_scale)
    ch = max(1, out_h // gui_scale)

    # 3. daten @ frame
    if actor is not None:
        slot_ids = [channel_id_at_frame(actor, "hotbar_slot_%d" % (s + 1), frame)
                    for s in range(_SLOTS)]
        active = _active_hotbar_slot(actor, frame)
        health = _numeric_channel_at_frame(uuid, "health", frame, 20.0)
        hunger = _numeric_channel_at_frame(uuid, "hunger", frame, 20.0)
        armor = _numeric_channel_at_frame(uuid, "armor", frame, 0.0)
    else:
        slot_ids = [None] * _SLOTS
        active = 0
        health, hunger, armor = 20.0, 20.0, 0.0

    # 3b. element-canvases malen + zu bildern hochskalieren
    canvases = {
        "crosshair": _paint_crosshair(cw, ch),
        "hotbar": _paint_hotbar(cw, ch, slot_ids, active),
        "health": _paint_health(cw, ch, health),
        "hunger": _paint_hunger(cw, ch, hunger),
        "armor": _paint_armor(cw, ch, armor),
    }
    images = {}
    for element, canvas in canvases.items():
        images[element] = _canvas_to_image(IMG_PREFIX + element, canvas, out_w, out_h)

    # 4. platzhalter raus, element-kette rein
    _remove_placeholder(ng)
    _build_element_chain(world_scene, ng, images)
    world_scene.use_nodes = True
    world_scene.compositing_node_group = ng

    report = {
        "status": "ok",
        "actor_uuid": uuid,
        "frame": frame,
        "slot_ids": slot_ids,
        "active_slot": active,
        "health": round(float(health), 3),
        "hunger": round(float(hunger), 3),
        "armor": round(float(armor), 3),
        "canvas_px": [cw, ch],
        "render_px": [out_w, out_h],
        "images": sorted(images.keys()),
    }
    print("[bbs-bridge] hud_overlay.build_hud: actor=%s frame=%s health=%s hunger=%s "
          "armor=%s items=%s active=%d" % (uuid or "(none)", frame, report["health"],
          report["hunger"], report["armor"],
          [i for i in slot_ids if i], active))
    return report


def remove_hud(scene=None):
    """Den HUD-composite wieder entfernen (rueckstandslos, invariante 5) und fp_hands
    schlichten checker-platzhalter wiederherstellen. Raeumt: die BBS_HUD_*-nodes aus der
    compositor-gruppe, die 5 element-bilder, die eigenen toggle/actor/scale-props. Ruft
    dann fp_hand.build_rig, das seinen platzhalter-layer neu haengt. Gibt ein report-dict.
    """
    world_scene = scene if scene is not None else bpy.context.scene
    removed = {"nodes": [], "images": [], "props": []}

    ng = bpy.data.node_groups.get(fp_hand.COMPOSITE_GROUP_NAME)
    if ng is not None:
        for element, _tp in ELEMENTS:
            for nm in (_NODE_IMG_PREFIX + element, _NODE_AO_PREFIX + element):
                n = ng.nodes.get(nm)
                if n is not None:
                    try:
                        ng.nodes.remove(n)
                        removed["nodes"].append(nm)
                    except (RuntimeError, ReferenceError):
                        pass

    for element, _tp in ELEMENTS:
        img = bpy.data.images.get(IMG_PREFIX + element)
        if img is not None:
            try:
                if getattr(img, "use_fake_user", False):
                    img.use_fake_user = False
                if getattr(img, "use_extra_user", False):
                    img.use_extra_user = False
                bpy.data.images.remove(img)
                removed["images"].append(IMG_PREFIX + element)
            except (RuntimeError, ReferenceError):
                pass

    for prop in (PROP_SHOW_CROSSHAIR, PROP_SHOW_HOTBAR, PROP_SHOW_HEALTH,
                 PROP_SHOW_HUNGER, PROP_SHOW_ARMOR, PROP_GUI_SCALE, PROP_HUD_ACTOR):
        if prop in world_scene.keys():
            try:
                del world_scene[prop]
                removed["props"].append(prop)
            except KeyError:
                pass

    # fp_hands platzhalter-layer wiederherstellen (idempotent — haengt checker neu in die
    # gruppe, damit der HUD-slot nicht leer bleibt und der show_hud-toggle wieder wirkt).
    if bpy.data.node_groups.get(fp_hand.COMPOSITE_GROUP_NAME) is not None:
        try:
            fp_hand.build_rig(world_scene)
        except Exception as e:   # noqa: BLE001
            print("[bbs-bridge] hud_overlay.remove_hud: fp_hand.build_rig fehlgeschlagen: %s" % e)

    print("[bbs-bridge] hud_overlay.remove_hud: %s" % removed)
    return removed
