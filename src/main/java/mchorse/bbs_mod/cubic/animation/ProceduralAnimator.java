package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public class ProceduralAnimator implements IAnimator
{
    @Override
    public List<String> getActions()
    {
        return Collections.singletonList("idle");
    }

    @Override
    public void setup(CubicModel model, ActionsConfig actionsConfig, boolean fade)
    {}

    @Override
    public void applyActions(IEntity entity, Model model, float transition)
    {
        if (entity == null)
        {
            return;
        }

        boolean isRolling = entity.getRoll() > 4;
        boolean isInSwimmingPose = entity.getEntityPose() == EntityPose.SWIMMING;

        /* Common variables */
        float handSwingProgress = entity.getHandSwingProgress(transition);
        float age = entity.getAge() + transition;
        float bodyYaw = Interpolations.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition);
        float headYaw = Interpolations.lerp(entity.getPrevHeadYaw(), entity.getHeadYaw(), transition);
        float yaw = headYaw - bodyYaw;
        float pitch = Interpolations.lerp(entity.getPrevPitch(), entity.getPitch(), transition);
        float limbSpeed = entity.getLimbSpeed(transition);
        float limbPhase = entity.getLimbPos(transition);
        float leaningPitch = entity.getLeaningPitch(transition);

        float coefficient = 1.0F;

        if (isRolling)
        {
            coefficient = (float) entity.getVelocity().lengthSquared();
            coefficient /= 0.2F;
            coefficient *= coefficient * coefficient;
        }

        if (coefficient < 1.0F)
        {
            coefficient = 1.0F;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            if (group.id.equals("anchor"))
            {
                if (entity.isUsingRiptide())
                {
                    group.current.rotate.x = -90.0F - pitch;
                    group.current.rotate2.y = age * -75.0F;
                }

                if (entity.isFallFlying())
                {
                    float roll = entity.getRoll() + transition;
                    float riptide = MathHelper.clamp(roll * roll / 100F, 0F, 1F);

                    if (!entity.isUsingRiptide())
                    {
                        group.current.rotate.x = riptide * (-90 - pitch);
                    }

                    Vec3d look = entity.getRotationVec(transition);
                    Vec3d velocity = entity.lerpVelocity(transition);
                    double vl = velocity.horizontalLengthSquared();
                    double ll = look.horizontalLengthSquared();

                    if (vl > 0 && ll > 0)
                    {
                        double m = (velocity.x * look.x + velocity.z * look.z) / Math.sqrt(vl * ll);
                        double n = velocity.x * look.z - velocity.z * look.x;

                        group.current.rotate.y = MathUtils.toDeg((float)(Math.signum(n) * Math.acos(m)));
                    }
                }
                else if (leaningPitch > 0F)
                {
                    float newPitch = entity.isTouchingWater() ? -90F - pitch : -90F;
                    float finalPitch = MathHelper.lerp(leaningPitch, 0F, newPitch);

                    group.current.rotate.x = finalPitch;

                    if (entity.getEntityPose() == EntityPose.SWIMMING)
                    {
                        group.current.translate.y -= 0.5F * 16F;
                        group.current.translate.z += 0.3F * 16F;
                    }
                }
            }
            else if (group.id.equals("head"))
            {
                group.current.rotate.y = -yaw;

                if (isRolling)
                {
                    group.current.rotate.x = 45;
                }
                else if (leaningPitch > 0F)
                {
                    group.current.rotate.x = this.lerpAngle(leaningPitch, group.current.rotate.x, isInSwimmingPose ? 45 : -pitch);
                }
                else
                {
                    group.current.rotate.x = -pitch;
                }
            }
            else if (group.id.equals("left_arm"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient);

                if (handSwingProgress > 0F)
                {
                    float swing = handSwingProgress;
                    float bodyY = MathHelper.sin(MathHelper.sqrt(swing) * MathUtils.PI * 2F) * 0.2F;

                    swing = 1.0F - swing;
                    swing = swing * swing * swing;
                    swing = 1.0F - swing;

                    float sinSwing = MathHelper.sin(swing * MathUtils.PI);
                    float sinSwing2 = MathHelper.sin(handSwingProgress * MathUtils.PI) * -(0.0F - 0.7F) * 0.75F;
                    float factor = 1F;

                    group.current.rotate.x = group.current.rotate.x + MathUtils.toDeg((sinSwing * 1.2F + sinSwing2));
                    group.current.rotate.y -= MathUtils.toDeg(bodyY * 2.0F * factor);
                    group.current.rotate.z -= MathUtils.toDeg(MathHelper.sin(handSwingProgress * MathUtils.PI) * -0.4F * factor);
                }
            }
            else if (group.id.equals("right_arm"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient);
            }
            else if (group.id.equals("body"))
            {

            }
            else if (group.id.equals("left_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient);
            }
            else if (group.id.equals("right_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient);
            }
        }
    }

    protected float lerpAngle(float a, float b, float magnitude)
    {
        float factor = (magnitude - b) % (360);

        if (factor < -180) factor += 360;
        if (factor >= 180) factor -= 360;

        return b + a * factor;
    }

    @Override
    public void update(IEntity entity)
    {}
}
