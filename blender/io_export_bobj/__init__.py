# ##### BEGIN GPL LICENSE BLOCK #####
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation; either version 2
#  of the License, or (at your option) any later version.
#
# ##### END GPL LICENSE BLOCK #####

bl_info = {
    "name": "Blockbuster extended OBJ format",
    "author": "Campbell Barton, Bastien Montagne, McHorse",
    "version": (0, 3, 0),
    "blender": (4, 0, 0),
    "location": "File > Import/Export",
    "description": "Import/Export Blockbuster OBJ models (meshes, armatures and keyframes)",
    "warning": "Bones must use XYZ Euler rotation mode for animations to work correctly",
    "category": "Import-Export",
}

import bpy
from bpy.props import BoolProperty, StringProperty
from bpy_extras.io_utils import (
    ExportHelper, ImportHelper,
    orientation_helper, path_reference_mode, axis_conversion,
)


# ------------------------------------------------------------------ #
#  EXPORTER
# ------------------------------------------------------------------ #

@orientation_helper(axis_forward='Z', axis_up='Y')
class ExportBOBJ(bpy.types.Operator, ExportHelper):
    bl_idname  = "export_scene.bobj"
    bl_label   = "Export Blockbuster OBJ"
    bl_options = {'PRESET'}

    filename_ext = ".bobj"
    filter_glob: StringProperty(default="*.bobj", options={'HIDDEN'})

    use_selection: BoolProperty(
        name="Selection Only",
        description="Export selected objects only",
        default=False,
    )
    include_geometry: BoolProperty(
        name="Export Geometry",
        description="Include meshes in the model file",
        default=True,
    )
    include_keyframes: BoolProperty(
        name="Export Keyframes",
        description="Include actions in the model file",
        default=True,
    )
    keep_vertex_order: BoolProperty(
        name="Keep Vertex Order",
        description="",
        default=False,
    )
    path_mode: path_reference_mode
    check_extension = True

    def execute(self, context):
        from . import export_bobj
        from mathutils import Matrix

        keywords = self.as_keywords(ignore=(
            "axis_forward", "axis_up", "check_existing", "filter_glob", "path_mode",
        ))
        keywords["global_matrix"] = (
            Matrix.Scale(1, 4) @
            axis_conversion(to_forward=self.axis_forward, to_up=self.axis_up).to_4x4()
        )
        return export_bobj.save(context, **keywords)


# ------------------------------------------------------------------ #
#  IMPORTER
# ------------------------------------------------------------------ #

@orientation_helper(axis_forward='Z', axis_up='Y')
class ImportBOBJ(bpy.types.Operator, ImportHelper):
    bl_idname  = "import_scene.bobj"
    bl_label   = "Import Blockbuster OBJ"
    bl_options = {'PRESET', 'UNDO'}

    filename_ext = ".bobj"
    filter_glob: StringProperty(default="*.bobj", options={'HIDDEN'})

    def execute(self, context):
        from . import import_bobj
        from mathutils import Matrix

        global_matrix = (
            Matrix.Scale(1, 4) @
            axis_conversion(to_forward=self.axis_forward, to_up=self.axis_up).to_4x4()
        )
        return import_bobj.load(context, self.filepath, global_matrix=global_matrix)


# ------------------------------------------------------------------ #
#  Menu hooks
# ------------------------------------------------------------------ #

def menu_func_export(self, context):
    self.layout.operator(ExportBOBJ.bl_idname, text="Blockbuster OBJ (.bobj)")


def menu_func_import(self, context):
    self.layout.operator(ImportBOBJ.bl_idname, text="Blockbuster OBJ (.bobj)")


# ------------------------------------------------------------------ #
#  Register / unregister
# ------------------------------------------------------------------ #

from . import mixamo_ik
from . import export_bbs_rig


class BBSAddonPrefs(bpy.types.AddonPreferences):
    """Wo der BBS-Baum liegt. Der Rig-Export ruft die dort VERSIONIERTEN Skripte auf,
    statt sie ins Addon zu kopieren — zwei Fassungen derselben Mathematik laufen
    sonst auseinander."""

    bl_idname = __package__

    bbs_werkzeuge: StringProperty(
        name="BBS-Werkzeuge",
        description="tools-Ordner des BBS-Repos (enthaelt rig-extrakt-blender.py)",
        subtype="DIR_PATH",
        default="")

    def draw(self, context):
        self.layout.prop(self, "bbs_werkzeuge")


classes = (
    BBSAddonPrefs,
    ExportBOBJ,
    ImportBOBJ,
    export_bbs_rig.ExportBBSRig,
    mixamo_ik.BBS_OT_mixamo_ik,
)


def register():
    from bpy.utils import register_class
    for cls in classes:
        register_class(cls)
    bpy.types.TOPBAR_MT_file_export.append(menu_func_export)
    bpy.types.TOPBAR_MT_file_export.append(export_bbs_rig.menu_func_export)
    bpy.types.TOPBAR_MT_file_import.append(menu_func_import)


def unregister():
    from bpy.utils import unregister_class
    for cls in reversed(classes):
        unregister_class(cls)
    bpy.types.TOPBAR_MT_file_export.remove(export_bbs_rig.menu_func_export)
    bpy.types.TOPBAR_MT_file_export.remove(menu_func_export)
    bpy.types.TOPBAR_MT_file_import.remove(menu_func_import)
