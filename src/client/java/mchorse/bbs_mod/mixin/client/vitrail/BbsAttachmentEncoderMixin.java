package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** All encoder overloads converge here; only registered Vitrail-owned views are replaced. */
@Mixin(targets = "com.mojang.renderpearl.frontend.FrontendCommandEncoder", remap = false)
public abstract class BbsAttachmentEncoderMixin
{
    @ModifyVariable(method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private RenderPassDescriptor bbs$baseLevels(RenderPassDescriptor descriptor) throws Throwable
    { return (RenderPassDescriptor) MixinBruecke.rufePflicht("VitrailFrameLogik", "baseLevels", descriptor); }
}
