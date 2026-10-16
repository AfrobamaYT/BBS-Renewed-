package mchorse.bbs_mod.forms.entities;

import net.minecraft.world.entity.Pose;

/**
 * The body states a replay records beside sneaking and sprinting: swimming, creative flight and
 * an elytra glide, with how far the body leans into a swim and how long a glide has gone. From
 * BBS FS e383f1930 (Wemppy4/bbs-fs, MIT); riding is this tree's own mount link and stays apart.
 */
public final class EntityState
{
    /** {@code Entity.FLAG_FALL_FLYING}, protected in vanilla. */
    public static final int FALL_FLYING_FLAG = 7;

    public static final String SWIMMING = "swimming", FLYING = "flying", GLIDING = "gliding", LEANING = "leaning", ROLL = "roll";

    private EntityState()
    {}

    /** A recorded state reads as on above zero, so an eased channel's undershoot stays off. */
    public static boolean isOn(double value)
    {
        return value > 0D;
    }

    /** The vanilla pose the states add up to; lying down beats standing tall, a crouch is what's left. */
    public static Pose pose(boolean gliding, boolean swimming, boolean sneaking)
    {
        return gliding ? Pose.FALL_FLYING : swimming ? Pose.SWIMMING : sneaking ? Pose.CROUCHING : Pose.STANDING;
    }
}
