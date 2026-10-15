package com.blenderbridge.actors;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LightForm;

/**
 * PHASE V2 — LightForm. Serialises a {@link LightForm} actor's light settings for the Blender bridge.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free, {@code PaintExporter}/{@code TextureExporter}-muster)</h2>
 * <p>{@link ActorPropertiesExporter} is already loaded in the running JVM — a new field/method there
 * would need a boot (the {@code canAddMethod} trap, package class-doc). So the light channel lives in
 * THIS new lazy class; the delegation is a single body-call in
 * {@code ActorPropertiesExporter.serialize} (an {@code out}-merge of {@link #serializeLight(Replay)}) —
 * that one line is the leader's job (HOTSPOT-regel).</p>
 *
 * <h2>NOT A KEYFRAME CHANNEL — a FORM-level static value</h2>
 * <p>Unlike color/glow/paint (which are animated {@code replay.properties} keyframe channels),
 * LightForm's data lives as {@code Value*} fields ON THE FORM object ({@code replay.form.get()}):
 * {@code LightForm.enabled} (ValueBoolean, default true), {@code LightForm.level} (ValueInt 0..15,
 * default 15). These are static settings, not curves — so the wire shape is a flat object of scalar
 * values, NOT sub-curve arrays. The Blender side (actor_light.py) turns them into a POINT lamp
 * (energy from level, reusing the world_light level→energy mapping).</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * {
 *   "light": {
 *     "enabled": true,
 *     "level":   15
 *   }
 * }
 * </pre>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY, like {@link ActorPropertiesExporter} that calls it — reads live film state.
 * Never throws: a non-LightForm replay returns an empty result (byte-identical old payload).</p>
 */
public final class LightFormExporter
{
    /** Top-level key of the light object inside {@code properties} (analogous to "color"/"glow"). */
    public static final String LIGHT = "light";
    /** Whether the light is on (LightForm.enabled). */
    public static final String ENABLED = "enabled";
    /** MC light level 0..15 (LightForm.level) — Blender maps it to lamp energy. */
    public static final String LEVEL = "level";

    private LightFormExporter() {}

    /**
     * Serialise the LightForm settings of a replay.
     *
     * @return an object carrying {@code "light"} when the replay's form is a {@link LightForm}; empty
     *         (size 0) otherwise — the caller only merges a non-empty result, so a non-light replay
     *         stays byte-identical. Never null, never throws.
     */
    public static JsonObject serializeLight(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.form == null)
        {
            return out;
        }

        Form form = replay.form.get();

        if (!(form instanceof LightForm light))
        {
            return out;
        }

        JsonObject data = new JsonObject();
        data.addProperty(ENABLED, light.enabled.get());
        data.addProperty(LEVEL, light.level.get());
        out.add(LIGHT, data);

        return out;
    }
}
