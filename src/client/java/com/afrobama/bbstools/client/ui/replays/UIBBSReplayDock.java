// Coded by Afrobama/Claude — UI Overhaul Phase 10: dedicated replay-editor dock
package com.afrobama.bbstools.client.ui.replays;

import com.afrobama.bbstools.client.ui.overlay.BBSToolsOverlayHelper;
import com.afrobama.bbstools.client.ui.theme.BBSToolsButton;
import com.afrobama.bbstools.client.ui.theme.BBSToolsSection;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.undo.BBSToolsUndoEntry;
import com.afrobama.bbstools.client.ui.undo.BBSToolsUndoStack;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 10 — dedicated replay-editor dock. Two-pane overlay panel:
 * left column is a scrollable list of replays in the currently-active
 * film, right column shows properties + actions for the selected
 * replay. This is BBSTools' answer to Refreshed's
 * {@code UIReplaysEditor} but built entirely on our own component
 * library so the visual language stays AE/Premiere instead of M3.
 *
 * <p>Initial scope is intentionally narrow: select + rename + toggle
 * actor flag + focus camera. Bulk operations and per-track keyframe
 * editing are follow-ups (Phase 10b / Phase 12).</p>
 */
public class UIBBSReplayDock extends UIOverlayPanel
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/replay-dock");

    public final UIScrollView replayList;
    public final BBSToolsSection listSection;
    public final BBSToolsSection propsSection;
    public final UITextbox nameField;
    public final BBSToolsButton actorBtn;
    public final BBSToolsButton focusBtn;
    public final BBSToolsButton refreshBtn;
    public final BBSToolsButton deleteBtn;
    public final BBSToolsButton duplicateBtn;
    public final BBSToolsButton moveUpBtn;
    public final BBSToolsButton moveDownBtn;

    /** Index into {@link #cachedReplays} of the selected replay. */
    private int selectedIndex = -1;

    /** Snapshot rebuilt every refresh — Replay refs we display in the
     *  list. Kept so click handlers don't have to re-walk the film. */
    private List<Replay> cachedReplays = new ArrayList<>();

    public static void open()
    {
        BBSToolsOverlayHelper.open(new UIBBSReplayDock(), 480, 0.55F);
    }

    public static UIBBSReplayDock getOpened()
    {
        return BBSToolsOverlayHelper.getOpened(UIBBSReplayDock.class);
    }

    public static boolean isOpened()
    {
        return getOpened() != null;
    }

    public UIBBSReplayDock()
    {
        super(IKey.constant("Replay Dock"));
        this.resizable();

        /* === Refresh button (top right) === */
        this.refreshBtn = new BBSToolsButton("Refresh").onClick(this::rebuild);
        this.refreshBtn.relative(this.content).x(1F, -90).y(4).w(80).h(18);

        /* === Left pane: replay list (under refresh button row) === */
        this.listSection = new BBSToolsSection("REPLAYS");
        this.listSection.accent = BBSToolsTheme.ACCENT_CAMERA;
        this.listSection.relative(this.content).x(6).y(28).w(180).h(1F, -34);

        this.replayList = new UIScrollView();
        this.replayList.relative(this.listSection).x(0).y(BBSToolsTheme.SECTION_HEADER_H)
            .w(1F).h(1F, -BBSToolsTheme.SECTION_HEADER_H);
        this.replayList.column(2).vertical().stretch().scroll().padding(2);
        this.listSection.add(this.replayList);

        /* === Right pane: properties === */
        this.propsSection = new BBSToolsSection("PROPERTIES");
        this.propsSection.accent = BBSToolsTheme.ACCENT_PRIMARY;
        this.propsSection.relative(this.content).x(192).y(28).w(1F, -198).h(1F, -34);

        this.nameField = new UITextbox(120, this::applyRename);
        this.nameField.placeholder(IKey.constant("(name)"));
        this.nameField.relative(this.propsSection).x(6).y(20).w(1F, -12).h(18);

        this.actorBtn = new BBSToolsButton("Actor: ?")
            .onClick(this::toggleActor);
        this.actorBtn.relative(this.propsSection).x(6).y(44).w(120).h(18);

        this.focusBtn = new BBSToolsButton("Focus camera").primary()
            .onClick(this::focusCamera);
        this.focusBtn.relative(this.propsSection).x(6).y(68).w(150).h(18);

        this.deleteBtn = new BBSToolsButton("Delete replay").destructive()
            .onClick(this::deleteSelected);
        this.deleteBtn.relative(this.propsSection).x(160).y(68).w(110).h(18);

        this.duplicateBtn = new BBSToolsButton("Duplicate")
            .onClick(this::duplicateSelected);
        this.duplicateBtn.relative(this.propsSection).x(6).y(92).w(110).h(18);

        this.moveUpBtn = new BBSToolsButton("Move ↑")
            .onClick(() -> this.moveSelected(-1));
        this.moveUpBtn.relative(this.propsSection).x(120).y(92).w(60).h(18);

        this.moveDownBtn = new BBSToolsButton("Move ↓")
            .onClick(() -> this.moveSelected(+1));
        this.moveDownBtn.relative(this.propsSection).x(184).y(92).w(60).h(18);

        this.propsSection.add(this.nameField, this.actorBtn,
            this.focusBtn, this.deleteBtn, this.duplicateBtn,
            this.moveUpBtn, this.moveDownBtn);

        this.content.add(this.refreshBtn, this.listSection, this.propsSection);

        this.minW(480).maxW(720);
        this.minH(280).maxH(540);

        this.rebuild();
    }

    private void rebuild()
    {
        this.cachedReplays = collectReplays();
        this.replayList.removeAll();

        if (this.cachedReplays.isEmpty())
        {
            this.replayList.add(this.themedEmptyRow("(no film open)"));
            this.applySelection(-1);
            return;
        }

        for (int i = 0; i < this.cachedReplays.size(); i++)
        {
            Replay r = this.cachedReplays.get(i);
            String name = safeName(r);
            int idx = i;
            BBSToolsButton row = new BBSToolsButton((idx == this.selectedIndex ? "▶ " : "  ") + name)
                .onClick(() -> this.applySelection(idx));
            row.h(18);
            this.replayList.add(row);
        }

        this.replayList.resize();
        if (this.selectedIndex < 0 || this.selectedIndex >= this.cachedReplays.size())
        {
            this.applySelection(this.cachedReplays.isEmpty() ? -1 : 0);
        }
        else
        {
            this.applySelection(this.selectedIndex);
        }
    }

    private void applySelection(int idx)
    {
        this.selectedIndex = idx;
        Replay r = this.currentReplay();
        if (r == null)
        {
            this.nameField.setText("");
            this.actorBtn.enabled(false).label("Actor: —");
            this.focusBtn.enabled(false);
            this.deleteBtn.enabled(false);
            this.duplicateBtn.enabled(false);
            this.moveUpBtn.enabled(false);
            this.moveDownBtn.enabled(false);
            return;
        }
        try { this.nameField.setText(safeName(r)); }
        catch (Throwable t) { LOG.warn("failed to fill name field", t); }

        boolean isActor = false;
        try { isActor = Boolean.TRUE.equals(r.actor.get()); }
        catch (Throwable t) { LOG.warn("failed to read replay.actor", t); }

        /* M1 from the review: label is mutable now, so reflect the
         * current state without a full list rebuild. */
        this.actorBtn.enabled(true).label("Actor: " + (isActor ? "ON" : "OFF"));
        this.focusBtn.enabled(true);
        this.deleteBtn.enabled(true);
        this.duplicateBtn.enabled(true);
        this.moveUpBtn.enabled(this.selectedIndex > 0);
        this.moveDownBtn.enabled(this.selectedIndex < this.cachedReplays.size() - 1);
    }

    private void applyRename(String newName)
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        if (!this.replayStillCurrent(r)) { this.rebuild(); return; }

        String before;
        try { before = r.label.get(); }
        catch (Throwable t) { LOG.warn("failed to read replay.label", t); return; }
        String after = newName == null ? "" : newName;
        if (after.equals(before)) return;

        final String finalBefore = before;
        try
        {
            r.label.set(after);
            BBSToolsUndoStack.push(new BBSToolsUndoEntry(
                "Rename replay " + (finalBefore.isEmpty() ? "(unnamed)" : finalBefore) + " → " + after,
                () -> { try { r.label.set(finalBefore); }
                        catch (Throwable t) { LOG.warn("undo rename failed", t); } },
                () -> { try { r.label.set(after); }
                        catch (Throwable t) { LOG.warn("redo rename failed", t); } }));
        }
        catch (Throwable t) { LOG.warn("rename failed", t); }
        this.rebuild();
    }

    private void toggleActor()
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        if (!this.replayStillCurrent(r)) { this.rebuild(); return; }

        boolean before;
        try { before = Boolean.TRUE.equals(r.actor.get()); }
        catch (Throwable t) { LOG.warn("failed to read replay.actor", t); return; }
        boolean after = !before;
        try
        {
            r.actor.set(after);
            BBSToolsUndoStack.push(new BBSToolsUndoEntry(
                "Toggle actor flag on " + safeName(r) + (after ? " → ON" : " → OFF"),
                () -> { try { r.actor.set(before); }
                        catch (Throwable t) { LOG.warn("undo actor-toggle failed", t); } },
                () -> { try { r.actor.set(after); }
                        catch (Throwable t) { LOG.warn("redo actor-toggle failed", t); } }));
            this.actorBtn.label("Actor: " + (after ? "ON" : "OFF"));
        }
        catch (Throwable t) { LOG.warn("actor toggle failed", t); }
    }

    private void focusCamera()
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        if (!this.replayStillCurrent(r)) { this.rebuild(); return; }
        try
        {
            com.afrobama.bbstools.client.editorcam.EditorCamHandler.cycleTo(r);
        }
        catch (Throwable t) { LOG.warn("focusCamera failed", t); }
    }

    /** Phase 10b — re-order the selected replay by {@code delta} slots
     *  (negative = move up). Uses {@link mchorse.bbs_mod.film.replays.Replays#remove}
     *  + {@link mchorse.bbs_mod.settings.values.core.ValueList#add(int, Object)}
     *  so the BBS notifyChange + sync pipeline still runs.
     *
     *  <p>M1 fix from the final review: undo/redo recompute the
     *  target position from the replay's CURRENT index plus a stored
     *  delta. Capturing absolute indices broke when intermediate
     *  reorders shifted other replays around (move A↓, move B↓,
     *  undo A → A landed at the wrong slot).</p>
     *
     *  <p>After remove(r) the list shrinks by one. ValueList.add(int, T)
     *  falls back to append when the index is out of range, which
     *  happens to give the correct final position for moveDown-by-N.
     *  Do not "fix" by pre-decrementing toIdx when delta &gt; 0. */
    private void moveSelected(int delta)
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        mchorse.bbs_mod.film.replays.Replays replays = currentReplaysContainer();
        if (replays == null || !this.replayStillCurrent(r)) { this.rebuild(); return; }

        java.util.List<Replay> list = replays.getList();
        int from = list.indexOf(r);
        if (from < 0) { this.rebuild(); return; }
        int to = from + delta;
        if (to < 0 || to >= list.size() || to == from) return;
        final int finalDelta = delta;
        try
        {
            replays.remove(r);
            replays.add(to, r);
            this.selectedIndex = to;
            BBSToolsUndoStack.push(new BBSToolsUndoEntry(
                "Move " + safeName(r) + " " + (delta < 0 ? "up" : "down"),
                () -> moveRelative(replays, r, -finalDelta),
                () -> moveRelative(replays, r, +finalDelta)));
        }
        catch (Throwable t) { LOG.warn("move-replay failed", t); }
        this.rebuild();
    }

    /** Helper for the M1 fix — apply a relative-delta move to a
     *  replay whose absolute position may have shifted. No-op if the
     *  replay is gone or the target is out of range. */
    private static void moveRelative(mchorse.bbs_mod.film.replays.Replays replays,
                                     Replay r, int delta)
    {
        try
        {
            java.util.List<Replay> list = replays.getList();
            int now = list.indexOf(r);
            if (now < 0) return;
            int target = now + delta;
            int max = list.size() - 1;
            if (target < 0) target = 0;
            if (target > max) target = max;
            if (target == now) return;
            replays.remove(r);
            replays.add(target, r);
        }
        catch (Throwable t) { LOG.warn("moveRelative failed", t); }
    }

    private void duplicateSelected()
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        mchorse.bbs_mod.film.replays.Replays replays = currentReplaysContainer();
        if (replays == null || !this.replayStillCurrent(r)) { this.rebuild(); return; }

        try
        {
            Replay copy = replays.addReplay();
            /* toData() + fromData() round-trip is the canonical deep
             * clone for BaseValueGroup subclasses (keyframes, custom
             * sheet titles, sheet colors, anchors — all preserved). */
            copy.fromData(r.toData());
            String suffix = " (copy)";
            try { copy.label.set(safeName(r) + suffix); }
            catch (Throwable t) { LOG.warn("rename of duplicate failed", t); }

            /* C2 fix from the final review: redo re-uses the same
             * `copy` instance via ValueList.add(T) so the reference
             * we captured stays the live object in the film's list
             * across undo/redo cycles. The previous implementation
             * spawned a fresh replay each redo, leaving any external
             * holder of `copy` pointing at a detached object. */
            BBSToolsUndoStack.push(new BBSToolsUndoEntry(
                "Duplicate replay " + safeName(r),
                () -> {
                    try { replays.remove(copy); }
                    catch (Throwable t) { LOG.warn("undo duplicate failed", t); }
                },
                () -> {
                    try { replays.add(copy); }
                    catch (Throwable t) { LOG.warn("redo duplicate failed", t); }
                }));
        }
        catch (Throwable t) { LOG.warn("duplicate-replay failed", t); }
        this.rebuild();
    }

    private void deleteSelected()
    {
        Replay r = this.currentReplay();
        if (r == null) return;
        mchorse.bbs_mod.film.replays.Replays replays = currentReplaysContainer();
        if (replays == null || !this.replayStillCurrent(r)) { this.rebuild(); return; }

        int originalIndex = replays.getList().indexOf(r);
        if (originalIndex < 0) { this.rebuild(); return; }
        String name = safeName(r);
        try
        {
            replays.remove(r);
            BBSToolsUndoStack.push(new BBSToolsUndoEntry(
                "Delete replay " + name,
                () -> {
                    try { replays.add(originalIndex, r); }
                    catch (Throwable t) { LOG.warn("undo delete-replay failed", t); }
                },
                () -> {
                    try { replays.remove(r); }
                    catch (Throwable t) { LOG.warn("redo delete-replay failed", t); }
                }));
            this.selectedIndex = -1;
        }
        catch (Throwable t) { LOG.warn("delete-replay failed", t); }
        this.rebuild();
    }

    /** Walk the same controller→panel chain {@code collectReplays}
     *  uses, but return the live {@link mchorse.bbs_mod.film.replays.Replays}
     *  container instead of a snapshot list. We need the container to
     *  call {@code add(index, value)} / {@code remove(value)}. */
    private static mchorse.bbs_mod.film.replays.Replays currentReplaysContainer()
    {
        try
        {
            java.util.List<mchorse.bbs_mod.film.BaseFilmController> ctls =
                mchorse.bbs_mod.BBSModClient.getFilms().getControllers();
            if (ctls != null && !ctls.isEmpty())
            {
                mchorse.bbs_mod.film.BaseFilmController ctl = ctls.get(ctls.size() - 1);
                if (ctl != null && ctl.film != null) return ctl.film.replays;
            }
        }
        catch (Throwable t) { LOG.warn("currentReplaysContainer failed", t); }
        return null;
    }

    /** C5 from the review — guards every mutation against the replay
     *  having dropped out of the active film since the dock cached it.
     *  Without this guard, renaming an orphan after a film-switch
     *  silently no-ops or worse mutates a detached object. */
    private boolean replayStillCurrent(Replay r)
    {
        List<Replay> live = collectReplays();
        return live.contains(r);
    }

    private Replay currentReplay()
    {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.cachedReplays.size())
        {
            return null;
        }
        return this.cachedReplays.get(this.selectedIndex);
    }

    /** M3 from the review — delegate to EditorCamHandler so the dock
     *  picks up the same film F8-cycle uses (playing → edited →
     *  last-snapshotted), not just the playing controller. */
    private static List<Replay> collectReplays()
    {
        try
        {
            return com.afrobama.bbstools.client.editorcam.EditorCamHandler.collectReplays();
        }
        catch (Throwable t)
        {
            LOG.warn("collectReplays delegation failed", t);
            return new ArrayList<>();
        }
    }

    private UIElement themedEmptyRow(String text)
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

    private static String safeName(Replay r)
    {
        return com.afrobama.bbstools.client.editorcam.EditorCamHandler.safeName(r);
    }

    @Override
    public void render(UIContext context)
    {
        /* Render the form-preview icon next to the selected entry so
         * the right pane shows which form the replay drives. */
        Replay r = this.currentReplay();
        if (r != null)
        {
            try
            {
                Form f = r.form == null ? null : r.form.get();
                if (f != null)
                {
                    int px = this.propsSection.area.x + 6;
                    int py = this.propsSection.area.y + 96;
                    int pw = Math.min(96, this.propsSection.area.w - 12);
                    int ph = Math.min(96, this.propsSection.area.h - 96 - 6);
                    if (pw > 16 && ph > 16)
                    {
                        context.batcher.box(px, py, px + pw, py + ph, BBSToolsTheme.BG_FIELD);
                        context.batcher.outline(px, py, px + pw, py + ph, BBSToolsTheme.BORDER);
                        context.batcher.clip(px + 1, py + 1, pw - 2, ph - 2, context);
                        FormUtilsClient.renderUI(f, context, px + 1, py + 1, px + pw - 1, py + ph - 1);
                        context.batcher.unclip(context);
                    }
                }
            }
            catch (Throwable t)
            {
                LOG.warn("form preview render failed", t);
            }
        }

        super.render(context);
    }

}
