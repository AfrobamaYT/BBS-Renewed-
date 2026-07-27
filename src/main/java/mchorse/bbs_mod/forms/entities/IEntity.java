package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Interface that provides access to an "Entity" within forms for rendering
 * and updating.
 */
public interface IEntity
{
    public void setWorld(World world);

    public World getWorld();

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

    public Vec3d getVelocity();

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

        /* afrobama 2026-07-27 — aus dem CML-fork (dort IEntity:243-244). Ohne diese zwei
         * zeilen faellt der wert beim kopieren zwischen entities wieder auf true zurueck. */
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

    public LimbAnimator getLimbAnimator();

    public float getLimbPos(float tickDelta);

    public float getLimbSpeed(float tickDelta);

    /* Swimming */

    public float getLeaningPitch(float tickDelta);

    public boolean isTouchingWater();

    public EntityPose getEntityPose();

    public int getRoll();

    public boolean isFallFlying();

    public Vec3d getRotationVec(float transition);

    public Vec3d lerpVelocity(float transition);

    public boolean isUsingRiptide();

    /* ⛔ MERGE 2026-07-27: vier default-getter aus dem CML-fork. TrackerClientClip
     * (first-person hand + HUD) und unsere autonomie-schicht lesen sie. */
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

    /* afrobama 2026-07-27 — setter aus dem CML-fork uebernommen (dort IEntity:62/69).
     * fs hat nur die getter, damit war der wert von aussen nie schreibbar: weder der
     * keyframe-kanal show_hand noch MCEntityShowHandMixin konnten ihn setzen. */
    public default void setShowHand(boolean show) {}

    public default boolean getShowHud()
    {
        return true;
    }

    public default void setShowHud(boolean show) {}

    /*
     * afrobama 2026-07-27 — restliche entity-zugriffe aus dem CML-fork, die
     * ReplayKeyframes fuer seine kanaele braucht (hotbar_slot_*, health, hunger,
     * death_time, using_item, item_use_time, fire, particles, active_hand, ridden).
     *
     * ⛔ ALLE als DEFAULT, nicht abstrakt. Im CML-fork sind mehrere davon abstrakt
     * (`public int getDeathTime();`) — uebernaehme man das 1:1, muesste jede
     * IEntity-implementierung in fs sie nachziehen, sonst kompiliert nichts mehr.
     * Die defaults liefern denselben wert wie ein nicht-gesetzter kanal, das
     * verhalten ohne keyframes bleibt also unveraendert.
     */
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

    public default Hand getActiveHand()
    {
        return Hand.MAIN_HAND;
    }

    public default void setActiveHand(Hand hand) {}

    public default IEntity getMountTarget()
    {
        return null;
    }
}