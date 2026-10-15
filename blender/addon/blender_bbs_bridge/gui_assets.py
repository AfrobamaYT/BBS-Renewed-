"""TASK 5.3a (HB-2) — GUI-asset-quelle: HUD-texturen aus dem client-jar.

Die schwester-datei zu `item_assets.py` (HB-1): item_assets baut den client-jar-
cache-loader (jar finden, entpacken, EINMALIG nach %LOCALAPPDATA% cachen), DIESES
modul setzt NUR die GUI-kategorie drauf — die HUD-sprites (hotbar, herzen, hunger,
armor, crosshair) aus `assets/minecraft/textures/gui/sprites/hud/`. KEIN eigener
jar-loader (kein doppel-loader, kein merge-konflikt in item_assets.py): wir
IMPORTIEREN item_assets und nutzen dessen public API:

    item_assets.find_client_jar()          -> str|None
    item_assets.jar_read(inner)             -> bytes|None
    item_assets.jar_names(prefix, suffix)   -> list[str]
    item_assets.extract_png_to_cache(inner) -> str|None  (lokaler PNG-pfad)
    item_assets.CACHE_DIR                    (konstante)

Verbraucher (v1): `fp_hand.py`s HUD-platzhalter (heute prozeduraler 9-streifen-
checker, FAEHIGKEITEN-register "keine echten hotbar/item-icons") wird von HB-4
(task 5.3b hud_overlay.py) durch die ECHTEN sprites hier ersetzt. Dieses modul ist
reine ASSET-QUELLE: kein tick/socket/register, wirft nie im import-pfad, ändert
keinen BBS-zustand.

=== WARUM `gui/sprites/hud/` (nicht `gui/`) ===
MC 1.20.5+ hat die alten sheet-texturen (`icons.png`/`widgets.png`) in EINZELNE
sprites unter `gui/sprites/hud/` zerlegt (jar-gemessen 1.21.1: `gui/hotbar.png`
existiert NICHT, `gui/sprites/hud/hotbar.png` schon; herzen sind einzel-PNGs unter
`gui/sprites/hud/heart/`). Der referenz-brief nennt "gui/: hotbar, herzen, ..." —
gemeint ist die HUD-kategorie, der reale pfad ist `gui/sprites/hud/`.

=== COLORSPACE (falle, world_anim end-review fund 1) ===
`img.colorspace_settings.name` NIE schreiben. HUD-sprites sind UI-grafik und werden
DIREKT angezeigt (nicht beleuchtet wie welt-texturen) → sie bleiben sRGB (loader-
default). Nicht auf 'Non-Color' setzen (das wäre für normal/data-maps) und nicht
zurückschreiben. `interpolation='Closest'` setzt der VERBRAUCHENDE node (world_import-
muster), nicht das image — hier für den collage-gate direkt beim 1:1-blit irrelevant
(integer-offsets, kein resampling).
"""

import os

# --- kuratierte GUI-element-liste -------------------------------------------
# Kategorien == die 5 brief-elemente + 2 eng verwandte HUD-bars (air/experience,
# die HB-4 fürs vollständige HUD ohnehin braucht). Jeder eintrag:
#   logischer name  ->  jar-interner pfad (relativ, ohne führenden slash)
# Alle pfade jar-VERIFIZIERT (1.21.1 clientonly). Fehlt einer zur laufzeit, fällt
# list_gui_assets() ihn als present=False aus (kein geratener default, kein throw).

_HUD = "assets/minecraft/textures/gui/sprites/hud/"

GUI_ASSETS = {
    "hotbar": {
        "hotbar": _HUD + "hotbar.png",
        "hotbar_selection": _HUD + "hotbar_selection.png",
        "hotbar_offhand_left": _HUD + "hotbar_offhand_left.png",
        "hotbar_offhand_right": _HUD + "hotbar_offhand_right.png",
        "hotbar_attack_indicator_background": _HUD + "hotbar_attack_indicator_background.png",
        "hotbar_attack_indicator_progress": _HUD + "hotbar_attack_indicator_progress.png",
    },
    "heart": {
        # container = leeres herz-fach (hintergrund), full/half = gefüllt/halb.
        "heart_container": _HUD + "heart/container.png",
        "heart_full": _HUD + "heart/full.png",
        "heart_half": _HUD + "heart/half.png",
        # verbreitetste zustands-varianten (poison/wither/frozen/absorbing) —
        # HB-4 wählt je nach kanal; hier als quelle bereitgestellt.
        "heart_hardcore_container": _HUD + "heart/container_hardcore.png",
        "heart_hardcore_full": _HUD + "heart/hardcore_full.png",
        "heart_hardcore_half": _HUD + "heart/hardcore_half.png",
        "heart_poisoned_full": _HUD + "heart/poisoned_full.png",
        "heart_poisoned_half": _HUD + "heart/poisoned_half.png",
        "heart_withered_full": _HUD + "heart/withered_full.png",
        "heart_withered_half": _HUD + "heart/withered_half.png",
        "heart_frozen_full": _HUD + "heart/frozen_full.png",
        "heart_frozen_half": _HUD + "heart/frozen_half.png",
        "heart_absorbing_full": _HUD + "heart/absorbing_full.png",
        "heart_absorbing_half": _HUD + "heart/absorbing_half.png",
        "heart_vehicle_container": _HUD + "heart/vehicle_container.png",
        "heart_vehicle_full": _HUD + "heart/vehicle_full.png",
        "heart_vehicle_half": _HUD + "heart/vehicle_half.png",
    },
    "hunger": {
        # "food" == hunger-schinken. _hunger-suffix = der zitter-zustand (hungrig).
        "food_empty": _HUD + "food_empty.png",
        "food_half": _HUD + "food_half.png",
        "food_full": _HUD + "food_full.png",
        "food_empty_hunger": _HUD + "food_empty_hunger.png",
        "food_half_hunger": _HUD + "food_half_hunger.png",
        "food_full_hunger": _HUD + "food_full_hunger.png",
    },
    "armor": {
        "armor_empty": _HUD + "armor_empty.png",
        "armor_half": _HUD + "armor_half.png",
        "armor_full": _HUD + "armor_full.png",
    },
    "crosshair": {
        "crosshair": _HUD + "crosshair.png",
        "crosshair_attack_indicator_background": _HUD + "crosshair_attack_indicator_background.png",
        "crosshair_attack_indicator_full": _HUD + "crosshair_attack_indicator_full.png",
        "crosshair_attack_indicator_progress": _HUD + "crosshair_attack_indicator_progress.png",
    },
    # eng verwandte HUD-bars (kein brief-kern, aber HB-4 braucht sie fürs volle HUD):
    "air": {
        "air": _HUD + "air.png",
        "air_bursting": _HUD + "air_bursting.png",
    },
    "experience": {
        "experience_bar_background": _HUD + "experience_bar_background.png",
        "experience_bar_progress": _HUD + "experience_bar_progress.png",
    },
}

# image-datablock-namen im Blender-file bekommen dieses präfix → kollisionsfrei mit
# item_assets (BBS_ITEM_*) und rückstandslos entfernbar (invariante 5). HB-4/CLEANUP
# räumt sie über den namens-präfix.
IMG_PREFIX = "BBS_GUI_"


def _item_assets():
    """HB-1s loader lazy holen (import-order-sicher, addon-lazy-import-konvention).
    Gibt None zurück wenn item_assets noch nicht deployt ist — jede public fn hier
    degradiert dann sauber (present=False / None) statt zu werfen."""
    try:
        from . import item_assets  # HB-1 (task 5.2b)
        return item_assets
    except Exception:  # noqa: BLE001 — modul fehlt/kaputt = quelle nicht verfügbar
        return None


def _iter_entries():
    """(category, name, inner_path) über alle GUI_ASSETS, stabile reihenfolge."""
    for category, entries in GUI_ASSETS.items():
        for name, inner in entries.items():
            yield category, name, inner


def list_gui_assets():
    """Manifest aller kuratierten GUI-assets mit präsenz-flag gegen die echte jar.

    -> list[dict(category, name, inner_path, present)]
    `present` = liegt der eintrag WIRKLICH in der jar (via item_assets.jar_names).
    Ohne item_assets-loader: present=None (unbekannt), inner_path trotzdem geliefert.
    Kein throw, kein geratener default.
    """
    ia = _item_assets()
    known = None
    if ia is not None:
        try:
            # EIN namelist-scan über das gui/-präfix, dann set-lookup (billig).
            known = set(ia.jar_names(prefix="assets/minecraft/textures/gui/"))
        except Exception:  # noqa: BLE001
            known = None
    out = []
    for category, name, inner in _iter_entries():
        present = (inner in known) if known is not None else None
        out.append({
            "category": category,
            "name": name,
            "inner_path": inner,
            "present": present,
        })
    return out


def _resolve_inner(name):
    """logischer name -> jar-interner pfad (oder None)."""
    for _cat, n, inner in _iter_entries():
        if n == name:
            return inner
    return None


def get_gui_icon(name):
    """Ein GUI-sprite als Blender-image (cache-datei via HB-1s loader, dann load).

    -> bpy.types.Image | None
    Idempotent: check_existing dedupt gleiche datei auf EINEN datablock (world_import/
    fp_arm-muster). Colorspace UNANGETASTET (sRGB-default, UI-grafik). Der Closest-
    look setzt der verbrauchende node (nicht hier). None wenn name unbekannt, loader
    fehlt, oder die jar-datei nicht extrahierbar ist — nie throw im happy-import-pfad.
    """
    import bpy
    inner = _resolve_inner(name)
    if inner is None:
        print("[gui_assets] unbekannter GUI-asset-name: %r" % (name,))
        return None
    ia = _item_assets()
    if ia is None:
        print("[gui_assets] item_assets (HB-1 loader) nicht verfügbar — GUI-quelle offline")
        return None
    try:
        local = ia.extract_png_to_cache(inner)
    except Exception as exc:  # noqa: BLE001
        print("[gui_assets] extract_png_to_cache warf für %s: %r" % (inner, exc))
        return None
    if not local or not os.path.isfile(local):
        print("[gui_assets] konnte %s nicht aus der jar extrahieren" % (inner,))
        return None
    img_name = IMG_PREFIX + name
    img = bpy.data.images.get(img_name)
    if img is not None and os.path.abspath(bpy.path.abspath(img.filepath)) == os.path.abspath(local):
        return img
    img = bpy.data.images.load(local, check_existing=True)
    img.name = img_name  # stabiler, präfixierter name (cleanup + wiederfindung)
    # colorspace NICHT anfassen (falle) — bleibt sRGB.
    return img


def all_present_names():
    """Alle logischen namen deren jar-datei wirklich existiert (present==True).
    Wenn der loader keine präsenz-info liefert (present==None): alle namen (best effort)."""
    return [a["name"] for a in list_gui_assets() if a["present"] is not False]


# --- collage-render (gate) --------------------------------------------------

def build_collage(out_path, columns=8, pad=2, bg=(0.10, 0.10, 0.12, 1.0),
                  scale=4, names=None):
    """Alle GUI-sprites 1:1 (pixel-scharf, KEIN resampling) in EIN collage-PNG bliten.

    Das ist der GATE-beweis (task 5.3a: "collage-render aller elemente original-
    auflösung, pixel-scharf, Closest"). NICHT über den EEVEE-render-pfad (der würde
    resamplen) — direkter numpy-blit der ORIGINAL-pixel, dann integer-nearest-
    hochskalierung um faktor `scale` (nur zur sichtbarkeit; die original-auflösung
    steht im manifest daneben, das collage bleibt pixel-treu weil scale ganzzahlig
    per pixel-replikation ist, kein interpolieren).

    -> dict(status, out_path, tile_count, missing, sheet_px)   (kein throw)
    """
    import bpy
    import numpy as np

    if names is None:
        names = all_present_names()

    # 1) alle sprites als (name, HxWx4 float-array) laden.
    tiles = []
    missing = []
    max_w = 0
    max_h = 0
    for name in names:
        img = get_gui_icon(name)
        if img is None:
            missing.append(name)
            continue
        w, h = img.size
        if w == 0 or h == 0:
            missing.append(name)
            continue
        # Blender-pixels: flat RGBA, zeile 0 = UNTEN. Als HxWx4, dann vertikal
        # flippen -> zeile 0 = OBEN (natürliche bild-orientierung fürs blit).
        px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
        px = px[::-1, :, :]
        tiles.append((name, px))
        max_w = max(max_w, w)
        max_h = max(max_h, h)

    if not tiles:
        return {"status": "error", "message": "keine sprites geladen",
                "missing": missing, "out_path": out_path}

    # 2) gitter-layout: gleich große zellen (max-tile + pad), sprite oben-links
    #    in seine zelle (pixel-genau, kein zentrieren-resample).
    cell_w = max_w + pad
    cell_h = max_h + pad
    n = len(tiles)
    cols = max(1, min(columns, n))
    rows = (n + cols - 1) // cols
    sheet_w = cols * cell_w + pad
    sheet_h = rows * cell_h + pad

    sheet = np.empty((sheet_h, sheet_w, 4), dtype=np.float32)
    sheet[:, :] = np.array(bg, dtype=np.float32)

    for i, (name, px) in enumerate(tiles):
        r = i // cols
        c = i % cols
        y0 = pad + r * cell_h
        x0 = pad + c * cell_w
        h, w = px.shape[0], px.shape[1]
        # alpha-over das sprite auf den hintergrund (sprites haben transparenz).
        dst = sheet[y0:y0 + h, x0:x0 + w, :]
        a = px[:, :, 3:4]
        dst[:, :, :3] = px[:, :, :3] * a + dst[:, :, :3] * (1.0 - a)
        dst[:, :, 3:4] = a + dst[:, :, 3:4] * (1.0 - a)

    # 3) integer-nearest hochskalieren (pixel-replikation, KEIN interpolieren) —
    #    hält jeden original-pixel scharf, nur größer sichtbar.
    if scale > 1:
        sheet = np.repeat(np.repeat(sheet, scale, axis=0), scale, axis=1)

    out_h, out_w = sheet.shape[0], sheet.shape[1]

    # 4) als Blender-image schreiben (zeile 0 = oben -> zurück flippen für pixels).
    coll_name = "BBS_GUI_Collage_Tmp"
    old = bpy.data.images.get(coll_name)
    if old is not None:
        bpy.data.images.remove(old)
    out_img = bpy.data.images.new(coll_name, width=out_w, height=out_h, alpha=True)
    flat = sheet[::-1, :, :].reshape(-1)
    out_img.pixels[:] = flat.tolist()
    out_img.file_format = 'PNG'
    out_img.filepath_raw = out_path
    out_img.save()
    bpy.data.images.remove(out_img)

    return {
        "status": "ok",
        "out_path": out_path,
        "tile_count": len(tiles),
        "missing": missing,
        "sheet_px": [out_w, out_h],
        "scale": scale,
        "cols": cols,
        "rows": rows,
    }
