package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.ScreenRectangleAlt;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ScreenRectangle.class)
public abstract class ScreenRectangleAltMixin implements ScreenRectangleAlt
{}
