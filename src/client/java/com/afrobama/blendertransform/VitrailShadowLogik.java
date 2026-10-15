package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.film.NativeReplayShadows;
import mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;

/** Required shadow lifecycle handlers outside woven classes. */
public final class VitrailShadowLogik
{
    private VitrailShadowLogik() {}

    public static void begin() { VitrailShadowFrame.close(); }

    @SuppressWarnings("unchecked")
    public static int gather(Minecraft minecraft, Camera view, Frustum frustum, LevelRenderState state, Object casters, Object original)
    {
        int world = ((Operation<Integer>) original).call(minecraft, view, frustum, casters);
        if (!VitrailShadowFrame.enabled() || !((dev.vitrail.pack.source.ShadowCasters) casters).entities()) return world;
        return world + NativeReplayShadows.gather(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), state);
    }

    /** Called after both light extractions and before the first terrain shadow pass. */
    public static void prepare(LevelRenderState state)
    {
        if (!VitrailShadowFrame.enabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = state.cameraRenderState.pos;
        SubmitNodeStorage discarded = new SubmitNodeStorage();
        VitrailShadowFrame.prepare(() -> mchorse.bbs_mod.forms.FormSammler.mitSammler(discarded, () ->
        {
            /* Same frozen ShadowGeometry.submit contract, with a disposable collector:
             * only the retained BBS model poses survive; real equipment is submitted later. */
            PoseStack pose = new PoseStack();
            for (net.minecraft.client.renderer.entity.state.EntityRenderState entity : state.entityRenderStates)
                minecraft.levelRenderer.entityRenderDispatcher().submit(entity, state.cameraRenderState,
                    entity.x - camera.x, entity.y - camera.y, entity.z - camera.z, pose, discarded);
            for (net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState block : state.blockEntityRenderStates)
            {
                pose.pushPose();
                pose.translate(block.blockPos.getX() - camera.x, block.blockPos.getY() - camera.y, block.blockPos.getZ() - camera.z);
                minecraft.levelRenderer.blockEntityRenderDispatcher().submit(block, pose, discarded, state.cameraRenderState);
                pose.popPose();
            }
            NativeReplayShadows.submit(state.cameraRenderState, discarded);
        }));
    }

    @SuppressWarnings("unchecked")
    public static void submit(Minecraft minecraft, Vec3 camera, LevelRenderState state,
        SubmitNodeStorage storage, Object original)
    {
        VitrailShadowFrame.collect(() -> mchorse.bbs_mod.forms.FormSammler.mitSammler(storage, () ->
        {
            ((Operation<Void>) original).call(minecraft, camera);
            NativeReplayShadows.submit(state.cameraRenderState, storage);
        }));
    }

    @SuppressWarnings("unchecked")
    public static void window(boolean open, Object original)
    {
        try { ((Operation<Void>) original).call(open); }
        finally { if (!open) VitrailShadowFrame.window(false); }
        if (open) VitrailShadowFrame.window(true);
        if (open && VitrailShadowFrame.enabled())
        {
            try { VitrailShadowFrame.draw(); }
            catch (Throwable failure)
            {
                try { ((Operation<Void>) original).call(false); }
                catch (Throwable closing) { if (closing != failure) failure.addSuppressed(closing); }
                finally { VitrailShadowFrame.window(false); }
                throw failure;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void draw(Vec3 camera, Object original)
    {
        try { ((Operation<Void>) original).call(camera); }
        finally { VitrailShadowFrame.close(); }
    }
}
