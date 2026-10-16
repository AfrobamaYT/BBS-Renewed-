"""Blender-native model source for the hybrid renderer.

BOBJ remains the interactive BBS representation.  A model may additionally name a
compact ``.blend`` library in its adjacent ``config.json``.  During a hybrid render
the library collection is appended as an editable actor instance, so Blender keeps
the author's original mesh data, modifiers (including Preserve Volume), shape-key
drivers and complete material node graphs instead of reconstructing an approximation
from BOBJ.

Config shape::

    "blender_source": {
      "file": "REDACTED_Advanced_Hybrid.blend",
      "collection": "REDACTED_Hybrid_Asset",
      "root": "REDACTED_Rig",
      "world": "Meshy material review world"
    }

The path is deliberately restricted to the model directory.  An explicitly declared
but broken native source raises: silently falling back to BOBJ would make a successful
hybrid job look plausible while violating the exact-render contract.
"""

import os

import bpy


class NativeModelError(RuntimeError):
    pass


def _source_path(bobj_path, spec):
    model_dir = os.path.realpath(os.path.dirname(bobj_path))
    rel = str(spec.get("file") or "").strip().replace("\\", "/")
    if not rel:
        raise NativeModelError("blender_source.file fehlt")
    path = os.path.realpath(os.path.join(model_dir, rel))
    try:
        inside = os.path.commonpath([path, model_dir]) == model_dir
    except ValueError:
        inside = False
    if not inside:
        raise NativeModelError("blender_source.file liegt ausserhalb des Modellordners")
    if not path.lower().endswith(".blend") or not os.path.isfile(path):
        raise NativeModelError("Blender-Quelle fehlt oder ist keine .blend: %s" % path)
    if bpy.data.filepath:
        try:
            if os.path.samefile(path, bpy.data.filepath):
                raise NativeModelError("Blender-Quelle darf nicht die gerade offene .blend sein")
        except OSError:
            pass
    return path


def _remove_loaded(objects, collection=None, world=None):
    for obj in objects:
        try:
            bpy.data.objects.remove(obj, do_unlink=True)
        except (ReferenceError, RuntimeError):
            pass
    if collection is not None:
        try:
            bpy.data.collections.remove(collection)
        except (ReferenceError, RuntimeError):
            pass
    if world is not None:
        try:
            if world.users == 0:
                bpy.data.worlds.remove(world)
        except (ReferenceError, RuntimeError):
            pass


def _clear_library_fake_users(objects, world):
    """Make appended dependencies removable by actors.py's scoped cleanup.

    The compact library is written with fake users so opening and resaving the asset
    itself cannot discard dependencies.  Those flags must not become ownership claims
    in the consumer file, otherwise every full actor refresh leaves Material.001,
    Light.001 and image datablocks behind.
    """
    blocks = set(objects)
    if world is not None:
        blocks.add(world)
    for obj in objects:
        if obj.data is not None:
            blocks.add(obj.data)
        ad = obj.animation_data
        if ad is not None and ad.action is not None:
            blocks.add(ad.action)
        if obj.type == 'MESH':
            if obj.data.shape_keys is not None:
                blocks.add(obj.data.shape_keys)
            for material in obj.data.materials:
                if material is None:
                    continue
                blocks.add(material)
                if material.use_nodes and material.node_tree is not None:
                    for node in material.node_tree.nodes:
                        if node.type == 'TEX_IMAGE' and node.image is not None:
                            blocks.add(node.image)
    for block in blocks:
        if hasattr(block, "use_fake_user"):
            block.use_fake_user = False


def import_for_bobj(bobj_path, config, target_collection, actor_name):
    """Append and return the native actor root, or ``None`` when no source is declared.

    The returned root has every appended render dependency as a descendant and every
    object is linked directly to ``target_collection``.  That matches actors.py's
    existing full-import and per-actor cleanup contracts.
    """
    spec = config.get("blender_source") if isinstance(config, dict) else None
    if spec is None:
        return None
    if not isinstance(spec, dict):
        raise NativeModelError("blender_source muss ein Objekt sein")

    path = _source_path(bobj_path, spec)
    collection_name = str(spec.get("collection") or "").strip()
    root_name = str(spec.get("root") or "").strip()
    world_name = str(spec.get("world") or "").strip()
    render_names = spec.get("render_objects", [])
    if isinstance(render_names, str):
        render_names = [render_names]
    if not isinstance(render_names, list) or not all(isinstance(name, str) and name.strip()
                                                     for name in render_names):
        raise NativeModelError("blender_source.render_objects muss eine Namensliste sein")
    render_names = [name.strip() for name in render_names]
    if not root_name or (not collection_name and not render_names):
        raise NativeModelError("blender_source braucht root und collection oder render_objects")

    loaded_collection = None
    loaded_world = None
    loaded_world_fresh = False
    objects = []
    try:
        # Worlds are scene-level, not descendants of the actor root.  Reuse the exact
        # already appended world while its source file has not changed; otherwise every
        # live full-import would leave another World.001 behind through the model scene.
        source_mtime = os.path.getmtime(path)
        if world_name:
            for candidate in bpy.data.worlds:
                if (candidate.get("bbs_native_source") == path
                        and candidate.get("bbs_native_world_original") == world_name
                        and abs(float(candidate.get("bbs_native_source_mtime", -1.0))
                                - source_mtime) < 1e-6):
                    loaded_world = candidate
                    break
        with bpy.data.libraries.load(path, link=False, recursive=True) as (source, loaded):
            if collection_name and collection_name not in source.collections:
                raise NativeModelError("Collection %r fehlt in %s" % (collection_name, path))
            if world_name and world_name not in source.worlds:
                raise NativeModelError("World %r fehlt in %s" % (world_name, path))
            if collection_name:
                loaded.collections = [collection_name]
            else:
                requested = [root_name] + render_names
                missing = [name for name in requested if name not in source.objects]
                if missing:
                    raise NativeModelError("Objekte fehlen in %s: %s" % (path, ", ".join(missing)))
                loaded.objects = requested
            loaded.worlds = [world_name] if world_name and loaded_world is None else []

        loaded_collection = loaded.collections[0] if collection_name and loaded.collections else None
        if loaded_world is None:
            loaded_world = loaded.worlds[0] if loaded.worlds else None
            if loaded_world is not None:
                loaded_world_fresh = True
                loaded_world["bbs_native_source"] = path
                loaded_world["bbs_native_world_original"] = world_name
                loaded_world["bbs_native_source_mtime"] = source_mtime
        if collection_name:
            if loaded_collection is None:
                raise NativeModelError("Collection %r konnte nicht geladen werden" % collection_name)
            objects = list(loaded_collection.all_objects)
        else:
            objects = [obj for obj in loaded.objects if obj is not None]
        roots = ([loaded.objects[0]] if not collection_name and loaded.objects else
                 [obj for obj in objects if obj.get("bbs_native_role") == "root"])
        if not roots:
            roots = [obj for obj in objects if obj.name == root_name]
        if len(roots) != 1:
            raise NativeModelError("genau ein Root %r erwartet, gefunden: %d" % (root_name, len(roots)))
        root = roots[0]
        if root.type != 'ARMATURE':
            raise NativeModelError("native Root %r ist keine Armature" % root.name)
        widgets = list(dict.fromkeys(bone.custom_shape for bone in root.pose.bones
                                     if bone.custom_shape is not None))
        for widget in widgets:
            if widget not in objects:
                objects.append(widget)
        _clear_library_fake_users(objects, loaded_world)

        render_meshes = [obj for obj in objects
                         if obj.type == 'MESH' and obj.get("bbs_native_role") == "render"]
        if not render_meshes and render_names and not collection_name:
            render_meshes = [obj for obj in loaded.objects[1:] if obj is not None and obj.type == 'MESH']
        elif not render_meshes and render_names:
            render_meshes = [obj for obj in objects if obj.type == 'MESH'
                             and obj.name in render_names]
        if not render_meshes:
            render_meshes = [obj for obj in objects if obj.type == 'MESH'
                             and any(mod.type == 'ARMATURE' and mod.object == root
                                     for mod in obj.modifiers)]
        if not render_meshes:
            render_meshes = [obj for obj in objects if obj.type == 'MESH'
                             and obj.parent == root and obj not in widgets]
        if not render_meshes:
            raise NativeModelError("native Quelle enthaelt kein Render-Mesh")
        for mesh in render_meshes:
            armature = next((m for m in mesh.modifiers
                             if m.type == 'ARMATURE' and m.object == root), None)
            if armature is None:
                raise NativeModelError("Render-Mesh %r ist nicht an den nativen Root gebunden" % mesh.name)
            if mesh.parent != root:
                world = mesh.matrix_world.copy()
                mesh.parent = root
                mesh.matrix_world = world

        from . import native_basis
        try:
            native_basis.apply(root, bobj_path)
        except (native_basis.BasisError, OSError, ValueError) as error:
            raise NativeModelError(str(error)) from error

        # Collection append creates a temporary library collection.  Link every object
        # directly into MC_Actors first; removing that collection then cannot orphan them.
        for obj in objects:
            if obj.name not in target_collection.objects:
                target_collection.objects.link(obj)
        if loaded_collection is not None:
            try:
                bpy.data.collections.remove(loaded_collection)
            except (ReferenceError, RuntimeError):
                pass
        loaded_collection = None

        original_root_name = root.name
        root.name = actor_name
        for widget in widgets:
            if widget.parent != root:
                world_matrix = widget.matrix_world.copy()
                widget.parent = root
                widget.matrix_world = world_matrix
            widget["bbs_rig_widget"] = True
            widget["bbs_native_role"] = "widget"
            widget.hide_render = True
            widget.hide_viewport = True
        root["bbs_native_source"] = path
        root["bbs_native_root"] = root_name
        root["bbs_native_exact"] = True
        if loaded_world is not None:
            root["bbs_native_world"] = loaded_world.name
        from . import native_controls
        try:
            native_controls.apply(root, bobj_path)
        except Exception as error:
            objects.extend(child for child in root.children_recursive if child not in objects)
            raise NativeModelError('Control widgets: ' + str(error)) from error
        for obj in objects:
            if obj != root:
                role = str(obj.get("bbs_native_role") or obj.type.lower())
                obj.name = "%s__%s__%s" % (actor_name, role, obj.name)

        print("[bbs-bridge] Blender-native model %r: %d objects from %s (root %s -> %s, "
              "world=%s)" % (actor_name, len(objects), path, original_root_name, root.name,
                               loaded_world.name if loaded_world else "default"))
        return root
    except Exception:
        # A reused authored world may belong to an earlier valid native actor.  Only
        # remove the world appended by this failed call; shared cached state is not ours.
        _remove_loaded(objects, loaded_collection,
                       loaded_world if loaded_world_fresh else None)
        raise
