package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame;

/** Required native entity handlers keep their implementation out of woven classes. */
public final class VitrailEntityDrawLogik
{
    private VitrailEntityDrawLogik()
    {}

    public static void opaque(boolean open)
    {
        if (open)
        {
            mchorse.bbs_mod.client.renderer.NativeMorphCapture.endCollection();
            VitrailActorFrame.drawWindow(false);
        }
    }

    @SuppressWarnings("unchecked")
    public static void normalClose(boolean open, Object original)
    {
        try { if (!open) VitrailActorFrame.drawWindow(true); }
        finally { ((Operation<Void>) original).call(open); }
    }

    public static void closeActorFrame()
    {
        try { mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame.close(); }
        finally { VitrailActorFrame.close(); }
    }

    public static void reset()
    {
        try { dev.vitrail.render.EntityDraw.endGroup(); }
        finally
        {
            mchorse.bbs_mod.client.renderer.MorphAufschub.discard();
            try { closeActorFrame(); }
            finally { mchorse.bbs_mod.forms.FormTranslucentQueue.discardVitrailMeshes(); }
        }
    }

    @SuppressWarnings("unchecked")
    public static void draw(Object pass, int count, int instances, int firstIndex,
        int baseVertex, int firstInstance, Object original)
    {
        if (!mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame.record((RenderPass) pass)
            && !VitrailActorFrame.record((RenderPass) pass))
        {
            mchorse.bbs_mod.integrations.vitrail.VitrailRetainedAlpha.bind((RenderPass) pass);
            ((Operation<Void>) original).call(pass, count, instances, firstIndex, baseVertex, firstInstance);
        }
    }
}
