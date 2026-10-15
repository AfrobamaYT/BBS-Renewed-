package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.platform.Window;
import mchorse.bbs_mod.compat.WindowAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Window.class)
public abstract class WindowAltMixin implements WindowAlt
{}
