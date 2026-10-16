package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.OverlayBox;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/** The placement gizmo's frame: outline, eight handles and the anchor dot (BBS FS 894caa1eb, Wemppy4/bbs-fs, MIT) */
final class PlacementRahmen
{
    static final int HANDLE_SIZE = 3;

    private PlacementRahmen()
    {}

    static void zeichnen(UIContext context, Area viewport, OverlayBox box, Placement placement, boolean ruhig)
    {
        int x1 = (int) UIPlacementGizmo.toScreenX(box.x, viewport, box.unitWidth);
        int y1 = (int) UIPlacementGizmo.toScreenY(box.y, viewport);
        int x2 = (int) UIPlacementGizmo.toScreenX(box.x + box.w, viewport, box.unitWidth);
        int y2 = (int) UIPlacementGizmo.toScreenY(box.y + box.h, viewport);
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;

        context.batcher.clip(viewport.x, viewport.y, viewport.w, viewport.h, context.menu.width, context.menu.height);
        context.batcher.outline(x1, y1, x2, y2, ruhig ? Colors.A75 | Colors.WHITE & Colors.RGB : Colors.WHITE);

        for (int[] p : new int[][] {{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}, {x1, my}, {x2, my}, {mx, y1}, {mx, y2}})
        {
            context.batcher.box(p[0] - HANDLE_SIZE, p[1] - HANDLE_SIZE, p[0] + HANDLE_SIZE, p[1] + HANDLE_SIZE, Colors.WHITE);
            context.batcher.outline(p[0] - HANDLE_SIZE, p[1] - HANDLE_SIZE, p[0] + HANDLE_SIZE, p[1] + HANDLE_SIZE, Colors.A100);
        }

        /* The anchor point the scaling pivots around */
        int ax = (int) UIPlacementGizmo.toScreenX(box.unitWidth * placement.windowX + placement.offsetX, viewport, box.unitWidth);
        int ay = (int) UIPlacementGizmo.toScreenY(Placement.HEIGHT * placement.windowY + placement.offsetY, viewport);

        context.batcher.box(ax - 1, ay - 1, ax + 1, ay + 1, Colors.WHITE);
        context.batcher.unclip(context);
    }
}
