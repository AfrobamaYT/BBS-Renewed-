package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.interps.CustomInterpolationManager;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Blender-Knochen-Keys ZURÜCK in den Film: das {@code bones}-Objekt eines ACTORS_IMPORT-Replays wird
 * zu Knochen-Tracks {@code pose.bones.<knochen>} (Gegenrichtung zu {@link PerBoneExporter}).
 *
 * <p>WIRE (von actor_export.bones_for_object gebaut):</p>
 * <pre>
 *   "bones": { "<knochen>": {
 *       "tx":[kf…],"ty":[…],"tz":[…], "sx":[…],"sy":[…],"sz":[…],
 *       "rx":[…],"ry":[…],"rz":[…]           (Euler, RADIANT, Drehordnung des Knochens)  ODER
 *       "qw":[…],"qx":[…],"qy":[…],"qz":[…]  mit "quat": true,
 *       "interp": [{tick, interp, lx, ly, rx, ry}, …]   je Tick die Interpolation des Keyframes
 *   }}
 * </pre>
 * <p>Alle Sub-Kurven tragen Keys an DENSELBEN Ticks (Blender füllt die Vereinigung der Frames auf,
 * s. actor_export) — ein BBS-Keyframe hält ja alle Komponenten zugleich. {@code interp} ist die
 * Interpolation JE KEYFRAME (BBS kennt nur eine je Keyframe, Blender eine je Komponente; die
 * Blender-Seite wählt und protokolliert die Degradation).</p>
 *
 * <p>VOLLERSATZ je Knochen wie bei den Objekt-Kanälen: der Track wird geleert und aus dem Payload
 * gebaut — ein in Blender gelöschter Key verschwindet auch hier.</p>
 *
 * <p>⛔ BESITZ: schreibt Blender einen Knochen, wird derselbe Knochen aus den Keyframes des
 * Pose-Tracks {@code pose} entfernt. Sonst addiert BBS beide Spuren ({@code BOBJModel.applyPose})
 * und der Arm stünde doppelt gehoben. Der letzte Schreiber besitzt den Knochen; der alte Stand
 * liegt im FilmBackup, das um jeden Import läuft ({@code ActorBridge}: {@code FilmBackup.protect}).
 * Der Eingriff läuft über {@code setValue(kopie, true)} — Undo-fähig als Teil des Imports.</p>
 *
 * <p>Neue, lazy geladene Klasse; der Anschluss in {@code ActorImporter.applyReplay} ist eine Zeile.</p>
 */
public final class PoseBoneImporter
{
    private static final String POSE_TRACK = "pose";

    private PoseBoneImporter() {}

    /**
     * @return Anzahl geschriebener Knochen-Tracks (0 = nichts anwendbar).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int apply(Replay replay, JsonObject bones)
    {
        if (replay == null || bones == null || replay.properties == null)
        {
            return 0;
        }

        Form form = replay.form.get();

        if (form == null)
        {
            return 0;
        }

        int written = 0;

        for (Map.Entry<String, JsonElement> entry : bones.entrySet())
        {
            String bone = entry.getKey();

            if (bone == null || bone.isEmpty() || !entry.getValue().isJsonObject())
            {
                continue;
            }

            JsonObject sub = entry.getValue().getAsJsonObject();
            List<Float> ticks = PoseBoneKeys.unionTicks(sub);

            if (ticks.isEmpty())
            {
                continue;
            }

            KeyframeChannel channel = replay.properties.getOrCreate(form, PerLimbService.POSE_BONES + bone);

            if (channel == null)
            {
                continue;
            }

            boolean quat = sub.has("quat") && sub.get("quat").isJsonPrimitive() && sub.get("quat").getAsBoolean();

            channel.removeAll();

            for (float tick : ticks)
            {
                PoseTransform t = new PoseTransform();

                t.translate.set(PoseBoneKeys.valueAt(sub, "tx", tick, 0F), PoseBoneKeys.valueAt(sub, "ty", tick, 0F), PoseBoneKeys.valueAt(sub, "tz", tick, 0F));
                t.scale.set(PoseBoneKeys.valueAt(sub, "sx", tick, 1F), PoseBoneKeys.valueAt(sub, "sy", tick, 1F), PoseBoneKeys.valueAt(sub, "sz", tick, 1F));

                if (quat)
                {
                    t.rotationMode = Transform.RotationMode.QUATERNION;
                    t.quat.set(PoseBoneKeys.valueAt(sub, "qx", tick, 0F), PoseBoneKeys.valueAt(sub, "qy", tick, 0F), PoseBoneKeys.valueAt(sub, "qz", tick, 0F), PoseBoneKeys.valueAt(sub, "qw", tick, 1F));

                    if (t.quat.lengthSquared() > 1e-12F)
                    {
                        t.quat.normalize();
                    }
                    else
                    {
                        t.quat.identity();
                    }
                }
                else
                {
                    t.rotate.set(PoseBoneKeys.valueAt(sub, "rx", tick, 0F), PoseBoneKeys.valueAt(sub, "ry", tick, 0F), PoseBoneKeys.valueAt(sub, "rz", tick, 0F));
                }

                int index = channel.insert(tick, t);
                Keyframe keyframe = channel.get(index);

                if (keyframe == null)
                {
                    continue;
                }

                JsonObject shape = PoseBoneKeys.shapeAt(sub, tick);

                if (shape != null)
                {
                    keyframe.getInterpolation().setInterp(resolveInterp(BridgeProtocol.getFieldString(shape, ActorProtocol.KF_INTERP, "")));
                    keyframe.lx = (float) BridgeProtocol.getDouble(shape, ActorProtocol.KF_LX, 5D);
                    keyframe.ly = (float) BridgeProtocol.getDouble(shape, ActorProtocol.KF_LY, 0D);
                    keyframe.rx = (float) BridgeProtocol.getDouble(shape, ActorProtocol.KF_RX, 5D);
                    keyframe.ry = (float) BridgeProtocol.getDouble(shape, ActorProtocol.KF_RY, 0D);
                }
            }

            int freed = releaseFromPoseTrack(replay, bone);

            System.out.println("[blenderbridge] pose.bones." + bone + ": " + ticks.size() + " keys aus Blender"
                + (quat ? " (quaternion)" : " (euler)") + (freed > 0 ? ", aus " + freed + " pose-keyframe(s) geloest" : ""));
            written++;
        }

        return written;
    }

    /**
     * Den Knochen aus jedem Keyframe des Pose-Tracks nehmen (Besitz wechselt zum Knochen-Track).
     * Undo-fähig: Kopie ändern, dann {@code setValue(kopie, true)}.
     *
     * @return Anzahl geänderter Pose-Keyframes
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int releaseFromPoseTrack(Replay replay, String bone)
    {
        KeyframeChannel channel = replay.properties.properties.get(POSE_TRACK);

        if (channel == null || channel.isEmpty())
        {
            return 0;
        }

        int count = 0;

        for (Object obj : new ArrayList<>(channel.getKeyframes()))
        {
            if (!(obj instanceof Keyframe keyframe) || !(keyframe.getValue() instanceof Pose pose))
            {
                continue;
            }

            if (!pose.transforms.containsKey(bone))
            {
                continue;
            }

            Pose kopie = pose.copy();

            kopie.transforms.remove(bone);
            keyframe.setValue(kopie, true);
            count++;
        }

        return count;
    }

    /** Wie ActorImporter.resolveInterp: eingebaut, dann eigene Interpolationen, sonst linear. */
    private static IInterp resolveInterp(String key)
    {
        if (key == null || key.isEmpty())
        {
            return Interpolations.LINEAR;
        }

        IInterp interp = Interpolations.MAP.get(key);

        if (interp != null)
        {
            return interp;
        }

        IInterp custom = CustomInterpolationManager.INSTANCE.get(key);

        return custom == null ? Interpolations.LINEAR : custom;
    }
}
