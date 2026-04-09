package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.Map;

public final class ModelPhysicsIO
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;
    private static final boolean DEFAULT_COLLISIONS = false;
    private static final float DEFAULT_RADIUS = 0.1F;

    private ModelPhysicsIO()
    {
    }

    public static ModelPhysicsConfig fromData(MapType map)
    {
        if (map == null || !map.has("bones", BaseType.TYPE_MAP))
        {
            return null;
        }

        MapType bones = map.getMap("bones");
        Map<String, ModelPhysicsConfig.Bone> out = new HashMap<>();

        for (String root : bones.keys())
        {
            if (!bones.has(root, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = bones.getMap(root);
            String end = entry.getString("end");
            String targetBone = entry.getString("target_bone", "");

            if (root == null || root.isEmpty() || end == null || end.isEmpty())
            {
                continue;
            }

            float gravity = entry.getFloat("gravity", DEFAULT_GRAVITY);
            float damping = entry.getFloat("damping", DEFAULT_DAMPING);
            int iterations = entry.getInt("iterations", DEFAULT_ITERATIONS);
            boolean collisions = entry.getBool("collisions", DEFAULT_COLLISIONS);
            float radius = entry.getFloat("radius", DEFAULT_RADIUS);

            if (iterations < 1)
            {
                iterations = 1;
            }

            if (radius < 0F)
            {
                radius = 0F;
            }

            out.put(root, new ModelPhysicsConfig.Bone(end, targetBone, gravity, damping, iterations, collisions, radius));
        }

        return out.isEmpty() ? null : new ModelPhysicsConfig(out);
    }

    public static MapType toData(ModelPhysicsConfig config)
    {
        MapType root = new MapType();
        MapType bones = new MapType();

        if (config != null && config.bones() != null)
        {
            for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
            {
                String rootId = entry.getKey();
                ModelPhysicsConfig.Bone bone = entry.getValue();

                if (rootId == null || rootId.isEmpty() || bone == null || bone.end() == null || bone.end().isEmpty())
                {
                    continue;
                }

                MapType map = new MapType();
                map.putString("end", bone.end());

                if (bone.targetBone() != null && !bone.targetBone().isEmpty())
                {
                    map.putString("target_bone", bone.targetBone());
                }

                map.putFloat("gravity", bone.gravity());
                map.putFloat("damping", bone.damping());
                map.putInt("iterations", Math.max(1, bone.iterations()));

                if (bone.collisions())
                {
                    map.putBool("collisions", true);
                }

                if (bone.radius() != DEFAULT_RADIUS)
                {
                    map.putFloat("radius", bone.radius());
                }

                bones.put(rootId, map);
            }
        }

        root.put("bones", bones);
        return root;
    }
}
