package mchorse.bbs_mod.obj.shapes;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

public class ShapeKeys implements IMapSerializable
{
    public final List<ShapeKey> shapeKeys = new ArrayList<>();

    public ShapeKeys copy()
    {
        ShapeKeys keys = new ShapeKeys();

        for (ShapeKey shapeKey : this.shapeKeys)
        {
            keys.shapeKeys.add(shapeKey.copy());
        }

        return keys;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ShapeKeys keys)
        {
            return this.shapeKeys.equals(keys.shapeKeys);
        }

        return super.equals(obj);
    }

    @Override
    public void toData(MapType data)
    {
        ListType keys = new ListType();

        for (ShapeKey key : this.shapeKeys)
        {
            keys.add(key.toData());
        }

        data.put("keys", keys);
    }

    @Override
    public void fromData(MapType data)
    {
        this.shapeKeys.clear();

        ListType list = data.getList("keys");

        for (BaseType keyType : list)
        {
            if (keyType instanceof MapType map)
            {
                ShapeKey key = new ShapeKey();

                key.fromData(map);
                this.shapeKeys.add(key);
            }
        }
    }
}