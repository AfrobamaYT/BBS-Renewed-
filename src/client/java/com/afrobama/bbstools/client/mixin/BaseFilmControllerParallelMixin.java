// Coded by Afrobama/Claude — Tier 1e Parallel Animator.
//
// Targets {@link mchorse.bbs_mod.film.BaseFilmController#startRenderFrame(float)}
// — the per-frame loop that walks every replay and runs
// {@code FormProperties.resetProperties} + {@code applyProperties} on
// the replay's Form. This is the CPU-bound Molang + keyframe
// interpolation hot-path that scales linearly with replay count and
// pins a single render-thread core at >1500 actors.
//
// Strategy: fan the per-replay body across
// {@link java.util.concurrent.ForkJoinPool#commonPool()} via
// {@code parallelStream().forEach}. The actor-bound branch (which
// touches {@code MinecraftClient.world.getEntityById} and mutates
// {@code PlayerEntity.setYaw/Pitch/etc.}) is NOT thread-safe with
// respect to the MC entity world, so we defer it to a serial post-
// pass on the render thread.
//
// THREADING AUDIT
// ---------------
// Per-replay work that runs inside the parallel task:
//   1. canUpdate(i, replay, entity, UpdateMode.PROPERTIES) — pure read.
//   2. getTransition(entity, transition) — returns float, no mutation.
//   3. replay.getTick(getTick()) — pure read.
//   4. entity.getForm() — read.
//   5. replay.properties.resetProperties(form) — mutates Form's
//      KeyframeChannel.runtimeValue; each replay has its OWN
//      FormProperties + Form copy (see BaseFilmController.createEntities
//      which calls FormUtils.copy per replay), so no cross-thread races.
//   6. replay.properties.applyProperties(form, tick + delta) — same
//      ownership story: each replay's FormProperties walk only touches
//      that replay's own Form copy.
//
// Per-replay work that we DO NOT run in parallel (deferred to serial):
//   - actors.get(replay.getId()) → MinecraftClient.getInstance().world
//     .getEntityById(...): touches the MC entity world, never safe off
//     the render thread.
//   - player.setYaw / setHeadYaw / setPitch / setBodyYaw / prevYaw=...:
//     PlayerEntity field writes — must stay on the render thread.
//   - actor.getForm() → resetProperties/applyProperties on the actor's
//     LIVE form: also needs to stay serial because the ActorEntity is
//     shared with the world tick loop.
//
// GL state: none of the above touches GL. GL state writes happen later
// in BaseFilmController.render(WorldRenderContext) and downstream
// FormUtilsClient.render — those are NOT inside startRenderFrame.
// Confirmed by grepping startRenderFrame body for any
// RenderSystem / GL11 / framebuffer call: NO MATCHES.
//
// Shared mutable state we ThreadLocal-ified:
//   - CubicModelAnimator.p/s/r — three static Vector3d scratch fields
//     (BBS upstream). See {@link com.afrobama.bbstools.perf.AnimatorScratch}.
//     Note: startRenderFrame does NOT directly call CubicModelAnimator
//     so the static fields are dormant during our parallel region.
//     The scaffolding is in place for a future Tier 1f that parallelizes
//     the actual cubic animator.
//   - MolangParser.setValue: the parser instance is owned by a Model
//     (model.parser); replays sharing the same Model would race here
//     if startRenderFrame's body invoked Molang variable setup. The
//     properties walk does NOT call MolangHelper.setMolangVariables —
//     that runs further down inside the actual model render path
//     (FormUtilsClient.render → ModelFormRenderer → ModelInstance.render)
//     which stays SERIAL on the render thread. We don't touch it.
//
// Registered as "BaseFilmControllerParallelMixin" in
// bbstools.client.mixins.json.
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import io.netty.util.collection.IntObjectMap;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerParallelMixin
{
    @Shadow
    @Final
    public mchorse.bbs_mod.film.Film film;

    @Shadow
    protected IntObjectMap<IEntity> entities;

    @Shadow
    protected abstract boolean canUpdate(int i, Replay replay, IEntity entity, BaseFilmController.UpdateMode updateMode);

    @Shadow
    protected abstract float getTransition(IEntity entity, float transition);

    @Shadow
    public abstract int getTick();

    @Shadow
    public abstract Map<String, Integer> getActors();

    @Inject(method = "startRenderFrame", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$parallelStartRenderFrame(float transition, CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.BaseFilmControllerParallelLogik und ist damit
         * hotswapbar — siehe MixinBruecke-javadoc.
         *
         * @Shadow-felder (film, entities) sind stabile BBS/netty-typen und werden direkt
         * durchgereicht. @Shadow-METHODEN (canUpdate/getTransition/getTick/getActors) bleiben
         * als closures, weil sie an DIESE BaseFilmController-instanz gebunden sind — der
         * kernel kennt den typ nicht in seiner signatur (rule 1/3).
         *
         * rufeUndAbbrechen liefert bei einem fehler false (= originale sequentielle schleife
         * läuft weiter) statt eine NPE beim unboxing eines null-rückgabewerts zu riskieren. */
        if (MixinBruecke.rufeUndAbbrechen("BaseFilmControllerParallelLogik", "parallelStartRenderFrame",
            transition,
            this.film,
            this.entities,
            (com.afrobama.blendertransform.BaseFilmControllerParallelLogik.CanUpdate)
                (i, replay, entity) -> this.canUpdate(i, replay, entity, BaseFilmController.UpdateMode.PROPERTIES),
            (com.afrobama.blendertransform.BaseFilmControllerParallelLogik.GetTransition)
                this::getTransition,
            (com.afrobama.blendertransform.BaseFilmControllerParallelLogik.GetTick)
                this::getTick,
            (com.afrobama.blendertransform.BaseFilmControllerParallelLogik.GetActors)
                this::getActors))
        {
            ci.cancel();
        }
    }

}
