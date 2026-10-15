package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin
{
    @Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"))
    public void onPlace(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> info)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "blockGesetzt", context, state);
    }
}
