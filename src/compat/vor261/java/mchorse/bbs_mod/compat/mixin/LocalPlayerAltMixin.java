package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.player.LocalPlayer;
import mchorse.bbs_mod.compat.LocalPlayerAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAltMixin implements LocalPlayerAlt
{}
