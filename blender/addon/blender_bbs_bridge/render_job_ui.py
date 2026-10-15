"""Controls for persistent local render jobs; never stored in Blender's undo document."""
import bpy
from . import render_jobs as jobs, render_job_state as store

_cached = {}


def refresh():
    global _cached
    try:
        job_id = jobs.latest()
        if not job_id:
            return None
        _cached = jobs.status(job_id)
    except Exception as error:
        _cached = {'phase': 'unavailable', 'error': str(error)}
        return None
    for window in bpy.context.window_manager.windows:
        for area in window.screen.areas:
            if area.type == 'VIEW_3D':
                area.tag_redraw()
    return 2.0 if _cached.get('active') else None


def follow():
    if not bpy.app.timers.is_registered(refresh):
        bpy.app.timers.register(refresh, first_interval=.2)


def shutdown():
    if bpy.app.timers.is_registered(refresh):
        bpy.app.timers.unregister(refresh)


def draw(layout):
    try:
        job_id = jobs.latest()
        if not job_id:
            return
        disk = store.read(jobs.folder(job_id) / 'status.json')
        value = _cached if _cached.get('id') == job_id and not _cached.get('active') else disk
        layout.label(text='Job %s: %s (%s/%s)' % (job_id[:8], value.get('phase'),
                     value.get('completed', 0), value.get('total', '?')))
        if value.get('error'):
            row = layout.row()
            row.alert = True
            row.label(text=str(value['error'])[:100])
        row = layout.row(align=True)
        for action, label in (('STATUS', 'Refresh'), ('CANCEL', 'Cancel'), ('RESUME', 'Resume')):
            button = row.operator('bbsbridge.render_job_control', text=label)
            button.action, button.job_id = action, job_id
    except Exception as error:
        layout.label(text='Render job: ' + str(error)[:100], icon='ERROR')


class BBSBRIDGE_OT_render_job_control(bpy.types.Operator):
    bl_idname = 'bbsbridge.render_job_control'
    bl_label = 'Render Job'
    bl_description = 'Inspect, cancel or resume the original render snapshot, not the current scene'
    action: bpy.props.EnumProperty(items=[('STATUS', 'Refresh', ''),
        ('CANCEL', 'Cancel', 'Finish the current frame or stop after 30 seconds'),
        ('RESUME', 'Resume', 'Reuse verified frames from the unchanged snapshot')])
    job_id: bpy.props.StringProperty(name='Job ID')

    def execute(self, context):
        try:
            if self.action == 'CANCEL':
                value = jobs.cancel(self.job_id)
                message = 'Cancellation requested; the current frame may finish'
            elif self.action == 'RESUME':
                value = jobs.launch(self.job_id)
                message = 'Original snapshot: ' + value['phase']
            else:
                value = jobs.status(self.job_id)
                message = value['phase'] + ': ' + value['output']
            self.report({'INFO'}, message)
            follow()
            return {'FINISHED'}
        except Exception as error:
            self.report({'ERROR'}, str(error))
            return {'CANCELLED'}


classes = (BBSBRIDGE_OT_render_job_control,)
