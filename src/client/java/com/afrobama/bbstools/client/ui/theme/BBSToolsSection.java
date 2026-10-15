// Coded by Afrobama/Claude — UI Overhaul Phase 2: collapsible section header
package com.afrobama.bbstools.client.ui.theme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Section header in AE/Premiere style — small-caps label, arrow chevron
 * indicating collapsed state, 1px underline accent. Children are added
 * as sub-elements; when {@link #collapsed} is true, the child render
 * loop is skipped so the body appears hidden.
 *
 * <p>Used to group dense field lists (e.g.
 * UIPhysicsBlockDestructionClip's 19 fields → 4 sections) without the
 * old BBS-CML wall-of-fields feel.</p>
 */
public class BBSToolsSection extends UIElement
{
    private final String label;
    public boolean collapsed;

    /** Optional accent color for the underline (defaults to BORDER). */
    public int accent = BBSToolsTheme.BORDER;

    public BBSToolsSection(String label)
    {
        this.label = label;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (context.mouseY >= this.area.y
            && context.mouseY < this.area.y + BBSToolsTheme.SECTION_HEADER_H
            && this.area.isInside(context.mouseX, context.mouseY))
        {
            this.collapsed = !this.collapsed;
            return true;
        }
        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;

        /* Header text: small-caps style by uppercase + faint tone. */
        String upper = this.label == null ? "" : this.label.toUpperCase();
        context.batcher.text(upper, x + BBSToolsTheme.PAD_SMALL, y + 3,
            BBSToolsTheme.TEXT_DIM);

        /* Chevron indicator on right side. */
        int chevX = x + w - BBSToolsTheme.PAD_MEDIUM;
        int chevY = y + 5;
        int chev = BBSToolsTheme.TEXT_DIM;
        if (this.collapsed)
        {
            /* ▶ — right-facing triangle */
            context.batcher.box(chevX, chevY, chevX + 1, chevY + 6, chev);
            context.batcher.box(chevX + 1, chevY + 1, chevX + 2, chevY + 5, chev);
            context.batcher.box(chevX + 2, chevY + 2, chevX + 3, chevY + 4, chev);
        }
        else
        {
            /* ▼ — down-facing triangle */
            context.batcher.box(chevX - 3, chevY + 1, chevX + 4, chevY + 2, chev);
            context.batcher.box(chevX - 2, chevY + 2, chevX + 3, chevY + 3, chev);
            context.batcher.box(chevX - 1, chevY + 3, chevX + 2, chevY + 4, chev);
        }

        /* Underline accent — 1px row. */
        int underY = y + BBSToolsTheme.SECTION_HEADER_H - 1;
        context.batcher.box(x, underY, x + w, underY + 1, this.accent);

        if (!this.collapsed)
        {
            super.render(context);
        }
    }

}
