package net.minecraft.client.renderer;

import com.mojang.blaze3d.shaders.FogShape;

/** BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s Nebelparameter als Wert (1.21.1 haelt sie einzeln in RenderSystem). */
public record FogParameters(float start, float end, FogShape shape, float red, float green, float blue, float alpha)
{
    public static final FogParameters NO_FOG = new FogParameters(Float.MAX_VALUE, 0F, FogShape.SPHERE, 0F, 0F, 0F, 0F);
}
