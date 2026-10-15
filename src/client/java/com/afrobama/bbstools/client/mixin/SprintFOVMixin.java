// Coded by Afrobama/Claude — re-enable vanilla sprint FOV multiplier in BBS editor mode
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legt vanillas sprint-multiplikator wieder auf das sichtfeld, waehrend BBS es ersetzt hat.
 *
 * <h2>26.2: aus GameRenderer.getFov wurde Camera.calculateFov</h2>
 *
 * <p>{@code GameRenderer.getFov(Camera, float, boolean)} gibt es nicht mehr. Die kamera
 * rechnet ihr sichtfeld selbst, und der alte boolesche parameter {@code changingFov} ist zur
 * METHODENWAHL geworden:</p>
 *
 * <pre>
 *   getFov(.., changingFov = true)   ->  Camera.calculateFov(float)      welt
 *   getFov(.., changingFov = false)  ->  Camera.calculateHudFov(float)   hand/HUD
 * </pre>
 *
 * <p>Dieser handler haengt nur an {@code calculateFov}. Das ist keine kuerzung, sondern der
 * unveraenderte gueltigkeitsbereich: die logik stieg schon immer bei {@code changingFov ==
 * false} sofort aus. Der uebergebene wert ist darum konstant {@code true} — sonst wuerde der
 * einzige aufruf, den es noch gibt, gleich wieder aussteigen.</p>
 *
 * <h2>⚠️ Zwei befunde, die vor dem naechsten anfassen zu lesen sind</h2>
 *
 * <p><b>1. Die begruendung im urspruenglichen javadoc traegt nicht.</b> Dort stand, dieser
 * mixin laufe wegen der hoeheren prioritaet NACH dem BBS-injektor und koenne dessen wert
 * umhuellen. Das stimmt nicht: BBS ruft {@code setReturnValue}, das cancelt den callback, und
 * mixin ueberspringt danach jeden weiteren RETURN-injektor derselben methode — unabhaengig
 * von der prioritaet (gemessen 2026-07-28, festgehalten in
 * {@code mchorse.bbs_mod.mixin.client.CameraMixin#bbsFov}). Dieser handler laeuft also NUR,
 * wenn KEINE BBS-kamera aktiv ist. Genau dort hat er aber nichts zu reparieren.</p>
 *
 * <p><b>2. In dem einen zweig, den er erreicht, verdoppelt er.</b> {@code calculateFov}
 * multipliziert selbst schon mit {@code lerp(oldFovModifier, fovModifier)} — das IST vanillas
 * sprint-boost (26.2-bytecode gelesen). {@code SprintFovLogik} legt seinen eigenen,
 * gleich berechneten faktor noch einmal darauf. Das ist kein neuer schaden aus dem
 * 26.2-umstieg — {@code getFov} tat in 1.21.1 dasselbe —, aber es ist der grund, warum diese
 * datei einen bewussten entscheid braucht statt eines weiteren ports. Der befund ist an den
 * leiter gemeldet; geloescht wird sie nicht im vorbeigehen.</p>
 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class SprintFOVMixin
{
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void bbstools$reapplySprintFov(float tickDelta, CallbackInfoReturnable<Float> info)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in die zielklasse
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * gesamte smoothing-logik (inkl. mc/player-zugriff) liegt in
         * `com.afrobama.blendertransform.SprintFovLogik` und wird bei jedem swap mitgetauscht.
         *
         * Die brücke bleibt auf double, obwohl das sichtfeld jetzt ein float ist: die logik
         * ist damit unveraendert und kernel-seitig nichts nachzuziehen. Der handle-aufruf
         * verbreitert float -> double von selbst. */
        Object neu = MixinBruecke.rufe("SprintFovLogik", "apply",
            Minecraft.getInstance(), true, tickDelta, (double) info.getReturnValue());

        if (neu instanceof Double d) info.setReturnValue(d.floatValue());
    }

}
