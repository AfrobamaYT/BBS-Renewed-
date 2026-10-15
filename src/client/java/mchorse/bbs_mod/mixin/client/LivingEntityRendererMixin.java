package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MobFormPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BBS' Form-Pose auf einen Vanilla-Mob legen — die EINREICH-Haelfte.
 *
 * <p>⚠️ 26.2 — DIESER HAKEN HAT ZWEI HAELFTEN STATT EINER. Bis 1.21.1 stand hier alles:
 * nach {@code setupAnim} die Modellteile um die Pose ergaenzen, und am Ende von
 * {@code render} zuruecksetzen. {@code render} heisst jetzt {@code submit} — aber das
 * {@code setupAnim} des RUMPFMODELLS ist gar nicht mehr hier drin. Es ist in die Zeichenphase
 * gewandert ({@code ModelFeatureRenderer.prepareModel}, am Bytecode geprueft); was in
 * {@code submit} noch danach greift, sind nur die EBENEN (Ruestung, Kleidung), fuer die
 * Vanilla direkt vor der Ebenen-Schleife einmal {@code setupAnim} ruft.
 *
 * <p>Deshalb hier drei Haken statt zwei:
 *
 * <ol>
 *   <li>Kopf: die Pose an den Zustand stempeln, damit sie die Zeichenphase erreicht —
 *       {@code MobFormRenderer}s Pose-Fenster ist nur waehrend des Einreichens offen.</li>
 *   <li>nach {@code setupAnim}: auflegen, damit die EBENEN mit der Pose eingereicht werden.</li>
 *   <li>Ende: wieder herunternehmen — die Teile gehoeren dem geteilten Modell des Renderers.</li>
 * </ol>
 *
 * <p>Die RUMPF-Haelfte sitzt in {@code ModelFeatureRendererMixin}. Beide rufen dieselbe
 * Logik in {@link MobFormPose}.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin
{
    /**
     * ⚠️ Der Stempel MUSS auch dann laufen, wenn gerade keine Form gerendert wird: er setzt
     * dann {@code null} und raeumt damit einen wiederverwendeten Zustand auf. Ein Ueberspringen
     * wuerde die Pose der vorigen Form an einer fremden Entity hinterlassen.
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    public void bbs$poseAnDenZustand(LivingEntityRenderState zustand, PoseStack matrixStack, SubmitNodeCollector sammler, CameraRenderState kamera, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "stempeln", zustand);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    public void onSetAngles(LivingEntityRenderState zustand, PoseStack matrixStack, SubmitNodeCollector sammler, CameraRenderState kamera, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "auflegen", zustand);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    public void onRenderEnd(LivingEntityRenderState zustand, PoseStack matrixStack, SubmitNodeCollector sammler, CameraRenderState kamera, CallbackInfo info)
    {
        MixinBruecke.rufe("LivingEntityPoseLogik", "zuruecksetzen");
    }
}
