package mchorse.bbs_mod.mixin.client.iris;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliderElementWidget.class)
public abstract class SliderElementWidgetMixin
{
    @Inject(method = "whileDragging", at = @At("TAIL"), remap = false)
    private void onDragging(CallbackInfo info)
    {
        /* Der Invoker ist eine Mixin-schnittstelle der ZIELklasse — der Kernel kennt sie
         * nicht, also bleibt der Aufruf hier und die Bruecke entscheidet nur, OB. */
        if (MixinBruecke.rufeUndAbbrechen("ShaderPaketLogik", "reglerNachziehen"))
        {
            ((StringElementWidgetInvoker) (Object) this).bbs$queue();
        }
    }
}