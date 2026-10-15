package com.blenderbridge.actors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.Map;

/**
 * PHASE F — Form-Transform + ShapeKeys serialisierung (audit §1/§8).
 *
 * <p>WHY eigene klasse (nicht in {@link ActorPropertiesExporter}): der bau-plan teilt den material-
 * sync auf mehrere parallel-agents auf, die ALLE {@code ActorPropertiesExporter.serialize()} als
 * ihren integrations-punkt haben — die klasse ist ein hotspot. Diese phase trägt deshalb ihre
 * form-transform-/shape-key-kenntnis in einer eigenen, lazy klasse; der aufrufer (serialize) fügt
 * nur EINE body-zeile hinzu, kein neues static-feld (hotswap-verbot auf geladenen klassen).</p>
 *
 * <p>DREI KANÄLE (alle top-level form-properties, keys aus {@code FormUtils.getPropertyPath}, real
 * gemessen an {@code Form.java:59-60} / {@code ModelForm.java:39}):</p>
 * <ul>
 *   <li>{@code "transform"} → {@link Transform} (form-level t/s/r/r2/pivot — pro-form translate/
 *       scale/DOPPEL-rotation, ZUSÄTZLICH zur objekt-position x/y/z die die transform-kanäle
 *       schon syncen). 15 skalar-subkurven (tx/ty/tz, sx/sy/sz, rx/ry/rz, r2x/r2y/r2z, px/py/pz).</li>
 *   <li>{@code "transform_overlay"} → {@link Transform} (zweite additive form-transform-ebene,
 *       {@code Form.java:60}). Gleiche 15 subkurven, eigener key {@code "formTransformOverlay"}.</li>
 *   <li>{@code "shape_keys"} → {@link ShapeKeys} (map name→float 0..1, mesh-morphs = mimik/
 *       verformung). 1:1 auf Blender-shape-keys — pro morph-name EINE skalar-subkurve.</li>
 * </ul>
 *
 * <p>WERTE ROH (RADIAN für rotationen, contract) — die addon-seite konvertiert nicht die einheit,
 * nur den achsen-raum. Keyframe-shape (tick/value/interp/lx/ly/rx/ry) ist byte-identisch zur
 * color/glow-serialisierung ({@link ActorPropertiesExporter}), damit ein einziger keyframe-parser
 * auf der Blender-seite reicht.</p>
 */
public final class FormTransformExporter
{
    private FormTransformExporter() {}

    /**
     * Serialisiert die form-transform-/shape-key-kanäle eines replays.
     *
     * @return ein JsonObject mit bis zu drei keys ({@code "formTransform"}, {@code "formTransformOverlay"},
     *         {@code "shapeKeys"}) — jeder nur präsent wenn sein kanal existiert und nicht leer ist.
     *         Leer (size()==0) wenn der replay keinen dieser kanäle trägt (byte-identisch für actors
     *         ohne form-transform/morphs).
     */
    public static JsonObject serializeFormTransform(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        // form-level transform (t/s/r/r2/pivot NEBEN der objekt-position) — der schlüssel ist der
        // property-path des Form.transform-value = "transform".
        KeyframeChannel transformChannel = replay.properties.properties.get("transform");

        if (transformChannel != null && !transformChannel.isEmpty())
        {
            out.add("formTransform", serializeTransformChannel(transformChannel));
        }

        // zweite additive transform-ebene (Form.transformOverlay -> property-path "transform_overlay").
        KeyframeChannel overlayChannel = replay.properties.properties.get("transform_overlay");

        if (overlayChannel != null && !overlayChannel.isEmpty())
        {
            out.add("formTransformOverlay", serializeTransformChannel(overlayChannel));
        }

        // mesh-morphs (ModelForm.shapeKeys -> property-path "shape_keys"), value = ShapeKeys.
        KeyframeChannel shapeKeysChannel = replay.properties.properties.get("shape_keys");

        if (shapeKeysChannel != null && !shapeKeysChannel.isEmpty())
        {
            JsonObject shapeKeys = serializeShapeKeysChannel(shapeKeysChannel);

            if (shapeKeys.size() > 0)
            {
                out.add("shapeKeys", shapeKeys);
            }
        }

        return out;
    }

    /**
     * Ein Transform-KeyframeChannel → {@code {"tx":[...],"ty":[...],...,"pz":[...]}} (15 skalar-kurven).
     * Spiegel der color/glow-serialisierung: pro keyframe wird jede skalar-komponente des Transform
     * (translate/scale/rotate/rotate2/pivot ×3 achsen) über {@link #component} als eigene subkurve
     * geschrieben. Rotationen bleiben RADIAN (contract).
     */
    private static JsonObject serializeTransformChannel(KeyframeChannel channel)
    {
        JsonArray tx = new JsonArray(), ty = new JsonArray(), tz = new JsonArray();
        JsonArray sx = new JsonArray(), sy = new JsonArray(), sz = new JsonArray();
        JsonArray rx = new JsonArray(), ry = new JsonArray(), rz = new JsonArray();
        JsonArray r2x = new JsonArray(), r2y = new JsonArray(), r2z = new JsonArray();
        JsonArray px = new JsonArray(), py = new JsonArray(), pz = new JsonArray();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            if (keyframe == null || !(keyframe.getValue() instanceof Transform transform))
            {
                continue;
            }

            tx.add(component(keyframe, transform.translate.x));
            ty.add(component(keyframe, transform.translate.y));
            tz.add(component(keyframe, transform.translate.z));

            sx.add(component(keyframe, transform.scale.x));
            sy.add(component(keyframe, transform.scale.y));
            sz.add(component(keyframe, transform.scale.z));

            rx.add(component(keyframe, transform.rotate.x));
            ry.add(component(keyframe, transform.rotate.y));
            rz.add(component(keyframe, transform.rotate.z));

            /* MERGE fsgh/2.5: Transform.rotate2 gibt es nicht mehr — upstream faltet es beim
             * LADEN in rotate. Effektiv ist die zweite rotation damit ueberall null, und genau
             * das schreiben wir. Die drei kanaele bleiben mit gleicher laenge im json, weil das
             * addon (actor_bones.py) sie parallel zu rx/ry/rz einliest; sie ersatzlos zu
             * streichen wuerde die kurven-laengen auseinanderlaufen lassen. */
            r2x.add(component(keyframe, 0F));
            r2y.add(component(keyframe, 0F));
            r2z.add(component(keyframe, 0F));

            px.add(component(keyframe, transform.pivot.x));
            py.add(component(keyframe, transform.pivot.y));
            pz.add(component(keyframe, transform.pivot.z));
        }

        JsonObject out = new JsonObject();
        out.add("tx", tx);   out.add("ty", ty);   out.add("tz", tz);
        out.add("sx", sx);   out.add("sy", sy);   out.add("sz", sz);
        out.add("rx", rx);   out.add("ry", ry);   out.add("rz", rz);
        out.add("r2x", r2x); out.add("r2y", r2y); out.add("r2z", r2z);
        out.add("px", px);   out.add("py", py);   out.add("pz", pz);
        return out;
    }

    /**
     * Ein ShapeKeys-KeyframeChannel → {@code {"<morph-name>":[kf...], ...}} — pro morph-name EINE
     * skalar-subkurve (value 0..1). WHY so: ein shape-key kann in EINEM keyframe fehlen und im
     * nächsten auftauchen (der user setzt "smile" erst ab tick 10). Wir sammeln erst die UNION aller
     * je vorkommenden namen über alle keyframes, dann baut jede subkurve einen eintrag PRO keyframe —
     * fehlt der name in einem keyframe, gilt 0F (neutraler morph). So bleibt jede subkurve gleich lang
     * wie die anderen und der Blender-shape-key faded sauber von/auf 0.
     */
    private static JsonObject serializeShapeKeysChannel(KeyframeChannel channel)
    {
        // union aller morph-namen über alle keyframes (insertion-reihenfolge stabil).
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();

        for (Object obj : channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) obj;

            if (keyframe != null && keyframe.getValue() instanceof ShapeKeys shapeKeys)
            {
                names.addAll(shapeKeys.shapeKeys.keySet());
            }
        }

        JsonObject out = new JsonObject();

        for (String name : names)
        {
            JsonArray curve = new JsonArray();

            for (Object obj : channel.getKeyframes())
            {
                Keyframe keyframe = (Keyframe) obj;

                if (keyframe == null || !(keyframe.getValue() instanceof ShapeKeys shapeKeys))
                {
                    continue;
                }

                Float value = shapeKeys.shapeKeys.get(name);
                curve.add(component(keyframe, value != null ? value : 0F));
            }

            out.add(name, curve);
        }

        return out;
    }

    /** Ein keyframe-eintrag für EINE skalar-komponente: value + tick/interp/handles.
     *  Byte-identisch zu {@code ActorPropertiesExporter.component} (geteilter keyframe-parser). */
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

    /** interp-key — nullable-IInterp-guard, identisch zu ActorExporter/ActorPropertiesExporter. */
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
