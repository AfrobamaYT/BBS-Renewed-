package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin
{
    @Inject(method = "render", at = @At(value = "INVOKE", target = "com.mojang.blaze3d.systems.RenderSystem.clear(IZ)V"))
    public void beforeRender(CallbackInfo info)
    {
        // TODO: BBSModClient.renderToFramebuffer();
    }
}