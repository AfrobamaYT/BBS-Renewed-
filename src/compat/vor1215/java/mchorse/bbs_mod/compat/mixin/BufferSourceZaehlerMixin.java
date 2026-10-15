package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** vor1215: zaehlt Vanillas Puffergriffe, damit die GUI-Buendel die Malreihenfolge halten (GuiRenderState). */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceZaehlerMixin
{
    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void bbs$zaehlen(CallbackInfoReturnable<?> info)
    {
        GuiRenderState.zeichnungen++;
    }
}
