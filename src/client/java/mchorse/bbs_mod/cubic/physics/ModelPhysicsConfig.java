package mchorse.bbs_mod.cubic.physics;

import java.util.Map;

public record ModelPhysicsConfig(Map<String, Bone> bones)
{
    public record Bone(String end, String targetBone, float gravity, float damping, int iterations, boolean relativeGravity, float relativeGravityRotateX, float relativeGravityRotateY, float relativeGravityRotateZ, boolean collisions, float radius)
    {
        public Bone(String end, String targetBone, float gravity, float damping, int iterations, boolean collisions, float radius)
        {
            this(end, targetBone, gravity, damping, iterations, false, 0F, 0F, 0F, collisions, radius);
        }

        public boolean hasRelativeGravityRotation()
        {
            return this.relativeGravityRotateX != 0F || this.relativeGravityRotateY != 0F || this.relativeGravityRotateZ != 0F;
        }
    }
}
