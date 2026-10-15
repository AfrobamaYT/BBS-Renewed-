"""5.1b-REST (W9-B5) — ECHTER rechter alex-arm fuer das fp_hand-rig (ersetzt den 5.1-quader).

ROUTE (user-entscheid 2026-07-17, hand-assets-brief v2: "der MC-arm ist einfach nur ein
gescaledter cube — warum nicht direkt porten?"): PORT statt tutorial-nachbau. QUELLE ist
dieselbe datei, aus der auch actors.py/bobj_import den MC_Actors-alex baut:
<BBS_ASSETS_DIR>/models/emoticons/alex/slim.bobj (via bobj_import._find_bobj_for_form).
BEWUSST die DATEI statt einer kopie des live-objekts: bobj_import backt die import-daten
identity-transformiert (raum-fix 949f9de, ein raum) — datei-route und live-mesh sind
GEOMETRISCH IDENTISCH (W9-B5-gate gegen den MC_Actors-alex in :9977: 388/388 verts,
selektion deckungsgleich, worst-delta 1.19e-07 = EIN float32-ulp; der rest-ulp kommt vom
identity-bake des importers, dessen euler-matrix-"nullen" 6e-17 sind — gemessen, kein
byte-sha also, sondern toleranz 1e-6), die datei-route haengt dafuer an keinem
szenen-zustand, funktioniert headless (:9976)
und fasst den user-sichtbaren actor nie an (invariante 5/5b; alex traegt seit dbfecb2 auch
pose-bone-fcurves — eine datei-quelle ist davon per konstruktion unabhaengig).

=== ARM-SELEKTION (GEMESSEN am slim.bobj 2026-07-17, nicht geraten) ===
  - gruppen die den rechten arm tragen: right_arm (292 verts) + low_right_arm (284) +
    low_right_arm.end (180 — der HAND-anteil, den der brief als "ggf." offen liess).
  - KONTAMINATION: 6 BEIN-verts tragen streu-gewichte auf low_right_arm (right_leg=1.0 +
    arm=0.00003..0.005) — ein "hat arm-gewicht"-kriterium zoege bein-geometrie mit.
    Darum DOMINANZ-kriterium: arm-gewichtssumme / gesamt-gewichtssumme > 0.5.
  - Ergebnis damit: 388 verts, 768 faces, EXAKT 0 faces die arm- und fremd-verts mischen
    (der arm ist geometrisch sauber trennbar — bmesh-delete verliert kein arm-face).

=== FP-POSE: DIE VANILLA-KETTE (bytecode-gemessen, 1.21.1 yarn) ===
Referenz ist NICHT augenmass, sondern die transform-kette die das echte spiel fuer den
leeren first-person-arm faehrt — javap-disassembly HeldItemRenderer.renderArmHoldingItem
+ PlayerEntityRenderer.renderArm (KEIN eigener scale/flip dort) + ModelPart-pivot:

  T(0.64, -0.6, -0.72)          # swing=0, equip-param=0 (voll gehoben; im spiel faehrt
  @ Ry(+45 deg)                  #   die hand ueber equip*-0.6 von unten hoch = 5.1b-ease)
  @ T(-1.0, 3.6, 3.5)            # kamera-raum, einheiten BLOECKE (verifiziert: projektion
  @ Rz(+120) @ Rx(+200) @ Ry(-135)  # des ergebnisses trifft den game-screenshot, s.u.)
  @ T(5.6, 0, 0)
  @ T(-5/16, 2.5/16, 0)          # ModelPart.rotate: slim-rightArm-pivot (-5, 2.5, 0)/16
  @ Rz(0.1 rad)                  # KONSTANTER idle-roll: renderArm nullt NUR arm.pitch,
                                 #   setAngles' roll bleibt — und weil renderArm
                                 #   animationProgress=0 uebergibt ist er DETERMINISTISCH
                                 #   cos(0)*0.05+0.05 = 0.1 rad (kein zeit-wackeln im FP-
                                 #   arm; ohne ihn sitzt der arm ~15px zu weit links/tief,
                                 #   pixel-gemessen am overlay)
  @ Rx(+90)                      # abbildung UNSERER daten in den vanilla-modell-raum:
                                 #   modell ist y-DOWN (arm zeigt +y), unsere daten sind
                                 #   Z-up mit arm nach -z -> genau Rx(+90). MC-kamera-raum
                                 #   und Blender-kamera-raum sind identisch (x-rechts,
                                 #   y-hoch, -z-vorn) -> ergebnis 1:1 als objekt-transform.

END-TO-END-VERIFIKATION gegen das laufende spiel (2026-07-17): render der hand-szene bei
GAME_PARITY_FOV ueber den echten game-screenshot (854x480, creative, first person, leere
hand) gelegt — arm-silhouetten-breite IDENTISCH (128px == 128px), kanten-versatz nach dem
idle-roll-fix im einstelligen pixel-bereich (messgenauigkeit der fenster-capture).

SCALE = 1.0, NICHT der actor-scale 0.9375: PlayerEntityRenderer.renderArm rendert das
ModelPart OHNE den 0.9375-world-scale aus LivingEntityRenderer (bytecode-gemessen) —
der vanilla-FP-arm ist bewusst 6.7% groesser als der welt-spieler. Wir matchen den FP-arm.

MC-FOV IST VERTIKAL: getFov -> getBasicProjectionMatrix nutzt den wert als vertikale
oeffnung; der fp_hand-lens-driver rechnet PROP_FOV dagegen HORIZONTAL (sensor_fit
HORIZONTAL, sw=36). Game-parity bei 16:9 also NICHT fov=70 sondern
2*atan(tan(35 deg)*16/9) = 102.45 grad — als GAME_PARITY_FOV_DEG exportiert (fuer
side-by-side-gates; das 5.1-default 65 bleibt unangetastet, user-tunable).

=== GRENZEN (dokumentiert, nicht versteckt) ===
  - custom split normals der quelle gehen im bmesh-roundtrip verloren — fuer die
    achsen-parallelen MC-quader sind geometrie-normalen == datei-normalen (flat shading
    wird explizit gesetzt), sichtbar identisch.
  - der setAngles-idle-roll ist im FP-pfad KONSTANT 0.1 rad (animationProgress=0, siehe
    kette) und steckt MIT in der pose — zeitliche bewegung traegt allein der bob-empty
    von 7fa0e02 (fp_channels), exakt wie im spiel (bobView wirkt dort auf die matrix,
    nicht auf die arm-winkel).
  - haelt der user in BBS ein item, rendert das spiel eine ANDERE pose (item-pfad) —
    v1 ist der leere-hand-arm (brief: "angewinkelt rechts-unten").
"""

import math
import os

import bpy
import bmesh
from mathutils import Matrix, Vector

from .bobj_import import (
    _bobj_vec,
    _bobj_material,
    _find_bobj_for_form,
    _import_bobj_geometry,
    _parse_bobj_extras,
    _read_bobj_config,
    _resolve_bbs_link,
)

# modell-schluessel wie ihn actors.py via formModel aufloest (armature in :9977 heisst so)
ALEX_FORM_MODEL = "emoticons/alex"

# die drei gruppen die den rechten arm tragen (messung im modul-docstring)
ARM_GROUPS = frozenset(("right_arm", "low_right_arm", "low_right_arm.end"))
# dominanz-schwelle gegen die 6 kontaminierten bein-verts (messung im modul-docstring)
ARM_DOMINANCE = 0.5

# right_arm-bone-HEAD (BOBJ Y-up, aus den arm_bone-zeilen gemessen) = schulter-gelenk.
# Die mesh-daten werden hierauf RE-ZENTRIERT: objekt-ursprung == schulter == der punkt
# um den die vanilla-kette dreht (und um den ein spaeterer equip-raise drehen wird).
SHOULDER_BOBJ = (-0.375, 1.375, 0.0)

# fuer remove_rig (fp_hand): so heissen material/image die build_arm_mesh anlegt/teilt —
# "BBS_MDL_" + png-name kommt aus bobj_import._bobj_material, der name ist dort pro
# textur EINDEUTIG und wird vom MC_Actors-alex GETEILT (kein duplikat, brief-vorgabe).
SHARED_MATERIAL_NAME = "BBS_MDL_alex.png"
SHARED_IMAGE_NAME = "alex.png"

# EIGENES arm-material fuer den skin-TAUSCH (set_arm_skin). Der arm rendert per default mit
# dem GETEILTEN BBS_MDL_alex.png — teilen war fuer's RENDERN korrekt (arm==alex-skin, ein
# datenblock statt duplikat). Aber ein skin-tausch DARF den geteilten datenblock NICHT
# mutieren, sonst aendert er den MC_Actors-alex MIT (gemessen W11-B1: nach build_rig +
# 2.-alex traegt BBS_MDL_alex.png users==2 — EIN datenblock). Deshalb entkoppelt set_arm_skin
# den arm-slot beim ERSTEN tausch auf eine EIGENE material-KOPIE (dieser name), auf der die
# textur getauscht wird. Der geteilte alex bleibt unberuehrt. Idempotent: 2. tausch nutzt die
# schon vorhandene kopie. Von fp_hand.CLEANUP_MANIFEST["materials"] als exklusiver block
# mitgeraeumt (kein shared_-block: gehoert komplett dem fp-arm).
ARM_SKIN_MATERIAL_NAME = "BBS_FP_ArmSkin"

# game-parity-FOV fuer side-by-side-gates (herleitung im modul-docstring: MC 70 vertikal
# -> horizontal bei 16:9). NICHT als neues default verdrahtet — 5.1-default bleibt 65.
GAME_PARITY_FOV_DEG = 2.0 * math.degrees(math.atan(math.tan(math.radians(35.0)) * 16.0 / 9.0))


def _vanilla_fp_arm_matrix():
    """Die kette aus dem modul-docstring, 1:1 in mathutils komponiert (positive
    Matrix.Rotation == JOML RotationAxis.POSITIVE_*.rotationDegrees, beide rechtshaendig).
    Ergebnis: objekt-matrix des arm-objekts RELATIV zur hand-kamera (parent-kette
    kamera -> bob-empty -> arm; bob ist identity solange keine kanaele gebacken sind)."""
    d = math.radians
    return (
        Matrix.Translation((0.64000005, -0.6, -0.71999997))
        @ Matrix.Rotation(d(45.0), 4, 'Y')
        @ Matrix.Translation((-1.0, 3.6, 3.5))
        @ Matrix.Rotation(d(120.0), 4, 'Z')
        @ Matrix.Rotation(d(200.0), 4, 'X')
        @ Matrix.Rotation(d(-135.0), 4, 'Y')
        @ Matrix.Translation((5.6, 0.0, 0.0))
        @ Matrix.Translation((-5.0 / 16.0, 2.5 / 16.0, 0.0))
        @ Matrix.Rotation(0.1, 4, 'Z')     # konstanter idle-roll (docstring; RADIANS)
        @ Matrix.Rotation(d(90.0), 4, 'X')
    )


FP_ARM_MATRIX = _vanilla_fp_arm_matrix()
FP_ARM_LOCATION = tuple(FP_ARM_MATRIX.to_translation())          # ~(0.514, -0.804, -0.689)
FP_ARM_ROTATION = tuple(FP_ARM_MATRIX.to_euler('XYZ'))           # ~(5.0, 34.9, -116.7) grad


def _arm_keep_indices(weights):
    """Vertex-indizes deren arm-gewichtsanteil die dominanz-schwelle reisst.
    `weights` ist die bobj_import._parse_bobj_extras-struktur (liste pro vertex,
    [(bone, w), ...] — nur w>0-eintraege stehen drin)."""
    keep = set()
    for idx, entries in enumerate(weights):
        total = 0.0
        arm = 0.0
        for bone, w in entries:
            if w <= 0.0:
                continue
            total += w
            if bone in ARM_GROUPS:
                arm += w
        if total > 0.0 and arm / total > ARM_DOMINANCE:
            keep.add(idx)
    return keep


def _purge_new_datablocks(coll, before, keep=()):
    """Datenbloecke entfernen die seit dem `before`-snapshot dazukamen (importer-reste),
    ausser `keep`. Nur users==0 — nie etwas wegreissen das schon benutzt wird."""
    for name in [n for n in (b.name for b in coll) if n not in before]:
        block = coll.get(name)
        if block is None or block in keep:
            continue
        if block.users == 0:
            try:
                coll.remove(block)
            except (ReferenceError, RuntimeError):
                pass


def build_arm_mesh(me):
    """Den rechten alex-arm aus slim.bobj in das (persistente) mesh-datenblock `me`
    bauen. Gibt das GETEILTE material zurueck (BBS_MDL_alex.png, Closest — dasselbe
    datenblock das der MC_Actors-alex nutzt, kein duplikat), oder None wenn die quelle
    fehlt/kaputt ist (aufrufer faellt auf den 5.1-quader zurueck, mit log).

    Ablauf == brief-route "mesh-kopie + bmesh-reduktion", nur mit der datei als quelle:
    voll-import ueber DENSELBEN weg wie der live-alex (_import_bobj_geometry inkl.
    identity-bake), dann bmesh-delete aller verts unterhalb der arm-dominanz, dann
    re-zentrierung auf das schulter-gelenk (SHOULDER_BOBJ). Idempotent: jeder lauf
    ersetzt die geometrie in `me` komplett (self-healing wie der 5.1-quader-pfad)."""
    path = _find_bobj_for_form(ALEX_FORM_MODEL)
    if path is None:
        print("[bbs-bridge] fp_arm: kein bobj fuer %r gefunden (BBS_ASSETS_DIR pruefen)"
              % ALEX_FORM_MODEL)
        return None

    _bones, weights, _bad = _parse_bobj_extras(path)
    if not weights:
        print("[bbs-bridge] fp_arm: %s ohne vw-gewichte — kein arm extrahierbar" % path)
        return None
    keep = _arm_keep_indices(weights)
    if not keep:
        print("[bbs-bridge] fp_arm: keine verts ueber der arm-dominanz (%s)" % path)
        return None

    mats_before = {m.name for m in bpy.data.materials}
    imgs_before = {i.name for i in bpy.data.images}
    meshes_before = {m.name for m in bpy.data.meshes}

    objs = _import_bobj_geometry(path)
    try:
        meshes = [o for o in objs if o.type == 'MESH']
        if not meshes:
            print("[bbs-bridge] fp_arm: import lieferte kein mesh (%s)" % path)
            return None
        src = meshes[0]
        if len(src.data.vertices) != len(weights):
            # gleicher zaehl-guard wie bobj_import._apply_bobj_weights: die vw-indizes
            # gelten nur wenn der importer die v-zeilen 1:1 uebernahm.
            print("[bbs-bridge] fp_arm: %d mesh-verts != %d vw-verts — abbruch"
                  % (len(src.data.vertices), len(weights)))
            return None

        shoulder = _bobj_vec(*SHOULDER_BOBJ)
        bm = bmesh.new()
        try:
            bm.from_mesh(src.data)
            bm.verts.ensure_lookup_table()   # 5.2-pflicht (paket-CLAUDE.md bmesh-falle)
            doomed = [v for v in bm.verts if v.index not in keep]
            bmesh.ops.delete(bm, geom=doomed, context='VERTS')
            for v in bm.verts:
                v.co -= shoulder             # ursprung -> schulter-gelenk (docstring)
            bm.to_mesh(me)                   # ersetzt die geometrie in `me` komplett
        finally:
            bm.free()
        me.materials.clear()                 # slot setzt der aufrufer (fp_hand)
        for poly in me.polygons:
            poly.use_smooth = False          # MC-look: flat (custom normals s. docstring)
        me.update()
    finally:
        # temp-import IMMER restlos weg (die objekte waren nur quelle) — objekte zuerst,
        # dann neu entstandene meshes/materialien/bilder des importers (users==0-guard).
        for o in objs:
            try:
                bpy.data.objects.remove(o, do_unlink=True)
            except (ReferenceError, RuntimeError):
                pass
        _purge_new_datablocks(bpy.data.meshes, meshes_before, keep=(me,))
        _purge_new_datablocks(bpy.data.materials, mats_before)
        _purge_new_datablocks(bpy.data.images, imgs_before)

    # material: EXAKT der bobj_import-pfad -> teilt den datenblock mit dem live-alex
    # (name "BBS_MDL_alex.png", check_existing-image) statt zu duplizieren.
    cfg = _read_bobj_config(path)
    mat = None
    link = cfg.get("texture")
    p = _resolve_bbs_link(link) if link else None
    if p is not None:
        try:
            img = bpy.data.images.load(p, check_existing=True)
            mat = _bobj_material(img, cfg.get("culling", False))
        except RuntimeError as e:
            print("[bbs-bridge] fp_arm: skin-textur laedt nicht (%s): %s" % (p, e))
    if mat is None:
        print("[bbs-bridge] fp_arm: kein material aus config.json — arm bleibt materiallos"
              " (aufrufer setzt fallback)")
        # geometrie steht trotzdem — der aufrufer entscheidet ob ihm das reicht; wir
        # melden None damit der 5.1-fallback greift (arm OHNE skin waere halb-fertig).
        return None

    print("[bbs-bridge] fp_arm: arm aus %s -> %d verts / %d faces, material '%s' (geteilt)"
          % (path, len(me.vertices), len(me.polygons), mat.name))
    return mat


# ============================================================================
# SKIN-TAUSCH (W11-B1) — png tauschen, gleiches UV (W9-B5 08bafb3-doku)
# ============================================================================
# DER ENTSCHEID (gemessen, nicht geraten): der arm rendert per default mit dem GETEILTEN
# BBS_MDL_alex.png-material — dasselbe datenblock das JEDER MC_Actors-alex nutzt (users==2
# nach build_rig + zweitem alex, live gemessen). Teilen war fuers RENDERN richtig (identischer
# skin, kein duplikat). Fuer den TAUSCH ist es die falle: eine textur-mutation am geteilten
# material aendert alex MIT. Loesung = TRENNUNG genau hier: set_arm_skin gibt dem arm-slot beim
# ersten tausch eine EIGENE material-kopie (ARM_SKIN_MATERIAL_NAME) und tauscht nur dort. UV
# bleibt identisch (nur das image-node-datum wechselt, mesh/UV unangetastet — 08bafb3-vorgabe).


def _find_tex_image_node(mat):
    """Den ersten TEX_IMAGE-node eines node-material finden (der arm-skin-tex-node).
    None wenn das material keine nodes/keinen tex-node hat."""
    if mat is None or not mat.use_nodes or mat.node_tree is None:
        return None
    for node in mat.node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            return node
    return None


def _arm_object():
    """Das fp-arm-objekt (fp_hand.ARM_OBJ_NAME). LAZY-import von fp_hand: fp_arm wird SEINERSEITS
    lazy aus fp_hand importiert (build_arm_mesh) — ein top-level `from . import fp_hand` waere
    zirkulaer. None wenn kein rig gebaut ist (aufrufer meldet das)."""
    try:
        from . import fp_hand
        name = fp_hand.ARM_OBJ_NAME
    except Exception:   # noqa: BLE001 — deploy-schieflage/alt-stand darf nicht crashen
        name = "BBS_FP_Arm"
    obj = bpy.data.objects.get(name)
    if obj is None or obj.type != 'MESH':
        return None
    return obj


def _dedicated_arm_material(current):
    """Das EIGENE arm-skin-material liefern (ARM_SKIN_MATERIAL_NAME), damit der tausch den
    geteilten alex NICHT anfasst. Idempotent:
      - ist der arm-slot schon das eigene material -> das zurueckgeben (2. tausch).
      - existiert das eigene material schon (frueherer tausch, rig neu gebaut) -> wiederverwenden.
      - sonst: eine KOPIE des aktuellen (geteilten) materials anlegen -> UV/nodes/Closest/alpha
        genau wie der arm sie hatte, nur ab jetzt eigenstaendig.
    `current` ist das material das gerade im arm-slot 0 sitzt (kann das geteilte oder der
    5.1-quader-BBS_FP_SkinMat sein — beide werden korrekt kopiert)."""
    if current is not None and current.name == ARM_SKIN_MATERIAL_NAME:
        return current
    existing = bpy.data.materials.get(ARM_SKIN_MATERIAL_NAME)
    if existing is not None:
        return existing
    if current is not None:
        mat = current.copy()          # KOPIE: eigene nodes, teilt aber noch das image-datum
        mat.name = ARM_SKIN_MATERIAL_NAME
    else:
        mat = bpy.data.materials.new(ARM_SKIN_MATERIAL_NAME)
        mat.use_nodes = True
    return mat


def set_arm_skin(png_path):
    """Den skin der first-person-hand tauschen: `png_path` ins arm-material laden, gleiches UV
    (08bafb3: "png tauschen, gleiches UV"). Closest-interpolation bleibt (MC-look, 64x64-skins
    verwaschen sonst). Gibt True bei erfolg, False bei fehler (kein rig / pfad fehlt / png laedt
    nicht) — der aufrufer sieht das, es wird nichts halb getauscht.

    TRENNUNG VOM GETEILTEN ALEX (der eigentliche entscheid, s. modul-kommentar oben): sitzt im
    arm-slot noch das geteilte BBS_MDL_alex.png, wird der slot ZUERST auf eine eigene kopie
    (ARM_SKIN_MATERIAL_NAME) umgehaengt; erst DANN wird die textur getauscht. So bleibt der
    MC_Actors-alex garantiert unberuehrt. Idempotent: wiederholtes set_arm_skin nutzt die
    schon vorhandene eigene kopie und ersetzt nur das image erneut.
    """
    if not png_path or not os.path.isfile(png_path):
        print("[bbs-bridge] fp_arm.set_arm_skin: png-pfad fehlt/kein file (%r) — kein tausch"
              % (png_path,))
        return False

    obj = _arm_object()
    if obj is None:
        print("[bbs-bridge] fp_arm.set_arm_skin: kein fp-arm im file (build_rig() zuerst) — "
              "kein tausch")
        return False
    if not obj.data.materials:
        print("[bbs-bridge] fp_arm.set_arm_skin: arm hat keinen material-slot — kein tausch")
        return False

    # 1. image laden (Closest setzt der node, nicht das image). check_existing dedupt gleiche
    # datei-pfade — ein zweiter tausch auf denselben skin legt kein .001-bild an.
    try:
        img = bpy.data.images.load(png_path, check_existing=True)
    except RuntimeError as e:
        print("[bbs-bridge] fp_arm.set_arm_skin: png laedt nicht (%s): %s" % (png_path, e))
        return False

    # 2. arm-slot auf das EIGENE material entkoppeln (schuetzt den geteilten alex).
    current = obj.data.materials[0]
    mat = _dedicated_arm_material(current)
    if obj.data.materials[0] is not mat:
        obj.data.materials[0] = mat

    # 3. das image im tex-node des EIGENEN materials tauschen (UV bleibt: nur .image wechselt).
    tex = _find_tex_image_node(mat)
    if tex is None:
        # kopiertes material hatte keinen tex-node (5.1-quader-fallback-material) -> einen
        # anlegen und an die Base Color haengen, damit der skin ueberhaupt sichtbar wird.
        nt = mat.node_tree
        tex = nt.nodes.new('ShaderNodeTexImage')
        tex.location = (-340, 260)
        bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is not None and "Base Color" in bsdf.inputs:
            nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    tex.image = img
    tex.interpolation = 'Closest'      # MC-look bleibt (paket-CLAUDE.md tex-falle)

    print("[bbs-bridge] fp_arm.set_arm_skin: arm-skin -> %s (material '%s', geteilter alex "
          "unberuehrt)" % (png_path, mat.name))
    return True


# ----------------------------------------------------------------------------
# USER-SKIN AUS DEM GAME HOLEN (W11-B1) — DOKUMENTIERTER FLOW, live-teil braucht :9880
# ----------------------------------------------------------------------------
# ZIEL (aufgabe): den skin des eingeloggten spielers automatisch auf den fp-arm legen.
# Der WEG dahin liegt IM game, nicht im dateisystem: der vanilla-skin-cache liegt unter
# `assets/skins/<xx>/<sha>` mit HASH-namen (NICHT username-gekeyt) — ein username→pfad-
# resolver allein auf der platte ist unzuverlaessig und waere geraten (Regel 0). Ehrlich ist:
# die laufende JVM kennt die geladene skin-textur des session-players und kann sie dumpen.
#
# LIVE-TEIL (braucht den EXEC-kanal :9880, tools/EXEC_USAGE.md) — BEWUSST NICHT hier gefeuert:
# :9880 ist der GETEILTE game-kanal (flotte + user greifen ihn). Ihn aus einem addon-modul
# unaufgefordert zu grabben kollidiert (invariante / max-autonomie: nur bei kanal FREI). Der
# koordinator (main) fuehrt den fetch aus wenn frei; das ergebnis (ein png-pfad) geht dann
# durch set_arm_skin() oben. Der ablauf (read-only, safe im tick — kein render/GL):
#
#   1. username:  net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername()
#   2. skin-textur des lokalen spielers -> die Identifier/das NativeImage der geladenen
#      PlayerSkin (SkinTextures.texture()); ueber MinecraftClient.getSkinProvider() bzw.
#      den PlayerListEntry des eigenen spielers aufgeloest.
#   3. das NativeImage per `.writeTo(java.io.File)` in eine temp-datei schreiben (game-seitig).
#   4. den pfad zurueck an Blender -> fp_arm.set_arm_skin(pfad).
#
# So bleibt DIESES modul rein Blender-seitig + testbar (:9976), und der game-zugriff bleibt
# beim kanal-eigner. Sobald der live-fetch verdrahtet ist, ist set_arm_skin die fertige senke.
