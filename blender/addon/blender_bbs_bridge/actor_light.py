"""PHASE V2 — LightForm → Blender POINT-lampe.

Ein BBS `LightForm`-actor (form-id `bbs:light`) ist reines licht, kein mesh — im payload trägt er
`properties.light = {"enabled":bool, "level":int 0..15}` (java `LightFormExporter`). Diese datei gibt
dem actor-root eine EIGENE POINT-lampe als kind-objekt (`BBS_ActorLight_<uuid8>`), deren energie aus
dem MC-level abgeleitet wird — mit DERSELBEN kurve die `world_light._build_point_lights` für welt-
leuchtblöcke nutzt (single source der level→energie-abbildung, kein zweiter faktor der driftet):

    energy = (level / 15) ** 2 * 60.0      # quadratisch: MC-licht linear, Blender quadratisch

WARUM ein kind-objekt statt den root SELBST zur lampe zu machen: der actor-root wird in actors.py
(`_make_actor_root`) gebaut — ein hotspot den andere agents/der leader anfassen. Eine additive
lampe als kind greift NICHT in den root-branch ein, trägt die animierten x/y/z-kanäle des roots
automatisch mit (parenting), und ist rückstandslos entfernbar. Der root bleibt das empty das
`_make_actor_root` für einen mesh-losen LightForm sowieso liefert.

IDEMPOTENT: erneuter aufruf findet die bestehende lampe wieder (name-suffix `<uuid8>`) und setzt
nur energie/sichtbarkeit neu — kein stapeln. `enabled=false` → energie 0 + hide_render.

Wirft nie (der aufrufer in actors.py wrappt zwar in try/except, aber die funktion selbst degradiert
still: kein light-payload / falscher form-typ = no-op).
"""
import bpy

# node/objekt-namen (stabil, zum wiederfinden + cleanup)
_LIGHT_PREFIX = "BBS_ActorLight_"
# level→energie-faktor: EXAKT world_light._build_point_lights (single source, dort dokumentiert:
# quadratisch weil MC-licht linear pro block fällt, Blender quadratisch). NICHT ohne side-by-side-
# gate ändern — falls LightForm-actors zu dunkel/hell wirken ist DAS der hebel.
_ENERGY_FACTOR = 60.0
# lichtfarbe eines LightForm-actors: warmes weiss wie DEFAULT_POINT_COLOR in world_light (fackel-
# artig; LightForm trägt in BBS keine farbe, nur level → keine geratene farbe, dieselbe wie welt-licht).
_LIGHT_COLOR = (1.0, 0.75, 0.45)


def _uuid8(replay):
    u = str(replay.get("uuid") or replay.get("id") or "actor")
    return u.replace("-", "")[:8] or "actor"


def _light_data(replay):
    """Das `light`-dict aus dem payload holen (`properties.light`), oder None wenn n/a."""
    if not isinstance(replay, dict):
        return None
    props = replay.get("properties")
    if not isinstance(props, dict):
        return None
    light = props.get("light")
    return light if isinstance(light, dict) else None


def has_light(replay):
    """Trägt der replay einen LightForm-light-payload?"""
    return _light_data(replay) is not None


def _find_actor_light(obj, uuid8):
    """Die bestehende actor-lampe dieses roots wiederfinden (kind mit passendem namen)."""
    target = _LIGHT_PREFIX + uuid8
    for child in obj.children:
        if child.type == 'LIGHT' and child.name == target:
            return child
    return None


def apply_light(obj, replay):
    """Dem LightForm-actor-root eine POINT-lampe geben (kind-objekt), energie aus dem MC-level.

    obj = actor-root (empty bei LightForm — kein mesh). Idempotent, wirft nie. no-op wenn der
    replay keinen light-payload trägt (nicht-LightForm actor)."""
    light = _light_data(replay)
    if light is None:
        return False

    uuid8 = _uuid8(replay)
    lamp_obj = _find_actor_light(obj, uuid8)

    if lamp_obj is None:
        # datablock + objekt neu, an DIESELBE collection wie der root hängen, dann parenten.
        data = bpy.data.lights.new(_LIGHT_PREFIX + uuid8, 'POINT')
        lamp_obj = bpy.data.objects.new(_LIGHT_PREFIX + uuid8, data)
        # in die/eine collection des roots linken (sonst rendert das objekt nicht)
        colls = list(obj.users_collection)
        target_coll = colls[0] if colls else bpy.context.scene.collection
        target_coll.objects.link(lamp_obj)
        # parent an den root → x/y/z-kanäle des roots ziehen die lampe mit; lokal auf dem root sitzen
        lamp_obj.parent = obj
        lamp_obj.matrix_parent_inverse = obj.matrix_world.inverted()
        lamp_obj.location = (0.0, 0.0, 0.0)

    lamp_obj["bbs_light_owner"] = str(replay.get("uuid") or replay.get("id") or "actor")
    data = lamp_obj.data
    data.type = 'POINT'
    data.color = _LIGHT_COLOR
    data.shadow_soft_size = 0.25

    try:
        level = int(light.get("level", 15))
    except (TypeError, ValueError):
        level = 15
    level = max(0, min(15, level))
    enabled = bool(light.get("enabled", True))

    if enabled:
        data.energy = (level / 15.0) ** 2 * _ENERGY_FACTOR
        lamp_obj.hide_render = False
        lamp_obj.hide_viewport = False
    else:
        # aus: energie 0 UND aus render/viewport (kein rest-licht)
        data.energy = 0.0
        lamp_obj.hide_render = True
        lamp_obj.hide_viewport = True

    return True
