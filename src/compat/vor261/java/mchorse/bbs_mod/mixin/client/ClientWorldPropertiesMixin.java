package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Before 26.1 (replaces the shared mixin): the world's time of day is Level.getDayTime (26.2: getDefaultClockTime). */
@Mixin(Level.class)
public class ClientWorldPropertiesMixin
{
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    public void onGetTimeOfDay(CallbackInfoReturnable<Long> info)
    {
        Object zeit = MixinBruecke.rufe("WeltUeberschreibungLogik", "tageszeit");

        if (zeit != null)
        {
            info.setReturnValue((Long) zeit);
        }
    }
}
