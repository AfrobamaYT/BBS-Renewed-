package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

/** 26.3 transient targets follow the main target; vanilla owns the persistent outline target. */
public final class NativeWorldTargets
{
    private static boolean resizing;

    public static void resizeExtras()
    {
        Minecraft mc = Minecraft.getInstance();

        if (resizing || mc.levelRenderer == null)
        {
            return;
        }

        RenderTarget target = mc.gameRenderer.mainRenderTarget();

        /* LevelRenderer.resize verwirft den Sichtbarkeitsgraphen und legt das Umriss-Ziel neu an
         * (RenderTarget.resize zerstoert immer, auch bei gleicher Groesse). BBS schaltet beim
         * Export JEDES Bild um: gemessen 2026-10-14 lief ein 4K60-Export dadurch mit ~3 statt 60
         * Bildern pro Sekunde, der Render-Thread stand in vmaCreateImage. Nur bei echter
         * Aenderung von Groesse oder Renderer nachziehen, wie Vanilla bei Fensteraenderungen. */
        if (NativeWorldTargetsGroesse.gleich(mc.levelRenderer, target.width, target.height))
        {
            return;
        }

        resizing = true;

        try
        {
            mc.levelRenderer.resize(target.width, target.height);
            NativeWorldTargetsGroesse.merken(mc.levelRenderer, target.width, target.height);
        }
        finally
        {
            resizing = false;
        }
    }

    private NativeWorldTargets()
    {}
}
