package mchorse.bbs_mod.forms.triggers;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Objects;
import java.util.UUID;

public class StateTrigger implements IMapSerializable
{
    public String id = UUID.randomUUID().toString();
    public int hotkey = -1;
    public String action = "";
    public MapType states = new MapType();

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof StateTrigger trigger)
        {
            return Objects.equals(this.id, trigger.id)
                && this.hotkey == trigger.hotkey
                && Objects.equals(this.action, trigger.action)
                && Objects.equals(this.states, trigger.states);
        }

        return super.equals(obj);
    }

    @Override
    public void toData(MapType data)
    {
        data.putString("id", this.id);
        data.putInt("hotkey", this.hotkey);
        data.putString("action", this.action);
        data.put("states", this.states);
    }

    @Override
    public void fromData(MapType data)
    {
        this.id = data.getString("id");
        this.hotkey = data.getInt("hotkey");
        this.action = data.getString("action");
        this.states = data.getMap("states");
    }
}