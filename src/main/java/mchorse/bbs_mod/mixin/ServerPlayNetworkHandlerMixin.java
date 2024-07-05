package mchorse.bbs_mod.mixin;

import com.mojang.brigadier.ParseResults;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.CommandActionClip;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin
{
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "parse", at = @At("HEAD"))
    public void onParse(String command, CallbackInfoReturnable<ParseResults<ServerCommandSource>> info)
    {
        BBSMod.getActions().addAction(this.player, () ->
        {
            CommandActionClip clip = new CommandActionClip();

            clip.command.set(command);

            return clip;
        });
    }
}