package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.TransientMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends the frame of 26.2's transient memory ({@link TransientMemory}) where Minecraft flips its frame. */
@Mixin(RenderSystem.class)
public abstract class RenderSystemAltMixin
{
    @Inject(method = "flipFrame", at = @At("TAIL"))
    private static void bbs$transientEnde(CallbackInfo info)
    {
        TransientMemory.INSTANZ.bildEnde();
    }
}
