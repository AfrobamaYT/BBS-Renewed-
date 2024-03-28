package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class StubEntity implements IEntity
{
    private World world;
    private int age;

    private Form form;
    private boolean sneaking;
    private boolean onGround = true;
    private float fallDistance;

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

    private Vec3d velocity = Vec3d.ZERO;

    private float[] extraVariables = new float[10];
    private float[] prevExtraVariables = new float[10];

    public StubEntity(World world)
    {
        this.world = world;
    }

    public StubEntity()
    {}

    @Override
    public World getWorld()
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
    public boolean isPunching()
    {
        return false;
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
    public double getFallDistance()
    {
        return this.fallDistance;
    }

    @Override
    public void setFallDistance(float fallDistance)
    {
        this.fallDistance = fallDistance;
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
    }

    @Override
    public double getY()
    {
        return this.prevY;
    }

    @Override
    public double getPrevY()
    {
        return this.y;
    }

    @Override
    public void setPrevY(double y)
    {
        this.prevY = y;
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
    }

    @Override
    public void setPosition(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }

    @Override
    public double getEyeHeight()
    {
        return 1.8F * 0.9F;
    }

    @Override
    public Vec3d getVelocity()
    {
        return this.velocity;
    }

    @Override
    public void setVelocity(float x, float y, float z)
    {
        this.velocity = new Vec3d(x, y, z);
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
    }

    @Override
    public float[] getExtraVariables()
    {
        return this.extraVariables;
    }

    @Override
    public float[] getPrettyExtraVariables()
    {
        return this.prevExtraVariables;
    }

    @Override
    public AABB getPickingHitbox()
    {
        float w = 0.6F;
        float h = 1.8F;

        return new AABB(
            this.getX() - w / 2, this.getY(), this.getZ() - w / 2,
            this.getX() + w / 2, this.getY() + h, this.getZ() + w / 2
        );
    }

    @Override
    public void update()
    {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;

        this.prevPrevBodyYaw = this.prevBodyYaw;

        this.prevYaw = this.yaw;
        this.prevHeadYaw = this.headYaw;
        this.prevPitch = this.pitch;
        this.prevBodyYaw = this.bodyYaw;

        for (int i = 0; i < this.extraVariables.length; i++)
        {
            this.prevExtraVariables[i] = this.extraVariables[i];
        }
    }
}