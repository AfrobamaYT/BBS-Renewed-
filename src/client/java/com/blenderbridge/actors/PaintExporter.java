package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.blenderbridge.actors.compat.MaterialCompat;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * PHASE P — Paint. Serialises the whole-form {@code paint} FormProperties channel of a Replay for
 * the Blender bridge.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free, {@code RegionClamp}/{@link TextureExporter}-muster)</h2>
 * <p>The material-sync work is split across parallel agents that ALL want to add a channel to the
 * ACTORS payload. {@link ActorPropertiesExporter} is already loaded in the running JVM (it exported
 * on the first bridge call) — a new field/method there would need a boot (the {@code canAddMethod}
 * trap, package class-doc). So the paint channel lives in THIS new lazy class, exactly like
 * {@link TextureExporter} owns the texture/PBR channels and {@link StringChannels} owns the item
 * channels. The delegation is a single body call in {@code ActorPropertiesExporter.serialize}
 * (an {@code out}-merge of {@link #serializePaint(Replay)}) — that one line is the leader's job.</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * {
 *   "paint": {
 *     "r":            [{"tick":0.0,"value":1.0,"interp":"linear","lx":5.0,"ly":0.0,"rx":5.0,"ry":0.0}, ...],
 *     "g":            [...],
 *     "b":            [...],
 *     "intensity":    [...],
 *     "shaderShadow": [...]
 *   }
 * }
 * </pre>
 *
 * <h2>PaintSettings (source of truth: PaintSettings.java:19-24)</h2>
 * <p>{@code public float r/g/b} (default 1), {@code public float intensity} (UNBOUNDED, may be
 * NEGATIVE — negative darkens the surface in BBS), {@code public boolean sync}, {@code public float
 * shaderShadow} (default {@code SHADER_SHADOW_DEFAULT}=1). Whole-form; per-bone paint (the
 * {@code paintColor} inside {@code pose:<bone>} PoseTransform) is a later phase.</p>
 *
 * <p>The value reises as FIVE separate scalar keyframe curves (r,g,b,intensity,shaderShadow) — a
 * mirror of {@code ActorPropertiesExporter.serializeGlowChannel}, value-cast to {@link PaintSettings}
 * — because Blender animates scalar F-curves and a packed struct would not be curve-interpolable on
 * the addon side. Blender maps r/g/b + intensity to a second overlay-tint Mix layer on the base
 * colour (factor = |intensity|); {@code shaderShadow} travels as a documented-limit flag (no clean
 * Blender AO mapping — the addon flags it, does not fake it, Regel 0). Tick/interp/handles are
 * byte-identical to the color/glow serialisation so the addon shares one keyframe parser.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY, like {@link ActorPropertiesExporter} that calls it — it reads live film state.
 * Never throws: an unexpected value type on a single keyframe is skipped, the export is never
 * aborted (an empty result means "no paint channel", byte-identical old payload).</p>
 */
public final class PaintExporter
{
    /** Top-level key of the paint object inside {@code properties} (analogous to "color"/"glow"). */
    public static final String PAINT = "paint";
    /** The FormProperties channel id of the whole-form paint settings (FormProperties: "paint"). */
    private static final String PAINT_CHANNEL = "paint";
    /** shaderShadow sub-curve key — a documented-limit flag (no 1:1 Blender AO, PaintSettings.java:24). */
    public static final String PROP_SHADER_SHADOW = "shaderShadow";

    private PaintExporter() {}

    /**
     * Serialise the whole-form paint channel of a replay.
     *
     * @return an object carrying {@code "paint"} when the replay has a non-empty paint channel; empty
     *         (size 0) otherwise — the caller only merges a non-empty result, so a replay without the
     *         paint channel stays byte-identical. Never null, never throws.
     */
    public static JsonObject serializePaint(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        KeyframeChannel paintChannel = replay.properties.properties.get(PAINT_CHANNEL);

        if (paintChannel != null && !paintChannel.isEmpty())
        {
            out.add(PAINT, serializePaintChannel(paintChannel));
        }

        return out;
    }

    /** A PaintSettings-KeyframeChannel -> {"r":[...],"g":[...],"b":[...],"intensity":[...],"shaderShadow":[...]}
     *  (5 scalar curves). Mirror of {@code ActorPropertiesExporter.serializeGlowChannel}, value-cast
     *  on {@link PaintSettings}, with intensity AND the shaderShadow flag as their own sub-curves. */
    private static JsonObject serializePaintChannel(KeyframeChannel channel)
    {
        JsonArray r = new JsonArray();
        JsonArray g = new JsonArray();
        JsonArray b = new JsonArray();
        JsonArray intensity = new JsonArray();
        JsonArray shaderShadow = new JsonArray();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            /* MaterialCompat instead of `instanceof PaintSettings`: absent in the BBS-CML jar this
             * fork compiles against (measured) -> no keyframe matches -> channel emits nothing. */
            Object paint = keyframe == null ? null : keyframe.getValue();

            if (!MaterialCompat.isPaint(paint))
            {
                continue;
            }

            r.add(component(keyframe, MaterialCompat.readFloat(paint, "r")));
            g.add(component(keyframe, MaterialCompat.readFloat(paint, "g")));
            b.add(component(keyframe, MaterialCompat.readFloat(paint, "b")));
            intensity.add(component(keyframe, MaterialCompat.readFloat(paint, "intensity")));
            shaderShadow.add(component(keyframe, MaterialCompat.readFloat(paint, "shaderShadow")));
        }

        JsonObject out = new JsonObject();
        out.add(ActorProtocol.PROP_R, r);
        out.add(ActorProtocol.PROP_G, g);
        out.add(ActorProtocol.PROP_B, b);
        out.add(ActorProtocol.PROP_INTENSITY, intensity);
        out.add(PROP_SHADER_SHADOW, shaderShadow);
        return out;
    }

    /** One keyframe entry (value + tick/interp/handles) — byte-identical to
     *  {@code ActorPropertiesExporter.component} (shared keyframe parser). */
    private static JsonObject component(Keyframe keyframe, float value)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty(ActorProtocol.KF_TICK, keyframe.getTick());
        entry.addProperty(ActorProtocol.KF_VALUE, value);
        entry.addProperty(ActorProtocol.KF_INTERP, interpKey(keyframe));
        entry.addProperty(ActorProtocol.KF_LX, keyframe.lx);
        entry.addProperty(ActorProtocol.KF_LY, keyframe.ly);
        entry.addProperty(ActorProtocol.KF_RX, keyframe.rx);
        entry.addProperty(ActorProtocol.KF_RY, keyframe.ry);
        return entry;
    }

    /** interp-key — nullable-IInterp guard, identical to ActorExporter/ActorPropertiesExporter. */
    private static String interpKey(Keyframe keyframe)
    {
        try
        {
            return keyframe.getInterpolation().getKey();
        }
        catch (Exception e)
        {
            return "linear";
        }
    }
}
