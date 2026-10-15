package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⚠️ 26.2 — {@code PlayerRenderer} heisst jetzt {@code AvatarRenderer} (Paket unveraendert:
 * {@code net.minecraft.client.renderer.entity.player}), und die Klasse ist generisch ueber
 * "alles, was ein Avatar ist" — Spieler wie Schaufensterpuppen.
 *
 * <p>⚠️ ZWEI HAKEN AUS DIESER DATEI SIND UMGEZOGEN ODER UMGEBAUT:
 *
 * <ul>
 *   <li><b>Der Abbruch beim Zeichnen des gemorphten Spielers</b> sass am Kopf von
 *       {@code render}. {@code AvatarRenderer} ueberschreibt {@code submit} (den Nachfolger)
 *       nicht mehr — ein Injektor darauf laesst das Weben scheitern. Er liegt jetzt in
 *       {@code EntityRenderDispatcherMixin}, an genau dem Aufruf, aus dem {@code
 *       PlayerRenderer.render} frueher gerufen wurde.</li>
 *   <li><b>Der Arm-Haken</b> hing an {@code renderHand(PoseStack, ..., Spieler, arm, aermel)}.
 *       Diese Methode ist in 26.2 privat und kennt weder Spieler noch Aermel; oeffentlich
 *       sind {@code renderRightHand}/{@code renderLeftHand}. Also je ein Haken pro Hand.
 *       ⚠️ VERHALTENSAENDERUNG: die Zuordnung Arm→Hand kam frueher aus dem Modell des
 *       gerenderten Spielers ({@code getModel().rightArm == arm}), jetzt aus der gerufenen
 *       Methode — dasselbe Ergebnis. Der SPIELER kommt jetzt aber nicht mehr als Argument,
 *       sondern ist {@code Minecraft.getInstance().player}. Fuer die Ich-Perspektive (der
 *       einzige Aufrufer im Spiel, {@code ItemInHandRenderer}) ist das derselbe Spieler; ein
 *       fremder Avatar wuerde jetzt die Form des lokalen Spielers bekommen.</li>
 * </ul>
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin
{
    /**
     * ⚠️ Voller Deskriptor, weil {@code AvatarRenderer} zusaetzlich eine
     * Brueckenmethode {@code getRenderOffset(EntityRenderState)} traegt — ohne ihn waere die
     * Zuordnung mehrdeutig.
     */
    @Inject(
        method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    public void onPositionOffset(AvatarRenderState zustand, CallbackInfoReturnable<Vec3> info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "versatzUnterdruecken", zustand))
        {
            info.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    public void onRenderRightArmBegin(PoseStack matrices, SubmitNodeCollector sammler, int licht, Identifier textur, boolean schlank, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "arm", matrices, sammler, licht, InteractionHand.MAIN_HAND))
        {
            info.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    public void onRenderLeftArmBegin(PoseStack matrices, SubmitNodeCollector sammler, int licht, Identifier textur, boolean schlank, CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("SpielerFormRendererLogik", "arm", matrices, sammler, licht, InteractionHand.OFF_HAND))
        {
            info.cancel();
        }
    }

}
