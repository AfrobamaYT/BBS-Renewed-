package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.CameraAlt;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Camera.class)
public abstract class CameraAltMixin implements CameraAlt
{}
