package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin
{
    @Inject(method = "use", at = @At("HEAD"))
    /* 26.2: ItemStack.use gibt InteractionResult statt InteractionResultHolder<ItemStack>
     * zurueck -- am jar geprueft. Der CallbackInfoReturnable-typparameter MUSS mitgehen,
     * sonst wirft Mixin beim laden, nicht beim uebersetzen. */
    public void onUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "gegenstandBenutzt", world, user, hand);
    }

    @Inject(method = "useOn", at = @At("HEAD"))
    public void onUseOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> info)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "gegenstandAufBlock", context);
    }
}
