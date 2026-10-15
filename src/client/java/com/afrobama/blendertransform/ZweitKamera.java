/*
 * The second-camera pass follows SecurityCraft's frame feeds (FrameFeedHandler.captureFrameFeeds,
 * github.com/Geforce132/SecurityCraft, branch 26.2), MIT License, Copyright (c) Geforce132 and
 * the SecurityCraft contributors.
 */
package com.afrobama.blendertransform;

import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.WindowRenderState;

/**
 * One full world render from another camera into its own target, after the main world.
 *
 * <p>For the "Preview camera" clip (audit 2026-10-14 #110: its window stayed black, the world
 * pass for it had been switched off since 26.2). The first try on 26.2 rendered the level before
 * the main one and crashed the sky pass afterwards. What SecurityCraft does, and this copies:
 * render AFTER the main level (before the screenshot hook), point the main target and the sky's
 * target at the preview, then restore the visible chunk sections and extract camera, window and
 * GUI again, so the main frame's GUI pass and the next frame see their own state.</p>
 *
 * <p>Pose, FOV, main target and sky target reach Minecraft through the hooks that already serve
 * RenderCameraPreview (KameraLogik.stellung/blickwinkel, SpielRendererLogik.filmpuffer/himmelsziel).</p>
 */
public final class ZweitKamera
{
    private static RenderTarget ziel;
    private static double[] stellung;
    private static float sichtfeld;

    private ZweitKamera() {}

    public static boolean aktiv()
    {
        return ziel != null;
    }

    public static RenderTarget ziel()
    {
        return ziel;
    }

    /** {x, y, z, yaw, pitch} in Minecraft's convention. */
    public static double[] stellung()
    {
        return stellung;
    }

    public static float sichtfeld()
    {
        return sichtfeld;
    }

    /** Renders the world as seen from {@code pose} into {@code target}. Throws what the render throws. */
    public static void zeichnen(DeltaTracker delta, RenderTarget target, double[] pose, float fov)
    {
        Minecraft mc = Minecraft.getInstance();
        WindowRenderState fenster = mc.gameRenderer.gameRenderState().windowRenderState;
        int breite = fenster.width;
        int hoehe = fenster.height;
        ObjectArrayList<SectionRenderDispatcher.RenderSection> sichtbar = mc.levelRenderer.visibleSections().clone();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> nah = mc.levelRenderer.nearbyVisibleSections().clone();

        ziel = target;
        stellung = pose;
        sichtfeld = fov;
        mc.gameRenderer.setRenderBlockOutline(false);
        Object pack = packAus();
        /* The film editor keeps its viewport's redirect set between frames (measured: always on
         * with the editor open). Left on, a model in this pass drew into the film buffer while
         * the native world pass ran on the preview target: DrawPassLease refused it, the pass
         * threw with the frame's PreparedFrame still in use, and the next form preview crashed
         * the game ("PreparedFrame already in use", audit 2026-10-14). */
        com.mojang.blaze3d.textures.GpuTextureView farbe = mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView tiefe = mchorse.bbs_mod.client.BBSRendering.outputDepthTextureOverride;

        mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride = null;
        mchorse.bbs_mod.client.BBSRendering.outputDepthTextureOverride = null;

        try
        {
            mc.gameRenderer.mainCamera().update(delta);
            mc.gameRenderer.extract(delta, true);
            fenster.width = target.width;
            fenster.height = target.height;
            //? if >=26.3 {
            /*mc.gameRenderer.renderLevel();
            *///?} else {
            mc.gameRenderer.renderLevel(delta);
            //?}
        }
        finally
        {
            mchorse.bbs_mod.client.BBSRendering.outputColorTextureOverride = farbe;
            mchorse.bbs_mod.client.BBSRendering.outputDepthTextureOverride = tiefe;
            frameFreigeben();
            packAn(pack);
            ziel = null;
            stellung = null;
            fenster.width = breite;
            fenster.height = hoehe;
            mc.gameRenderer.setRenderBlockOutline(true);
            mc.levelRenderer.visibleSections().clear();
            mc.levelRenderer.visibleSections().addAll(sichtbar);
            mc.levelRenderer.nearbyVisibleSections().clear();
            mc.levelRenderer.nearbyVisibleSections().addAll(nah);
            /* The main frame's GUI is drawn after this: extract camera, window and GUI again. */
            mc.gameRenderer.mainCamera().update(delta);
            mc.gameRenderer.extract(delta, true);
        }
    }

    /**
     * A pass that threw leaves the dispatcher's one PreparedFrame in use, and every later
     * prepareFrame (the next form preview) throws. After the main level nothing may hold it.
     */
    private static void frameFreigeben()
    {
        try
        {
            Object dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
            java.lang.reflect.Field feld = dispatcher.getClass().getDeclaredField("preparedFrame");
            feld.setAccessible(true);
            Object frame = feld.get(dispatcher);
            java.lang.reflect.Field kontext = frame.getClass().getDeclaredField("context");
            kontext.setAccessible(true);

            if (kontext.get(frame) != null)
            {
                org.slf4j.LoggerFactory.getLogger("bbs/zweitkamera").error("Preview pass left the PreparedFrame in use; released");
                ((AutoCloseable) frame).close();
            }
        }
        catch (Exception fehler)
        {
            throw new IllegalStateException("Minecraft changed: cannot check the PreparedFrame after the preview pass", fehler);
        }
    }

    /**
     * The shader pack (Vitrail) sits out the preview pass: it drew its frame into its own targets
     * and the window, never into the preview target -- measured: black preview with Somber, sky and
     * ground without it (audit 2026-10-14 #110). Every Vitrail draw path reads the static
     * PackChain.active, so the pass runs with it cleared; the preview shows the world unshaded.
     * Its held render pass is flushed on both sides so no pass spans the swap.
     */
    private static Object packAus()
    {
        try
        {
            Class<?> kette = Class.forName("dev.vitrail.render.PackChain");
            java.lang.reflect.Field aktiv = kette.getDeclaredField("active");
            aktiv.setAccessible(true);
            Object pack = aktiv.get(null);

            if (pack != null)
            {
                halten();
                aktiv.set(null, null);
            }

            return pack;
        }
        catch (ClassNotFoundException keinVitrail)
        {
            return null;
        }
        catch (ReflectiveOperationException fehler)
        {
            throw new IllegalStateException("Vitrail changed: cannot take its pack out of the preview pass", fehler);
        }
    }

    private static void packAn(Object pack)
    {
        if (pack == null)
        {
            return;
        }

        try
        {
            halten();
            java.lang.reflect.Field aktiv = Class.forName("dev.vitrail.render.PackChain").getDeclaredField("active");
            aktiv.setAccessible(true);
            aktiv.set(null, pack);
        }
        catch (ReflectiveOperationException fehler)
        {
            throw new IllegalStateException("Vitrail changed: cannot put its pack back after the preview pass", fehler);
        }
    }

    /** GeometryHold.flush(null): ends a render pass Vitrail keeps open across draws. */
    private static void halten() throws ReflectiveOperationException
    {
        Class.forName("dev.vitrail.render.GeometryHold").getMethod("flush", java.util.function.Supplier.class).invoke(null, (Object) null);
    }
}
