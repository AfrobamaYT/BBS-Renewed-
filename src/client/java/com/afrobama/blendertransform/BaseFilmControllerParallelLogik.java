package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.bbs.fastpath.ActorWork;
import com.afrobama.bbstools.sonden.Meldesonde;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import io.netty.util.collection.IntObjectMap;

import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Logik des Tier-1e-parallel-animator-mixins — herausgelöst aus
 * {@code BaseFilmControllerParallelMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Ziel: {@link BaseFilmController#startRenderFrame(float)} — die pro-frame-schleife, die
 * jeden replay durchläuft und {@code FormProperties.resetProperties} + {@code applyProperties}
 * auf dem form des replays läuft. Das ist der CPU-lastige Molang+keyframe-interpolations-
 * hot-path, der linear mit der replay-zahl skaliert und bei >1500 actors einen einzelnen
 * render-thread-core auslastet.</p>
 *
 * <p>Strategie: den per-replay-teil über {@link java.util.concurrent.ForkJoinPool#commonPool()}
 * via {@code parallelStream().forEach} fächern. Der actor-gebundene zweig (der
 * {@code MinecraftClient.world.getEntityById} anfasst und {@code PlayerEntity.setYaw/Pitch/etc.}
 * mutiert) ist NICHT thread-sicher bezüglich der MC-entity-welt, deshalb läuft er als serieller
 * post-pass auf dem render-thread.</p>
 *
 * <h2>THREADING-AUDIT</h2>
 * Per-replay-arbeit, die INNERHALB des parallelen tasks läuft:
 * <ol>
 *   <li>canUpdate(i, replay, entity, UpdateMode.PROPERTIES) — reiner read.</li>
 *   <li>getTransition(entity, transition) — liefert float, keine mutation.</li>
 *   <li>replay.getTick(getTick()) — reiner read.</li>
 *   <li>entity.getForm() — read.</li>
 *   <li>replay.properties.resetProperties(form) — mutiert Form's KeyframeChannel.runtimeValue;
 *       jeder replay hat seine EIGENE FormProperties + Form-kopie (siehe
 *       BaseFilmController.createEntities, das FormUtils.copy pro replay ruft), also keine
 *       cross-thread-races.</li>
 *   <li>replay.properties.applyProperties(form, tick + delta) — gleiche eigentums-geschichte:
 *       jeder replay's FormProperties-durchlauf fasst nur die eigene Form-kopie an.</li>
 * </ol>
 * Per-replay-arbeit, die NICHT parallel läuft (auf seriell verschoben):
 * <ul>
 *   <li>actors.get(replay.getId()) → MinecraftClient.getInstance().world.getEntityById(...):
 *       fasst die MC-entity-welt an, nie sicher ausserhalb des render-threads.</li>
 *   <li>player.setYaw / setHeadYaw / setPitch / setBodyYaw / prevYaw=...: PlayerEntity-feld-
 *       schreibzugriffe — müssen auf dem render-thread bleiben.</li>
 *   <li>actor.getForm() → resetProperties/applyProperties auf dem LEBENDEN form des actors:
 *       muss auch seriell bleiben, weil die ActorEntity mit dem welt-tick-loop geteilt wird.</li>
 * </ul>
 *
 * <p>GL-state: nichts oben fasst GL an. GL-state-schreibzugriffe passieren später in
 * BaseFilmController.render(WorldRenderContext) und nachgelagert FormUtilsClient.render — die
 * liegen NICHT innerhalb von startRenderFrame.</p>
 *
 * <p>⚠ {@code @Shadow}-felder/-methoden des mixins ({@code film}, {@code entities},
 * {@code canUpdate}, {@code getTransition}, {@code getTick}, {@code getActors}) bleiben im
 * mixin (rule 3) — sie sind an die BaseFilmController-instanz gebunden und der kernel kennt
 * den typ nicht in seiner signatur. Der handler liest sie vorab und reicht die werte + kleine
 * closures (JDK-funktionale interfaces, kein kernel-typ) durch.</p>
 */
public final class BaseFilmControllerParallelLogik
{
    private BaseFilmControllerParallelLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/parallel-animator");
    private static volatile long lastFallbackLogMs;

    /** Schmale schnittstelle auf die shadow-methoden des mixins — vom handler mit `this::` gebaut. */
    public interface CanUpdate
    {
        boolean test(int i, Replay replay, IEntity entity);
    }

    public interface GetTransition
    {
        float apply(IEntity entity, float transition);
    }

    public interface GetTick
    {
        int get();
    }

    public interface GetActors
    {
        Map<String, Integer> get();
    }

    /**
     * Einstiegspunkt des mixin-handlers. Übernimmt auch die arbeit, die vorher im mixin vor
     * dem brücken-aufruf lag (film→replays lesen, entities→entryList snapshotten, skip-gate
     * berechnen) — {@code film} und {@code entities} sind stabile BBS/netty-typen, dürfen also
     * in der kernel-signatur stehen; nur die @Shadow-METHODEN (canUpdate/getTransition/getTick/
     * getActors) bleiben als closures, weil sie an die konkrete BaseFilmController-instanz
     * gebunden sind.
     *
     * @return {@code true}, wenn der aufrufer {@code ci.cancel()} rufen soll (parallel-pfad hat
     *         den frame übernommen)
     */
    public static boolean parallelStartRenderFrame(
        float transition,
        Film film,
        IntObjectMap<IEntity> entities,
        CanUpdate canUpdate,
        GetTransition getTransition,
        GetTick getTick,
        GetActors getActors)
    {
        if (!BBSPerfConfig.ENABLE_PARALLEL_ANIMATOR) return false;
        if (film == null || entities == null || entities.isEmpty()) return false;

        List<Replay> replays;
        try
        {
            replays = film.replays.getList();
        }
        catch (Throwable t)
        {
            Meldesonde.HALTER.aktiv.verschluckt("BaseFilmControllerParallelMixin.replays.getList", t);
            return false;
        }
        if (replays == null || replays.isEmpty()) return false;

        /* Snapshot der einträge, damit der parallele stream eine stabile sicht durchläuft;
         * IntObjectMap.entrySet() ist während der iteration nicht thread-sicher teilbar. */
        List<Map.Entry<Integer, IEntity>> entries = new ArrayList<>(entities.entrySet());

        int tickNow = getTick.get();
        Map<String, Integer> actors = getActors.get();

        /* SKIP-BAKED-PROPERTIES-gate (ENABLE_SKIP_BAKED_PROPERTIES). Wenn der render-loop-
         * bypass die crowd gerade aktiv aus dem bone-bake zeichnet, wird die lebende form-pose
         * eines VOLLSTÄNDIG gebackenen replays nie gelesen (Tier2hInstancedDraw liefert bei
         * einem hit gecachte matrizen) — reset+applyProperties unten wäre also totes gewicht.
         * Das ganze gate ist O(1): crowdBakeComplete ist das vom bypass veröffentlichte
         * dirty-flag (der frühere per-replay-firstUncachedTick-check kostete 10.9% — genau die
         * wand, die er vermeiden sollte). gen/size binden das flag an den aktuellen bake +
         * crowd; die bypass-drew-wache stellt sicher, dass die crowd diesen frame nachweislich
         * aus dem bake kommt (eine gemischte/actor-szene bricht den bypass ab → rendert die
         * crowd normal → liest die lebende pose → kein skip). Nur der SELEKTIERTE replay ist
         * pro replay ausgenommen (rendert live fürs bearbeiten). */
        boolean skipEnabled =
               BBSPerfConfig.ENABLE_SKIP_BAKED_PROPERTIES
            && BBSPerfConfig.ENABLE_RENDER_LOOP_BYPASS
            && BBSPerfConfig.ENABLE_TIER2_H
            && BBSPerfConfig.ENABLE_ARMY_BAKE
            && BBSPerfConfig.crowdBakeComplete
            && BBSPerfConfig.crowdBakeCompleteGen == BBSPerfConfig.tier2hVatRebuilds
            && BBSPerfConfig.crowdBakeCompleteSize == entities.size()
            && (BBSPerfConfig.bbstoolsRenderFrameId - BBSPerfConfig.bbstoolsLastBypassDrawFrame) <= 1L;
        Object selRef = BBSPerfConfig.currentEntityRef;

        long t0 = System.nanoTime();
        List<ActorWork> actorTodo = new CopyOnWriteArrayList<>();

        try
        {
            entries.parallelStream().forEach(entry ->
            {
                int i = entry.getKey();
                IEntity entity = entry.getValue();
                if (entity == null) return;
                if (i < 0 || i >= replays.size()) return;
                Replay replay = replays.get(i);
                if (replay == null) return;

                /* SKIP nicht-selektierte replays, wenn die crowd vollständig gebacken ist
                 * (crowdBakeComplete, O(1) über skipEnabled geprüft): ihre lebende pose wird
                 * nicht genutzt (wird aus dem bone-bake gezeichnet). crowdBakeComplete wird
                 * nur gesetzt, wenn der bypass in einem vollen durchlauf NULL replays
                 * versteckt hat, ein noch-nicht-gebackener replay kann also nicht übersprungen
                 * werden (das flag ist false, bis die ganze crowd fertig ist). Der selektierte
                 * replay rendert immer live fürs bearbeiten. */
                if (skipEnabled && entity != selRef)
                {
                    BBSPerfConfig.skippedBakedProps++;
                    return;
                }

                if (!canUpdate.test(i, replay, entity))
                {
                    return;
                }

                float delta = getTransition.apply(entity, transition);
                int tick = replay.getTick(tickNow);
                float tickPlusDelta = tick + delta;

                Form form1 = entity.getForm();
                if (form1 != null)
                {
                    try
                    {
                        replay.properties.resetProperties(form1);
                        replay.properties.applyProperties(form1, tickPlusDelta);
                    }
                    catch (Throwable th)
                    {
                        throttledLog("per-replay properties threw, skipping: " + th);
                        Meldesonde.HALTER.aktiv.verschluckt("BaseFilmControllerParallelMixin.applyProperties", th);
                    }
                }

                /* Alles actor-seitige: auf den seriellen pass verschieben, damit es auf dem
                 * render-thread läuft (fasst die MC-entity-welt an). */
                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());
                    if (entityId != null)
                    {
                        actorTodo.add(new ActorWork(i, replay, entity, entityId, tickPlusDelta));
                    }
                }
            });

            /* PASS 2 — serieller render-thread-teil für actor-gebundene replays. Spiegelt die
             * originale BBS-logik exakt. */
            if (!actorTodo.isEmpty())
            {
                for (ActorWork aw : actorTodo)
                {
                    try
                    {
                        runActorPass(aw);
                    }
                    catch (Throwable th)
                    {
                        throttledLog("actor pass threw for replay " + aw.i() + ": " + th);
                        Meldesonde.HALTER.aktiv.verschluckt("BaseFilmControllerParallelMixin.actorPass", th);
                    }
                }
            }

            BBSPerfConfig.parallelAnimatorTicks++;
            BBSPerfConfig.bbsStartRenderFrameNsAccum += (System.nanoTime() - t0);
            BBSPerfConfig.bbsStartRenderFrameFrames++;

            return true;
        }
        catch (Throwable t)
        {
            BBSPerfConfig.parallelAnimatorFallbacks++;
            LOG.warn("[parallel-animator] fallback to sequential: {}", t.toString());
            /* NICHT abbrechen — die originale sequentielle schleife weiterlaufen lassen. */
            return false;
        }
    }

    private static void runActorPass(ActorWork aw)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        Entity anEntity = mc.level.getEntity(aw.entityId());
        if (anEntity == null) return;

        if (anEntity instanceof mchorse.bbs_mod.entity.ActorEntity actor)
        {
            Form form = actor.getForm();
            if (form != null)
            {
                aw.replay().properties.resetProperties(form);
                aw.replay().properties.applyProperties(form, aw.tickPlusDelta());
            }
        }
        else if (anEntity instanceof Player player)
        {
            Morph morph = Morph.getMorph(player);
            if (morph != null)
            {
                Form form = morph.getForm();
                if (form != null)
                {
                    aw.replay().properties.resetProperties(form);
                    aw.replay().properties.applyProperties(form, aw.tickPlusDelta());
                }
            }

            float yawHead = aw.replay().keyframes.headYaw.interpolate(aw.tickPlusDelta()).floatValue();
            float yawBody = aw.replay().keyframes.bodyYaw.interpolate(aw.tickPlusDelta()).floatValue();
            float pitch = aw.replay().keyframes.pitch.interpolate(aw.tickPlusDelta()).floatValue();

            player.setYRot(yawHead);
            player.setYHeadRot(yawHead);
            player.setXRot(pitch);
            player.setYBodyRot(yawBody);
            player.yRotO = yawHead;
            player.yHeadRotO = yawHead;
            player.xRotO = pitch;
            player.yBodyRotO = yawBody;
        }
    }

    private static void throttledLog(String reason)
    {
        long now = System.currentTimeMillis();
        if (now - lastFallbackLogMs > 60_000L)
        {
            lastFallbackLogMs = now;
            LOG.info("[parallel-animator] {}", reason);
        }
    }
}
