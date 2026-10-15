package mchorse.bbs_mod.mixin.client.vitrail;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.vitrail.render.FrameState", remap = false)
public abstract class VitrailFrameStateMixin
{
    @Shadow private long lastFrameNanos;
    @Shadow private boolean timed;
    @Shadow private int frameCounter;
    @Shadow private float frameTime;
    @Shadow private float frameTimeCounter;

    @Inject(method = "advanceClock()V", at = @At("HEAD"), cancellable = true)
    private void bbs$filmTime(CallbackInfo ci) throws Throwable
    {
        double[] next = (double[]) MixinBruecke.rufePflicht("VitrailExportClockLogik", "advance",
            this.frameCounter, this.frameTimeCounter);
        if (next == null) return;
        this.frameCounter = (int) next[0];
        this.frameTime = (float) next[1];
        this.frameTimeCounter = (float) next[2];
        /* Resume the native clock from this frame, never from before a long video export. */
        this.lastFrameNanos = System.nanoTime();
        this.timed = true;
        ci.cancel();
    }
}
