package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ModelConstraintsRuntime
{
    private static final WeakHashMap<MapType, Map<String, ModelConstraintsConfig.BoneConstraint>> EMBEDDED = new WeakHashMap<>();

    private ModelConstraintsRuntime()
    {
    }

    public static void clearCache()
    {
        EMBEDDED.clear();
    }

    public static void invalidate(String modelId)
    {
        EMBEDDED.clear();
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> bones = getBones(instance);

        if (bones == null || bones.isEmpty())
        {
            return;
        }

        applyToModel(model, bones);
    }

    public static Map<String, ModelConstraintsConfig.BoneConstraint> getBones(ModelInstance instance)
    {
        if (instance != null && instance.form instanceof ModelForm form && form.constraints.get() instanceof MapType map)
        {
            Map<String, ModelConstraintsConfig.BoneConstraint> cached = EMBEDDED.get(map);

            if (cached != null)
            {
                return cached;
            }

            ModelConstraintsConfig config = ModelConstraintsIO.fromData(map);
            Map<String, ModelConstraintsConfig.BoneConstraint> bones = config == null || config.bones() == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(config.bones()));

            EMBEDDED.put(map, bones);

            return bones;
        }

        return Collections.emptyMap();
    }

    private static void applyToModel(Model model, Map<String, ModelConstraintsConfig.BoneConstraint> bones)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null)
            {
                continue;
            }

            ModelConstraintsConfig.BoneConstraint c = bones.get(group.id);

            if (c == null || !c.enabled())
            {
                continue;
            }

            float minX = c.minX();
            float minY = c.minY();
            float minZ = c.minZ();
            float maxX = c.maxX();
            float maxY = c.maxY();
            float maxZ = c.maxZ();

            if (minX > maxX)
            {
                float t = minX;
                minX = maxX;
                maxX = t;
            }

            if (minY > maxY)
            {
                float t = minY;
                minY = maxY;
                maxY = t;
            }

            if (minZ > maxZ)
            {
                float t = minZ;
                minZ = maxZ;
                maxZ = t;
            }

            group.current.rotate.x = MathUtils.clamp(group.current.rotate.x, minX, maxX);
            group.current.rotate.y = MathUtils.clamp(group.current.rotate.y, minY, maxY);
            group.current.rotate.z = MathUtils.clamp(group.current.rotate.z, minZ, maxZ);
        }
    }

}
