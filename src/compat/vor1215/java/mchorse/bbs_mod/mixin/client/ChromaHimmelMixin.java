package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vor 1.21.5 (ersetzt die gemeinsame Fassung): 1.21.4 holt die Himmelsfarbe aus ClientLevel.getSkyColor. */
@Mixin(ClientLevel.class)
public class ChromaHimmelMixin
{
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void bbs$chromaHimmel(Vec3 ort, float teilbild, CallbackInfoReturnable<Integer> info)
    {
        if (MixinBruecke.rufe("ChromaHimmelLogik", "farbeOderNull") instanceof Integer farbe)
        {
            info.setReturnValue(farbe);
        }
    }
}
