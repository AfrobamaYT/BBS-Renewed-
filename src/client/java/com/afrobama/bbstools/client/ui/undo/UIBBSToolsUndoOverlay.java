// Coded by Afrobama/Claude — UI Overhaul Phase 9: undo history overlay panel
package com.afrobama.bbstools.client.ui.undo;

import com.afrobama.bbstools.client.ui.overlay.BBSToolsOverlayHelper;
import com.afrobama.bbstools.client.ui.theme.BBSToolsButton;
import com.afrobama.bbstools.client.ui.theme.BBSToolsSection;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import net.minecraft.client.Minecraft;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Phase 9 deliverable — overlay-style inspector for the
 * {@link BBSToolsUndoStack}. Lists undo + redo stacks in two collapsible
 * sections, lets the user click a single row to walk back/forward to
 * that point, and exposes header buttons for one-shot undo / redo /
 * clear.
 *
 * <p>Aesthetics follow the AE/Premiere direction from
 * {@code STYLE_GUIDE.md} — sharp 0–2 px corners, multi-accent palette,
 * uppercase section headers in {@code TEXT_DIM}.</p>
 *
 * <p>The overlay re-reads the stack on every render so we don't need
 * to subscribe individual rows. With CAPACITY = 64 a 1-Hz re-list is
 * negligible.</p>
 */
public class UIBBSToolsUndoOverlay extends UIOverlayPanel
{
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    public final BBSToolsSection undoSection;
    public final BBSToolsSection redoSection;
    public final UIScrollView undoList;
    public final UIScrollView redoList;
    public final BBSToolsButton undoBtn;
    public final BBSToolsButton redoBtn;
    public final BBSToolsButton clearBtn;

    private int lastUndoSize = -1;
    private int lastRedoSize = -1;

    public static void open()
    {
        BBSToolsOverlayHelper.open(new UIBBSToolsUndoOverlay(), 280, 0.5F);
    }

    public static UIBBSToolsUndoOverlay getOpened()
    {
        return BBSToolsOverlayHelper.getOpened(UIBBSToolsUndoOverlay.class);
    }

    public static boolean isOpened()
    {
        return getOpened() != null;
    }

    public UIBBSToolsUndoOverlay()
    {
        super(IKey.constant("Undo History"));

        this.resizable();

        /* === Header buttons row === */
        this.undoBtn = new BBSToolsButton("Undo").primary().onClick(() ->
        {
            BBSToolsUndoStack.undo();
            this.invalidate();
        });
        this.undoBtn.relative(this.content).x(6).y(4).w(70).h(18);

        this.redoBtn = new BBSToolsButton("Redo").onClick(() ->
        {
            BBSToolsUndoStack.redo();
            this.invalidate();
        });
        this.redoBtn.relative(this.content).x(80).y(4).w(70).h(18);

        this.clearBtn = new BBSToolsButton("Clear").destructive().onClick(() ->
        {
            BBSToolsUndoStack.clear();
            this.invalidate();
        });
        this.clearBtn.relative(this.content).x(154).y(4).w(60).h(18);

        /* === Undo section (top — most-recent edit first) === */
        this.undoSection = new BBSToolsSection("UNDO STACK");
        this.undoSection.accent = BBSToolsTheme.ACCENT_PRIMARY;
        this.undoSection.relative(this.content).x(6).y(28).w(1F, -12).h(220);

        this.undoList = new UIScrollView();
        this.undoList.relative(this.undoSection).x(0).y(BBSToolsTheme.SECTION_HEADER_H)
            .w(1F).h(1F, -BBSToolsTheme.SECTION_HEADER_H);
        this.undoList.column(1).vertical().stretch().scroll().padding(2);
        this.undoSection.add(this.undoList);

        /* === Redo section (bottom — what was undone) === */
        this.redoSection = new BBSToolsSection("REDO STACK");
        this.redoSection.accent = BBSToolsTheme.ACCENT_CAMERA;
        this.redoSection.collapsed = true;
        this.redoSection.relative(this.content).x(6).y(252).w(1F, -12).h(120);

        this.redoList = new UIScrollView();
        this.redoList.relative(this.redoSection).x(0).y(BBSToolsTheme.SECTION_HEADER_H)
            .w(1F).h(1F, -BBSToolsTheme.SECTION_HEADER_H);
        this.redoList.column(1).vertical().stretch().scroll().padding(2);
        this.redoSection.add(this.redoList);

        this.content.add(this.undoBtn, this.redoBtn, this.clearBtn,
            this.undoSection, this.redoSection);

        this.minW(280).maxW(420);
        this.minH(380).maxH(560);

        this.invalidate();
    }

    /** Re-builds both lists from the current stack snapshot. Cheap
     *  enough at CAPACITY=64 to call on every state-change. */
    private void invalidate()
    {
        List<BBSToolsUndoEntry> undo = BBSToolsUndoStack.snapshot();
        List<BBSToolsUndoEntry> redo = BBSToolsUndoStack.redoSnapshot();
        this.lastUndoSize = undo.size();
        this.lastRedoSize = redo.size();

        this.undoList.removeAll();
        this.redoList.removeAll();

        /* snapshot ordering: index 0 is most-recent. For undo rows the
         * top row is the *current* state (0 pops); for redo rows the
         * top row is the next-redo (1 pop). */
        for (int i = 0; i < undo.size(); i++)
        {
            BBSToolsUndoEntry entry = undo.get(i);
            this.undoList.add(this.buildRow(entry, false, i));
        }
        if (undo.isEmpty())
        {
            this.undoList.add(this.emptyRow("(no edits yet)"));
        }

        for (int i = 0; i < redo.size(); i++)
        {
            BBSToolsUndoEntry entry = redo.get(i);
            this.redoList.add(this.buildRow(entry, true, i + 1));
        }
        if (redo.isEmpty())
        {
            this.redoList.add(this.emptyRow("(nothing to redo)"));
        }

        this.undoList.resize();
        this.redoList.resize();
        this.undoBtn.enabled(!undo.isEmpty());
        this.redoBtn.enabled(!redo.isEmpty());
        this.clearBtn.enabled(!undo.isEmpty() || !redo.isEmpty());
    }

    /** Build one stack row. {@code popsToTarget} is the literal number
     *  of {@link BBSToolsUndoStack#undo()} or {@code .redo()} calls
     *  required to land on this entry — captured explicitly so the
     *  click handler never has to reason about ordering of the
     *  snapshot list. (Bug C1 fix from Phase 10 review.) */
    private UIElement buildRow(BBSToolsUndoEntry entry, boolean isRedo, int popsToTarget)
    {
        String time = TIME_FMT.format(new Date(entry.timestampMs));
        String prefix = isRedo
            ? "+" + popsToTarget
            : (popsToTarget == 0 ? "now" : "-" + popsToTarget);
        String label = "[" + prefix + "  " + time + "] " + entry.label;

        BBSToolsButton row = new BBSToolsButton(label)
            .onClick(() ->
            {
                for (int i = 0; i < popsToTarget; i++)
                {
                    boolean ok = isRedo ? BBSToolsUndoStack.redo() : BBSToolsUndoStack.undo();
                    if (!ok) break;
                }
                this.invalidate();
            });
        row.h(18);
        return row;
    }

    private UIElement emptyRow(String text)
    {
        return new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.text(text,
                    this.area.x + 4, this.area.y + 4, BBSToolsTheme.TEXT_FAINT);
                super.render(context);
            }
        }.h(14);
    }

    @Override
    public void render(UIContext context)
    {
        /* Drift detection — if either stack size changed underneath
         * us (e.g. a programmatic edit triggered push, which also
         * clears the redo stack), schedule a rebuild on the NEXT
         * frame. Doing it inline would mutate the children list while
         * the framework is iterating it during render. (Bugs C2 + M5
         * from the Phase 10 review.) */
        int u = BBSToolsUndoStack.size();
        int r = BBSToolsUndoStack.redoSize();
        if (u != this.lastUndoSize || r != this.lastRedoSize)
        {
            this.lastUndoSize = u;
            this.lastRedoSize = r;
            Minecraft.getInstance().execute(this::invalidate);
        }
        super.render(context);
    }

}
