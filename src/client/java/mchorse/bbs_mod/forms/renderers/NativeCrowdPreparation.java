package mchorse.bbs_mod.forms.renderers;

import com.afrobama.bbstools.client.bbs.fastpath.NativeCrowdFrame;
import com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw;
import com.afrobama.blendertransform.ModelFormRendererFastPathLogik;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Zeichenziel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

/** The actor's real context is complete here; no body is drawn while collecting the crowd. */
public final class NativeCrowdPreparation
{
    private NativeCrowdPreparation()
    {}

    public static void renderOrCollect(Form form, FormRenderingContext context)
    {
        if (mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame.collecting())
        {
            FormUtilsClient.render(form, context);
            return;
        }
        if (FilmControllerContext.sammelDurchgang && !context.worldLabel)
        {
            if (mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.collect(form, context)) return;
            FramebufferFormPreparation.prepare(form, context);
            collect(form, context);
        }
        else if (mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.world(context))
            mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.replay(form, context.entity, () -> FormUtilsClient.render(form, context));
        else FormUtilsClient.render(form, context);
    }

    private static void collect(Form form, FormRenderingContext context)
    {
        if (!Tier2hInstancedDraw.nativeEnabled() || !NativeCrowdFrame.collecting()
            || context.ui || context.stencilMap != null || Zeichenziel.eigenes()) return;
        FormRenderer<?> base = FormUtilsClient.getRenderer(form);
        if (!(base instanceof ModelFormRenderer renderer)) return;
        form.applyStates(context.transition);
        int light = context.light;
        boolean pushed = false;
        try
        {
            if (!form.visible.get()) return;
            context.stack.pushPose();
            if (context.world != null) context.world.pushPose();
            pushed = true;
            renderer.applyTransforms(context.stack, false, context.getTransition());
            if (context.world != null) renderer.applyTransforms(context.world, false, context.getTransition());
            float blend = 1F - MathUtils.clamp(form.lighting.get(), 0F, 1F);
            int u = context.light & 65535, v = context.light >>> 16;
            u = (int) Lerps.lerp(u, net.minecraft.util.LightCoordsUtil.pack(15, 0), blend);
            context.light = u | v << 16;
            ModelFormRendererFastPathLogik.classify(renderer, context, null, null);
        }
        finally
        {
            if (pushed)
            {
                context.stack.popPose();
                if (context.world != null) context.world.popPose();
            }
            context.light = light;
            form.unapplyStates();
        }
    }
}
