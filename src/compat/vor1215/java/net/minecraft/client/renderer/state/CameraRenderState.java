package net.minecraft.client.renderer.state;

import mchorse.bbs_mod.compat.CameraRenderStateAlt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: der Kamerazustand eines Bildes wie 1.21.11, dazu die
 * 26.2-Felder (Projektion, Blickdrehung, Winkel). LevelRendererAltMixin fuellt ihn zu Beginn
 * von renderLevel aus 1.21.4s Kamera und Matrizen.
 */
public class CameraRenderState implements CameraRenderStateAlt
{
    public BlockPos blockPos = BlockPos.ZERO;
    public Vec3 pos = Vec3.ZERO;
    public boolean initialized;
    public Vec3 entityPos = Vec3.ZERO;
    public Quaternionf orientation = new Quaternionf();

    private final Matrix4f projektion = new Matrix4f();
    private final Matrix4f blick = new Matrix4f();
    private float xRot, yRot;

    @Override
    public void bbs$setMatrizen(Matrix4f projektion, Matrix4f blick, float xRot, float yRot)
    {
        this.projektion.set(projektion);
        this.blick.set(blick);
        this.xRot = xRot;
        this.yRot = yRot;
    }

    @Override public Matrix4f bbsProjectionMatrix() { return this.projektion; }
    @Override public Matrix4f bbsViewRotationMatrix() { return this.blick; }
    @Override public float bbsXRot() { return this.xRot; }
    @Override public float bbsYRot() { return this.yRot; }
}
