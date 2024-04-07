package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float tickDelta;

    @Shadow
    public float lastFrameDuration;

    @Shadow
    private long prevTimeMillis;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> info)
    {
        if (BBSModClient.getVideoRecorder().isRecording())
        {
            int counter = BBSModClient.getVideoRecorder().getCounter();

            if (counter == 0)
            {
                this.tickDelta = 0;
            }

            this.lastFrameDuration = 0.3333333F;
            this.prevTimeMillis = timeMillis;
            this.tickDelta += this.lastFrameDuration;

            int i = (int) this.tickDelta;

            this.tickDelta -= (float) i;

            BBSRendering.serverTicks += i;

            info.setReturnValue(i);
        }
    }
}