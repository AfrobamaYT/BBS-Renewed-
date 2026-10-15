package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Both sky attachments follow the active BBS world target instead of the constructor's window target. */
@Mixin(SkyRenderer.class)
public abstract class SkyRenderTargetMixin
{
    @ModifyExpressionValue(method = "render", require = 2, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
        opcode = Opcodes.GETFIELD))
    private RenderTarget bbs$skyTarget(RenderTarget original) throws Throwable
    {
        return (RenderTarget) MixinBruecke.rufePflicht("SpielRendererLogik", "himmelsziel", original);
    }
}
