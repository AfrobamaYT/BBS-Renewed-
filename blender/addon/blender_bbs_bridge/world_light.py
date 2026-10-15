"""Task 3.5 — MC-licht/himmel aus dem game-truth-manifest (world_meta.json).

Der java-exporter schreibt neben die OBJ ein manifest mit dem EXAKTEN vanilla-skyAngle
(kein nachgebauter formel-klon), timeOfDay und jedem leuchtenden block (pos+luminance+id).
Hier wird daraus: sonne/mond (BBS_WorldSun/BBS_WorldMoon), world-hintergrund (himmel nach
tageszeit), punkt-lichter für gezielte quellen (fackel/lantern/glowstone…) und
emission-materialien für flächige leuchter (lava/magma/feuer…).

Alles lebt in der collection `MC_World_Light` und wird beim re-import ERSETZT
(rückstandslos, gleiche hygiene wie _clear_world_collection). Nur main-thread rufen.

OFFENE VERIFIKATION (H-gate, braucht laufendes game): das VORZEICHEN des sonnen-winkels
ist gegen den live-schatten im game zu prüfen (plan 3.5-beweis: nachtszene side-by-side).
Die winkel-MATHE (skyAngle→elevation) ist deterministisch gegen synthetische metas getestet.
"""

import bpy
import json
import math
import os

LIGHT_COLLECTION = "MC_World_Light"

# custom-prop auf der szene: name des scene.world das VOR unserem tausch aktiv war
# ("" = es gab keins). End-review fund 6: apply_world_meta hängte scene.world auf
# BBS_MC_World um, aber NICHTS merkte sich das original — der himmel des users war nach
# einem einzigen import dauerhaft ersetzt (invariante 5 "rückstandslos" verletzt).
# Custom-prop statt state-modul-global, damit die info einen .blend-save/-reload überlebt.
PREV_WORLD_PROP = "bbsbridge_prev_world"

# gezielte punkt-licht-quellen: block-id -> lichtfarbe (linear-ish). Alles andere leuchtende
# kriegt DEFAULT_POINT_COLOR — AUSSER es steht in _AREA_EMITTERS (flächig -> nur material).
_POINT_COLORS = {
    "minecraft:torch": (1.0, 0.55, 0.25),
    "minecraft:wall_torch": (1.0, 0.55, 0.25),
    "minecraft:lantern": (1.0, 0.6, 0.3),
    "minecraft:campfire": (1.0, 0.5, 0.2),
    "minecraft:soul_torch": (0.35, 0.75, 1.0),
    "minecraft:soul_wall_torch": (0.35, 0.75, 1.0),
    "minecraft:soul_lantern": (0.35, 0.75, 1.0),
    "minecraft:soul_campfire": (0.35, 0.75, 1.0),
    "minecraft:glowstone": (1.0, 0.72, 0.4),
    "minecraft:sea_lantern": (0.75, 0.92, 1.0),
    "minecraft:shroomlight": (1.0, 0.65, 0.35),
    "minecraft:end_rod": (0.9, 0.87, 1.0),
}
DEFAULT_POINT_COLOR = (1.0, 0.75, 0.45)

# flächige leuchter: KEIN punkt-licht (die emission-fläche selbst leuchtet — ein punkt-licht
# pro lava-block wäre bei einem lava-see tausende lichter und doppelt hell).
_AREA_EMITTERS = {
    "minecraft:lava", "minecraft:fire", "minecraft:soul_fire", "minecraft:magma_block",
    "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:glow_lichen",
}

# material-namens-hints -> emission-stärke (Principled "Emission Strength").
# namen kommen aus dem sprite-pfad (minecraft_block_<sprite>), siehe MCprep-befund.
_EMISSION_STRENGTH = {
    "lava": 8.0, "fire": 6.0, "magma": 3.0, "glowstone": 5.0, "sea_lantern": 4.0,
    "shroomlight": 4.0, "torch": 5.0, "lantern": 5.0, "campfire_fire": 6.0,
    "end_rod": 4.0, "froglight": 4.0, "glow_lichen": 1.5, "cave_vines": 2.0,
}

# über dieser zahl werden nur die hellsten quellen zu punkt-lichtern (rest: log, kein
# stilles cap — sichtbarkeit siegt, aber tausende lichter killen EEVEE).
MAX_POINT_LIGHTS = 400

# MC-himmelsfarben (linear). tag = vanilla-blau, nacht = fast schwarz mit blau-rest.
_SKY_DAY = (0.18, 0.36, 0.90)
_SKY_NIGHT = (0.004, 0.006, 0.018)

# === SICHTBARE HIMMELSKÖRPER + WOLKEN (vanilla-geometrie, BYTECODE-GEMESSEN) ==================
# Quelle: javap -c WorldRenderer.renderSky/renderClouds (yarn 1.21.1, im scratchpad protokolliert,
# NICHT geraten — Regel 6). Die vanilla-himmels-kuppel:
#   MatrixStack: rotY(-90°) · rotX(skyAngle*360°)   (== unser sonnen-lampe-winkel, s.u.)
#   SONNE: quad in DIESEM frame bei y=+100, ecken (±30, 100, ±30), UV voll 0..1, sun.png (32²).
#   MOND:  quad bei y=-100 (GEGENÜBER der sonne), ecken (±20, -100, ±20), UV = phase-zelle im
#          4×2-atlas moon_phases.png (128×64). phase = (lunarTime/24000)%8.
#   WOLKEN: horizontale ebene bei welt-y = getCloudsHeight() = 192 (Overworld), clouds.png (256²).
# Wir bauen die scheiben NICHT auf einer 100-einheiten-kuppel um die kamera (die reist mit dem
# betrachter und müsste pro frame folgen) — sondern als GROSSE, WEIT ENTFERNTE emissive quads in
# richtung des himmelskörpers, so dass sie aus JEDER position der (statischen) export-szene als
# helle scheibe am himmel stehen. Das ist der ehrliche snapshot: fixe position zur export-zeit.
_SKY_BODY_DISTANCE = 900.0      # abstand scheibe→welt-mitte (weit hinter jeder export-region)
_SUN_DISC_SIZE = 140.0          # kantenlänge der sonnen-scheibe in blender-einheiten (weithin sichtbar)
_MOON_DISC_SIZE = 95.0          # mond kleiner als sonne (vanilla ±20 vs ±30 → ~2/3)
_CLOUD_HEIGHT_MC_Y = 192.0      # vanilla getCloudsHeight() (Overworld) — bl_z = mc_y
_CLOUD_THICKNESS_MC = 12.0      # vanilla wolken-klotz-dicke: MC rendert wolken als 12³-quader, nicht als papier
# MC-cloud-drift: WorldRenderer scrollt die wolken-textur mit der spielzeit nach +X. Vanilla-tempo
# ≈ 0.6 block/s (cloudsHeight-scroll, javap-abgelesen). Bei 20 tps / render-fps ist das ein
# frame-getriebener driver — deterministisch (kein poll, kein tick-abo), im render sichtbar.
_CLOUD_DRIFT_BLOCKS_PER_SEC = 0.6
# MC: eine clouds.png-KACHEL (256px) deckt 256*12 = 3072 blöcke (jeder pixel = ein 12×12-block-
# wolkenklotz). Ein voller textur-durchgang über so viele blöcke = wolken lesen sich als wolken,
# nicht als dichtes gitter. (Ein erster versuch mit 12 blöcken/durchgang war ein rauschiges muster.)
_CLOUD_BLOCKS_PER_TEXTURE = 3072.0

# environment-texturen in der client-jar (item_assets-loader liest sie, KEIN neu-bau)
_TEX_SUN = "assets/minecraft/textures/environment/sun.png"
_TEX_MOON = "assets/minecraft/textures/environment/moon_phases.png"
_TEX_CLOUDS = "assets/minecraft/textures/environment/clouds.png"


def _restore_scene_world(scene):
    """scene.world auf das VOR unserem tausch aktive world zurückhängen (end-review fund 6:
    es gab keinen restore-pfad — 'rückstandslos' galt für die collection, nicht fürs world).

    Regeln (kein dangling): steht scene.world nicht (mehr) auf BBS_MC_World, wird NICHTS
    umgehängt (user hat selbst gewechselt — sein wille siegt), nur die merk-prop geräumt.
    Existiert das gemerkte world nicht mehr (user hat es gelöscht) ODER gab es nie eins
    (prop ""), wird None gesetzt statt unser world hängen zu lassen. Fehlt die prop ganz
    (import aus einem stand VOR diesem fix), ebenfalls None + log — rückstandslos siegt.
    Danach: unser world-datablock weg wenn unbenutzt (voll rückstandslos, wie die lichter)."""
    stored = scene.get(PREV_WORLD_PROP, None)
    cur = scene.world
    if cur is not None and cur.name == "BBS_MC_World":
        if stored:
            prev = bpy.data.worlds.get(stored)
            if prev is None:
                print("[bbs-bridge] world-restore: gemerktes world '%s' existiert nicht mehr "
                      "-> scene.world=None" % stored)
            scene.world = prev
        else:
            if stored is None:
                print("[bbs-bridge] world-restore: kein original gemerkt (import vor dem "
                      "restore-fix?) -> scene.world=None")
            scene.world = None
    if PREV_WORLD_PROP in scene:
        try:
            del scene[PREV_WORLD_PROP]
        except (KeyError, TypeError):
            pass
    bbs_world = bpy.data.worlds.get("BBS_MC_World")
    if bbs_world is not None and bbs_world.users == 0:
        try:
            bpy.data.worlds.remove(bbs_world)
        except (ReferenceError, RuntimeError):
            pass


def _clear_light_collection():
    """MC_World_Light samt lichtern/world weg — re-import ersetzt, stapelt nie.
    Seit end-review fund 6 inklusive scene.world-restore (siehe _restore_scene_world) —
    beim RE-IMPORT ist das restore+neu-tauschen ein sauberer zyklus (apply merkt sich das
    soeben restaurierte user-world gleich wieder), beim ENTFERNEN ist es der restore."""
    _restore_scene_world(bpy.context.scene)
    coll = bpy.data.collections.get(LIGHT_COLLECTION)
    if coll is not None:
        lights, meshes, mats = set(), set(), set()
        for obj in list(coll.objects):
            if obj.data is not None and isinstance(obj.data, bpy.types.Light):
                lights.add(obj.data)
            if obj.data is not None and isinstance(obj.data, bpy.types.Mesh):
                meshes.add(obj.data)   # fog-würfel, sonnen-/mond-scheibe, wolken-ebene
            for slot in getattr(obj, "material_slots", []):
                if slot.material is not None:
                    mats.add(slot.material)
            bpy.data.objects.remove(obj, do_unlink=True)
        for li in lights:
            try:
                if li.users == 0:
                    bpy.data.lights.remove(li)
            except (ReferenceError, RuntimeError):
                pass
        # reihenfolge mesh -> material -> image (referenz-kette, world_import-muster): sonst
        # bleibt mat.users/img.users nie 0 und die BBS_MC_*-datenblöcke bleiben als .001-müll
        # liegen (invariante 5 "rückstandslos" verletzt — sun/moon/cloud-material + env-bilder).
        for me in meshes:
            try:
                if me.users == 0:
                    bpy.data.meshes.remove(me)
            except (ReferenceError, RuntimeError):
                pass
        imgs = set()
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


def _daylight(sky_angle):
    """0..1 wieviel 'tag' es ist. skyAngle: 0=mittag, 0.5=mitternacht (vanilla-arc)."""
    return max(0.0, math.cos(sky_angle * 2.0 * math.pi))


def _lerp3(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def _env_tex_png(inner_path):
    """Eine environment-textur (sun/moon/clouds) aus der client-jar in den lokalen cache
    holen und den pfad liefern (oder None). Nutzt HB-1s jar-cache-loader (item_assets) —
    EINE jar-quelle im ganzen addon, kein zweiter loader. Defensiv importiert: fehlt das
    modul/die jar, ist die scheibe/wolke ein stiller no-op (wie ein fehlendes manifest)."""
    try:
        from . import item_assets
    except Exception as e:  # noqa: BLE001 — sky-veredelung darf den import nie killen
        print("[bbs-bridge] himmelskörper: item_assets nicht ladbar (%s: %s)"
              % (type(e).__name__, e))
        return None
    try:
        return item_assets.extract_png_to_cache(inner_path)
    except Exception as e:  # noqa: BLE001
        print("[bbs-bridge] himmelskörper: textur nicht extrahierbar (%s): %s" % (inner_path, e))
        return None


def _sky_body_material(name, png_path, emission=6.0, uv_rect=None):
    """Emissive, unlit-look (Closest) material für sonnen-/mond-scheibe. Die textur speist
    Emission Color, Emission Strength macht sie hell — so leuchtet die scheibe aus SICH selbst,
    unabhängig von der szenen-beleuchtung (wie der vanilla-himmelskörper).

    ⚠️ GEMESSEN (2026-07-18): sun.png UND moon_phases.png sind VOLL OPAK (alpha=255 überall) —
    KEINE alpha-silhouette. Die runde scheibe steckt im RGB, der HINTERGRUND ist SCHWARZ. Vanilla
    zeichnet sie darum ADDITIV (blendFuncSeparate, schwarz addiert nichts → nur die helle scheibe
    erscheint). In EEVEE bilden wir das nach, indem wir die HELLIGKEIT der textur als ALPHA nehmen
    (schwarze ecken → transparent, helle scheibe → opak) + BLEND — so verschwindet der dunkle
    kasten und der runde rand fadet weich aus wie in MC. NICHT das (nutzlose, überall-1) alpha
    der textur verdrahten.

    `uv_rect`=(u0,v0,u1,v1) fenstert eine atlas-zelle (mond-phase); None = volle textur.
    Idempotent per material-name (re-import baut denselben graph neu)."""
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    try:
        img = bpy.data.images.load(png_path, check_existing=True)
    except RuntimeError as e:
        print("[bbs-bridge] himmelskörper: bild lädt nicht (%s): %s" % (png_path, e))
        return None
    tex.image = img
    tex.interpolation = 'Closest'          # MC-look (kein resample der 32²/128²-pixel)
    tex.extension = 'CLIP'                 # atlas-zelle bleibt in ihren grenzen (kein rand-bleed)
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    emis_col = bsdf.inputs.get("Emission Color")
    emis_str = bsdf.inputs.get("Emission Strength")
    if emis_col is not None:
        nt.links.new(tex.outputs["Color"], emis_col)
    if emis_str is not None:
        emis_str.default_value = emission
    # HELLIGKEIT als alpha (schwarzer hintergrund → transparent, scheibe → opak): ein
    # RGB-to-BW-node hinter der textur speist Principled.Alpha. So verschwindet die schwarze
    # ecke (= der dunkle kasten), nur die leuchtende scheibe bleibt.
    alpha_in = bsdf.inputs.get("Alpha")
    if alpha_in is not None:
        bw = nt.nodes.new("ShaderNodeRGBToBW")
        nt.links.new(tex.outputs["Color"], bw.inputs["Color"])
        nt.links.new(bw.outputs["Val"], alpha_in)
    # BLEND (weicher rand-fade wie der additive vanilla-himmelskörper), nicht CLIP/HASHED
    if hasattr(mat, "blend_method"):
        try:
            items = mat.bl_rna.properties["blend_method"].enum_items.keys()
            for cand in ('BLEND', 'HASHED', 'CLIP'):
                if cand in items:
                    mat.blend_method = cand
                    break
        except (KeyError, AttributeError, TypeError):
            pass
    if hasattr(mat, "use_backface_culling"):
        mat.use_backface_culling = False   # von beiden seiten sichtbar
    if hasattr(mat, "shadow_method"):
        try:
            mat.shadow_method = 'NONE'     # der himmelskörper wirft keinen schatten
        except TypeError:
            pass
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    # atlas-fensterung (mond-phase): ein Mapping-node zwischen implizite UV und die textur.
    # Blender-5.2-falle (paket-CLAUDE.md): ShaderNodeMapping-sockets sind 0=Vector/1=Location/
    # 2=Rotation/3=Scale (NICHT die intuitive reihenfolge). Wir skalieren+verschieben die UV
    # in die zelle: scale=(u1-u0, v1-v0), location=(u0, v0).
    if uv_rect is not None:
        u0, v0, u1, v1 = uv_rect
        uvnode = nt.nodes.new("ShaderNodeTexCoord")
        mapnode = nt.nodes.new("ShaderNodeMapping")
        mapnode.inputs[3].default_value = (u1 - u0, v1 - v0, 1.0)     # Scale
        mapnode.inputs[1].default_value = (u0, v0, 0.0)              # Location
        nt.links.new(uvnode.outputs["UV"], mapnode.inputs[0])       # Vector
        nt.links.new(mapnode.outputs["Vector"], tex.inputs["Vector"])
    return mat


def _moon_phase(time_of_day):
    """Mond-phase (0..7) für den 4×2-atlas. EHRLICHE GRENZE (Regel 0): die echte vanilla-phase
    ist `(getLunarTime()/24000)%8` und hängt am TAGES-ZÄHLER — der exporter schreibt aber nur
    `getTimeOfDay()%24000` (0..23999, siehe StreamMetaWriter), der tageszähler fehlt. Wir können
    die reale phase daher NICHT rekonstruieren; default = VOLLMOND (phase 0, was vanilla an tag 0
    zeigt). Das ist bewusst statisch, kein geratener tageszähler."""
    return 0


def _disc_quad_verts(size):
    """4 ecken eines XY-quads (in der lokalen ebene der scheibe), zentriert, kantenlänge `size`.
    Reihenfolge = UV (0,0),(1,0),(1,1),(0,1) — passt zur vollen textur / atlas-zelle."""
    h = size * 0.5
    return [(-h, -h, 0.0), (h, -h, 0.0), (h, h, 0.0), (-h, h, 0.0)]


def _build_sky_disc(coll, name, mat, size, direction, up_hint=(0.0, 0.0, 1.0)):
    """Eine flache, emissive scheibe (sonne/mond) WEIT in richtung `direction` (blender-welt-
    einheitsvektor) platzieren, mit dem gesicht zur welt-mitte gedreht. `direction` ist die
    disc-richtung = -lichtvektor der zugehörigen lampe (die scheibe steht dort, WO das licht
    HERKOMMT), so dass scheibe und schatten übereinstimmen. UV-quad + material tragen die textur.
    Gibt das objekt (oder None wenn kein material)."""
    if mat is None:
        return None
    import mathutils
    d = mathutils.Vector(direction)
    if d.length < 1e-6:
        d = mathutils.Vector((0.0, 0.0, 1.0))
    d.normalize()
    mesh = bpy.data.meshes.new(name)
    verts = _disc_quad_verts(size)
    faces = [(0, 1, 2, 3)]
    mesh.from_pydata(verts, [], faces)
    uv_layer = mesh.uv_layers.new(name="UVMap")
    for i, uv in enumerate([(0, 0), (1, 0), (1, 1), (0, 1)]):
        uv_layer.data[i].uv = uv
    mesh.materials.append(mat)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    # position: weit in scheiben-richtung; ausrichtung: quad-normal (+Z lokal) zur welt-mitte,
    # also entgegen `direction`. rotation_difference von +Z auf -direction.
    obj.location = (d.x * _SKY_BODY_DISTANCE, d.y * _SKY_BODY_DISTANCE, d.z * _SKY_BODY_DISTANCE)
    z_axis = mathutils.Vector((0.0, 0.0, 1.0))
    obj.rotation_euler = z_axis.rotation_difference(-d).to_euler()
    obj.hide_select = True            # user klickt die scheibe nicht versehentlich an
    coll.objects.link(obj)
    return obj


def _build_clouds(coll, region, sky_angle):
    """Wolken-KLOTZ-ebene: eine große horizontale platte bei welt-y=192 (bl_z=192, MCs cloud-höhe)
    mit VANILLA-DICKE (12 blöcke, MC rendert wolken als 12³-klötze, nicht als papier), textur
    clouds.png gekachelt (weiß/transparent), leicht durchscheinend, MC-look (Closest, kein
    resample). Deckt die export-region großzügig ab (+rand), so dass beim blick nach oben überall
    wolken stehen. DRIFTET wie in MC (frame-getriebener driver auf location.x, ~0.6 block/s nach
    +X = vanilla cloud-scroll) — deterministisch, kein poll, im render sichtbar. Gibt das objekt
    oder None (keine textur/region)."""
    png = _env_tex_png(_TEX_CLOUDS)
    if png is None:
        return None
    # ausdehnung: um die region-mitte, großzügig über die region hinaus (damit der himmel beim
    # hochschauen von jeder stelle bedeckt ist). Fallback-mitte 0,0 wenn keine region.
    if region is not None and len(region) == 6:
        x0, y0, z0, x1, y1, z1 = [float(v) for v in region]
        # MC-region -> blender: bl_x=x, bl_y=-z
        cx = (x0 + x1) * 0.5
        cz = (z0 + z1) * 0.5
        bcx = cx
        bcy = -cz
        span = max(x1 - x0, z1 - z0, 128.0)
    else:
        bcx, bcy, span = 0.0, 0.0, 512.0
    half = span * 1.5 + 256.0         # weit über die region hinaus
    bz = _CLOUD_HEIGHT_MC_Y           # bl_z = mc_y = 192 (UNTERKANTE des klotzes)
    bz_top = bz + _CLOUD_THICKNESS_MC # oberkante = +12 (vanilla wolken-dicke)
    mesh = bpy.data.meshes.new("BBS_MC_Clouds")
    # KLOTZ statt flacher quad: 8 ecken (unten bz, oben bz_top). Reihenfolge: 0-3 unten, 4-7 oben.
    verts = [(bcx - half, bcy - half, bz),     (bcx + half, bcy - half, bz),
             (bcx + half, bcy + half, bz),     (bcx - half, bcy + half, bz),
             (bcx - half, bcy - half, bz_top), (bcx + half, bcy - half, bz_top),
             (bcx + half, bcy + half, bz_top), (bcx - half, bcy + half, bz_top)]
    # unterseite (nach unten schauend sichtbar), oberseite, 4 seitenwände. Winding so, dass
    # normalen nach außen zeigen (unten -Z, oben +Z).
    faces = [(3, 2, 1, 0), (4, 5, 6, 7),
             (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    mesh.from_pydata(verts, [], faces)
    # UV so, dass ein textur-durchgang _CLOUD_BLOCKS_PER_TEXTURE blöcke deckt -> gekachelt (REPEAT).
    # Nur die 4 top-verts (4..7) bekommen die kachel-UV; die box-loops erben sie tolerant (die
    # seiten sind schmal = 12 einheiten, UV-verzerrung dort ist unsichtbar).
    tiles = (2.0 * half) / _CLOUD_BLOCKS_PER_TEXTURE
    uv_layer = mesh.uv_layers.new(name="UVMap")
    # je loop ein UV; wir mappen nach vert-index-muster (0/1/2/3 der grundfläche) modulo.
    _uv_by_corner = {0: (0, 0), 1: (tiles, 0), 2: (tiles, tiles), 3: (0, tiles),
                     4: (0, 0), 5: (tiles, 0), 6: (tiles, tiles), 7: (0, tiles)}
    for li, loop in enumerate(mesh.loops):
        uv_layer.data[li].uv = _uv_by_corner.get(loop.vertex_index, (0, 0))
    mat = bpy.data.materials.get("BBS_MC_CloudMat")
    if mat is None:
        mat = bpy.data.materials.new("BBS_MC_CloudMat")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    tex = nt.nodes.new("ShaderNodeTexImage")
    try:
        img = bpy.data.images.load(png, check_existing=True)
    except RuntimeError as e:
        print("[bbs-bridge] wolken: bild lädt nicht (%s): %s" % (png, e))
        return None
    tex.image = img
    tex.interpolation = 'Closest'
    tex.extension = 'REPEAT'          # gekachelt über die große platte
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    # wolken leicht selbst-hell (MC-wolken sind hell, tagsüber weiß) — ein bisschen emission,
    # damit sie nicht von der sonnen-lampe abhängen und auch bei blick nach oben sichtbar sind.
    emis_col = bsdf.inputs.get("Emission Color")
    emis_str = bsdf.inputs.get("Emission Strength")
    d = _daylight(sky_angle)
    if emis_col is not None:
        nt.links.new(tex.outputs["Color"], emis_col)
    if emis_str is not None:
        emis_str.default_value = 0.15 + 0.85 * d      # nachts dunkler, tags hell
    # halb-durchsichtig: clouds.png ist RGBA (weiße wolke + transparente lücken). Zusätzlich die
    # ganze platte etwas durchscheinend, damit sie wie MC-wolken wirken (nicht solide).
    alpha_in = bsdf.inputs.get("Alpha")
    if alpha_in is not None and "Alpha" in tex.outputs:
        # textur-alpha * globaler faktor -> ein MULTIPLY-mix
        mix = nt.nodes.new('ShaderNodeMath')
        mix.operation = 'MULTIPLY'
        mix.inputs[1].default_value = 0.75            # gesamt-deckkraft (MC-wolken-look)
        nt.links.new(tex.outputs["Alpha"], mix.inputs[0])
        nt.links.new(mix.outputs["Value"], alpha_in)
    if hasattr(mat, "blend_method"):
        try:
            items = mat.bl_rna.properties["blend_method"].enum_items.keys()
            for cand in ('BLEND', 'HASHED', 'CLIP'):
                if cand in items:
                    mat.blend_method = cand
                    break
        except (KeyError, AttributeError, TypeError):
            pass
    if hasattr(mat, "use_backface_culling"):
        mat.use_backface_culling = False              # von unten (blick nach oben) sichtbar
    if hasattr(mat, "shadow_method"):
        try:
            mat.shadow_method = 'NONE'
        except TypeError:
            pass
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    mesh.materials.append(mat)
    mesh.update()
    obj = bpy.data.objects.new("BBS_MC_Clouds", mesh)
    obj.hide_select = True
    coll.objects.link(obj)
    _add_cloud_drift_driver(obj, bcx)
    return obj


def _add_cloud_drift_driver(obj, base_x):
    """Frame-getriebenen driver auf location.x legen = wolken-drift nach +X wie in MC. Bewegung
    ist DETERMINISTISCH (nur von der frame-nummer + szenen-fps abhängig), also im render exakt
    reproduzierbar und ohne poll/tick-abo — passt zum snapshot-modell, macht die wolken aber
    lebendig (user-rüge: 'wolken statisch'). Fällt still zurück auf statisch, wenn driver-API
    nicht verfügbar ist (wirft nie). base_x = die ruhe-x (region-mitte), damit die drift dort
    startet.
      x(frame) = base_x + (frame / fps) * drift_blocks_per_sec
    Ein driver-ausdruck kann `frame` (Blenders eingebaute) direkt nutzen; fps holen wir als
    scene-property-var, damit unterschiedliche render-fps korrekt skalieren."""
    try:
        fcurve = obj.driver_add("location", 0)   # index 0 = X
    except (RuntimeError, TypeError):
        return
    try:
        drv = fcurve.driver
        drv.type = 'SCRIPTED'
        var = drv.variables.new()
        var.name = "fps"
        var.type = 'SINGLE_PROP'
        tgt = var.targets[0]
        tgt.id_type = 'SCENE'
        tgt.id = bpy.context.scene
        tgt.data_path = "render.fps"
        # frame/fps = sekunden; * blocks/s = zurückgelegte blöcke; + ruhe-x
        drv.expression = "%f + (frame / fps) * %f" % (base_x, _CLOUD_DRIFT_BLOCKS_PER_SEC)
    except (RuntimeError, TypeError, AttributeError, IndexError):
        # driver angelegt, aber ausdruck/var scheiterte -> lieber ganz weg (statisch) als kaputt
        try:
            obj.driver_remove("location", 0)
        except (RuntimeError, TypeError):
            pass


def _build_sky(scene, sky_angle):
    """World-hintergrund nach tageszeit. Eigene world 'BBS_MC_World' — die des users
    bleibt unangetastet, wir hängen nur scene.world um (und nur auf UNSERER szene)."""
    world = bpy.data.worlds.get("BBS_MC_World")
    if world is None:
        world = bpy.data.worlds.new("BBS_MC_World")
    world.use_nodes = True
    bg = None
    for n in world.node_tree.nodes:
        if n.type == 'BACKGROUND':
            bg = n
            break
    if bg is None:
        bg = world.node_tree.nodes.new("ShaderNodeBackground")
    d = _daylight(sky_angle)
    col = _lerp3(_SKY_NIGHT, _SKY_DAY, d)
    bg.inputs["Color"].default_value = (col[0], col[1], col[2], 1.0)
    bg.inputs["Strength"].default_value = 0.4 + 0.6 * d
    # kein volume am world-output — rendert in EEVEE schwarz (siehe _build_fog-doc);
    # altlast einer früheren version aufräumen falls vorhanden
    stale = world.node_tree.nodes.get("BBS_MC_Fog")
    if stale is not None:
        world.node_tree.nodes.remove(stale)
    # SCENE.WORLD-RESTORE (end-review fund 6): das original VOR dem tausch merken, damit
    # _clear_light_collection/_restore_scene_world es zurückhängen kann — sonst ist der
    # himmel des users nach EINEM import dauerhaft ersetzt (invariante 5 verletzt, live
    # gezeigt). Nur merken wenn das aktuelle world NICHT unseres ist: ein re-import darf
    # die erinnerung nie mit BBS_MC_World überschreiben ("" = es gab kein world).
    prev = scene.world
    if prev is None or prev.name != "BBS_MC_World":
        scene[PREV_WORLD_PROP] = "" if prev is None else prev.name
    scene.world = world
    return d, col


def _build_fog(coll, region, sky_col, daylight):
    """MC-distanz-fog v1: REGION-BEGRENZTER volumen-würfel (BBS_MC_FogVolume).

    ⚠️ GEMESSEN 2026-07-17: ein Principled-Volume am WORLD-output rendert in EEVEE 5.2
    KOMPLETT SCHWARZ (welt-volumen integriert über die volumetric/clip-range, nicht bis
    zur geometrie — selbst density 0.008 schluckt alles). Darum objekt-volumen: würfel
    über die region, fog wirkt nur auf strahlen IM würfel, himmel bleibt sauber — das
    entspricht MCs fog-semantik ohnehin besser. Dichte = v1-augenmaß, feintuning am
    side-by-side-gate (plan 3.5-rest)."""
    if region is None or len(region) != 6:
        return
    x0, y0, z0, x1, y1, z1 = [float(v) for v in region]
    mesh = bpy.data.meshes.new("BBS_MC_FogVolume")
    obj = bpy.data.objects.new("BBS_MC_FogVolume", mesh)
    # MC-region -> blender-AABB (bl_x=x, bl_y=-z, bl_z=y), +1 weil max inklusiv ist
    bx0, bx1 = x0, x1 + 1.0
    by0, by1 = -(z1 + 1.0), -z0
    bz0, bz1 = y0, y1 + 1.0
    verts = [(x, y, z) for x in (bx0, bx1) for y in (by0, by1) for z in (bz0, bz1)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    mesh.from_pydata(verts, [], faces)
    mat = bpy.data.materials.get("BBS_MC_FogMat")
    if mat is None:
        mat = bpy.data.materials.new("BBS_MC_FogMat")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    vol = nt.nodes.new("ShaderNodeVolumePrincipled")
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(vol.outputs["Volume"], out.inputs["Volume"])
    vol.inputs["Color"].default_value = (sky_col[0], sky_col[1], sky_col[2], 1.0)
    # REGION-SKALIERTE dichte (W6-S-research, 77a6748): vanilla-fog ist PROZENT-relativ zur
    # render distance, nicht absolut — eine fixe konstante wirkt in großen exports zu dünn.
    # Beer-Lambert: dichte = -ln(T_ziel)/strecke. Preset "klar": T=0.5 über die größte
    # horizontale ausdehnung (0.693/extent) — bewusst dezent; "normal" wäre T=0.15
    # (1.897/extent), fürs side-by-side-feintuning mit dem user. Tageszeit moduliert leicht.
    extent = max(bx1 - bx0, by1 - by0, 1.0)
    vol.inputs["Density"].default_value = (0.693 / extent) * (0.7 + 0.3 * daylight)
    if "Emission Strength" in vol.inputs:
        vol.inputs["Emission Strength"].default_value = 0.0
    mesh.materials.append(mat)
    obj.display_type = 'WIRE'   # im viewport nicht im weg, rendert trotzdem
    obj.hide_select = True      # user klickt nicht versehentlich den fog-kasten an
    coll.objects.link(obj)


def _build_sun(coll, sky_angle, time_of_day=None):
    """Sonne + mond: je eine SUN-LAMPE (beleuchtung) UND eine sichtbare emissive SCHEIBE
    (himmelskörper). Winkel: rotation um die blender-Y-achse — MC's sonne läuft den ost-west-
    bogen (MC +x=osten -> bl +x). VORZEICHEN LIVE VERIFIZIERT (dossier: in-game-sonne ost @
    morgen == (0.87,0,0.50), kein flip).

    Die SCHEIBE steht dort, WO das licht HERKOMMT (disc-richtung = -lampen-lichtvektor), aus
    der lampen-matrix abgeleitet — so stimmen scheibe und schatten IMMER überein (kein zweiter,
    driftender winkel). Sonnen-lampe emittiert entlang lokal -Z; disc-richtung = lokal +Z."""
    import mathutils
    theta = sky_angle * 2.0 * math.pi
    d = _daylight(sky_angle)

    sun_data = bpy.data.lights.new("BBS_WorldSun", 'SUN')
    sun_data.energy = 0.02 + 4.0 * d
    sun_data.color = _lerp3((1.0, 0.55, 0.3), (1.0, 0.98, 0.95), min(1.0, d * 2.0))
    sun_data.angle = math.radians(0.53)   # reale sonnen-winkelgröße = knackige MC-schatten
    sun = bpy.data.objects.new("BBS_WorldSun", sun_data)
    sun.rotation_euler = (0.0, -theta, 0.0)
    coll.objects.link(sun)

    # sichtbare SONNEN-SCHEIBE in richtung des sonnenlichts-ursprungs (lokal +Z der lampe)
    sun_dir = sun.rotation_euler.to_matrix() @ mathutils.Vector((0.0, 0.0, 1.0))
    sun_png = _env_tex_png(_TEX_SUN)
    if sun_png is not None:
        sun_mat = _sky_body_material("BBS_MC_SunMat", sun_png, emission=8.0)
        _build_sky_disc(coll, "BBS_WorldSunDisc", sun_mat, _SUN_DISC_SIZE, sun_dir)

    # MOND-lampe + -scheibe (immer gebaut; nachts hell, tags dunkel — vanilla zeigt beide
    # zur dämmerung gleichzeitig). Mond steht dem sonnen-arc GEGENÜBER (theta + pi).
    moon_data = bpy.data.lights.new("BBS_WorldMoon", 'SUN')
    moon_data.energy = 0.15 if d < 0.2 else 0.0      # nur nachts beleuchtet der mond
    moon_data.color = (0.6, 0.7, 1.0)
    moon = bpy.data.objects.new("BBS_WorldMoon", moon_data)
    moon.rotation_euler = (0.0, -theta + math.pi, 0.0)
    coll.objects.link(moon)

    moon_dir = moon.rotation_euler.to_matrix() @ mathutils.Vector((0.0, 0.0, 1.0))
    moon_png = _env_tex_png(_TEX_MOON)
    if moon_png is not None:
        phase = _moon_phase(time_of_day)
        col = phase % 4
        row = (phase // 4) % 2
        uv_rect = (col / 4.0, row / 2.0, (col + 1) / 4.0, (row + 1) / 2.0)
        # mond nachts hell, tags stark gedimmt (nicht ganz aus — der user soll die scheibe
        # sehen, aber sie ist tagsüber gegen den blauen himmel schwach, wie in MC)
        moon_emis = 5.0 if d < 0.2 else 1.2
        moon_mat = _sky_body_material("BBS_MC_MoonMat", moon_png, emission=moon_emis,
                                      uv_rect=uv_rect)
        _build_sky_disc(coll, "BBS_WorldMoonDisc", moon_mat, _MOON_DISC_SIZE, moon_dir)
    return theta


def _build_point_lights(coll, entries):
    """Punkt-lichter für gezielte quellen. entries: liste "x y z lum id" (MC-koords)."""
    candidates = []
    for raw in entries:
        toks = raw.split()
        if len(toks) != 5:
            continue
        x, y, z, lum = int(toks[0]), int(toks[1]), int(toks[2]), int(toks[3])
        bid = toks[4]
        if bid in _AREA_EMITTERS:
            continue
        candidates.append((lum, x, y, z, bid))
    candidates.sort(reverse=True)   # hellste zuerst — die überleben ein cap
    dropped = max(0, len(candidates) - MAX_POINT_LIGHTS)
    if dropped:
        print("[bbs-bridge] licht-cap: %d punkt-lichter, %d schwächste NICHT gebaut "
              "(MAX_POINT_LIGHTS=%d)" % (len(candidates), dropped, MAX_POINT_LIGHTS))
    made = 0
    for lum, x, y, z, bid in candidates[:MAX_POINT_LIGHTS]:
        data = bpy.data.lights.new("BBS_WL", 'POINT')
        # energie ~ luminance²: MC-licht fällt linear pro block, blender quadratisch —
        # die quadrierung gleicht den sichtbaren radius grob an (feintuning = pixel-gate).
        # W6-S-research (77a6748): vanillas lightmap ist exponentiell-SATURIEREND (0.8^(15-lvl));
        # bei lum 12-15 ~deckungsgleich mit quadratisch, bei lum 4-10 wäre sättigung 3-8x heller.
        # NICHT wechseln ohne side-by-side-gate — aber DAS ist der hebel falls schwache
        # sekundärlichter (glow_lichen, amethyst) im vergleich zu dunkel wirken.
        data.energy = (lum / 15.0) ** 2 * 60.0
        data.color = _POINT_COLORS.get(bid, DEFAULT_POINT_COLOR)
        data.shadow_soft_size = 0.25
        obj = bpy.data.objects.new("BBS_WL_%d_%d_%d" % (x, y, z), data)
        # MC-block -> blender: bl_x=x, bl_y=-z, bl_z=y, +0.5 = block-mitte
        obj.location = (x + 0.5, -(z + 0.5), y + 0.5)
        coll.objects.link(obj)
        made += 1
    return made, dropped


def _apply_emission_materials():
    """Flächige leuchter: Principled-emission aus der eigenen base-textur speisen.
    Idempotent — erneuter lauf setzt dieselben werte, stapelt keine nodes."""
    from .world_import import _base_color_tex
    touched = 0
    for mat in bpy.data.materials:
        if not mat.name.startswith("minecraft_block_") or not mat.use_nodes:
            continue
        strength = None
        for hint, s in _EMISSION_STRENGTH.items():
            if hint in mat.name:
                strength = max(s, strength or 0.0)
        if strength is None:
            continue
        bsdf, tex = _base_color_tex(mat.node_tree)
        if bsdf is None:
            continue
        emis_col = bsdf.inputs.get("Emission Color")
        emis_str = bsdf.inputs.get("Emission Strength")
        if emis_col is None or emis_str is None:
            continue   # exotischer node-aufbau — lieber dunkel als kaputt
        if tex is not None and not emis_col.is_linked:
            mat.node_tree.links.new(tex.outputs["Color"], emis_col)
        emis_str.default_value = strength
        touched += 1
    return touched


def apply_world_meta(obj_path):
    """Manifest neben der OBJ lesen und das licht-setup bauen. Fehlt das manifest
    (älterer java-stand), passiert NICHTS — welt-import bleibt voll funktional."""
    meta_path = os.path.join(os.path.dirname(obj_path), "world_meta.json")
    if not os.path.isfile(meta_path):
        return None
    try:
        with open(meta_path, "r", encoding="utf-8") as fh:
            meta = json.load(fh)
    except (OSError, ValueError) as e:
        print("[bbs-bridge] world_meta.json unlesbar:", e)
        return None
    _clear_light_collection()
    coll = bpy.data.collections.new(LIGHT_COLLECTION)
    bpy.context.scene.collection.children.link(coll)
    sky_angle = float(meta.get("skyAngle", 0.0))
    time_of_day = meta.get("timeOfDay")
    d, sky_col = _build_sky(bpy.context.scene, sky_angle)
    _build_sun(coll, sky_angle, time_of_day)
    # sichtbare wolken-ebene bei welt-y=192 (MC-cloud-höhe), gekachelt über die region
    clouds = _build_clouds(coll, meta.get("region"), sky_angle)
    _build_fog(coll, meta.get("region"), sky_col, d)
    lights, dropped = _build_point_lights(coll, meta.get("emissive") or [])
    mats = _apply_emission_materials()
    print("[bbs-bridge] MC-licht: skyAngle=%.4f (tag %.0f%%), %d punkt-lichter (%d gecappt), "
          "%d emission-materialien, sonne+mond-scheibe+wolken=%s, zeit %s"
          % (sky_angle, d * 100, lights, dropped, mats,
             "ja" if clouds is not None else "nein(keine jar/textur)", time_of_day))
    return {"daylight": d, "lights": lights, "dropped": dropped, "emissionMats": mats,
            "clouds": clouds is not None}
