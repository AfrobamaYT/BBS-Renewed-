"""Debounced authoring over the existing protected durable sender.

Blender's msgbus does not report viewport transforms. Inspect native curve changes
on the existing main-thread timer, after modal operators finish (Blender 4.2+).
https://docs.blender.org/api/main/bpy.msgbus.html
https://developer.blender.org/docs/release_notes/4.2/python_api/#modal-operators
"""
import time
import bpy

from . import actor_document as document, actor_export, actor_outbox as outbox
from . import recording_live, shot_freeze, state, actor_native_undo
from .actor_outbox_store import encode

PROP = 'bbsbridge_live_authoring'
_status = 'off'
_context = None
_candidate = None
_attempted = None
_stable_since = 0.0
_next_scan = 0.0


def reset(_scene=None, _unused_context=None):
    global _context, _candidate, _attempted, _next_scan, _status
    _context = _candidate = _attempted = None
    _next_scan = 0.0
    _status = 'waiting for completed edits'


def busy():
    wm = bpy.context.window_manager
    if wm.is_interface_locked or recording_live._session or recording_live._control_pending:
        return True
    for window in wm.windows:
        if not hasattr(window, 'modal_operators'):
            raise RuntimeError('Live editing requires Blender 4.2 or newer')
        if window.modal_operators or window.screen.is_animation_playing:
            return True
    return bool(state._playhead_bbs_playing or shot_freeze.ist_eingefroren())


def pump():
    global _context, _candidate, _attempted, _stable_since, _next_scan, _status
    scene = bpy.context.scene
    if actor_native_undo._error:
        _status = actor_native_undo._error
        return
    if not getattr(scene, PROP, False):
        _status = 'off'
        return
    now = time.monotonic()
    if now < _next_scan:
        return
    _next_scan = now + 0.1
    try:
        info = document.binding()
        if not info:
            raise RuntimeError('Save and enable durable actor editing first')
        context = (scene.as_pointer(), info['document'], int(scene.get(document.ACK, 0)))
        if context != _context:
            _context, _candidate, _attempted = context, None, None
        if busy() or document._restoring or outbox._opening:
            _candidate = None
            _status = 'waiting for editing or playback to finish'
            return
        if state.film_switch_detected or outbox._paused:
            _candidate = None
            _status = 'paused: ' + ('BBS film changed' if state.film_switch_detected else outbox._status)
            return
        document.activate()
        if any(record.get('kind') == 'compensation' or record['payload'].get('resync')
               for record in document.records()):
            _candidate = None
            _status = 'waiting for shared undo/redo or resync'
            return
        _roots, bases = outbox.projected_bases()
        fields, _skipped, _degraded, scale = actor_export.build_conditional_payload(bases=bases)
        if not fields['replays']:
            _candidate = _attempted = None
            _status = 'no changed protected curves or controls'
            return
        signature = encode([scale, fields['replays']])
        if signature != _candidate:
            _candidate, _stable_since = signature, now
            _status = 'editing'
            return
        if signature == _attempted or now - _stable_since < 0.25:
            return
        # queue() projects pending operations too, so successive offline edits are
        # persisted in order and an already queued value cannot be sent twice.
        outbox.queue()
        _attempted = signature
        _status = outbox._status
    except Exception as exc:
        _status = 'paused: ' + str(exc)


class BBSBRIDGE_OT_live_actor_editing(bpy.types.Operator):
    bl_idname = 'bbsbridge.live_actor_editing'
    bl_label = 'Toggle Live Actor Editing'
    bl_description = ('Automatically queue changed actor curves, rig controls and visibility. '
                      'Root movement needs keyframes; enable durable editing first')

    @classmethod
    def poll(cls, context):
        if not outbox.bound():
            cls.poll_message_set('Save and enable durable actor editing first')
            return False
        return True

    def execute(self, context):
        setattr(context.scene, PROP, not getattr(context.scene, PROP))
        if getattr(context.scene, PROP):
            # Imported actors were created outside Blender's native undo stack.
            # Give the first edit a real pre-edit snapshot, not the empty startup scene.
            bpy.ops.ed.undo_push(message='Live Actor Editing baseline')
        self.report({'INFO'}, 'Live actor editing ' + ('on' if getattr(context.scene, PROP) else 'off'))
        return {'FINISHED'}


def register():
    setattr(bpy.types.Scene, PROP, bpy.props.BoolProperty(
        name='Live Actor Editing', default=False, update=reset,
        description='Send supported actor curves and controls after editing pauses; root movement needs keys'))
    reset()
    actor_native_undo.register()


def unregister():
    actor_native_undo.unregister()
    if hasattr(bpy.types.Scene, PROP):
        delattr(bpy.types.Scene, PROP)
    reset()


classes = (BBSBRIDGE_OT_live_actor_editing,)
