package com.blenderbridge.actors;

import com.blenderbridge.actors.compat.MaterialCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Pose-Spuren eines Replays nach Blender: die Knochen-Tracks {@code pose.bones.<knochen>} UND der
 * Pose-Track {@code pose} (ein ganzer Posensatz je Keyframe), beide als Sub-Kurven je Knochen.
 *
 * <p>⛔ WARUM UMGEBAUT (2026-09-13, gemessen am laufenden Stack): bis hierher suchte diese Klasse
 * Kanäle mit dem Präfix {@code pose:} — eine Benennung, die es in diesem BBS nicht gibt
 * ({@link PerLimbService#POSE_BONES} ist {@code pose.bones.}), und den Track {@code pose} fasste
 * kein Exporter an. Ein in BBS gehobener Arm kam in Blender nie an: Actor-Import „2 actors / 1
 * models / 10 keys", {@code upper_arm_fk.L} blieb Identität. Der Live-Link soll aber genau das:
 * in BBS animieren, in Blender ergänzen — und umgekehrt ({@link PoseBoneImporter}).</p>
 *
 * <p>PRODUZIERT unter {@code properties}:</p>
 * <pre>
 *   "bones":     { "<knochen>": { sub-kurven } }   — die Knochen-Tracks pose.bones.<knochen>
 *   "poseTrack": { "<knochen>": { sub-kurven } }   — der Pose-Track, je Knochen aufgefächert
 * </pre>
 * <p>Sub-Kurven (jede eine Liste {tick, value, interp, lx, ly, rx, ry} — dieselbe Keyframe-Form wie
 * die Objekt-Kanäle, ein Parser auf der Blender-Seite reicht):
 * {@code tx/ty/tz} translate, {@code sx/sy/sz} scale, {@code rx/ry/rz} Euler-Rotation in RADIANT und
 * in der Drehordnung des Knochens (BBS wertet den Kanal so aus, {@code BOBJBone.eulerQuaternion}),
 * {@code qx/qy/qz/qw} dieselbe Rotation als Quaternion, {@code quat} = true, wenn irgendein Key im
 * Quaternion-Modus steht (dann sind {@code qx..qw} die Wahrheit und {@code rx..rz} nur die
 * Rücklese). Material-Sub-Kurven ({@code cr..ca, pr..pi, gr..gi}) wie bisher, nur wenn ≠ Default.</p>
 *
 * <p>Der Pose-Track: BBS interpoliert einen Pose-Keyframe knochenweise mit der Interpolation des
 * Keyframes ({@code PoseKeyframeFactory}); ein Knochen, der in einem Keyframe fehlt, steht dort auf
 * Identität ({@code Pose.get} legt ihn so an). Genau so wird aufgefächert: Vereinigung aller
 * Knochen über alle Keyframes, je Keyframe der Transform oder Identität, Interpolation und Handles
 * vom Pose-Keyframe.</p>
 *
 * <p>Stehen für einen Knochen BEIDE Spuren, addiert BBS sie ({@code BOBJModel.applyPose}: translate
 * add, rotate add, scale add−1); die Blender-Seite summiert dann dicht je Tick. {@code fix} (Blend
 * zur Ruhe) reist nicht — dokumentierte Grenze. Knochen-Tracks verschachtelter Formen
 * ({@code 0/pose.bones.…}) gehören zu einem Body-Part, nicht zur Armature — übersprungen.</p>
 *
 * <p>RÜCKWÄRTSKOMPATIBEL: der alte Schlüssel {@code pose:<knochen>} wird weiter erkannt.</p>
 */
public final class PerBoneExporter
{
    /** Alte Benennung (vor 2.5), weiter erkannt. */
    private static final String POSE_PREFIX = "pose:";

    /** Der Pose-Track (ganzer Posensatz je Keyframe). */
    private static final String POSE_TRACK = "pose";

    private PerBoneExporter() {}

    /**
     * Serialisiert Knochen-Tracks und Pose-Track eines Replays.
     *
     * @return ein JsonObject mit höchstens den Keys {@code "bones"} und {@code "poseTrack"}. Leer
     *         (size()==0) für Replays ohne Pose-Spuren — byte-identisch zum bisherigen Payload.
     */
    public static JsonObject serializePerBone(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.properties == null)
        {
            return out;
        }

        JsonObject bones = new JsonObject();

        for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet())
        {
            String boneName = boneNameOf(entry.getKey());
            KeyframeChannel channel = entry.getValue();

            if (boneName == null || channel == null || channel.isEmpty())
            {
                continue;
            }

            JsonObject bone = serializeSubcurves(channel.getKeyframes(),
                keyframe -> keyframe.getValue() instanceof PoseTransform pose ? pose : null);

            if (bone.size() > 0)
            {
                bones.add(boneName, bone);
            }
        }

        if (bones.size() > 0)
        {
            out.add("bones", bones);
        }

        JsonObject poseTrack = serializePoseTrack(replay);

        if (poseTrack.size() > 0)
        {
            out.add("poseTrack", poseTrack);
        }

        return out;
    }

    /**
     * Knochenname eines Property-Schlüssels — {@code pose.bones.<b>} (heutiges BBS) oder
     * {@code pose:<b>} (alt); null für alles andere und für Knochen verschachtelter Formen
     * ({@code <part>/pose.bones.<b>}).
     */
    static String boneNameOf(String key)
    {
        if (key == null)
        {
            return null;
        }

        if (key.startsWith(PerLimbService.POSE_BONES))
        {
            String bone = key.substring(PerLimbService.POSE_BONES.length());

            return bone.isEmpty() ? null : bone;
        }

        if (key.startsWith(POSE_PREFIX))
        {
            String bone = key.substring(POSE_PREFIX.length());

            return bone.isEmpty() ? null : bone;
        }

        return null;
    }

    /** Der Pose-Track je Knochen aufgefächert: {"<knochen>": {sub-kurven}}, leer wenn kein Track. */
    private static JsonObject serializePoseTrack(Replay replay)
    {
        JsonObject out = new JsonObject();
        KeyframeChannel channel = replay.properties.properties.get(POSE_TRACK);

        if (channel == null || channel.isEmpty())
        {
            return out;
        }

        List keyframes = channel.getKeyframes();
        Set<String> names = new LinkedHashSet<>();

        for (Object obj : keyframes)
        {
            if (obj instanceof Keyframe keyframe && keyframe.getValue() instanceof Pose pose)
            {
                names.addAll(pose.transforms.keySet());
            }
        }

        for (String name : names)
        {
            JsonObject bone = serializeSubcurves(keyframes, keyframe ->
            {
                if (!(keyframe.getValue() instanceof Pose pose))
                {
                    return null;
                }

                /* transforms.get, NICHT pose.get: letzteres legt den Knochen im Posensatz an. */
                PoseTransform transform = pose.transforms.get(name);

                return transform == null ? IDENTITY : transform;
            });

            if (bone.size() > 0)
            {
                out.add(name, bone);
            }
        }

        return out;
    }

    /** Identität — ein Knochen, der in einem Pose-Keyframe fehlt (Pose.get würde genau das anlegen). */
    private static final PoseTransform IDENTITY = new PoseTransform();

    /**
     * Die Sub-Kurven aus einer Keyframe-Liste; {@code transformOf} liefert je Keyframe den Transform
     * (oder null = Keyframe überspringen).
     */
    private static JsonObject serializeSubcurves(List keyframes, Function<Keyframe, PoseTransform> transformOf)
    {
        JsonArray tx = new JsonArray(), ty = new JsonArray(), tz = new JsonArray();
        JsonArray sx = new JsonArray(), sy = new JsonArray(), sz = new JsonArray();
        JsonArray rx = new JsonArray(), ry = new JsonArray(), rz = new JsonArray();
        JsonArray qx = new JsonArray(), qy = new JsonArray(), qz = new JsonArray(), qw = new JsonArray();

        JsonArray cr = new JsonArray(), cg = new JsonArray(), cb = new JsonArray(), ca = new JsonArray();
        JsonArray pr = new JsonArray(), pg = new JsonArray(), pb = new JsonArray(), pi = new JsonArray();
        JsonArray gr = new JsonArray(), gg = new JsonArray(), gb = new JsonArray(), gi = new JsonArray();

        boolean anyQuat = false, anyColor = false, anyPaint = false, anyGlow = false;
        Vector3f euler = new Vector3f();

        for (Object obj : keyframes)
        {
            if (!(obj instanceof Keyframe keyframe))
            {
                continue;
            }

            PoseTransform pose = transformOf.apply(keyframe);

            if (pose == null)
            {
                continue;
            }

            tx.add(component(keyframe, pose.translate.x));
            ty.add(component(keyframe, pose.translate.y));
            tz.add(component(keyframe, pose.translate.z));

            sx.add(component(keyframe, pose.scale.x));
            sy.add(component(keyframe, pose.scale.y));
            sz.add(component(keyframe, pose.scale.z));

            /* Euler modusbewusst gelesen (Quaternion-Key -> ZYX-Rücklese = Blenders XYZ), Quaternion
             * immer aus createRotation() — die eine Stelle, die den Modus kennt. */
            pose.getEulerRotation(euler);
            rx.add(component(keyframe, euler.x));
            ry.add(component(keyframe, euler.y));
            rz.add(component(keyframe, euler.z));

            Quaternionf q = pose.createRotation();
            qx.add(component(keyframe, q.x));
            qy.add(component(keyframe, q.y));
            qz.add(component(keyframe, q.z));
            qw.add(component(keyframe, q.w));

            if (pose.rotationMode == Transform.RotationMode.QUATERNION)
            {
                anyQuat = true;
            }

            cr.add(component(keyframe, pose.color.r));
            cg.add(component(keyframe, pose.color.g));
            cb.add(component(keyframe, pose.color.b));
            ca.add(component(keyframe, pose.color.a));

            if (pose.color.r != 1F || pose.color.g != 1F || pose.color.b != 1F || pose.color.a != 1F)
            {
                anyColor = true;
            }

            Object paintColor = MaterialCompat.readObject(pose, "paintColor");

            if (paintColor != null)
            {
                pr.add(component(keyframe, MaterialCompat.readFloat(paintColor, "r")));
                pg.add(component(keyframe, MaterialCompat.readFloat(paintColor, "g")));
                pb.add(component(keyframe, MaterialCompat.readFloat(paintColor, "b")));

                float paintIntensity = MaterialCompat.readFloat(paintColor, "a");

                pi.add(component(keyframe, paintIntensity));

                if (paintIntensity != 0F)
                {
                    anyPaint = true;
                }
            }

            Object glowingColor = MaterialCompat.readObject(pose, "glowingColor");

            if (glowingColor != null)
            {
                gr.add(component(keyframe, MaterialCompat.readFloat(glowingColor, "r")));
                gg.add(component(keyframe, MaterialCompat.readFloat(glowingColor, "g")));
                gb.add(component(keyframe, MaterialCompat.readFloat(glowingColor, "b")));

                float glowIntensity = MaterialCompat.readFloat(pose, "glowIntensity");

                gi.add(component(keyframe, glowIntensity));

                if (glowIntensity != 0F)
                {
                    anyGlow = true;
                }
            }
        }

        JsonObject out = new JsonObject();

        if (tx.size() == 0)
        {
            return out;
        }

        out.add("tx", tx);   out.add("ty", ty);   out.add("tz", tz);
        out.add("sx", sx);   out.add("sy", sy);   out.add("sz", sz);
        out.add("rx", rx);   out.add("ry", ry);   out.add("rz", rz);
        out.add("qx", qx);   out.add("qy", qy);   out.add("qz", qz);   out.add("qw", qw);
        out.addProperty("quat", anyQuat);

        if (anyColor)
        {
            out.add("cr", cr); out.add("cg", cg); out.add("cb", cb); out.add("ca", ca);
        }

        if (anyPaint)
        {
            out.add("pr", pr); out.add("pg", pg); out.add("pb", pb); out.add("pi", pi);
        }

        if (anyGlow)
        {
            out.add("gr", gr); out.add("gg", gg); out.add("gb", gb); out.add("gi", gi);
        }

        return out;
    }

    /** Ein Keyframe-Eintrag für EINE Skalar-Komponente: value + tick/interp/handles (geteilte Form). */
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
