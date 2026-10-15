package mchorse.bbs_mod.compat.main.mixin;

import mchorse.bbs_mod.compat.main.KomponentenAlt;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** vor1205: haengt KomponentenAlt (Data-Component-Zugriff auf NBT) an ItemStack. */
@Mixin(ItemStack.class)
public abstract class ItemStackKomponentenMixin implements KomponentenAlt
{}
