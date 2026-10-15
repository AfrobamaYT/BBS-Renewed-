"""Small systemd-supervised process: wait for memory, then launch one offline Blender."""
import fcntl
import os
from pathlib import Path
import subprocess
import sys
import time
import traceback
import render_job_state as store
from process_environment import saubere_umgebung


def run(folder):
    job = store.manifest(folder)
    relative = next(line.split('::', 1)[1] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
    group = Path('/sys/fs/cgroup') / relative.lstrip('/')
    limit = int((group / 'memory.max').read_text())
    if limit != job['memory_mib'] * 1024 * 1024:
        raise RuntimeError('Kernel memory limit does not match the render budget')
    store.update(folder, enforced_memory_bytes=limit)
    store.verify(job['runtime'], folder)
    store.verify({'source.blend': job['snapshot']}, folder)
    store.completed(folder, job)
    runtime = Path(os.environ.get('XDG_RUNTIME_DIR', '/run/user/%d' % os.getuid()))
    if not runtime.is_dir() or runtime.stat().st_uid != os.getuid():
        raise RuntimeError('No owned user runtime directory for the render lock')
    needed = (job['memory_mib'] + 1024) * 1024 * 1024
    with (runtime / 'bbs-render-worker.lock').open('a') as lock:
        while True:
            if (folder / 'cancel').exists():
                store.update(folder, phase='cancelled', error='Cancelled while waiting')
                return
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
            except BlockingIOError:
                acquired = False
            available = store.available_memory()
            if acquired and available >= needed:
                break
            if acquired:
                fcntl.flock(lock, fcntl.LOCK_UN)
            store.update(folder, phase='waiting', available_mib=available // (1024 * 1024),
                         needed_mib=needed // (1024 * 1024), supervisor_pid=os.getpid())
            time.sleep(2)
        try:
            execute(folder, job)
        finally:
            store.update(folder, peak_memory_bytes=int((group / 'memory.peak').read_text()))


def execute(folder, job):
    env = saubere_umgebung()
    env.pop('WAYLAND_DISPLAY', None)
    env.pop('HYPRLAND_INSTANCE_SIGNATURE', None)
    env['DISPLAY'] = job['display']
    env['BLENDER_USER_CONFIG'] = str(folder / 'config')
    env['BLENDER_USER_SCRIPTS'] = str(folder / 'scripts')
    env['OMP_NUM_THREADS'] = '4'
    store.update(folder, phase='starting', supervisor_pid=os.getpid())
    command = [job['binary'], '--background', '--factory-startup', '--offline-mode',
               '--enable-autoexec' if job['use_scripts'] else '--disable-autoexec',
               '--threads', '4', '--python-exit-code', '1', '--python',
               str(folder / 'runtime/blender_bbs_bridge/render_job_worker.py'), '--', str(folder)]
    with (folder / 'blender.log').open('ab') as log:
        child = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT)
        requested = None
        while child.poll() is None:
            if (folder / 'cancel').exists():
                requested = requested or time.monotonic()
                if time.monotonic() - requested > 30:
                    # Terminate only this Popen child, never a process matched by name.
                    child.terminate()
                    try:
                        child.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        child.kill()
                    break
            time.sleep(.5)
        code = child.wait()
    value = store.read(folder / 'status.json')
    if value.get('phase') not in store.TERMINAL or (code != 0 and value.get('phase') == 'done'):
        value = store.update(folder, phase='cancelled' if requested else 'failed',
                             error='Render process exited with code %d; see blender.log' % code)
    store.update(folder, process_exit=code)
    if value.get('phase') == 'failed':
        raise RuntimeError(value.get('error', 'Render worker failed'))


if __name__ == '__main__':
    directory = Path(sys.argv[1]).resolve()
    try:
        run(directory)
    except Exception as error:
        traceback.print_exc()
        store.update(directory, phase='failed', error=str(error))
        raise SystemExit(1)
