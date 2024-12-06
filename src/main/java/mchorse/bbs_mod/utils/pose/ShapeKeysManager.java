package mchorse.bbs_mod.utils.pose;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.resources.Link;

public class ShapeKeysManager extends DataManager
{
    public static final ShapeKeysManager INSTANCE = new ShapeKeysManager();

    @Override
    protected Link getFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/shape_keys.json");
    }
}