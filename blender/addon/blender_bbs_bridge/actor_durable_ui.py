"""Explicit saved-document opt-in for restart-safe actor editing."""
import bpy
from . import actor_outbox as outbox


class BBSBRIDGE_OT_enable_durable(bpy.types.Operator):
    bl_idname = 'bbsbridge.enable_durable'
    bl_label = 'Enable Durable Actor Editing'
    bl_description = 'Save this .blend and bind queued actor edits to the current BBS project'

    def invoke(self, context, event):
        return context.window_manager.invoke_confirm(
            self, event, title='Save and enable durable actor editing?',
            message='Saves this .blend and the BBS film identity. Queued root/item/bone edits are '
                    'retained on disk and resumed after reconnect or restart.',
            confirm_text='Save and enable', icon='WARNING')

    def execute(self, context):
        if not outbox.enable():
            self.report({'ERROR'}, outbox._status)
            return {'CANCELLED'}
        return {'FINISHED'}


class BBSBRIDGE_OT_retry_durable(bpy.types.Operator):
    bl_idname = 'bbsbridge.retry_durable'
    bl_label = 'Resume Durable Queue'
    bl_description = 'Recheck the project and pending receipts; never discard uncertain operations'

    def execute(self, context):
        outbox.retry()
        outbox.pump()
        self.report({'INFO'}, outbox._status)
        return {'FINISHED'}


classes = (BBSBRIDGE_OT_enable_durable, BBSBRIDGE_OT_retry_durable)


class BBSBRIDGE_OT_resync_keep_local(bpy.types.Operator):
    bl_idname = 'bbsbridge.resync_keep_local'
    bl_label = 'Resync — Keep Local Edits'
    bl_description = 'Fence earlier queued writes, adopt the current BBS basis, keep local protected edits'

    def invoke(self, context, event):
        return context.window_manager.invoke_confirm(
            self, event, title='Reconcile this project?',
            message='Older queued writes are fenced, not repeated. The current BBS state becomes '
                    'the new basis; your locally changed root, item and bone curves are kept.',
            confirm_text='Reconcile and keep local', icon='WARNING')

    def execute(self, context):
        from . import actor_resync
        if not actor_resync.queue():
            self.report({'ERROR'}, outbox._status)
            return {'CANCELLED'}
        return {'FINISHED'}


classes += (BBSBRIDGE_OT_resync_keep_local,)


class BBSBRIDGE_OT_shared_undo(bpy.types.Operator):
    bl_idname = 'bbsbridge.shared_undo'
    bl_label = 'Shared Undo'
    bl_description = ('Undo the latest durably confirmed protected actor edit in both Blender and BBS. '
                      'Newer edits or conflicts are never overwritten')

    @classmethod
    def poll(cls, context):
        return outbox.bound()

    def execute(self, context):
        from . import actor_shared_undo
        if not actor_shared_undo.queue('undo'):
            self.report({'ERROR'}, actor_shared_undo._status)
            return {'CANCELLED'}
        self.report({'INFO'}, actor_shared_undo._status)
        return {'FINISHED'}


class BBSBRIDGE_OT_shared_redo(bpy.types.Operator):
    bl_idname = 'bbsbridge.shared_redo'
    bl_label = 'Shared Redo'
    bl_description = ('Redo the latest shared-undo actor edit in both Blender and BBS using the '
                      'same durable conflict guards')

    @classmethod
    def poll(cls, context):
        return outbox.bound()

    def execute(self, context):
        from . import actor_shared_undo
        if not actor_shared_undo.queue('redo'):
            self.report({'ERROR'}, actor_shared_undo._status)
            return {'CANCELLED'}
        self.report({'INFO'}, actor_shared_undo._status)
        return {'FINISHED'}


classes += (BBSBRIDGE_OT_shared_undo, BBSBRIDGE_OT_shared_redo)
