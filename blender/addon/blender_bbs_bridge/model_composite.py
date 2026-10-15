"""HYBRID-COMPOSITE v1 — BBS-welt (in-game-render) + Blender-modelle pro frame kamera-
synchron uebereinander = das finale bild.

> Plan: docs/superpowers/plans/2026-07-18-hybrid-composite-plan.md (§5 v1-tasks).
> Bausteine wiederverwendet, NICHT neu erfunden — die compositor-mechanik (node-gruppe,
> get-or-create, AlphaOver, scripted-Factor-driver) ist 1:1 die von `fp_hand._build_
> compositor` (dessen `_get_or_create_node`/`_ensure_driver` werden hier direkt importiert),
> die film_transparent-render-settings 1:1 von `fp_hand._configure_hand_scene_render`, der
> externe-bild-als-quelle-weg 1:1 von `hud_overlay` (`CompositorNodeImage`), und die
> PNG-sequenz-render+ffmpeg-mux-hilfen 1:1 von `render_drive` (`film_frame_range`,
> `mux_sequence`, `find_ffmpeg`, `_snapshot/_restore_render_settings`).

## DIE VISION (user), WAS v1 DAVON LIEFERT
  BBS rendert die WELT (blöcke + replays + VFX, MC-look, raw/pixelig) — das ist der
  hintergrund. Blender rendert NUR die ausgewaehlten modelle/actors auf transparentem
  hintergrund — das ist der vordergrund. Beide sehen aus DERSELBEN kamera (`BBS_FilmCam`,
  die dense-gebakte film-fahrt — CameraAuthority/FilmCameraExporter loesen die sync,
  NICHT hier) → pro frame uebereinandergelegt = ein bild in dem MC-welt und Blender-modell
  kamera-synchron zusammensitzen. Der USP: einen MC↔Blender-live-link gibt es sonst nirgends.

## DIE SCHICHTEN (compositor-stapel, unten → oben) — v1
```
  [BBS-frame-PNG]      (CompositorNodeImage)   <- hintergrund: welt+actors+VFX aus BBS
      --AlphaOver-->                              (Background)
  [Blender-modelle]    (CompositorNodeRLayers)  <- vordergrund: ausgewaehlte modelle, alpha
                                                   (Foreground, film_transparent)
      -> NodeGroupOutput
```
  Die STATISCHE Blender-welt (`MC_World`) taucht in KEINER render-schicht auf — sie ist
  nie in die modell-szene gelinkt (nur orientierung/beleuchtung beim editieren, hide_render).

## v2a TIEFEN-OCCLUSION (GEBAUT — der block verdeckt das modell wo er naeher ist)
  v1s AlphaOver legt das modell IMMER VOR den BBS-frame (kein tiefen-vergleich) — falsch
  wo ein MC-block VOR dem modell steht (modell hinter einer wand-kante). v2a loest das mit
  Blenders `CompositorNodeZcombine`: pro pixel gewinnt die KLEINERE tiefe (= das naehere
  element). Ist der welt-block naeher als das modell, bleibt der BBS-pixel (die welt) stehen
  = block verdeckt modell.
    - MODELL-Z: `use_pass_z` auf der modell-szenen-view-layer → der modell-RLayers-node
      bekommt einen `Depth`-output (gemessen: RLayers-outputs = [Image, Alpha, Depth]).
    - WELT-Z (holdout): eine EIGENE szene `BBS_Model_WorldZ` rendert die schon importierte
      `MC_World`-geometrie (state.WORLD_COLLECTION) mit DERSELBEN `BBS_FilmCam` + `use_pass_z`
      → ihr `Depth`-output ist die welt-tiefe. RGB wird VERWORFEN (nur der Z-pass zaehlt) —
      das SICHTBARE welt-bild bleibt der BBS-in-game-frame, die welt-GEOMETRIE dient nur als
      tiefen/holdout-referenz. Z-raeume matchen exakt (beide in Blender-einheiten == bloecke,
      keine linearisierung, kein Java, kein EXR).
    - Z-COMBINE: `Zcombine(A=modell-bild, DepthA=modell-Z, B=BBS-frame, DepthB=welt-holdout-Z)`
      (5.2-sockets GEMESSEN: A/Depth A/B/Depth B/Use Alpha/Anti-Alias). Wo modell-Z < welt-Z
      → modell; wo welt-Z < modell-Z (block naeher) → BBS-frame = OCCLUSION. Wo das modell
      KEINE geometrie hat, ist modell-Z = far-clip → B (welt/BBS-frame) gewinnt (hintergrund).
    - TOGGLE occlusion an/aus: ein `CompositorNodeSwitch` (5.2, sockets Switch/Off/On) waehlt
      zwischen dem v1-AlphaOver-ergebnis (Off) und dem Z-Combine-ergebnis (On), getrieben vom
      welt-szenen-prop `bbscomp_occlusion` per scripted-driver. So bleibt v1 unveraendert
      erreichbar (kein welt-import? → occlusion aus, AlphaOver, exakt v1-verhalten).
  EHRLICHE Z-COMBINE-GRENZEN (Regel 0 — nicht kaschiert):
    - Kein sauberes anti-aliasing an der schnittkante (ein Z-wert pro pixel) — die `Anti-Alias`-
      option glaettet den worst fall, perfekte kanten braeuchten DEEP compositing (Nuke), das
      Blenders compositor NICHT kann (aus-scope, plan §2).
    - Deckungs-annahme: die `MC_World`-geometrie muss die BBS-welt exakt spiegeln (tut sie fuer
      den offline-render — selber export). Live-editierte bloecke die BBS zeigt aber der letzte
      welt-export nicht hat wuerden falsch occluden (dann v2b, plan §5 — NICHT gebaut).
    - Kein welt-import in der szene (leere `MC_World`) → welt-Z = far ueberall → Z-Combine
      zeigt ueberall das modell = wie v1. build_composite loggt das + laesst occlusion dann aus.

## v2b-PFAD (NICHT gebaut, HIER NUR DOKUMENTIERT als folge-task)
  `Readback` um einen depth-readback erweitern (`GL_DEPTH_COMPONENT/GL_FLOAT`), linearisieren
  (near/far aus MC-projektion), als EXR/f32 transportieren. Deutlich mehr arbeit (Java +
  kalibrierung) — NUR wenn v2a's geometrie-Z nachweislich nicht reicht (live-composite mit
  block-deltas die der export nicht hat). Details im plan §5 (tasks 9-11).

## DIE BBS-FRAME-QUELLE (der hintergrund)
  Das in-game-bild kommt von BBS als PNG (java `com.mcstudio.see.Readback.captureMain
  Framebuffer(path)` liest MCs echten framebuffer, genutzt in AutonomyRoutes:267) —
  eine PNG pro film-tick, `bbs_frame_%04d.png` ODER ein einzelnes bild. Das braucht einen
  laufenden GL-render-thread (echtes game). `set_bbs_frame(path)` reicht den pfad rein
  (einzelbild ODER image-sequence). HEADLESS (test-instanz :9976, kein GL-context) gibt es
  keinen echten frame — dann ein test-hintergrund-bild reingeben und das COMPOSITING
  beweisen (modell ueber hintergrund); der echte frame-transport ist BBS-seite + der
  offline-render-loop (siehe render_composite), nicht dieses modul.

## AUSWAHL: WELCHE OBJEKTE IN DIE MODELL-SZENE? (v1)
  Vorrang: (1) explizite objekt-liste an build_composite(objects=...), sonst
  (2) die collection `BBS_Composite_Models` falls vorhanden + nicht leer, sonst
  (3) alle roots der `MC_Actors`-collection (state.ACTORS_COLLECTION). Die objekte werden
  in die modell-szenen-collection GELINKT (nicht verschoben — ein Blender-objekt lebt in
  mehreren szenen zugleich, invariante 5b: MC_Actors bleibt unberuehrt).

## STATE / IDEMPOTENZ / RUECKSTANDSLOS (invariante 5)
  Rein lokal, KEIN tick/socket/register (wie fp_hand/hud_overlay/bobj_check). Alle
  datenbloecke `BBS_Model_`/`BBS_MC_Frame`-prefixed. `build_composite()` ist idempotent
  (get-or-create per name, kein .001-duplikat). `remove_composite()` raeumt alles nach
  CLEANUP_MANIFEST rueckstandslos + restauriert die vorherige scene.compositing_node_group.
"""

import math
import os
import time

import bpy

from . import state
from .evaluation_scope import quiet
from .fp_hand import (
    _configure_hand_scene_render,
    _ensure_driver,
    _get_or_create_node,
)
from .film_camera_import import BBS_FILM_CAM_NAME


# ---- namen: ALLE datenbloecke die dieses modul anlegt sind BBS_Model_-/BBS_MC_Frame-
# prefixed (invariante 5). remove_composite() geht GENAU die CLEANUP-liste unten durch. ----
MODEL_SCENE_NAME = "BBS_Model_Layer"        # eigene szene: NUR die ausgewaehlten modelle
MODEL_WORLD_NAME = "BBS_Model_World"        # neutrale beleuchtungs-world (film_transparent
                                            # macht den hintergrund selbst unsichtbar)
WORLDZ_SCENE_NAME = "BBS_Model_WorldZ"      # v2a: welt-Z-holdout-szene (rendert MC_World-
                                            # geometrie NUR fuer den Z-pass, RGB verworfen)
COMPOSITE_GROUP_NAME = "BBS_Model_Composite"  # eigene compositor-node-gruppe (welt-szene)
FRAME_IMG_NAME = "BBS_MC_Frame"             # der BBS-in-game-frame als Image-datenblock

# compositor-node-namen (innerhalb von COMPOSITE_GROUP_NAME)
_N_OUT = "BBS_Model_GroupOutput"
_N_IMG_BBS = "BBS_Model_Image_BBSFrame"     # hintergrund: der BBS-frame
_N_RL_MODEL = "BBS_Model_RLayers_Model"     # vordergrund: der modell-render (transparent, +Z)
_N_AO_MODEL = "BBS_Model_AlphaOver_Model"   # v1: BBS-frame(BG) <- AlphaOver - modell(FG)
# v2a occlusion-nodes:
_N_RL_WORLDZ = "BBS_Model_RLayers_WorldZ"   # welt-Z-holdout-render (nur der Depth-output zaehlt)
_N_ZC_MODEL = "BBS_Model_ZCombine_Model"    # Zcombine(modell vs BBS-frame per tiefe)
_N_SWITCH = "BBS_Model_Switch_Occlusion"    # Off=AlphaOver(v1) / On=Zcombine(v2a) per prop

# custom-property-namen (auf der WELT-szene, wie fp_hand.PROP_SHOW_*): toggle des modell-
# layers (FLOAT 0..1 — erlaubt spaeteres weiches ein/ausblenden, wie fp_hands toggles) +
# ein occlusion-toggle (0=v1-AlphaOver, 1=v2a-Z-Combine) + der gemerkte pfad zum vorherigen
# compositing_node_group-namen fuer sauberes restore.
PROP_SHOW_MODELS = "bbscomp_show_models"
PROP_OCCLUSION = "bbscomp_occlusion"        # v2a: 0=modell immer vorn (v1), 1=tiefen-occlusion
_PROP_PREV_NG = "bbscomp_prev_node_group"   # interner merker (name der vorherigen gruppe)

# eine benannte collection erlaubt dem user eine EXPLIZITE modell-auswahl (vorrang 2).
SELECTION_COLLECTION = "BBS_Composite_Models"
#: rendert ZUSAETZLICH zu MC_Actors (Effekte, Requisiten); uebersteht den Neu-Import der Actors.
EXTRA_COLLECTION = "BBS_Composite_Extra"

MODEL_RENDER_RES = (1920, 1080)

# neutrale beleuchtung fuer die modell-szene (nur damit die modelle nicht schwarz sind —
# der hintergrund selbst ist durch film_transparent unsichtbar, wie fp_hands hand-world).
_MODELWORLD_COLOR = (0.6, 0.6, 0.65, 1.0)
_MODELWORLD_STRENGTH = 0.8

CLEANUP_MANIFEST = {
    # WORLDZ_SCENE_NAME zuerst raeumen ist egal (nur objekte GELINKT, keine eigenen daten) —
    # aber beide szenen VOR der node-gruppe (die RLayers-nodes halten sie).
    "scenes": (MODEL_SCENE_NAME, WORLDZ_SCENE_NAME),
    "node_groups": (COMPOSITE_GROUP_NAME,),
    "images": (FRAME_IMG_NAME,),
    "worlds": (MODEL_WORLD_NAME,),
    "scene_props": (PROP_SHOW_MODELS, PROP_OCCLUSION, _PROP_PREV_NG),
}


# ============================================================================
# modell-szene (analog fp_hand._get_or_create_hand_scene, aber OHNE eigene kamera/arm:
# die szene teilt sich BBS_FilmCam mit dem film → automatisch kamera-synchron)
# ============================================================================

def _get_or_create_model_world():
    w = bpy.data.worlds.get(MODEL_WORLD_NAME)
    if w is None:
        w = bpy.data.worlds.new(MODEL_WORLD_NAME)
    w.use_nodes = True
    bg = next((n for n in w.node_tree.nodes if n.type == 'BACKGROUND'), None)
    if bg is not None:
        bg.inputs["Color"].default_value = _MODELWORLD_COLOR
        bg.inputs["Strength"].default_value = _MODELWORLD_STRENGTH
    return w


def _native_world(models):
    """Resolve the authored world carried by one Blender-native actor.

    Multiple different authored worlds cannot describe one shared render layer.  In
    that case the neutral legacy world remains active and the conflict is loud.
    """
    names = sorted({str(obj.get("bbs_native_world")) for obj in models
                    if obj.get("bbs_native_world")})
    if len(names) == 1:
        world = bpy.data.worlds.get(names[0])
        if world is not None:
            return world
        print("[bbs-bridge] native model world %r fehlt — neutraler Fallback" % names[0])
    elif len(names) > 1:
        print("[bbs-bridge] mehrere native model worlds %s — neutraler Fallback" % names)
    return None


def _film_camera():
    """BBS_FilmCam holen (die dense-gebakte film-fahrt) — die modell-szene nutzt EXAKT diese
    kamera als scene.camera → automatisch synchron zum BBS-render (dieselbe fahrt, dieselbe
    frame↔tick-indizierung). None wenn kein film importiert (dann kann v1 nicht rendern,
    aber der node-graph laesst sich fuer den gate-beweis trotzdem mit einer TEST-kamera
    bauen — build_composite nimmt `cam_name` entgegen)."""
    return bpy.data.objects.get(BBS_FILM_CAM_NAME)


def _mit_abhaengigkeiten(objekte):
    """Objekte + was sie zum RICHTIGEN aussehen brauchen, reihenfolge-stabil.

    ⛔ EIN MESH OHNE SEINE ARMATURE RENDERT IN DER RUHEPOSE — und zwar ohne fehler,
    ohne warnung, einfach falsch posiert. Das ist genau die art stiller fehlschlag,
    die im fertigen frame erst auffaellt. Die armature haengt am Armature-MODIFIER
    bzw. am parent; beides wird hier aufgeloest. (Auftrag REDACTED live-link §4:
    "Rekursive Collections und benoetigte Abhaengigkeiten mitnehmen".)

    Bewusst NICHT aufgeloest: constraint-ziele und geometry-nodes-referenzen. Die
    brauchen eine eigene runde mit eigenem beweis — sie hier blind mitzuziehen
    wuerde fremde objekte in die render-szene holen, ohne dass jemand geprueft hat,
    was das am bild aendert."""
    raus, gesehen = [], set()

    def dazu(o):
        if o is not None and o.name not in gesehen:
            gesehen.add(o.name)
            raus.append(o)

    for o in objekte:
        dazu(o)
        for mod in getattr(o, "modifiers", []) or []:
            if mod.type == 'ARMATURE' and getattr(mod, "object", None) is not None:
                dazu(mod.object)
        if getattr(o, "parent", None) is not None and o.parent.type == 'ARMATURE':
            dazu(o.parent)
    return raus


def _select_model_objects(objects=None):
    """Welche objekte in die modell-szene? Vorrang: explizite liste > SELECTION_COLLECTION >
    MC_Actors-roots. Gibt eine (moeglicherweise leere) liste von objekten zurueck. Links
    (nicht verschiebt) — invariante 5b: MC_Actors bleibt unberuehrt.

    ⛔ `all_objects`, NICHT `objects`: `collection.objects` ist FLACH und sieht nur die
    direkte ebene. Eine verschachtelte collection (`MC_Actors/steve/...` — genau so legt
    ein shot-autor seine figuren ab) faellt damit still aus dem render, ohne fehler."""
    if objects:
        return _mit_abhaengigkeiten(list(objects))
    sel = bpy.data.collections.get(SELECTION_COLLECTION)
    if sel is not None and len(sel.all_objects) > 0:
        return _mit_abhaengigkeiten(list(sel.all_objects))
    actors = bpy.data.collections.get(state.ACTORS_COLLECTION)
    objekte = list(actors.all_objects) if actors is not None else []
    # Zusaetzliche Objekte (Effekte, Requisiten), die NEBEN den Actors rendern sollen. Eigene
    # Collection statt Kind von BBS_Composite_Models: der Render-Import baut MC_Actors neu, ein
    # Verweis darauf fiel heraus und der Render zeigte nur die Effekte (gemessen 2026-10-14).
    extra = bpy.data.collections.get(EXTRA_COLLECTION)
    if extra is not None:
        objekte += [o for o in extra.all_objects if o not in objekte]
    # Actors, die nicht ins Composite gehoeren (der eigene Koerper im Ego-Film: die Kamera sitzt
    # in seinem Kopf, BBS zeichnet ihn dort auch nicht). Szenen-Eigenschaft, damit sie den
    # Neu-Import der Actors uebersteht: Liste von bbs_uuid.
    ohne = {u for s in bpy.data.scenes for u in (s.get("bbs_composite_ohne") or ())}
    if ohne:
        def wurzel(o):
            while o.parent is not None:
                o = o.parent
            return o
        objekte = [o for o in objekte if wurzel(o).get("bbs_uuid", "") not in ohne]
    # nur echte objekte (nicht empties/groups die keine geometrie tragen? — v1 nimmt
    # ALLE, der render zeigt was rendert; empties rendern nichts, sind harmlos)
    return _mit_abhaengigkeiten(objekte) if objekte else []


def _link_objects_to_scene(scene, objects):
    """Objekte in die modell-szenen-collection linken, idempotent. LINKT (kein verschieben):
    dieselben objekte bleiben in MC_Actors sichtbar (ein objekt lebt in mehreren szenen).
    Gibt die anzahl frisch verlinkter objekte zurueck."""
    n = 0
    for o in objects:
        if o is None:
            continue
        if o.name not in scene.collection.objects:
            try:
                scene.collection.objects.link(o)
                n += 1
            except RuntimeError:
                pass  # schon verlinkt (race mit einem parallelen build) — harmlos
    return n


def _unlink_all_model_objects(scene):
    """Alle objekte aus der modell-szenen-collection loesen (NUR unlink, NIE loeschen —
    die objekte gehoeren dem user/MC_Actors). Fuer ein sauberes re-select bei re-build."""
    for o in list(scene.collection.objects):
        try:
            scene.collection.objects.unlink(o)
        except RuntimeError:
            pass


def _get_or_create_model_scene(objects=None, cam_name=BBS_FILM_CAM_NAME):
    """Die modell-szene: eigene szene mit film_transparent, world zur beleuchtung,
    kamera = BBS_FilmCam (geteilt mit dem film = kamera-synchron). NUR die ausgewaehlten
    modelle drin (gelinkt, nicht verschoben). Idempotent (re-select bei re-build)."""
    scene = bpy.data.scenes.get(MODEL_SCENE_NAME)
    if scene is None:
        scene = bpy.data.scenes.new(MODEL_SCENE_NAME)
    _configure_hand_scene_render(scene)   # film_transparent=True, EEVEE, view_transform Standard
    scene.render.resolution_x, scene.render.resolution_y = MODEL_RENDER_RES
    # v2a: der modell-RLayers-node braucht einen Depth-output fuer die Z-Combine-occlusion
    # (gemessen: use_pass_z=True → RLayers-outputs = [Image, Alpha, Depth]). Immer AN — der
    # pass kostet in EEVEE praktisch nichts und die occlusion-verdrahtung braucht ihn.
    scene.view_layers[0].use_pass_z = True

    # kamera: die film-kamera (oder eine explizit uebergebene TEST-kamera fuer gate)
    cam = bpy.data.objects.get(cam_name)
    if cam is not None and cam.type == 'CAMERA':
        # kamera muss in DIESER szene verlinkt sein damit scene.camera greift
        if cam.name not in scene.collection.objects:
            try:
                scene.collection.objects.link(cam)
            except RuntimeError:
                pass
        scene.camera = cam

    # modelle re-select (unlink kamera NICHT — sie wurde gerade verlinkt)
    cam_name_now = scene.camera.name if scene.camera else None
    for o in list(scene.collection.objects):
        if o.name != cam_name_now:
            try:
                scene.collection.objects.unlink(o)
            except RuntimeError:
                pass
    models = _select_model_objects(objects)
    scene.world = _native_world(models) or _get_or_create_model_world()
    n_linked = _link_objects_to_scene(scene, models)
    return scene, models, n_linked


# ============================================================================
# v2a: welt-Z-holdout-szene — rendert die schon importierte MC_World-geometrie mit DERSELBEN
# BBS_FilmCam, NUR fuer den Z-pass (RGB wird verworfen). Ihr Depth-output ist die welt-tiefe
# die der Z-Combine gegen die modell-tiefe vergleicht. Z-raeume matchen exakt (beide Blender-
# einheiten == bloecke) — kein linearisieren, kein Java, kein EXR (plan §2 v2a).
# ============================================================================

def _mc_world_objects():
    """Die render-baren objekte der importierten MC_World-collection (state.WORLD_COLLECTION),
    REKURSIV (die welt kann in unter-collections liegen). Leere/fehlende collection → []
    (dann kann v2a nicht occluden — build_composite loggt es + laesst occlusion aus)."""
    coll = bpy.data.collections.get(state.WORLD_COLLECTION)
    if coll is None:
        return []
    # all_objects zieht rekursiv durch geschachtelte collections (nicht nur .objects direkt)
    return [o for o in coll.all_objects if o.type in {'MESH', 'CURVE', 'SURFACE', 'META', 'FONT'}]


def _get_or_create_worldz_scene(cam_name=BBS_FILM_CAM_NAME):
    """Die welt-Z-holdout-szene: dieselbe BBS_FilmCam, die MC_World-geometrie GELINKT (nie
    verschoben — invariante 5b, die welt gehoert dem user), Z-pass an, RGB egal (nur Depth
    zaehlt). Idempotent (re-select bei re-build). Gibt (scene, n_world_objs) zurueck;
    n_world_objs==0 heisst: keine welt importiert → occlusion kann nicht occluden."""
    scene = bpy.data.scenes.get(WORLDZ_SCENE_NAME)
    if scene is None:
        scene = bpy.data.scenes.new(WORLDZ_SCENE_NAME)
    # gleiche render-basis wie die modell-szene, aber film_transparent egal (wir werfen RGB weg)
    scene.render.resolution_x, scene.render.resolution_y = MODEL_RENDER_RES
    scene.render.engine = 'BLENDER_EEVEE'
    scene.view_settings.view_transform = 'Standard'
    scene.view_layers[0].use_pass_z = True   # der ganze zweck dieser szene: der Depth-output

    # kamera = dieselbe film-kamera (oder eine explizite TEST-cam) — verlinken, dann setzen
    cam = bpy.data.objects.get(cam_name)
    if cam is not None and cam.type == 'CAMERA':
        if cam.name not in scene.collection.objects:
            try:
                scene.collection.objects.link(cam)
            except RuntimeError:
                pass
        scene.camera = cam

    # welt-geometrie re-select (nur die kamera behalten, den rest neu linken)
    cam_name_now = scene.camera.name if scene.camera else None
    for o in list(scene.collection.objects):
        if o.name != cam_name_now:
            try:
                scene.collection.objects.unlink(o)
            except RuntimeError:
                pass
    world_objs = _mc_world_objects()
    _link_objects_to_scene(scene, world_objs)
    return scene, len(world_objs)


# ============================================================================
# BBS-frame-quelle (der hintergrund) — als CompositorNodeImage-datenblock (wie hud_overlay)
# ============================================================================

def _get_or_create_frame_image(path=None):
    """Das BBS-in-game-bild als bpy-image-datenblock (BBS_MC_Frame). `path` = einzelbild
    ODER erstes bild einer sequenz (%04d). None laesst ein evtl. vorhandenes bild stehen
    (re-build ohne neuen frame). Fehlt der pfad + kein bild da → None (der Image-node
    zeigt dann nichts, der modell-render liegt ueber transparent → sichtbar, kein crash)."""
    img = bpy.data.images.get(FRAME_IMG_NAME)
    if path is None:
        return img
    if not os.path.isfile(path):
        print("[bbs-bridge] model_composite: BBS-frame '%s' existiert nicht — hintergrund leer" % path)
        return img
    if img is None:
        img = bpy.data.images.load(path)
        img.name = FRAME_IMG_NAME
    else:
        img.filepath = path
        try:
            img.reload()
        except RuntimeError:
            pass
    return img


def set_bbs_frame(path, sequence=False, scene=None):
    """Den BBS-frame-hintergrund (um)setzen. `path` = PNG-pfad (einzelbild) oder erstes
    frame einer `%04d`-sequenz. `sequence=True` schaltet den Image-node auf IMAGE-source
    mit auto-refresh (fuer den offline-render-loop: pro frame das passende bild). Gibt das
    image (oder None). build_composite muss vorher gelaufen sein (node existiert)."""
    world_scene = scene if scene is not None else bpy.context.scene
    img = _get_or_create_frame_image(path)
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    if ng is not None:
        node = ng.nodes.get(_N_IMG_BBS)
        if node is not None:
            node.image = img
            if img is not None and sequence:
                # image-sequence: der compositor zieht pro scene-frame das passende bild
                try:
                    img.source = 'SEQUENCE'
                    node.frame_duration = 100000  # gross genug; frame_offset koppelt an scene-frame
                    node.use_auto_refresh = True
                except (AttributeError, TypeError):
                    pass
    return img


# ============================================================================
# compositor: BBS-frame(BG) <- AlphaOver - modell-render(FG) — eigene node-gruppe
# (dasselbe generische muster wie fp_hand._build_compositor, EIN AlphaOver)
# ============================================================================

def _get_or_create_node_group():
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    if ng is None:
        ng = bpy.data.node_groups.new(COMPOSITE_GROUP_NAME, 'CompositorNodeTree')
    if not any(it.in_out == 'OUTPUT' and it.name == "Image" for it in ng.interface.items_tree):
        ng.interface.new_socket(name="Image", in_out='OUTPUT', socket_type='NodeSocketColor')
    return ng


def _build_compositor(ng, world_scene, model_scene, frame_img, worldz_scene=None):
    """Der node-graph:
      v1-zweig (occlusion aus): BBS-frame(BG) <- AlphaOver - modell-render(FG).
      v2a-zweig (occlusion an):  Zcombine(A=modell-bild+modell-Z, B=BBS-frame+welt-holdout-Z)
                                 — pro pixel gewinnt die kleinere tiefe (block verdeckt modell
                                 wo er naeher ist).
    Ein `CompositorNodeSwitch` (Off=v1, On=v2a) waehlt per welt-szenen-prop PROP_OCCLUSION,
    dahinter das NodeGroupOutput.

    Link-aufrufe sind idempotent OHNE eigene dedup-logik (Blenders links.new() ersetzt
    einen vorhandenen link auf denselben INPUT-socket — single-link-constraint), wie in
    fp_hand._build_compositor dokumentiert. Socket-namen/indizes sind LIVE gemessen (5.2):
    Zcombine IN = [A, Depth A, B, Depth B, Use Alpha, Anti-Alias], OUT = [Result, Depth];
    Switch IN = [Switch, Off, On], OUT = [Image]; RLayers-Depth-output erscheint erst mit
    view_layer.use_pass_z=True (in _get_or_create_model_scene/_get_or_create_worldz_scene an)."""
    out = _get_or_create_node(ng, _N_OUT, 'NodeGroupOutput')

    img_bbs = _get_or_create_node(ng, _N_IMG_BBS, 'CompositorNodeImage')
    if frame_img is not None:
        img_bbs.image = frame_img

    rl_model = _get_or_create_node(ng, _N_RL_MODEL, 'CompositorNodeRLayers')
    rl_model.scene = model_scene
    rl_model.layer = model_scene.view_layers[0].name

    ao_model = _get_or_create_node(ng, _N_AO_MODEL, 'CompositorNodeAlphaOver')
    ao_model.inputs["Type"].default_value = 'Over'
    ao_model.inputs["Straight Alpha"].default_value = False

    # --- v2a occlusion-nodes ---
    rl_worldz = _get_or_create_node(ng, _N_RL_WORLDZ, 'CompositorNodeRLayers')
    if worldz_scene is not None:
        rl_worldz.scene = worldz_scene
        rl_worldz.layer = worldz_scene.view_layers[0].name

    zc = _get_or_create_node(ng, _N_ZC_MODEL, 'CompositorNodeZcombine')
    # Use Alpha=True: die modell-alpha wird beim tiefen-merge respektiert (transparente
    # modell-raender mischen sauber statt hart zu clippen). Anti-Alias=True glaettet den
    # worst-fall der Z-kanten-treppe (die ehrliche grenze bleibt, plan §2).
    zc.inputs["Use Alpha"].default_value = True
    zc.inputs["Anti-Alias"].default_value = True

    sw = _get_or_create_node(ng, _N_SWITCH, 'CompositorNodeSwitch')

    # kosmetische positionen (nur fuer den node-editor-blick, keine funktion)
    img_bbs.location = (-800, 200)
    rl_model.location = (-800, -100)
    rl_worldz.location = (-800, -450)
    ao_model.location = (-450, 100)
    zc.location = (-450, -250)
    sw.location = (-150, 0)
    out.location = (100, 0)

    # v1-zweig: BBS-frame = BACKGROUND, modell-render = FOREGROUND (modell VOR welt)
    ng.links.new(img_bbs.outputs["Image"], ao_model.inputs["Background"])
    ng.links.new(rl_model.outputs["Image"], ao_model.inputs["Foreground"])

    # v2a-zweig: A = modell (bild+tiefe), B = BBS-frame (bild) + welt-holdout-tiefe.
    # Zcombine nimmt pro pixel das kleinere Depth. Wo das modell keine geometrie hat, ist
    # modell-Depth = far-clip → B (welt/BBS-frame) gewinnt = hintergrund sichtbar.
    ng.links.new(rl_model.outputs["Image"], zc.inputs["A"])
    ng.links.new(rl_model.outputs["Depth"], zc.inputs["Depth A"])
    ng.links.new(img_bbs.outputs["Image"], zc.inputs["B"])
    ng.links.new(rl_worldz.outputs["Depth"], zc.inputs["Depth B"])

    # Switch: Off = v1-AlphaOver, On = v2a-Zcombine
    ng.links.new(ao_model.outputs["Image"], sw.inputs["Off"])
    ng.links.new(zc.outputs["Result"], sw.inputs["On"])
    ng.links.new(sw.outputs["Image"], out.inputs["Image"])

    # TOGGLE-driver 1: der modell-layer-Factor <- welt-szenen-prop (identisch fp_hand-muster;
    # inputs[2] = Factor, array_index 0 weil skalarer VALUE-socket). Wirkt auf den v1-AlphaOver.
    _ensure_driver(ng, 'nodes["%s"].inputs[2].default_value' % _N_AO_MODEL, 0, "var",
                   [("var", "SCENE", world_scene, '["%s"]' % PROP_SHOW_MODELS)])
    # TOGGLE-driver 2: der Switch (occlusion an/aus) <- welt-szenen-prop PROP_OCCLUSION.
    # inputs[0] = Switch (BOOLEAN-socket, driver evaluiert 0/1). 0=v1-AlphaOver, 1=v2a-Zcombine.
    _ensure_driver(ng, 'nodes["%s"].inputs[0].default_value' % _N_SWITCH, 0, "var",
                   [("var", "SCENE", world_scene, '["%s"]' % PROP_OCCLUSION)])


# ============================================================================
# public API
# ============================================================================

def build_composite(scene=None, objects=None, bbs_frame=None, sequence=False,
                    cam_name=BBS_FILM_CAM_NAME, occlusion=None):
    """Den HYBRID-COMPOSITE (v1 AlphaOver + v2a Z-Combine-occlusion) bauen/aktualisieren:
    eine modell-szene (nur die ausgewaehlten modelle, transparent, +Z-pass, kamera=BBS_FilmCam),
    eine welt-Z-holdout-szene (MC_World-geometrie, nur Depth), und eine compositor-node-gruppe
    die BEIDE zweige haelt: v1 (modell IMMER vorn) und v2a (block verdeckt modell per tiefe),
    umschaltbar per PROP_OCCLUSION. IDEMPOTENT.

    `scene`     : die WELT-szene an die der compositor gehaengt wird (default context.scene).
    `objects`   : explizite modell-auswahl (default: BBS_Composite_Models > MC_Actors).
    `bbs_frame` : pfad zum BBS-in-game-frame-PNG (einzelbild oder %04d-sequenz-start). None
                  laesst ein evtl. vorhandenes bild stehen. HEADLESS: test-hintergrund-bild.
    `sequence`  : True = image-sequence-modus (offline-render zieht pro frame das passende).
    `cam_name`  : kamera fuer beide szenen (default BBS_FilmCam; gate nutzt eine TEST-cam).
    `occlusion` : None = den vorhandenen wert lassen (default 0=aus beim ersten build).
                  True/False = tiefen-occlusion (v2a Z-Combine) ein/aus setzen. Ist keine
                  welt importiert (MC_World leer), zwingt build_composite occlusion auf AUS
                  (occlusion braeuchte die welt-Z, plan §2) und loggt es.

    Gibt ein report-dict (status, scene-namen, anzahl modelle+welt-objekte, occlusion-zustand).
    """
    world_scene = scene if scene is not None else bpy.context.scene

    if PROP_SHOW_MODELS not in world_scene.keys():
        world_scene[PROP_SHOW_MODELS] = 1.0
    if PROP_OCCLUSION not in world_scene.keys():
        world_scene[PROP_OCCLUSION] = 0.0   # default: v1-verhalten (modell vorn), occlusion opt-in
    # vorherige compositing-gruppe merken (fuer sauberes restore in remove_composite),
    # aber NUR wenn es nicht schon unsere ist (kein selbst-ueberschreiben bei re-build).
    prev = getattr(world_scene, "compositing_node_group", None)
    if prev is not None and prev.name != COMPOSITE_GROUP_NAME and _PROP_PREV_NG not in world_scene.keys():
        world_scene[_PROP_PREV_NG] = prev.name

    model_scene, models, n_linked = _get_or_create_model_scene(objects, cam_name)
    worldz_scene, n_world = _get_or_create_worldz_scene(cam_name)
    frame_img = _get_or_create_frame_image(bbs_frame)

    # occlusion-wunsch aufloesen: kann nur AN sein wenn welt-geometrie da ist (sonst waere
    # die welt-Z ueberall far → Z-Combine zeigt ueberall das modell = kein occlusion-nutzen).
    if occlusion is not None:
        want = 1.0 if occlusion else 0.0
        if want > 0.0 and n_world == 0:
            print("[bbs-bridge] model_composite: occlusion angefragt, aber MC_World ist leer "
                  "(0 objekte) — occlusion bleibt AUS (v1-AlphaOver). Erst welt importieren.")
            want = 0.0
        world_scene[PROP_OCCLUSION] = want

    ng = _get_or_create_node_group()
    _build_compositor(ng, world_scene, model_scene, frame_img, worldz_scene)

    # sequence-modus nachziehen (set_bbs_frame regelt die node-source)
    if bbs_frame is not None and sequence:
        set_bbs_frame(bbs_frame, sequence=True, scene=world_scene)

    world_scene.use_nodes = True
    world_scene.compositing_node_group = ng

    report = {
        "status": "ok",
        "world_scene": world_scene.name,
        "model_scene": model_scene.name,
        "worldz_scene": worldz_scene.name,
        "node_group": ng.name,
        "n_models": len(models),
        "n_linked": n_linked,
        "n_world_objs": n_world,
        "occlusion": bool(world_scene[PROP_OCCLUSION] > 0.0),
        "model_names": [o.name for o in models][:20],
        "camera": model_scene.camera.name if model_scene.camera else None,
        "bbs_frame": frame_img.name if frame_img is not None else None,
        "bbs_frame_path": (frame_img.filepath if frame_img is not None else None),
    }
    print("[bbs-bridge] model_composite.build_composite: modell-szene '%s' (%d modelle, cam=%s) "
          "+ welt-Z '%s' (%d objekte) + compositor '%s' -> welt-szene '%s', occlusion=%s, bbs-frame=%s"
          % (model_scene.name, len(models), report["camera"], worldz_scene.name, n_world,
             ng.name, world_scene.name, report["occlusion"], report["bbs_frame_path"] or "(keins)"))
    return report


def set_occlusion(on, scene=None):
    """Die tiefen-occlusion (v2a Z-Combine) an/aus schalten OHNE re-build — setzt nur den
    welt-szenen-prop PROP_OCCLUSION, den Switch-driver liest ihn live. Gibt den effektiven
    zustand zurueck (kann bei leerer welt auf AUS gezwungen werden)."""
    world_scene = scene if scene is not None else bpy.context.scene
    want = 1.0 if on else 0.0
    if want > 0.0 and len(_mc_world_objects()) == 0:
        print("[bbs-bridge] model_composite.set_occlusion: MC_World leer — occlusion bleibt AUS.")
        want = 0.0
    world_scene[PROP_OCCLUSION] = want
    return bool(want > 0.0)


@quiet()
def render_composite(out_dir=None, start=None, end=None, samples=None, mux=True, fps=None,
                     scene=None, cam_name=BBS_FILM_CAM_NAME, bbs_frame_dir=None,
                     bbs_frame_pattern="bbs_frame_%04d.png", engine='BLENDER_EEVEE'):
    """Den composited film als PNG-sequenz rendern (+ optional ffmpeg-mux zu mp4).
    REUSE render_drive.film_frame_range/mux_sequence/_snapshot/_restore — dieselbe
    frame↔tick-indizierung und dieselben render-settings-snapshot-hygiene wie render_film.

    Der unterschied zu render_drive.render_film: pro frame wird VOR dem render der passende
    BBS-hintergrund-frame gesetzt (`bbs_frame_dir/bbs_frame_pattern % f`), UND die WELT-szene
    rendert MIT der composite-node-gruppe (die den modell-render ueber den BBS-frame legt).
    Das ergebnis ist das FINALE bild (BBS-welt + Blender-modell kamera-synchron).

    `bbs_frame_dir`: verzeichnis der BBS-in-game-frame-PNGs (aus dem BBS-render/Readback-loop).
                     None → der schon gesetzte einzel-frame bleibt fuer ALLE frames stehen
                     (statischer hintergrund, z.b. gate/close-up ohne kamerafahrt-hintergrund).

    OFFLINE BY DESIGN wie render_film: kein live-BBS-zugriff, `scene.frame_set(f)` treibt die
    gebakten fcurves. Blockiert bis fertig (N frames x s/frame). Nicht-destruktiv (snapshot/
    restore der render-settings). Gibt ein result-dict."""
    from . import render_drive

    world_scene = scene if scene is not None else bpy.context.scene
    t_wall = time.time()

    # composite muss stehen (node-gruppe an der welt-szene). Kein auto-build hier — der
    # aufrufer entscheidet die auswahl/kamera; wir bauen nur, was schon eingerichtet ist.
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    if ng is None or getattr(world_scene, "compositing_node_group", None) is not ng:
        return {"ok": False, "error": "composite nicht gebaut/aktiv — erst build_composite() rufen"}

    cam = bpy.data.objects.get(cam_name)
    if cam is None or cam.type != 'CAMERA' or cam.data is None:
        return {"ok": False, "error": "keine kamera '%s' — erst film-kamera importieren" % cam_name}

    baked = render_drive.film_frame_range(cam)
    if baked is None and (start is None or end is None):
        return {"ok": False, "error": "'%s' hat keine gebakten fcurves — erst film-kamera importieren" % cam_name}
    if start is None:
        start = baked[0]
    if end is None:
        end = baked[1]
    if end < start:
        return {"ok": False, "error": "leere frame-range (%d..%d)" % (start, end)}

    if out_dir is None:
        import tempfile
        out_dir = os.path.join(tempfile.gettempdir(),
                               "bbs_composite_render_" + time.strftime("%Y%m%d_%H%M%S"))
    os.makedirs(out_dir, exist_ok=True)
    if fps is None:
        fps = world_scene.render.fps / (world_scene.render.fps_base or 1.0)

    n_total = end - start + 1
    snap = render_drive._snapshot_render_settings(world_scene)
    frame_secs = []
    missing = []
    frames_without_bbs = []
    try:
        # WELT-szene rendert MIT der composite-gruppe (die modell-szene wird als RLayers
        # mitgezogen). Render-preset + PNG (render_drive-hilfen, MC-look view_transform).
        # ⛔ ENGINE AUF ALLE DREI SZENEN: jede Blender-szene rendert mit IHRER engine —
        # nur die welt-szene umzustellen liesse die modell-ebene still auf Eevee (P7
        # engine-wahl waere wirkungslos). Modell/WeltZ sind addon-eigene szenen, kein
        # user-zustand — kein snapshot noetig.
        render_drive._apply_eevee_preset(world_scene, samples, engine=engine)
        for addon_szene in (bpy.data.scenes.get(MODEL_SCENE_NAME), bpy.data.scenes.get(WORLDZ_SCENE_NAME)):
            if addon_szene is not None:
                addon_szene.render.engine = engine
                if engine == 'CYCLES' and samples is not None and hasattr(addon_szene, "cycles"):
                    addon_szene.cycles.samples = samples
                    addon_szene.cycles.device = world_scene.cycles.device if hasattr(world_scene, "cycles") else 'CPU'
        if engine != 'CYCLES' and samples is None:
            # Ohne Vorgabe rendert Eevee 64 Samples je Szene und Bild (gemessen 2026-10-14:
            # 1,6 s Modell-Ebene + 1,2 s Tiefe pro Bild). Modell 8 (saubere Kanten, kaum teurer
            # als 1), Tiefe 1 (mehr Samples mitteln die Tiefe an Kanten nur unscharf), Haupt-Szene 1
            # (ihr eigenes Bild verwirft der Compositor). Gemessen danach: 0,66 s pro Bild.
            for name, n in ((MODEL_SCENE_NAME, 8), (WORLDZ_SCENE_NAME, 1)):
                addon_szene = bpy.data.scenes.get(name)
                if addon_szene is not None:
                    addon_szene.eevee.taa_render_samples = n
            world_scene.eevee.taa_render_samples = 1
        world_scene.camera = cam
        for f in range(start, end + 1):
            t0 = time.time()
            # pro frame den passenden BBS-hintergrund setzen (falls ein dir gegeben)
            if bbs_frame_dir is not None:
                fp = os.path.join(bbs_frame_dir, bbs_frame_pattern % f)
                if os.path.isfile(fp):
                    set_bbs_frame(fp, sequence=False, scene=world_scene)
                else:
                    frames_without_bbs.append(f)
            world_scene.frame_set(f)
            world_scene.render.filepath = os.path.join(out_dir, render_drive.FRAME_PATTERN % f)
            bpy.ops.render.render(write_still=True)   # NIE Render-Result-pixels lesen
            dt = time.time() - t0
            frame_secs.append(dt)
            print("[bbs-composite] frame %d/%d (%.2fs)" % (f - start + 1, n_total, dt))
    finally:
        render_drive._restore_render_settings(world_scene, snap)

    for f in range(start, end + 1):
        if not os.path.isfile(os.path.join(out_dir, (render_drive.FRAME_PATTERN % f) + ".png")):
            missing.append(f)

    mp4 = None
    mux_err = None
    if missing:
        status = "render unvollstaendig: %d/%d frames fehlen" % (len(missing), n_total)
    elif mux:
        mp4, mux_err = render_drive.mux_sequence(out_dir, start, n_total, fps)

    wall = time.time() - t_wall
    # ⛔ FEHLENDE HINTERGRUNDFRAMES SIND EIN FEHLER (auftrag REDACTED §2). Bisher wurden sie
    # in frames_without_bbs gesammelt, der render lief weiter, und `ok` sah sie nicht an:
    # ein composite mit schwarzem loch hinter der figur kam als erfolg zurueck.
    fehler = None
    if missing:
        fehler = "render unvollstaendig: %d/%d frames fehlen (z.b. %s)" % (
            len(missing), n_total, missing[:5])
    elif frames_without_bbs:
        fehler = "%d von %d frames OHNE BBS-hintergrund (z.b. %s) — hinter der figur waere es leer" % (
            len(frames_without_bbs), n_total, frames_without_bbs[:5])
    ok = (not missing and not frames_without_bbs
          and (mp4 is not None or not mux or mux_err is None))
    result = {
        "ok": ok, "error": fehler,
        "out_dir": out_dir, "start": start, "end": end, "frames": n_total,
        "missing": missing, "mp4": mp4, "mux_error": mux_err, "fps": fps,
        "wall_s": round(wall, 3),
        "s_per_frame": round(sum(frame_secs) / max(len(frame_secs), 1), 3),
        "frames_without_bbs_bg": frames_without_bbs,
    }
    print("[bbs-composite] render fertig: %s" % result)
    return result


def _clear_extra_user(block):
    """Wie fp_hand._clear_extra_user: ein offener Node-Editor der die gruppe unpinned
    anzeigt setzt use_extra_user → block.users bleibt sonst faelschlich >0 und
    remove_composite haelt den block fuer belegt. Gilt genauso fuer use_fake_user."""
    if getattr(block, "use_fake_user", False):
        block.use_fake_user = False
    if getattr(block, "use_extra_user", False):
        block.use_extra_user = False


def remove_composite(scene=None):
    """Entfernt ALLES was build_composite() anlegte, nach CLEANUP_MANIFEST — rueckstandslos
    (invariante 5): die modelle selbst (MC_Actors/user) bleiben unberuehrt, NUR unsere
    BBS_Model_*-/BBS_MC_Frame-datenbloecke + die toggle-property gehen weg. Restauriert die
    vorherige scene.compositing_node_group. Gibt ein report-dict {kategorie: [namen]}."""
    world_scene = scene if scene is not None else bpy.context.scene
    removed = {k: [] for k in CLEANUP_MANIFEST}

    # 0. compositor-verdrahtung ZUERST loesen (auf JEDER szene die drauf zeigt) — erst dann
    # hat die node-gruppe users==0. Vorherige gruppe restaurieren (gemerkter name).
    ng = bpy.data.node_groups.get(COMPOSITE_GROUP_NAME)
    prev_name = world_scene.get(_PROP_PREV_NG)
    prev_ng = bpy.data.node_groups.get(prev_name) if prev_name else None
    if ng is not None:
        for sc in bpy.data.scenes:
            if getattr(sc, "compositing_node_group", None) is ng:
                sc.compositing_node_group = prev_ng   # None wenn keine vorherige (sauber)

    # 1. die modell-szene: erst alle (gelinkten) objekte loesen (NIE loeschen — user-besitz),
    # dann die szene entfernen. Die kamera (BBS_FilmCam) ist auch nur gelinkt → bleibt.
    for name in CLEANUP_MANIFEST["scenes"]:
        sc = bpy.data.scenes.get(name)
        if sc is None:
            continue
        _unlink_all_model_objects(sc)
        _clear_extra_user(sc)
        try:
            bpy.data.scenes.remove(sc)
            removed["scenes"].append(name)
        except (ReferenceError, RuntimeError) as e:
            print("[bbs-bridge] model_composite: entfernen von szene '%s' fehlgeschlagen: %s" % (name, e))

    # 2. rest-datenbloecke — node_groups VOR images (der RLayers-node haelt die szene, der
    # Image-node haelt das frame-bild; erst nach dem gruppen-remove ist img.users==0. Gleiche
    # reihenfolge-falle wie fp_hand/world_import: node_group vor image).
    _rest = (
        ("node_groups", bpy.data.node_groups),
        ("images", bpy.data.images),
        ("worlds", bpy.data.worlds),
    )
    for category, coll in _rest:
        for name in CLEANUP_MANIFEST[category]:
            block = coll.get(name)
            if block is None:
                continue
            _clear_extra_user(block)
            if block.users > 0:
                print("[bbs-bridge] model_composite: %s '%s' hat noch %d user, NICHT entfernt"
                      % (category, name, block.users))
                continue
            try:
                coll.remove(block)
                removed[category].append(name)
            except (ReferenceError, RuntimeError) as e:
                print("[bbs-bridge] model_composite: entfernen von %s '%s' fehlgeschlagen: %s"
                      % (category, name, e))

    # 3. custom-properties auf der welt-szene
    for prop in CLEANUP_MANIFEST["scene_props"]:
        if prop in world_scene.keys():
            try:
                del world_scene[prop]
                removed["scene_props"].append(prop)
            except KeyError:
                pass

    print("[bbs-bridge] model_composite.remove_composite: %s" % removed)
    return removed
