package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.AbstractClientPlayerAlt;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerAltMixin implements AbstractClientPlayerAlt
{}
