package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Interface that provides access to an "Entity" within forms for rendering
 * and updating.
 */
public interface IEntity
{
    public void setWorld(Level world);

    public Level getWorld();

    public Form getForm();

    public void setForm(Form form);

    public ItemStack getEquipmentStack(EquipmentSlot slot);

    public void setEquipmentStack(EquipmentSlot slot, ItemStack stack);

    public int getSelectedSlot();

    public boolean isSneaking();

    public void setSneaking(boolean sneaking);

    public boolean isSprinting();

    public void setSprinting(boolean sprinting);

    public boolean isOnGround();

    public void setOnGround(boolean ground);

    public void swingArm();

    public float getHandSwingProgress(float tickDelta);

    public int getAge();

    public void setAge(int ticks);

    public float getFallDistance();

    public void setFallDistance(float fallDistance);

    public int getHurtTimer();

    public void setHurtTimer(int hurtTimer);

    public double getX();

    public double getPrevX();

    public void setPrevX(double x);

    public double getY();

    public double getPrevY();

    public void setPrevY(double y);

    public double getZ();

    public double getPrevZ();

    public void setPrevZ(double z);

    public void setPosition(double x, double y, double z);

    public double getEyeHeight();

    public Vec3 getVelocity();

    public void setVelocity(float x, float y, float z);

    public float getYaw();

    public float getPrevYaw();

    public void setYaw(float yaw);

    public void setPrevYaw(float prevYaw);

    public float getHeadYaw();

    public float getPrevHeadYaw();

    public void setHeadYaw(float headYaw);

    public void setPrevHeadYaw(float prevHeadYaw);

    public float getPitch();

    public float getPrevPitch();

    public void setPitch(float pitch);

    public void setPrevPitch(float prevPitch);

    public float getBodyYaw();

    public float getPrevBodyYaw();

    public float getPrevPrevBodyYaw();

    public void setBodyYaw(float bodyYaw);

    public void setPrevBodyYaw(float prevBodyYaw);

    public void setPrevPrevBodyYaw(float prevPrevBodyYaw);

    public float[] getExtraVariables();

    public float[] getPrevExtraVariables();

    public AABB getPickingHitbox();

    public void update();

    /** Gegenstand im hotbar-slot, leer wenn die entity keine hotbar hat.
     *
     *  MERGE 2026-07-27: aus dem CML-fork — TrackerClientClip (first-person hand) liest ihn. */
    public default ItemStack getHotbarItem(int slot)
    {
        return ItemStack.EMPTY;
    }

    public default void copy(IEntity entity)
    {
        this.setForm(entity.getForm());

        this.setSneaking(entity.isSneaking());
        this.setSprinting(entity.isSprinting());
        this.setOnGround(entity.isOnGround());
        this.setFallDistance(entity.getFallDistance());
        this.setHurtTimer(entity.getHurtTimer());

        this.setPrevX(entity.getPrevX());
        this.setPrevY(entity.getPrevY());
        this.setPrevZ(entity.getPrevZ());
        this.setPosition(entity.getX(), entity.getY(), entity.getZ());

        this.setPrevYaw(entity.getPrevYaw());
        this.setPrevHeadYaw(entity.getPrevHeadYaw());
        this.setPrevPitch(entity.getPrevPitch());
        this.setPrevBodyYaw(entity.getPrevBodyYaw());
        this.setPrevPrevBodyYaw(entity.getPrevPrevBodyYaw());

        this.setYaw(entity.getYaw());
        this.setHeadYaw(entity.getHeadYaw());
        this.setPitch(entity.getPitch());
        this.setBodyYaw(entity.getBodyYaw());

        this.setVelocity((float) entity.getVelocity().x, (float) entity.getVelocity().y, (float) entity.getVelocity().z);

        this.setShowHand(entity.getShowHand());
        this.setShowHud(entity.getShowHud());

        float[] extraVariables = this.getExtraVariables();
        float[] prevExtraVariables = this.getPrevExtraVariables();

        for (int i = 0; i < extraVariables.length; i++)
        {
            extraVariables[i] = entity.getExtraVariables()[i];
            prevExtraVariables[i] = entity.getPrevExtraVariables()[i];
        }
    }

    public WalkAnimationState getLimbAnimator();

    public float getLimbPos(float tickDelta);

    public float getLimbSpeed(float tickDelta);

    /* Swimming */

    public float getLeaningPitch(float tickDelta);

    public boolean isTouchingWater();

    public Pose getEntityPose();

    public int getRoll();

    public boolean isFallFlying();

    /* Recorded body states (BBS FS e383f1930): swimming, creative flight, an elytra glide, the lean
     * into a swim and the glide's roll; replays write them down and hand them back. */
    public boolean isSwimming();

    public void setSwimming(boolean swimming);

    public boolean isFlying();

    public void setFlying(boolean flying);

    public void setFallFlying(boolean fallFlying);

    public void setLeaningPitch(float leaningPitch);

    public void setRoll(int roll);

    public Vec3 getRotationVec(float transition);

    public Vec3 lerpVelocity(float transition);

    public boolean isUsingRiptide();

    public default float getHealth()
    {
        return 20F;
    }

    public default int getHunger()
    {
        return 20;
    }

    public default boolean getShowHand()
    {
        return true;
    }

    public default void setShowHand(boolean show) {}

    public default boolean getShowHud()
    {
        return true;
    }

    public default void setShowHud(boolean show) {}

    public default void setHealth(float health) {}

    public default void setHunger(int hunger) {}

    public default void setHotbarItem(int slot, ItemStack stack) {}

    public default void setSelectedSlot(int slot) {}

    public default int getDeathTime()
    {
        return 0;
    }

    public default void setDeathTime(int deathTime) {}

    public default boolean isUsingItem()
    {
        return false;
    }

    public default void setUsingItem(boolean usingItem) {}

    public default int getItemUseTimeLeft()
    {
        return 0;
    }

    public default void setItemUseTimeLeft(int itemUseTimeLeft) {}

    public default int getFireTicks()
    {
        return 0;
    }

    public default void setFireTicks(int fireTicks) {}

    public default boolean isParticlesEnabled()
    {
        return true;
    }

    public default void setParticlesEnabled(boolean particlesEnabled) {}

    public default InteractionHand getActiveHand()
    {
        return InteractionHand.MAIN_HAND;
    }

    public default void setActiveHand(InteractionHand hand) {}

    public default IEntity getMountTarget()
    {
        return null;
    }

    public default void setMountTarget(IEntity mountTarget) {}

    public default IEntity getRiderTarget()
    {
        return null;
    }

    public default void setRiderTarget(IEntity riderTarget) {}

    public default boolean isSitting()
    {
        return false;
    }

    public default void setSitting(boolean sitting) {}
}