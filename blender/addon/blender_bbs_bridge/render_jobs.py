"""Save-copy submission and control of isolated, resource-limited local render jobs."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import bpy
from . import render_job_state as store, render_drive
from .process_environment import saubere_umgebung


def root():
    base = os.environ.get('BBS_RENDER_JOB_ROOT')
    if not base:
        base = str(Path(os.environ.get('XDG_STATE_HOME', Path.home() / '.local/state'))
                   / 'bbs-blender-bridge/render-jobs')
    path = Path(base).expanduser().resolve()
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    return path


def folder(job_id):
    if str(uuid.UUID(hex=job_id)).replace('-', '') != job_id:
        raise ValueError('Invalid render job ID')
    return root() / job_id


def platform_ready():
    if sys.platform != 'linux' or not shutil.which('systemd-run'):
        raise RuntimeError('Isolated render jobs require Linux with a systemd user service')
    probe = subprocess.run(['systemctl', '--user', 'show-environment'],
                           capture_output=True, timeout=5)
    if probe.returncode:
        raise RuntimeError('User service manager is unavailable; no unbounded fallback')


def dependencies():
    # Native paths include origin metadata for appended libraries. Retain existing
    # files as preconditions; missing USED images are rejected separately.
    paths = set(bpy.utils.blend_paths(absolute=True, packed=False))
    for image in bpy.data.images:
        if not image.users or image.source == 'VIEWER':
            continue
        if image.source == 'GENERATED':
            if image.is_dirty and not image.packed_file:
                raise RuntimeError('Pack the generated image before rendering: ' + image.name)
            continue
        if image.is_dirty:
            raise RuntimeError('Save the edited image before rendering: ' + image.name)
        if image.source != 'FILE':
            raise RuntimeError('Image sequence/UDIM needs an isolated asset cache: ' + image.name)
        path = Path(bpy.path.abspath(image.filepath, library=image.library))
        if not image.packed_file and not path.is_file():
            raise RuntimeError('Missing render image: ' + str(path))
    return {str(Path(p).resolve()): store.digest(p) for p in sorted(paths) if Path(p).is_file()}


def status(job_id):
    path = folder(job_id)
    job = store.manifest(path)
    value = store.read(path / 'status.json')
    service = store.unit_state('bbs-render-' + job_id + '.service')
    active = service.get('ActiveState') in {'active', 'activating', 'deactivating'}
    if not active and value.get('phase') not in store.TERMINAL:
        value.update(phase='interrupted', error='Render service stopped: ' + service.get('Result', 'unknown'))
    value.update(id=job_id, output=job['output'], service=service, active=active)
    return value


def launch(job_id):
    """A per-job lock and stable service name make repeated starts idempotent."""
    import fcntl
    path = folder(job_id)
    with (path / 'control.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        current = status(job_id)
        if current['active'] or current['phase'] == 'done':
            return current
        job = store.manifest(path)
        store.verify(job['runtime'], path)
        store.verify({'source.blend': job['snapshot']}, path)
        store.completed(path, job)
        (path / 'cancel').unlink(missing_ok=True)
        store.update(path, phase='queued', error=None, result=None)
        unit = 'bbs-render-' + job_id + '.service'
        subprocess.run(['systemctl', '--user', 'reset-failed', unit], capture_output=True, timeout=5)
        env = saubere_umgebung()
        python = shutil.which('python3', path=env.get('PATH'))
        if python is None:
            raise RuntimeError('No clean Python interpreter available for render supervisor')
        command = ['systemd-run', '--user', '--collect', '--unit=' + unit,
                   '--property=MemoryMax=%dM' % job['memory_mib'],
                   '--property=MemorySwapMax=1024M', '--property=CPUQuota=400%',
                   '--property=KillMode=control-group', '--property=UMask=0077',
                   '--working-directory=' + str(path), python,
                   str(path / 'runtime/blender_bbs_bridge/render_job_supervisor.py'), str(path)]
        try:
            started = subprocess.run(command, env=env, capture_output=True, text=True, timeout=15)
            if started.returncode:
                raise RuntimeError(started.stderr.strip())
        except Exception as error:
            # A timed-out control connection is not proof the service failed to start.
            if not status(job_id)['active']:
                store.update(path, phase='failed', error='Start failed: ' + str(error))
            raise RuntimeError('Check render job %s before retry: %s' % (job_id, error)) from error
    return status(job_id)


def submit(out_dir=None, start=None, end=None, samples=None, mux=True,
           memory_mib=4096, job_id=None, use_scripts=False):
    platform_ready()
    if type(memory_mib) is not int or not 1024 <= memory_mib <= 16384:
        raise ValueError('Render memory budget must be an integer, 1024..16384 MiB')
    if samples is not None and (type(samples) is not int or not 1 <= samples <= 4096):
        raise ValueError('Samples must be an integer, 1..4096, or None')
    if type(mux) is not bool or type(use_scripts) is not bool:
        raise ValueError('Mux and script trust must be explicit booleans')
    job_id = job_id or uuid.uuid4().hex
    path = folder(job_id)
    if path.exists():
        job = store.manifest(path)
        requested = {'start': job['start'] if start is None else start,
                     'end': job['end'] if end is None else end, 'samples': samples,
                     'memory_mib': memory_mib, 'mux': mux, 'use_scripts': use_scripts,
                     'source_path': bpy.data.filepath, 'scene': bpy.context.scene.name}
        output_base = Path(out_dir).expanduser().resolve() if out_dir else root() / 'outputs'
        if (any(job[key] != value for key, value in requested.items())
                or str(output_base / ('render-' + job_id)) != job['output']):
            raise ValueError('Job ID already belongs to different parameters; use Status or a new ID')
        return status(job_id)
    scene = bpy.context.scene
    camera = bpy.data.objects.get(render_drive.BBS_FILM_CAM_NAME)
    if camera is None or camera.type != 'CAMERA':
        raise RuntimeError('Import Film Camera before submitting a render job')
    bounds = render_drive.film_frame_range(camera)
    start = bounds[0] if start is None and bounds else start
    end = bounds[1] if end is None and bounds else end
    if (type(start) is not int or type(end) is not int or start < 0 or end < start):
        raise ValueError('Render needs an explicit non-negative inclusive frame range')
    if bpy.context.window_manager.is_interface_locked:
        raise RuntimeError('Finish the current Blender operation before making a snapshot')
    for obj in scene.objects:
        if obj.get('bbs_model_error') and not obj.hide_render:
            raise RuntimeError('Unresolved render model: ' + obj.name)
        for modifier in obj.modifiers:
            cache = getattr(modifier, 'point_cache', None)
            if ((cache is not None and (cache.use_disk_cache or cache.use_external))
                    or modifier.type in {'FLUID', 'MESH_SEQUENCE_CACHE'}
                    or (modifier.type == 'NODES' and stateful_nodes(modifier.node_group))):
                raise RuntimeError('External simulation cache needs a frozen asset copy: ' + obj.name)
    if any(cache.users for cache in bpy.data.cache_files) or any(v.users for v in bpy.data.volumes):
        raise RuntimeError('External geometry/volume caches need a frozen asset copy')
    caches = [p.point_cache for obj in scene.objects for p in obj.particle_systems]
    if scene.rigidbody_world is not None:
        caches.append(scene.rigidbody_world.point_cache)
    if any(cache.use_disk_cache or cache.use_external for cache in caches):
        raise RuntimeError('External particle/rigid-body caches need a frozen asset copy')
    findings = render_drive.cache_pruefen(scene, start, end)
    if findings:
        raise RuntimeError('; '.join(findings))
    resources = dependencies()
    source = bpy.data.filepath
    frame = scene.frame_current
    from . import actor_document
    binding = actor_document.binding()
    output_base = Path(out_dir).expanduser().resolve() if out_dir else root() / 'outputs'
    output = output_base / ('render-' + job_id)
    path.mkdir(mode=0o700)
    (path / 'receipts').mkdir()
    output.mkdir(parents=True, mode=0o700)
    try:
        copy = path / 'source.blend'
        result = bpy.ops.wm.save_as_mainfile(filepath=str(copy), copy=True,
                                           relative_remap=True, check_existing=False)
        if result != {'FINISHED'} or (bpy.data.filepath, actor_document.binding(), scene.frame_current) != (source, binding, frame):
            raise RuntimeError('Native Save Copy did not preserve the editing document')
        package = Path(__file__).resolve().parent
        shutil.copytree(package, path / 'runtime/blender_bbs_bridge',
                        ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
        runtime = {str(p.relative_to(path)): store.digest(p)
                   for p in (path / 'runtime').rglob('*') if p.is_file()}
        job = {'version': 1, 'id': job_id, 'source_path': source, 'binding': binding,
               'snapshot': store.digest(copy), 'runtime': runtime, 'dependencies': resources,
               'scene': scene.name, 'camera': camera.name, 'start': start, 'end': end,
               'samples': samples, 'mux': bool(mux), 'memory_mib': int(memory_mib),
               'use_scripts': bool(use_scripts), 'output': str(output),
               'binary': bpy.app.binary_path, 'blender_version': list(bpy.app.version),
               'build_hash': bpy.app.build_hash.decode(), 'display': os.environ.get('DISPLAY', '')}
        store.write(path / 'manifest.json', job)
        (path / 'manifest.sha256').write_text(store.digest(path / 'manifest.json'))
        store.update(path, phase='prepared', completed=0, total=end - start + 1, id=job_id)
        store.write(root() / 'latest.json', {'id': job_id})
    except Exception as error:
        store.update(path, phase='failed', error='Snapshot failed: ' + str(error))
        raise
    return launch(job_id)


def cancel(job_id):
    path = folder(job_id)
    current = status(job_id)
    if current['phase'] != 'done':
        (path / 'cancel').touch(exist_ok=True)
    return current


def latest():
    path = root() / 'latest.json'
    return store.read(path)['id'] if path.exists() else ''


def stateful_nodes(tree, seen=None):
    seen = set() if seen is None else seen
    if tree is None or tree.as_pointer() in seen:
        return False
    seen.add(tree.as_pointer())
    for node in tree.nodes:
        if node.bl_idname in {'GeometryNodeSimulationInput', 'GeometryNodeSimulationOutput', 'GeometryNodeBake'}:
            return True
        if stateful_nodes(getattr(node, 'node_tree', None), seen):
            return True
    return False
