package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.math.Interpolations;
import mchorse.bbs_mod.utils.math.MathUtils;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

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
    public void setup(CubicModel model, ActionsConfig actionsConfig)
    {}

    @Override
    public void applyActions(IEntity entity, Model model, float transition)
    {
        if (entity == null)
        {
            return;
        }

        boolean bl = false;

        /* Common variables */
        float handSwingProgress = entity.getHandSwingProgress(transition);
        float age = entity.getAge() + transition;
        float bodyYaw = Interpolations.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition);
        float headYaw = Interpolations.lerp(entity.getPrevHeadYaw(), entity.getHeadYaw(), transition);
        float yaw = headYaw - bodyYaw;
        float pitch = Interpolations.lerp(entity.getPrevPitch(), entity.getPitch(), transition);
        float limbSpeed = entity.getLimbSpeed(transition);
        float limbPhase = entity.getLimbPos(transition);

        float k = 1.0F;

        if (bl) {
            k = (float) entity.getVelocity().lengthSquared();
            k /= 0.2F;
            k *= k * k;
        }

        if (k < 1.0F) {
            k = 1.0F;
        }

        for (ModelGroup group : model.getAllGroups())
        {
            if (group.id.equals("head"))
            {
                group.current.rotate.x = -pitch;
                group.current.rotate.y = -(yaw);
            }
            else if (group.id.equals("left_arm"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / k);

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
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / k);
            }
            else if (group.id.equals("body"))
            {

            }
            else if (group.id.equals("left_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / k);
            }
            else if (group.id.equals("right_leg"))
            {
                group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / k);
            }
        }
    }

    @Override
    public void update(IEntity entity)
    {}
}
