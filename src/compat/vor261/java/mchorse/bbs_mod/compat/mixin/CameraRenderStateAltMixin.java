package mchorse.bbs_mod.compat.mixin;

import mchorse.bbs_mod.compat.CameraRenderStateAlt;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CameraRenderState.class)
public abstract class CameraRenderStateAltMixin implements CameraRenderStateAlt
{
    @Unique
    private final Matrix4f bbs$projektion = new Matrix4f();
    @Unique
    private final Matrix4f bbs$blick = new Matrix4f();
    @Unique
    private float bbs$xRot;
    @Unique
    private float bbs$yRot;

    @Override
    public void bbs$setMatrizen(Matrix4f projektion, Matrix4f blick, float xRot, float yRot)
    {
        this.bbs$projektion.set(projektion);
        this.bbs$blick.set(blick);
        this.bbs$xRot = xRot;
        this.bbs$yRot = yRot;
    }

    @Override
    public Matrix4f bbsProjectionMatrix()
    {
        return this.bbs$projektion;
    }

    @Override
    public Matrix4f bbsViewRotationMatrix()
    {
        return this.bbs$blick;
    }

    @Override
    public float bbsXRot()
    {
        return this.bbs$xRot;
    }

    @Override
    public float bbsYRot()
    {
        return this.bbs$yRot;
    }
}
