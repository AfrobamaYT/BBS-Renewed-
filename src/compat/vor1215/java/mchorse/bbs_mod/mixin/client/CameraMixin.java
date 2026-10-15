package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before 26.1 (replaces the shared CameraMixin): the camera follows its entity in
 * Camera.setup(level, entity, detached, mirrored, partialTick) instead of 26.2's
 * alignWithEntity; the field of view is computed in GameRenderer.getFov (compat
 * GameRendererFovMixin), not in the camera.
 */
@Mixin(Camera.class)
public abstract class CameraMixin
{
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At(value = "RETURN"))
    public void onUpdate(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float tickDelta, CallbackInfo ci)
    {
        double[] s = (double[]) MixinBruecke.rufe("KameraLogik", "stellung", tickDelta);

        if (s != null)
        {
            this.setPosition(s[0], s[1], s[2]);
            this.setRotation((float) s[3], (float) s[4]);
        }
    }
}
