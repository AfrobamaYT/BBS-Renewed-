package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * vor1205: Basis fuer BBS-eigene VertexConsumer, die die 1.21-API (addVertex/set*) selbst
 * umsetzen. Die alten abstrakten Methoden von 1.20.4 leiten dorthin um; endVertex entfaellt
 * (die 1.21-API kennt keins), ebenso die Vorgabefarbe.
 */
public abstract class NeuerVertexVerbraucher implements VertexConsumer
{
    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        return this.addVertex((float) x, (float) y, (float) z);
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a)
    {
        return this.setColor(r, g, b, a);
    }

    @Override
    public VertexConsumer uv(float u, float v)
    {
        return this.setUv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v)
    {
        return this.setUv1(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v)
    {
        return this.setUv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this.setNormal(x, y, z);
    }

    @Override
    public void endVertex()
    {}

    @Override
    public void defaultColor(int r, int g, int b, int a)
    {}

    @Override
    public void unsetDefaultColor()
    {}
}
