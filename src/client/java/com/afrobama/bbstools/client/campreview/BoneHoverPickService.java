// Coded by Afrobama/Claude — Phase 15 v6: alt-cam bone hover via BBS stencil pipeline
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state for the alt-camera bone-hover system. The tile's
 * {@link UIClipCameraPreview} publishes hover requests here (with the
 * mouse position in tile-local coords). The pre-pass mixin reads
 * those requests once per frame, runs BBS's stencil-pick pipeline at
 * the alt-camera POV into a dedicated stencil framebuffer, and writes
 * the picked Form + bone-name back so the tile can display it on the
 * next render.
 *
 * <p>One {@link StencilFormFramebuffer} per clip UUID — they're
 * allocated lazily and pooled across clip activations.</p>
 */
public final class BoneHoverPickService
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/bone-hover");

    /** Hover request published by a tile this UI-render frame. */
    public static final class Request
    {
        public final int mouseX;
        public final int mouseY;
        public final int tileW;
        public final int tileH;
        /** Shift held at click → bypass {@code replayMarkedBonesOnly} filter
         *  so any bone is pickable, mirroring BBS's Shift-modifier behavior. */
        public final boolean shift;
        /** Ctrl held at click → pick the parent bone (one level up the
         *  rig hierarchy). Useful for grabbing the limb when the
         *  fingertip is what's under the cursor. */
        public final boolean ctrl;

        public Request(int mouseX, int mouseY, int tileW, int tileH)
        {
            this(mouseX, mouseY, tileW, tileH, false, false);
        }

        public Request(int mouseX, int mouseY, int tileW, int tileH, boolean shift, boolean ctrl)
        {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.tileW = tileW;
            this.tileH = tileH;
            this.shift = shift;
            this.ctrl = ctrl;
        }
    }

    /** Pick result written by the pre-pass after running the stencil
     *  pipeline. {@code null} form means nothing was hit.
     *
     *  <p>{@code stencilTexId} + {@code stencilW/H} + {@code pickedIndex}
     *  let the tile draw a blue highlight overlay via the BBS
     *  picker_preview shader after the framebuffer blit.</p> */
    public static final class Result
    {
        public final Form form;
        public final String bone;
        public final int stencilTexId;
        public final int stencilW;
        public final int stencilH;
        public final int pickedIndex;
        public final long timestamp;

        public Result(Form form, String bone, int stencilTexId, int stencilW, int stencilH, int pickedIndex)
        {
            this.form = form;
            this.bone = bone;
            this.stencilTexId = stencilTexId;
            this.stencilW = stencilW;
            this.stencilH = stencilH;
            this.pickedIndex = pickedIndex;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static final ConcurrentHashMap<UUID, Request> REQUESTS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, Result> RESULTS = new ConcurrentHashMap<>();

    /** Per-clip stencil framebuffer pool. */
    private static final ConcurrentHashMap<UUID, StencilFormFramebuffer> STENCILS = new ConcurrentHashMap<>();

    /** Most recent left-click on a tile, captured in tile-local coords.
     *  Mirrors {@link #REQUESTS} but only present for ONE frame — used
     *  by the pre-pass to trigger BBS's selection update via the chat
     *  log + a hook to UIPoseEditor (Phase 15 v7). */
    public static final ConcurrentHashMap<UUID, Request> CLICKS = new ConcurrentHashMap<>();

    private BoneHoverPickService() {}

    public static StencilFormFramebuffer getStencil(UUID clipId)
    {
        StencilFormFramebuffer fb = STENCILS.get(clipId);
        if (fb == null)
        {
            try
            {
                fb = new StencilFormFramebuffer();
                STENCILS.put(clipId, fb);
                LOG.info("Allocated stencil framebuffer for clip {}", clipId);
            }
            catch (Throwable t)
            {
                LOG.warn("Failed to allocate stencil for clip {}: {}", clipId, t.toString());
                return null;
            }
        }
        return fb;
    }

    /** Drop a tile's resources when it deactivates. */
    public static void releaseClip(UUID clipId)
    {
        REQUESTS.remove(clipId);
        RESULTS.remove(clipId);
        CLICKS.remove(clipId);
        StencilFormFramebuffer fb = STENCILS.remove(clipId);
        if (fb != null)
        {
            try { fb.getFramebuffer().delete(); }
            catch (Throwable ignored) {}
        }
    }

}
