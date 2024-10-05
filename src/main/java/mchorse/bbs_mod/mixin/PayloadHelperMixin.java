package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSSettings;
import net.fabricmc.fabric.impl.networking.payload.PayloadHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PayloadHelper.class)
public class PayloadHelperMixin
{
    @ModifyVariable(method = "assertSize", at = @At("HEAD"), ordinal = 0)
    private static int modifyMaxPacketSize(int value)
    {
        return BBSSettings.unlimitedPacketSize != null && BBSSettings.unlimitedPacketSize.get() ? Integer.MAX_VALUE : value;
    }
}