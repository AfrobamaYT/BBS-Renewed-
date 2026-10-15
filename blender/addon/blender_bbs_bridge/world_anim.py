"""Task: animierte MC-texturen (water/lava/fire/...) im Viewport/Render abspielen.

Der java-exporter schreibt neben world_meta.json einen "animated"-abschnitt:
    {"material": "minecraft_block_water_still", "frames": 32, "frametime": 3,
     "interpolate": false, "file": "textures/anim/water_still_strip.png"}
Die strip-PNG ist VERTIKAL gestapelt: breite W, hoehe W*frames, frame 0 oben.

Bauplan (dossier-vorgabe, "keyframes statt driver" — siehe unten):
  1. Mapping-node zwischen UV und dem bestehenden image-tex-node einhaengen (falls fehlt).
  2. Mapping.Scale.Y = 1/frames (das UV-fenster ist genau EIN frame hoch).
  3. Mapping.Location.Y wird pro MC-tick gekeyframt: frame k -> (frames-1-k)/frames.
     GEMESSEN (2026-07-17, per pixel-sample am synthetischen 4-frame-fixture, NICHT geraten):
     Blenders Mapping-node rechnet out_v = in_v*scale + location, der quad-UV-bereich ist
     [0,1] und Blenders bild-puffer hat v=0=BILD-UNTEN/v=1=BILD-OBEN. Der strip hat frame 0
     OBEN in der PNG-DATEI (top-down) -> frame 0 muss das fenster [1-1/frames, 1] treffen.
     Ein erster versuch mit NEGATIVEM offset (-k/frames) zeigte im pixel-gate frame
     (frames-1-k) statt frame k -- also exakt rueckwaerts; positives offset (frames-1-k)/frames
     behoben, gate PASS (siehe world_anim-abschnitt im paket-dossier).
  4. Jeder frame-wechsel ist ein HARTER cut (MC-block-animationen sind nicht interpoliert,
     "interpolate" im manifest ist bisher IMMER false in freier wildbahn) -> CONSTANT-keys.
  5. Loop: cyclic F-modifier (CYCLES) auf der f-curve, key-bereich = [0, frames*frametime_bl).

WARUM KEYFRAMES STATT DRIVER (dossier-falle, live getestet 2026-07-17): ein SCRIPTED-driver
auf `mapping.inputs["Location"].data_path`-array-index MIT scene.frame_current als variable
sollte äquivalent sein (fp_hand.py nutzt genau dieses driver-muster erfolgreich für andere
node-inputs) -- ABER hier reicht die einfachere, robustere technik (keyframes, wie überall
sonst im addon: actors.py/film_camera_import.py) und vermeidet eine zweite technik nur für
dieses modul. Kein SCRIPTED-python-string mit floor-division noetig.

MC-tick -> blender-frame: 1 tick = scene.render.fps / 20.0 blender-frames (20 ticks/sekunde
ist MCs feste tick-rate, unabhaengig von blenders fps). frametime ist in TICKS PRO FRAME
(wie lange ein strip-frame stehen bleibt) -- BenachTaggt mit scene.render.fps_base beachten
(manche szenen haben fps_base != 1, z.B. 29.97 = 30/1.001).

Alles best-effort/idempotent (re-run ersetzt keyframes+nodes, dupliziert nichts). Fehlt die
"animated"-sektion im manifest oder das material, ist das ein STILLER no-op (dossier-konvention
wie world_light.apply_world_meta).
"""

import bpy
import os
import re

MAPPING_NODE_NAME = "BBS_Anim_Mapping"
TEXCOORD_NODE_NAME = "BBS_Anim_TexCoord"

# TINT-VARIANTEN (leader-befund 2026-07-17, live bestaetigt in der geteilten test-instanz):
# world_import's biome-tint erzeugt PRO TINT-FARBE ein eigenes material
# "<basis>_<6-stelliger-hex>" (z.b. minecraft_block_water_still_3F76E4) -- der exakte
# BASIS-name aus dem manifest trifft NUR das ungetintete material, die faces mit echtem
# wasser/laub-tint haengen am SUFFIX-material. Exaktes startswith() waere zu weit (wuerde
# z.b. 'minecraft_block_water_still_something_else' faelschlich matchen) -- darum ANKER-regex
# pro eintrag: exakt der basis-name ODER basis-name + genau 6 hex-ziffern.


def _mc_ticks_to_blender_frames(scene):
    """1 MC-tick in blender-frames. MC tickt fest 20x/sekunde, unabhaengig von der
    blender-szenen-framerate -- also scene-fps/20 (fps_base mitgerechnet, dossier-falle
    bei ntsc-raten wie 29.97)."""
    fps = scene.render.fps / scene.render.fps_base
    return fps / 20.0


def _matching_materials(base_name):
    """ALLE materialien die zu einem animated-eintrag gehoeren: der exakte basis-name
    (ungetintet) UND jede tint-variante "<basis>_<6-hex>" (biome-tint-material, siehe
    modul-header). ^-verankert + $-verankert -- kein blindes startswith (das wuerde
    z.b. 'minecraft_block_magma_stuff' faelschlich unter 'minecraft_block_magma' fassen)."""
    pat = re.compile(r"^%s(_[0-9A-Fa-f]{6})?$" % re.escape(base_name))
    return [m for m in bpy.data.materials if pat.match(m.name)]


def _find_image_tex_node(nt):
    """Den image-tex-node der WIRKLICH die Base Color speist finden.

    world_import._base_color_tex deckt den EINFACHEN fall (textur direkt an Base Color)
    UND den fallback "genau 1 image-node im tree" ab -- letzterer versagt bei
    BIOME-TINT-materialien (world_import._wire_tint_multiply): dort haengt zwischen textur
    und Base Color ein 'BBS_Tint'-MIX-node, UND es liegen 2 TEX_IMAGE-nodes im tree (die
    aktive textur + eine ungenutzte alte kopie aus einem frueheren import-durchlauf) ->
    der "genau 1"-fallback greift nicht mehr, _base_color_tex gibt None zurueck (live
    gefunden an minecraft_block_water_still_3F76E4, hatte 2 TEX_IMAGE-nodes + BBS_Tint).
    Fix: BSDF->Base Color->(falls MIX 'BBS_Tint') inputs[6]="A"=die textur (siehe
    world_import._wire_tint_multiply: 'nt.links.new(tex.outputs["Color"], mix.inputs[6])').
    """
    from .world_import import _base_color_tex
    bsdf, tex = _base_color_tex(nt)
    if tex is not None:
        return tex
    if bsdf is None:
        return None
    base_in = bsdf.inputs.get("Base Color")
    if base_in is None or not base_in.is_linked:
        return None
    src = base_in.links[0].from_node
    if src.type == 'MIX' and src.name.startswith("BBS_Tint"):
        a_in = src.inputs[6]
        if a_in.is_linked:
            cand = a_in.links[0].from_node
            if cand.type == 'TEX_IMAGE' and cand.image is not None:
                return cand
    return None


def _ensure_mapping_chain(nt, tex_node):
    """UV -> Mapping -> tex_node.Vector einhaengen falls noch nicht vorhanden.
    Idempotent: ein 2. lauf findet die vorhandene Mapping-node am NAMEN wieder und
    haengt nichts doppelt ein."""
    mapping = nt.nodes.get(MAPPING_NODE_NAME)
    created = mapping is None
    if mapping is None:
        mapping = nt.nodes.new("ShaderNodeMapping")
        mapping.name = MAPPING_NODE_NAME
        mapping.label = MAPPING_NODE_NAME
        mapping.location = (tex_node.location.x - 200, tex_node.location.y - 200)

    texcoord = nt.nodes.get(TEXCOORD_NODE_NAME)
    if texcoord is None:
        texcoord = nt.nodes.new("ShaderNodeTexCoord")
        texcoord.name = TEXCOORD_NODE_NAME
        texcoord.label = TEXCOORD_NODE_NAME
        texcoord.location = (mapping.location.x - 200, mapping.location.y)

    vec_in = mapping.inputs.get("Vector")
    if not vec_in.is_linked:
        nt.links.new(texcoord.outputs["UV"], vec_in)

    tex_vec = tex_node.inputs.get("Vector")
    if tex_vec is None or not tex_vec.is_linked or tex_vec.links[0].from_node is not mapping:
        # bestehenden link (falls einer da war, z.B. UV direkt) kappen und umhaengen
        if tex_vec is not None:
            for lk in list(tex_vec.links):
                nt.links.remove(lk)
            nt.links.new(mapping.outputs["Vector"], tex_vec)
    return mapping, created


def _mapping_data_path(mapping):
    """data_path RELATIV ZUM NODE_TREE (der ID die animation_data traegt) -- ein node
    selbst ist KEIN ID-block, keyframe_insert()/fcurve.find() muessen ueber die ID mit
    'nodes["name"].inputs[1].default_value' adressiert werden (dieselbe technik wie
    fp_hand._ensure_driver, siehe paket-dossier)."""
    return 'nodes["%s"].inputs[1].default_value' % mapping.name


def _clear_existing_anim_keys(nt, mapping):
    """Vorherige keyframes auf Location.Y wegraeumen (re-import/re-apply ersetzt,
    stapelt nie) -- gleiche hygiene wie world_light._clear_light_collection.

    FALLE (live gefunden): keyframe_points.remove(kp, fast=True) waehrend ueber eine
    LISTE der alten points iteriert wird wirft 'Keyframe not in F-Curve' -- 'FAST'
    verschiebt/invalidiert die restlichen punkt-referenzen im selben array sofort nach
    dem ersten remove. Fix: rueckwaerts iterieren (index-basiert, kein 'FAST') ODER
    einfach so oft von hinten poppen bis leer -- hier: rueckwaerts ohne FAST, sicher."""
    from .actors import _find_fcurve
    path = _mapping_data_path(mapping)
    fc = _find_fcurve(nt, path, 1)
    if fc is None:
        return
    pts = fc.keyframe_points
    for i in range(len(pts) - 1, -1, -1):
        pts.remove(pts[i])
    fc.update()
    for mod in list(fc.modifiers):
        fc.modifiers.remove(mod)


def _resolve_schedule(frames, frametime, order, tick_scale):
    """Liste von (bl_frame, tile_index) fuer die abzuspielende sequenz.

    OHNE "order" (linear-fallback, der bisherige weg): tile k bei bl_frame k*step,
    step = frametime (ticks) -> blender-frames, k=0..frames-1.

    MIT "order" (leader-befund 2026-07-17: B2 liefert bald aufgeloeste (tileIndex,
    timeTicks)-paare -- exakt das was AnimationResourceMetadata.forEachFrame() aus dem
    echten .mcmeta liefert, z.b. lava PING-PONGT: 0,1,2,...,19,18,...,1 statt linear
    0..19): jeder eintrag ist [tileIndex, timeTicks], die zeit ist PRO-EINTRAG (nicht
    das pauschale frametime!) -- frametime bleibt nur der linear-fallback-default.
    """
    if order:
        sched = []
        bl_frame = 0.0
        for item in order:
            if not isinstance(item, (list, tuple)) or len(item) < 2:
                continue
            tile_index, time_ticks = int(item[0]), int(item[1])
            sched.append((bl_frame, tile_index))
            bl_frame += max(1, int(round(time_ticks * tick_scale)))
        return sched, bl_frame
    frame_step_bl = max(1, int(round(frametime * tick_scale)))
    sched = [(k * frame_step_bl, k) for k in range(frames)]
    return sched, frame_step_bl * frames


def _keyframe_mapping_offset(nt, mapping, frames, frametime, tick_scale, order=None):
    """Location.Y der Mapping-node pro strip-frame/schedule-eintrag CONSTANT-keyframen
    + cyclic-loop.

    data_path fuer eine node-input-VEKTOR-komponente (RELATIV ZUM NODE_TREE, siehe
    _mapping_data_path): 'inputs[1]' ist 'Location' bei ShaderNodeMapping (socket-reihenfolge
    0=Vector/1=Location/2=Rotation/3=Scale, live verifiziert -- siehe _apply_one-kommentar),
    array_index=1 darin ist die Y-komponente. Y (nicht X) weil der strip VERTIKAL gestapelt ist.
    """
    path = _mapping_data_path(mapping)
    _clear_existing_anim_keys(nt, mapping)

    schedule, total_bl_frames = _resolve_schedule(frames, frametime, order, tick_scale)
    if not schedule:
        return 0

    loc_in = mapping.inputs[1]
    loc_in.default_value[1] = 0.0
    if not nt.keyframe_insert(data_path=path, index=1, frame=0):
        print("[bbs-bridge] world_anim: keyframe_insert auf %s fehlgeschlagen" % path)
        return 0

    from .actors import _find_fcurve
    fc = _find_fcurve(nt, path, 1)
    if fc is None:
        print("[bbs-bridge] world_anim: konnte keine fcurve fuer Mapping.Location.Y finden")
        return 0

    def tile_to_v(tile_index):
        # GEMESSEN (nicht geraten, 2026-07-17): Blenders Mapping-node wendet
        # out_v = in_v*scale + location an, in_v der quad-UV liegt in [0,1] und Blenders
        # bild-puffer hat v=0=BILD-UNTEN, v=1=BILD-OBEN. Der strip hat kachel 0 OBEN in der
        # DATEI (top-down-konvention) -> kachel 0 muss das UV-FENSTER [1-1/frames, 1] treffen,
        # NICHT [0, 1/frames] (das waere die LETZTE kachel). offset fuer kachel i daher
        # POSITIV: (frames-1-i)/frames -- ein direkt-empirischer pixel-sample-test deckte den
        # anfaenglichen vorzeichenfehler auf (negatives offset zeigte kachel (frames-1-i)).
        return (frames - 1 - tile_index) / float(frames)

    for bl_frame, tile_index in schedule:
        v_offset = tile_to_v(tile_index)
        kp = fc.keyframe_points.insert(bl_frame, v_offset, options={'FAST'})
        kp.interpolation = 'CONSTANT'
    # LOOP-RAND-KEY (gate-2-fix, gemessen): ohne einen key GENAU bei total_bl_frames mit
    # dem wert der ERSTEN schedule-kachel setzt der CYCLES-modifier die periode auf
    # [erster_key, LETZTER_key] statt [0, total_bl_frames) -- der frame direkt NACH der
    # letzten kachel evaluierte dadurch auf die vorletzte kachel statt die erste. Der
    # rand-key schliesst die periode exakt (funktioniert fuer linear UND order/ping-pong
    # gleichermassen, weil er einfach "schedule[0]'s wert" wiederholt).
    first_v = tile_to_v(schedule[0][1])
    kp_end = fc.keyframe_points.insert(total_bl_frames, first_v, options={'FAST'})
    kp_end.interpolation = 'CONSTANT'
    fc.update()

    # loop: cyclic modifier wiederholt die jetzt EXAKT [0, total_bl_frames)-lange periode
    mods = fc.modifiers
    cyc = None
    for m in mods:
        if m.type == 'CYCLES':
            cyc = m
            break
    if cyc is None:
        cyc = mods.new('CYCLES')
    cyc.mode_before = 'REPEAT'
    cyc.mode_after = 'REPEAT'
    return total_bl_frames


def _apply_to_material(mat, frames, frametime, order, base_dir, rel_file, scene):
    """Ein EINZELNES material (basis ODER tint-variante) verdrahten. Rueckgabe: True bei
    erfolg, sonst False (mit log) -- crasht nie, ein kaputtes material darf die anderen
    treffer desselben entries nicht verhindern."""
    if not mat.use_nodes or mat.node_tree is None:
        print("[bbs-bridge] world_anim: material ohne node-tree:", mat.name)
        return False
    nt = mat.node_tree
    tex_node = _find_image_tex_node(nt)
    if tex_node is None:
        print("[bbs-bridge] world_anim: kein image-tex-node fuer:", mat.name)
        return False

    # optional: falls der java-exporter eine dedizierte strip-datei mitliefert und die
    # aktuell gebundene textur NICHT der strip ist, auf den strip umhaengen (best effort --
    # fehlt die datei, bleibt die bestehende single-frame-textur einfach animiert-aber-
    # unbewegt-aussehend fuer den bildinhalt, das UV-fenster stimmt trotzdem).
    if rel_file:
        strip_path = os.path.join(base_dir, rel_file)
        if os.path.isfile(strip_path):
            img_name = os.path.basename(strip_path)
            img = bpy.data.images.get(img_name)
            if img is None or os.path.abspath(bpy.path.abspath(img.filepath)) != os.path.abspath(strip_path):
                img = bpy.data.images.load(strip_path, check_existing=True)
            if tex_node.image is not img:
                tex_node.image = img
            # KEINE colorspace-zuweisung hier (end-review fund 1, 2026-07-17): eine frühere
            # selbst-zuweisung (name = name) war bei GESUNDEN strips ein no-op, aber bei einem
            # korrupten/undecodierbaren PNG lädt Blender einen placeholder mit colorspace-name
            # "" — das zurückschreiben von "" ins enum wirft TypeError und die exception
            # entkam bis in den tick-timer (der bei exception STILL deregistriert). Blender
            # lädt PNGs ohnehin als sRGB (dossier: "texturen NICHT konvertieren").
            tex_node.interpolation = 'Closest'

    mapping, _created = _ensure_mapping_chain(nt, tex_node)
    # ShaderNodeMapping-socket-reihenfolge (LIVE VERIFIZIERT, nicht die intuitive
    # Vector/Scale/Rotation-annahme): 0=Vector, 1=Location, 2=Rotation, 3=Scale.
    # Ein erster versuch schrieb "scale" nach inputs[2] (= Rotation!) -> die UV wurde
    # verdreht statt gefenstert, pixel-gate zeigte falsche/fest-blaue farbe -> aufgedeckt.
    # Rotation GANZ (alle 3 komponenten) resetten -- der bug-versuch hatte Y mit 0.25 belegt.
    mapping.inputs[2].default_value[0] = 0.0
    mapping.inputs[2].default_value[1] = 0.0
    mapping.inputs[2].default_value[2] = 0.0
    mapping.inputs[3].default_value[0] = 1.0
    mapping.inputs[3].default_value[1] = 1.0 / frames   # Scale.Y = 1 frame-fenster
    mapping.inputs[3].default_value[2] = 1.0

    tick_scale = _mc_ticks_to_blender_frames(scene)
    total = _keyframe_mapping_offset(nt, mapping, frames, frametime, tick_scale, order)
    return total > 0


def _apply_one(entry, base_dir, scene):
    """Ein animated-eintrag: matcht ALLE materialien (basis + tint-varianten, siehe
    _matching_materials) und verdrahtet JEDE einzeln. interpolate=true wird V1-BEWUSST
    genauso wie interpolate=false behandelt (harte CONSTANT-cuts, keine echte lineare
    blend-ueberblendung) -- dokumentierte grenze, kein stilles ignorieren (leader-vorgabe):
    fuer 3-frame-magma-artige texturen ist ein sichtbarer harter cut statt sanftem fade
    der einzige unterschied, kosmetisch, kein korrektheitsproblem (die richtige kachel
    zur richtigen zeit steht so oder so)."""
    mat_name = entry.get("material")
    frames = int(entry.get("frames") or 0)
    frametime = int(entry.get("frametime") or 1)
    order = entry.get("order")   # optional: [[tileIndex,timeTicks],...], siehe _resolve_schedule
    if not mat_name or frames <= 1:
        return None
    mats = _matching_materials(mat_name)
    if not mats:
        print("[bbs-bridge] world_anim: kein material gefunden fuer:", mat_name)
        return None
    rel_file = entry.get("file")
    hit_names = []
    for mat in mats:
        # DEFENSIV (end-review fund 1): _apply_to_material's docstring verspricht "crasht
        # nie" — hier wird das erzwungen. Ein kaputtes material/strip (z.b. korruptes PNG,
        # exotischer node-tree) loggt und überspringt, statt die anderen treffer UND
        # (weiter oben) den tick-timer zu töten. Breites except ist hier absicht: die
        # fehlerquellen sind bpy-interna (TypeError/RuntimeError/...), der schaden eines
        # verschluckten bugs ist ein log, der schaden einer entkommenen exception ein
        # stiller timer-tod.
        try:
            if _apply_to_material(mat, frames, frametime, order, base_dir, rel_file, scene):
                hit_names.append(mat.name)
        except Exception as e:  # noqa: BLE001 — siehe kommentar, timer-schutz siegt
            print("[bbs-bridge] world_anim: material '%s' uebersprungen (%s: %s)"
                  % (mat.name, type(e).__name__, e))
    if not hit_names:
        return None
    if entry.get("interpolate"):
        print("[bbs-bridge] world_anim: '%s' hat interpolate=true -- V1-GRENZE: wir spielen "
              "trotzdem harte CONSTANT-cuts (keine echte blend-ueberblendung zwischen kacheln), "
              "kosmetischer unterschied, keine falsche kachel/zeit." % mat_name)
    return {"material": mat_name, "matched": hit_names, "frames": frames,
            "frametime": frametime, "order": bool(order)}


def apply_animated(meta, base_dir):
    """meta["animated"] abarbeiten. Fehlt die sektion (aelterer java-stand / kein
    animiertes material in der region) -> STILLER no-op, exakt wie world_light."""
    entries = meta.get("animated") if isinstance(meta, dict) else None
    if not entries:
        return None
    scene = bpy.context.scene
    done = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        r = _apply_one(entry, base_dir, scene)
        if r is not None:
            done.append(r)
    total_mats = sum(len(r["matched"]) for r in done)
    print("[bbs-bridge] world_anim: %d/%d animierte eintraege verdrahtet (%d materialien "
          "insgesamt inkl. tint-varianten)" % (len(done), len(entries), total_mats))
    return done
