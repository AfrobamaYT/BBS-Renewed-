package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BBS' Form-Pose auf einen Vanilla-Mob legen — die ZEICHEN-Haelfte.
 *
 * <p>⚠️ NEU IN 26.2, KEIN PORT EINER ALTEN DATEI. Bis 1.21.1 gab es diese Stelle nicht: ein
 * Renderer posierte sein Modell und zeichnete es im selben Aufruf, und
 * {@code LivingEntityRendererMixin} kam dazwischen. In 26.2 wird beim Einreichen nur ein
 * Auftrag angelegt; posiert und gezeichnet wird der RUMPF erst hier, in
 * {@code ModelFeatureRenderer.prepareModel}:
 *
 * <pre>
 *   model.setupAnim(zustand);                 &lt;-- Haltung entsteht (Vanilla)
 *   model.renderToBuffer(stapel, ...);        &lt;-- gezeichnet
 * </pre>
 *
 * <p>Genau zwischen diesen beiden Zeilen gehoert BBS' Pose aufgelegt — und vor der Rueckkehr
 * wieder herunter, weil die {@code ModelPart}e dem geteilten Modell des Renderers gehoeren
 * und der naechste Auftrag mit denselben zeichnet.
 *
 * <p>Die Pose kommt vom Zustand ({@code MobFormPose.POSE_AM_ZUSTAND}); gestempelt hat sie
 * {@code LivingEntityRendererMixin} beim Einreichen. Traegt der Zustand keine, kehrt
 * {@link MobFormPose#auflegen(Object)} sofort zurueck — dieser Haken laeuft fuer JEDES
 * eingereichte Modell der Welt.
 */
@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin
{
    @Inject(
        method = "prepareModel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbs$poseAuflegen(ModelFeatureRenderer.Submit<?> auftrag, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "auflegen", auftrag.state());
    }

    @Inject(method = "prepareModel", at = @At("RETURN"))
    private void bbs$poseHerunternehmen(ModelFeatureRenderer.Submit<?> auftrag, CallbackInfo info)
    {
        MixinBruecke.rufe("ModelFeatureRendererLogik", "zuruecksetzen");
    }
}
