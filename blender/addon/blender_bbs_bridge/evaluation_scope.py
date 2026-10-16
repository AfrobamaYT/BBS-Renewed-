"""Read-only evaluation scopes must not acquire an editor's playhead ownership."""
from contextlib import contextmanager
import bpy
from . import state

_depth = 0


def active():
    return _depth > 0 or bpy.app.is_job_running('RENDER')


@contextmanager
def quiet():
    """Nest safely around sampling, baking and renders, including exception cleanup."""
    global _depth
    _depth += 1
    try:
        yield
    finally:
        _depth -= 1
        # Blender may deliver a frame callback after a synchronous evaluation returns.
        scene = getattr(bpy.context, 'scene', None)
        if scene is not None:
            state._playhead_last_remote_frame = scene.frame_current
