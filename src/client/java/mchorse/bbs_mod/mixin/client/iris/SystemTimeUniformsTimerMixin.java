package mchorse.bbs_mod.mixin.client.iris;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemTimeUniforms.Timer.class)
public class SystemTimeUniformsTimerMixin
{
    @Shadow(remap = false)
    private float frameTimeCounter;

    @Shadow(remap = false)
    private float lastFrameTime;

    private int heldFrames;

    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, remap = false)
    public void onBeginFrame(CallbackInfo info)
    {
        /* Drei felder je bild — wie beim RenderTickCounter gehen sie als WERTE rein und
         * als array zurueck, statt ueber reflexion. */
        double[] neu = (double[]) MixinBruecke.rufe("AufnahmeBildtaktLogik", "shaderTakt",
            this.frameTimeCounter, this.lastFrameTime, this.heldFrames);

        if (neu == null)
        {
            return;
        }

        this.frameTimeCounter = (float) neu[0];
        this.lastFrameTime = (float) neu[1];
        this.heldFrames = (int) neu[2];

        if (neu[3] != 0)
        {
            info.cancel();
        }
    }
}
