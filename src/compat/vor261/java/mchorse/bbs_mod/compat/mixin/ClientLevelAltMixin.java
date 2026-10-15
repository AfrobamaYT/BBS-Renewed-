package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import mchorse.bbs_mod.compat.ClientLevelAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientLevel.class)
public abstract class ClientLevelAltMixin implements ClientLevelAlt
{}
