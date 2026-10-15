package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.resources.model.ModelManager;
import mchorse.bbs_mod.compat.ModelManagerAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ModelManager.class)
public abstract class ModelManagerAltMixin implements ModelManagerAlt
{}
