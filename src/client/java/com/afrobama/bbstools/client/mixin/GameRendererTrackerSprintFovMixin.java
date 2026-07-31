// Coded by Afrobama/Claude — apply sprint FOV multiplier during BBS
// replay playback in editor mode. ITER 24 REWRITE: bypass upstream's
// TrackerClientClip.updateSprintFov pipeline entirely (it's not being
// fed in user's BBS-CML setup — log confirmed isSprinting stays
// false on the static state field). Instead read
// TrackerClientClip.currentEntity.isSprinting() directly each frame
// and synthesize our own smoothed multiplier. This works as long as
// some part of BBS sets currentEntity to the replay's StubEntity
// (which it does — applyClip line 1036 of upstream).
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⚠️ DER KLASSENNAME LUEGT SEIT 26.2: das ziel ist {@link Camera}, nicht {@code GameRenderer}.
 *
 * <p>{@code GameRenderer.getFov(Camera, float, boolean)} gibt es nicht mehr; das sichtfeld
 * rechnet die kamera selbst ({@code Camera.calculateFov} fuer die welt,
 * {@code Camera.calculateHudFov} fuer die hand). Umbenannt wird die datei hier nicht, weil der
 * name in {@code bbstools.client.mixins.json} steht und die registrierung nur der leiter
 * anfasst — die umbenennung ist gemeldet.</p>
 *
 * <h2>Nur noch an der WELT, nicht mehr an der hand</h2>
 *
 * <p>Frueher traf dieser handler beide sichtfelder, weil beide durch dieselbe methode liefen.
 * Jetzt haengt er nur an {@code calculateFov}. Das ist absicht und deckt sich mit beiden
 * seiten: vanilla legt auf die hand ueberhaupt keinen sichtfeld-effekt mehr (konstante 70°),
 * und BBS rechnet den sprint-anteil aus dem hand-sichtfeld sogar bewusst wieder heraus
 * (schalter "Hand follows sprint FOV"). Ein sprint-boost auf der hand wuerde also gegen die
 * eigene einstellung arbeiten.</p>
 *
 * <h2>⚠️ Im produktpfad ist dieser handler wirkungslos — gemessen, nicht vermutet</h2>
 *
 * <p>Er feuert nur, solange der BBS-injektor {@code CameraMixin#bbsFov} den rueckgabewert
 * NICHT ersetzt hat: {@code setReturnValue} cancelt den callback, danach ueberspringt mixin
 * jeden weiteren RETURN-injektor, egal welche prioritaet er traegt (gemessen 2026-07-28 — die
 * beiden dort erwaehnten wirkungslosen anlaeufe mit prioritaet 3000 bzw. 4000 sind genau
 * dieser mixin). Sein eigenes tor {@code levelRenderingActive} ist umgekehrt nur offen,
 * waehrend der tracker-clip rendert — was eine aktive BBS-kamera VORAUSSETZT. Die beiden
 * bedingungen schliessen einander aus.</p>
 *
 * <p>Erreichbar bleibt allein der handprobe-pfad {@code SprintFovState.MANUAL_SPRINT}, der in
 * {@code SprintFovTrackerLogik} VOR dem tor steht und das sichtfeld pauschal mit 1,3
 * multipliziert. Das produkt-verhalten liefert heute {@code PositionFovLogik.sprintFaktor()}
 * innerhalb des BBS-injektors. Der befund ist an den leiter gemeldet; entfernt wird der mixin
 * nicht nebenbei in einer runde, die nur die ziele nachzieht.</p>
 */
@Mixin(value = Camera.class, priority = 3000)
public abstract class GameRendererTrackerSprintFovMixin
{
    /* Manual override lives in tracker.SprintFovState — Mixins can't
     *  have non-private static fields. */

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void bbstools$applyTrackerSprintFov(float tickDelta, CallbackInfoReturnable<Float> cir)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in die zielklasse
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * reflection-probe + die gesamte smoothing-logik liegen in
         * `com.afrobama.blendertransform.SprintFovTrackerLogik` und werden bei jedem swap
         * mitgetauscht.
         *
         * Die brücke bleibt auf double, obwohl das sichtfeld jetzt ein float ist: die logik
         * ist damit unveraendert und kernel-seitig nichts nachzuziehen. */
        Object neu = MixinBruecke.rufe("SprintFovTrackerLogik", "apply",
            com.afrobama.bbstools.client.tracker.SprintFovState.MANUAL_SPRINT,
            net.minecraft.client.Minecraft.getInstance(), tickDelta,
            (double) cir.getReturnValue());

        if (neu instanceof Double d) cir.setReturnValue(d.floatValue());
    }

}
