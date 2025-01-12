package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

public class GunProjectileEntity extends PersistentProjectileEntity
{
    private boolean despawn;
    private Form form;
    private IEntity entity = new StubEntity();

    public GunProjectileEntity(EntityType<? extends PersistentProjectileEntity> type, World world)
    {
        super(type, world, ItemStack.EMPTY);

        ModelForm modelForm = new ModelForm();

        modelForm.model.set("player/steve");
        modelForm.texture.set(Link.assets("models/player/steve/steve.png"));
        this.form = modelForm;
    }

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        this.form = form;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch)
    {}

    @Override
    public void tick()
    {
        super.tick();

        this.entity.update();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    public void checkDespawn()
    {
        super.checkDespawn();

        if (this.despawn)
        {
            this.discard();
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt)
    {
        super.readCustomDataFromNbt(nbt);

        this.despawn = nbt.getBoolean("despawn");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt)
    {
        super.writeCustomDataToNbt(nbt);

        nbt.putBoolean("despawn", true);
    }
}