package mchorse.bbs_mod.compat.alt;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/**
 * Minecraft 26.2's LightCoordsUtil, for the versions before 26.1: the packing is the same as
 * LightTexture's there, the level lookup is LevelRenderer.getLightColor.
 */
public class LightCoordsUtil
{
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    public static final int FULL_SKY = LightTexture.FULL_SKY;

    public static int pack(int block, int sky)
    {
        return LightTexture.pack(block, sky);
    }

    public static int block(int packed)
    {
        return LightTexture.block(packed);
    }

    public static int sky(int packed)
    {
        return LightTexture.sky(packed);
    }

    public static int max(int coords1, int coords2)
    {
        return pack(Math.max(block(coords1), block(coords2)), Math.max(sky(coords1), sky(coords2)));
    }

    public static int lightCoordsWithEmission(int lightCoords, int emission)
    {
        /* Wie Mojangs LightTexture.lightCoordsWithEmission (1.21.11, am Bytecode abgelesen), die es vor 1.21.2 nicht gibt. */
        if (emission == 0)
        {
            return lightCoords;
        }

        return pack(Math.max(block(lightCoords), emission), Math.max(sky(lightCoords), emission));
    }

    public static int getLightCoords(BlockAndTintGetter level, BlockPos pos)
    {
        return LevelRenderer.getLightColor(level, pos);
    }
}
