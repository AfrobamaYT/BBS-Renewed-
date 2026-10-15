package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** vor1215: das offene GUI-Buendel der Schicht ist aelter als Vanillas Puffer und geht zuerst raus. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsFlushMixin
{
    @Inject(method = "flush", at = @At("HEAD"))
    private void bbs$buendelZuerst(CallbackInfo info)
    {
        GuiRenderState.von((GuiGraphics) (Object) this).flush();
    }
}
