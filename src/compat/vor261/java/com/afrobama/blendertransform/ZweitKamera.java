package com.afrobama.blendertransform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mchorse.bbs_mod.compat.Geraet;
import net.minecraft.client.DeltaTracker;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vor 26.1: der zweite Weltdurchgang der Vorschaukamera ist hier NICHT gebaut. Das 26.3-Original
 * nutzt die Extraktionsphase (GameRenderer.extract, gameRenderState), die es auf den reinen
 * GL-Knoten nicht gibt. Erhalten bleibt der bisherige Weg dieser Knoten: das Vorschauziel wird
 * schwarz geloescht. Einmal je Start laut im Log, damit das schwarze Fenster nicht still bleibt.
 */
public final class ZweitKamera
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/zweitkamera");
    private static boolean gemeldet;

    private ZweitKamera() {}

    public static boolean aktiv()
    {
        return false;
    }

    public static RenderTarget ziel()
    {
        return null;
    }

    public static double[] stellung()
    {
        return null;
    }

    public static float sichtfeld()
    {
        return 0F;
    }

    public static void zeichnen(DeltaTracker delta, RenderTarget target, double[] pose, float fov)
    {
        if (!gemeldet)
        {
            gemeldet = true;
            LOG.warn("Preview camera: world pass is not built before 26.1, the window shows black");
        }

        Geraet.get().createCommandEncoder()
            .clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(0F, 0F, 0F, 1F), target.getDepthTexture(), 0.0);
    }
}
