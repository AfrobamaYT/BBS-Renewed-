package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** All encoder overloads converge here; only registered Vitrail-owned views are replaced. */
@Mixin(targets = "com.mojang.blaze3d.systems.CommandEncoder", remap = false)
public abstract class BbsAttachmentEncoderMixin
{
    @ModifyVariable(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private RenderPassDescriptor bbs$baseLevels(RenderPassDescriptor descriptor) throws Throwable
    { return (RenderPassDescriptor) MixinBruecke.rufePflicht("VitrailFrameLogik", "baseLevels", descriptor); }
}
