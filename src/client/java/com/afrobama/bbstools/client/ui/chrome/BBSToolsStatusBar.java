// Coded by Afrobama/Claude — UI Overhaul Phase 13: 22px bottom-most status bar
package com.afrobama.bbstools.client.ui.chrome;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency;
import com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import net.minecraft.client.Minecraft;

/**
 * Phase 13 — bottom-most status row.
 *
 * <p>22 px tall, sits below the page navigator. Three info regions:</p>
 * <ul>
 *   <li>Left — current FPS, average over last second</li>
 *   <li>Center — undo stack size + redo stack size, transparency percent</li>
 *   <li>Right — editor-mode badge (NORMAL / EDIT-MODE), mod version</li>
 * </ul>
 *
 * <p>Updates every render frame (cheap — three text draws + one box).</p>
 */
public class BBSToolsStatusBar extends UIElement
{
    public static final int HEIGHT = 16;
    private static final int PAD_X = 6;

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int y2 = y + HEIGHT;

        context.batcher.box(x, y, x + w, y2, BBSToolsTheme.BG_PANEL);
        context.batcher.box(x, y, x + w, y + 1, BBSToolsTheme.BORDER);

        int textY = y + (HEIGHT - 8) / 2;

        /* Left — FPS. */
        String fpsStr = fpsLabel();
        context.batcher.text(fpsStr, x + PAD_X, textY, BBSToolsTheme.TEXT_DIM);

        /* Center — undo/redo + transparency. */
        int undoN = BBSToolsUndoStack.size();
        int redoN = BBSToolsUndoStack.redoSize();
        int alpha = Math.round(BBSToolsTransparency.PANEL_BG_ALPHA * 100F);
        String centerStr = String.format("u%d r%d · α%d%%", undoN, redoN, alpha);
        int centerW = context.batcher.getFont().getWidth(centerStr);
        context.batcher.text(centerStr, x + (w - centerW) / 2, textY, BBSToolsTheme.TEXT_DIM);

        /* Right — editor-mode + version. */
        String rightStr = buildRightSegment();
        int rightW = context.batcher.getFont().getWidth(rightStr);
        context.batcher.text(rightStr, x + w - PAD_X - rightW, textY, BBSToolsTheme.TEXT_DIM);

        super.render(context);
    }

    private static String fpsLabel()
    {
        try
        {
            int fps = Minecraft.getInstance().getFps();
            return "fps " + fps;
        }
        catch (Throwable ignored) { return "fps ?"; }
    }

    private static String buildRightSegment()
    {
        StringBuilder sb = new StringBuilder();
        try
        {
            if (com.afrobama.bbstools.editormode.EditorMode.ACTIVE)
            {
                sb.append("EDIT-MODE · ");
            }
        }
        catch (Throwable ignored) { /* editor mode flag not present in some builds */ }
        sb.append(com.afrobama.bbstools.client.ui.theme.BBSToolsBrand.NAME);
        sb.append(' ');
        sb.append(com.afrobama.bbstools.client.ui.theme.BBSToolsBrand.VERSION);
        return sb.toString();
    }

}
