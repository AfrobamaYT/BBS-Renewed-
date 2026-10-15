package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** vor1205: 1.21s VertexConsumer-API (addVertex/set*) per Interface-Injection, siehe Vertexpuffer. */
public interface VertexNeu
{
    private VertexConsumer selbst()
    {
        return (VertexConsumer) this;
    }

    default VertexConsumer addVertex(float x, float y, float z)
    {
        Vertexpuffer.offen().beginnen(this.selbst(), x, y, z);

        return this.selbst();
    }

    default VertexConsumer addVertex(Vector3f lage)
    {
        return this.addVertex(lage.x, lage.y, lage.z);
    }

    default VertexConsumer addVertex(Matrix4f matrix, float x, float y, float z)
    {
        Vector3f lage = matrix.transformPosition(x, y, z, new Vector3f());

        return this.addVertex(lage.x, lage.y, lage.z);
    }

    default VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z)
    {
        return this.addVertex(pose.pose(), x, y, z);
    }

    default VertexConsumer addVertex(PoseStack.Pose pose, Vector3f lage)
    {
        return this.addVertex(pose.pose(), lage.x, lage.y, lage.z);
    }

    default void addVertex(float x, float y, float z, int farbe, float u, float v, int ueberlagerung, int licht, float nx, float ny, float nz)
    {
        this.addVertex(x, y, z);

        Vertexpuffer p = Vertexpuffer.offen();

        p.farbe(this.selbst(), farbe);
        p.uv(this.selbst(), u, v);
        p.ueberlagerung(this.selbst(), ueberlagerung);
        p.licht(this.selbst(), licht);
        p.normale(this.selbst(), nx, ny, nz);
    }

    default VertexConsumer setColor(int r, int g, int b, int a)
    {
        Vertexpuffer.offen().farbe(this.selbst(), (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255);

        return this.selbst();
    }

    default VertexConsumer setColor(float r, float g, float b, float a)
    {
        return this.setColor((int) (r * 255F), (int) (g * 255F), (int) (b * 255F), (int) (a * 255F));
    }

    default VertexConsumer setColor(int argb)
    {
        Vertexpuffer.offen().farbe(this.selbst(), argb);

        return this.selbst();
    }

    default VertexConsumer setUv(float u, float v)
    {
        Vertexpuffer.offen().uv(this.selbst(), u, v);

        return this.selbst();
    }

    default VertexConsumer setUv1(int u, int v)
    {
        Vertexpuffer.offen().ueberlagerung(this.selbst(), u & 0xFFFF | v << 16);

        return this.selbst();
    }

    default VertexConsumer setOverlay(int gepackt)
    {
        Vertexpuffer.offen().ueberlagerung(this.selbst(), gepackt);

        return this.selbst();
    }

    default VertexConsumer setUv2(int u, int v)
    {
        Vertexpuffer.offen().licht(this.selbst(), u & 0xFFFF | v << 16);

        return this.selbst();
    }

    default VertexConsumer setLight(int gepackt)
    {
        Vertexpuffer.offen().licht(this.selbst(), gepackt);

        return this.selbst();
    }

    default VertexConsumer setNormal(float x, float y, float z)
    {
        Vertexpuffer.offen().normale(this.selbst(), x, y, z);

        return this.selbst();
    }

    default VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z)
    {
        Vector3f n = pose.normal().transform(x, y, z, new Vector3f()).normalize();

        return this.setNormal(n.x, n.y, n.z);
    }
}
