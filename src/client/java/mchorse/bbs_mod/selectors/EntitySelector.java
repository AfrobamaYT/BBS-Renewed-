package mchorse.bbs_mod.selectors;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;

public class EntitySelector implements IMapSerializable
{
    public Form form;
    public Identifier entity;
    public String name = "";

    public boolean matches(LivingEntity mcEntity)
    {
        Identifier id = Registries.ENTITY_TYPE.getId(mcEntity.getType());

        if (id.equals(this.entity))
        {
            Text displayName = mcEntity.getDisplayName();

            if (displayName != null && !this.name.isEmpty())
            {
                if (mcEntity.hasCustomName())
                {
                    String literalString = displayName.getString();

                    return Objects.equals(literalString, this.name);
                }
                else
                {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public void fromData(MapType data)
    {
        if (data.has("form")) this.form = FormUtils.fromData(data.getMap("form"));
        if (data.has("entity")) this.entity = new Identifier(data.getString("entity"));
        if (data.has("name")) this.name = data.getString("name");
    }

    @Override
    public void toData(MapType data)
    {
        if (this.form != null) data.put("form", FormUtils.toData(this.form));
        if (this.entity != null) data.putString("entity", this.entity.toString());
        if (this.name != null) data.putString("name", this.name);
    }
}