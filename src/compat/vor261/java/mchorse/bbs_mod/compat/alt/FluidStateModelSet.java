package mchorse.bbs_mod.compat.alt;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.FluidState;

/**
 * 26.2's FluidStateModelSet, for the versions before 26.1: Fabric's FluidRenderHandler (what the
 * shared code used before the 26.2 port) supplies the sprites and the tint. Sprites are asked
 * without world and position, which Fabric allows (item contexts do the same).
 */
public final class FluidStateModelSet
{
    public FluidModel get(FluidState fluid)
    {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());

        if (handler == null)
        {
            return null;
        }

        TextureAtlasSprite[] sprites = handler.getFluidSprites(null, null, fluid);

        return new FluidModel(material(sprites, 0), material(sprites, 1), material(sprites, 2),
            (state, world, pos) -> handler.getFluidColor(world, pos, fluid));
    }

    private static FluidModel.Material material(TextureAtlasSprite[] sprites, int index)
    {
        return sprites != null && index < sprites.length && sprites[index] != null ? new FluidModel.Material(sprites[index]) : null;
    }
}
