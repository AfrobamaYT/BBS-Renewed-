package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.MathUtils;

/**
 * A model's bone spoken in vanilla's terms: radians, vanilla's own directions. Cubic and BOBJ rigs
 * disagree with vanilla and with each other about units and which way each axis turns, so a pose
 * lifted from vanilla is written once against this (BBS FS f0668c423, Wemppy4/bbs-fs, MIT).
 */
public interface VanillaBone
{
    public float pitch();

    public void pitch(float pitch);

    public float yaw();

    public void yaw(float yaw);

    public float roll();

    public void roll(float roll);

    /** A cubic bone: degrees, x = -pitch, y = -yaw, z = +roll. */
    public static VanillaBone of(ModelGroup group)
    {
        return group == null ? null : new VanillaBone()
        {
            public float pitch() { return -MathUtils.toRad(group.current.rotate.x); }
            public void pitch(float pitch) { group.current.rotate.x = -MathUtils.toDeg(pitch); }
            public float yaw() { return -MathUtils.toRad(group.current.rotate.y); }
            public void yaw(float yaw) { group.current.rotate.y = -MathUtils.toDeg(yaw); }
            public float roll() { return MathUtils.toRad(group.current.rotate.z); }
            public void roll(float roll) { group.current.rotate.z = MathUtils.toDeg(roll); }
        };
    }

    /** A BOBJ bone: radians already, x = -pitch, y = +yaw, z = -roll. */
    public static VanillaBone of(BOBJBone bone)
    {
        return bone == null ? null : new VanillaBone()
        {
            public float pitch() { return -bone.transform.rotate.x; }
            public void pitch(float pitch) { bone.transform.rotate.x = -pitch; }
            public float yaw() { return bone.transform.rotate.y; }
            public void yaw(float yaw) { bone.transform.rotate.y = yaw; }
            public float roll() { return -bone.transform.rotate.z; }
            public void roll(float roll) { bone.transform.rotate.z = -roll; }
        };
    }
}
