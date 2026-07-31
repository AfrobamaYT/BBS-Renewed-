// Coded by Afrobama/Claude — Army System: per-replay cache-progress loading bar.
//
// DaVinci-style visual feedback: a thin horizontal bar floats above each
// replay showing how much of its animation is cached (green = cached, red =
// still live). Filled as the bake records frames. Drawn as two camera-relative
// line segments at AFTER_ENTITIES (no shaders, cheap, billboard-ish).
//
// The bypass loop feeds a per-frame snapshot of (camera-relative position +
// progress); this renderer just draws it. Default OFF (toggle).
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
/* 26.2: die Fabrikmethoden (lines(), entityCutout(), ...) sitzen nicht mehr an RenderType
 * selbst, sondern in der Nachbarklasse RenderTypes -- RenderType ist nur noch der Typ. */
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;

import java.util.ArrayList;

public final class CacheBarRenderer
{
    private CacheBarRenderer() {}

    /* Per-frame snapshot (filled on render thread by the bypass, drawn here). */
    private static float[] SNAP = new float[0];
    private static int SNAP_COUNT = 0;

    private static volatile boolean REGISTERED = false;

    /** Bar geometry. */
    private static final float BAR_WIDTH  = 0.7F;   /* blocks */
    private static final float BAR_HEIGHT = 2.35F;  /* above replay origin */

    public static void register()
    {
        if (REGISTERED) return;
        REGISTERED = true;
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(ctx ->
        {
            if (!BBSPerfConfig.ENABLE_CACHE_LOADING_BAR || SNAP_COUNT == 0) return;
            try { draw(ctx.poseStack(), ctx.submitNodeCollector()); }
            catch (Throwable ignored) {}
        });
    }

    /** Reset the snapshot at the start of a bypass frame. */
    public static void beginFrame(int expected)
    {
        SNAP_COUNT = 0;
        int cap = expected * 4;
        if (SNAP.length < cap) SNAP = new float[Math.max(cap, SNAP.length * 2)];
    }

    /** Record one replay: camera-relative position + cache progress [0..1]. */
    public static void record(Matrix4f camRelWorld, float progress)
    {
        int idx = SNAP_COUNT * 4;
        if (idx + 4 > SNAP.length) return;
        SNAP[idx]     = camRelWorld.m30();
        SNAP[idx + 1] = camRelWorld.m31();
        SNAP[idx + 2] = camRelWorld.m32();
        SNAP[idx + 3] = progress < 0F ? 0F : (progress > 1F ? 1F : progress);
        SNAP_COUNT++;
    }

    private static void draw(PoseStack stack, SubmitNodeCollector sammler)
    {
        if (stack == null || sammler == null) return;

        /* 26.2: es gibt keinen VertexConsumer mehr zum Hineinschreiben. Die Geometrie wird
         * als AUFTRAG eingereiht und spaeter gezeichnet -- der Eckenaufbau steht darum in
         * einem Lambda. Er selbst ist unveraendert; nur die Matrix kommt aus der
         * uebergebenen Pose statt aus einem vorher eingefangenen Stapel, weil das Lambda
         * erst laeuft, wenn der Stapel schon weitergewandert sein kann. */
        sammler.submitCustomGeometry(stack, RenderTypes.lines(), (entry, vc) ->
        {
        Matrix4f m = entry.pose();

        float half = BAR_WIDTH * 0.5F;
        for (int i = 0; i < SNAP_COUNT; i++)
        {
            int idx = i * 4;
            float x = SNAP[idx], y = SNAP[idx + 1] + BAR_HEIGHT, z = SNAP[idx + 2];
            float p = SNAP[idx + 3];

            float xL = x - half;
            float xMid = x - half + BAR_WIDTH * p;
            float xR = x + half;

            /* Green: cached portion. */
            if (p > 0.001F)
            {
                vc.addVertex(m, xL, y, z).setColor(60, 220, 90, 255).setNormal(entry, 1F, 0F, 0F);
                vc.addVertex(m, xMid, y, z).setColor(60, 220, 90, 255).setNormal(entry, 1F, 0F, 0F);
            }
            /* Red: remaining (live) portion. */
            if (p < 0.999F)
            {
                vc.addVertex(m, xMid, y, z).setColor(210, 60, 60, 255).setNormal(entry, 1F, 0F, 0F);
                vc.addVertex(m, xR, y, z).setColor(210, 60, 60, 255).setNormal(entry, 1F, 0F, 0F);
            }
        }

        });

        /* Kein endBatch mehr: der Auftrag ist eingereiht, gezeichnet wird er vom Sammler
         * zusammen mit allem anderen. Ein manuelles Abschicken gibt es nicht. */
    }

}
