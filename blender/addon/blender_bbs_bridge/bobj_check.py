"""BOBJ material-kompatibilitaets-pruefer (Blender -> BBS ModelForm).

WARUM DIESES MODUL EXISTIERT
----------------------------
`io_export_bobj` (community, TABU) transportiert KEINE material-daten. Empirisch am
laufenden exporter gemessen (2026-07-17, W10-B3o, headless :9976, 6 faelle rueckstandslos):
JEDER material-typ ergibt eine BYTE-IDENTISCHE BOBJ-struktur `o / v / vt / vn / f` —
kein `usemtl`, kein `mtllib`, kein textur-pfad, keine node-daten, kein config.json.
Der exporter-quelltext (export_bobj.py) hat NULL material-code (verifiziert per grep:
`material|texture|node|mtl|image|color` = 0 treffer). Er schreibt nur:
  v/vt/vn/f  geometrie + UV        <- die UV-koordinaten reisen (WO auf der textur)
  vw         vertex-weights
  arm_bone   armature
  kf/an/ao   keyframes
Das WIE der oberflaeche (welche textur, farbe, glanz) liefert BBS SEPARAT ueber die
`config.json`-`texture`-link neben der .bobj (die BBS/der user schreibt, nicht der
exporter) — siehe `bobj_import._apply_bobj_texture` fuer die konsum-seite. BBS' ModelForm
rendert EINE textur pro modell, per UV gesampelt, punkt.

FOLGE FUER DEN USER: alles was in Blender NICHT ueber eine flache image-textur ausgedrueckt
ist — procedural-noise, vertex-farben, principled-emission, mehrere material-slots — geht
beim BOBJ-export VERLOREN, still. Der user sieht in Blender bunt/leuchtend, in BBS grau/flach.
Dieses modul macht das VOR dem export sichtbar, statt hinterher zu ueberraschen.

WAS "KOMPATIBEL" HEISST (der ueberlebende pfad, aus der import-seite abgeleitet)
-------------------------------------------------------------------------------
Ein material transportiert seine ERSCHEINUNG nach BBS nur, wenn seine Base Color von
GENAU EINEM image-texture-node kommt (optional dessen Alpha fuer transparenz) — das ist
exakt was `bobj_import._bobj_material` beim RE-import wieder aufbaut. Der rest der node-
graph ist fuer BBS unsichtbar. Selbst diese eine textur reist nicht IM bobj: der user
muss sie separat als config.json-`texture`-link hinterlegen. Wir warnen also nicht "kaputt",
sondern "das transportiert sich nicht — back es in eine textur / hinterlege config.json".

BENUTZUNG (eigenstaendig, kein register/tick/socket noetig)
----------------------------------------------------------
    from blender_bbs_bridge import bobj_check
    warns = bobj_check.check_material_compat(obj)   # -> list[str], leer = alles gut
    for w in warns:
        print(w)
Die UI-buttons (ui.py = fremdes territorium) koennen das VOR `export_scene.bobj` rufen und
die liste anzeigen. `check_selection_compat()` fasst mehrere objekte zusammen.

Reine analyse: liest bpy-daten, schreibt NICHTS, wirft NICHT (defensiv pro material).
"""

import bpy


# Die BSDF-eingaenge, die BBS beim re-import ueberhaupt anfassen kann (Base Color + Alpha).
# Alles andere am Principled (Emission, Metallic, Roughness-textur, Normal, ...) ist fuer den
# BOBJ-transport unsichtbar. Quelle: bobj_import._bobj_material (Color->Base Color, Alpha->Alpha).
_TRANSPORTABLE_BSDF_INPUTS = ("Base Color",)

# Node-typen, die eine echte flache textur liefern (der einzige transportierbare farb-quell-typ).
_IMAGE_TEX_TYPE = 'TEX_IMAGE'

# Principled-emission gilt als "gesetzt" ab dieser schwelle (default ist 0 = aus). Nur zum
# WARNEN — der wert reist ohnehin nicht, wir wollen nur nicht bei jedem default-material meckern.
_EMISSION_STRENGTH_EPS = 1e-4


def _principled(mat):
    """Der Principled-BSDF-node eines materials, oder None. Nie werfen."""
    if not getattr(mat, "use_nodes", False) or mat.node_tree is None:
        return None
    for n in mat.node_tree.nodes:
        if n.type == 'BSDF_PRINCIPLED':
            return n
    return None


def _linked_from_socket(socket):
    """Der node, dessen output in `socket` (ein INPUT) fuehrt, oder None.

    Folgt genau EINE kante rueckwaerts. Reroute-nodes werden durchgereicht, damit ein
    'NodeReroute' zwischen textur und BSDF nicht faelschlich als 'keine textur' zaehlt.
    """
    if socket is None or not socket.is_linked:
        return None
    try:
        from_node = socket.links[0].from_node
    except (IndexError, AttributeError):
        return None
    # Reroute transparent durchlaufen (kann sich mehrfach ketten).
    guard = 0
    while from_node is not None and from_node.type == 'REROUTE' and guard < 16:
        inp = from_node.inputs[0] if from_node.inputs else None
        if inp is None or not inp.is_linked:
            return None
        from_node = inp.links[0].from_node
        guard += 1
    return from_node


def _base_color_source(bsdf):
    """Woher kommt die Base Color? -> ('image', node) | ('procedural', node) | ('const', None) | ('none', None).

    'image'      : ein einzelner TEX_IMAGE-node treibt Base Color (mit bild)  -> TRANSPORTIERT (via config.json)
    'image_empty': TEX_IMAGE-node ohne zugewiesenes bild                       -> transportiert nichts sinnvolles
    'procedural' : irgendein anderer node (noise/vertex-color/mix/...)         -> VERLOREN
    'const'      : nichts verlinkt, nur der default-farbwert                   -> VERLOREN (keine textur)
    """
    if bsdf is None:
        return ('none', None)
    inp = bsdf.inputs.get("Base Color")
    src = _linked_from_socket(inp)
    if src is None:
        return ('const', None)
    if src.type == _IMAGE_TEX_TYPE:
        if getattr(src, "image", None) is None:
            return ('image_empty', src)
        return ('image', src)
    return ('procedural', src)


def check_material_compat(obj):
    """Pruefe ob `obj`s materialien ihre ERSCHEINUNG durch den BOBJ-export tragen.

    Gibt eine liste menschenlesbarer warnungen zurueck (leer = alles transportabel).
    Jede warnung nennt konkret WAS verloren geht und WAS zu tun ist (meist: in eine
    image-textur backen + als config.json-'texture'-link hinterlegen).

    Ist rein diagnostisch — aendert nichts, wirft nie (defensiv pro material). Nicht-mesh-
    objekte (armature/empty/kamera) haben keine materialien -> leere liste.
    """
    warns = []
    if obj is None:
        return warns
    data = getattr(obj, "data", None)
    mats = getattr(data, "materials", None)
    if mats is None:
        return warns  # kein mesh/curve mit material-slots -> nichts zu pruefen

    # --- struktur-warnung: mehrere material-slots kollabieren ---
    # Gemessen (W10-B3o): 2-slot-mesh -> EIN o-block, faces ohne usemtl-trenner. BBS kriegt
    # ein modell = eine textur. Die zweite (und weitere) materialzuweisung ist danach weg.
    used_slots = [m for m in mats if m is not None]
    if len(used_slots) > 1:
        warns.append(
            "Objekt '%s' hat %d material-slots. BOBJ transportiert nur EINE oberflaeche pro "
            "modell (die slots kollabieren beim export zu einem mesh ohne trenner) — BBS "
            "rendert alle faces mit EINER textur. Fuer mehrere oberflaechen: in ein einziges "
            "material mit EINER atlas-/kombi-textur backen." % (obj.name, len(used_slots))
        )

    if not used_slots:
        # kein material -> BBS faellt auf sein default zurueck. Kein datenverlust, nur hinweis.
        warns.append(
            "Objekt '%s' hat kein material. BBS rendert es mit der modell-textur aus "
            "config.json (falls hinterlegt) — sonst untexturiert." % obj.name
        )
        return warns

    # --- pro material: laesst sich seine erscheinung als flache textur ausdruecken? ---
    for slot_i, mat in enumerate(used_slots):
        tag = "material '%s' (slot %d) an '%s'" % (mat.name, slot_i, obj.name)

        if not getattr(mat, "use_nodes", False) or mat.node_tree is None:
            # Node-loses material: nur diffuse_color, keine textur -> BBS kriegt nichts davon.
            warns.append(
                "%s benutzt keine nodes (nur eine flat-farbe) — diese farbe reist NICHT durch "
                "BOBJ. Weise eine image-textur zu (Base Color <- Image Texture) und hinterlege "
                "sie als config.json-'texture'-link." % tag
            )
            continue

        bsdf = _principled(mat)
        if bsdf is None:
            warns.append(
                "%s hat keinen Principled-BSDF — BBS liest die oberflaeche aus einem einzelnen "
                "image-texture-node in Base Color. Anderer shader-typ = fuer BOBJ unsichtbar." % tag
            )
            continue

        kind, node = _base_color_source(bsdf)
        if kind == 'const':
            warns.append(
                "%s: Base Color ist eine feste farbe (keine textur verlinkt) — die farbe reist "
                "NICHT durch BOBJ. Ersetze sie durch eine Image-Texture und hinterlege config.json." % tag
            )
        elif kind == 'image_empty':
            warns.append(
                "%s: der Image-Texture-node in Base Color hat KEIN bild zugewiesen — es reist "
                "keine oberflaeche. Weise ein bild zu." % tag
            )
        elif kind == 'procedural':
            nk = getattr(node, "type", "?")
            hint = ""
            if nk in ('TEX_NOISE', 'TEX_MUSGRAVE', 'TEX_VORONOI', 'TEX_WAVE', 'TEX_MAGIC',
                      'TEX_GRADIENT', 'TEX_CHECKER', 'TEX_BRICK'):
                hint = (" Procedurale texturen existieren nur in Blenders shader-graph — back sie "
                        "in eine image-textur (Bake -> Diffuse/Emit) und weise das ergebnis zu.")
            elif nk in ('VERTEX_COLOR', 'ATTRIBUTE', 'COLOR_ATTRIBUTE'):
                hint = (" Vertex-/color-attribute-farben reisen NICHT (BOBJ transportiert keine "
                        "color-attribute) — back sie in eine image-textur.")
            elif nk in ('MIX', 'MIX_RGB', 'MIX_SHADER'):
                hint = (" Ein Mix/Blend im graph ist fuer BBS unsichtbar — back das ergebnis in "
                        "eine image-textur.")
            warns.append(
                "%s: Base Color kommt aus einem '%s'-node, nicht direkt aus einer Image-Texture "
                "— diese oberflaeche reist NICHT durch BOBJ.%s" % (tag, nk, hint)
            )
        # kind == 'image' -> transportabel, keine warnung fuer die farbe selbst.

        # --- emission: gesetzt aber unsichtbar in BBS ---
        emis_in = bsdf.inputs.get("Emission Strength")
        emis_col = bsdf.inputs.get("Emission Color")
        emis_on = False
        if emis_in is not None:
            try:
                emis_on = float(emis_in.default_value) > _EMISSION_STRENGTH_EPS or emis_in.is_linked
            except (TypeError, ValueError):
                emis_on = bool(getattr(emis_in, "is_linked", False))
        if emis_on and emis_col is not None:
            warns.append(
                "%s hat Principled-Emission gesetzt — emission reist NICHT durch BOBJ (BBS' "
                "ModelForm rendert nur die flache textur, kein leuchten). Fuer leucht-look: "
                "die emission in die base-textur einbacken (heller malen), oder BBS-seitig als "
                "licht/emissive-block loesen." % tag
            )

    return warns


def check_selection_compat(objects=None):
    """Wie check_material_compat, aber ueber mehrere objekte (default: aktuelle selektion).

    Praktisch fuer einen UI-button 'pruefe auswahl vor dem BOBJ-export'. Gibt eine flache
    warn-liste zurueck (schon pro objekt beschriftet, weil check_material_compat den obj-namen
    in jede zeile schreibt). Leere liste = alle ausgewaehlten objekte transportabel.
    """
    if objects is None:
        objects = list(getattr(bpy.context, "selected_objects", []) or [])
    warns = []
    for ob in objects:
        # Nur objekte mit material-slots sind interessant; check_material_compat filtert selbst.
        warns.extend(check_material_compat(ob))
    return warns


def summarize(objects=None):
    """Eine kurze status-zeile fuer ein panel/log: 'N objekt(e), M warnung(en)'.

    Gibt (n_objects_checked, n_warnings, warn_list) zurueck.
    """
    if objects is None:
        objects = list(getattr(bpy.context, "selected_objects", []) or [])
    mesh_objs = [o for o in objects if getattr(getattr(o, "data", None), "materials", None) is not None]
    warns = check_selection_compat(mesh_objs)
    return len(mesh_objs), len(warns), warns
