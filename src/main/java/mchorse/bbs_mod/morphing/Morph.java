package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.lang.reflect.Field;

public class Morph
{
    public Form form;

    public static Morph getMorph(PlayerEntity entity)
    {
        try
        {
            // TODO: DON'T USE REFLECTION!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            Field field = PlayerEntity.class.getDeclaredField("morph");

            return  (Morph) field.get(entity);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public void update(PlayerEntity entity)
    {
        if (this.form != null)
        {
            this.form.update(new MCEntity(entity));
        }
    }

    public NbtElement toNbt()
    {
        NbtCompound compound = new NbtCompound();

        if (this.form != null)
        {
            compound.put("Form", DataStorageUtils.toNbt(FormUtils.toData(this.form)));
        }

        return compound;
    }

    public void fromNbt(NbtCompound compound)
    {
        if (compound.contains("Form"))
        {
            MapType map = (MapType) DataStorageUtils.fromNbt(compound.getCompound("Form"));

            this.form = FormUtils.fromData(map);
        }
    }
}