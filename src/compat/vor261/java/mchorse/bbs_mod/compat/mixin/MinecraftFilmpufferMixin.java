package mchorse.bbs_mod.compat.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2's GameRenderer.mainRenderTarget() redirect onto BBS' film buffer (shared GameRendererMixin);
 * before 26.1 the main target is Minecraft.getMainRenderTarget().
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFilmpufferMixin
{
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void bbs$umlenkenAufFilmpuffer(CallbackInfoReturnable<RenderTarget> info)
    {
        RenderTarget puffer = (RenderTarget) MixinBruecke.rufe("SpielRendererLogik", "filmpuffer");

        if (puffer != null)
        {
            info.setReturnValue(puffer);
        }
    }
}
