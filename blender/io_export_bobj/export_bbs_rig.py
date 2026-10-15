# BBS-Control-Rig aus Blenders MENUE exportieren — Datei > Export > BBS Control-Rig.
#
# ⛔ Das ist der fehlende Importweg. BBS liest `rig.json` laengst selbst, sobald sie
# neben `model.bobj` im Modellordner liegt (RigMechanik.laden) — erzeugt wurde sie aber
# bisher NUR ueber tools/rig-export.py von der Kommandozeile. Fuer einen Nutzer existierte
# das Feature damit gar nicht: er konnte sein Rig in Blender bauen und nicht hineinbringen.
#
# Geschrieben wird ein kompletter Modellordner:
#     <ziel>/<name>/model.bobj   Skelett + Haut
#     <ziel>/<name>/rig_roh.json Blenders Constraint-Graph, roh
#     <ziel>/<name>/rig.json     was BBS ausfuehrt (Controls, Mechanik, Reihenfolge)
#     <ziel>/<name>/textures/
import os

import bpy
from bpy.props import StringProperty
from bpy_extras.io_utils import ExportHelper


def _werkzeug(name):
    """Ein Skript aus dem BBS-Baum. Der Pfad steht in den Addon-Einstellungen —
    die Extraktion ist dort versioniert und darf NICHT ins Addon kopiert werden,
    sonst laufen zwei Fassungen derselben Mathematik auseinander."""
    einst = bpy.context.preferences.addons.get(__package__)
    wurzel = (einst.preferences.bbs_werkzeuge if einst else "") or ""

    if not wurzel:
        raise RuntimeError(
            "Pfad zum BBS-Baum fehlt. Einstellungen > Add-ons > Blockbuster OBJ > "
            "'BBS-Werkzeuge' auf den tools-Ordner des Repos setzen.")

    pfad = os.path.join(bpy.path.abspath(wurzel), name)

    if not os.path.exists(pfad):
        raise RuntimeError("Nicht gefunden: " + pfad)

    return pfad


class ExportBBSRig(bpy.types.Operator, ExportHelper):
    """Das aktive Control-Rig samt Haut als BBS-Modellordner schreiben"""

    bl_idname = "export_scene.bbs_rig"
    bl_label = "BBS Control-Rig exportieren"
    bl_options = {"REGISTER"}

    filename_ext = ""
    use_filter_folder = True

    modellname: StringProperty(
        name="Modellname",
        description="Ordnername unter config/bbs/assets/models",
        default="mein_rig")

    @classmethod
    def poll(cls, context):
        return any(o.type == "ARMATURE" for o in context.scene.objects)

    def execute(self, context):
        try:
            return self._lauf(context)
        except Exception as fehler:
            self.report({"ERROR"}, str(fehler))

            return {"CANCELLED"}

    def _lauf(self, context):
        arm = context.view_layer.objects.active

        if arm is None or arm.type != "ARMATURE":
            arm = max((o for o in bpy.data.objects if o.type == "ARMATURE"),
                      key=lambda o: sum(1 for b in o.pose.bones if b.custom_shape),
                      default=None)

        if arm is None:
            raise RuntimeError("Keine Armature in der Szene.")

        ziel = os.path.join(os.path.dirname(self.filepath), self.modellname)
        os.makedirs(os.path.join(ziel, "textures"), exist_ok=True)
        roh = os.path.join(ziel, "rig_roh.json")
        bobj = os.path.join(ziel, "model.bobj")

        vorher_aktiv = context.view_layer.objects.active
        vorher_aus = [o for o in bpy.data.objects if o.select_get()]

        if context.mode != "OBJECT":
            bpy.ops.object.mode_set(mode="OBJECT")

        context.view_layer.objects.active = arm

        # Blenders Constraint-Graph. Das Skript liest den Zielpfad aus der Umgebung.
        os.environ["BBS_RIG_ROH"] = roh
        os.environ["BBS_TEXTUR_ZIEL"] = ziel
        raum = {"__name__": "__bbs__"}
        exec(compile(open(_werkzeug("rig-extrakt-blender.py")).read(),
                     "rig-extrakt-blender.py", "exec"), raum)
        extrakt = raum.get("result", {})
        # Guide objects on bones (face board frames, labels) -- task 105.
        exec(compile(open(_werkzeug("rig-beiwerk-blender.py")).read(),
                     "rig-beiwerk-blender.py", "exec"), {"__name__": "__bbs__"})

        try:
            exec(compile(open(_werkzeug("textur-blender.py")).read(),
                         "textur-blender.py", "exec"), {"__name__": "__bbs__"})
        except Exception:
            pass  # Texturen sind Zierde, kein Grund den Rig-Export zu verlieren.

        # ⛔ NUR die Meshes, die wirklich an diesem Rig haengen — nie die Widgets.
        figur = [o for o in bpy.data.objects if o.type == "MESH"
                 and (o.parent is arm
                      or any(m.type == "ARMATURE" and m.object is arm for m in o.modifiers))]

        bpy.ops.object.select_all(action="DESELECT")

        for o in figur:
            o.select_set(True)

        arm.select_set(True)
        context.view_layer.objects.active = arm
        bpy.ops.export_scene.bobj(use_selection=True, filepath=bobj)

        bpy.ops.object.select_all(action="DESELECT")

        for o in vorher_aus:
            o.select_set(True)

        context.view_layer.objects.active = vorher_aktiv

        # Roh-Graph -> was BBS ausfuehrt. Braucht numpy; Blender bringt es mit.
        import subprocess
        import sys

        lauf = subprocess.run([sys.executable, _werkzeug("rig-extrakt.py"), roh, bobj,
                               os.path.join(ziel, "rig.json")],
                              capture_output=True, text=True)

        if lauf.returncode != 0:
            raise RuntimeError("rig-extrakt: " + (lauf.stderr or lauf.stdout)[-300:])

        # BOBJ carries the interactive skin; the native library preserves drivers,
        # constraints and control widgets for the existing Blender bridge importer.
        from . import native_rig_export
        native_rig_export.write(arm, ziel)

        self.report({"INFO"}, "%s: %s Controls, %s Shapes -> %s"
                    % (self.modellname, extrakt.get("ctrls", "?"),
                       extrakt.get("shapes", "?"), ziel))

        return {"FINISHED"}


def menu_func_export(self, context):
    self.layout.operator(ExportBBSRig.bl_idname, text="BBS Control-Rig (Ordner)")
