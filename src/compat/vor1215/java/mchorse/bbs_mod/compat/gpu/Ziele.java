package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/** Neue Ziele und Projektion in 1.21.4-Form; vor 1.21.2 ersetzt vor1212 diese Datei (clearError, Sortierung). */
public final class Ziele
{
    private Ziele()
    {}

    public static TextureTarget neu(int breite, int hoehe)
    {
        return new TextureTarget(breite, hoehe, true);
    }

    public static void projektion(Matrix4f matrix, ProjectionType art)
    {
        RenderSystem.setProjectionMatrix(matrix, art);
    }
}
