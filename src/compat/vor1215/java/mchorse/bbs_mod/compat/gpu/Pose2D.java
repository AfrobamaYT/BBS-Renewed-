package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

/**
 * Die 2D-Pose von GuiGraphics ab 1.21.6 (Matrix3x2fStack) als Sicht auf 1.21.4s PoseStack.
 * Der PoseStack bleibt die Wahrheit, weil Vanilla-Zeichnungen ihn lesen: jede Abfrage
 * (GuiGraphicsAlt.bbsPose) liest ihn neu ein, jede Veraenderung schreibt den 2D-Anteil
 * zurueck, Push/Pop laufen auf beiden Stapeln. Z-Verschiebung und Rest bleiben unberuehrt.
 */
public class Pose2D extends Matrix3x2fStack
{
    private final PoseStack stapel;

    public Pose2D(PoseStack stapel)
    {
        super(64);

        this.stapel = stapel;
    }

    public Pose2D einlesen()
    {
        Matrix4f m = this.stapel.last().pose();

        super.set(m.m00(), m.m01(), m.m10(), m.m11(), m.m30(), m.m31());

        return this;
    }

    private Pose2D zurueck()
    {
        Matrix4f m = this.stapel.last().pose();

        m.m00(this.m00).m01(this.m01).m10(this.m10).m11(this.m11).m30(this.m20).m31(this.m21);

        return this;
    }

    @Override
    public Matrix3x2fStack pushMatrix()
    {
        this.stapel.pushPose();

        return super.pushMatrix();
    }

    @Override
    public Matrix3x2fStack popMatrix()
    {
        this.stapel.popPose();
        super.popMatrix();

        return this.einlesen();
    }

    @Override public Matrix3x2f translate(float x, float y) { super.translate(x, y); return this.zurueck(); }
    @Override public Matrix3x2f translateLocal(float x, float y) { super.translateLocal(x, y); return this.zurueck(); }
    @Override public Matrix3x2f scale(float x, float y) { super.scale(x, y); return this.zurueck(); }
    @Override public Matrix3x2f scale(float xy) { super.scale(xy); return this.zurueck(); }
    @Override public Matrix3x2f scaleLocal(float x, float y) { super.scaleLocal(x, y); return this.zurueck(); }
    @Override public Matrix3x2f rotate(float winkel) { super.rotate(winkel); return this.zurueck(); }
    @Override public Matrix3x2f identity() { super.identity(); return this.zurueck(); }
}
