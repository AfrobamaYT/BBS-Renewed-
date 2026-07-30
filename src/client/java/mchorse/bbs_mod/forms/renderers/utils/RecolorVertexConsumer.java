package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Matrix4f;

public class RecolorVertexConsumer implements VertexConsumer
{
    public static Color newColor;

    protected VertexConsumer consumer;
    protected Color color;

    public RecolorVertexConsumer(VertexConsumer consumer, Color color)
    {
        this.consumer = consumer;
        this.color = color;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z)
    {
        return this.consumer.addVertex(x, y, z);
    }

    @Override
    public VertexConsumer addVertex(Matrix4f matrix, float x, float y, float z)
    {
        return this.consumer.addVertex(matrix, x, y, z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha)
    {
        red = MathUtils.clamp((int) (this.color.r * red), 0, 255);
        green = MathUtils.clamp((int) (this.color.g * green), 0, 255);
        blue = MathUtils.clamp((int) (this.color.b * blue), 0, 255);
        alpha = MathUtils.clamp((int) (this.color.a * alpha), 0, 255);

        return this.consumer.setColor(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer setUv(float u, float v)
    {
        return this.consumer.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v)
    {
        return this.consumer.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v)
    {
        return this.consumer.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z)
    {
        return this.consumer.setNormal(x, y, z);
    }
}
