package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before 26.1 (replaces the shared mixin): the name tag is submitted by submitNameTag (26.2: submitNameDisplay). */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin
{
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    public void onRenderLabelIfPresent(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("NamensschildLogik", "unterdruecken"))
        {
            info.cancel();
        }
    }
}
