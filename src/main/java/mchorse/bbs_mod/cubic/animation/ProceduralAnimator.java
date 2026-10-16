package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import mchorse.bbs_mod.utils.interps.Lerps;
import java.util.Arrays;
import java.util.List;

public class ProceduralAnimator implements IAnimator
{
    public ActionPlayback basePre;
    public ActionPlayback basePost;

    private IModelInstance model;

    @Override
    public List<String> getActions()
    {
        return Arrays.asList("base_pre", "base_post");
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actions, boolean fade)
    {
        this.model = model;

        this.basePre = this.createAction(this.basePre, actions.getConfig("base_pre"), true);
        this.basePost = this.createAction(this.basePost, actions.getConfig("base_post"), true);
    }

    /**
     * Create an action with default priority
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping)
    {
        return this.createAction(old, config, looping, 1);
    }

    /**
     * Create an action playback based on given arguments. This method
     * is used for creating actions so it was easier to tell which
     * actions are missing. Besides that, you can pass an old action so
     * in form merging situation it wouldn't interrupt animation.
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping, int priority)
    {
        Animations animations = this.model == null ? null : this.model.getAnimations();

        if (animations == null)
        {
            return null;
        }

        Animation action = animations.get(config.name);

        /* If given action is missing, then omit creation of ActionPlayback */
        if (action == null)
        {
            return null;
        }

        /* If old is the same, then there is no point creating a new one */
        if (old != null && old.action == action)
        {
            old.config = config;
            old.setSpeed(1);

            return old;
        }

        return new ActionPlayback(action, config, looping, priority);
    }

    @Override
    public void update(IEntity entity)
    {
        /* Update primary actions */
        if (this.basePre != null)
        {
            this.basePre.update();
        }

        if (this.basePost != null)
        {
            this.basePost.update();
        }
    }

    @Override
    public void applyActions(IEntity target, IModelInstance armature, float transition)
    {
        if (target == null)
        {
            return;
        }

        if (this.basePre != null)
        {
            this.basePre.apply(target, armature.getModel(), transition, 1F, false);
        }

        IModel model = armature.getModel();
        /* Which bone plays which role: the model's own assignment, else the old names (BBS FS f3113ccdd) */
        java.util.Map<String, String> roles = armature.getProceduralBones();
        String headBone = ProceduralBone.HEAD.resolve(model, roles), torsoBone = ProceduralBone.TORSO.resolve(model, roles);
        String rightArmBone = ProceduralBone.RIGHT_ARM.resolve(model, roles), leftArmBone = ProceduralBone.LEFT_ARM.resolve(model, roles);
        String rightLegBone = ProceduralBone.RIGHT_LEG.resolve(model, roles), leftLegBone = ProceduralBone.LEFT_LEG.resolve(model, roles);
        String anchorBone = ProceduralBone.ANCHOR.resolve(model, roles);
        ItemStack main = target.getEquipmentStack(EquipmentSlot.MAINHAND);
        ItemStack offhand = target.getEquipmentStack(EquipmentSlot.OFFHAND);

        boolean isRolling = target.getRoll() > 4;
        boolean isInSwimmingPose = target.getEntityPose() == Pose.SWIMMING;

        /* Common variables */
        float handSwingProgress = target.getHandSwingProgress(transition);
        float age = target.getAge() + transition;
        float bodyYaw = Lerps.lerp(target.getPrevBodyYaw(), target.getBodyYaw(), transition);
        float headYaw = Lerps.lerp(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
        float yaw = headYaw - bodyYaw;
        float pitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        float limbSpeed = target.getLimbSpeed(transition);
        float limbPhase = target.getLimbPos(transition);
        float leaningPitch = target.getLeaningPitch(transition);

        float coefficient = 1F;

        if (isRolling)
        {
            coefficient = (float) (target.getVelocity().lengthSqr() / 2D);
            coefficient = Math.min(1F, coefficient * coefficient * coefficient);
        }

        model.resetPose();

        if (target.isSneaking())
        {
            model.applyPose(armature.getSneakingPose());

            /* The code below finalizes bones in euler, and the sneaking pose fully lives in its euler
             * readback — drop the composed orientation so the euler writes stay authoritative. */
            for (ModelGroup group : model.getAllGroups())
            {
                group.orient = null;
            }

            for (BOBJBone bone : model.getAllBOBJBones())
            {
                bone.orient = null;
            }
        }

        /* For regular models */
        if (model instanceof Model)
        {
            ModelGroup leftArm = null;
            ModelGroup rightArm = null;
            ModelGroup torso = null;
            ModelGroup rightLeg = null, leftLeg = null;

            for (ModelGroup group : model.getAllGroups())
            {
                if (!anchorBone.isEmpty() && group.id.equals(anchorBone))
                {
                    if (target.isUsingRiptide())
                    {
                        /* Riptide spin was rotate.x·rotate2.y (a pitch then a Y spin
                         * composed after); with rotate2 gone, hold that exact
                         * composition as a quaternion (degrees for cubic current). */
                        group.current.rotationMode = Transform.RotationMode.QUATERNION;
                        group.current.quat.set(Matrices.toQuaternionZYXDegrees(-90.0F - pitch, 0F, 0F)
                            .mul(Matrices.toQuaternionZYXDegrees(0F, age * -75.0F, 0F)));
                    }

                    if (target.isFallFlying())
                    {
                        float roll = target.getRoll() + transition;
                        float riptide = Mth.clamp(roll * roll / 100F, 0F, 1F);

                        if (!target.isUsingRiptide())
                        {
                            group.current.rotate.x = riptide * (-90 - pitch);
                        }

                        Vec3 look = target.getRotationVec(transition);
                        Vec3 velocity = target.lerpVelocity(transition);
                        double vl = velocity.horizontalDistanceSqr();
                        double ll = look.horizontalDistanceSqr();

                        if (vl > 0 && ll > 0)
                        {
                            double m = (velocity.x * look.x + velocity.z * look.z) / Math.sqrt(vl * ll);
                            double n = velocity.x * look.z - velocity.z * look.x;

                            group.current.rotate.y = MathUtils.toDeg((float)(Math.signum(n) * Math.acos(m)));
                        }
                    }
                    else if (leaningPitch > 0F)
                    {
                        float newPitch = target.isTouchingWater() ? -90F - pitch : -90F;

                        group.current.rotate.x = Mth.lerp(leaningPitch, 0F, newPitch);

                        if (target.getEntityPose() == Pose.SWIMMING)
                        {
                            group.current.translate.y -= 0.5F * 16F;
                            group.current.translate.z += 0.3F * 16F;
                        }
                    }
                }
                else if (!headBone.isEmpty() && group.id.equals(headBone))
                {
                    /* The look direction must not erase the authored sneaking head offset. */
                    float sneakX = target.isSneaking() ? group.current.rotate.x - group.initial.rotate.x : 0F;
                    float sneakY = target.isSneaking() ? group.current.rotate.y - group.initial.rotate.y : 0F;
                    if (target.isSneaking()) group.current.rotate.x = group.initial.rotate.x;
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
                    group.current.rotate.x += sneakX;
                    group.current.rotate.y += sneakY;
                }
                else if (!rightArmBone.isEmpty() && group.id.equals(rightArmBone))
                {
                    group.current.rotate.x += MathUtils.toDeg(Mth.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient);
                    group.current.rotate.z += MathUtils.toDeg(1F * (Mth.cos(-age * 0.09F) * 0.05F + 0.05F));
                    group.current.rotate.x += MathUtils.toDeg(1F * Mth.sin(-age * 0.067F) * 0.05F);

                    if (!main.isEmpty())
                    {
                        group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                    }

                    rightArm = group;
                }
                else if (!leftArmBone.isEmpty() && group.id.equals(leftArmBone))
                {
                    group.current.rotate.x += MathUtils.toDeg(Mth.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient);
                    group.current.rotate.z += MathUtils.toDeg(-1F * (Mth.cos(-age * 0.09F) * 0.05F + 0.05F));
                    group.current.rotate.x += MathUtils.toDeg(-1F * Mth.sin(-age * 0.067F) * 0.05F);

                    if (!offhand.isEmpty())
                    {
                        group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                    }

                    leftArm = group;
                }
                else if (!torsoBone.isEmpty() && group.id.equals(torsoBone))
                {
                    torso = group;
                }
                else if (!rightLegBone.isEmpty() && group.id.equals(rightLegBone))
                {
                    group.current.rotate.x = MathUtils.toDeg(Mth.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient);
                    rightLeg = group;
                }
                else if (!leftLegBone.isEmpty() && group.id.equals(leftLegBone))
                {
                    group.current.rotate.x = MathUtils.toDeg(Mth.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient);
                    leftLeg = group;
                }
            }

            if (handSwingProgress > 0F && torso != null && leftArm != null && rightArm != null)
            {
                ModelGroup group;
                float swingFactor = handSwingProgress;

                torso.current.rotate.y = -MathUtils.toDeg(Mth.sin(Mth.sqrt(swingFactor) * MathUtils.PI * 2F) * 0.2F);

                leftArm.current.translate.z += (float) Math.sin(MathUtils.toRad(torso.current.rotate.y)) * 5F;
                leftArm.current.translate.x += (float) Math.cos(MathUtils.toRad(torso.current.rotate.y)) * 5F - 5F;
                rightArm.current.translate.z -= (float) Math.sin(MathUtils.toRad(torso.current.rotate.y)) * 5F;
                rightArm.current.translate.x -= (float) Math.cos(MathUtils.toRad(torso.current.rotate.y)) * 5F - 5F;

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
                float swing1 = Mth.sin(swingFactor * MathUtils.PI);
                float swign2 = Mth.sin(handSwingProgress * MathUtils.PI) * -(headPitch - 0.7F) * 0.75F;
                rightArm.current.rotate.x = group.current.rotate.x + MathUtils.toDeg(swing1 * 1.2F + swign2);
                rightArm.current.rotate.y += torso.current.rotate.y * 2F;
                rightArm.current.rotate.z += MathUtils.toDeg(Mth.sin(handSwingProgress * MathUtils.PI) * -0.4F);
            }

            /* Last, as vanilla does: the swim stroke overrides the walk, weighted by the lean (BBS FS f0668c423) */
            if (leaningPitch > 0F) VanillaSwimPose.apply(VanillaBone.of(rightArm), VanillaBone.of(leftArm), VanillaBone.of(rightLeg), VanillaBone.of(leftLeg), leaningPitch, limbPhase, handSwingProgress, target.isUsingItem());
        }
        /* For BOBJ models */
        else
        {
            BOBJBone bobjLeftArm = null;
            BOBJBone bobjRightLeg = null, bobjLeftLeg = null;
            BOBJBone bobjRightArm = null, bobjTorso = null;

            for (BOBJBone bone : model.getAllBOBJBones())
            {
                if (!anchorBone.isEmpty() && bone.name.equals(anchorBone))
                {
                    if (target.isUsingRiptide())
                    {
                        /* See the cubic riptide above; BOBJ channels are radians. */
                        bone.transform.rotationMode = Transform.RotationMode.QUATERNION;
                        bone.transform.quat.set(Matrices.toQuaternionZYXRadians(MathUtils.toRad(-90.0F - pitch), 0F, 0F)
                            .mul(Matrices.toQuaternionZYXRadians(0F, MathUtils.toRad(age * -75.0F), 0F)));
                    }

                    if (target.isFallFlying())
                    {
                        float roll = target.getRoll() + transition;
                        float riptide = Mth.clamp(roll * roll / 100F, 0F, 1F);

                        if (!target.isUsingRiptide())
                        {
                            bone.transform.rotate.x = MathUtils.toRad(riptide * (-90 - pitch));
                        }

                        Vec3 look = target.getRotationVec(transition);
                        Vec3 velocity = target.lerpVelocity(transition);
                        double vl = velocity.horizontalDistanceSqr();
                        double ll = look.horizontalDistanceSqr();

                        if (vl > 0 && ll > 0)
                        {
                            double m = (velocity.x * look.x + velocity.z * look.z) / Math.sqrt(vl * ll);
                            double n = velocity.x * look.z - velocity.z * look.x;

                            bone.transform.rotate.y = (float)(Math.signum(n) * Math.acos(m));
                        }
                    }
                    else if (leaningPitch > 0F)
                    {
                        float newPitch = target.isTouchingWater() ? -90F - pitch : -90F;

                        bone.transform.rotate.x = MathUtils.toRad(Mth.lerp(leaningPitch, 0F, newPitch));

                        if (target.getEntityPose() == Pose.SWIMMING)
                        {
                            bone.transform.translate.y -= MathUtils.toRad(0.5F * 16F);
                            bone.transform.translate.z += MathUtils.toRad(0.3F * 16F);
                        }
                    }
                }
                else if (!torsoBone.isEmpty() && bone.name.equals(torsoBone))
                {
                    bobjTorso = bone;
                }
                else if (!headBone.isEmpty() && bone.name.equals(headBone))
                {
                    /* BOBJ pose offsets are already radians; its reset transform is identity. */
                    float sneakX = target.isSneaking() ? bone.transform.rotate.x : 0F;
                    float sneakY = target.isSneaking() ? bone.transform.rotate.y : 0F;
                    if (target.isSneaking()) bone.transform.rotate.x = 0F;
                    bone.transform.rotate.y = MathUtils.toRad(-yaw);

                    if (isRolling)
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(45);
                    }
                    else if (leaningPitch > 0F)
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(this.lerpAngle(leaningPitch, bone.transform.rotate.x, isInSwimmingPose ? 45 : -pitch));
                    }
                    else
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(-pitch);
                    }
                    bone.transform.rotate.x += sneakX;
                    bone.transform.rotate.y += sneakY;
                }
                else if (!rightArmBone.isEmpty() && bone.name.equals(rightArmBone))
                {
                    bone.transform.rotate.x += Mth.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient;
                    bone.transform.rotate.z -= 1F * (Mth.cos(-age * 0.09F) * 0.05F + 0.05F);
                    bone.transform.rotate.x += 1F * Mth.sin(-age * 0.067F) * 0.05F;

                    if (!main.isEmpty())
                    {
                        bone.transform.rotate.x = bone.transform.rotate.x * 0.5F + MathUtils.toRad(18F);
                    }

                    bobjRightArm = bone;
                }
                else if (!leftArmBone.isEmpty() && bone.name.equals(leftArmBone))
                {
                    bone.transform.rotate.x += Mth.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient;
                    bone.transform.rotate.z -= -1F * (Mth.cos(-age * 0.09F) * 0.05F + 0.05F);
                    bone.transform.rotate.x += -1F * Mth.sin(-age * 0.067F) * 0.05F;

                    if (!offhand.isEmpty())
                    {
                        bone.transform.rotate.x = bone.transform.rotate.x * 0.5F + MathUtils.toRad(18F);
                    }

                    bobjLeftArm = bone;
                }
                else if (!rightLegBone.isEmpty() && bone.name.equals(rightLegBone))
                {
                    bone.transform.rotate.x = Mth.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient;
                    bobjRightLeg = bone;
                }
                else if (!leftLegBone.isEmpty() && bone.name.equals(leftLegBone))
                {
                    bone.transform.rotate.x = Mth.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient;
                    bobjLeftLeg = bone;
                }
            }

            if (handSwingProgress > 0F && bobjLeftArm != null && bobjRightArm != null)
            {
                BOBJBone group;
                float swingFactor = handSwingProgress;
                float rotate = -MathUtils.toDeg(Mth.sin(Mth.sqrt(swingFactor) * MathUtils.PI * 2F) * 0.2F);

                if (bobjTorso != null) bobjTorso.transform.rotate.y = -MathUtils.toRad(rotate);

                bobjLeftArm.transform.translate.z -= ((float) Math.sin(MathUtils.toRad(rotate)) * 5F) / 16F;
                bobjLeftArm.transform.translate.x -= ((float) Math.cos(MathUtils.toRad(rotate)) * 5F - 5F) / 16F;
                bobjRightArm.transform.translate.z += ((float) Math.sin(MathUtils.toRad(rotate)) * 5F) / 16F;
                bobjRightArm.transform.translate.x += ((float) Math.cos(MathUtils.toRad(rotate)) * 5F - 5F) / 16F;

                group = bobjRightArm;
                group.transform.rotate.y -= MathUtils.toRad(rotate);
                group = bobjLeftArm;
                group.transform.rotate.y -= MathUtils.toRad(rotate);
                group = bobjLeftArm;
                group.transform.rotate.x += MathUtils.toRad(rotate);

                swingFactor = 1F - handSwingProgress;
                swingFactor *= swingFactor;
                swingFactor *= swingFactor;
                swingFactor = 1F - swingFactor;

                float headPitch = 0F;
                float swing1 = Mth.sin(swingFactor * MathUtils.PI);
                float swign2 = Mth.sin(handSwingProgress * MathUtils.PI) * -(headPitch - 0.7F) * 0.75F;
                bobjRightArm.transform.rotate.x = MathUtils.toRad(group.transform.rotate.x + MathUtils.toDeg(swing1 * 1.2F + swign2));
                bobjRightArm.transform.rotate.y -= MathUtils.toRad(rotate * 2F);
                bobjRightArm.transform.rotate.z -= Mth.sin(handSwingProgress * MathUtils.PI) * -0.4F;
            }

            if (leaningPitch > 0F) VanillaSwimPose.apply(VanillaBone.of(bobjRightArm), VanillaBone.of(bobjLeftArm), VanillaBone.of(bobjRightLeg), VanillaBone.of(bobjLeftLeg), leaningPitch, limbPhase, handSwingProgress, target.isUsingItem());
        }

        if (this.basePost != null)
        {
            this.basePost.postApply(target, armature.getModel(), transition);
        }
    }

    @Override
    public void playAnimation(String name)
    {}

    protected float lerpAngle(float a, float b, float magnitude)
    {
        float factor = (magnitude - b) % (360);

        if (factor < -180) factor += 360;
        if (factor >= 180) factor -= 360;

        return b + a * factor;
    }
}
