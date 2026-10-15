package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.gui.Gui;
import mchorse.bbs_mod.compat.GuiAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class GuiAltMixin implements GuiAlt
{}
