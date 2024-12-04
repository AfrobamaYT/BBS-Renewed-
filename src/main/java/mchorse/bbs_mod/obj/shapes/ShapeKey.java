package mchorse.bbs_mod.obj.shapes;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Objects;

public class ShapeKey implements IMapSerializable
{
    public String name;
    public float value;
    public boolean relative = true;

    public ShapeKey()
    {}

    public ShapeKey(String name, float value)
    {
        this.name = name;
        this.value = value;
    }

    public ShapeKey(String name, float value, boolean relative)
    {
        this(name, value);
        this.relative = relative;
    }

    public ShapeKey setValue(float value)
    {
        this.value = value;

        return this;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ShapeKey)
        {
            ShapeKey shape = (ShapeKey) obj;

            return this.value == shape.value && Objects.equals(this.name, shape.name) && this.relative == shape.relative;
        }

        return super.equals(obj);
    }

    public ShapeKey copy()
    {
        return new ShapeKey(this.name, this.value, this.relative);
    }

    @Override
    public void toData(MapType data)
    {
        data.putString("name", this.name);
        data.putFloat("value", this.value);
        data.putBool("relative", this.relative);
    }

    @Override
    public void fromData(MapType data)
    {
        this.name = data.getString("name");
        this.value = data.getFloat("value");
        this.relative = data.getBool("relative");
    }
}