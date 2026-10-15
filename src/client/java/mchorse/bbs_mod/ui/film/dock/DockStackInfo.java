package mchorse.bbs_mod.ui.film.dock;

import java.util.List;

/**
 * One resolved dock stack: the panels that share a rectangle, which of them is on top, and where that
 * rectangle is in normalized editor coordinates (0..1).
 *
 * <p>A stack of one is the normal case — a panel that shares its spot with nobody. Only stacks of two or
 * more get a tab strip.</p>
 *
 * <p>Produced by {@link DockStackGeometry#collect} out of the persisted layout tree; consumed by the film
 * editor to place panels, tab strips and drag handles.</p>
 */
public class DockStackInfo
{
    public final List<String> panelIds;
    public final String activePanelId;
    public final float x;
    public final float y;
    public final float w;
    public final float h;

    public DockStackInfo(List<String> panelIds, String activePanelId, float x, float y, float w, float h)
    {
        this.panelIds = panelIds;
        this.activePanelId = activePanelId;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean isStacked()
    {
        return this.panelIds.size() > 1;
    }

    public String getAnchorPanelId()
    {
        return this.panelIds.isEmpty() ? "" : this.panelIds.get(0);
    }
}
