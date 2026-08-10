package mchorse.bbs_mod.mixin;

import com.afrobama.bbstools.kernel.KernelBruecke;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin
{
    @Shadow
    public ServerPlayer player;

    @Inject(method = "parseCommand", at = @At("HEAD"))
    public void onParse(String command, CallbackInfoReturnable<ParseResults<CommandSourceStack>> info)
    {
        /* this.player ist @Shadow und bleibt hier — der Kernel sieht das Feld nicht, er bekommt es. */
        KernelBruecke.rufe("SpielerAktionenLogik", "befehlGetippt", this.player, command);
    }

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult redirectOnBlockInteract(ServerPlayerGameMode manager, ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, BlockHitResult hitResult)
    {
        KernelBruecke.rufe("SpielerAktionenLogik", "blockBenutzt", this.player, hitResult, hand);

        /* ⚠️ @Redirect ERSETZT den Aufruf. Diese Zeile IST das Original — faellt sie weg,
         * passiert der Rechtsklick auf den Block nicht mehr. Sie bleibt deshalb im Mixin. */
        return manager.useItemOn(player, world, stack, hand, hitResult);
    }
}