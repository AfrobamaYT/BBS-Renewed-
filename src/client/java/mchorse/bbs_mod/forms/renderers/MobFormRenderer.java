package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    private Entity entity;

    private String lastId = "";
    public float prevHandSwing;
    private double prevX = Float.MIN_VALUE;
    private double prevZ = Float.MIN_VALUE;
    private float prevYawHead;
    private float prevPitch;


    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    private void ensureEntity()
    {
        String id = this.form.mobID.get();

        if (!this.lastId.equals(id))
        {
            this.lastId = id;
            this.entity = null;
        }

        if (this.entity != null)
        {
            return;
        }

        this.entity = Registries.ENTITY_TYPE.get(new Identifier(id)).create(MinecraftClient.getInstance().world);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            float scale = this.form.uiScale.get();

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            consumers.setUI(true);
            MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, context.getTransition(), stack, consumers, LightmapTextureManager.MAX_SKY_LIGHT_COORDINATE);
            consumers.draw();
            consumers.setUI(false);

            stack.pop();

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            int light = context.light;

            context.stack.push();

            MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, context.getTransition(), context.stack, consumers, light);
            consumers.draw();

            context.stack.pop();

            RenderSystem.enableDepthTest();
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            if (this.prevX == Float.MIN_VALUE)
            {
                this.prevX = entity.getX();
                this.prevZ = entity.getZ();
            }

            this.entity.tick();

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.prevPitch = this.prevPitch;
                livingEntity.prevHeadYaw = this.prevYawHead;
                livingEntity.prevBodyYaw = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    a.setPrevSpeed(b.getPrevSpeed());
                    a.setSpeed(b.getSpeed());
                    a.setPos(b.getPos());
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    livingEntity.swingHand(Hand.MAIN_HAND);
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setHeadYaw(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setPitch(entity.getPitch());
            this.entity.setBodyYaw(0F);

            this.entity.setPos(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setSneaking(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.age = entity.getAge();

            this.prevX = entity.getX();
            this.prevZ = entity.getZ();
            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }
}