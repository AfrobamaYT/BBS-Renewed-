package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Arm;
import net.minecraft.world.World;

import java.util.Collections;

public class ActorEntity extends LivingEntity
{
    public static DefaultAttributeContainer.Builder createActorAttributes()
    {
        return LivingEntity.createLivingAttributes()
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1D)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1D)
            .add(EntityAttributes.GENERIC_ATTACK_SPEED)
            .add(EntityAttributes.GENERIC_LUCK);
    }

    private boolean despawn;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);

        ModelForm model = new ModelForm();

        model.model.set("player/steve");
        this.form = model;
    }

    public MCEntity getEntity()
    {
        return this.entity;
    }

    public Form getForm()
    {
        return this.form;
    }

    @Override
    public Iterable<ItemStack> getArmorItems()
    {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {}

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }

    @Override
    public void tick()
    {
        super.tick();

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
