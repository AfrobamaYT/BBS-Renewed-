package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.color.block.BlockColors;
import mchorse.bbs_mod.compat.BlockColorsAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockColors.class)
public abstract class BlockColorsAltMixin implements BlockColorsAlt
{}
