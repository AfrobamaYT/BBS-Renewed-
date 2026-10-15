package com.afrobama.blendertransform;

import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame;
import mchorse.bbs_mod.integrations.vitrail.VitrailAttachmentViews;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Required native actor and attachment operations for the optional Vitrail path. */
public final class VitrailFrameLogik
{
    private VitrailFrameLogik()
    {}

    public static void begin()
    {
        mchorse.bbs_mod.client.renderer.MorphAufschub.discard();
        VitrailActorFrame.begin();
        mchorse.bbs_mod.client.renderer.NativeMorphCapture.beginCollection();
    }

    public static RenderPassDescriptor baseLevels(RenderPassDescriptor descriptor)
    {
        return VitrailAttachmentViews.descriptor(descriptor);
    }

    public static void views(GpuTextureView view, GpuTextureView baseView)
    {
        VitrailAttachmentViews.register(view, baseView);
    }

    public static void forget(GpuTextureView view)
    {
        VitrailAttachmentViews.forget(view);
    }

    public static void capture(RenderPipeline pipeline, IModelVAO vao, Matrix4f matrix,
        float r, float g, float b, float a, int light, int overlay, int passMode, Texture texture,
        float emission, CallbackInfo callback)
    {
        if (mchorse.bbs_mod.graphics.Zeichenziel.eigenes()) return;
        if (vao.eckenZahl() > 0 && mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame.capture(pipeline, vao, matrix,
            r, g, b, a, light, overlay, passMode, texture, emission))
        {
            callback.cancel();
            return;
        }
        if (mchorse.bbs_mod.client.renderer.NativeMorphCapture.suppresses(pipeline, vao, matrix,
            r, g, b, a, light, overlay, passMode, texture, emission))
        {
            callback.cancel();
            return;
        }
        if (vao.eckenZahl() > 0 && VitrailActorFrame.capture(pipeline, vao, matrix,
            r, g, b, a, light, overlay, passMode, texture, emission))
        {
            mchorse.bbs_mod.client.renderer.NativeMorphCapture.captured(vao);
            callback.cancel();
        }
    }
}
