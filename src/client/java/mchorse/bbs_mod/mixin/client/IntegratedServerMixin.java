package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin
{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void onTick(CallbackInfo info)
    {
        if (BBSModClient.getVideoRecorder().isRecording())
        {
            if (BBSRendering.lastServerTicks == BBSRendering.serverTicks)
            {
                info.cancel();
            }
            else
            {
                BBSRendering.lastServerTicks = BBSRendering.serverTicks;
            }
        }
    }
}