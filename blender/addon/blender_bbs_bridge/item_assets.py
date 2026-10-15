"""Task 5.2b (HB-1) — Item-asset-pipeline: MC-items als Blender-objekte + HUD-icons.

ZWECK: aus den item-ids der string-kanaele (task 5.2) das RICHTIGE item in die
first-person-hand rendern (schwert = klinge, block = wuerfel, fackel = sprite) und das
gleiche item als HUD-icon liefern. Alle assets kommen aus der bereits vorhandenen
client-jar (KEIN download) — dieses modul ist der EINZIGE jar-cache-loader; die
gui-seite (task 5.3a, gui_assets.py) importiert `jar_read`/`jar_names`/
`extract_png_to_cache`/`CACHE_DIR`/`find_client_jar` von hier (koordiniert mit HB-2).

=== QUELLE (verifiziert 2026-07-18, KEIN download) ===
client-jar (9.4M): ~/.gradle/caches/fabric-loom/**/minecraft-clientonly-1.21.1-*.jar
  assets/minecraft/textures/item/*.png    (605 sprites/icons)
  assets/minecraft/textures/block/*.png   (1012 block-flaechen)
  assets/minecraft/textures/gui/**/*.png   (476 gui — HB-2/task 5.3a)
  assets/minecraft/models/item/*.json     (1754 item-modelle)
  assets/minecraft/models/block/*.json    (2137 block-modelle)

=== DISPLAY-TRANSFORM (BYTECODE-GEMESSEN, javap 1.21.1 yarn — NICHT geraten, Regel 0/6) ===
Die held-item-transformation ist NICHT augenmass. Gemessen an der echten renderer-kette:

  net.minecraft.client.render.model.json.Transformation.apply(leftHanded, stack)   [javap -c]
    -> stack.translate(sign*tx, ty, tz)         # sign = leftHanded ? -1 : +1 (righthand: +1)
    -> stack.multiply(Quaternionf.rotationXYZ(rx*DEG2RAD, ry*DEG2RAD, rz*DEG2RAD))
         # bei leftHanded werden ry,rz negiert — righthand NICHT
    -> stack.scale(sx, sy, sz)
  == matrix  T(translation) @ R_xyz(rotation) @ S(scale)   (post-multiply matrix-stack)

  Transformation$Deserializer.deserialize:                                          [javap -c]
    translation = json * 0.0625 (= /16), dann clamp jede achse auf [-5, +5]
       # (wiki sagt "±80" — das ist der PRE-scale wert; 80/16 = 5 bloecke = MC-clamp)
    scale       = json,        dann clamp jede achse auf [-4, +4]   (cap 4.0)
    rotation    = json grad, unveraendert

  net.minecraft.client.render.item.ItemRenderer.renderItem(...):                    [javap -c]
    ... transformation.apply(leftHanded, stack); stack.translate(-0.5, -0.5, -0.5); ...
    # der -0.5-pivot: die gebackenen quads liegen in [0..1]^3 (modell-coords/16),
    #   apply/scale/rotate wirken um den block-MITTELPUNKT [0.5,0.5,0.5].

=> vollstaendige item-LOKAL-matrix (relativ zum held-item-anker, siehe ANKER unten):
   M_item = T(trans_clamped) @ R_xyz(rot_deg) @ S(scale_clamped) @ T(-0.5,-0.5,-0.5)
   auf ein mesh dessen geometrie in [0..1]-block-raum liegt (modell-coords/16).

=== ANKER (v1-scope, dokumentierte grenze — NICHT die animierte swing/equip-kette) ===
Der volle vanilla renderFirstPersonItem faehrt eine ANIMIERTE equip/swing/bob-kette
(HeldItemRenderer, bytecode gesichtet, aber zeit-abhaengig). V1-ziel laut plan ist die
STATISCHE leere-hand-parity — deshalb ankert das item am bob-empty (DERSELBE anker den
der fp-arm nutzt, fp_hand.BOB_OBJ_NAME) und die held-item-kette ist die STATISCHE
teilkette: der equip-basis-offset + der display-transform oben. Der genaue kamera-raum-
offset (ITEM_ANCHOR_*) ist gegen den silhouette-gate kalibriert (pixel-methode wie
fp_arm W9-B5), nicht geraten — abweichungen sind im gate dokumentiert. Bob/swing-
animation = 5.3b/kanal-arbeit, transport unveraendert.

=== ITEM-TYPEN ===
  sprite  (parent item/generated | item/handheld): flacher layer0-textur-quad, 1px
          (1/16-block) extrudiert (MC-standard-look) — silhouette traegt die alpha-textur.
  block   (parent kette endet in block/* mit elements): echtes wuerfel-mesh aus den
          `elements` (from/to in 1/16, pro-face textur+UV), display block/block gui_light.
  HUD-icon: sprite-items -> layer0-PNG direkt (get_icon). block-items -> isometrisches
          mini-render aus dem block-mesh (gui-display-transform rot [30,225,0]).

Enchant-glint = NICHT-ZIEL v1 (dokumentiert, kein hardcoded UV-scroll).

Lazy aus fp_hand/hud_overlay importierbar; KEIN tick/socket/register-bezug (reine
asset-fabrik + cache). Wirft nie in den aufrufer — quelle fehlt -> None + log.
"""

import glob
import math
import os
import struct
import threading
import zipfile

import bpy
import bmesh
from mathutils import Matrix, Vector


# ============================================================================
# JAR-CACHE-LOADER (dieses modul ist die EINZIGE quelle — gui_assets.py importiert)
# ============================================================================

# exakter pfad zuerst (koordinator-verifiziert 2026-07-18), dann glob-fallback bei drift.
_JAR_EXACT = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly/"
    "1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2/"
    "minecraft-clientonly-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2.jar"
)
_JAR_GLOB = os.path.expanduser(
    "~/.gradle/caches/fabric-loom/**/minecraft-clientonly-1.21.1-*.jar"
)

# cache: LOKAL, nicht ins addon-verzeichnis (read-only nach install) und nicht ins projekt.
CACHE_DIR = os.path.join(
    os.environ.get("LOCALAPPDATA") or os.path.join(os.path.expanduser("~"), ".cache"),
    "bbs-blender-bridge", "jar-assets",
)

_ASSET_ROOT = "assets/minecraft"

# modul-globaler zipfile-handle (lazy geoeffnet, ueber ganze addon-laufzeit offen —
# das oeffnen kostet, das lesen aus einem offenen handle ist billig). Ein lock, weil
# theoretisch mehrere handler den handle teilen (addon ist single-thread, aber billig
# abgesichert). _JAR_PATH cacht auch das negative ergebnis nicht (jar koennte nachziehen).
_zip = None
_zip_path = None
_zip_lock = threading.Lock()


def find_client_jar():
    """Pfad zur client-jar (str) oder None. Exakter pfad zuerst, dann glob-fallback."""
    if os.path.isfile(_JAR_EXACT):
        return _JAR_EXACT
    hits = glob.glob(_JAR_GLOB, recursive=True)
    return hits[0] if hits else None


def _get_zip():
    """Den gecachten zipfile-handle liefern (lazy). None wenn keine jar existiert.
    Idempotent + selbst-heilend: wechselt der pfad, wird neu geoeffnet."""
    global _zip, _zip_path
    with _zip_lock:
        path = find_client_jar()
        if path is None:
            return None
        if _zip is not None and _zip_path == path:
            return _zip
        # anderer pfad/erster aufruf -> alten handle schliessen, neu oeffnen
        if _zip is not None:
            try:
                _zip.close()
            except Exception:   # noqa: BLE001
                pass
            _zip = None
        try:
            _zip = zipfile.ZipFile(path)
            _zip_path = path
        except (OSError, zipfile.BadZipFile) as e:
            print("[bbs-bridge] item_assets: client-jar oeffnet nicht (%s): %s" % (path, e))
            _zip = None
            _zip_path = None
        return _zip


def jar_read(inner_path):
    """EINE jar-interne datei roh lesen -> bytes, oder None (jar/eintrag fehlt).
    `inner_path` ist der volle jar-pfad, z.B.
    "assets/minecraft/textures/gui/sprites/hud/hotbar.png"."""
    z = _get_zip()
    if z is None:
        return None
    try:
        return z.read(inner_path)
    except KeyError:
        return None
    except (OSError, zipfile.BadZipFile) as e:
        print("[bbs-bridge] item_assets.jar_read: lesefehler (%s): %s" % (inner_path, e))
        return None


def jar_names(prefix="", suffix=""):
    """Gefilterte namelist der jar. z.B. jar_names(prefix="assets/minecraft/textures/gui/",
    suffix=".png") -> alle gui-PNGs. Leere liste wenn keine jar."""
    z = _get_zip()
    if z is None:
        return []
    return [n for n in z.namelist()
            if n.startswith(prefix) and n.endswith(suffix)]


def extract_png_to_cache(inner_path):
    """Eine jar-interne datei EINMALIG in den lokalen cache schreiben -> lokaler dateipfad
    (str), oder None. Idempotent per existenz+groesse (die jar aendert sich nicht zur
    laufzeit). Die cache-struktur spiegelt die jar-pfade -> item- und gui-seite liegen
    kollisionsfrei nebeneinander. Das ist der pfad fuer bpy.data.images.load."""
    if not inner_path:
        return None
    # inner_path ist ein POSIX-jar-pfad; sicher auf lokale segmente mappen
    safe = inner_path.replace("\\", "/").lstrip("/")
    out = os.path.join(CACHE_DIR, *safe.split("/"))
    z = _get_zip()
    if z is None:
        return None
    try:
        info = z.getinfo(inner_path)
    except KeyError:
        return None
    # idempotenz: existiert die datei schon mit passender groesse -> wiederverwenden
    if os.path.isfile(out) and os.path.getsize(out) == info.file_size:
        return out
    data = jar_read(inner_path)
    if data is None:
        return None
    os.makedirs(os.path.dirname(out), exist_ok=True)
    tmp = out + ".part"
    try:
        with open(tmp, "wb") as f:
            f.write(data)
        os.replace(tmp, out)   # atomar: kein halb-geschriebenes file bei crash
    except OSError as e:
        print("[bbs-bridge] item_assets: cache-schreibfehler (%s): %s" % (out, e))
        try:
            os.remove(tmp)
        except OSError:
            pass
        return None
    return out


def _png_size(data):
    """Breite/hoehe (px) aus PNG-bytes ohne bild-decode (IHDR ist immer der erste chunk).
    Fuer die sprite-extrusion (aspect ratio) + isometrie-render. (None,None) bei nicht-PNG."""
    if not data or len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None, None
    # IHDR: laenge(4) 'IHDR'(4) width(4) height(4) ...  ab byte 8
    w, h = struct.unpack(">II", data[16:24])
    return w, h


# ============================================================================
# MODELL-AUFLOESUNG (parent-kette, textures/display/elements mergen)
# ============================================================================
import json   # noqa: E402 — nach dem loader, bewusst hier gruppiert

# die vanilla sprite-basis-modelle (parent-kette endet hier -> flat-item-extrusion)
_SPRITE_PARENTS = frozenset((
    "minecraft:item/generated", "item/generated",
    "minecraft:item/handheld", "item/handheld",
    "minecraft:builtin/generated", "builtin/generated",
))
_MODEL_CACHE = {}   # item_id -> aufgeloestes model-dict (session-lokal)


def _strip_ns(ref):
    """"minecraft:item/diamond_sword" / "item/diamond_sword" -> "item/diamond_sword"."""
    if ":" in ref:
        return ref.split(":", 1)[1]
    return ref


def _read_model_json(model_ref):
    """model-json aus der jar lesen. model_ref z.B. "item/diamond_sword" oder
    "block/cube_all" (mit oder ohne minecraft:-namespace). None wenn nicht da."""
    rel = _strip_ns(model_ref)
    data = jar_read("%s/models/%s.json" % (_ASSET_ROOT, rel))
    if data is None:
        return None
    try:
        return json.loads(data.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        print("[bbs-bridge] item_assets: model-json kaputt (%s): %s" % (model_ref, e))
        return None


def _resolve_model(item_id):
    """Die volle parent-kette eines items aufloesen. Gibt ein dict:
      {"kind": "sprite"|"block", "textures": {...}, "display": {...},
       "elements": [...] | None, "sprite_parent": <ref>|None}
    oder None wenn das item-modell fehlt. textures/display werden von den kind-modellen
    NACH den parents gemerged (kind gewinnt); elements erbt vom NAECHSTEN vorfahr der
    welche hat (MC-semantik). Die kette bricht bei einem sprite-basis-parent (kind=sprite,
    kein weiteres erben von elements)."""
    item_id = _strip_ns(item_id)
    if item_id in _MODEL_CACHE:
        return _MODEL_CACHE[item_id]

    # item-modelle liegen unter models/item/<name>.json; block-item-modelle zeigen per
    # parent auf block/<name> (z.b. stone -> block/stone). start ist immer item/<id>.
    ref = "item/%s" % item_id
    textures = {}
    display = {}
    elements = None
    kind = None
    sprite_parent = None
    seen = set()

    while ref is not None and ref not in seen:
        seen.add(ref)
        if ref in _SPRITE_PARENTS:
            kind = "sprite"
            sprite_parent = ref
            # sprite-basis definiert display (generated/handheld) — die schon-gemergten
            # kind-display-werte GEWINNEN (nicht ueberschreiben)
            base = _read_model_json(ref)
            if base:
                for k, v in (base.get("display") or {}).items():
                    display.setdefault(k, v)
                for k, v in (base.get("textures") or {}).items():
                    textures.setdefault(k, v)
            break

        node = _read_model_json(ref)
        if node is None:
            break
        # textures/display: kind gewinnt -> setdefault (kind wurde zuerst besucht)
        for k, v in (node.get("textures") or {}).items():
            textures.setdefault(k, v)
        for k, v in (node.get("display") or {}).items():
            display.setdefault(k, v)
        # elements: der ERSTE vorfahr mit elements liefert sie (kind kann eigene haben)
        if elements is None and node.get("elements"):
            elements = node["elements"]
        parent = node.get("parent")
        if parent is None:
            break
        ref = _strip_ns(parent) if ":" in parent else parent
        # block-basis-namen ohne "block/"-prefix (parent "block/block") sind schon rel

    if kind is None:
        # kein sprite-parent getroffen -> wenn elements da sind, ist es ein block-mesh;
        # sonst konnten wir es nicht aufloesen
        kind = "block" if elements else None

    if kind is None:
        _MODEL_CACHE[item_id] = None
        return None

    resolved = {
        "kind": kind,
        "textures": textures,
        "display": display,
        "elements": elements,
        "sprite_parent": sprite_parent,
    }
    _MODEL_CACHE[item_id] = resolved
    return resolved


def _resolve_texture(textures, key):
    """Einen textur-verweis aufloesen: folgt #-referenzen (z.b. "#layer0" -> das was
    unter "layer0" steht). Gibt den finalen "minecraft:item/foo"-ref oder None."""
    seen = set()
    ref = textures.get(key)
    while ref is not None and ref.startswith("#"):
        name = ref[1:]
        if name in seen:
            return None
        seen.add(name)
        ref = textures.get(name)
    return ref


def _texture_png_path(tex_ref):
    """"minecraft:item/diamond_sword" -> lokaler cache-PNG-pfad (via extract). None wenn
    die textur nicht in der jar liegt."""
    if not tex_ref:
        return None
    rel = _strip_ns(tex_ref)
    return extract_png_to_cache("%s/textures/%s.png" % (_ASSET_ROOT, rel))


# ============================================================================
# DISPLAY-TRANSFORM (bytecode-gemessen, siehe modul-docstring)
# ============================================================================
_DEG2RAD = math.pi / 180.0


def _clamp(v, lo, hi):
    return lo if v < lo else hi if v > hi else v


def display_matrix(display, context="firstperson_righthand"):
    """Die item-lokal-matrix fuer einen display-context (default firstperson_righthand)
    aus dem gemergten display-dict. Reproduziert BYTECODE-EXAKT (modul-docstring):
      M = T(trans/16 clamp[-5,5]) @ R_xyz(rot_deg) @ S(scale clamp[-4,4]) @ T(-0.5,-0.5,-0.5)
    fehlt der context -> identity @ T(-0.5) (nur der pivot; MC nutzt IDENTITY dann)."""
    entry = (display or {}).get(context) or {}
    tr = entry.get("translation", [0.0, 0.0, 0.0])
    rot = entry.get("rotation", [0.0, 0.0, 0.0])
    sc = entry.get("scale", [1.0, 1.0, 1.0])

    tx = _clamp(float(tr[0]) * 0.0625, -5.0, 5.0)
    ty = _clamp(float(tr[1]) * 0.0625, -5.0, 5.0)
    tz = _clamp(float(tr[2]) * 0.0625, -5.0, 5.0)
    sx = _clamp(float(sc[0]), -4.0, 4.0)
    sy = _clamp(float(sc[1]), -4.0, 4.0)
    sz = _clamp(float(sc[2]), -4.0, 4.0)

    m_t = Matrix.Translation((tx, ty, tz))
    # rotationXYZ(rx,ry,rz) == Rx @ Ry @ Rz (JOML komponiert in dieser reihenfolge)
    m_r = (Matrix.Rotation(float(rot[0]) * _DEG2RAD, 4, 'X')
           @ Matrix.Rotation(float(rot[1]) * _DEG2RAD, 4, 'Y')
           @ Matrix.Rotation(float(rot[2]) * _DEG2RAD, 4, 'Z'))
    m_s = Matrix.Diagonal((sx, sy, sz, 1.0))
    m_pivot = Matrix.Translation((-0.5, -0.5, -0.5))
    return m_t @ m_r @ m_s @ m_pivot


# ============================================================================
# GEOMETRIE: SPRITE-ITEM (flat layer0-extrusion) + BLOCK-ITEM (cube aus elements)
# ============================================================================
# MC-block-modell-raum ist [0..16]^3, gebacken /16 -> [0..1]^3. Wir bauen die geometrie
# DIREKT in [0..1] (die display_matrix erwartet das, siehe pivot -0.5).

# sprite-extrusion: MC extrudiert die flache textur 1px (1/16) in die tiefe. fuer die
# SILHOUETTE (gate) traegt die vorder/rueckflaeche die alpha-textur; die 1px-dicke gibt
# den standard-item-look von der seite. Wir bauen ein duennes box-quad in [0..1]-flaeche
# in der XY-ebene (front bei z=0.5-halbe-dicke), tiefe entlang z.
_SPRITE_THICKNESS = 1.0 / 16.0   # 1px in block-einheiten


def _build_sprite_mesh(me, aspect=1.0):
    """Flaches item-sprite in `me` bauen: quad in der [0..1]-XY-ebene (block-modell-raum),
    1px in z extrudiert, UV 1:1. `aspect` = png-breite/hoehe (fast immer 1.0 bei items).
    Die textur (alpha) traegt die silhouette; die geometrie ist der volle 0..1-quad.

    SEITEN-UV (nicht (0,0)!): die 4 rand-faces der 1px-dicke bekommen die UV der
    ENTSPRECHENDEN textur-KANTE (untere/obere/linke/rechte pixel-reihe) — so erben sie
    deren alpha und clippen dort weg wo die kante transparent ist (item-raender sind es
    fast immer). Ein festes (0,0) machte die seiten als voller pixel(0,0) sichtbar = der
    dunkle rahmen, den der erste gate-render zeigte."""
    bm = bmesh.new()
    try:
        z0 = 0.5 - _SPRITE_THICKNESS * 0.5
        z1 = 0.5 + _SPRITE_THICKNESS * 0.5
        # 8 verts: front-quad (z1) + back-quad (z0), volle 0..1-flaeche in x/y
        vf = [bm.verts.new((0.0, 0.0, z1)), bm.verts.new((1.0, 0.0, z1)),
              bm.verts.new((1.0, 1.0, z1)), bm.verts.new((0.0, 1.0, z1))]
        vb = [bm.verts.new((0.0, 0.0, z0)), bm.verts.new((1.0, 0.0, z0)),
              bm.verts.new((1.0, 1.0, z0)), bm.verts.new((0.0, 1.0, z0))]
        bm.verts.ensure_lookup_table()   # 5.2-pflicht (paket-CLAUDE.md bmesh-falle)
        uv_layer = bm.loops.layers.uv.new("UVMap")
        uv_full = [(0, 0), (1, 0), (1, 1), (0, 1)]
        # (verts, per-loop-UV) — front/back voll, seiten auf die passende textur-kante
        faces = [
            ((vf[0], vf[1], vf[2], vf[3]), uv_full),                       # front (+z)
            ((vb[3], vb[2], vb[1], vb[0]), [(0, 1), (1, 1), (1, 0), (0, 0)]),  # back (-z)
            ((vf[0], vb[0], vb[1], vf[1]), [(0, 0), (0, 0), (1, 0), (1, 0)]),  # unten -> v=0-kante
            ((vf[1], vb[1], vb[2], vf[2]), [(1, 0), (1, 0), (1, 1), (1, 1)]),  # rechts -> u=1-kante
            ((vf[2], vb[2], vb[3], vf[3]), [(1, 1), (1, 1), (0, 1), (0, 1)]),  # oben -> v=1-kante
            ((vf[3], vb[3], vb[0], vf[0]), [(0, 1), (0, 1), (0, 0), (0, 0)]),  # links -> u=0-kante
        ]
        for fv, uvs in faces:
            f = bm.faces.new(fv)
            f.smooth = False   # MC flat-look
            for loop, uv in zip(f.loops, uvs):
                loop[uv_layer].uv = uv
        bm.to_mesh(me)
    finally:
        bm.free()
    me.update()


# reihenfolge von `elements`-faces auf lokale wuerfel-koordinaten (MC block-modell-raum).
# from/to sind in [0..16]; wir teilen /16. face-keys -> (4 eck-offsets, UV-achsen).
_FACE_DIRS = ("down", "up", "north", "south", "west", "east")


def _element_face_verts(f, t, face):
    """Die 4 eck-koordinaten (in [0..1]) einer element-face, MC-konvention.
    f=(fx,fy,fz), t=(tx,ty,tz) sind schon /16. Reihenfolge = MC-face-winding
    (gegen den uhrzeigersinn von aussen), damit normalen nach aussen zeigen."""
    fx, fy, fz = f
    tx, ty, tz = t
    if face == "down":    # -y
        return [(fx, fy, fz), (tx, fy, fz), (tx, fy, tz), (fx, fy, tz)]
    if face == "up":      # +y
        return [(fx, ty, tz), (tx, ty, tz), (tx, ty, fz), (fx, ty, fz)]
    if face == "north":   # -z
        return [(tx, fy, fz), (fx, fy, fz), (fx, ty, fz), (tx, ty, fz)]
    if face == "south":   # +z
        return [(fx, fy, tz), (tx, fy, tz), (tx, ty, tz), (fx, ty, tz)]
    if face == "west":    # -x
        return [(fx, fy, fz), (fx, fy, tz), (fx, ty, tz), (fx, ty, fz)]
    if face == "east":    # +x
        return [(tx, fy, tz), (tx, fy, fz), (tx, ty, fz), (tx, ty, tz)]
    return None


def _build_block_mesh(me, elements):
    """Wuerfel-mesh aus `elements` (from/to in [0..16], /16 -> [0..1]) in `me`. Jede
    definierte face wird ein quad; UV aus der element-face (`uv` in [0..16], /16) oder
    default 0..1. Material-slots pro textur-key legt der aufrufer (get_item); hier
    tragen wir den textur-key per face-material-index in me an, aufrufer mappt sie."""
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")
    # material-key -> slot-index (aufrufer legt die materialien in dieser reihenfolge an)
    face_tex_keys = []
    try:
        for el in elements:
            f = tuple(c / 16.0 for c in el["from"])
            t = tuple(c / 16.0 for c in el["to"])
            faces = el.get("faces", {})
            for face_name in _FACE_DIRS:
                fd = faces.get(face_name)
                if fd is None:
                    continue
                coords = _element_face_verts(f, t, face_name)
                if coords is None:
                    continue
                verts = [bm.verts.new(c) for c in coords]
                bm.verts.ensure_lookup_table()
                try:
                    face = bm.faces.new(verts)
                except ValueError:
                    continue   # degeneriert/doppelt — ueberspringen
                face.smooth = False
                # UV: element-`uv` ist [u1,v1,u2,v2] in [0..16]; v ist top-down in MC
                uv = fd.get("uv", [0, 0, 16, 16])
                u1, v1, u2, v2 = (uv[0] / 16.0, uv[1] / 16.0, uv[2] / 16.0, uv[3] / 16.0)
                # MC v=0 ist bild-OBEN -> Blender v=1 ist oben: v -> 1-v
                quad_uv = [(u1, 1 - v1), (u2, 1 - v1), (u2, 1 - v2), (u1, 1 - v2)]
                for loop, coord_uv in zip(face.loops, quad_uv):
                    loop[uv_layer].uv = coord_uv
                # texture-ref der face merken -> material-slot
                tex_key = fd.get("texture", "#all")
                face_tex_keys.append((face, tex_key))
        bm.to_mesh(me)
    finally:
        bm.free()
    me.update()
    return face_tex_keys


# ============================================================================
# OEFFENTLICHE API: get_item(id) -> blender-obj, get_icon(id) -> image
# ============================================================================
ITEM_OBJ_PREFIX = "BBS_Item_"     # ein objekt pro item-id (cache)
ITEM_MESH_PREFIX = "BBS_ItemMesh_"
ITEM_MAT_PREFIX = "BBS_ItemMat_"   # material pro item-textur (geteilt wie bobj_import)
ICON_IMG_PREFIX = "BBS_ItemIcon_"


def _item_material(tex_png_path, mat_name):
    """Material (Closest, alpha-clip bei RGBA) fuer eine item/block-textur. Geteilt per
    name (kein duplikat bei mehreren items derselben textur). Muster == bobj_import."""
    mat = bpy.data.materials.get(mat_name)
    if mat is not None:
        return mat
    mat = bpy.data.materials.new(mat_name)
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    tex = nt.nodes.new('ShaderNodeTexImage')
    try:
        img = bpy.data.images.load(tex_png_path, check_existing=True)
    except RuntimeError as e:
        print("[bbs-bridge] item_assets: textur laedt nicht (%s): %s" % (tex_png_path, e))
        return None
    tex.image = img
    tex.interpolation = 'Closest'   # MC-look (paket-CLAUDE.md tex-falle)
    tex.location = (-340, 260)
    if bsdf is not None:
        nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
        # item-sprites sind RGBA mit echter alpha-silhouette -> alpha verdrahten + clip
        if img.depth > 24 and "Alpha" in tex.outputs:
            nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
            mat.blend_method = 'CLIP' if hasattr(mat, "blend_method") else mat.blend_method
        if "Roughness" in bsdf.inputs:
            bsdf.inputs["Roughness"].default_value = 0.9
        if "Emission Strength" in bsdf.inputs:
            # items/sprites in MC sind vollhell (unlit-look) — leichte selbst-emission
            # holt den flachen MC-look ohne von der szenen-beleuchtung abzuhaengen.
            pass
    return mat


def get_item(item_id, use_cache=True):
    """Ein MC-item als Blender-mesh-objekt (in [0..1]-block-raum, display noch NICHT
    angewandt — das macht attach_to_hand). Gibt das objekt oder None (item unbekannt/
    quelle fehlt). Gecacht per item-id: ein zweiter aufruf liefert dasselbe objekt.

    Das objekt ist NICHT in eine szene gelinkt (der aufrufer entscheidet wohin) und
    traegt custom-props `bbs_item_id`, `bbs_item_kind` fuer den HUD/hand-code."""
    item_id = _strip_ns(item_id)
    obj_name = ITEM_OBJ_PREFIX + item_id
    if use_cache:
        existing = bpy.data.objects.get(obj_name)
        if existing is not None and existing.type == 'MESH':
            return existing

    resolved = _resolve_model(item_id)
    if resolved is None:
        print("[bbs-bridge] item_assets.get_item: kein modell fuer %r" % item_id)
        return None

    mesh_name = ITEM_MESH_PREFIX + item_id
    me = bpy.data.meshes.get(mesh_name) or bpy.data.meshes.new(mesh_name)

    if resolved["kind"] == "sprite":
        tex_ref = _resolve_texture(resolved["textures"], "layer0")
        png = _texture_png_path(tex_ref)
        if png is None:
            print("[bbs-bridge] item_assets.get_item: sprite-textur fehlt (%r layer0=%r)"
                  % (item_id, tex_ref))
            return None
        w, h = _png_size(jar_read("%s/textures/%s.png"
                                  % (_ASSET_ROOT, _strip_ns(tex_ref))) or b"")
        aspect = (w / h) if (w and h) else 1.0
        _build_sprite_mesh(me, aspect=aspect)
        mat = _item_material(png, ITEM_MAT_PREFIX + _strip_ns(tex_ref).replace("/", "_"))
        me.materials.clear()
        if mat is not None:
            me.materials.append(mat)
    else:  # block
        elements = resolved["elements"]
        face_keys = _build_block_mesh(me, elements)
        # material-slot pro eindeutiger AUFGELOESTER textur (nicht pro roh-face-key: cube.json
        # nutzt #down/#up/... die alle via cube_all.json auf #all->block/stone zeigen -> EIN
        # slot fuer stone, nicht 6). key = der finale textur-ref (oder der roh-key als fallback).
        me.materials.clear()
        slot_for_ref = {}
        for poly_idx, (_face, tex_key) in enumerate(face_keys):
            if tex_key.startswith("#"):
                ref = _resolve_texture(resolved["textures"], tex_key[1:])
            else:
                ref = tex_key
            dedup_key = ref if ref else tex_key   # gleiche textur -> gleicher slot
            if dedup_key not in slot_for_ref:
                png = _texture_png_path(ref)
                if png is not None:
                    mat = _item_material(
                        png, ITEM_MAT_PREFIX + _strip_ns(ref).replace("/", "_"))
                else:
                    mat = None
                slot_for_ref[dedup_key] = len(me.materials)
                me.materials.append(mat)
            if poly_idx < len(me.polygons):
                me.polygons[poly_idx].material_index = slot_for_ref[dedup_key]
        me.update()

    obj = bpy.data.objects.get(obj_name)
    if obj is None or obj.type != 'MESH':
        obj = bpy.data.objects.new(obj_name, me)
    else:
        obj.data = me
    obj["bbs_item_id"] = item_id
    obj["bbs_item_kind"] = resolved["kind"]
    return obj


# === ITEM-IN-HAND ANKER (kamera-raum, BYTECODE-GEMESSEN — javap HeldItemRenderer 1.21.1) ===
# renderFirstPersonItem, rechte hand, KEIN swing/equip-in-bewegung (equipProgress=0,
# swingProgress=0 -> statische leere-hand-parity, v1-scope). int_15 = arm-sign = +1 (right).
#   applyEquipOffset(stack, RIGHT, 0):  translate(+1*0.56, -0.52 + 0*(-0.6), -0.72)
#   normal-item-zweig:  translate(+1*-0.4785682, -0.094387, 0.05731531)
#                       rotX(-11.935 grad); rotY(+1*65.3 grad); rotZ(+1*-9.785 grad)
#   danach folgt Transformation.apply(display) + translate(-0.5) (in display_matrix drin).
# MC-kamera-raum == Blender-kamera-raum (x-rechts, y-hoch, -z-vorn) -> 1:1 als objekt-matrix
# relativ zur hand-kamera (parent-kette cam -> bob -> item; bob identity ohne kanaele).
# equip-raise/swing = zeitabhaengig, transport 5.3b — hier bewusst der ruhepunkt (docstring).
_d = math.radians
ITEM_HAND_ANCHOR = (
    Matrix.Translation((0.56, -0.52, -0.72))                     # applyEquipOffset (equip=0)
    @ Matrix.Translation((-0.4785682, -0.094387, 0.05731531))    # normal-item base-translate
    @ Matrix.Rotation(_d(-11.935), 4, 'X')
    @ Matrix.Rotation(_d(65.3), 4, 'Y')
    @ Matrix.Rotation(_d(-9.785), 4, 'Z')
)


def attach_to_hand(item_id, parent_obj, context="firstperson_righthand",
                   anchor_matrix=None):
    """Das item-objekt (get_item) an `parent_obj` (bob-empty des fp-rigs) parenten und
    mit der vollen held-item-matrix positionieren:
        M = ITEM_HAND_ANCHOR @ display_matrix(context)
    ITEM_HAND_ANCHOR ist die bytecode-gemessene kamera-raum-kette (equip=0/swing=0, s.o.);
    `anchor_matrix` uebersteuert sie nur fuer gate-experimente (default = ITEM_HAND_ANCHOR).
    Gibt das objekt oder None.

    matrix_parent_inverse-identity + matrix_basis = local: der parent (bob, an der kamera)
    traegt nur die kamera-folge, die item-pose ist exakt anchor@display (bob identity ohne
    gebackene kanaele -- wie der arm)."""
    obj = get_item(item_id)
    if obj is None:
        return None
    disp = display_matrix(_resolved_display(item_id), context)
    anchor = anchor_matrix if anchor_matrix is not None else ITEM_HAND_ANCHOR
    local = anchor @ disp
    obj.parent = parent_obj
    obj.matrix_parent_inverse = Matrix.Identity(4)
    obj.matrix_basis = local
    return obj


def _resolved_display(item_id):
    r = _resolve_model(_strip_ns(item_id))
    return (r or {}).get("display") or {}


def get_icon(item_id, use_cache=True):
    """HUD-icon eines items als Blender-image. sprite-items -> die layer0-PNG DIREKT
    (Closest, non-color). block-items -> isometrisches mini-render (get_block_icon).
    None wenn das item unbekannt ist / quelle fehlt."""
    item_id = _strip_ns(item_id)
    resolved = _resolve_model(item_id)
    if resolved is None:
        print("[bbs-bridge] item_assets.get_icon: kein modell fuer %r" % item_id)
        return None
    if resolved["kind"] == "sprite":
        tex_ref = _resolve_texture(resolved["textures"], "layer0")
        png = _texture_png_path(tex_ref)
        if png is None:
            return None
        try:
            img = bpy.data.images.load(png, check_existing=True)
        except RuntimeError as e:
            print("[bbs-bridge] item_assets.get_icon: png laedt nicht (%s): %s" % (png, e))
            return None
        return img
    # block-item: isometrisches mini-render
    return get_block_icon(item_id, use_cache=use_cache)


# icon-render-groesse (HUD-icons sind klein; MC nutzt 16x16, wir rendern 64 fuer schaerfe)
_ICON_RENDER_RES = 64
_ICON_SCENE_NAME = "BBS_ItemIconRender"


def get_block_icon(item_id, use_cache=True):
    """Isometrisches mini-render eines block-items -> Blender-image (BBS_ItemIcon_<id>).
    Nutzt die vanilla block-GUI-display-transform (rotation [30,225,0], scale 0.625) in
    einer temporaeren render-szene (KEIN java-render-hook noetig, plan-vorgabe). Cache per
    image-name. None bei fehler. Rendert ueber write_still+load (Render-Result-falle,
    fp_hand-dossier)."""
    item_id = _strip_ns(item_id)
    img_name = ICON_IMG_PREFIX + item_id
    if use_cache:
        cached = bpy.data.images.get(img_name)
        if cached is not None:
            return cached

    obj = get_item(item_id)
    if obj is None:
        return None

    import tempfile
    scene = bpy.data.scenes.get(_ICON_SCENE_NAME) or bpy.data.scenes.new(_ICON_SCENE_NAME)
    scene.render.resolution_x = scene.render.resolution_y = _ICON_RENDER_RES
    scene.render.film_transparent = True
    scene.render.engine = 'BLENDER_EEVEE'
    scene.view_settings.view_transform = 'Standard'

    # temp-objekt (KOPIE, damit das gecachte item-objekt unberuehrt bleibt)
    tmp = obj.copy()
    tmp.data = obj.data      # mesh teilen (read-only render)
    tmp.name = "BBS_IconTmp_" + item_id
    scene.collection.objects.link(tmp)
    # vanilla block-GUI-display: rotation [30,225,0], scale 0.625, pivot -0.5
    gui_disp = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0],
                        "scale": [0.625, 0.625, 0.625]}}
    tmp.matrix_basis = display_matrix(gui_disp, "gui")

    # ortho-kamera von vorn (die display-rotation macht die isometrie)
    cam_data = bpy.data.cameras.new("BBS_IconCam_Data")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = 1.4
    cam_obj = bpy.data.objects.new("BBS_IconCam", cam_data)
    cam_obj.location = (0.0, 0.0, 4.0)
    cam_obj.rotation_euler = (0.0, 0.0, 0.0)
    scene.collection.objects.link(cam_obj)
    scene.camera = cam_obj
    sun_data = bpy.data.lights.new("BBS_IconSun_Data", 'SUN')
    sun_data.energy = 3.0
    sun_obj = bpy.data.objects.new("BBS_IconSun", sun_data)
    sun_obj.rotation_euler = (math.radians(45), math.radians(20), 0)
    scene.collection.objects.link(sun_obj)

    out = os.path.join(tempfile.gettempdir(), img_name + ".png")
    result = None
    try:
        scene.render.filepath = out
        # aktive szene temporaer auf die icon-render-szene setzen, rendern, zuruecksetzen
        # (write_still + load statt Render-Result.pixels — fp_hand-dossier Render-Result-falle)
        win = bpy.context.window
        prev_scene = win.scene if win is not None else None
        if win is not None:
            win.scene = scene
        try:
            bpy.ops.render.render(write_still=True)
        finally:
            if win is not None and prev_scene is not None:
                win.scene = prev_scene
        if os.path.isfile(out):
            result = bpy.data.images.load(out, check_existing=False)
            result.name = img_name
            # pixel-daten SOFORT in den speicher holen: ein frisch geladenes image hat
            # has_data erst nach reload() (sonst wirft .save()/.pixels beim konsumenten
            # "does not have any image data" — gemessen). Der HUD-compositor (HB-4) liest
            # die pixel direkt, darum hier proaktiv laden.
            try:
                result.reload()
            except RuntimeError:
                pass
    except Exception as e:   # noqa: BLE001 — render darf den aufrufer nie killen
        print("[bbs-bridge] item_assets.get_block_icon: render-fehler (%r): %s"
              % (item_id, e))
    finally:
        # temp-render-objekte restlos weg (das item-objekt/mesh bleibt gecacht)
        for o in (tmp, cam_obj, sun_obj):
            try:
                bpy.data.objects.remove(o, do_unlink=True)
            except (ReferenceError, RuntimeError):
                pass
        for d in (cam_data, sun_data):
            try:
                if d.users == 0:
                    (bpy.data.cameras if d == cam_data else bpy.data.lights).remove(d)
            except (ReferenceError, RuntimeError):
                pass
    return result


# ============================================================================
# CLEANUP (fuer remove_rig / gate-hygiene — eigene TEST_/BBS_-datenbloecke)
# ============================================================================
def clear_cache():
    """Modul-lokalen model-cache leeren (nach reload/gate). Beruehrt keine Blender-daten."""
    _MODEL_CACHE.clear()


def remove_all_items():
    """Alle BBS_Item_*/BBS_ItemMesh_*/BBS_ItemMat_*/BBS_ItemIcon_*-datenbloecke + die
    icon-render-szene entfernen (users==0-guard). Fuer remove_rig/gate-rueckstandsfreiheit.
    Reihenfolge objekt->mesh->material->image (referenz-kette, world_import-muster)."""
    for coll, prefix in ((bpy.data.objects, ITEM_OBJ_PREFIX),):
        for b in [x for x in coll if x.name.startswith(prefix)]:
            try:
                coll.remove(b, do_unlink=True)
            except (ReferenceError, RuntimeError):
                pass
    for coll, prefix in ((bpy.data.meshes, ITEM_MESH_PREFIX),
                         (bpy.data.materials, ITEM_MAT_PREFIX),
                         (bpy.data.images, ICON_IMG_PREFIX)):
        for b in [x for x in coll if x.name.startswith(prefix)]:
            if b.users == 0:
                try:
                    coll.remove(b)
                except (ReferenceError, RuntimeError):
                    pass
    sc = bpy.data.scenes.get(_ICON_SCENE_NAME)
    if sc is not None and sc.name != (bpy.context.window.scene.name
                                      if bpy.context.window else ""):
        try:
            bpy.data.scenes.remove(sc)
        except (ReferenceError, RuntimeError):
            pass
