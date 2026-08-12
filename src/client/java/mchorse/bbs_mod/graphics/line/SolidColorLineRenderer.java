package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Matrix4f;

public class SolidColorLineRenderer implements ILineRenderer
{
    private static SolidColorLineRenderer INSTANCE = new SolidColorLineRenderer();

    private Color color = new Color();

    public static ILineRenderer get(float r, float g, float b, float a)
    {
        return INSTANCE.setColor(r, g, b, a);
    }

    public static ILineRenderer get(Color color)
    {
        return INSTANCE.setColor(color);
    }

    public SolidColorLineRenderer setColor(float r, float g, float b, float a)
    {
        this.color.set(r, g, b, a);

        return this;
    }

    public SolidColorLineRenderer setColor(Color color)
    {
        this.color.copy(color);

        return this;
    }

    /** Farbe als ARGB-Schnappschuss — der Singleton wird je Aufruf umgefaerbt,
     *  ein spaeter laufendes Lambda darf ihn deshalb nie selbst lesen. */
    public int argb()
    {
        return ((int) (this.color.a * 255F) << 24) | ((int) (this.color.r * 255F) << 16)
            | ((int) (this.color.g * 255F) << 8) | (int) (this.color.b * 255F);
    }

    @Override
    public void render(VertexConsumer builder, Matrix4f matrix, LinePoint point)
    {
        builder.addVertex(matrix, point.x, point.y, 0F).setColor(this.color.r, this.color.g, this.color.b, this.color.a);
    }
}