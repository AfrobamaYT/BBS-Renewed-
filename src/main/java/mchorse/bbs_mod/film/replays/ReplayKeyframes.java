package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.generic.factories.KeyframeFactories;
import org.joml.Vector2d;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    public final GenericKeyframeChannel<Double> x = new GenericKeyframeChannel<>("x", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> y = new GenericKeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> z = new GenericKeyframeChannel<>("z", KeyframeFactories.DOUBLE);

    public final GenericKeyframeChannel<Double> vX = new GenericKeyframeChannel<>("vX", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> vY = new GenericKeyframeChannel<>("vY", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> vZ = new GenericKeyframeChannel<>("vZ", KeyframeFactories.DOUBLE);

    public final GenericKeyframeChannel<Double> yaw = new GenericKeyframeChannel<>("yaw", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> pitch = new GenericKeyframeChannel<>("pitch", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> headYaw = new GenericKeyframeChannel<>("headYaw", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> bodyYaw = new GenericKeyframeChannel<>("bodyYaw", KeyframeFactories.DOUBLE);

    public final GenericKeyframeChannel<Double> sneaking = new GenericKeyframeChannel<>("sneaking", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> sprinting = new GenericKeyframeChannel<>("sprinting", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> grounded = new GenericKeyframeChannel<>("grounded", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> fall = new GenericKeyframeChannel<>("fall", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> damage = new GenericKeyframeChannel<>("damage", KeyframeFactories.DOUBLE);

    public final GenericKeyframeChannel<Double> stickLeftX = new GenericKeyframeChannel<>("stick_lx", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> stickLeftY = new GenericKeyframeChannel<>("stick_ly", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> stickRightX = new GenericKeyframeChannel<>("stick_rx", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> stickRightY = new GenericKeyframeChannel<>("stick_ry", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> triggerLeft = new GenericKeyframeChannel<>("trigger_l", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> triggerRight = new GenericKeyframeChannel<>("trigger_r", KeyframeFactories.DOUBLE);

    /* Miscellaneous animatable keyframe channels */
    public final GenericKeyframeChannel<Double> extra1X = new GenericKeyframeChannel<>("extra1_x", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> extra1Y = new GenericKeyframeChannel<>("extra1_y", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> extra2X = new GenericKeyframeChannel<>("extra2_x", KeyframeFactories.DOUBLE);
    public final GenericKeyframeChannel<Double> extra2Y = new GenericKeyframeChannel<>("extra2_y", KeyframeFactories.DOUBLE);

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

            this.fall.insert(tick, (double) entity.getFallDistance());
        }

        this.sneaking.insert(tick, entity.isSneaking() ? 1D : 0D);
        this.sprinting.insert(tick, entity.isSprinting() ? 1D : 0D);
        this.grounded.insert(tick, entity.isOnGround() ? 1D : 0D);
        this.damage.insert(tick, (double) entity.getHurtTimer());

        if (rotation)
        {
            this.yaw.insert(tick, (double) entity.getYaw());
            this.pitch.insert(tick, (double) entity.getPitch());
            this.headYaw.insert(tick, (double) entity.getHeadYaw());
            this.bodyYaw.insert(tick, (double) entity.getBodyYaw());
        }

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            this.stickLeftX.insert(tick, (double) sticks[0]);
            this.stickLeftY.insert(tick, (double) sticks[1]);
        }

        if (rightStick)
        {
            this.stickRightX.insert(tick, (double) sticks[2]);
            this.stickRightY.insert(tick, (double) sticks[3]);
        }

        if (triggers)
        {
            this.triggerLeft.insert(tick, (double) sticks[4]);
            this.triggerRight.insert(tick, (double) sticks[5]);
        }

        if (extra1)
        {
            this.extra1X.insert(tick, (double) sticks[6]);
            this.extra1Y.insert(tick, (double) sticks[7]);
        }

        if (extra2)
        {
            this.extra2X.insert(tick, (double) sticks[8]);
            this.extra2Y.insert(tick, (double) sticks[9]);
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
            entity.setVelocity(this.vX.interpolate(tick).floatValue(), this.vY.interpolate(tick).floatValue(), this.vZ.interpolate(tick).floatValue());
            entity.setFallDistance(this.fall.interpolate(tick).floatValue());

            GenericKeyframeSegment<Double> x = this.x.findSegment(tick);
            Vector2d xx = this.getPrev(x, tick, entity.getPrevX());
            GenericKeyframeSegment<Double> y = this.y.findSegment(tick);
            Vector2d yy = this.getPrev(y, tick, entity.getPrevY());
            GenericKeyframeSegment<Double> z = this.z.findSegment(tick);
            Vector2d zz = this.getPrev(z, tick, entity.getPrevZ());

            entity.setPosition(xx.x, yy.x, zz.x);
            entity.setPrevX(xx.y);
            entity.setPrevY(yy.y);
            entity.setPrevZ(zz.y);
        }

        if (rotation)
        {
            GenericKeyframeSegment<Double> yaw = this.yaw.findSegment(tick);
            Vector2d yyaw = this.getPrev(yaw, tick, entity.getPrevYaw());
            GenericKeyframeSegment<Double> pitch = this.pitch.findSegment(tick);
            Vector2d ppitch = this.getPrev(pitch, tick, entity.getPrevPitch());
            GenericKeyframeSegment<Double> headYaw = this.headYaw.findSegment(tick);
            Vector2d hheadYaw = this.getPrev(headYaw, tick, entity.getPrevHeadYaw());
            GenericKeyframeSegment<Double> bodyYaw = this.bodyYaw.findSegment(tick);
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
        entity.setHurtTimer(this.damage.interpolate(tick).intValue());

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            sticks[0] = this.stickLeftX.interpolate(tick).floatValue();
            sticks[1] = this.stickLeftY.interpolate(tick).floatValue();
        }

        if (rightStick)
        {
            sticks[2] = this.stickRightX.interpolate(tick).floatValue();
            sticks[3] = this.stickRightY.interpolate(tick).floatValue();
        }

        if (triggers)
        {
            sticks[4] = this.triggerLeft.interpolate(tick).floatValue();
            sticks[5] = this.triggerRight.interpolate(tick).floatValue();
        }

        if (extra1)
        {
            sticks[6] = this.extra1X.interpolate(tick).floatValue();
            sticks[7] = this.extra1Y.interpolate(tick).floatValue();
        }

        if (extra2)
        {
            sticks[8] = this.extra2X.interpolate(tick).floatValue();
            sticks[9] = this.extra2Y.interpolate(tick).floatValue();
        }
    }

    /**
     * Force teleportation for the previous keyframe being constant
     */
    private Vector2d getPrev(GenericKeyframeSegment<Double> frame, int tick, double prev)
    {
        if (frame == null)
        {
            return new Vector2d(prev, prev);
        }

        if (frame != null && frame.b != null)
        {
            /* Special case for when there is no keyframe afterwards */
            if (Objects.equals(frame.a, frame.b) && Objects.equals(frame.postB, frame.b) && !Objects.equals(frame.preA, frame.a))
            {
                if (frame.preA.getInterpolation().getInterp() == Interpolations.CONST && frame.a.getTick() == tick)
                {
                    return new Vector2d(frame.a.getValue(), frame.a.getValue());
                }
            }

            if (frame.a.getInterpolation().getInterp() == Interpolations.CONST && frame.b.getTick() == tick)
            {
                return new Vector2d(frame.b.getValue(), frame.b.getValue());
            }
        }

        Double interpolated = frame.createInterpolated();

        return new Vector2d(interpolated == null ? prev : interpolated, prev);
    }
}