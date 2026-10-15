// Coded by Afrobama/Claude — Phase 12: clip-keyed framebuffer lifecycle
package com.afrobama.bbstools.client.campreview;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-active-clip framebuffer ownership. Lazily allocates a
 * {@link TextureTarget} the first time a clip activates and
 * releases it when the clip leaves the active set for a few frames.
 *
 * <p>Default size 320×180 — Phase 13 can let the docked viewport
 * resize its framebuffer to match the panel's actual on-screen rect
 * for crisper output, but 320×180 is a good starting point that keeps
 * total GPU cost low.</p>
 *
 * <p>Allocation happens on the render thread (called from the pre-pass
 * mixin), so we don't need additional synchronization.</p>
 */
public final class ClipViewportFramebuffers
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/viewport-fb");

    /* 640×360 — Phase 15 v4 bump: user reported visible pixelation on
     * replay motion at 256×144. The world-skip path is so cheap that
     * we can afford 4× the pixel count. Combined with linear texture
     * filtering on the color attachment, motion edges blend instead
     * of staircase. */
    private static final int DEFAULT_W = 640;
    private static final int DEFAULT_H = 360;

    /** Grace period in frames before a no-longer-active clip's
     *  framebuffer is freed. Keeps GPU resources alive across short
     *  gaps between consecutive Add-Camera clips in the timeline. */
    private static final int IDLE_FRAMES_BEFORE_FREE = 60;

    private static final Map<UUID, TextureTarget> FBS = new HashMap<>();
    private static final Map<UUID, Integer> IDLE_TICKS = new HashMap<>();

    private ClipViewportFramebuffers() {}

    public static void register()
    {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> disposeAll());
    }

    public static TextureTarget get(UUID id)
    {
        TextureTarget fb = FBS.get(id);
        if (fb == null)
        {
            try
            {
                fb = new TextureTarget("bbstools-clip-viewport", DEFAULT_W, DEFAULT_H,
                    true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
                /* 26.2: setClearColor ist entfallen -- die Loeschfarbe wird beim Loeschen
                 * mitgegeben, nicht am Ziel geparkt (CameraPreviewRenderHook tut das). */

                /* ⚠️ VERLOREN: setFilterMode gibt es nicht mehr. Die Filterung haengt jetzt
                 * nicht mehr am Renderziel, sondern am GpuSampler des Zuges, der die Kachel
                 * blittet -- das Ziel weiss gar nicht mehr, wie es spaeter gelesen wird.
                 * Absicht war GL_LINEAR, damit die Kachel beim Abspielen glaettet statt zu
                 * treppen. Wer das zurueckwill, muss es am Blit setzen, nicht hier. */

                FBS.put(id, fb);
                LOG.info("Allocated {}x{} viewport framebuffer for clip {}", DEFAULT_W, DEFAULT_H, id);
            }
            catch (Throwable t)
            {
                LOG.warn("Failed to allocate framebuffer for clip {}: {}", id, t.toString());
                return null;
            }
        }
        IDLE_TICKS.put(id, 0);
        return fb;
    }

    public static int getWidth() { return DEFAULT_W; }
    public static int getHeight() { return DEFAULT_H; }

    /** Bump idle counters for clips that didn't render this frame.
     *  Free framebuffers that have idled past the grace period. */
    public static void garbageCollect(Set<UUID> renderedThisFrame)
    {
        Iterator<Map.Entry<UUID, TextureTarget>> it = FBS.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, TextureTarget> e = it.next();
            UUID id = e.getKey();
            if (renderedThisFrame.contains(id))
            {
                IDLE_TICKS.put(id, 0);
                continue;
            }
            int idle = IDLE_TICKS.getOrDefault(id, 0) + 1;
            if (idle >= IDLE_FRAMES_BEFORE_FREE)
            {
                try { e.getValue().destroyBuffers(); }
                catch (Throwable ignored) {}
                IDLE_TICKS.remove(id);
                it.remove();
            }
            else
            {
                IDLE_TICKS.put(id, idle);
            }
        }
    }

    public static int activeCount()
    {
        return FBS.size();
    }

    public static Set<UUID> allIds()
    {
        return new HashSet<>(FBS.keySet());
    }

    public static void disposeAll()
    {
        for (TextureTarget fb : FBS.values())
        {
            try { fb.destroyBuffers(); }
            catch (Throwable ignored) {}
        }
        FBS.clear();
        IDLE_TICKS.clear();
    }

}
