package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.ItemStackRenderStateAlt;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateAltMixin implements ItemStackRenderStateAlt
{}
