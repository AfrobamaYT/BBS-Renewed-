package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.NativeCrowdPreparation;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.integrations.vitrail.VitrailShaderOptions;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Retain placed models for the same native shader collection/replay as film actors. */
public final class VitrailModelBlockRenderer extends ModelBlockEntityRenderer
{
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(Form form, IEntity entity, PoseStack.Pose pose, int light, float delta, boolean axes)
    {}

    public static void beginFrame()
    {
        PENDING.clear();
    }

    public static void renderPending()
    {
        ModelBlockEntityRenderer.weltzuegeZeichnen();
        boolean collecting = FilmControllerContext.sammelDurchgang;

        try
        {
            for (Pending draw : PENDING)
            {
                PoseStack stack = new PoseStack();
                stack.last().set(draw.pose());
                FormRenderingContext context = new FormRenderingContext()
                    .set(FormRenderType.MODEL_BLOCK, draw.entity(), stack, draw.light(),
                        OverlayTexture.NO_OVERLAY, draw.delta())
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera());

                NativeCrowdPreparation.renderOrCollect(draw.form(), context);

                if (!collecting && draw.axes())
                {
                    stack.pushPose();
                    MatrixStackUtils.scaleBack(stack);
                    Draw.coolerAxes(stack, 0.5F, 0.005F);
                    stack.popPose();
                }
            }
        }
        finally
        {
            if (!collecting)
            {
                PENDING.clear();
            }
        }
    }

    public VitrailModelBlockRenderer(BlockEntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void submit(ModelBlockRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.zeichnen || state.form == null || !GpuBackend.isVulkan()
            || !FabricLoader.getInstance().isModLoaded("vitrail") || !VitrailShaderOptions.present()
            || mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()
            || !(FormUtilsClient.getRenderer(state.form) instanceof ModelFormRenderer))
        {
            super.submit(state, matrices, collector, camera);
            return;
        }

        PoseStack stack = new PoseStack();
        stack.last().set(matrices.last());
        stack.translate(0.5F, 0F, 0.5F);
        MatrixStackUtils.applyTransform(stack, state.transform);
        PENDING.add(new Pending(state.form, state.bbsEntity, stack.last().copy(), state.lichtOben,
            state.teilTick, state.achsen));

        /* Vanilla's cracks, shadow submissions and debug box keep their original path. */
        state.zeichnen = false;

        try
        {
            super.submit(state, matrices, collector, camera);
        }
        finally
        {
            state.zeichnen = true;
        }
    }
}
