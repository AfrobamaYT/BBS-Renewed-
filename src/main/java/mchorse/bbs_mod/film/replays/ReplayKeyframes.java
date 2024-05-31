package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.interps.Interps;
import org.joml.Vector2d;

import java.util.Arrays;
import java.util.List;

public class ReplayKeyframes extends ValueGroup
{
    public static final String GROUP_POSITION = "position";
    public static final String GROUP_ROTATION = "rotation";
    public static final String GROUP_LEFT_STICK = "lstick";
    public static final String GROUP_RIGHT_STICK = "rstick";
    public static final String GROUP_TRIGGERS = "triggers";
    public static final String GROUP_EXTRA1 = "extra1";
    public static final String GROUP_EXTRA2 = "extra2";

    public static final List<String> CURATED_CHANNELS = Arrays.asList("x", "y", "z", "pitch", "yaw", "headYaw", "bodyYaw", "sneaking", "sprinting", "stick_lx", "stick_ly", "stick_rx", "stick_ry", "trigger_l", "trigger_r", "extra1_x", "extra1_y", "extra2_x", "extra2_y", "grounded", "damage", "vX", "vY", "vZ");

    public final KeyframeChannel x = new KeyframeChannel("x");
    public final KeyframeChannel y = new KeyframeChannel("y");
    public final KeyframeChannel z = new KeyframeChannel("z");

    public final KeyframeChannel vX = new KeyframeChannel("vX");
    public final KeyframeChannel vY = new KeyframeChannel("vY");
    public final KeyframeChannel vZ = new KeyframeChannel("vZ");

    public final KeyframeChannel yaw = new KeyframeChannel("yaw");
    public final KeyframeChannel pitch = new KeyframeChannel("pitch");
    public final KeyframeChannel headYaw = new KeyframeChannel("headYaw");
    public final KeyframeChannel bodyYaw = new KeyframeChannel("bodyYaw");

    public final KeyframeChannel sneaking = new KeyframeChannel("sneaking");
    public final KeyframeChannel sprinting = new KeyframeChannel("sprinting");
    public final KeyframeChannel grounded = new KeyframeChannel("grounded");
    public final KeyframeChannel fall = new KeyframeChannel("fall");
    public final KeyframeChannel damage = new KeyframeChannel("damage");

    public final KeyframeChannel stickLeftX = new KeyframeChannel("stick_lx");
    public final KeyframeChannel stickLeftY = new KeyframeChannel("stick_ly");
    public final KeyframeChannel stickRightX = new KeyframeChannel("stick_rx");
    public final KeyframeChannel stickRightY = new KeyframeChannel("stick_ry");
    public final KeyframeChannel triggerLeft = new KeyframeChannel("trigger_l");
    public final KeyframeChannel triggerRight = new KeyframeChannel("trigger_r");

    /* Miscellaneous animatable keyframe channels */
    public final KeyframeChannel extra1X = new KeyframeChannel("extra1_x");
    public final KeyframeChannel extra1Y = new KeyframeChannel("extra1_y");
    public final KeyframeChannel extra2X = new KeyframeChannel("extra2_x");
    public final KeyframeChannel extra2Y = new KeyframeChannel("extra2_y");

    public ReplayKeyframes(String id)
    {
        super(id);

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.vX);
        this.add(this.vY);
        this.add(this.vZ);
        this.add(this.yaw);
        this.add(this.pitch);
        this.add(this.headYaw);
        this.add(this.bodyYaw);
        this.add(this.sneaking);
        this.add(this.sprinting);
        this.add(this.grounded);
        this.add(this.fall);
        this.add(this.damage);
        this.add(this.stickLeftX);
        this.add(this.stickLeftY);
        this.add(this.stickRightX);
        this.add(this.stickRightY);
        this.add(this.triggerLeft);
        this.add(this.triggerRight);
        this.add(this.extra1X);
        this.add(this.extra1Y);
        this.add(this.extra2X);
        this.add(this.extra2Y);
    }

    public void record(int tick, IEntity entity, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || groups.contains(GROUP_POSITION);
        boolean rotation = empty || groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || groups.contains(GROUP_EXTRA2);

        /* Position and rotation */
        if (position)
        {
            this.x.insert(tick, entity.getX());
            this.y.insert(tick, entity.getY());
            this.z.insert(tick, entity.getZ());

            this.vX.insert(tick, entity.getVelocity().x);
            this.vY.insert(tick, entity.getVelocity().y);
            this.vZ.insert(tick, entity.getVelocity().z);

            this.fall.insert(tick, entity.getFallDistance());
        }

        this.sneaking.insert(tick, entity.isSneaking() ? 1D : 0D);
        this.sprinting.insert(tick, entity.isSprinting() ? 1D : 0D);
        this.grounded.insert(tick, entity.isOnGround() ? 1D : 0D);
        this.damage.insert(tick, entity.getHurtTimer());

        if (rotation)
        {
            this.yaw.insert(tick, entity.getYaw());
            this.pitch.insert(tick, entity.getPitch());
            this.headYaw.insert(tick, entity.getHeadYaw());
            this.bodyYaw.insert(tick, entity.getBodyYaw());
        }

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            this.stickLeftX.insert(tick, sticks[0]);
            this.stickLeftY.insert(tick, sticks[1]);
        }

        if (rightStick)
        {
            this.stickRightX.insert(tick, sticks[2]);
            this.stickRightY.insert(tick, sticks[3]);
        }

        if (triggers)
        {
            this.triggerLeft.insert(tick, sticks[4]);
            this.triggerRight.insert(tick, sticks[5]);
        }

        if (extra1)
        {
            this.extra1X.insert(tick, sticks[6]);
            this.extra1Y.insert(tick, sticks[7]);
        }

        if (extra2)
        {
            this.extra2X.insert(tick, sticks[8]);
            this.extra2Y.insert(tick, sticks[9]);
        }
    }

    /**
     * Apply a frame at given tick on the given entity.
     */
    public void apply(int tick, IEntity entity, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || !groups.contains(GROUP_POSITION);
        boolean rotation = empty || !groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || !groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || !groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || !groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || !groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || !groups.contains(GROUP_EXTRA2);

        if (position)
        {
            entity.setVelocity((float) this.vX.interpolate(tick), (float) this.vY.interpolate(tick), (float) this.vZ.interpolate(tick));
            entity.setFallDistance((float) this.fall.interpolate(tick));

            Pair<Keyframe, Keyframe> x = this.x.findSegment(tick);
            Vector2d xx = this.getPrev(x, tick, entity.getPrevX());
            Pair<Keyframe, Keyframe> y = this.y.findSegment(tick);
            Vector2d yy = this.getPrev(y, tick, entity.getPrevY());
            Pair<Keyframe, Keyframe> z = this.z.findSegment(tick);
            Vector2d zz = this.getPrev(z, tick, entity.getPrevZ());

            entity.setPosition(xx.x, yy.x, zz.x);
            entity.setPrevX(xx.y);
            entity.setPrevY(yy.y);
            entity.setPrevZ(zz.y);
        }

        if (rotation)
        {
            Pair<Keyframe, Keyframe> yaw = this.yaw.findSegment(tick);
            Vector2d yyaw = this.getPrev(yaw, tick, entity.getPrevYaw());
            Pair<Keyframe, Keyframe> pitch = this.pitch.findSegment(tick);
            Vector2d ppitch = this.getPrev(pitch, tick, entity.getPrevPitch());
            Pair<Keyframe, Keyframe> headYaw = this.headYaw.findSegment(tick);
            Vector2d hheadYaw = this.getPrev(headYaw, tick, entity.getPrevHeadYaw());
            Pair<Keyframe, Keyframe> bodyYaw = this.bodyYaw.findSegment(tick);
            Vector2d bbodyYaw = this.getPrev(bodyYaw, tick, entity.getPrevBodyYaw());

            entity.setYaw((float) yyaw.x);
            entity.setPitch((float) ppitch.x);
            entity.setHeadYaw((float) hheadYaw.x);
            entity.setBodyYaw((float) bbodyYaw.x);

            entity.setPrevYaw((float) yyaw.y);
            entity.setPrevPitch((float) ppitch.y);
            entity.setPrevHeadYaw((float) hheadYaw.y);
            entity.setPrevBodyYaw((float) bbodyYaw.y);
        }

        /* Motion and fall distance */
        entity.setSneaking(this.sneaking.interpolate(tick) != 0D);
        entity.setSprinting(this.sprinting.interpolate(tick) != 0D);
        entity.setOnGround(this.grounded.interpolate(tick) != 0D);
        entity.setHurtTimer((int) this.damage.interpolate(tick));

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            sticks[0] = (float) this.stickLeftX.interpolate(tick);
            sticks[1] = (float) this.stickLeftY.interpolate(tick);
        }

        if (rightStick)
        {
            sticks[2] = (float) this.stickRightX.interpolate(tick);
            sticks[3] = (float) this.stickRightY.interpolate(tick);
        }

        if (triggers)
        {
            sticks[4] = (float) this.triggerLeft.interpolate(tick);
            sticks[5] = (float) this.triggerRight.interpolate(tick);
        }

        if (extra1)
        {
            sticks[6] = (float) this.extra1X.interpolate(tick);
            sticks[7] = (float) this.extra1Y.interpolate(tick);
        }

        if (extra2)
        {
            sticks[8] = (float) this.extra2X.interpolate(tick);
            sticks[9] = (float) this.extra2Y.interpolate(tick);
        }
    }

    /**
     * Force teleportation for the previous keyframe being constant
     */
    private Vector2d getPrev(Pair<Keyframe, Keyframe> frame, int tick, double prev)
    {
        if (frame != null && frame.b != null)
        {
            /* Special case for when there is no keyframe afterwards */
            if (frame.a == frame.b && frame.b.next == frame.b && frame.a.prev != frame.a)
            {
                if (frame.a.prev.getInterpolation() == Interps.CONST && frame.a.getTick() == tick)
                {
                    return new Vector2d(frame.a.getValue(), frame.a.getValue());
                }
            }

            if (frame.a.getInterpolation() == Interps.CONST && frame.b.getTick() == tick)
            {
                return new Vector2d(frame.b.getValue(), frame.b.getValue());
            }
        }

        return new Vector2d(KeyframeChannel.compute(frame, tick), prev);
    }
}