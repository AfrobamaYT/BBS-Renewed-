package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    public static final Color color = new Color();

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.getContext().flush();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        PoseStack matrices = context.batcher.getContext().pose();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.pushPose();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());
        matrices.translate(-0.5F, 0F, -0.5F);

        matrices.last().normal().getScale(Vectors.EMPTY_3F);
        matrices.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        Color set = Color.white();
        FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(this.form.blockState.get(), matrices, consumers, LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        matrices.popPose();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.pushPose();
        if (context.world != null)
        {
            context.world.pushPose();
        }
        context.stack.translate(-0.5F, 0F, -0.5F);
        if (context.world != null)
        {
            context.world.translate(-0.5F, 0F, -0.5F);
        }

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }

        /* 26.2: der Hijack des Nicht-Picking-Zweigs ist ersatzlos entfallen. Er tat nichts
         * anderes, als je gezeichneter RenderType-Ebene (RenderLayerMixin haengt an
         * RenderType#setupRenderState) den globalen Blend-Schalter anzuschalten. Die
         * Block-Ebenen, die der Vanilla-Blockrenderer unten selbst waehlt, tragen ihr Blending
         * in ihrer eigenen Pipeline (RenderType#pipeline()). clearRunnables() unten bleibt
         * stehen und laeuft nun ins Leere - harmlos. */

        color.set(context.color);
        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)); the
         * picking branch above never publishes, so the stencil keeps every pixel. */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(this.form.blockState.get(), context.stack, consumers, light, context.overlay);
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.popPose();
        if (context.world != null)
        {
            context.world.popPose();
        }

        /* 26.2: hier ist nichts mehr zurueckzustellen. Der Tiefentest ist kein globaler Zustand
         * mehr, den der Block-Draw verstellt haben koennte - jeder Draw bekommt ihn aus seiner
         * Pipeline (RenderType#pipeline()). Ersatzlos entfallen. */
    }
}
