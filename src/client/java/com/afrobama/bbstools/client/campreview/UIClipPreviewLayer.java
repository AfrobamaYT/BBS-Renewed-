// Coded by Afrobama/Claude — Phase 15 v2: dynamic mount + horizontal squeeze layout
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Container UIElement that owns one {@link UIClipCameraPreview} per
 * active Add-Camera clip. Syncs the child set on each render against
 * {@link ActiveCameraClipRegistry} and runs a horizontal squeeze-to-fit
 * auto-layout on tiles the user hasn't manually positioned.
 *
 * <p>Layout: tiles arrange in a horizontal row anchored to the top-right
 * of the screen. Each tile keeps a 16:9 ratio. As more cameras activate,
 * all auto-tiles shrink uniformly so the row fits within the available
 * width. The moment the user drags/resizes a tile, its
 * {@link UIClipCameraPreview#manuallyPositioned} flag flips on and the
 * auto-layout stops touching it.</p>
 */
public class UIClipPreviewLayer extends UIElement
{
    /** Default 16:9 tile size before squeeze. Larger than the Phase 12
     *  default (240×135) so first-time users see the replay clearly.
     *  Auto-layout shrinks proportionally if many cameras are active
     *  or screen is narrow. */
    private static final int BASE_TILE_W = 320;
    private static final int BASE_TILE_H = 180;
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int MIN_TILE_W = 128;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("bbstools/cam-preview/layer");

    private final Map<UUID, UIClipCameraPreview> mounted = new HashMap<>();
    private boolean loggedFirstActive;
    private boolean loggedFirstRender;

    public UIClipPreviewLayer()
    {
        /* Full-screen invisible container; children position themselves
         *  in absolute coords via applyAutoLayout. canBeRendered is
         *  overridden so we render even before resize() has propagated. */
        this.wh(1F, 1F);
        LOG.info("UIClipPreviewLayer instance created, wh=1F,1F");
    }

    @Override
    public boolean canBeRendered(mchorse.bbs_mod.ui.utils.Area viewport)
    {
        return true;
    }

    @Override
    public void render(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        if (!loggedFirstRender)
        {
            loggedFirstRender = true;
            LOG.info("UIClipPreviewLayer.render() first call — area: x={} y={} w={} h={}",
                this.area.x, this.area.y, this.area.w, this.area.h);
        }

        /* Force the layer's area to span the full screen so child tile
         *  position lookups stay in absolute screen coords. */
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null)
        {
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            if (this.area.w != sw || this.area.h != sh)
            {
                this.area.setSize(sw, sh);
            }
        }

        syncChildren();
        super.render(context);
    }

    private void syncChildren()
    {
        java.util.List<ActiveCameraClipRegistry.Active> active = ActiveCameraClipRegistry.snapshot();

        /* Add a preview for every newly-active clip. */
        for (ActiveCameraClipRegistry.Active a : active)
        {
            if (mounted.containsKey(a.id)) continue;

            UIClipCameraPreview preview = new UIClipCameraPreview(a.clip, a.id);
            mounted.put(a.id, preview);
            this.add(preview);
            LOG.info("Mounted preview tile for clip {} (active count = {})", a.id, mounted.size());
        }

        if (!loggedFirstActive && !active.isEmpty())
        {
            loggedFirstActive = true;
            LOG.info("First active clip detected — registry returning {} entries", active.size());
        }

        /* Remove previews whose clips deactivated. */
        Iterator<Map.Entry<UUID, UIClipCameraPreview>> it = mounted.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, UIClipCameraPreview> e = it.next();
            boolean stillActive = false;
            for (ActiveCameraClipRegistry.Active a : active)
            {
                if (a.id.equals(e.getKey())) { stillActive = true; break; }
            }
            if (!stillActive)
            {
                this.remove(e.getValue());
                it.remove();
            }
        }

        applyAutoLayout();
    }

    /**
     * Horizontal row, right-anchored, shrink-to-fit. Only repositions
     * tiles that have never been manually moved.
     */
    private void applyAutoLayout()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        int sw = mc.getWindow().getScaledWidth();

        /* Count tiles still under auto-layout. */
        int autoCount = 0;
        for (UIClipCameraPreview p : mounted.values())
        {
            if (!p.manuallyPositioned) autoCount++;
        }
        if (autoCount == 0) return;

        /* Available width = full screen minus margins. */
        int availableW = sw - 2 * MARGIN;
        int naturalRowW = autoCount * BASE_TILE_W + (autoCount - 1) * GAP;

        float scale = 1F;
        if (naturalRowW > availableW)
        {
            scale = (float) (availableW - (autoCount - 1) * GAP) / (autoCount * BASE_TILE_W);
        }

        int tileW = Math.max(MIN_TILE_W, (int) (BASE_TILE_W * scale));
        int tileH = (int) (tileW * (BASE_TILE_H / (float) BASE_TILE_W));

        /* Anchor row LEFT-edge, top — BBS's main preview is typically
         *  on the right half of the screen, so we put alt tiles on the
         *  left to avoid overlap. User can drag from there. */
        int startX = MARGIN;
        int y = MARGIN;

        int i = 0;
        for (UIClipCameraPreview p : mounted.values())
        {
            if (p.manuallyPositioned) continue;
            int x = startX + i * (tileW + GAP);
            p.xy(x, y);
            p.wh(tileW, tileH);
            p.resize();
            i++;
        }
    }

}
