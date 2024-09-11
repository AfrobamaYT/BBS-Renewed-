package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.properties.StringProperty;

public class MobForm extends Form
{
    public final StringProperty mobID = new StringProperty(this, "modId", "minecraft:chicken");

    public MobForm()
    {
        this.register(this.mobID);
    }
}