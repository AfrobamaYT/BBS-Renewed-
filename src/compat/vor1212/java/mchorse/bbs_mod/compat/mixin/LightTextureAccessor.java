package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vor1212: die Lichtkarte ist noch eine DynamicTexture (16x16), kein TextureTarget. */
@Mixin(LightTexture.class)
public interface LightTextureAccessor
{
    @Accessor("lightTexture")
    DynamicTexture bbs$textur();
}
