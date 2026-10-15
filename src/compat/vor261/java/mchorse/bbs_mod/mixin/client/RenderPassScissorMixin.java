package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before 26.1 (replaces the shared mixin on 26.2's FrontendRenderPass): Mojang's only pass is GlRenderPass. */
@Mixin(GlRenderPass.class)
public abstract class RenderPassScissorMixin
{
    @Inject(method = "enableScissor(IIII)V", at = @At("RETURN"))
    private void bbs$scissor(int x, int y, int width, int height, CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "scissor", (RenderPass) (Object) this, x, y, width, height);
    }

    @Inject(method = "disableScissor()V", at = @At("RETURN"))
    private void bbs$defaultScissor(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "defaultScissor", (RenderPass) (Object) this);
    }

    @Inject(method = "close()V", at = @At("RETURN"))
    private void bbs$closed(CallbackInfo callback) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "closed", (RenderPass) (Object) this);
    }
}
