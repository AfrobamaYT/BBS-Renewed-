package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public class SimpleOptionMixin
{
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    public void onGetValue(CallbackInfoReturnable info)
    {
        Object wert = MixinBruecke.rufe("EditorVorschauLogik", "helligkeit", this);

        if (wert != null)
        {
            info.setReturnValue(wert);
        }
    }
}