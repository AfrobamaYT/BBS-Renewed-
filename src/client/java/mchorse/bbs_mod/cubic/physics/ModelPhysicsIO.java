package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ModelPhysicsIO
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;

    private ModelPhysicsIO()
    {
    }

    public static File getFile(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        return BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/physics.json");
    }

    public static ModelPhysicsConfig read(String modelId)
    {
        File file = getFile(modelId);

        if (file == null || !file.exists())
        {
            return null;
        }

        MapType map = readMap(file);

        if (map == null || !map.has("chains", BaseType.TYPE_LIST))
        {
            return null;
        }

        ListType chains = map.getList("chains");
        List<ModelPhysicsConfig.Chain> out = new ArrayList<>();

        for (int i = 0; i < chains.size(); i++)
        {
            if (!chains.has(i, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = chains.getMap(i);
            String root = entry.getString("root");
            String end = entry.getString("end");

            if (root.isEmpty() || end.isEmpty())
            {
                continue;
            }

            String attach = entry.getString("attach");
            float gravity = entry.getFloat("gravity", DEFAULT_GRAVITY);
            float damping = entry.getFloat("damping", DEFAULT_DAMPING);
            int iterations = entry.getInt("iterations", DEFAULT_ITERATIONS);

            if (iterations < 1)
            {
                iterations = 1;
            }

            out.add(new ModelPhysicsConfig.Chain(attach, root, end, gravity, damping, iterations));
        }

        return out.isEmpty() ? null : new ModelPhysicsConfig(out);
    }

    public static boolean write(String modelId, ModelPhysicsConfig config)
    {
        File file = getFile(modelId);

        if (file == null)
        {
            return false;
        }

        file.getParentFile().mkdirs();

        MapType root = new MapType();
        ListType chains = new ListType();

        if (config != null && config.chains() != null)
        {
            for (ModelPhysicsConfig.Chain chain : config.chains())
            {
                if (chain == null)
                {
                    continue;
                }

                String rootId = chain.root();
                String endId = chain.end();

                if (rootId == null || rootId.isEmpty() || endId == null || endId.isEmpty())
                {
                    continue;
                }

                MapType entry = new MapType();
                String attach = chain.attach();

                if (attach != null && !attach.isEmpty() && !attach.equals(rootId))
                {
                    entry.putString("attach", attach);
                }

                entry.putString("root", rootId);
                entry.putString("end", endId);
                entry.putFloat("gravity", chain.gravity());
                entry.putFloat("damping", chain.damping());
                entry.putInt("iterations", Math.max(1, chain.iterations()));

                chains.add(entry);
            }
        }

        root.put("chains", chains);

        return DataToString.writeSilently(file, root, true);
    }

    private static MapType readMap(File file)
    {
        try
        {
            BaseType parsed = DataToString.fromString(IOUtils.readText(file));

            if (parsed instanceof MapType map)
            {
                return map;
            }
        }
        catch (Exception e)
        {}

        return null;
    }
}
