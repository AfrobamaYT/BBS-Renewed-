package mchorse.bbs_mod.cubic.model;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Transform;

public class ArmorSlot implements IMapSerializable
{
    public final Transform transform = new Transform();
    public String group = "";

    @Override
    public void toData(MapType data)
    {
        /* Unnecessary yet */
    }

    @Override
    public void fromData(MapType data)
    {
        this.transform.fromData(data.getMap("transform"));
        this.group = data.getString("group");
    }
}
