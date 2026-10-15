package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before 26.1 (replaces the shared InGameHudMixin): the HUD draws in Gui.render(graphics, delta). */
@Mixin(Gui.class)
public class InGameHudMixin
{
    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void render(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("HudZeichnenLogik", "anfang"))
        {
            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(CallbackInfo info)
    {
        MixinBruecke.rufe("HudZeichnenLogik", "ende");
    }
}
