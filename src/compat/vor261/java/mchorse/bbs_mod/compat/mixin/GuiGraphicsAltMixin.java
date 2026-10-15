package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.gui.GuiGraphics;
import mchorse.bbs_mod.compat.GuiGraphicsAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsAltMixin implements GuiGraphicsAlt
{}
