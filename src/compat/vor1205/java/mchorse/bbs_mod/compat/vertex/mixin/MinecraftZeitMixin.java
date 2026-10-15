package mchorse.bbs_mod.compat.vertex.mixin;

import mchorse.bbs_mod.compat.vertex.MinecraftZeit;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public abstract class MinecraftZeitMixin implements MinecraftZeit
{}
