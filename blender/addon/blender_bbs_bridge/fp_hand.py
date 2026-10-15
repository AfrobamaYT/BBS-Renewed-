"""TASK 5.1 — first-person hand+HUD-compositing-rig, als eigenes modul verpackt.

Die KONSTRUKTION hier war schon BEWIESEN als live-objekte in einer Blender-test-instanz
(FOV-isolation welt-RMS 0.0, hand/HUD-toggles per render bestaetigt -- siehe die 5
BBS_FP_render*.png/BBS_FP_hotbar_dummy.png in C:/Users/eres5/AppData/Local/Temp/claude/
fp_rig/). Dieses modul goss NUR die konstruktion in idempotenten code -- die werte unten
(mesh-verts, transforms, node-graph, driver-formeln) sind 1:1 aus der LEBENDEN szene per
Blender-MCP (:9876) introspektion abgelesen (2026-07-17), nicht neu erfunden.

## ARCHITEKTUR (kein "ein transform, 2 kameras"-empty wie im urspruenglichen plan-v2-entwurf
## -- die tatsaechlich gebaute + bewiesene loesung ist einfacher und robuster):
  - EIGENE szene `HAND_SCENE_NAME` ("BBS_FP_Hand"): eigene kamera (`BBS_FP_CamB`, sitzt fix
    bei origin/identity-rotation), eigene sonne, ein arm-PLATZHALTER (quader-mesh, "BBS_FP_Arm",
    an die kamera geparented -- klassische viewmodel-technik: das mesh bewegt sich MIT der
    kamera, nicht die kamera mit dem game). Eigene film_transparent-render-settings (1920x1080)
    damit die alpha aus dem hand-render fuer den compositor nutzbar ist.
  - Der COMPOSITOR sitzt NICHT auf der hand-szene, sondern auf der WELT-szene (`build_rig`s
    `scene`-param): eine node-GRUPPE (`COMPOSITE_GROUP_NAME`) mit je einem Render-Layers-node
    pro szene (welt + hand), AlphaOver welt<-hand, dann ein HUD-platzhalter-bild (scale+
    translate+AlphaOver) obendrauf, `NodeGroupOutput` -> `scene.compositing_node_group`
    (Blender 5.2: KEIN `CompositorNodeComposite` mehr, siehe paket-CLAUDE.md).
  - FOV-ISOLATION: die hand-kamera hat ihr EIGENES sichtfeld (`PROP_FOV` custom-prop auf der
    hand-szene, default 65 grad) -- komplett unabhaengig von der welt-kamera-FOV. Verdrahtet
    per SCRIPTED-driver auf `camera.data.lens` (NIE `.angle` -- Blender-5.2-falle, siehe
    paket-CLAUDE.md "driver nur auf camera.data.lens"). Das IST das 5.1-kern-gate: welt-crop
    RMS bleibt 0.0 waehrend die hand-FOV sich aendert, weil beide render-passes komplett
    getrennte kamera-objekte in komplett getrennten szenen sind (keine geteilte kamera-property
    die beide FOVs koppeln koennte).
  - TOGGLES: `PROP_SHOW_HAND`/`PROP_SHOW_HUD` (float-custom-props auf der WELT-szene, 0..1 --
    bewusst FLOAT statt bool: erlaubt spaeteres weiches ein/ausblenden statt hartem an/aus,
    siehe plan-v2 task 5.3b "hud-ease 0.25/tick"). Treiben je den `Factor`-eingang eines
    AlphaOver-node per SCRIPTED-driver (`"var"`-ausdruck, SINGLE_PROP auf `["prop_name"]`).
  - node-EINGABE-OPTIONEN (Scale-type/interpolation/extension/AlphaOver-type) sind in Blender
    5.2 INPUT-SOCKETS (MENU-typ), NICHT python-attribute wie `node.space` -- siehe
    paket-CLAUDE.md. Werte sind STRINGS wie 'Relative'/'Nearest'/'Clip'/'Over', keine
    SCREAMING_CASE-enums (gemessen per introspektion, nicht geraten).

## PLATZHALTER, NICHT final (siehe plan-v2 5.2/5.2b/5.3a/5.3b -- ausdruecklich offen):
  - Arm = seit 5.1b-rest (W9-B5) der ECHTE rechte alex-arm, aus slim.bobj extrahiert und
    in der bytecode-gemessenen vanilla-FP-pose (modul fp_arm.py: selektion, pose-kette,
    geteiltes BBS_MDL_alex.png-material -- KEIN duplikat, Closest bleibt). Der 5.1-quader
    lebt nur noch als FALLBACK wenn die bobj-quelle fehlt/kaputt ist (log sagt es dann
    deutlich); skin-austausch = png tauschen (gleiches UV-layout), dokumentiert in fp_arm.
  - HUD = 9-streifen-checker-platzhalter, PROZEDURAL generiert (kein pfad auf eine externe
    datei -- die scratch-PNGs im 5.1-report ueberleben keinen neustart/anderen rechner, siehe
    oben; darum hier `bpy.data.images.new` + gesetzte pixel + `pack()`, komplett im .blend
    selbst enthalten). Echte hotbar/item-icons kommen aus dem client-jar via 5.2b/5.3a.
  - Kanal-mapping (5.1b-VORARBEIT, 2026-07-17): der TRANSPORT lebt in `fp_channels.py` --
    dieses modul stellt dafuer die drei anker: (1) bob-empty `BOB_OBJ_NAME` zwischen kamera
    und arm (bob-kurven landen dort, arm-pose bleibt konstant), (2) hand-szenen-prop
    `PROP_SPRINT_FACTOR` als `sfac` im lens-driver (sprint-FOV multiplikativ, welt-szene
    unberuehrt = FOV-isolation haelt), (3) die zwei welt-szenen-show-props als ease-ziel
    (waren schon immer float genau dafuer). use-pose/equip-dip/swing + POV-bindung +
    skin-arm bleiben offener 5.1b-rest.
"""

import bpy

# ---- namen: ALLE datenbloecke die dieses modul anlegt sind BBS_FP_-prefixed (paket-regel:
# users objekte/actions nie anfassen, alles eigene rueckstandslos entfernbar). Siehe
# CLEANUP_MANIFEST unten -- remove_rig() geht GENAU diese liste durch, nichts mehr, nichts
# weniger. ----
HAND_SCENE_NAME = "BBS_FP_Hand"
HAND_WORLD_NAME = "BBS_FP_HandWorld"
CAM_OBJ_NAME = "BBS_FP_CamB"
CAM_DATA_NAME = "BBS_FP_CamB_Data"
BOB_OBJ_NAME = "BBS_FP_HandBob"   # 5.1b: bob-empty ZWISCHEN kamera und arm (kanal-baken,
                                   # siehe fp_channels.py) — kamera-lokal identity solange
                                   # keine kanaele gebacken sind (arm-pose unveraendert)
ARM_OBJ_NAME = "BBS_FP_Arm"
ARM_MESH_NAME = "BBS_FP_ArmMesh"
ARM_MAT_NAME = "BBS_FP_SkinMat"
SUN_OBJ_NAME = "BBS_FP_HandSun"
SUN_DATA_NAME = "BBS_FP_HandSun_Data"
HUD_IMG_NAME = "BBS_FP_HUD_Hotbar_Dummy"
COMPOSITE_GROUP_NAME = "BBS_FP_Composite"

# compositor-node-namen (innerhalb von COMPOSITE_GROUP_NAME)
_N_OUT = "BBS_FP_GroupOutput"
_N_RL_WORLD = "BBS_FP_RLayers_World"
_N_RL_HAND = "BBS_FP_RLayers_Hand"
_N_AO_HAND = "BBS_FP_AlphaOver_Hand"
_N_IMG_HUD = "BBS_FP_Image_HUD"
_N_SCALE_HUD = "BBS_FP_Scale_HUD"
_N_TRANSLATE_HUD = "BBS_FP_Translate_HUD"
_N_AO_HUD = "BBS_FP_AlphaOver_HUD"

# custom-property-namen (registriert als bpy.props in __init__.py -- paket-konvention "props
# zentral dort"; hier nur die STRINGS als single-source-of-truth fuer driver-data-paths).
PROP_FOV = "bbsfp_hand_fov_deg"                # auf der HAND-szene (stirbt mit ihr)
PROP_SHOW_HAND = "bbsfp_show_hand"             # auf der WELT-szene (build_rig-param `scene`)
PROP_SHOW_HUD = "bbsfp_show_hud"               # auf der WELT-szene
PROP_SPRINT_FACTOR = "bbsfp_sprint_fov_factor" # auf der HAND-szene (stirbt mit ihr) —
                                               # sprint-FOV-multiplikator (1.0..1.15), von
                                               # fp_channels.apply_channels gebacken; der
                                               # lens-driver liest ihn als `sfac`. REINE
                                               # custom-prop, BEWUSST nicht in __init__.py
                                               # registriert: kein UI-slider (wert kommt aus
                                               # kanaelen, nicht vom user), kein register-hook

DEFAULT_FOV_DEG = 65.0
HAND_RENDER_RES = (1920, 1080)

# arm-quader-mass + pose: NUR NOCH FALLBACK (5.1b-rest W9-B5 -- der echte arm kommt aus
# fp_arm.build_arm_mesh, diese konstanten greifen wenn die bobj-quelle fehlt/kaputt ist).
# 8 verts / 6 quad-faces = ein simpler kasten, aus der bewiesenen 5.1-szene abgelesen.
_ARM_VERTS = (
    (-0.125, -0.125, -0.375), (-0.125, -0.125, 0.375), (-0.125, 0.125, -0.375), (-0.125, 0.125, 0.375),
    (0.125, -0.125, -0.375), (0.125, -0.125, 0.375), (0.125, 0.125, -0.375), (0.125, 0.125, 0.375),
)
_ARM_FACES = ((0, 1, 3, 2), (2, 3, 7, 6), (6, 7, 5, 4), (4, 5, 1, 0), (2, 6, 4, 0), (7, 3, 1, 5))
_ARM_LOCATION = (0.35, -0.22, -1.1)                              # kamera-lokal (arm ist child der kamera)
_ARM_ROTATION = (0.2617993950843811, 0.1745329201221466, -0.4363323152065277)  # ~15/10/-25 grad
_ARM_BASE_COLOR = (0.75, 0.55, 0.40, 1.0)                        # skin-platzhalter-ton
_ARM_ROUGHNESS = 0.75

_CAM_SENSOR_WIDTH = 36.0
_CAM_SENSOR_HEIGHT = 24.0

_SUN_ROTATION = (0.9599310755729675, 0.2617993950843811, 3.4906585216522217)
_SUN_ENERGY = 3.0

_HANDWORLD_COLOR = (0.6, 0.6, 0.65, 1.0)     # neutral-kuehl, NUR fuer die arm-beleuchtung (film_transparent
_HANDWORLD_STRENGTH = 0.5                   # macht den hintergrund selbst unsichtbar, siehe _configure_hand_scene_render)

_HUD_DUMMY_SIZE = (182, 22)      # 9-slot-hotbar-platzhalter-groesse, aus der bewiesenen szene abgelesen
_HUD_DUMMY_SLOTS = 9
_HUD_SCALE_XY = 2.0
_HUD_TRANSLATE_Y = -152.0

# ---- CLEANUP-MANIFEST: jeder datenblock den build_rig() anlegt. remove_rig() arbeitet diese
# kategorien GENAU in dieser reihenfolge ab (objekte -> szene -> rest-daten -> node-gruppe ->
# custom-properties). Reihenfolge ist PFLICHT: eine mesh/kamera/licht/material/bild darf erst
# weg NACHDEM das objekt das sie haelt weg ist, sonst bleibt sie mit users>0 haengen und der
# naechste build_rig() findet sie nicht wieder (.001-dublette) -- exakt die falle die
# world_import._clear_world_collection schon dokumentiert (mesh VOR material VOR image).
CLEANUP_MANIFEST = {
    # kinder VOR eltern (arm haengt am bob-empty, das an der kamera)
    "objects": (ARM_OBJ_NAME, BOB_OBJ_NAME, CAM_OBJ_NAME, SUN_OBJ_NAME),
    "scenes": (HAND_SCENE_NAME,),
    "meshes": (ARM_MESH_NAME,),
    "cameras": (CAM_DATA_NAME,),
    "lights": (SUN_DATA_NAME,),
    # ARM_MAT_NAME = 5.1-quader-fallback-material. "BBS_FP_ArmSkin" = das EIGENE arm-skin-
    # material das fp_arm.set_arm_skin beim skin-tausch anlegt (entkoppelt vom geteilten alex,
    # s. fp_arm.ARM_SKIN_MATERIAL_NAME) — exklusiv fp-arm-besitz, darum hier (kein shared_-block).
    "materials": (ARM_MAT_NAME, "BBS_FP_ArmSkin"),
    "images": (HUD_IMG_NAME,),
    "worlds": (HAND_WORLD_NAME,),
    "node_groups": (COMPOSITE_GROUP_NAME,),
    "scene_props": (PROP_SHOW_HAND, PROP_SHOW_HUD),   # PROP_FOV + PROP_SPRINT_FACTOR sterben
                                                       # MIT der hand-szene oben
    # GETEILTE datenbloecke (W9-B5, echter arm): das alex-material+skin-image teilt sich der
    # arm mit dem MC_Actors-alex. remove_rig entfernt sie NUR bei users==0 (kein alex im
    # file = wir waren der einzige nutzer) und OHNE user-flag-reset/warnung -- users>0 ist
    # hier der ERWARTETE zustand, nicht der fehlerfall wie bei den exklusiven kategorien.
    # Namen muessen fp_arm.SHARED_MATERIAL_NAME/-IMAGE_NAME entsprechen (dort hergeleitet).
    "shared_materials": ("BBS_MDL_alex.png",),
    "shared_images": ("alex.png",),
}


# ============================================================================
# get-or-create-bausteine (jeder idempotent: 2. aufruf findet das vorhandene datum per NAME
# und aktualisiert es, statt ein .001-duplikat anzulegen)
# ============================================================================

def _get_or_create_hand_world():
    w = bpy.data.worlds.get(HAND_WORLD_NAME)
    if w is None:
        w = bpy.data.worlds.new(HAND_WORLD_NAME)
    w.use_nodes = True
    bg = next((n for n in w.node_tree.nodes if n.type == 'BACKGROUND'), None)
    if bg is not None:
        bg.inputs["Color"].default_value = _HANDWORLD_COLOR
        bg.inputs["Strength"].default_value = _HANDWORLD_STRENGTH
    return w


def _get_or_create_arm_mesh():
    """Arm-geometrie in den persistenten ARM_MESH_NAME-datenblock bauen. Gibt
    (mesh, geteiltes_material_oder_None) zurueck: material != None heisst "echter
    alex-arm steht" (fp_arm-pfad, W9-B5), None heisst 5.1-quader-fallback (die
    bobj-quelle fehlt/kaputt -- geloggt, kein stilles halb-fertig).
    Beide pfade RESETTEN die geometrie bei jedem lauf -- self-healing falls jemand
    im edit-mode dran rumgeschnitten hat (beweis c: "rig komplett neu da")."""
    me = bpy.data.meshes.get(ARM_MESH_NAME)
    if me is None:
        me = bpy.data.meshes.new(ARM_MESH_NAME)
    shared_mat = None
    try:
        from . import fp_arm
        shared_mat = fp_arm.build_arm_mesh(me)
    except Exception as e:   # noqa: BLE001 -- quellen-fehler darf build_rig nie killen
        print("[bbs-bridge] fp_hand: echter arm nicht baubar (%s) -> quader-fallback" % e)
    if shared_mat is None:
        me.clear_geometry()
        me.from_pydata(_ARM_VERTS, [], _ARM_FACES)
        me.update()
    return me, shared_mat


def _get_or_create_arm_material():
    mat = bpy.data.materials.get(ARM_MAT_NAME)
    if mat is None:
        mat = bpy.data.materials.new(ARM_MAT_NAME)
    mat.use_nodes = True
    mat.diffuse_color = (0.8, 0.8, 0.8, 1.0)
    bsdf = next((n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = _ARM_BASE_COLOR
        bsdf.inputs["Roughness"].default_value = _ARM_ROUGHNESS
    return mat


def _get_or_create_hud_dummy_image():
    """9-streifen-checker als sichtbarer HUD-platzhalter -- PROZEDURAL erzeugt (kein pfad auf
    eine temp-datei, siehe modul-docstring: die 5.1-report-PNGs in AppData/Local/Temp ueberleben
    keinen neustart/anderen rechner). `pack()` haelt die pixel im .blend selbst, unabhaengig
    vom dateisystem. Wird NUR beim allerersten anlegen generiert -- ein 2. build_rig()-lauf
    laesst ein evtl. vom user schon ersetztes bild (z.B. echte 5.2b-hotbar) unangetastet."""
    img = bpy.data.images.get(HUD_IMG_NAME)
    if img is not None:
        return img
    w, h = _HUD_DUMMY_SIZE
    img = bpy.data.images.new(HUD_IMG_NAME, width=w, height=h, alpha=True)
    slot_w = w / _HUD_DUMMY_SLOTS
    pixels = [0.0] * (w * h * 4)
    for y in range(h):
        for x in range(w):
            light = (int(x / slot_w) % 2) == 0
            v = 0.85 if light else 0.15
            idx = (y * w + x) * 4
            pixels[idx] = v
            pixels[idx + 1] = v
            pixels[idx + 2] = v
            pixels[idx + 3] = 1.0
    img.pixels[:] = pixels
    img.pack()
    return img


def _get_or_create_cam():
    obj = bpy.data.objects.get(CAM_OBJ_NAME)
    cam_data = bpy.data.cameras.get(CAM_DATA_NAME)
    if cam_data is None:
        cam_data = bpy.data.cameras.new(CAM_DATA_NAME)
    cam_data.sensor_fit = 'HORIZONTAL'
    cam_data.sensor_width = _CAM_SENSOR_WIDTH
    cam_data.sensor_height = _CAM_SENSOR_HEIGHT
    if obj is None or obj.type != 'CAMERA':
        obj = bpy.data.objects.new(CAM_OBJ_NAME, cam_data)
    else:
        obj.data = cam_data
    obj.location = (0.0, 0.0, 0.0)
    obj.rotation_euler = (0.0, 0.0, 0.0)
    return obj


def _get_or_create_sun():
    obj = bpy.data.objects.get(SUN_OBJ_NAME)
    light_data = bpy.data.lights.get(SUN_DATA_NAME)
    if light_data is None:
        light_data = bpy.data.lights.new(SUN_DATA_NAME, type='SUN')
    light_data.energy = _SUN_ENERGY
    if obj is None or obj.type != 'LIGHT':
        obj = bpy.data.objects.new(SUN_OBJ_NAME, light_data)
    else:
        obj.data = light_data
    obj.location = (0.0, 0.0, 0.0)
    obj.rotation_euler = _SUN_ROTATION
    return obj


def _get_or_create_bob(cam_obj):
    """5.1b: bob-empty zwischen kamera und arm — der KANAL-anker. fp_channels bakt
    view-bob (location x/y + rotation x/z, kamera-lokal) hierauf; ohne gebackene
    kanaele bleibt es identity und der arm steht exakt wie in 5.1 (welt-transform
    unveraendert, die 5.1-render-gates bleiben gueltig). Transforms werden bei jedem
    build_rig NUR zurueckgesetzt wenn KEINE animation drauf liegt (ein re-build darf
    gebackene kanaele nicht wegwischen — analog PROP_FOV-verhalten)."""
    obj = bpy.data.objects.get(BOB_OBJ_NAME)
    if obj is None or obj.type != 'EMPTY':
        obj = bpy.data.objects.new(BOB_OBJ_NAME, None)
        obj.empty_display_type = 'PLAIN_AXES'
        obj.empty_display_size = 0.1
    obj.parent = cam_obj
    obj.rotation_mode = 'XYZ'     # matrix-stack-ordnung translate->rotZ->rotX == euler XYZ
                                   # (siehe fp_channels.py docstring "WAS WOHIN")
    if getattr(obj, "animation_data", None) is None or obj.animation_data.action is None:
        obj.location = (0.0, 0.0, 0.0)
        obj.rotation_euler = (0.0, 0.0, 0.0)
    obj.scale = (1.0, 1.0, 1.0)
    return obj


def _get_or_create_arm(bob_obj):
    me, shared_mat = _get_or_create_arm_mesh()
    obj = bpy.data.objects.get(ARM_OBJ_NAME)
    if obj is None or obj.type != 'MESH':
        obj = bpy.data.objects.new(ARM_OBJ_NAME, me)
    else:
        obj.data = me
    # echter arm (W9-B5): GETEILTES BBS_MDL_alex.png-material (dasselbe datenblock wie der
    # MC_Actors-alex, kein duplikat) + die vanilla-FP-pose aus fp_arm (bytecode-kette,
    # ursprung == schulter-gelenk). Fallback: 5.1-quader mit platzhalter-material/-pose.
    if shared_mat is not None:
        from . import fp_arm   # import ist hier schon warm (arm kam gerade daraus)
        mat = shared_mat
        loc, rot = fp_arm.FP_ARM_LOCATION, fp_arm.FP_ARM_ROTATION
    else:
        mat = _get_or_create_arm_material()
        loc, rot = _ARM_LOCATION, _ARM_ROTATION
    if len(obj.data.materials) == 0:
        obj.data.materials.append(mat)
    else:
        obj.data.materials[0] = mat
    obj.parent = bob_obj          # viewmodel-technik: arm haengt (via bob-empty, 5.1b) an der
    obj.location = loc            # kamera, nicht am world-transform
    obj.rotation_euler = rot
    obj.scale = (1.0, 1.0, 1.0)   # BEWUSST 1.0 auch fuer den echten arm: der vanilla-FP-pfad
                                   # rendert OHNE den 0.9375-actor-scale (fp_arm-docstring)
    return obj


def _link_to_scene(scene, obj):
    """Objekt in die scene-collection linken, idempotent (kein doppel-link bei 2. build_rig()-
    lauf -- bpy.data.objects.new() verlinkt NICHT automatisch, ein ungeschuetzter zweiter
    `.link()`-aufruf auf dieselbe collection wirft RuntimeError, darum der membership-check)."""
    if obj.name not in scene.collection.objects:
        scene.collection.objects.link(obj)


def _configure_hand_scene_render(scene):
    scene.render.resolution_x, scene.render.resolution_y = HAND_RENDER_RES
    scene.render.film_transparent = True   # PFLICHT: alpha-kanal ist wie der arm ins compositing
                                            # der welt-szene alpha-over-t wird (siehe modul-docstring)
    scene.render.engine = 'BLENDER_EEVEE'
    scene.view_settings.view_transform = 'Standard'   # dossier-falle: NICHT 'AgX'/'Filmic' hier


def _ensure_driver(id_data, data_path, index, expression, var_specs):
    """Legt einen SCRIPTED-driver an <data_path>[index] an oder aktualisiert ihn falls er
    schon existiert (idempotent -- ein 2. build_rig()-lauf dupliziert keine fcurves/variablen).
    var_specs: liste von (name, id_type, ziel_id, ziel_data_path), z.B.
    ('fov', 'SCENE', hand_scene, '["bbsfp_hand_fov_deg"]')."""
    ad = id_data.animation_data_create()
    fc = ad.drivers.find(data_path, index=index)
    if fc is None:
        fc = ad.drivers.new(data_path, index=index)
    drv = fc.driver
    drv.type = 'SCRIPTED'
    drv.expression = expression
    for v in list(drv.variables):
        drv.variables.remove(v)
    for name, id_type, target_id, target_path in var_specs:
        var = drv.variables.new()
        var.name = name
        var.type = 'SINGLE_PROP'
        tgt = var.targets[0]
        tgt.id_type = id_type
        tgt.id = target_id
        tgt.data_path = target_path
    return fc


def _get_or_create_hand_scene():
    scene = bpy.data.scenes.get(HAND_SCENE_NAME)
    if scene is None:
        scene = bpy.data.scenes.new(HAND_SCENE_NAME)
    scene.world = _get_or_create_hand_world()
    _configure_hand_scene_render(scene)
    if PROP_FOV not in scene.keys():          # NUR beim ersten mal -- user-getunter FOV-wert
        scene[PROP_FOV] = DEFAULT_FOV_DEG      # bleibt bei einem re-build erhalten (siehe docstring build_rig)
    if PROP_SPRINT_FACTOR not in scene.keys():  # 5.1b: sprint-multiplikator, neutral 1.0 --
        scene[PROP_SPRINT_FACTOR] = 1.0          # gebackene kurven (fp_channels) ueberleben re-build

    cam_obj = _get_or_create_cam()
    _link_to_scene(scene, cam_obj)
    scene.camera = cam_obj

    bob_obj = _get_or_create_bob(cam_obj)
    _link_to_scene(scene, bob_obj)

    arm_obj = _get_or_create_arm(bob_obj)
    _link_to_scene(scene, arm_obj)

    sun_obj = _get_or_create_sun()
    _link_to_scene(scene, sun_obj)

    # FOV-ISOLATION-driver: lens = (sensor_width/2) / tan(radians(fov*sfac)/2), fov aus DIESER
    # szene ("BBS_FP_Hand"), sensor_width aus der kamera selbst -- komplett unabhaengig von
    # der welt-kamera (siehe modul-docstring "FOV-ISOLATION"). NIE auf .angle (Blender-5.2-falle).
    # 5.1b: sfac = sprint-FOV-faktor (BBS: fov_effektiv = basis_fov * faktor, semantik-referenz
    # §3 / TrackerClientClip.adjustFovForSprint) — MULTIPLIKATIV im winkel-raum VOR der
    # lens-umrechnung, exakt wie BBS. sfac=1.0 (default) == 5.1-verhalten byte-identisch.
    _ensure_driver(
        cam_obj.data, "lens", 0,
        "(sw/2.0) / tan(radians(fov*sfac)/2.0)",
        [("fov", "SCENE", scene, '["%s"]' % PROP_FOV),
         ("sfac", "SCENE", scene, '["%s"]' % PROP_SPRINT_FACTOR),
         ("sw", "CAMERA", cam_obj.data, "sensor_width")],
    )
    return scene


# ============================================================================
# compositor (auf der WELT-szene, siehe build_rig)
# ============================================================================

def _get_or_create_node_group():
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    if ng is None:
        ng = bpy.data.node_groups.new(COMPOSITE_GROUP_NAME, 'CompositorNodeTree')
    if not any(it.in_out == 'OUTPUT' and it.name == "Image" for it in ng.interface.items_tree):
        ng.interface.new_socket(name="Image", in_out='OUTPUT', socket_type='NodeSocketColor')
    return ng


def _get_or_create_node(ng, name, bl_idname):
    n = ng.nodes.get(name)
    if n is None:
        n = ng.nodes.new(bl_idname)
        n.name = name
    return n


def _build_compositor(ng, world_scene, hand_scene):
    """Baut/aktualisiert den kompletten node-graph. Link-aufrufe sind idempotent OHNE
    eigene dedup-logik: Blenders NodeTree.links.new() ersetzt automatisch einen vorhandenen
    link auf denselben INPUT-socket (single-link-constraint), ein 2. build_rig()-lauf erzeugt
    also nie parallele/doppelte links."""
    out = _get_or_create_node(ng, _N_OUT, 'NodeGroupOutput')
    rl_world = _get_or_create_node(ng, _N_RL_WORLD, 'CompositorNodeRLayers')
    rl_world.scene = world_scene
    rl_world.layer = world_scene.view_layers[0].name
    rl_hand = _get_or_create_node(ng, _N_RL_HAND, 'CompositorNodeRLayers')
    rl_hand.scene = hand_scene
    rl_hand.layer = hand_scene.view_layers[0].name

    ao_hand = _get_or_create_node(ng, _N_AO_HAND, 'CompositorNodeAlphaOver')
    ao_hand.inputs["Type"].default_value = 'Over'
    ao_hand.inputs["Straight Alpha"].default_value = False

    img_hud = _get_or_create_node(ng, _N_IMG_HUD, 'CompositorNodeImage')
    img_hud.image = _get_or_create_hud_dummy_image()

    scale_hud = _get_or_create_node(ng, _N_SCALE_HUD, 'CompositorNodeScale')
    scale_hud.inputs["Type"].default_value = 'Relative'
    scale_hud.inputs["X"].default_value = _HUD_SCALE_XY
    scale_hud.inputs["Y"].default_value = _HUD_SCALE_XY
    scale_hud.inputs["Frame Type"].default_value = 'Stretch'
    scale_hud.inputs["Interpolation"].default_value = 'Nearest'   # pixel-look, wie world_import's 'Closest'
    scale_hud.inputs["Extension X"].default_value = 'Clip'
    scale_hud.inputs["Extension Y"].default_value = 'Clip'

    translate_hud = _get_or_create_node(ng, _N_TRANSLATE_HUD, 'CompositorNodeTranslate')
    translate_hud.inputs["X"].default_value = 0.0
    translate_hud.inputs["Y"].default_value = _HUD_TRANSLATE_Y
    translate_hud.inputs["Interpolation"].default_value = 'Nearest'
    translate_hud.inputs["Extension X"].default_value = 'Clip'
    translate_hud.inputs["Extension Y"].default_value = 'Clip'

    ao_hud = _get_or_create_node(ng, _N_AO_HUD, 'CompositorNodeAlphaOver')
    ao_hud.inputs["Type"].default_value = 'Over'
    ao_hud.inputs["Straight Alpha"].default_value = False

    # kosmetische positionen (hilft beim nachschauen im node-editor, hat keine funktion)
    rl_world.location = (-800, 300)
    rl_hand.location = (-800, 0)
    ao_hand.location = (-500, 200)
    img_hud.location = (-800, -300)
    scale_hud.location = (-600, -300)
    translate_hud.location = (-400, -300)
    ao_hud.location = (-150, 0)
    out.location = (100, 0)

    ng.links.new(rl_world.outputs["Image"], ao_hand.inputs["Background"])
    ng.links.new(rl_hand.outputs["Image"], ao_hand.inputs["Foreground"])
    ng.links.new(img_hud.outputs["Image"], scale_hud.inputs["Image"])
    ng.links.new(scale_hud.outputs["Image"], translate_hud.inputs["Image"])
    ng.links.new(ao_hand.outputs["Image"], ao_hud.inputs["Background"])
    ng.links.new(translate_hud.outputs["Image"], ao_hud.inputs["Foreground"])
    ng.links.new(ao_hud.outputs["Image"], out.inputs["Image"])

    # TOGGLE-driver: Factor-eingang jedes AlphaOver per scripted-driver auf die welt-szenen-
    # custom-properties (siehe modul-docstring "TOGGLES"). array_index=0 weil Factor ein
    # skalarer VALUE-socket ist (kein subscript wie location[i]).
    _ensure_driver(ng, 'nodes["%s"].inputs[2].default_value' % _N_AO_HAND, 0, "var",
                   [("var", "SCENE", world_scene, '["%s"]' % PROP_SHOW_HAND)])
    _ensure_driver(ng, 'nodes["%s"].inputs[2].default_value' % _N_AO_HUD, 0, "var",
                   [("var", "SCENE", world_scene, '["%s"]' % PROP_SHOW_HUD)])


# ============================================================================
# public API
# ============================================================================

def build_rig(scene=None):
    """Baut ODER aktualisiert den kompletten first-person hand+HUD-compositing-rig.
    IDEMPOTENT: erkennt vorhandene BBS_FP_*-datenbloecke per name und aktualisiert sie statt
    zu duplizieren (siehe die _get_or_create_*-bausteine oben) -- zweimal aufgerufen legt
    NIE ein zweites `BBS_FP_Arm.001` o.ae. an.

    `scene`: die WELT-szene an die der compositor gehaengt wird (default: bpy.context.scene).
    Die hand-szene selbst heisst IMMER `HAND_SCENE_NAME` ("BBS_FP_Hand"), unabhaengig vom
    `scene`-Parameter.

    Was NEU angelegt wird beim 1. aufruf, beim 2. aufruf nur AKTUALISIERT wird:
      - struktur (mesh-geometrie, transforms, material-zuweisung, kamera-sensor, node-graph,
        alle links, alle driver) wird JEDES mal aus den konstanten oben neu gesetzt --
        self-healing, siehe beweis c ("rig komplett neu da").
      - user-tunable werte (PROP_FOV, PROP_SHOW_HAND, PROP_SHOW_HUD) werden NUR beim allerersten
        mal auf ihren default gesetzt -- ein re-build ueberschreibt NICHT was der user am
        FOV-slider/an den toggles eingestellt hat.
    """
    world_scene = scene if scene is not None else bpy.context.scene

    if PROP_SHOW_HAND not in world_scene.keys():
        world_scene[PROP_SHOW_HAND] = 1.0
    if PROP_SHOW_HUD not in world_scene.keys():
        world_scene[PROP_SHOW_HUD] = 1.0

    hand_scene = _get_or_create_hand_scene()

    ng = _get_or_create_node_group()
    _build_compositor(ng, world_scene, hand_scene)
    world_scene.use_nodes = True
    world_scene.compositing_node_group = ng

    print("[bbs-bridge] fp_hand.build_rig: hand-szene '%s' + compositor '%s' -> welt-szene '%s' "
          "(fov=%.1f, show_hand=%.2f, show_hud=%.2f)"
          % (HAND_SCENE_NAME, COMPOSITE_GROUP_NAME, world_scene.name,
             hand_scene[PROP_FOV], world_scene[PROP_SHOW_HAND], world_scene[PROP_SHOW_HUD]))
    return {"hand_scene": hand_scene, "node_group": ng, "world_scene": world_scene}


def _clear_extra_user(block):
    """GEMESSEN (2026-07-17, live remove_rig()-lauf): ein offener Node-Editor der die
    compositor-node-gruppe OHNE pin anzeigt ("browse tree"-dropdown) setzt Blenders internes
    `use_extra_user`-flag -- ein zusaetzlicher phantom-user der NICHT ueber echte ID-pointer
    (scene.compositing_node_group, node.image, ...) laeuft und darum auch NICHT verschwindet
    wenn man nur diese pointer auf None setzt. `block.users` bleibt dann >0 obwohl nichts
    "echtes" mehr drauf zeigt, und remove_rig() haette den datenblock faelschlich fuer belegt
    gehalten (genau DAS ist beim ersten test-lauf passiert -- der user hat den Compositing-
    screen offen, komplett normal waehrend man an diesem feature arbeitet). Gilt genauso fuer
    `use_fake_user`. Ohne dieses reset waere remove_rig() NICHT rueckstandslos gewesen."""
    if getattr(block, "use_fake_user", False):
        block.use_fake_user = False
    if getattr(block, "use_extra_user", False):
        block.use_extra_user = False


def remove_rig(scene=None):
    """Entfernt ALLES was build_rig() anlegte, nach CLEANUP_MANIFEST -- rueckstandslos
    (paket-regel: users objekte/actions bleiben unberuehrt, NUR unsere BBS_FP_*-datenbloecke +
    die beiden welt-szenen-toggle-properties gehen weg). Gibt ein dict {kategorie: [entfernte
    namen]} zurueck (fuer den vorher/nachher-beweis)."""
    world_scene = scene if scene is not None else bpy.context.scene
    removed = {k: [] for k in CLEANUP_MANIFEST}

    # -1. (5.1b) gebackene kanal-kurven ZUERST weg: bob-empty/hand-szenen-actions restlos
    # (gehoeren komplett uns; sonst hinterlaesst das szenen-remove orphan-actions), auf der
    # WELT-szene chirurgisch NUR unsere zwei prop-fcurves (user-animation bleibt) -- die
    # props selbst loescht schritt 4, ihre fcurves wuerden sonst dangeln. try/except:
    # remove_rig muss auch laufen wenn fp_channels mal fehlt/kaputt ist (alt-stand).
    try:
        from . import fp_channels
        fp_channels.clear_channels(world_scene)
    except Exception as e:   # noqa: BLE001 -- cleanup darf nie am kanal-modul sterben
        print("[bbs-bridge] fp_hand.remove_rig: kanal-cleanup uebersprungen: %s" % e)

    # 0. compositor-verdrahtung ZUERST loesen, auf JEDER szene die drauf zeigt (nicht nur
    # world_scene -- ein rig koennte inzwischen an eine andere szene gehaengt worden sein).
    # Erst danach hat die node-gruppe users==0 und darf weg (schritt weiter unten).
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    if ng is not None:
        for sc in bpy.data.scenes:
            if getattr(sc, "compositing_node_group", None) is ng:
                sc.compositing_node_group = None

    # 1. objekte ZUERST (haengen an der hand-szene; muessen weg BEVOR ihre daten (mesh/kamera/
    # licht) unten entfernt werden, sonst bleiben die daten mit users>0 haengen -- gleiche
    # falle wie world_import._clear_world_collection).
    for name in CLEANUP_MANIFEST["objects"]:
        obj = bpy.data.objects.get(name)
        if obj is None:
            continue
        _clear_extra_user(obj)
        try:
            bpy.data.objects.remove(obj, do_unlink=True)
            removed["objects"].append(name)
        except (ReferenceError, RuntimeError) as e:
            print("[bbs-bridge] fp_hand: entfernen von objekt '%s' fehlgeschlagen: %s" % (name, e))

    # 2. die hand-szene selbst (objekte drin sind jetzt weg)
    for name in CLEANUP_MANIFEST["scenes"]:
        sc = bpy.data.scenes.get(name)
        if sc is None:
            continue
        _clear_extra_user(sc)
        try:
            bpy.data.scenes.remove(sc)
            removed["scenes"].append(name)
        except (ReferenceError, RuntimeError) as e:
            print("[bbs-bridge] fp_hand: entfernen von szene '%s' fehlgeschlagen: %s" % (name, e))

    # 3. rest-datenbloecke -- jetzt users==0 (objekte/szene sind weg). REIHENFOLGE PFLICHT:
    # node_groups ZUERST, noch VOR images -- der compositor-node-baum haelt selbst eine
    # referenz auf das HUD-bild (`BBS_FP_Image_HUD`-node.image), also ist img.users erst NACH
    # dem entfernen der node-gruppe wirklich 0 (GEMESSEN: mit images-vor-node_groups blieb das
    # bild beim 1. cleanup-versuch faelschlich als "1 user" haengen -- exakt die
    # mesh-vor-material-vor-image-falle die world_import._clear_world_collection schon dokumentiert,
    # hier auf node_group-vor-image uebertragen).
    _rest = (
        ("node_groups", bpy.data.node_groups),
        ("meshes", bpy.data.meshes), ("cameras", bpy.data.cameras), ("lights", bpy.data.lights),
        ("materials", bpy.data.materials), ("images", bpy.data.images), ("worlds", bpy.data.worlds),
    )
    for category, coll in _rest:
        for name in CLEANUP_MANIFEST[category]:
            block = coll.get(name)
            if block is None:
                continue
            _clear_extra_user(block)   # siehe docstring oben -- NUR "echte" ID-pointer-user zaehlen
            if block.users > 0:
                print("[bbs-bridge] fp_hand: %s '%s' hat noch %d user, NICHT entfernt (pruefen)"
                      % (category, name, block.users))
                continue
            try:
                coll.remove(block)
                removed[category].append(name)
            except (ReferenceError, RuntimeError) as e:
                print("[bbs-bridge] fp_hand: entfernen von %s '%s' fehlgeschlagen: %s" % (category, name, e))

    # 3b. GETEILTE datenbloecke (echter-arm-material + skin-image, W9-B5): material VOR
    # image (der tex-node haelt das bild -- gleiche reihenfolge-falle wie node_groups/images
    # oben). NUR bei users==0 entfernen, STILL ueberspringen sonst: existiert der
    # MC_Actors-alex (oder haelt der user das bild absichtlich mit fake-user), gehoeren
    # die bloecke NICHT uns -- kein _clear_extra_user hier, das waere ein eingriff in
    # fremden besitz (invariante 5).
    for category, coll in (("shared_materials", bpy.data.materials),
                           ("shared_images", bpy.data.images)):
        for name in CLEANUP_MANIFEST[category]:
            block = coll.get(name)
            if block is None or block.users > 0:
                continue
            try:
                coll.remove(block)
                removed[category].append(name)
            except (ReferenceError, RuntimeError) as e:
                print("[bbs-bridge] fp_hand: entfernen von %s '%s' fehlgeschlagen: %s"
                      % (category, name, e))

    # 4. toggle-custom-properties auf der welt-szene (PROP_FOV ist schon mit der hand-szene weg)
    for prop in CLEANUP_MANIFEST["scene_props"]:
        if prop in world_scene.keys():
            try:
                del world_scene[prop]
                removed["scene_props"].append(prop)
            except KeyError:
                pass

    print("[bbs-bridge] fp_hand.remove_rig: %s" % removed)
    return removed
