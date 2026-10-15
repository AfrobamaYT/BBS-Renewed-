# Pruefsatz fuer den arm_ik-Transport des BOBJ-Addon-Forks.
# Aufruf:  blender -b --factory-startup --python blender/pruefe-arm-ik.py
# (factory-startup: installierte Fremd-Addons wie Poliigon blockieren headless.)
# Headless-Beweis fuer den arm_ik-Transport (BBS #65).
# Teil A: handgesetzter IK-Constraint -> arm_ik-Zeile mit exakten Werten.
# Teil B: mixamorig-Skelett -> Operator bbs.mixamo_ik -> 4 Ketten + Ziel-Knochen.
import math
import os
import sys

import bpy
from mathutils import Vector

import tempfile
OUT = tempfile.mkdtemp(prefix="bobj-ik-")
fehler = []


def pruefe(name, bedingung, detail=""):
    status = "OK  " if bedingung else "FEHL"
    print("[%s] %s %s" % (status, name, detail))
    if not bedingung:
        fehler.append(name)


def leere_szene():
    bpy.ops.wm.read_homefile(use_empty=True)


def neue_armature(name):
    arm_data = bpy.data.armatures.new(name)
    obj = bpy.data.objects.new(name, arm_data)
    bpy.context.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    return obj


def bone(obj, name, head, tail, parent=None):
    b = obj.data.edit_bones.new(name)
    b.head = Vector(head)
    b.tail = Vector(tail)
    if parent is not None:
        b.parent = obj.data.edit_bones[parent]
    return b


def haenge_wuerfel_an(obj, gruppe):
    bpy.ops.mesh.primitive_cube_add(size=0.5, location=(0, 0, 0.5))
    cube = bpy.context.object
    vg = cube.vertex_groups.new(name=gruppe)
    vg.add(range(len(cube.data.vertices)), 1.0, 'REPLACE')
    mod = cube.modifiers.new("Armature", 'ARMATURE')
    mod.object = obj
    cube.parent = obj
    bpy.context.view_layer.objects.active = obj


bpy.ops.preferences.addon_enable(module="io_export_bobj")

# ---------- Teil A: handgesetzter Constraint ----------
leere_szene()
obj = neue_armature("ArmA")
bpy.ops.object.mode_set(mode='EDIT')
bone(obj, "root", (0, 0, 0), (0, 0, 0.4))
bone(obj, "unter", (0, 0, 0.4), (0, 0.03, 0.8), "root")
bone(obj, "ziel", (0, 0.1, 0.8), (0, 0.1, 0.9))
bone(obj, "pol", (0, 0.3, 0.4), (0, 0.3, 0.5))
bpy.ops.object.mode_set(mode='POSE')
pb = obj.pose.bones["unter"]
con = pb.constraints.new('IK')
con.target = obj
con.subtarget = "ziel"
con.chain_count = 2
con.pole_target = obj
con.pole_subtarget = "pol"
con.pole_angle = 0.3
con.influence = 0.75
con.use_stretch = True
bpy.ops.object.mode_set(mode='OBJECT')
haenge_wuerfel_an(obj, "root")

pfad_a = os.path.join(OUT, "iktest_a.bobj")
bpy.ops.export_scene.bobj(filepath=pfad_a, use_selection=False)

zeilen = [z for z in open(pfad_a).read().splitlines() if z.startswith("arm_ik")]
pruefe("A: genau eine arm_ik-zeile", len(zeilen) == 1, str(zeilen))

if zeilen:
    t = zeilen[0].split()
    pruefe("A: tip/ziel/kette/pol", t[1:5] == ["unter", "ziel", "2", "pol"], str(t[1:5]))
    pruefe("A: polwinkel in grad", abs(float(t[5]) - math.degrees(0.3)) < 0.01, t[5])
    pruefe("A: gewicht 0.75", abs(float(t[6]) - 0.75) < 1e-5, t[6])
    pruefe("A: tipRot 0, stretch 1", t[7] == "0" and t[8] == "1", str(t[7:9]))

# ---------- Teil B: mixamorig-skelett + operator ----------
leere_szene()
obj = neue_armature("ArmB")
P = "mixamorig:"
bpy.ops.object.mode_set(mode='EDIT')
bone(obj, P + "Hips", (0, 0, 0.95), (0, 0, 1.1))
bone(obj, P + "Spine", (0, 0, 1.1), (0, 0, 1.4), P + "Hips")
for seite, sx in (("Left", 1), ("Right", -1)):
    bone(obj, P + seite + "Arm", (sx * 0.2, 0, 1.4), (sx * 0.45, 0.02, 1.4), P + "Spine")
    bone(obj, P + seite + "ForeArm", (sx * 0.45, 0.02, 1.4), (sx * 0.7, 0, 1.4), P + seite + "Arm")
    bone(obj, P + seite + "Hand", (sx * 0.7, 0, 1.4), (sx * 0.8, 0, 1.4), P + seite + "ForeArm")
    bone(obj, P + seite + "UpLeg", (sx * 0.1, 0, 0.95), (sx * 0.1, -0.02, 0.5), P + "Hips")
    bone(obj, P + seite + "Leg", (sx * 0.1, -0.02, 0.5), (sx * 0.1, 0, 0.08), P + seite + "UpLeg")
    bone(obj, P + seite + "Foot", (sx * 0.1, 0, 0.08), (sx * 0.1, -0.12, 0.02), P + seite + "Leg")
bpy.ops.object.mode_set(mode='OBJECT')

resultat = bpy.ops.bbs.mixamo_ik()
pruefe("B: operator FINISHED", resultat == {'FINISHED'}, str(resultat))
haenge_wuerfel_an(obj, P + "Hips")

pfad_b = os.path.join(OUT, "iktest.bobj")
bpy.ops.export_scene.bobj(filepath=pfad_b, use_selection=False)

inhalt = open(pfad_b).read().splitlines()
ik_zeilen = [z for z in inhalt if z.startswith("arm_ik")]
bones = [z.split()[1] for z in inhalt if z.startswith("arm_bone")]
pruefe("B: vier arm_ik-zeilen", len(ik_zeilen) == 4, "\n  " + "\n  ".join(ik_zeilen))
for ziel in ("IK.hand.L", "IK.hand.R", "IK.foot.L", "IK.foot.R",
             "IK.elbow.L", "IK.elbow.R", "IK.knee.L", "IK.knee.R"):
    pruefe("B: knochen %s exportiert" % ziel, ziel in bones)
tips = sorted(z.split()[1] for z in ik_zeilen)
pruefe("B: ketten sitzen an ForeArm/Leg", tips == sorted(
    [P + "LeftForeArm", P + "RightForeArm", P + "LeftLeg", P + "RightLeg"]), str(tips))
pruefe("B: stretch aus (starre glieder)", all(z.split()[8] == "0" for z in ik_zeilen),
       str([z.split()[8] for z in ik_zeilen]))

print("\n%d fehler" % len(fehler))
sys.exit(1 if fehler else 0)
