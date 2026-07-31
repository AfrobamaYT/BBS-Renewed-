package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⚠️ 26.2 — DIESER HAKEN HAT ZWEI HAELFTEN STATT EINER.
 *
 * <p>Bis 1.21.1 hing er an {@code EntityRenderDispatcher.render(...)} und umschloss dort
 * {@code EntityRenderer.render(entity, ...)} — die Entity lag mit auf dem Tisch. In 26.2
 * heisst der Aufruf {@code submit(zustand, ...)} und sieht nur noch den Render-Zustand.
 * Deshalb:
 *
 * <ol>
 *   <li>{@link #bbs$merkeEntity} legt die Entity beim EINSAMMELN am Zustand ab
 *       (Fabrics {@code RenderStateDataKey}, siehe {@code MorphRenderer.ENTITY_AM_ZUSTAND}),</li>
 *   <li>{@link #bbs$umschliesseSubmit} holt sie beim EINREICHEN wieder heraus.</li>
 * </ol>
 *
 * <p>⚠️ HIER LIEGT JETZT AUCH DER SPIELER-HAKEN. Er sass bis 1.21.1 in
 * {@code PlayerEntityRendererMixin} am Kopf von {@code PlayerRenderer.render}. {@code
 * PlayerRenderer} heisst in 26.2 {@code AvatarRenderer} und ueberschreibt {@code submit}
 * ueberhaupt nicht mehr (am jar geprueft) — ein Injektor auf eine dort nicht deklarierte
 * Methode scheitert beim Weben. Die Stelle, an der {@code PlayerRenderer.render} frueher
 * gerufen wurde, ist genau der Aufruf, den dieser Haken umschliesst; die Pose steht dort
 * identisch. Beide Faelle (Spieler-Morph, SelectorOwner) laufen deshalb hier durch.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin
{
    /**
     * Legt die Entity am frisch gesammelten Zustand ab.
     *
     * <p>Das ist der Vanilla-Trichter fuer alle Welt-Entities: {@code
     * LevelExtractor.extractEntity} ruft genau diese Methode (am Bytecode geprueft).
     */
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void bbs$merkeEntity(Entity entity, float teilTick, CallbackInfoReturnable<EntityRenderState> cir)
    {
        EntityRenderState zustand = cir.getReturnValue();

        if (zustand != null)
        {
            ((FabricRenderState) zustand).setData(MorphRenderer.ENTITY_AM_ZUSTAND, entity);
        }
    }

    @WrapOperation(
        method = "submit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
        )
    )
    private void bbs$umschliesseSubmit(EntityRenderer<?, ?> renderer, EntityRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera, Operation<Void> original)
    {
        Entity entity = MorphRenderer.entityVon(zustand);

        if (entity instanceof LivingEntity lebendig)
        {
            /* ⚠️ Der Teil-Tick ist kein Parameter mehr — submit kennt ihn nicht. Der
             * DeltaTracker traegt aber denselben Wert wie beim Einsammeln: beides passiert
             * im selben Bild, dazwischen wird er nicht weitergedreht. */
            float teilTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

            /* ⚠️ VERLUST, BEWUSST UND GEMELDET: hier stand der Weiss-Anteil der
             * Ueberlagerung aus getWhiteOverlayProgress (Ladungsblitz des Creepers,
             * Trefferblitz). Der Invoker dafuer heisst LivingEntityRendererInvoker und traegt
             * noch die 1.21.1-Signatur (LivingEntity, float); in 26.2 nimmt die Methode den
             * Render-Zustand. Ein @Invoker auf eine nicht existierende Signatur laesst das
             * Weben scheitern — also wird er hier NICHT mehr benutzt. Bis er nachgezogen ist,
             * ist der Weiss-Anteil 0; der rote Anteil (Schaden/Tod) stimmt. */
            int overlay = OverlayTexture.pack(0F, lebendig.hurtTime > 0 || lebendig.deathTime > 0);

            if (lebendig instanceof AbstractClientPlayer spieler
                && MorphRenderer.renderPlayer(spieler, 0F, teilTick, matrices, sammler, zustand.lightCoords))
            {
                return;
            }

            if (MorphRenderer.renderLivingEntity(lebendig, 0F, teilTick, matrices, sammler, zustand.lightCoords, overlay))
            {
                return;
            }
        }

        original.call(renderer, zustand, matrices, sammler, kamera);
    }
}
