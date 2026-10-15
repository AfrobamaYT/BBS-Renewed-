package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distance-based render LOD for crowd-tagged entities. Cancels render entirely
 * past 320 blocks, hides body parts (nose, hat_rim, hat, jacket, arms) in
 * progressive distance bands. Saves ~25-35% vertex transforms at 5000+ tagged
 * villagers.
 *
 * Migrated from BBS-CML-EDITION fork's mchorse.bbs_mod.mixin.client.CrowdLODMixin.
 *
 * <h2>⚠️ 26.2 — was sich geaendert hat</h2>
 *
 * <p>{@code render(entity, yaw, teilTick, stapel, verbraucher, licht)} heisst
 * {@code submit(zustand, stapel, sammler, kamera)}, und die ENTITY steht dort nicht mehr in
 * der Signatur. Sie kommt aus der Zusatzablage am Zustand, die
 * {@code EntityRenderDispatcherMixin} beim Einsammeln fuellt
 * ({@code MorphRenderer.entityVon}). Fehlt sie — ein Zustand, der nicht ueber den Dispatcher
 * lief —, gilt Vanilla-Verhalten.
 *
 * <p><b>Das ABBRECHEN wirkt unveraendert:</b> hier haengen Rumpfmodell, Ebenen und
 * Namensschild dran; wird {@code submit} abgebrochen, wird fuer die Entity nichts eingereicht
 * und also nichts gezeichnet — wie vorher.
 *
 * <p><b>⚠️ DAS TEILE-VERSTECKEN WIRKT HIER NICHT MEHR</b> (gemessen, nicht vermutet):
 * {@code ModelPart.skipDraw} liest Vanilla erst beim ZEICHNEN, und das passiert seit 26.2
 * spaeter — in {@code ModelFeatureRenderer.prepareModel}, das vorher noch einmal
 * {@code setupAnim} ruft. Was {@code submit} an den Teilen verstellt, ist beim Zeichnen
 * laengst wieder zurueckgesetzt (der TAIL-Haken unten stellt es korrekt wieder her, also
 * bleibt kein Schaden zurueck — aber auch keine Wirkung). Das Abstands-Culling, der grosse
 * Teil der Ersparnis, bleibt vollstaendig.
 *
 * <p>Wo die versteckte Haelfte hin muesste: derselbe Haken wie in
 * {@code ModelFeatureRendererMixin} (nach {@code Model.setupAnim}, zurueck vor
 * {@code RETURN}). Das braucht in {@code CrowdLodLogik} einen getrennten Eingang fuer
 * "nur Cullen" und "nur Teile", weil sonst am Sammel-Punkt Teile versteckt wuerden, die dort
 * niemand zuruecksetzt. {@code CrowdLodLogik} liegt ausserhalb dieses Auftrags — gemeldet.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CrowdLODMixin
{
    @Shadow @Final protected EntityModel<?> model;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void bbs$crowdLODCullAndHide(LivingEntityRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.CrowdLodLogik und ist damit hotswapbar — siehe
         * MixinBruecke-javadoc. @Shadow-feld `model` bleibt im mixin (rule 3) und wird
         * durchgereicht. */
        Entity entity = MorphRenderer.entityVon(zustand);

        if (!(entity instanceof LivingEntity lebendig))
        {
            return;
        }

        if (MixinBruecke.rufeUndAbbrechen("CrowdLodLogik", "crowdLODCullAndHide", lebendig, this.model))
        {
            ci.cancel();
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("TAIL"))
    private void bbs$crowdLODRestore(LivingEntityRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera, CallbackInfo ci)
    {
        MixinBruecke.rufe("CrowdLodLogik", "crowdLODRestore");
    }

}
