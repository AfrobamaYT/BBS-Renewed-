package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin
{
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void onTick(IntegratedServer server, BooleanSupplier supplier, Operation<Void> original)
    {
        /* Der Originalaufruf geht als Runnable rueber: MixinExtras' Operation bleibt hier,
         * die Entscheidung WIE OFT getaktet wird liegt in der hotswapbaren Logik. */
        MixinBruecke.rufe("AufnahmeServerTaktLogik", "takten",
            (Runnable) () -> original.call(server, supplier));
    }
}