package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.OverlayTextureAlt;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OverlayTexture.class)
public interface OverlayTextureAltMixin extends OverlayTextureAlt
{
    @Accessor("texture")
    DynamicTexture bbs$textur();
}
