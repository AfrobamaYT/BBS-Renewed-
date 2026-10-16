package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class StubEntity implements IEntity
{
    private boolean swimming, flying, fallFlying;
    private float leaningPitch;
    private int roll;

    private Level world;
    private int age;

    private Form form;
    private boolean sneaking;
    private boolean sprinting;
    private boolean onGround = true;
    private float fallDistance;
    private int hurtTimer;
    private int deathTime;
    private boolean usingItem;
    private int itemUseTimeLeft;
    private int fireTicks;
    private boolean particlesEnabled = true;
    private InteractionHand activeHand = InteractionHand.MAIN_HAND;

    private double prevX;
    private double prevY;
    private double prevZ;
    private double x;
    private double y;
    private double z;

    private float prevYaw;
    private float prevHeadYaw;
    private float prevPitch;
    private float prevBodyYaw;
    private float prevPrevBodyYaw;

    private float yaw;
    private float headYaw;
    private float pitch;
    private float bodyYaw;

    private int armSwing;

    private Vec3 velocity = Vec3.ZERO;

    private float[] extraVariables = new float[10];
    private float[] prevExtraVariables = new float[10];
    private boolean externalPrevPosition;
    private boolean externalPrevRotation;

    private WalkAnimationState limbAnimator = new WalkAnimationState();
    private final Map<EquipmentSlot, ItemStack> items = new HashMap<>();
    private IEntity mountTarget;
    private IEntity riderTarget;
    private boolean sitting;

    /* Hotbar / status state (afrobama keyframe channels) */
    private final ItemStack[] hotbarItems = new ItemStack[9];
    private int selectedSlot;
    private float health = 20F;
    private int hunger = 20;
    private boolean showHand = true;
    private boolean showHud = true;

    public StubEntity(Level world)
    {
        this.world = world;

        for (EquipmentSlot value : EquipmentSlot.values())
        {
            this.items.put(value, ItemStack.EMPTY);
        }

        Arrays.fill(this.hotbarItems, ItemStack.EMPTY);
    }

    public StubEntity()
    {
        for (EquipmentSlot value : EquipmentSlot.values())
        {
            this.items.put(value, ItemStack.EMPTY);
        }

        Arrays.fill(this.hotbarItems, ItemStack.EMPTY);
    }

    @Override
    public void setWorld(Level world)
    {
        this.world = world;
    }

    @Override
    public Level getWorld()
    {
        return this.world;
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        this.form = form;
    }

    @Override
    public ItemStack getEquipmentStack(EquipmentSlot slot)
    {
        return this.items.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void setEquipmentStack(EquipmentSlot slot, ItemStack stack)
    {
        if (stack == null)
        {
            stack = ItemStack.EMPTY;
        }

        this.items.put(slot, stack);
    }

    @Override
    public int getSelectedSlot()
    {
        return this.selectedSlot;
    }

    @Override
    public void setSelectedSlot(int slot)
    {
        this.selectedSlot = Math.max(0, Math.min(8, slot));
    }

    @Override
    public ItemStack getHotbarItem(int slot)
    {
        if (slot < 0 || slot >= 9)
        {
            return ItemStack.EMPTY;
        }

        ItemStack item = this.hotbarItems[slot];

        return item != null ? item : ItemStack.EMPTY;
    }

    @Override
    public void setHotbarItem(int slot, ItemStack stack)
    {
        if (slot < 0 || slot >= 9)
        {
            return;
        }

        this.hotbarItems[slot] = stack != null ? stack : ItemStack.EMPTY;
    }

    @Override
    public float getHealth()
    {
        return this.health;
    }

    @Override
    public void setHealth(float health)
    {
        this.health = health;
    }

    @Override
    public int getHunger()
    {
        return this.hunger;
    }

    @Override
    public void setHunger(int hunger)
    {
        this.hunger = hunger;
    }

    @Override
    public boolean getShowHand()
    {
        return this.showHand;
    }

    @Override
    public void setShowHand(boolean show)
    {
        this.showHand = show;
    }

    @Override
    public boolean getShowHud()
    {
        return this.showHud;
    }

    @Override
    public void setShowHud(boolean show)
    {
        this.showHud = show;
    }

    @Override
    public boolean isSneaking()
    {
        return this.sneaking;
    }

    @Override
    public void setSneaking(boolean sneaking)
    {
        this.sneaking = sneaking;
    }

    @Override
    public boolean isSprinting()
    {
        return this.sprinting;
    }

    @Override
    public void setSprinting(boolean sprinting)
    {
        this.sprinting = sprinting;
    }

    @Override
    public boolean isOnGround()
    {
        return this.onGround;
    }

    @Override
    public void setOnGround(boolean ground)
    {
        this.onGround = ground;
    }

    @Override
    public void swingArm()
    {
        this.armSwing = 6;
    }

    @Override
    public float getHandSwingProgress(float tickDelta)
    {
        return this.armSwing <= 0 ? 0F : 1F - (this.armSwing - tickDelta) / 6F;
    }

    @Override
    public int getAge()
    {
        return this.age;
    }

    @Override
    public void setAge(int ticks)
    {
        this.age = ticks;
    }

    @Override
    public float getFallDistance()
    {
        return this.fallDistance;
    }

    @Override
    public void setFallDistance(float fallDistance)
    {
        this.fallDistance = fallDistance;
    }

    @Override
    public int getHurtTimer()
    {
        return this.hurtTimer;
    }

    @Override
    public void setHurtTimer(int hurtTimer)
    {
        this.hurtTimer = hurtTimer;
    }

    @Override
    public int getDeathTime()
    {
        return this.deathTime;
    }

    @Override
    public void setDeathTime(int deathTime)
    {
        this.deathTime = deathTime;
    }

    @Override
    public boolean isUsingItem()
    {
        return this.usingItem;
    }

    @Override
    public void setUsingItem(boolean usingItem)
    {
        this.usingItem = usingItem;
    }

    @Override
    public int getItemUseTimeLeft()
    {
        return this.itemUseTimeLeft;
    }

    @Override
    public void setItemUseTimeLeft(int itemUseTimeLeft)
    {
        this.itemUseTimeLeft = itemUseTimeLeft;
    }

    @Override
    public int getFireTicks()
    {
        return this.fireTicks;
    }

    @Override
    public void setFireTicks(int fireTicks)
    {
        this.fireTicks = fireTicks;
    }

    @Override
    public boolean isParticlesEnabled()
    {
        return this.particlesEnabled;
    }

    @Override
    public void setParticlesEnabled(boolean particlesEnabled)
    {
        this.particlesEnabled = particlesEnabled;
    }

    @Override
    public InteractionHand getActiveHand()
    {
        return this.activeHand;
    }

    @Override
    public void setActiveHand(InteractionHand hand)
    {
        this.activeHand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    @Override
    public double getX()
    {
        return this.x;
    }

    @Override
    public double getPrevX()
    {
        return this.prevX;
    }

    @Override
    public void setPrevX(double x)
    {
        this.prevX = x;
        this.externalPrevPosition = true;
    }

    @Override
    public double getY()
    {
        return this.y;
    }

    @Override
    public double getPrevY()
    {
        return this.prevY;
    }

    @Override
    public void setPrevY(double y)
    {
        this.prevY = y;
        this.externalPrevPosition = true;
    }

    @Override
    public double getZ()
    {
        return this.z;
    }

    @Override
    public double getPrevZ()
    {
        return this.prevZ;
    }

    @Override
    public void setPrevZ(double z)
    {
        this.prevZ = z;
        this.externalPrevPosition = true;
    }

    @Override
    public void setPosition(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public double getEyeHeight()
    {
        return 1.8F * 0.9F;
    }

    @Override
    public Vec3 getVelocity()
    {
        return this.velocity;
    }

    @Override
    public void setVelocity(float x, float y, float z)
    {
        this.velocity = new Vec3(x, y, z);
    }

    @Override
    public float getYaw()
    {
        return this.yaw;
    }

    @Override
    public float getPrevYaw()
    {
        return this.prevYaw;
    }

    @Override
    public void setYaw(float yaw)
    {
        this.yaw = yaw;
    }

    @Override
    public void setPrevYaw(float prevYaw)
    {
        this.prevYaw = prevYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float getHeadYaw()
    {
        return this.headYaw;
    }

    @Override
    public float getPrevHeadYaw()
    {
        return this.prevHeadYaw;
    }

    @Override
    public void setHeadYaw(float headYaw)
    {
        this.headYaw = headYaw;
    }

    @Override
    public void setPrevHeadYaw(float prevHeadYaw)
    {
        this.prevHeadYaw = prevHeadYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float getPitch()
    {
        return this.pitch;
    }

    @Override
    public float getPrevPitch()
    {
        return this.prevPitch;
    }

    @Override
    public void setPitch(float pitch)
    {
        this.pitch = pitch;
    }

    @Override
    public void setPrevPitch(float prevPitch)
    {
        this.prevPitch = prevPitch;
        this.externalPrevRotation = true;
    }

    @Override
    public float getBodyYaw()
    {
        return this.bodyYaw;
    }

    @Override
    public float getPrevBodyYaw()
    {
        return this.prevBodyYaw;
    }

    @Override
    public float getPrevPrevBodyYaw()
    {
        return this.prevPrevBodyYaw;
    }

    @Override
    public void setBodyYaw(float bodyYaw)
    {
        this.bodyYaw = bodyYaw;
    }

    @Override
    public void setPrevBodyYaw(float prevBodyYaw)
    {
        this.prevBodyYaw = prevBodyYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public void setPrevPrevBodyYaw(float prevPrevBodyYaw)
    {
        this.prevPrevBodyYaw = prevPrevBodyYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float[] getExtraVariables()
    {
        return this.extraVariables;
    }

    @Override
    public float[] getPrevExtraVariables()
    {
        return this.prevExtraVariables;
    }

    @Override
    public AABB getPickingHitbox()
    {
        Form form = this.getForm();
        float w = 0.6F;
        float h = 1.8F;

        if (form != null && form.hitbox.get())
        {
            w = form.hitboxWidth.get();
            h = form.hitboxHeight.get();
        }

        return new AABB(
            this.getX() - w / 2, this.getY(), this.getZ() - w / 2,
            w, h, w
        );
    }

    @Override
    public void update()
    {
        float delta = (float) Mth.length(this.x - this.prevX, 0D, this.z - this.prevZ);
        float speed = Math.min(delta * 4F, 1F);

        this.limbAnimator.update(speed, 0.4F, 1F);

        this.armSwing -= 1;
        this.age += 1;

        if (!this.externalPrevPosition)
        {
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
        }

        if (!this.externalPrevRotation && this.mountTarget == null)
        {
            this.prevPrevBodyYaw = this.prevBodyYaw;
            this.prevYaw = this.yaw;
            this.prevHeadYaw = this.headYaw;
            this.prevPitch = this.pitch;
            this.prevBodyYaw = this.bodyYaw;
        }

        this.externalPrevPosition = false;
        this.externalPrevRotation = false;

        for (int i = 0; i < this.extraVariables.length; i++)
        {
            this.prevExtraVariables[i] = this.extraVariables[i];
        }
    }

    @Override
    public WalkAnimationState getLimbAnimator()
    {
        return this.limbAnimator;
    }

    @Override
    public float getLimbPos(float tickDelta)
    {
        return this.limbAnimator.position(tickDelta);
    }

    @Override
    public float getLimbSpeed(float tickDelta)
    {
        return this.limbAnimator.speed(tickDelta);
    }

    @Override
    public float getLeaningPitch(float tickDelta)
    {
        return this.leaningPitch;
    }

    @Override
    public boolean isTouchingWater()
    {
        return false;
    }

    @Override
    public Pose getEntityPose()
    {
        return this.mountTarget != null || this.sitting ? Pose.SITTING : EntityState.pose(this.fallFlying, this.swimming, this.sneaking);
    }

    @Override
    public IEntity getMountTarget()
    {
        return this.mountTarget;
    }

    @Override
    public void setMountTarget(IEntity mountTarget)
    {
        this.mountTarget = mountTarget;
    }

    @Override
    public IEntity getRiderTarget()
    {
        return this.riderTarget;
    }

    @Override
    public void setRiderTarget(IEntity riderTarget)
    {
        this.riderTarget = riderTarget;
    }

    @Override
    public boolean isSitting()
    {
        return this.sitting;
    }

    @Override
    public void setSitting(boolean sitting)
    {
        this.sitting = sitting;
    }

    @Override
    public int getRoll()
    {
        return this.roll;
    }

    @Override
    public boolean isFallFlying()
    {
        return this.fallFlying;
    }

    /* Recorded body states (BBS FS e383f1930): the stub has no ticks, so it just holds them. */
    @Override public boolean isSwimming() { return this.swimming; }
    @Override public void setSwimming(boolean swimming) { this.swimming = swimming; }
    @Override public boolean isFlying() { return this.flying; }
    @Override public void setFlying(boolean flying) { this.flying = flying; }
    @Override public void setFallFlying(boolean fallFlying) { this.fallFlying = fallFlying; }
    @Override public void setLeaningPitch(float leaningPitch) { this.leaningPitch = leaningPitch; }
    @Override public void setRoll(int roll) { this.roll = roll; }

    @Override
    public Vec3 getRotationVec(float transition)
    {
        return Vec3.ZERO;
    }

    @Override
    public Vec3 lerpVelocity(float transition)
    {
        return Vec3.ZERO;
    }

    @Override
    public boolean isUsingRiptide()
    {
        return false;
    }
}
