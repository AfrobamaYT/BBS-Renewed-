package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemTimeUniforms.Timer.class)
public class SystemTimeUniformsTimerMixin
{
    @Shadow
    private float frameTimeCounter;

    @Shadow
    private float lastFrameTime;

    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, remap = false)
    public void onBeginFrame(CallbackInfo info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            float videoFrameRate = BBSRendering.getVideoFrameRate();

            this.lastFrameTime = 20F / videoFrameRate;
            this.frameTimeCounter = (videoRecorder.getCounter() / videoFrameRate) % 3600F;

            info.cancel();
        }
    }
}