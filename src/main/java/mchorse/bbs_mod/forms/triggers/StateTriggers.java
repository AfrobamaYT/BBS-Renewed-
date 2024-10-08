package mchorse.bbs_mod.forms.triggers;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

public class StateTriggers implements IMapSerializable
{
    public final List<StateTrigger> triggers = new ArrayList<>();

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof StateTriggers triggers)
        {
            return this.triggers.equals(triggers.triggers);
        }

        return super.equals(obj);
    }

    @Override
    public void toData(MapType data)
    {
        ListType triggers = new ListType();

        for (StateTrigger trigger : this.triggers)
        {
            triggers.add(trigger.toData());
        }

        data.put("list", triggers);
    }

    @Override
    public void fromData(MapType data)
    {
        this.triggers.clear();

        for (BaseType baseType : data.getList("list"))
        {
            if (!baseType.isMap())
            {
                continue;
            }

            StateTrigger trigger = new StateTrigger();

            trigger.fromData(baseType.asMap());
            this.triggers.add(trigger);
        }
    }
}