package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin
{
    @ModifyExpressionValue(method = "drop(Z)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity onDropItem(ItemEntity entity)
    {
        /* drop(boolean) returns void in 26.3; retain the actual created ItemEntity. */
        KernelBruecke.rufe("SpielerAktionenLogik", "gegenstandGeworfen", this, entity);
        return entity;
    }
}
