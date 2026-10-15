package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.AbstractTextureAlt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractTexture.class)
public abstract class AbstractTextureAltMixin implements AbstractTextureAlt
{}
