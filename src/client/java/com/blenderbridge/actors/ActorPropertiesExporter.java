package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.blenderbridge.actors.compat.MaterialCompat;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Serialisiert die material-tragenden FormProperties-kanäle eines Replays für den Blender-bridge.
 *
 * <p>WHY eigene klasse: {@link ActorExporter} ist beim ersten export schon geladen; die package-
 * deploy-regel (siehe StringChannels) erlaubt dort nur EINEN body-call, kein neues feld/methode.
 * Diese klasse ist neu + lazy, trägt deshalb die ganze FormProperties-kenntnis des bridge.</p>
 *
 * <p>V1 exportiert nur den top-level {@code "color"}-kanal = whole-actor tint (Color-value, floats
 * r/g/b/a 0..1). Die color reist als VIER separate keyframe-kurven (r,g,b,a), weil Blender skalare
 * F-curves animiert — ein packed-int wäre auf der addon-seite nicht als kurve interpolierbar. Tick/
 * interp/handles sind identisch zur transform-serialisierung (ActorExporter.exportChannel), damit
 * die addon-seite EINEN keyframe-shape-parser für beide nutzt. paint/glow/per-bone: spätere tasks.</p>
 */
public final class ActorPropertiesExporter
{
    private ActorPropertiesExporter() {}

    public static JsonObject serialize(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        /* ModelForm.texture is a static per-actor override, not a keyframe channel.
         * Without it Blender used config.json's default skin even when BBS rendered
         * this form with a different one (BBSPromo's blue Steve). */
        if (replay.form.get() instanceof ModelForm modelForm
            && modelForm.texture.get() != null)
        {
            out.addProperty("formTexture", modelForm.texture.get().toString());
        }

        KeyframeChannel colorChannel = replay.properties.properties.get("color");

        if (colorChannel != null && !colorChannel.isEmpty())
        {
            out.add("color", serializeColorChannel(colorChannel));
        }

        // Sichtbarkeit: der boolesche top-level "visible"-kanal (Form.visible). Reist als 1/0-kurve;
        // BBS haelt bool-werte stufig (BooleanKeyframeFactory.interpolate liefert immer a), darum
        // setzt die addon-seite CONSTANT. Ohne den kanal war ein in BBS ausgeblendeter actor in
        // Blender in allen frames sichtbar.
        KeyframeChannel visibleChannel = replay.properties.properties.get("visible");

        if (visibleChannel != null && !visibleChannel.isEmpty())
        {
            /* Inline statt Hilfsmethode: die CF-Instanz nimmt nur Rumpf-Swaps. */
            JsonArray visible = new JsonArray();

            for (Object raw : visibleChannel.getKeyframes())
            {
                Keyframe keyframe = (Keyframe) raw;

                if (keyframe.getValue() instanceof Boolean value)
                {
                    visible.add(component(keyframe, value ? 1F : 0F));
                }
            }

            out.add("visible", visible);
        }

        // PHASE G — glow/emission: der GlowSettings-kanal (whole-form leucht-farbe + intensität)
        // reist als vier skalar-subkurven r/g/b/intensity, exakt wie color (Blender animiert
        // skalare F-curves). Blender-seite mappt r/g/b -> BSDF Emission Color, intensity ->
        // Emission Strength. intensity kann NEGATIV sein (dunkelt in BBS) — die grenze (Blender
        // clamped ≥0) wird addon-seitig geflaggt, hier reist der rohe wert.
        KeyframeChannel glowChannel = replay.properties.properties.get(ActorProtocol.PROP_GLOW);

        if (glowChannel != null && !glowChannel.isEmpty())
        {
            out.add(ActorProtocol.PROP_GLOW, serializeGlowChannel(glowChannel));
        }

        // WELLE 1 (2026-07-18, 4 parallel-worker): weitere material-kanaele aus disjunkten lazy
        // exporter-klassen. Jeder gibt ein JsonObject (leer wenn kein kanal) — additiv ins properties-
        // objekt gemerged (keySet-loop), so bleibt der payload byte-identisch fuer actors ohne den
        // kanal. Die 4 klassen waren Paint/Texture/FormTransform/ShapeGraph — ShapeGraphExporter
        // ist 2026-07-27 mit dem CML-fremden shape-node-graph (ShapeForm) entfernt worden
        // (siehe zurueckgestellt/cml-unerreichbar/), payload verliert dadurch nur den
        // "shapeGraph"-key bei actors, die ohnehin nie ein ShapeForm waren (nicht upstream-teil).
        mergeInto(out, PaintExporter.serializePaint(replay));
        mergeInto(out, TextureExporter.serializeTexture(replay));
        mergeInto(out, FormTransformExporter.serializeFormTransform(replay));
        mergeInto(out, FormIdentityExporter.serialize(replay));
        // PHASE B — per-bone: pose:<bone>-transforms (12 subkurven/bone) + per-bone-material.
        // Produziert properties.bones = {"<bone>":{tx/ty/tz sx/sy/sz rx/ry/rz r2x/r2y/r2z [+material]}}.
        mergeInto(out, PerBoneExporter.serializePerBone(replay));
        mergeInto(out, PoseOverlayExporter.serialize(replay));
        JsonObject rigProperties = RigPropertyCurves.serialize(replay);

        if (rigProperties.size() > 0)
        {
            out.add(RigPropertyCurves.KEY, rigProperties);
        }
        // PHASE V — form-typ-spezifika (LightForm/LabelForm/Illusion). FORM-static (Value*-felder,
        // keine keyframe-kanäle) → flache werte unter properties.{light,label,illusion}. Additiv.
        mergeInto(out, LightFormExporter.serializeLight(replay));
        mergeInto(out, LabelFormExporter.serializeLabel(replay));
        mergeInto(out, IllusionExporter.serializeIllusion(replay));
        mergeInto(out, BillboardExporter.serialize(replay));
        mergeInto(out, CubicModelExport.describe(replay));
        mergeInto(out, PackagedModelExport.describe(replay));

        return out;
    }

    /** Alle keys eines quell-JsonObjects additiv in {@code target} kopieren (leer = no-op). */
    private static void mergeInto(JsonObject target, JsonObject source)
    {
        if (source == null)
        {
            return;
        }

        for (String key : source.keySet())
        {
            target.add(key, source.get(key));
        }
    }

    /** Ein GlowSettings-KeyframeChannel -> {"r":[...],"g":[...],"b":[...],"intensity":[...]}
     *  (4 skalar-kurven). Spiegel von {@link #serializeColorChannel}, value-cast auf GlowSettings,
     *  {@code a} ersetzt durch {@code intensity} (GlowSettings hat kein alpha). */
    private static JsonObject serializeGlowChannel(KeyframeChannel channel)
    {
        JsonArray r = new JsonArray();
        JsonArray g = new JsonArray();
        JsonArray b = new JsonArray();
        JsonArray intensity = new JsonArray();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            /* MaterialCompat instead of `instanceof GlowSettings`: the BBS-CML jar this fork
             * compiles against has no GlowSettings class (measured). Absent -> isGlow() is
             * false for every keyframe -> empty sub-curves -> the caller skips the channel. */
            Object glow = keyframe == null ? null : keyframe.getValue();

            if (!MaterialCompat.isGlow(glow))
            {
                continue;
            }

            r.add(component(keyframe, MaterialCompat.readFloat(glow, "r")));
            g.add(component(keyframe, MaterialCompat.readFloat(glow, "g")));
            b.add(component(keyframe, MaterialCompat.readFloat(glow, "b")));
            intensity.add(component(keyframe, MaterialCompat.readFloat(glow, "intensity")));
        }

        JsonObject out = new JsonObject();
        out.add(ActorProtocol.PROP_R, r);
        out.add(ActorProtocol.PROP_G, g);
        out.add(ActorProtocol.PROP_B, b);
        out.add(ActorProtocol.PROP_INTENSITY, intensity);
        return out;
    }

    /** Ein Color-KeyframeChannel -> {"r":[...],"g":[...],"b":[...],"a":[...]} (4 skalar-kurven). */
    private static JsonObject serializeColorChannel(KeyframeChannel channel)
    {
        JsonArray r = new JsonArray();
        JsonArray g = new JsonArray();
        JsonArray b = new JsonArray();
        JsonArray a = new JsonArray();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            if (keyframe == null || !(keyframe.getValue() instanceof Color color))
            {
                continue;
            }

            r.add(component(keyframe, color.r));
            g.add(component(keyframe, color.g));
            b.add(component(keyframe, color.b));
            a.add(component(keyframe, color.a));
        }

        JsonObject out = new JsonObject();
        out.add(ActorProtocol.PROP_R, r);
        out.add(ActorProtocol.PROP_G, g);
        out.add(ActorProtocol.PROP_B, b);
        out.add(ActorProtocol.PROP_A, a);
        return out;
    }

    /** Ein keyframe-eintrag für EINE farb-komponente: value + tick/interp/handles (wie exportChannel). */
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

    /** interp-key — identisch zu ActorExporter.interpKey (nullable IInterp-guard). */
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
