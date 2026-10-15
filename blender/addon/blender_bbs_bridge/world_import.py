"""PHASE 2 — welt-import (WORLD_EXPORT -> WORLD_EXPORT_RESULT -> OBJ).

MC-welt als OBJ nach Blender holen: region um die kamera anfragen, ergebnis
importieren, MC-look (nearest-neighbour texturen, alpha, biome-tint, wasser,
overlay-z-fighting-fix) drauf, viewport draufsetzen.
"""

import bpy
import mathutils
import json
import math
import os
import time

from . import state


def _camera_region_fields(cam, radius, y_min_offset, y_max_offset):
    """Würfel-region um die kamera, in MC-BLOCKkoordinaten (ganzzahlig).
    Umrechnung = das kamera-mapping aus dem header: bbs_x=bl_x, bbs_y=bl_z, bbs_z=-bl_y.
    floor() weil blöcke ganzzahlig sind (int(-3.2) würde Richtung 0 runden = falscher block)."""
    loc = cam.matrix_world.to_translation()
    cx = math.floor(loc.x)      # mc_x =  bl_x
    cy = math.floor(loc.z)      # mc_y =  bl_z   (Blenders Z ist MCs höhe)
    cz = math.floor(-loc.y)     # mc_z = -bl_y
    min_y = cy + y_min_offset
    max_y = cy + y_max_offset
    if min_y > max_y:           # falls jemand die offsets verdreht übergibt
        min_y, max_y = max_y, min_y
    min_y = max(state.MC_Y_MIN, min(state.MC_Y_MAX, min_y))
    max_y = max(state.MC_Y_MIN, min(state.MC_Y_MAX, max_y))
    return {
        "minX": cx - radius, "minY": min_y, "minZ": cz - radius,
        "maxX": cx + radius, "maxY": max_y, "maxZ": cz + radius,
    }


def _clear_world_collection():
    """Alte MC_World-collection samt objekten/meshes/materials/bildern weg, damit ein
    re-import ERSETZT statt zu stapeln. Scoped aufgeräumt: nur was an DIESER collection
    hing (kein globales purge — das würde ungenutzte user-materials mitreißen)."""
    coll = bpy.data.collections.get(state.WORLD_COLLECTION)
    if coll is None:
        return
    mats, meshes = set(), set()
    for obj in list(coll.objects):
        for slot in obj.material_slots:
            if slot.material is not None:
                mats.add(slot.material)
        if obj.data is not None:
            meshes.add(obj.data)
        bpy.data.objects.remove(obj, do_unlink=True)
    # reihenfolge: meshes VOR materials — das mesh hält die material-referenz,
    # sonst ist mat.users nie 0 und die alten materials bleiben als .001-müll liegen.
    imgs = set()
    for me in meshes:
        try:
            if me.users == 0 and isinstance(me, bpy.types.Mesh):
                bpy.data.meshes.remove(me)
        except (ReferenceError, RuntimeError):
            pass
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


def _base_color_tex(node_tree):
    """(principled, image-tex-node der die Base Color speist). Nur DIESE textur zählt
    für alpha — ein normal/roughness-map-alpha wäre der falsche kanal."""
    bsdf = None
    for n in node_tree.nodes:
        if n.type == 'BSDF_PRINCIPLED':
            bsdf = n
            break
    if bsdf is None:
        return None, None
    inp = bsdf.inputs.get("Base Color")
    if inp is not None and inp.is_linked:
        src = inp.links[0].from_node
        # Re-imports already have our tint between the base image and the BSDF.
        # OBJ can also leave a second, unused map_d image node in the material.
        if src.type == 'MIX' and src.name.startswith('BBS_Tint'):
            tint_input = src.inputs[6]
            if tint_input.is_linked:
                src = tint_input.links[0].from_node
        if src.type == 'TEX_IMAGE' and src.image is not None:
            return bsdf, src
    # fallback: genau EIN image-node im tree -> das ist zwangsläufig die base-textur
    tex = [n for n in node_tree.nodes if n.type == 'TEX_IMAGE' and n.image is not None]
    if len(tex) == 1:
        return bsdf, tex[0]
    return bsdf, None


def _set_alpha_clip(mat):
    """Use Eevee Next dithering or the available legacy alpha mode."""
    if hasattr(mat, 'surface_render_method'):
        mat.surface_render_method = 'DITHERED'
        return True
    if not hasattr(mat, "blend_method"):
        return False    # property in dieser Blender-version weg -> alpha regelt der node-tree
    try:
        items = mat.bl_rna.properties["blend_method"].enum_items.keys()
    except (KeyError, AttributeError):
        items = ()
    for cand in ('CLIP', 'HASHED', 'BLEND'):
        if cand in items:
            try:
                mat.blend_method = cand
                return True
            except TypeError:
                continue
    return False


def _offset_overlay_faces(objects, eps=0.002):
    """Z-FIGHTING-FIX (2026-07-16, am render bewiesen): MCs grass_block.json hat ZWEI exakt
    deckungsgleiche wuerfel — element 1 seiten = grass_block_side (braun, opak), element 2
    seiten = grass_block_side_overlay (nur die gras-kante, rest alpha=0 und RGB=SCHWARZ).

    Koplanar + HASHED-blend = der depth-test entscheidet zufaellig -> man sieht die schwarzen
    alpha-0-pixel des overlays statt der braunen seite dahinter: block-seiten wirken KOMPLETT
    SCHWARZ. In-game passiert das nicht, weil MCs chunk-renderer die overlay-quads sortiert
    nach der basis zeichnet; im OBJ ist diese ordnung weg.

    Fix: overlay-faces winzig (2mm) entlang ihrer normale nach aussen schieben -> eindeutig
    vorne, alpha wirkt, gras-kante gruen ueber brauner erde. Bewiesen: aus schwarzem block
    wird ein korrekter MC-grass-block.

    Gilt fuer JEDES material dessen name auf '_overlay' endet (grass_block_side_overlay,
    snowy varianten, ...) — nicht nur gras.
    """
    import bmesh

    total = 0
    for obj in objects:
        if obj.type != 'MESH' or obj.data is None:
            continue
        ov_idx = {i for i, m in enumerate(obj.data.materials)
                  if m is not None and "_overlay" in m.name}
        if not ov_idx:
            continue
        bm = bmesh.new()
        try:
            bm.from_mesh(obj.data)
            # BEIDE lookup-tables — ohne verts.ensure_lookup_table() ist v.index UNDEFINIERT
            # (oft -1). Das liess den moved-set kollabieren und crashte Blender hart beim
            # grossen welt-import (46k verts). Deshalb tracken wir jetzt das BMVert-OBJEKT
            # selbst (hashable, stabil) statt seines index — kein lookup-table-risiko mehr.
            bm.faces.ensure_lookup_table()
            bm.verts.ensure_lookup_table()
            moved = set()
            for f in bm.faces:
                if f.material_index not in ov_idx:
                    continue
                n = f.normal.copy()          # kopie: f.normal wird bei vert-move neu berechnet
                for v in f.verts:
                    # jeden vertex nur EINMAL schieben (er haengt an mehreren overlay-faces)
                    if v not in moved:
                        v.co += n * eps
                        moved.add(v)
            bm.to_mesh(obj.data)
            total += len(moved)
        finally:
            bm.free()
        obj.data.update()
    if total:
        print("[bbs-bridge] overlay-fix: %d vertices %.3f nach aussen (gegen Z-fighting/schwarze seiten)"
              % (total, eps))


def _apply_mc_look(objects):
    """DER kritische schritt: MC-texturen sind 16x16 — mit Blenders default-interpolation
    ('Linear') sehen sie verwaschen/Blender-ig aus statt pixelig. 'Closest' = nearest
    neighbour = der MC-look. Dazu alpha für glas/laub (sonst blickdicht)."""
    seen = set()
    n_tex = n_alpha = n_tint = n_alpha_fix = n_water = 0
    for obj in objects:
        for slot in obj.material_slots:
            mat = slot.material
            if mat is None or mat.name in seen:
                continue
            seen.add(mat.name)
            if not mat.use_nodes or mat.node_tree is None:
                continue
            nt = mat.node_tree
            for node in nt.nodes:
                if node.type == 'TEX_IMAGE' and node.image is not None:
                    node.interpolation = 'Closest'   # <-- der MC-look, ohne das ist alles matsch
                    n_tex += 1
            bsdf, tex = _base_color_tex(nt)
            if bsdf is None or tex is None:
                continue

            # BIOME-TINT (2026-07-16): MCs grass/laub/ranken-texturen sind GRAUSTUFEN — die
            # farbe kommt zur render-zeit vom biome-tint. Der exporter schreibt ihn als Kd ins
            # MTL (-> mat.diffuse_color), ABER Blenders OBJ-importer hängt bei vorhandenem
            # map_Kd die textur DIREKT an Base Color und ignoriert Kd komplett -> alles grau.
            # Fix: einen MULTIPLY-mix zwischen textur und Base Color hängen (textur x tint),
            # exakt das was MC beim rendern tut. Ohne das ist gras weiss/grau statt gruen.
            if _wire_tint_multiply(nt, bsdf, tex, mat.diffuse_color):
                n_tint += 1

            # WASSER: eigene behandlung (transparent statt opak) und danach FERTIG —
            # der alpha-block unten wuerde die map_d-alpha wieder dranhaengen.
            if _make_water_translucent(mat, nt, bsdf, tex):
                n_water += 1
                continue

            try:
                alpha_in = bsdf.inputs.get("Alpha")
                # Bit depth is not an alpha flag: PNG grayscale+tRNS is reported
                # as depth 16, yet Blender decodes its transparent pixels correctly.
                # Always use the decoded Alpha output (opaque RGB yields 1), never
                # map_d's Color/luminance output or a guessed depth threshold.
                if alpha_in is not None and "Alpha" in tex.outputs:
                    if not any(link.from_node == tex and link.from_socket == tex.outputs['Alpha']
                               for link in alpha_in.links):
                        for link in list(alpha_in.links):
                            nt.links.remove(link)
                        nt.links.new(tex.outputs["Alpha"], alpha_in)
                        n_alpha_fix += 1
                _set_alpha_clip(mat)
                n_alpha += 1
            except (RuntimeError, KeyError):
                pass
    print("[bbs-bridge] mc-look: %d tex->Closest, %d alpha-linked, %d tinted, %d alpha-links repariert, %d wasser transparent"
          % (n_tex, n_alpha, n_tint, n_alpha_fix, n_water))


def _make_water_translucent(mat, nt, bsdf, tex):
    """WASSER-FIX (2026-07-16): der exporter schreibt `d 1.0` fuer ALLES, also auch fuer
    wasser -> es kommt als OPAKE blaue platte an. Im spiel ist wasser durchsichtig; man
    sieht den grund. Ohne das wirkt jeder see wie eine flache blaue folie (user-befund:
    "das Wasser ist flach").

    Fix: alpha runter + etwas roughness/IOR, damit Eevee es als fluessigkeit zeichnet.
    Wir nutzen ALPHA (nicht Transmission): transmission bräuchte in Eevee screen-space-
    refraction pro material und macht bei einer 1-block-dicken flaeche mehr aerger als
    nutzen. Alpha ist der look den MC selbst hat.

    Gibt True zurueck wenn das material wasser ist.
    """
    name = mat.name.lower()
    if "water" not in name:
        return False

    alpha_in = bsdf.inputs.get("Alpha")
    if alpha_in is not None:
        # den map_d-link kappen (die wasser-textur ist opak, ihr alpha ist nutzlos)
        for lk in list(alpha_in.links):
            nt.links.remove(lk)
        alpha_in.default_value = 0.62      # MC-wasser: gut sichtbar, grund scheint durch
    if "Roughness" in bsdf.inputs:
        bsdf.inputs["Roughness"].default_value = 0.08     # spiegelnde oberflaeche
    if "IOR" in bsdf.inputs:
        bsdf.inputs["IOR"].default_value = 1.33           # wasser
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.5
    # blend: echtes alpha-blending, NICHT hashed (hashed = dithering = koerniges wasser)
    if hasattr(mat, "blend_method"):
        try:
            items = mat.bl_rna.properties["blend_method"].enum_items.keys()
            for cand in ('BLEND', 'HASHED'):
                if cand in items:
                    mat.blend_method = cand
                    break
        except (KeyError, AttributeError, TypeError):
            pass
    if hasattr(mat, "use_backface_culling"):
        mat.use_backface_culling = False   # von unten auch sichtbar
    return True


def _srgb_to_linear(c):
    """sRGB-kanal -> linear. Die offizielle sRGB-EOTF, exakt was Blender intern nutzt.

    GEMESSEN 2026-07-16: der biome-tint kommt als sRGB aus dem MTL (Kd 0.541 0.714 0.537 =
    hex 8AB689, MCs gras-gruen). Ein Socket-`default_value` ist aber LINEAR — den sRGB-wert
    direkt reinzuschreiben macht die farbe ~2x zu hell: gras wurde blass-graugruen statt satt.
    Richtig: 0.541 -> 0.254, 0.714 -> 0.467, 0.537 -> 0.250.
    (Die TEXTUR braucht das nicht — die konvertiert Blender selbst ueber colorspace='sRGB'.)
    """
    c = max(0.0, min(1.0, float(c)))
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _wire_tint_multiply(nt, bsdf, tex, diffuse_color):
    """Textur x biome-tint als MULTIPLY-mix vor Base Color haengen.

    Der OBJ-importer haengt bei vorhandenem map_Kd die textur DIREKT an Base Color und
    wirft Kd (= unser biome-tint aus dem MTL) weg. MC dagegen MULTIPLIZIERT die
    graustufen-textur mit dem tint -> gras wird gruen. Genau das bauen wir nach.

    Gibt True zurueck wenn ein tint verdrahtet wurde (weisses Kd = kein tint = skip).
    Idempotent: laeuft der import zweimal, wird nicht doppelt eingehaengt.
    """
    # weiss-check auf den ROHEN sRGB-werten (1.0 bleibt 1.0, also egal) — die konvertierung
    # danach, damit im socket ein linearer wert landet.
    r, g, b = (_srgb_to_linear(diffuse_color[i]) for i in range(3))
    # weiss (oder fast) = untinted -> textur unveraendert lassen
    if r > 0.999 and g > 0.999 and b > 0.999:
        return False

    base_in = bsdf.inputs.get("Base Color")
    if base_in is None or not base_in.is_linked:
        return False
    # schon ein mix davor? (re-import) -> nur die farbe aktualisieren
    src = base_in.links[0].from_node
    if src.type == 'MIX' and src.name.startswith("BBS_Tint"):
        src.inputs[7].default_value = (r, g, b, 1.0)
        return True

    mix = nt.nodes.new('ShaderNodeMix')
    mix.name = "BBS_Tint"
    mix.label = "BBS biome tint"
    mix.data_type = 'RGBA'
    mix.blend_type = 'MULTIPLY'
    mix.location = (tex.location.x + 220, tex.location.y)
    mix.inputs[0].default_value = 1.0            # Factor: voll multiplizieren
    nt.links.new(tex.outputs["Color"], mix.inputs[6])   # A = textur
    mix.inputs[7].default_value = (r, g, b, 1.0)        # B = tint-farbe
    nt.links.new(mix.outputs[2], base_in)               # Result -> Base Color
    return True


def _frame_objects_in_viewport(objs):
    """Den 3D-viewport auf die frisch importierte welt richten.

    WARUM DAS SEIN MUSS (user 2026-07-16: "ich sehe immernoch keine welt"): wir importieren
    in ECHTEN MC-weltkoordinaten — sein film spielt bei x≈471, also landet die welt 458
    einheiten vom nullpunkt weg. Blenders view steht auf (0,0,0) mit distance ~10. Das
    objekt ist da, sichtbar, nicht excluded — nur eben ausserhalb des bildes. Ohne diesen
    schritt sieht ein import nach "nichts passiert" aus, und man sucht den fehler im
    exporter statt in der kamera.

    clip_end wird mit angehoben: bei einer 150-einheiten-welt reicht Blenders default (1000)
    noch, bei 32 chunks (512 breit) nicht mehr -> die welt wuerde weggeclippt.
    Alles per bpy.data statt bpy.ops — ops braucht den richtigen context und knallt im timer.
    """
    meshes = [o for o in objs if o.type == 'MESH' and len(o.data.vertices) > 0]
    if not meshes:
        return
    lo = mathutils.Vector((float("inf"),) * 3)
    hi = mathutils.Vector((float("-inf"),) * 3)
    for o in meshes:
        for corner in o.bound_box:
            w = o.matrix_world @ mathutils.Vector(corner)
            lo = mathutils.Vector((min(lo[i], w[i]) for i in range(3)))
            hi = mathutils.Vector((max(hi[i], w[i]) for i in range(3)))
    mid = (lo + hi) / 2.0
    size = (hi - lo).length or 10.0
    n = 0
    for win in bpy.context.window_manager.windows:
        for area in win.screen.areas:
            if area.type != 'VIEW_3D':
                continue
            for sp in area.spaces:
                if sp.type != 'VIEW_3D':
                    continue
                sp.region_3d.view_location = mid
                sp.region_3d.view_distance = size * 0.8
                sp.clip_end = max(sp.clip_end, size * 4.0)
                n += 1
            area.tag_redraw()
    if n:
        print("[bbs-bridge] viewport -> welt @ (%.0f, %.0f, %.0f), %.0f einheiten gross"
              % (mid.x, mid.y, mid.z, size))


def _apply_animated_meta(obj_path):
    """world_meta.json fuer world_anim.apply_animated lesen. Eigener read (statt die
    rueckgabe von world_light.apply_world_meta wiederzuverwenden) weil die nur licht-
    statistik zurueckgibt, nicht das rohe manifest -- gleiche stille-no-op-konvention
    wie apply_world_meta: fehlt datei/JSON kaputt -> nichts passiert, welt-import bleibt
    voll funktional."""
    meta_path = os.path.join(os.path.dirname(obj_path), "world_meta.json")
    if not os.path.isfile(meta_path):
        return None
    try:
        with open(meta_path, "r", encoding="utf-8") as fh:
            meta = json.load(fh)
    except (OSError, ValueError) as e:
        print("[bbs-bridge] world_meta.json unlesbar (anim):", e)
        return None
    from .world_anim import apply_animated
    return apply_animated(meta, os.path.dirname(obj_path))


def _import_world_obj(obj_path, blocks="?", faces="?", status_suffix="", frame_view=True):
    """OBJ importieren + in die MC_World-collection packen + MC-look anwenden.
    Läuft im timer = main-thread. Blockt Blender bei großen regionen ein paar sekunden —
    das ist ok, der nutzer hat es angefordert. Darum start/ende-log mit dauer.

    `status_suffix` (W6-B3, task 3.2-addon): fertiger chunk-ehrlichkeits-warnhinweis aus
    `_chunk_coverage_suffix`, oder "" (default -- alter java-stand ohne die felder, oder
    region vollstaendig geladen). Wird NUR an den erfolgs-status drangehaengt, die
    fehlerpfade (import failed/produced nothing) bleiben unveraendert -- ein result ohne
    objPath erreicht diese funktion ohnehin nie."""
    t0 = time.time()
    print("[bbs-bridge] OBJ import start:", obj_path)
    before = {o.name for o in bpy.data.objects}
    try:
        # forward=-Z / up=Y sind die importer-defaults und exakt unsere kamera-inverse
        # (bl_x=x, bl_y=-z, bl_z=y) -> geometrie deckungsgleich mit der gesyncten kamera.
        # Explizit gesetzt, damit ein geänderter default das nie still verschiebt.
        bpy.ops.wm.obj_import(filepath=obj_path, forward_axis='NEGATIVE_Z', up_axis='Y')
    except (RuntimeError, TypeError) as e:
        state._world_status = "import failed"
        print("[bbs-bridge] OBJ import failed:", e)
        return
    new_objs = [o for o in bpy.data.objects if o.name not in before]
    if not new_objs:
        # A fully loaded all-air region is a valid world export: there is simply no
        # holdout geometry for Z occlusion.  The hybrid compositor already defines
        # that case as AlphaOver with occlusion off.  Keep a malformed/non-empty OBJ
        # loud, and also keep incomplete chunk coverage loud — either could hide a wall.
        try:
            empty_air = int(blocks) == 0 and int(faces) == 0 and not status_suffix
        except (TypeError, ValueError):
            empty_air = False
        if empty_air:
            _clear_world_collection()
            from . import world_delta, world_refresh
            world_delta.arm_from_meta(obj_path)
            world_refresh.accepted(automatic=not frame_view)
            dt = time.time() - t0
            state._world_status = "0 blocks / 0 faces (%.1fs)" % dt
            print("[bbs-bridge] OBJ import: fully loaded region contains only air; "
                  "no holdout geometry, occlusion stays off")
            return
        state._world_status = "import produced nothing"
        print("[bbs-bridge] OBJ import produced no objects:", obj_path)
        return
    _clear_world_collection()
    coll = bpy.data.collections.new(state.WORLD_COLLECTION)
    bpy.context.scene.collection.children.link(coll)
    for o in new_objs:
        for c in list(o.users_collection):
            c.objects.unlink(o)
        coll.objects.link(o)
    _offset_overlay_faces(new_objs)
    _apply_mc_look(new_objs)
    # Task 3.5: game-truth-licht (sonne/himmel/punkt-lichter) aus world_meta.json —
    # fehlt das manifest (älterer java-stand), ist das ein stiller no-op.
    # DEFENSIV GEWRAPPT (end-review fund 1, live bewiesen): diese funktion läuft im
    # tick-timer, und bpy.app.timers deregistriert bei JEDER entkommenen exception den
    # timer STILL (dossier-regel in tick.py). Ein kaputtes licht-/anim-setup (z.b. ein
    # korrupter strip-PNG -> TypeError tief in bpy) darf den import degradieren, NIE
    # die bridge töten — geometrie steht zu diesem zeitpunkt schon, licht/anim sind
    # veredelung. Gleiche haltung wie die actors-handler (log + weiterlaufen).
    try:
        from .world_light import apply_world_meta
        apply_world_meta(obj_path)
    except Exception as e:  # noqa: BLE001 — timer-schutz, siehe kommentar
        print("[bbs-bridge] WARN: world_light-setup fehlgeschlagen (import laeuft weiter): %s: %s"
              % (type(e).__name__, e))
    # Task animierte texturen (water/lava/fire-strips) — braucht dasselbe manifest,
    # eigener guarded read (fehlt datei/sektion = stiller no-op, dossier-konvention).
    try:
        _apply_animated_meta(obj_path)
    except Exception as e:  # noqa: BLE001 — timer-schutz, siehe kommentar oben
        print("[bbs-bridge] WARN: world_anim-setup fehlgeschlagen (import laeuft weiter): %s: %s"
              % (type(e).__name__, e))
    # Task 3.6 (live-block-delta): delta-tracker auf DIESE region armen (liest world_meta.json
    # neben der OBJ) + alte MC_World_Delta-patches raeumen (neuer export = neue baseline).
    # Sitzt bewusst HIER hinter dem solicited+requestId-gate von _handle_world_export_result:
    # nur ein EIGENER import darf das region-gate des push-consumers setzen (world_delta-doc).
    try:
        from . import world_delta
        world_delta.arm_from_meta(obj_path)
    except Exception as e:  # noqa: BLE001 — timer-schutz, siehe kommentar oben
        print("[bbs-bridge] WARN: world_delta-arm fehlgeschlagen (import laeuft weiter): %s: %s"
              % (type(e).__name__, e))
    from . import world_refresh
    world_refresh.accepted(automatic=not frame_view)
    if frame_view:
        _frame_objects_in_viewport(new_objs)
    dt = time.time() - t0
    state._world_status = "%s blocks / %s faces (%.1fs)%s" % (blocks, faces, dt, status_suffix)
    print("[bbs-bridge] OBJ import done: %d objects -> '%s' in %.2fs"
          % (len(new_objs), state.WORLD_COLLECTION, dt))


def _chunk_coverage_suffix(f):
    """Task 3.2-addon (W6-B3): chunk-ehrlichkeits-warnsuffix aus dem WORLD_EXPORT_RESULT.

    W6-B1 (java, parallel) erweitert das result um `chunksTotal`/`chunksLoaded`/
    `missingChunks` (region hatte ungeladene chunks -> stiller `ok:true` mit löchern,
    siehe welle-6-brief W6-B1). DEFENSIV gebaut: ein älterer java-stand ohne diese felder
    liefert weiterhin exakt das heutige verhalten (leerer suffix, `state._world_incomplete`
    bleibt False) -- kein crash, kein leerer warn-text, kein geratener default für fehlende
    zahlen. `chunksTotal` muss zusätzlich > 0 sein (0 waere eine leere/degenerierte region,
    kein sinnvoller nenner fuer einen bruch).

    Gibt (suffix:str, incomplete:bool) zurück. suffix ist "" wenn alles geladen ist ODER
    die felder fehlen -- der aufrufer haengt ihn einfach an den bestehenden status-string an.
    """
    total = f.get("chunksTotal")
    loaded = f.get("chunksLoaded")
    if not isinstance(total, int) or not isinstance(loaded, int) or total <= 0:
        return "", False   # felder fehlen/falscher typ -> alter java-stand, kein warn-text
    if loaded >= total:
        return "", False   # vollstaendig geladen -> keine warnung
    return (" ⚠ %d/%d chunks geladen — region näher am spieler wählen "
            "oder hinteleportieren" % (loaded, total)), True


def _handle_world_export_result(f):
    """Antwort auf unseren WORLD_EXPORT-request. Jeder fehlerpfad loggt + läuft weiter,
    nie crashen — ein kaputter export darf den tick-loop nicht killen."""
    # SOLICITED-GATE (2026-07-17, user-beschwerde #2 "welt in meiner szene gespawnt"):
    # die java-bridge BROADCASTET jedes WORLD_EXPORT_RESULT an ALLE clients — auch exports
    # die ein ANDERER client (agent-gate, zweite instanz) angefragt hat. Ohne dieses gate
    # importierte die user-instanz fremde exports ungefragt und ersetzte mc_world.
    # Gleiche haltung wie der actors-pfad (solicited vs push per state-flag).
    if not state._world_pending:
        print("[bbs-bridge] fremdes WORLD_EXPORT_RESULT ignoriert (nicht von uns angefragt)")
        return
    # REQUEST-KORRELATION (end-review fund 2, 2026-07-17): das pending-flag allein schützte
    # nur die IDLE-instanz — während unser eigener request lief (120s-fenster), war das gate
    # für das ERSTE ankommende result offen, egal wessen. Bei parallelen exports (agents +
    # user, dasselbe game, dasselbe verzeichnis) importierte die user-instanz so den fremden
    # export UND verbrauchte ihr pending; das eigene result wurde danach als "fremd" verworfen.
    # Jetzt: ui.py schickt fields.requestId (uuid4) mit, java echot sie im result — nur das
    # EIGENE result kommt durch. Fremde results lassen pending+deadline UNANGETASTET, der
    # A response without its request identity cannot authorize replacing scene geometry.
    # Older peers without correlation must time out rather than consume a foreign result.
    rid = f.get("requestId")
    if state._world_request_id and rid != state._world_request_id:
        print("[bbs-bridge] fremdes WORLD_EXPORT_RESULT ignoriert (requestId %s != unser %s, "
              "eigener request laeuft weiter)" % (str(rid)[:12], state._world_request_id[:12]))
        return
    from . import world_refresh
    automatic = world_refresh.receive(f)
    state._world_pending = False
    state._world_request_id = ""
    if automatic is False:
        return
    if not f.get("ok"):
        state._world_status = "export failed"
        print("[bbs-bridge] world export failed:", f.get("error"))
        return
    obj_path = f.get("objPath")
    if not obj_path:
        state._world_status = "result without objPath"
        print("[bbs-bridge] world export ok but no objPath in result:", f)
        return
    if not os.path.isfile(obj_path):
        state._world_status = "objPath missing"
        print("[bbs-bridge] objPath does not exist:", obj_path)
        return
    blocks = f.get("blocks", "?")
    faces = f.get("faces", "?")
    print("[bbs-bridge] world export ok: %s blocks, %s faces -> %s" % (blocks, faces, obj_path))
    suffix, incomplete = _chunk_coverage_suffix(f)
    state._world_incomplete = incomplete
    if incomplete:
        missing = f.get("missingChunks") or []
        print("[bbs-bridge] chunk coverage: %d/%d geladen, %d fehlend (z.b. %s)"
              % (f.get("chunksLoaded"), f.get("chunksTotal"), len(missing), missing[:5]))
    selected = {obj.session_uid for obj in bpy.context.selected_objects}
    active = bpy.context.view_layer.objects.active
    active_id = active.session_uid if active else None
    try:
        _import_world_obj(obj_path, blocks, faces, status_suffix=suffix, frame_view=automatic is not True)
    finally:
        if automatic is True:
            bpy.context.view_layer.update()
            for obj in bpy.context.view_layer.objects:
                if obj is None:
                    continue
                obj.select_set(obj.session_uid in selected)
                if obj.session_uid == active_id:
                    bpy.context.view_layer.objects.active = obj
