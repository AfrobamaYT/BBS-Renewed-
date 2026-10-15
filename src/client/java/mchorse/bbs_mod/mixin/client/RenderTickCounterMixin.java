package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.blendertransform.AufnahmeBildtaktLogik;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.Timer.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float partialTick;

    @Shadow
    public float tickDelta;

    @Shadow
    private long lastMs;

    private int heldFrames;

    private long lastFrameTime;

    /**
     * ⚠️ 26.2: {@code advanceTime(long, boolean)} ist in ZWEI Methoden zerlegt worden --
     * {@code advanceGameTime(long)} (gibt die Tickzahl zurueck) und
     * {@code advanceRealTime(long)}. Der Boolean WAR die Fallunterscheidung; jetzt sagt der
     * Methodenname, welche Zeit gemeint ist.
     *
     * <p>Dieser Haken zwingt beim Video-Export einen festen Zeitschritt auf und braucht die
     * SPIELZEIT -- also {@code advanceGameTime}. Die Echtzeit bleibt unangetastet.</p>
     */
    @Inject(method = "advanceTime", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> info)
    {
        /* Fuenf felder, ein aufruf: drei @Shadow, zwei vom mixin angebaute. Sie gehen als
         * WERTE rein und raus — feldLesen/feldSchreiben waeren zehn reflexionsaufrufe je bild.
         * Die indizes stehen als konstanten in der logik. */
        double[] neu = (double[]) MixinBruecke.rufe("AufnahmeBildtaktLogik", "takt",
            timeMillis, this.partialTick, this.tickDelta, this.lastMs,
            this.heldFrames, this.lastFrameTime);

        if (neu == null)
        {
            return;
        }

        this.partialTick = (float) neu[AufnahmeBildtaktLogik.REST];
        this.tickDelta = (float) neu[AufnahmeBildtaktLogik.TAKTE];
        this.lastMs = (long) neu[AufnahmeBildtaktLogik.LETZTE_MS];
        this.heldFrames = (int) neu[AufnahmeBildtaktLogik.GEHALTENE];
        this.lastFrameTime = (long) neu[AufnahmeBildtaktLogik.LETZTE_BILDZEIT];

        int rueckgabe = (int) neu[AufnahmeBildtaktLogik.RUECKGABE];

        if (rueckgabe != AufnahmeBildtaktLogik.KEINE_RUECKGABE)
        {
            info.setReturnValue(rueckgabe);
        }
    }
}
