package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ItemForm;
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

public class ItemFormRenderer extends FormRenderer<ItemForm>
{
    public ItemFormRenderer(ItemForm form)
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

        matrices.last().normal().getScale(Vectors.EMPTY_3F);
        matrices.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        Color set = Color.white();
        FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);
        Minecraft.getInstance().getItemRenderer().renderStatic(this.form.stack.get(), this.form.modelTransform.get(), LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY, matrices, consumers, Minecraft.getInstance().level, 0);
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

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }

        /* 26.2: der else-Zweig ist ersatzlos entfallen - gleicher Grund wie in
         * ModelFormRenderer#renderItems: sein Hijack tat nichts anderes, als je gezeichneter
         * RenderType-Ebene den globalen Blend-Schalter anzuschalten. Die Item-Ebenen, die der
         * Vanilla-Item-Renderer unten selbst waehlt, tragen ihr Blending in ihrer eigenen
         * Pipeline (RenderType#pipeline()). clearRunnables() weiter unten bleibt stehen und
         * laeuft im Nicht-Picking-Fall ins Leere - harmlos. */

        BlockFormRenderer.color.set(context.color);
        FormColorBlend.blend(BlockFormRenderer.color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)). */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(BlockFormRenderer.color));
        Minecraft.getInstance().getItemRenderer().renderStatic(this.form.stack.get(), this.form.modelTransform.get(), light, context.overlay, context.stack, consumers, context.entity.getWorld(), 0);
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.popPose();

        /* 26.2: das enableDepthTest() am Methodenende ist ersatzlos entfallen. Es stellte den
         * globalen Tiefentest wieder her, den der Item-Draw verstellt haben koennte - beides
         * gibt es nicht mehr. Jeder folgende Draw bekommt seine Tiefe aus der Pipeline seines
         * RenderType (RenderType#pipeline()). */
    }
}
