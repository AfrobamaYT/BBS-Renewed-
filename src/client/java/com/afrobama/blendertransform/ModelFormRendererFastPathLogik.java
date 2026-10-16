package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.bbs.fastpath.CrowdRouter;
import com.afrobama.bbstools.client.bbs.fastpath.FastPathBatch;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik des fast-path-classify-mixins — herausgelöst aus {@code ModelFormRendererFastPathMixin}
 * (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>PRO-ENTITY-PFAD, DER TEUERSTE der sechs (3000+ render3D-aufrufe/frame bei army-scale — das
 * mixin, das den ganzen crowd-fast-path trägt). Die beiden {@code @Unique}-instanz-felder
 * ({@code cachedInstance}/{@code cachedReason}, an EINE {@code ModelFormRenderer}-instanz
 * gebunden) bleiben logisch beim aufrufer — diese methode bekommt sie als argumente und liefert
 * sie über das rückgabe-array aktualisiert zurück, statt sie selbst zu halten. {@code self}
 * (die {@code ModelFormRenderer}-instanz) ist ein BBS-typ im stabilen loader, kein kernel-typ —
 * unbedenklich als {@code Object} über die brücke zu reichen (siehe
 * {@link com.afrobama.bbstools.client.blender.MixinBruecke}-javadoc).</p>
 */
public final class ModelFormRendererFastPathLogik
{
    private ModelFormRendererFastPathLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/hook");

    private static volatile boolean DIAG_HOOK_FIRED;
    private static volatile boolean DIAG_THREW;

    /* ---- P4 bypass COMPARE harness ---- */
    private static final float[] CMP_A = new float[16];
    private static final float[] CMP_B = new float[16];
    private static long cmpLastLogMs = 0L;

    /**
     * @param self           die {@code ModelFormRenderer}-instanz ({@code (Object)(Object) this} im mixin)
     * @param context        render-context
     * @param cachedInstance {@code this.bbstools$cachedInstance} des aufrufers (kann {@code null} sein)
     * @param cachedReason   {@code this.bbstools$cachedReason} des aufrufers (kann {@code null} sein)
     * @return {@code Object[3]}: {@code [0]=Boolean cancel, [1]=ModelInstance neuerCachedInstance,
     *         [2]=CrowdRouter.Reason neuerCachedReason} — cancel ist {@code null}-sicher als
     *         {@code Boolean.FALSE} zu lesen, wenn der aufruf früh returnt (z.B. video-export)
     */
    public static Object[] classify(ModelFormRenderer self, FormRenderingContext context,
                                     ModelInstance cachedInstance, CrowdRouter.Reason cachedReason)
    {
        Object[] noCancel = { Boolean.FALSE, cachedInstance, cachedReason };
        if (mchorse.bbs_mod.integrations.vitrail.VitrailShadowFrame.collecting()
            || mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.collecting()
            || mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame.replaying()
            || context != null && mchorse.bbs_mod.client.renderer.NativeMorphCapture.replayingBody(self.getForm(), context.entity))
            return noCancel;
        /* VIDEO-EXPORT GUARD — see original mixin comment: full model must render during export. */
        try
        {
            mchorse.bbs_mod.utils.VideoRecorder vr = mchorse.bbs_mod.BBSModClient.getVideoRecorder();
            if (vr != null && vr.isRecording()) return noCancel;
        }
        catch (Throwable ignored) {}

        /* Only in-world ENTITY render of replays may take the crowd fast path. */
        if (context == null || context.type != FormRenderType.ENTITY)
        {
            return noCancel;
        }

        boolean nativeCrowd = com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw.nativeEnabled();
        if (nativeCrowd && (context.ui || context.stencilMap != null || mchorse.bbs_mod.graphics.Zeichenziel.eigenes())) return noCancel;
        if (nativeCrowd && !com.afrobama.bbstools.client.bbs.fastpath.NativeCrowdFrame.collecting())
        {
            return new Object[]{com.afrobama.bbstools.client.bbs.fastpath.NativeCrowdFrame.cancels(self.getForm(), context.entity),
                cachedInstance, cachedReason};
        }

        /* COUNT GATE — below the army threshold, ordinary editing stays 100% vanilla. */
        if (BBSPerfConfig.liveReplayCount < BBSPerfConfig.ARMY_REPLAY_THRESHOLD)
        {
            return noCancel;
        }

        /* SELECTED replay renders LIVE — never submitted/cancelled to the bake. */
        if (BBSPerfConfig.ENABLE_EDIT_LIVE
            && context.entity != null
            && context.entity == BBSPerfConfig.currentEntityRef)
        {
            BBSPerfConfig.editLiveSelectedSkips++;
            return noCancel;
        }

        if (!DIAG_HOOK_FIRED)
        {
            DIAG_HOOK_FIRED = true;
            LOG.info("[fast-path/hook] FIRST FIRE — context={} entity={} stack={}",
                "ok",
                context.entity == null ? "null" : context.entity.getClass().getSimpleName(),
                context.stack == null ? "null" : "ok");
        }

        try
        {
            Form form = self.getForm();
            if (!(form instanceof ModelForm modelForm)) return noCancel;

            ModelInstance instance = self.getModel();

            /* Reuse the cached classify result on the normal render pass when the instance is
             * unchanged; skips the per-frame router walk + counter bumps for every replay. */
            CrowdRouter.Reason reason;
            boolean normalPass = context.stencilMap == null;
            ModelInstance newCachedInstance = cachedInstance;
            CrowdRouter.Reason newCachedReason = cachedReason;

            if (normalPass && instance == cachedInstance && cachedReason != null)
            {
                reason = cachedReason;
            }
            else
            {
                reason = CrowdRouter.classify(modelForm, instance, context);
                CrowdRouter.logOnceForReason(reason, "instance.id=" + (instance == null ? "null" : instance.id));

                if (normalPass)
                {
                    newCachedInstance = instance;
                    newCachedReason = reason;
                }
            }

            if (reason != CrowdRouter.Reason.ACCEPTED)
            {
                return new Object[] { Boolean.FALSE, newCachedInstance, newCachedReason };
            }

            CrowdRouter.logFirstAccept(modelForm, instance);

            /* Capture transform snapshot — context.stack may be mutated downstream, copy NOW. */
            Matrix4f worldMatrix = context.stack == null
                ? new Matrix4f()
                : new Matrix4f(context.stack.last().pose());

            if (BBSPerfConfig.RENDER_LOOP_BYPASS_COMPARE)
            {
                compareBypassMatrix(context, worldMatrix);
            }

            FastPathBatch.Submission sub = new FastPathBatch.Submission(
                self,
                context.entity,
                instance,
                modelForm,
                worldMatrix,
                context.light,
                context.overlay,
                context.color,
                context.getTransition(),
                context.renderEquipment);
            FastPathBatch.submit(sub);
            if (nativeCrowd) com.afrobama.bbstools.client.bbs.fastpath.NativeCrowdFrame.capture(sub);

            boolean cancel = false;
            if (BBSPerfConfig.ENABLE_FAST_PATH_RENDER)
            {
                BBSPerfConfig.fastPathCancellations++;
                cancel = true;
            }

            return new Object[] { cancel, newCachedInstance, newCachedReason };
        }
        catch (Throwable t)
        {
            if (!DIAG_THREW)
            {
                DIAG_THREW = true;
                LOG.warn("[fast-path/hook] classify threw: {}", t.toString(), t);
            }
            return noCancel;
        }
    }

    private static void compareBypassMatrix(FormRenderingContext context, Matrix4f captured)
    {
        try
        {
            if (context.entity == null) return;
            net.minecraft.client.Camera cam =
                net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
            if (cam == null) return;

            double cx = cam.position().x, cy = cam.position().y, cz = cam.position().z;
            Matrix4f candidate = mchorse.bbs_mod.film.BaseFilmController.getMatrixForRenderWithRotation(
                context.entity, cx, cy, cz, context.getTransition());

            captured.get(CMP_A);
            candidate.get(CMP_B);
            float maxd = 0F;
            for (int i = 0; i < 16; i++)
            {
                float d = Math.abs(CMP_A[i] - CMP_B[i]);
                if (d > maxd) maxd = d;
            }

            BBSPerfConfig.bypassCompareCount++;
            if (maxd > BBSPerfConfig.bypassCompareMaxDelta) BBSPerfConfig.bypassCompareMaxDelta = maxd;
            if (maxd > 0.001F) BBSPerfConfig.bypassCompareMismatches++;

            long now = System.currentTimeMillis();
            if (now - cmpLastLogMs > 5000L)
            {
                cmpLastLogMs = now;
                LOG.info("[bypass-compare] maxDelta={} capturedT=({},{},{}) candidateT=({},{},{})",
                    maxd, CMP_A[12], CMP_A[13], CMP_A[14],
                    CMP_B[12], CMP_B[13], CMP_B[14]);
            }
        }
        catch (Throwable ignored) {}
    }
}
