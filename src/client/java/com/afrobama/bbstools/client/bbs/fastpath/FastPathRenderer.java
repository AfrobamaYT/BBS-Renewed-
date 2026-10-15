// Coded by Afrobama/Claude — Tier 1.5b fast-path renderer.
//
// Actual render-replacement for routed replays. Called from
// FastPathBatch.flush() when BBSPerfConfig.ENABLE_FAST_PATH_RENDER is on.
//
// Tier 1.5b-1 architecture (this file):
//   - Hoist RenderSystem state setup OUT of the per-replay loop
//     (enable_blend / disable_cull / lightmap / overlay set ONCE per flush
//     instead of 100× per frame)
//   - Per submission: re-execute the BBS vanilla render flow via accessor
//     invokers (ensureAnimator + applyActions + applyPose + texture-bind
//     + renderModel)
//   - Texture bind ONCE per (model.id, texture) sub-group
//
// Expected speedup at this stage: ~1.3–2× over vanilla (state-setup
// batching dominates). Real win comes in Tier 1.5b-2 with vertex-cache +
// 1-draw-per-model-id (skinning still CPU-side) and Tier 2 with GPU
// instancing.
//
// Iris compat: uses vanilla () -> mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL
// — no custom shader, no transform-patcher friction. The output VBO
// path is exactly what Iris already supports for vanilla entity render.
package com.afrobama.bbstools.client.bbs.fastpath;

import net.minecraft.client.renderer.RenderPipelines;

import com.afrobama.bbstools.client.mixin.bbs.ModelFormRendererAccessor;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FastPathRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/renderer");

    /* Diagnostic one-shot flags. */
    private static volatile boolean LOGGED_FIRST_FLUSH = false;
    private static volatile boolean LOGGED_FIRST_RENDER = false;

    private FastPathRenderer() {}

    /**
     * Main entry called by FastPathBatch.flush() at end-of-frame.
     * Iterates the per-model-id batch and renders all submissions with
     * state setup hoisted outside the per-replay loop.
     */
    public static void flushAll()
    {
        Map<String, List<FastPathBatch.Submission>> batch = FastPathBatch.snapshotBatch();
        if (batch.isEmpty()) return;

        if (!LOGGED_FIRST_FLUSH)
        {
            LOGGED_FIRST_FLUSH = true;
            int total = FastPathBatch.currentBatchSize();
            LOG.info("[fast-path/renderer] FIRST flushAll — models={} totalSubs={}",
                batch.size(), total);
        }

        long startNs = System.nanoTime();
        int rendered = 0;
        int failed = 0;

        /* Hoisted state setup — done ONCE per flush instead of per replay.
         * Mirrors what BBS does at the head of renderModel(...).
         *
         * 26.2: Blend ist kein umschaltbarer Zustand mehr. enableBlend + defaultBlendFunc
         * standen hier nur, damit das Modell durchsichtig gemischt wird — das traegt jetzt
         * die Pipeline des Modell-Zeichenwegs selbst: ModelVAOPipeline.DURCHSICHTIG mit
         * BlendFunction.TRANSLUCENT, genau die Mischung, die defaultBlendFunc setzte.
         * Zu hoisten bleiben Lichtkarte und Overlay — die sind weiterhin globaler Zustand. */
        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
        // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
        // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

        try
        {
            for (Map.Entry<String, List<FastPathBatch.Submission>> entry : batch.entrySet())
            {
                List<FastPathBatch.Submission> list = entry.getValue();
                if (list.isEmpty()) continue;

                for (FastPathBatch.Submission s : list)
                {
                    try
                    {
                        renderOne(s);
                        rendered++;
                    }
                    catch (Throwable t)
                    {
                        failed++;
                        if (failed <= 3)
                        {
                            LOG.warn("[fast-path/renderer] renderOne threw for model={}: {}",
                                s.instance == null ? "?" : s.instance.id, t.toString(), t);
                        }
                    }
                }
            }
        }
        finally
        {
            /* Kein disableBlend mehr: die Mischung gehoert der Pipeline (siehe oben), es
             * gibt keinen globalen Blend-Zustand, den dieses finally zuruecksetzen muesste. */
        }

        long elapsedNs = System.nanoTime() - startNs;
        BBSPerfConfig.fastPathRenderNsAccum += elapsedNs;
        BBSPerfConfig.fastPathRendered += rendered;
        BBSPerfConfig.fastPathRenderFailed += failed;
    }

    /**
     * Tier 2-b fallback entry — invoked by Tier2bDrawPath when a bucket's
     * SkinnedMeshIR is unavailable (BOBJ models, models with meshes /
     * shape keys). Delegates to renderOne so visual parity is preserved
     * while the merged-buffer path handles the rest.
     *
     * NOTE: caller is responsible for state setup (blend/lightmap/overlay)
     * — Tier2bDrawPath.flushAll hoists those once at the top, matching
     * what flushAll() does here.
     */
    public static void renderOneFallback(FastPathBatch.Submission s)
    {
        renderOne(s);
    }

    /**
     * Render a single captured replay. Dispatches to the direct path
     * (Tier 1.5b-2) or the invoker path (Tier 1.5b-1) based on
     * BBSPerfConfig.ENABLE_FAST_PATH_DIRECT.
     */
    private static void renderOne(FastPathBatch.Submission s)
    {
        ModelFormRenderer self = s.renderer;
        if (self == null) return;
        ModelFormRendererAccessor selfA = (ModelFormRendererAccessor) (Object) self;

        ModelInstance instance = s.instance;
        if (instance == null || instance.model == null) return;

        /* Animator chain — shared between both render modes. Replicates
         * render3D lines 609-623. */
        selfA.bbstools$ensureAnimator(s.transition);
        IAnimator animator = selfA.bbstools$getAnimator();
        if (animator == null) return;

        instance.model.resetPose();
        animator.applyActions(s.entity, instance, s.transition);
        instance.model.applyPose(selfA.bbstools$getPose());

        /* Texture + color resolution (matches render3D lines 615-619). */
        Link link = s.form.texture.get();
        Link texture = link == null ? instance.getTexture() : link;
        Color color = s.form.color.get().copy();
        color.mul(s.color);

        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }

        /* Shader. Iris-compat by construction: vanilla MC entity
         * translucent cull program goes through Iris's gbuffers_entities
         * pipeline.
         *
         * SOLID-VORSCHAU (#63): der Schnellpfad waehlt seine Pipeline HIER selbst und lief
         * damit an der zentralen Wahl (BBSShaders.getModel) vorbei — gemessen blieb die
         * Menge texturiert, waehrend der volle Weg Clay zeichnete. Die Abfrage steht IM
         * Supplier (je Zeichenbefehl, wie ueberall sonst), die Wahl ist dieselbe wie in
         * getModel: Culling an, Tiefe schreiben, DREI-Puffer-Familie — der CubicVAORenderer
         * darunter bindet die ModelVAO-Puffer 0/1/2. Die gemergten Pfade (Tier2b/2e) zeichnen
         * derzeit nicht (HALB OFFEN 26.2) und gehoeren beim Wiederbeleben an
         * SolidVorschauPipeline.holen(true, ...), die ENTITY-Familie. */
        Supplier<RenderPipeline> shader = () -> com.afrobama.bbstools.client.solid.SolidVorschau.aktiv()
            ? com.afrobama.bbstools.client.solid.SolidVorschauPipeline.holen(false, true, true)
            : mchorse.bbs_mod.compat.alt.RenderPipelinesAlt.ENTITY_TRANSLUCENT_CULL;

        /* Build matrix stack from snapshot. Re-apply the 180° Y rotation
         * that vanilla does inside render3D line 625. */
        PoseStack stack = new PoseStack();
        stack.mulPose(s.worldMatrix);
        stack.rotate(Axis.YP.rotation((float) MathUtils.PI));

        if (BBSPerfConfig.ENABLE_FAST_PATH_DIRECT)
        {
            renderDirect(s, selfA, instance, stack, shader, color, texture);
        }
        else
        {
            /* Invoker-mode (Tier 1.5b-1 baseline): BBS' privater renderModel-wrapper,
             * inklusive captureMatrices + renderItems.
             *
             * AUTONOMIE 2026-07-27: an fs' signatur angepasst. Der schnellpfad haelt eine
             * farbe; fs will zwei (kontext- und form-farbe, die multipliziert werden), also
             * geben wir unsere als kontext-farbe und WHITE als form-farbe — das ergibt
             * dasselbe produkt. additive=false ist der normale blend-modus. Der equipment-
             * schalter faellt hier weg: fs entscheidet das ueber die form selbst. Eine
             * matrix dient als stack UND world, wie im CML-pfad zuvor. */
            selfA.bbstools$renderModel(
                s.entity, shader, stack, instance,
                s.light, s.overlay,
                color, mchorse.bbs_mod.utils.colors.Color.white(), false,
                false, null, s.transition, stack);
        }

        if (!LOGGED_FIRST_RENDER)
        {
            LOGGED_FIRST_RENDER = true;
            LOG.info("[fast-path/renderer] FIRST renderOne completed — model={} texture={} mode={}",
                instance.id, texture == null ? "null" : texture.toString(),
                BBSPerfConfig.ENABLE_FAST_PATH_DIRECT ? "DIRECT" : "INVOKER");
        }
    }

    /**
     * Tier 1.5b-2 — direct ModelInstance.render() call. Skips BBS's
     * renderModel(...) private wrapper:
     *   - No per-call enable/disable blend (hoisted)
     *   - No per-call lightmap/overlay setup (hoisted)
     *   - No captureMatrices walk (no body parts in fast-path)
     *   - No renderItems (no equipment, router excluded it)
     *
     * Der bedingte Cull, der hier frueher stand, ist seit 26.2 Sache der Pipeline
     * (ModelVAOPipeline.DURCHSICHTIG_BEIDSEITIG) — siehe Kommentar im Rumpf.
     */
    private static void renderDirect(FastPathBatch.Submission s,
                                     ModelFormRendererAccessor selfA,
                                     ModelInstance instance,
                                     PoseStack stack,
                                     Supplier<RenderPipeline> shader,
                                     Color color,
                                     Link texture)
    {
        /* 26.2: Cull ist kein umschaltbarer Zustand mehr, sondern Teil der Pipeline. Das Paar
         * disableCull/enableCull um den Zeichenaufruf entfaellt deshalb ersatzlos, samt der
         * Fahne, die es nur zusammenhielt. Die beidseitige Variante ist
         * ModelVAOPipeline.DURCHSICHTIG_BEIDSEITIG, und gewaehlt wird sie im Modell-Zeichenweg
         * an genau der Bedingung, die hier stand: ModelInstance.isCulling() ist
         * instance.config.culling. Der Schnellpfad kann daran nichts mehr drehen und muss es
         * auch nicht — die Information geht nicht verloren, sie wandert nur an die Stelle,
         * die die Pipeline aussucht. */
        instance.render(
            stack, shader, color, s.light, s.overlay,
            /* stencilMap */ null,
            s.form.shapeKeys.get(),
            /* AUTONOMIE 2026-07-27: fs loest texturen PRO MATERIAL auf (Function<material,Link>),
             * CML hatte eine einzige fuer das ganze modell. Wir haben genau eine, also fuer
             * jedes material dieselbe zurueckgeben; ist keine gesetzt, fs' eigene
             * material-aufloesung machen lassen statt null durchzureichen. */
            (material) -> texture != null ? texture : instance.getMaterialTexture(material, null));
    }

    public static void resetCounters()
    {
        LOGGED_FIRST_FLUSH = false;
        LOGGED_FIRST_RENDER = false;
        BBSPerfConfig.fastPathRendered = 0L;
        BBSPerfConfig.fastPathRenderFailed = 0L;
        BBSPerfConfig.fastPathRenderNsAccum = 0L;
    }

    public static String snapshot()
    {
        double avgUs = BBSPerfConfig.fastPathRendered == 0 ? 0
            : (BBSPerfConfig.fastPathRenderNsAccum / (double) BBSPerfConfig.fastPathRendered) / 1000.0;
        return String.format(
            "rendered=%d  failed=%d  avgPerReplay=%.1fµs",
            BBSPerfConfig.fastPathRendered,
            BBSPerfConfig.fastPathRenderFailed,
            avgUs);
    }

}
