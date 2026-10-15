package mchorse.bbs_mod.ui.film.dock;

import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a persisted layout tree into rectangles: which dock stack sits where, and how much gap each of
 * its edges gets. Pure arithmetic over normalized coordinates — no UI elements, no rendering, no film
 * data, so it can be reasoned about (and read) on its own.
 */
public class DockStackGeometry
{
    /** Gap between two panels, in pixels. */
    /**
     * Naht zwischen den Flaechen. Der Entwurf setzt {@code .main{gap:2px;padding:2px}} — mit 4
     * standen ringsum breite graue Streifen, die wie unfertige Raender wirkten. Der GREIFbereich
     * des Splitters bleibt davon unberuehrt, der ist eigenstaendig.
     */
    public static final int PANEL_GAP_PX = 2;
    /** Tolerance for "this edge touches that edge" on normalized (0..1) coordinates. */
    public static final float PANEL_EDGE_EPS = 0.001F;

    private DockStackGeometry()
    {}

    /**
     * Walk the layout tree and emit one {@link DockStackInfo} per leaf, with the rectangle that leaf
     * occupies inside (x, y, w, h).
     */
    public static void collect(EditorLayoutNode node, float x, float y, float w, float h, List<DockStackInfo> out)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            String panelId = ((EditorLayoutNode.PanelNode) node).getPanelId();
            List<String> ids = new ArrayList<>();
            ids.add(panelId);
            out.add(new DockStackInfo(ids, panelId, x, y, w, h));

            return;
        }

        if (node instanceof EditorLayoutNode.StackNode)
        {
            EditorLayoutNode.StackNode stack = (EditorLayoutNode.StackNode) node;
            out.add(new DockStackInfo(new ArrayList<>(stack.getPanelIds()), stack.getActivePanelId(), x, y, w, h));

            return;
        }

        if (!(node instanceof EditorLayoutNode.SplitterNode))
        {
            return;
        }

        EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;

        if (splitter.isHorizontal())
        {
            float h1 = h * splitter.getRatio();

            collect(splitter.getFirst(), x, y, w, h1, out);
            collect(splitter.getSecond(), x, y + h1, w, h - h1, out);
        }
        else
        {
            float w1 = w * splitter.getRatio();

            collect(splitter.getFirst(), x, y, w1, h, out);
            collect(splitter.getSecond(), x + w1, y, w - w1, h, out);
        }
    }

    /**
     * Rectangle of the stack holding {@code previewPanelId}, or null when it is not in the layout. The
     * preview is special-cased because it is frameless: its neighbours have to draw the whole gap.
     */
    public static float[] stackRect(List<DockStackInfo> stackInfos, String previewPanelId)
    {
        for (DockStackInfo info : stackInfos)
        {
            if (info.panelIds.contains(previewPanelId))
            {
                return new float[] {info.x, info.y, info.w, info.h};
            }
        }

        return null;
    }

    /**
     * Per-edge gaps so seams between panels don't double up: a full gap where a side does not get a
     * matching half from the other side — the editor's outer edge or the frameless preview — and a half
     * gap where a regular neighbour meets it. Returns left, top, right, bottom offsets in pixels.
     */
    public static int[] gutter(DockStackInfo info, float[] preview)
    {
        int half = PANEL_GAP_PX / 2;
        float x = info.x, y = info.y, w = info.w, h = info.h;

        boolean left = x <= PANEL_EDGE_EPS;
        boolean top = y <= PANEL_EDGE_EPS;
        boolean right = x + w >= 1F - PANEL_EDGE_EPS;
        boolean bottom = y + h >= 1F - PANEL_EDGE_EPS;

        if (preview != null)
        {
            float vx = preview[0], vy = preview[1], vw = preview[2], vh = preview[3];
            boolean spanY = y < vy + vh - PANEL_EDGE_EPS && y + h > vy + PANEL_EDGE_EPS;
            boolean spanX = x < vx + vw - PANEL_EDGE_EPS && x + w > vx + PANEL_EDGE_EPS;

            left |= spanY && Math.abs(x - (vx + vw)) <= PANEL_EDGE_EPS;
            right |= spanY && Math.abs((x + w) - vx) <= PANEL_EDGE_EPS;
            top |= spanX && Math.abs(y - (vy + vh)) <= PANEL_EDGE_EPS;
            bottom |= spanX && Math.abs((y + h) - vy) <= PANEL_EDGE_EPS;
        }

        return new int[] {
            left ? PANEL_GAP_PX : half,
            top ? PANEL_GAP_PX : half,
            right ? PANEL_GAP_PX : half,
            bottom ? PANEL_GAP_PX : half
        };
    }
}
