"""Promote the temporary no-form actor without losing its transform or sync basis."""


def transfer(old, new):
    if new.type != 'ARMATURE':
        import bpy
        bpy.data.objects.remove(new, do_unlink=True)
        raise RuntimeError('Actor model could not replace its placeholder')
    animation = old.animation_data
    if old.constraints or (animation and (animation.drivers or animation.nla_tracks)):
        from . import actors
        actors._remove_actor_root(new)
        raise RuntimeError('Placeholder has local constraints, drivers or NLA; promotion needs explicit resolution')
    for key in old.keys():
        if key not in ('bbs_form', 'bbs_form_type', 'bbs_id') and not key.startswith('bbs_native_'):
            new[key] = old[key]
    new.matrix_world = old.matrix_world.copy()
    for child in [new] + list(new.children_recursive):
        child.hide_viewport = old.hide_viewport
        child.hide_render = old.hide_render
    if animation and animation.action:
        destination = new.animation_data_create()
        destination.action = animation.action
        if animation.action_slot is not None:
            destination.action_slot = animation.action_slot
    for child in list(old.children):
        world = child.matrix_world.copy()
        child.parent = new
        child.matrix_world = world
