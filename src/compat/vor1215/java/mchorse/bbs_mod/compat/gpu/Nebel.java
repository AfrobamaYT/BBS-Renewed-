package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogParameters;

/** Der aktuelle Nebel als FogParameters (1.21.4: RenderSystem.getShaderFog); vor 1.21.2 ersetzt vor1212 diese Datei. */
public final class Nebel
{
    private Nebel()
    {}

    public static FogParameters aktuell()
    {
        return RenderSystem.getShaderFog();
    }
}
