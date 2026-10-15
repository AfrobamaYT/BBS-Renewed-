package com.blenderbridge.actors;

import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Lesehilfen für das {@code bones}-Objekt eines ACTORS_IMPORT-Replays (Sub-Kurven je Knochen, siehe
 * {@link PoseBoneImporter}): Vereinigung der Ticks, Wert einer Sub-Kurve am Tick, die von Blender
 * gewählte Keyframe-Form. Reine Payload-Funktionen ohne Film-Zugriff.
 */
final class PoseBoneKeys
{
    /** Zwei Ticks näher als das gelten als derselbe Keyframe (Blender rundet Frames auf 1e-4). */
    static final float TICK_EPS = 1e-3F;

    private static final String[] SUBCURVES = {"tx", "ty", "tz", "sx", "sy", "sz", "rx", "ry", "rz", "qx", "qy", "qz", "qw"};

    private PoseBoneKeys() {}

    /** Vereinigung aller Ticks über alle Sub-Kurven, sortiert, auf {@link #TICK_EPS} zusammengelegt. */
    static List<Float> unionTicks(JsonObject sub)
    {
        TreeSet<Float> ticks = new TreeSet<>();

        for (String key : SUBCURVES)
        {
            JsonElement arr = sub.get(key);

            if (arr == null || !arr.isJsonArray())
            {
                continue;
            }

            for (JsonElement element : arr.getAsJsonArray())
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                float tick = (float) BridgeProtocol.getDouble(element.getAsJsonObject(), ActorProtocol.KF_TICK, 0D);
                Float near = ticks.floor(tick + TICK_EPS);

                if (near == null || Math.abs(near - tick) > TICK_EPS)
                {
                    ticks.add(tick);
                }
            }
        }

        return new ArrayList<>(ticks);
    }

    /** Wert der Sub-Kurve {@code key} am Tick, oder {@code sonst}, wenn dort kein Key liegt. */
    static float valueAt(JsonObject sub, String key, float tick, float sonst)
    {
        JsonObject kf = keyAt(sub.get(key), tick);

        return kf == null ? sonst : (float) BridgeProtocol.getDouble(kf, ActorProtocol.KF_VALUE, sonst);
    }

    /**
     * Die von Blender gewählte Keyframe-Form (interp/handles) am Tick — aus {@code interp}, sonst aus
     * der ersten Rotations-, dann Orts-, dann Skalen-Sub-Kurve mit einem Key dort.
     */
    static JsonObject shapeAt(JsonObject sub, float tick)
    {
        JsonObject shape = keyAt(sub.get("interp"), tick);

        if (shape != null)
        {
            return shape;
        }

        for (String key : new String[] {"rx", "qw", "tx", "sx"})
        {
            shape = keyAt(sub.get(key), tick);

            if (shape != null)
            {
                return shape;
            }
        }

        return null;
    }

    /** Der Keyframe-Eintrag einer Liste am Tick, oder null. */
    static JsonObject keyAt(JsonElement arr, float tick)
    {
        if (arr == null || !arr.isJsonArray())
        {
            return null;
        }

        for (JsonElement element : arr.getAsJsonArray())
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject kf = element.getAsJsonObject();

            if (Math.abs((float) BridgeProtocol.getDouble(kf, ActorProtocol.KF_TICK, Double.NaN) - tick) <= TICK_EPS)
            {
                return kf;
            }
        }

        return null;
    }
}
