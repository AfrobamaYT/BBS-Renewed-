"""Offline Blender entry point. Uses the existing renderer on a private packed copy."""
import os
from pathlib import Path
import sys
import traceback
import bpy
sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_job_state as store


def check_drivers(scene):
    failed = []
    holders = [scene, scene.world] + list(scene.objects)
    holders += [o.data for o in scene.objects if o.data is not None]
    for holder in holders:
        animation = getattr(holder, 'animation_data', None)
        for curve in animation.drivers if animation else ():
            if not curve.driver.is_valid:
                failed.append(holder.name + ':' + curve.data_path)
    if failed:
        raise RuntimeError('Invalid evaluated drivers in render copy: ' + ', '.join(failed[:8]))


def open_snapshot(folder, job):
    packed = folder / 'packed.blend'
    receipt = folder / 'packed.json'
    if receipt.exists():
        store.verify({'packed.blend': store.read(receipt)['sha256']}, folder)
        bpy.ops.wm.open_mainfile(filepath=str(packed), load_ui=False, use_scripts=job['use_scripts'])
        return
    store.verify(job['dependencies'])
    bpy.ops.wm.open_mainfile(filepath=str(folder / 'source.blend'),
                             load_ui=False, use_scripts=job['use_scripts'])
    if bpy.ops.file.pack_libraries() != {'FINISHED'} or bpy.ops.file.pack_all() != {'FINISHED'}:
        raise RuntimeError('Native asset packing failed')
    # Reject unresolved resources instead of rendering against changing external files.
    for image in bpy.data.images:
        if image.users and image.source == 'FILE' and not image.packed_file:
            raise RuntimeError('Image was not packed: ' + image.name)
    for collection in (bpy.data.libraries, bpy.data.sounds, bpy.data.fonts):
        for item in collection:
            path = getattr(item, 'filepath', '')
            if item.users and path and not path.startswith('<') and not item.packed_file:
                raise RuntimeError('External dependency was not packed: ' + item.name)
    # Blender retains .blend origin paths for appended data even with no linked
    # libraries. Other unresolved paths (movies, caches, shader files) are not safe.
    remaining = bpy.utils.blend_paths(absolute=True, packed=False)
    unresolved = [p for p in remaining if not p.lower().endswith('.blend')]
    if unresolved:
        raise RuntimeError('Unpacked render dependencies: ' + ', '.join(unresolved[:5]))
    store.verify(job['dependencies'])
    if bpy.ops.wm.save_as_mainfile(filepath=str(packed), check_existing=False) != {'FINISHED'}:
        raise RuntimeError('Cannot save the packed render input')
    store.write(receipt, {'sha256': store.digest(packed), 'source': job['snapshot']})


def run(folder):
    assert bpy.app.background
    job = store.manifest(folder)
    store.verify(job['runtime'], folder)
    store.verify({'source.blend': job['snapshot']}, folder)
    if list(bpy.app.version) != job['blender_version'] or bpy.app.build_hash.decode() != job['build_hash']:
        raise RuntimeError('Blender version/build changed; submit a new render job')
    store.update(folder, phase='preparing', worker_pid=os.getpid())
    open_snapshot(folder, job)
    sys.path.insert(0, str(folder / 'runtime'))
    from blender_bbs_bridge import render_drive, actor_illusion, state
    actor_illusion.register_drivers()
    scene = bpy.data.scenes[job['scene']]
    if bpy.context.window is not None:
        bpy.context.window.scene = scene
    scene.render.use_file_extension = True
    bpy.context.view_layer.update()
    check_drivers(scene)
    previous = store.completed(folder, job)
    rendered = []
    def progress(frame, total, filename, reused):
        check_drivers(scene)
        if not reused:
            store.write(folder / 'receipts' / ('%d.json' % frame),
                        {'frame': frame, 'name': Path(filename).name, 'job': job['id'],
                         'input': store.read(folder / 'packed.json')['sha256'],
                         'sha256': store.digest(filename)})
            rendered.append(frame)
        store.update(folder, phase='rendering', completed=frame - job['start'] + 1,
                     total=total, last_frame=frame, reused=sorted(previous),
                     rendered_this_attempt=rendered, worker_pid=os.getpid())
    store.update(folder, phase='rendering', reused=sorted(previous),
                 auto_run_texts_skipped=bool(bpy.app.autoexec_fail))
    with bpy.context.temp_override(scene=scene):
        result = render_drive.render_film(out_dir=job['output'], start=job['start'],
            end=job['end'], samples=job['samples'], mux=job['mux'], scene=scene,
            cam_name=job['camera'], progress=progress,
            cancelled=lambda: (folder / 'cancel').exists(), completed_frames=previous)
    if not result['ok'] or state._connected or result['seq_out_delta'] != 0:
        raise RuntimeError('Offline render contract failed: ' + str(result))
    store.update(folder, phase='done', result=result, completed=job['end'] - job['start'] + 1)


if __name__ == '__main__':
    directory = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
    try:
        run(directory)
    except Exception as error:
        renderer = sys.modules.get('blender_bbs_bridge.render_drive')
        cancel_type = getattr(renderer, 'RenderCancelled', ())
        phase = 'cancelled' if isinstance(error, cancel_type) else 'failed'
        store.update(directory, phase=phase, error=str(error), worker_pid=os.getpid())
        if phase == 'failed':
            traceback.print_exc()
            raise
