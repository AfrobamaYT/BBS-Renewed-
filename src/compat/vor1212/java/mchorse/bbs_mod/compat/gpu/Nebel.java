package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogParameters;

/** Vor 1.21.2: der Nebel steht einzeln in RenderSystem (Start, Ende, Form, Farbe) und wird hier zusammengesetzt. */
public final class Nebel
{
    private Nebel()
    {}

    public static FogParameters aktuell()
    {
        float[] farbe = RenderSystem.getShaderFogColor();

        return new FogParameters(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), RenderSystem.getShaderFogShape(),
            farbe[0], farbe[1], farbe[2], farbe[3]);
    }
}
