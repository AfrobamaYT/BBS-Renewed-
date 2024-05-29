package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
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

        ItemStack main = entity.getEquipmentStack(EquipmentSlot.MAINHAND);
        ItemStack offhand = entity.getEquipmentStack(EquipmentSlot.OFFHAND);

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

        float coefficient = 1F;

        if (isRolling)
        {
            coefficient = (float) (entity.getVelocity().lengthSquared() / 2D);
            coefficient = Math.min(1F, coefficient * coefficient * coefficient);
        }

        ModelGroup leftArm = null;
        ModelGroup rightArm = null;
        ModelGroup torso = null;

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

                    group.current.rotate.x = MathHelper.lerp(leaningPitch, 0F, newPitch);

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
            else if (group.id.equals("right_arm"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient);

                if (!main.isEmpty())
                {
                    group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                }

                rightArm = group;
            }
            else if (group.id.equals("left_arm"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient);

                if (!offhand.isEmpty())
                {
                    group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                }

                leftArm = group;
            }
            else if (group.id.equals("torso"))
            {
                torso = group;
            }
            else if (group.id.equals("right_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient);
            }
            else if (group.id.equals("left_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient);
            }
        }

        if (handSwingProgress > 0F && torso != null && leftArm != null && rightArm != null)
        {
            ModelGroup group;
            float swingFactor = handSwingProgress;

            torso.current.rotate.y = -MathUtils.toDeg(MathHelper.sin(MathHelper.sqrt(swingFactor) * MathUtils.PI * 2F) * 0.2F);

            rightArm.current.translate.z = MathHelper.sin(MathUtils.toRad(torso.current.rotate.y)) * 5F / 16F;
            rightArm.current.translate.x -= MathHelper.cos(MathUtils.toRad(torso.current.rotate.y)) * 5F / 16F;
            leftArm.current.translate.z = -MathHelper.sin(MathUtils.toRad(torso.current.rotate.y)) * 5F / 16F;
            leftArm.current.translate.x -= MathHelper.cos(MathUtils.toRad(torso.current.rotate.y)) * 5F / 16F;

            group = rightArm;
            group.current.rotate.y += torso.current.rotate.y;
            group = leftArm;
            group.current.rotate.y += torso.current.rotate.y;
            group = leftArm;
            group.current.rotate.x += torso.current.rotate.y;

            swingFactor = 1F - handSwingProgress;
            swingFactor *= swingFactor;
            swingFactor *= swingFactor;
            swingFactor = 1F - swingFactor;

            float headPitch = 0F;
            float swing1 = MathHelper.sin(swingFactor * MathUtils.PI);
            float swign2 = MathHelper.sin(handSwingProgress * MathUtils.PI) * -(headPitch - 0.7F) * 0.75F;
            rightArm.current.rotate.x = group.current.rotate.x + MathUtils.toDeg( swing1 * 1.2F + swign2);
            rightArm.current.rotate.y += torso.current.rotate.y * 2F;
            rightArm.current.rotate.z += MathUtils.toDeg(MathHelper.sin(handSwingProgress * MathUtils.PI) * -0.4F);
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
