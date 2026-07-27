// Coded by Afrobama/Claude — BBS Renewed Camera Preview Phase 1
package com.afrobama.bbstools.client.campreview;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Container UIElement that holds one {@link UICameraPreviewWindow} per
 * active {@link CameraPreview}. Synchronizes the child set with
 * {@link CameraPreviewRegistry} on each render — newly-added previews
 * spawn windows automatically; removed previews tear down their windows.
 *
 * <p>Add this overlay to the dashboard's root once and it picks up any
 * previews the user adds via keybind / chat command without further
 * wiring.</p>
 *
 * @deprecated will be removed in Phase 11 — see new clip-driven design
 */
@Deprecated
public class UICameraPreviewOverlay extends UIElement
{
    private final Map<UUID, UICameraPreviewWindow> windows = new HashMap<>();

    public UICameraPreviewOverlay()
    {
        this.wh(1F, 1F);
    }

    @Override
    public void render(UIContext context)
    {
        this.syncChildren();
        super.render(context);
    }

    /* Coded by Afrobama/Claude — Phase 6: click-through priority fix.
     *  Iterate children in REVERSE (last-added = visually-on-top) so the
     *  top window captures the click before anything underneath. Without
     *  this, BBS's default front-to-back UIElement traversal hits the
     *  first-added window even when a later window visually overlaps it. */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        java.util.List<mchorse.bbs_mod.ui.framework.elements.IUIElement> kids = this.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--)
        {
            mchorse.bbs_mod.ui.framework.elements.IUIElement child = kids.get(i);
            if (!child.isVisible()) continue;
            /* mouseClicked() returns the consuming element (non-null) or
             *  null on pass-through. Any non-null = a window ate the click. */
            if (child.mouseClicked(context) != null) return true;
        }
        return false;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        java.util.List<mchorse.bbs_mod.ui.framework.elements.IUIElement> kids = this.getChildren();
        for (int i = kids.size() - 1; i >= 0; i--)
        {
            mchorse.bbs_mod.ui.framework.elements.IUIElement child = kids.get(i);
            if (!child.isVisible()) continue;
            if (child.mouseScrolled(context) != null) return true;
        }
        return false;
    }

    private void syncChildren()
    {
        /* Add windows for new previews. */
        for (CameraPreview p : CameraPreviewRegistry.list())
        {
            if (!p.enabled) continue;
            if (this.windows.containsKey(p.id)) continue;

            UICameraPreviewWindow win = new UICameraPreviewWindow(p);
            this.windows.put(p.id, win);
            this.add(win);
        }

        /* Remove windows for previews that vanished from the registry. */
        java.util.Iterator<Map.Entry<UUID, UICameraPreviewWindow>> it = this.windows.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, UICameraPreviewWindow> e = it.next();
            CameraPreview p = CameraPreviewRegistry.find(e.getKey());
            if (p == null || !p.enabled)
            {
                this.remove(e.getValue());
                it.remove();
            }
        }
    }

}
