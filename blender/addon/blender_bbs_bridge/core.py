"""socket-io + protokoll + kamera-mapping.

_connect/_next_seq/_send/_poll_recv sind der non-blocking TCP-layer gegen den
BBS-socket-server (siehe state.BBS_HOST/state.BBS_PORT). _get_sync_camera +
die beiden _*_cam-mapping-funktionen uebersetzen zwischen Blenders und BBS'
koordinatensystem (siehe COORDINATE_CONTRACT-kommentar im __init__.py-header).
"""

import bpy
import math
import socket
import threading
import json
from mathutils import Euler

from . import state

_connection_lock = threading.Lock()
_connection_generation = 0


def _clear_connection_state():
    """Forget transport state under _connection_lock; durable actor intents stay intact."""
    previous, state._sock = state._sock, None
    if previous is not None:
        try:
            previous.close()
        except OSError:
            pass
    state._connected = state._peer_ready = False
    state._connected_endpoint = None
    state.peer_capabilities = set()
    state.peer_protocol = 0
    state.peer_version = ''
    state.peer_project = state.peer_engine = ''
    state._recv_buf = b''
    state._last_seq_in.clear()
    state._cam_adopted = False
    state._initial_camera_pending = True
    state.cam_owner = state.playhead_owner = 'NOBODY'
    state._playhead_last_preview_sent = None
    state._playhead_last_remote_frame = None
    state._marker_revision = ''
    state._marker_status = 'waiting for BBS marker snapshot'
    state._marker_lifetime += 1


def _stop_connection():
    """Cancel the connection lifetime without waiting on a blocking network operation."""
    global _connection_generation
    with _connection_lock:
        _connection_generation += 1
        _clear_connection_state()
        state._status = 'disconnected'


def _get_sync_camera():
    """Die zu synchende kamera: aktive scene-kamera, sonst erste kamera."""
    scene = bpy.context.scene
    if scene.camera is not None:
        return scene.camera
    for obj in scene.objects:
        if obj.type == 'CAMERA':
            return obj
    return None


# ---- mapping Blender -> BBS ----
def _blender_cam_to_bbs(cam):
    m = cam.matrix_world
    loc = m.to_translation()
    bbs_x = loc.x
    bbs_y = loc.z
    bbs_z = -loc.y
    rot = m.to_euler('XYZ')
    bl_pitch = math.degrees(rot.x)
    bl_roll = math.degrees(rot.y)
    bl_yaw = math.degrees(rot.z)
    bbs_pitch = 90.0 - bl_pitch
    bbs_roll = bl_roll
    bbs_yaw = -bl_yaw
    # FOV: vertical, grad
    cam_data = cam.data
    if cam_data.sensor_fit == 'VERTICAL':
        v_fov = math.degrees(cam_data.angle_y)
    else:
        render = bpy.context.scene.render
        aspect = (render.resolution_x / render.resolution_y) if render.resolution_y else 1.0
        v_fov = math.degrees(2 * math.atan(math.tan(cam_data.angle_x / 2) / aspect))
    return {
        "x": round(bbs_x, 6), "y": round(bbs_y, 6), "z": round(bbs_z, 6),
        "pitch": round(bbs_pitch, 6), "yaw": round(bbs_yaw, 6), "roll": round(bbs_roll, 6),
        "fov": round(v_fov, 6),
    }


# ---- mapping BBS -> Blender (invers, für eingehende BBS-kamera) ----
def _bbs_to_blender_cam(cam, f):
    state._applying_remote = True
    try:
        bl_x = f["x"]
        bl_y = -f["z"]
        bl_z = f["y"]
        bl_pitch = math.radians(90.0 - f["pitch"])
        bl_roll = math.radians(f["roll"])
        bl_yaw = math.radians(-f["yaw"])
        cam.location = (bl_x, bl_y, bl_z)
        cam.rotation_mode = 'XYZ'
        cam.rotation_euler = Euler((bl_pitch, bl_roll, bl_yaw), 'XYZ')
        if "fov" in f and cam.data is not None:
            cam.data.sensor_fit = 'VERTICAL'
            cam.data.angle_y = math.radians(f["fov"])
    finally:
        state._applying_remote = False


# ---- socket-io ----
def _connect():
    """NICHT-blockierend: der eigentliche connect läuft in einem daemon-thread.

    1-FPS-BUG (user-report 2026-07-17): create_connection(timeout=2.0) lief hier direkt im
    main-thread-tick. Steht kein BBS (game zu), refused Windows den connect nicht immer
    sofort (dual-stack/firewall) — jeder sekündliche versuch blockte die UI bis zu 2s →
    Blender fiel auf ~1 fps, solange die bridge an war. Sockets dürfen off-main (kein bpy
    in _connect_blocking!), nur der tick-einstieg bleibt main-thread."""
    t = state._connect_thread
    if t is not None and t.is_alive():
        return None   # ein versuch läuft schon — nicht stapeln
    t = threading.Thread(target=_connect_blocking,
                         args=(_connection_generation, (state.BBS_HOST, state.BBS_PORT)),
                         daemon=True, name="bbs-bridge-connect")
    state._connect_thread = t
    t.start()
    return None


def _connect_blocking(generation=None, endpoint=None):
    """Connect without bpy calls, publishing only within the still-current lifetime.

    Blocking network work never holds the lifecycle lock. HELLO is sent before the
    socket is exposed to the main thread, and cancelled sockets are always closed.
    Every fresh connection forgets ownership rather than reasserting old camera state.
    """
    with _connection_lock:
        generation = _connection_generation if generation is None else generation
        endpoint = (state.BBS_HOST, state.BBS_PORT) if endpoint is None else endpoint
        if generation != _connection_generation:
            return None
        _clear_connection_state()
    s = None
    try:
        s = socket.create_connection(endpoint, timeout=2.0)
        with _connection_lock:
            if (generation != _connection_generation
                    or endpoint != (state.BBS_HOST, state.BBS_PORT)):
                s.close()
                return None
        hello = {"type": "HELLO", "origin": "blender", "seq": _next_seq(), "fields": {
            "app": "blender",
            "version": state.ADDON_VERSION,
            "protocol": state.PROTOCOL_VERSION,
            "capabilities": list(state.LOCAL_CAPABILITIES),
        }}
        s.sendall((json.dumps(hello) + "\x00").encode("utf-8"))
        s.setblocking(False)
        with _connection_lock:
            if (generation != _connection_generation
                    or endpoint != (state.BBS_HOST, state.BBS_PORT)):
                s.close()
                return None
            state._sock = s
            state._connected_endpoint = endpoint
            state._connected = True
            state._status = "connected"
        print("[bbs-bridge] connected to BBS :%d" % endpoint[1])
    except OSError:
        if s is not None:
            s.close()
        with _connection_lock:
            if (generation == _connection_generation
                    and endpoint == (state.BBS_HOST, state.BBS_PORT)):
                state._sock = None
                state._connected = False
                state._status = "no BBS server (:%d)" % endpoint[1]
    return None


def _next_seq():
    state._seq_out += 1
    return state._seq_out


def _send(msg):
    if state._sock is None:
        return
    try:
        state._sock.sendall((json.dumps(msg) + "\x00").encode("utf-8"))
    except OSError:
        state._connected = False
        state._status = "send failed"
        try:
            state._sock.close()
        except OSError:
            pass
        state._sock = None


def _poll_recv():
    """non-blocking read, gibt liste vollständiger nachrichten zurück."""
    if state._sock is None:
        return []
    msgs = []
    try:
        while True:
            chunk = state._sock.recv(4096)
            if not chunk:
                state._connected = False
                state._status = "closed by BBS"
                state._sock = None
                break
            state._recv_buf += chunk
    except BlockingIOError:
        pass
    except OSError:
        state._connected = False
        state._status = "recv error"
        state._sock = None
    while b"\x00" in state._recv_buf:
        raw, state._recv_buf = state._recv_buf.split(b"\x00", 1)
        if raw.strip():
            try:
                msgs.append(json.loads(raw.decode("utf-8")))
            except json.JSONDecodeError:
                pass
    return _coalesce_recording_preview(msgs)


def _coalesce_recording_preview(messages):
    """Keep only the newest disposable recording frame between reliable envelopes.

    A heavy model import can pause Blender while BBS continues producing 20-tps preview samples.
    Applying that stale backlog delays REPLAY_RECORD_CONTROL_RESULT/STOP and cannot improve the
    preview. Flush the newest pending frame immediately before each reliable message so ordering
    remains exact; the authoritative post-recording ACTORS_EXPORT_RESULT is untouched.
    """
    out = []
    pending = None
    run_count = 0

    for message in messages:
        if message.get("type") == "REPLAY_RECORD_FRAME":
            pending = message
            run_count += 1
            continue
        if pending is not None:
            out.append(pending)
            pending = None
        out.append(message)

    if pending is not None:
        out.append(pending)

    state._record_frames_received += run_count
    state._record_frames_coalesced += max(0, run_count - sum(
        1 for message in out if message.get("type") == "REPLAY_RECORD_FRAME"))
    return out
