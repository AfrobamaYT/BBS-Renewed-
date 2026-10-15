package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Vor 1.21.2: TextureTarget fragt den clearError-Schalter ab, die Projektion nimmt nur die Sortierung. */
public final class Ziele
{
    private Ziele()
    {}

    public static TextureTarget neu(int breite, int hoehe)
    {
        return new TextureTarget(breite, hoehe, true, Minecraft.ON_OSX);
    }

    public static void projektion(Matrix4f matrix, ProjectionType art)
    {
        RenderSystem.setProjectionMatrix(matrix, art.vertexSorting());
    }
}
