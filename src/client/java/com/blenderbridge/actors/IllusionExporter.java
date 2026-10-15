package com.blenderbridge.actors;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import com.blenderbridge.actors.compat.MaterialCompat;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * PHASE V1 — Illusion. Serialises a form's primary {@link Illusion} (visual duplicates) for the
 * Blender bridge.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free, {@code LightFormExporter}-muster)</h2>
 * <p>{@link ActorPropertiesExporter} is already loaded; the illusion channel lives in THIS new lazy
 * class. One body-call in {@code ActorPropertiesExporter.serialize} merges it (leader's job).</p>
 *
 * <h2>FORM-level static value (every form has one)</h2>
 * <p>{@code Form.illusion} (ValueIllusion → {@link Illusion}) sits on EVERY form (Form.java:77), not
 * just a subtype. Its fields are plain {@code public int/float/boolean} (count/spread/spacing/
 * directions-bitflags/offset/opacity/glow/…). We only emit when {@code count > 0} — a form without
 * illusions keeps the byte-identical old payload. Blender (actor_illusion.py) turns count+offset+
 * directions into an array modifier.</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * {
 *   "illusion": {
 *     "count":      4,
 *     "spread":     0.0,
 *     "spacing":    1.0,
 *     "directions": 2,     // bitflags: FRONT=1 BACK=2 LEFT=4 RIGHT=8 UP=16 DOWN=32
 *     "offset":     0.0,
 *     "opacity":    1.0,
 *     "uniform":    false,
 *     "invert":     false,
 *     "glow":       0.0,
 *     "gradual":       false,   // nische 2026-07-18: per-kopie-transform-rampe model→letzte kopie
 *     "gradualInvert": false,
 *     "delay":          0.0,    // nische 2026-07-19 (L4): motion-trail — kopie N zeigt actor-
 *                               //   transform von frame−N·delay (TICKS). >0 → geo-nodes-pfad.
 *     "distort":        0.0,    // nische 2026-07-19 (L4): per-kopie-zerfall, wächst mit index.
 *     "distortUniform": false,  //   uniform → alle kopien gleich stark; invert → erste am stärksten
 *     "distortInvert":  false,
 *     "transform": {            // nur präsent wenn NICHT default (Transform.isDefault())
 *       "tx":0.0,"ty":0.0,"tz":0.0,   // translate (blöcke)
 *       "sx":1.0,"sy":1.0,"sz":1.0,   // scale
 *       "rx":0.0,"ry":0.0,"rz":0.0    // rotate (RADIAN, euler)
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>GRENZEN (Regel 0 — ehrlich geflaggt, nicht falsch gemappt)</h2>
 * <p>Sync-bar (v1): count/spread/spacing/directions/offset → array modifier; opacity/glow →
 * material-hinweis. {@code gradual} (per-kopie-transform-rampe) + {@code transform} werden GESENDET
 * (nische 2026-07-18): der addon baut die rampe über den array-modifier-objekt-offset (jede kopie
 * akkumuliert einen bruchteil des transforms → volle {@code transform} an der letzten kopie).
 * {@code delay} (motion-trail) + {@code distort}/{@code distortUniform}/{@code distortInvert}
 * (zerfall) werden GESENDET (nische 2026-07-19, L4): der addon baut sie über einen GEOMETRY-NODES-
 * instancer statt des statischen array-modifiers. {@code delay} trailt den actor-TRANSFORM per kopie
 * (frame−N·delay, scrub-safe via driver-F-curve-sampling) — die pro-kopie-POSE-deformation (arm/bein-
 * stellung aus der vergangenheit) bleibt addon-seitig die dokumentierte grenze (Blender kann kein
 * per-instance-zeit-sampling armature-deformierter mesh). NICHT gesendet / gameplay bzw. v2:
 * {@code real} (welt-kollision), {@code textures}/{@code randomTextures} (per-kopie-texturen),
 * {@code illusionOverlay} + {@code additionalIllusions} (weitere schichten).</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY. Never throws: no form / count==0 returns an empty result.</p>
 */
public final class IllusionExporter
{
    /** Top-level key of the illusion object inside {@code properties}. */
    public static final String ILLUSION = "illusion";

    private IllusionExporter() {}

    /**
     * Serialise the primary illusion of a replay's form.
     *
     * @return an object carrying {@code "illusion"} when the form has an illusion with {@code count>0};
     *         empty (size 0) otherwise. Never null, never throws.
     */
    public static JsonObject serializeIllusion(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.form == null)
        {
            return out;
        }

        Form form = replay.form.get();

        /* Form.illusion + the Illusion type itself only exist in newer BBS-CML; the jar this fork
         * compiles against has neither (measured). Reading them reflectively keeps the whole
         * channel intact — it simply yields nothing here and starts producing data by itself once
         * the BBS base carries the feature. Every read below is null-safe. */
        Object illusionValue = MaterialCompat.readObject(form, "illusion");

        if (illusionValue == null)
        {
            return out;
        }

        /* form.illusion is a value wrapper (ValueIllusion) — .get() yields the Illusion. */
        Object illusion = MaterialCompat.invokeGet(illusionValue);

        if (!MaterialCompat.isIllusion(illusion) || MaterialCompat.readInt(illusion, "count") <= 0)
        {
            return out;
        }

        JsonObject data = new JsonObject();
        data.addProperty("count", MaterialCompat.readInt(illusion, "count"));
        data.addProperty("spread", MaterialCompat.readFloat(illusion, "spread"));
        data.addProperty("spacing", MaterialCompat.readFloat(illusion, "spacing"));
        data.addProperty("directions", MaterialCompat.readInt(illusion, "directions"));
        data.addProperty("offset", MaterialCompat.readFloat(illusion, "offset"));
        data.addProperty("opacity", MaterialCompat.readFloat(illusion, "opacity"));
        data.addProperty("uniform", MaterialCompat.readBoolean(illusion, "uniform"));
        data.addProperty("invert", MaterialCompat.readBoolean(illusion, "invert"));
        /* glow is a FLOAT on Illusion (glow strength), not a flag — verified against the type. */
        data.addProperty("glow", MaterialCompat.readFloat(illusion, "glow"));

        /* nische 2026-07-18: gradual per-copy transform ramp. The transform is only emitted when it
         * is non-default (Transform.isDefault) — a gradual flag with an identity transform is a no-op
         * on the Blender side too, so the addon degrades cleanly either way. rotate is RADIAN euler. */
        data.addProperty("gradual", MaterialCompat.readBoolean(illusion, "gradual"));
        data.addProperty("gradualInvert", MaterialCompat.readBoolean(illusion, "gradualInvert"));

        /* nische 2026-07-19 (L4): delay (motion-trail) + distort (progressive disintegration). Both
         * ride to actor_illusion.py's GEO-NODES path (the array modifier can neither time-shift nor
         * per-copy-deform). delay>0 → each copy N shows the actor TRANSFORM from frame−N·delay (the
         * ghost trail lagging behind the motion); delay is in TICKS (as Illusion.delay). distort>0 →
         * per-copy noise displacement growing with the copy index (last copies dissolve); distortUniform
         * makes all copies equally distorted, distortInvert reverses the ramp (first copy strongest).
         * Always emitted (default 0/false = byte-identical no-op on the Blender side: 0 → array path). */
        data.addProperty("delay", MaterialCompat.readFloat(illusion, "delay"));
        data.addProperty("distort", MaterialCompat.readFloat(illusion, "distort"));
        data.addProperty("distortUniform", MaterialCompat.readBoolean(illusion, "distortUniform"));
        data.addProperty("distortInvert", MaterialCompat.readBoolean(illusion, "distortInvert"));

        Transform transform = MaterialCompat.readObject(illusion, "transform") instanceof Transform t ? t : null;

        if (transform != null && !transform.isDefault())
        {
            JsonObject t = new JsonObject();
            t.addProperty("tx", transform.translate.x);
            t.addProperty("ty", transform.translate.y);
            t.addProperty("tz", transform.translate.z);
            t.addProperty("sx", transform.scale.x);
            t.addProperty("sy", transform.scale.y);
            t.addProperty("sz", transform.scale.z);
            t.addProperty("rx", transform.rotate.x);
            t.addProperty("ry", transform.rotate.y);
            t.addProperty("rz", transform.rotate.z);
            data.add("transform", t);
        }

        out.add(ILLUSION, data);

        return out;
    }
}
