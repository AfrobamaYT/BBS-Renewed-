// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase T, texture-swap/crossfade + PBR).
package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.List;

/**
 * TextureExporter — Phase T of the material-sync masterplan: serialise the form-level TEXTURE-SWAP /
 * CROSSFADE channel and the PBR normal/specular intensities of a replay so Blender can build the
 * image-swap + crossfade nodes.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free, {@code RegionClamp}-muster)</h2>
 * <p>{@link ActorExporter} is already loaded in the running JVM (it exported on the first bridge
 * call), so it can only take BODY edits via hotswap — no new field, no new method signature (the
 * {@code canAddMethod}/{@code <clinit>} traps, see the package class-doc). {@link
 * ActorPropertiesExporter} owns the color/glow/paint channels; the texture channels are their own
 * concept (Link-valued, not Color/GlowSettings), so they live in THIS new lazy class, exactly like
 * {@link StringChannels} owns the item channels. The delegation is a single body call in
 * {@code ActorPropertiesExporter.serialize}: {@code out}-merge of {@link #serializeTexture(Replay)}.</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * {
 *   "texture": {
 *     "keys":  [[0.0, "assets:models/player/steve/steve.png", "linear"],
 *               [20.0, "assets:models/player/alex/alex.png",  "linear"]],
 *     "blend": [{"tick":0.0,"value":0.0,"interp":"linear",...},
 *               {"tick":20.0,"value":1.0,"interp":"linear",...}]
 *   },
 *   "pbrNormal":   [{"tick":..,"value":..,...}, ...],
 *   "pbrSpecular": [{"tick":..,"value":..,...}, ...]
 * }
 * </pre>
 *
 * <h2>HOW A FORM-LEVEL TEXTURE-SWAP IS MODELLED (source of truth: FormProperties.applyTextureBlend)</h2>
 * <p>The form {@code texture} channel is a {@code KeyframeChannel<Link>}: each keyframe holds ONE
 * texture {@link Link}. There is no stored scalar "blend" — BBS derives the crossfade at playback
 * from the segment position between two adjacent Link keyframes (see
 * {@code FormProperties.applyTextureBlend}: {@code from = segment.a}, {@code to = segment.b}, blend =
 * the segment's interpolated 0..1). So we send BOTH: (1) {@code keys} — the ordered texture Links as
 * strings ({@link Link#toString()} = {@code "source:path"}, the addon resolves the PNG the same way
 * {@code bobj_import._resolve_bbs_link} does), and (2) {@code blend} — a scalar 0..1 sub-curve
 * SYNTHESISED so the addon has an explicit crossfade factor: at each keyframe {@code i>0} the value is
 * 1.0 (fully on that texture) with the SAME interp/handles as the source Link keyframe, so a
 * {@code hermite}/{@code linear} texture segment fades exactly the way BBS interpolates it. The
 * addon reads {@code keys} pairwise (i-1 -> i) and drives its Mix factor from {@code blend}.</p>
 *
 * <h2>PBR INTENSITIES</h2>
 * <p>{@code ModelForm.pbrNormalIntensity} / {@code pbrSpecularIntensity} are plain {@code ValueFloat}
 * (0..4). When animated they land in {@code replay.properties.properties} under
 * {@code "pbr_normal_intensity"} / {@code "pbr_specular_intensity"} as {@code KeyframeChannel<Float>}
 * — serialised via {@link #scalarChannel} exactly like the numeric transform channels.</p>
 *
 * <h2>DOCUMENTED LIMIT (Regel 0 — flag, do not fake)</h2>
 * <p>The texture-swap transports the ASSET PATH only. Whether that PNG is resolvable on the Blender
 * side (a bbs-asset link vs. a vanilla-jar texture) is the addon's job — a missing image degrades to
 * a node without an image + a log, it is not the exporter's concern and never blocks the export.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY, like {@link ActorPropertiesExporter} / {@link StringChannels} that call it —
 * it reads live film state. Never throws: an unexpected value type on a single keyframe is skipped,
 * the export is never aborted.</p>
 */
public final class TextureExporter
{
    /** Top-level key of the texture-swap object inside {@code properties}. */
    public static final String TEXTURE = "texture";
    /** Ordered texture-Link keyframes: {@code [[tick, "source:path", interp], ...]}. */
    public static final String TEXTURE_KEYS = "keys";
    /** Synthesised 0..1 crossfade factor sub-curve (per texture segment). */
    public static final String TEXTURE_BLEND = "blend";
    /** PBR normal-map intensity scalar sub-curve (0..4). */
    public static final String PBR_NORMAL = "pbrNormal";
    /** PBR specular intensity scalar sub-curve (0..4). */
    public static final String PBR_SPECULAR = "pbrSpecular";

    /** The property-channel ids of the two PBR intensities (ModelForm.java:33-34, ValueFloat names). */
    private static final String PBR_NORMAL_CHANNEL = "pbr_normal_intensity";
    private static final String PBR_SPECULAR_CHANNEL = "pbr_specular_intensity";
    /** The form-level texture-swap channel id (ModelForm.texture = ValueLink("texture", ...)). */
    private static final String TEXTURE_CHANNEL = "texture";

    private TextureExporter() {}

    /**
     * Serialise the texture-swap + PBR-intensity channels of a replay.
     *
     * @return an object carrying any of {@code "texture"} / {@code "pbrNormal"} / {@code "pbrSpecular"}
     *         that are present and non-empty; empty (size 0) when the replay has none of them — the
     *         caller only merges a non-empty result, so a replay without these channels stays
     *         byte-identical. Never null, never throws.
     */
    public static JsonObject serializeTexture(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        /* --- form-level texture-swap (Link-valued channel) --- */
        KeyframeChannel textureChannel = replay.properties.properties.get(TEXTURE_CHANNEL);

        if (textureChannel != null && !textureChannel.isEmpty())
        {
            JsonObject texture = serializeTextureChannel(textureChannel);

            if (texture != null)
            {
                out.add(TEXTURE, texture);
            }
        }

        /* --- PBR normal/specular intensities (scalar ValueFloat channels) --- */
        JsonArray pbrNormal = scalarChannel(replay.properties.properties.get(PBR_NORMAL_CHANNEL));

        if (pbrNormal != null && pbrNormal.size() > 0)
        {
            out.add(PBR_NORMAL, pbrNormal);
        }

        JsonArray pbrSpecular = scalarChannel(replay.properties.properties.get(PBR_SPECULAR_CHANNEL));

        if (pbrSpecular != null && pbrSpecular.size() > 0)
        {
            out.add(PBR_SPECULAR, pbrSpecular);
        }

        return out;
    }

    /**
     * The form-level texture channel -> {@code {"keys":[[tick,link,interp],...], "blend":[kf...]}}.
     *
     * <p>{@code keys} is the ordered sequence of texture Links (as {@code "source:path"} strings). The
     * {@code blend} sub-curve is synthesised so the addon has an explicit 0..1 crossfade factor with
     * the SAME tick/interp/handles as the source keyframes: the first key is 0.0 (fully on the initial
     * texture), every subsequent key is 1.0 (fully faded onto its texture) — the addon pairs key i-1
     * -> i and drives its Mix factor from {@code blend}, so a {@code hermite} texture segment fades
     * exactly as BBS interpolates the segment (getSegmentBlendFactor uses the same handles).</p>
     *
     * @return the texture object, or null when no keyframe held a Link (nothing to swap).
     */
    private static JsonObject serializeTextureChannel(KeyframeChannel channel)
    {
        JsonArray keys = new JsonArray();
        JsonArray blend = new JsonArray();

        List<?> keyframes = channel.getKeyframes();
        int index = 0;

        for (Object obj : keyframes)
        {
            Keyframe keyframe = (Keyframe) obj;

            if (keyframe == null)
            {
                continue;
            }

            Object value = keyframe.getValue();
            String link;

            if (value instanceof Link linkValue)
            {
                link = linkValue.toString();
            }
            else if (value == null)
            {
                /* A texture keyframe with no Link = "no texture here" — carry an empty string so the
                 * key indices stay aligned with the blend sub-curve and the addon can skip it. */
                link = "";
            }
            else
            {
                /* Factory said Link; anything else is unexpected — skip, do not abort. */
                continue;
            }

            /* keys: [tick, "source:path", interp] */
            JsonArray key = new JsonArray();
            key.add(keyframe.getTick());
            key.add(link);
            key.add(interpKey(keyframe));
            keys.add(key);

            /* blend: 0.0 on the first key, 1.0 on every subsequent one (fully faded onto its texture),
             * carrying the source keyframe's interp + handles so the fade shape matches BBS. */
            blend.add(component(keyframe, index == 0 ? 0F : 1F));

            index++;
        }

        if (keys.size() == 0)
        {
            return null;
        }

        JsonObject out = new JsonObject();
        out.add(TEXTURE_KEYS, keys);
        out.add(TEXTURE_BLEND, blend);
        return out;
    }

    /**
     * A numeric (Float/Double) property channel -> a scalar keyframe sub-curve, or null when the
     * channel is absent/empty/non-numeric. Same keyframe shape as {@code ActorExporter.exportChannel}
     * (tick/value/interp/handles) so the addon reuses one parser.
     */
    private static JsonArray scalarChannel(KeyframeChannel channel)
    {
        if (channel == null || channel.isEmpty())
        {
            return null;
        }

        /* Belt-and-braces: these are ValueFloat channels, but guard the factory so a mislabelled
         * channel cannot produce a fake curve (mirrors StringChannels' factory check). */
        if (!KeyframeFactories.isNumeric(channel.getFactory()))
        {
            return null;
        }

        JsonArray out = new JsonArray();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            if (keyframe == null || !(keyframe.getValue() instanceof Number number))
            {
                continue;
            }

            out.add(component(keyframe, number.floatValue()));
        }

        return out;
    }

    /** One keyframe entry (value + tick/interp/handles) — identical shape to ActorPropertiesExporter. */
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

    /** interp-key — identical to ActorPropertiesExporter.interpKey (nullable IInterp guard). */
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
