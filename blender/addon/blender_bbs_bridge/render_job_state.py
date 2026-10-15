"""Durable render-job records; no Blender calls or alternate render implementation."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import uuid

TERMINAL = {'done', 'failed', 'cancelled', 'interrupted'}


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def write(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, sort_keys=True, allow_nan=False)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)
    if os.name == 'posix':
        descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def update(folder, **fields):
    path = Path(folder) / 'status.json'
    value = read(path) if path.exists() else {}
    value.update(fields, updated=time.time())
    write(path, value)
    return value


def manifest(folder):
    folder = Path(folder)
    if digest(folder / 'manifest.json') != (folder / 'manifest.sha256').read_text().strip():
        raise RuntimeError('Render manifest changed; start a new job')
    value = read(folder / 'manifest.json')
    if value.get('version') != 1 or value.get('id') != folder.name:
        raise RuntimeError('Render job identity/version mismatch')
    return value


def verify(files, root=None):
    for path, expected in files.items():
        actual = Path(root) / path if root is not None else Path(path)
        if not actual.is_file() or digest(actual) != expected:
            raise RuntimeError('Render input changed or missing: ' + str(actual))


def unit_state(unit):
    command = ['systemctl', '--user', 'show', unit,
               '--property=LoadState,ActiveState,Result,MainPID,MemoryMax,MemoryPeak']
    process = subprocess.run(command, capture_output=True, text=True, timeout=5)
    values = dict(line.split('=', 1) for line in process.stdout.splitlines() if '=' in line)
    if not values:
        raise RuntimeError('Cannot query render service: ' + process.stderr.strip())
    return values


def available_memory():
    for line in Path('/proc/meminfo').read_text().splitlines():
        if line.startswith('MemAvailable:'):
            return int(line.split()[1]) * 1024
    raise RuntimeError('Kernel did not report MemAvailable; no render started')


def completed(folder, job):
    """Reuse only native frames with their exact persisted completion receipts."""
    frames = set()
    for path in sorted((Path(folder) / 'receipts').glob('*.json')):
        receipt = read(path)
        frame = receipt['frame']
        if not isinstance(frame, int) or not job['start'] <= frame <= job['end']:
            raise RuntimeError('Frame receipt is outside job range')
        packed = read(Path(folder) / 'packed.json')
        if (receipt.get('job') != job['id'] or receipt.get('input') != packed['sha256']
                or packed.get('source') != job['snapshot']
                or receipt['name'] != 'frame_%04d.png' % frame):
            raise RuntimeError('Frame receipt belongs to another render input')
        verify({receipt['name']: receipt['sha256']}, job['output'])
        frames.add(frame)
    return frames
