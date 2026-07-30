package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    public RenderTarget entityTarget;

    /* Deferred form translucency spans the frame: forms enqueue their translucent pass while
     * entities render, and the queue flushes right before the translucent terrain layer so the
     * blending sits under water/glass the way vanilla entities do. The RETURN hook is a safety
     * net for frames where the translucent layer never draws (e.g. a replaced terrain pipeline). */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void onRenderWorldStart(CallbackInfo info)
    {
        FormTranslucentQueue.begin();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    public void onRenderWorldEnd(CallbackInfo info)
    {
        FormTranslucentQueue.flush();
    }

    /* MERGE fs-2.5: ihre renderSky-signatur nimmt MatrixStack als ersten parameter,
     * unsere Matrix4f. UNSERE ist die, mit der der mixin auf 1.21.1 nachweislich laedt —
     * ein mixin, dessen ziel es nicht gibt, laesst den start scheitern. Ihre queue-hooks
     * darueber sind uebernommen, nur diese eine signatur bleibt unsere. */
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    public void onRenderSky(CallbackInfo info)
    {
        if (BBSSettings.chromaSkyEnabled.get())
        {
            Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
            int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
            Color color = Color.rgba(argb);

            GL11.glClearColor(color.r, color.g, color.b, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.setShaderFogColor(color.r, color.g, color.b, 1F);

            info.cancel();
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    public void onRenderLayer(RenderType renderLayer, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo info)
    {
        /* Iris' shadow pass re-runs renderLayer into the shadow map early in the frame — its
         * translucent layer must not fire the flush, or the queue deactivates before the main
         * pass has even rendered the forms. */
        if (renderLayer == RenderType.translucent() && !BBSRendering.isIrisShadowPass())
        {
            FormTranslucentQueue.flush();
        }

        if (BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get())
        {
            BBSRendering.onRenderChunkLayer(positionMatrix);

            info.cancel();
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("TAIL"))
    public void onRenderChunkLayer(RenderType layer, double x, double y, double z, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo info)
    {
        if (layer == RenderType.solid())
        {
            BBSRendering.onRenderChunkLayer(positionMatrix);
        }
    }

    @Inject(at = @At("RETURN"), method = "initOutline")
    private void onLoadEntityOutlineShader(CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(at = @At("RETURN"), method = "resize")
    private void onResized(CallbackInfo info)
    {
        if (this.entityTarget == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(method = "prepareCullFrustum", at = @At("HEAD"))
    private void onSetupFrustum(Vec3 pos, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo info)
    {
        BBSRendering.camera.set(positionMatrix);
    }
}