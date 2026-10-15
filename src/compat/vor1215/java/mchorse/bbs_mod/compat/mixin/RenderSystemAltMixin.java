package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.TransientMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** vor1215: beim Bildwechsel Transient-Speicher freigeben und erledigte GPU-Rueckmeldungen ausfuehren. */
@Mixin(RenderSystem.class)
public abstract class RenderSystemAltMixin
{
    @Inject(method = "flipFrame", at = @At("TAIL"))
    private static void bbs$transientEnde(CallbackInfo info)
    {
        TransientMemory.INSTANZ.bildEnde();
        GlDevice.instanz().bildEnde();
        mchorse.bbs_mod.compat.gpu.Vorgaben.bildEnde();
    }
}
