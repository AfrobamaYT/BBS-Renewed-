package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FrontendRenderPass.class, remap = false)
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
