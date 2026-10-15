package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.MinecraftAlt;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class MinecraftAltMixin implements MinecraftAlt
{}
