"""Eingefrorener shot-stand + backup vor explizitem re-import (auftrag REDACTED §2).

Die bruecke importiert kamera und actors bei JEDEM automatischen render neu
("IMMER frisch gebakt", render_bridge-header). Fuer den live-sync ist das richtig.
Fuer einen shot, an dem jemand in Blender weitergearbeitet hat — rig angefasst,
keys gesetzt, material gebaut — ist es der teuerste bug, den es gibt: der naechste
render ersetzt die arbeit still durch den BBS-stand, ohne fehler, ohne frage.

Zwei zustaende, ein schalter (scene-prop, PERSISTIERT im .blend — anders als die
drive-toggles, die absichtlich SKIP_SAVE sind):
  * NICHT eingefroren (default): wie bisher, jeder render importiert frisch.
  * EINGEFROREN: der automatische render fasst kamera, actors und welt NICHT an und
    rendert den stand, der in der szene steht. Ein EXPLIZITER import (knopf im panel)
    laeuft weiter — aber erst nachdem der jetzige stand als kopie gesichert wurde.

Kein tick, kein socket, kein register hier: reine hilfsfunktionen, die ui.py und
render_bridge.py rufen. __init__.py registriert die prop unter PROP_FROZEN.
"""
import time

import bpy

from . import state

#: Name der scene-prop. __init__.py registriert sie ueber diese konstante, damit
#: name und verbraucher nie auseinanderlaufen (fp_hand-muster).
PROP_FROZEN = "bbsbridge_shot_eingefroren"

#: Praefix der sicherungs-collection. Alles darunter gehoert der bruecke und ist
#: rueckstandslos loeschbar; der rest der szene bleibt unberuehrt.
BACKUP_PREFIX = "BBS_Backup_"


def ist_eingefroren(scene=None):
    scene = scene or bpy.context.scene
    return bool(getattr(scene, PROP_FROZEN, False))


def backup_anlegen(scene=None):
    """Den JETZIGEN stand von MC_Actors + BBS_FilmCam als kopie sichern.

    Kopiert objekte, ihre daten UND ihre actions (obj.copy() allein teilt die action —
    ein re-import, der die action leert, leerte sonst auch die 'sicherung'). Materialien
    bleiben geteilt, bekommen aber einen fake-user: der re-import raeumt verwaiste
    datenbloecke, und geteilt+fake-user ueberlebt das. Rueckgabe: (collection-name,
    anzahl objekte) oder (None, 0) wenn es nichts zu sichern gab."""
    scene = scene or bpy.context.scene
    quellen = []
    actors = bpy.data.collections.get(state.ACTORS_COLLECTION)
    if actors is not None:
        quellen.extend(actors.all_objects)
    cam = bpy.data.objects.get("BBS_FilmCam")
    if cam is not None:
        quellen.append(cam)
    if not quellen:
        return None, 0

    stamp = time.strftime("%Y%m%d_%H%M%S")
    coll = bpy.data.collections.new(BACKUP_PREFIX + stamp)
    coll.hide_render = True          # sicherung rendert nie mit
    scene.collection.children.link(coll)
    # eltern-beziehungen (mesh -> armature) in der kopie nachziehen: erst alle
    # kopieren, dann parents umhaengen.
    kopien = {}
    for o in quellen:
        k = o.copy()
        if o.data is not None:
            k.data = o.data.copy()
        if o.animation_data and o.animation_data.action:
            k.animation_data_create()
            k.animation_data.action = o.animation_data.action.copy()
        for slot in getattr(k, "material_slots", []) or []:
            if slot.material is not None:
                slot.material.use_fake_user = True
        coll.objects.link(k)
        kopien[o.name] = k
    for o in quellen:
        if o.parent is not None and o.parent.name in kopien:
            k = kopien[o.name]
            k.parent = kopien[o.parent.name]
            k.matrix_parent_inverse = o.matrix_parent_inverse.copy()
    copied_ids = {o: kopien[o.name] for o in quellen}
    copied_ids.update({o.data: kopien[o.name].data for o in quellen if o.data is not None})
    for obj in kopien.values():
        _remap_backup_references(obj, copied_ids)
    print("[bbs-bridge] shot eingefroren -> stand gesichert als '%s' (%d objekte)"
          % (coll.name, len(kopien)))
    return coll.name, len(kopien)


def backups_auflisten():
    return sorted(c.name for c in bpy.data.collections if c.name.startswith(BACKUP_PREFIX))


def _remap_pointers(owner, copied_ids):
    """Only change pointers on copied structures; never remap an original ID globally."""
    for prop in owner.bl_rna.properties:
        if prop.type != 'POINTER' or prop.is_readonly:
            continue
        value = getattr(owner, prop.identifier, None)
        if isinstance(value, bpy.types.ID) and value in copied_ids:
            setattr(owner, prop.identifier, copied_ids[value])


def _remap_backup_references(obj, copied_ids):
    holders = [obj]
    if obj.pose is not None:
        holders.extend(obj.pose.bones)
    for holder in holders:
        _remap_pointers(holder, copied_ids)
        for constraint in holder.constraints:
            _remap_pointers(constraint, copied_ids)
            for target in getattr(constraint, 'targets', ()):
                _remap_pointers(target, copied_ids)
    for modifier in obj.modifiers:
        _remap_pointers(modifier, copied_ids)
    for data in (obj, obj.data, getattr(obj.data, 'shape_keys', None)):
        animation = getattr(data, 'animation_data', None)
        if animation is not None:
            for curve in animation.drivers:
                for variable in curve.driver.variables:
                    for target in variable.targets:
                        _remap_pointers(target, copied_ids)
