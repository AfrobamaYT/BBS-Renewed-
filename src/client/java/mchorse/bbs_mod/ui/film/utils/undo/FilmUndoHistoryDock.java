package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Makes the film editor's undo history a dock panel instead of a floating overlay.
 *
 * <p>It owns the one {@link UIUndoHistory} element the film editor docks, and it keeps the persisted
 * layout tree in sync with the user's on/off choice. Everything else — where the panel sits, how wide it
 * is, which stack it shares tabs with — is handled by the existing dock system in UIFilmPanel, exactly
 * like the five panels that were there before.</p>
 *
 * <p>The film panel is reached only through the four handles passed to the constructor, so none of its
 * private layout methods have to be opened up and none of this logic lands in that file.</p>
 *
 * <h2>Why the state is split across two places</h2>
 * <ul>
 *   <li><b>WHETHER</b> the history is docked: {@link BBSSettings#editorUndoHistoryDocked}.</li>
 *   <li><b>WHERE</b> it sits: the layout tree in {@code BBSSettings.editorLayoutSettings}, under
 *       {@link #PANEL_ID}, saved and restored with every other panel.</li>
 * </ul>
 * That is the split every editor with docks uses (Godot's {@code editor_layout.cfg} keeps a
 * {@code dock_closed} list next to the per-slot panel lists; Qt keeps the objectName in the saved state).
 *
 * <p>⚠ FALLE, the classic one when a panel is added to an editor that already ships a saved layout: an
 * existing user's layout file has no {@code undoHistory} entry, and "not in the tree" must NOT be read as
 * "the user closed it". Here absence means the panel was never docked, and the setting — not the tree —
 * decides. When the setting is on and the tree has no entry, {@link #reconcile} puts it at the default
 * slot rather than leaving it invisible.</p>
 */
public class FilmUndoHistoryDock
{
    /**
     * ⚠ Persisted in the user's layout file. NEVER rename this — an id change silently drops every saved
     * position, which is the single most reported docking regression in other editors.
     */
    public static final String PANEL_ID = "undoHistory";

    /**
     * Where the panel lands the first time it is docked: below the replay list, in the left sidebar. The
     * user's request pointed at exactly that free space. It is only a starting point — the saved layout
     * wins from the second run on, and the panel's drag handle moves it anywhere afterwards.
     */
    private static final String DEFAULT_ANCHOR_PANEL_ID = "replaysList";
    private static final int DEFAULT_ANCHOR_EDGE = EditorLayoutNode.EDGE_BOTTOM;
    /** Used only if the anchor is missing from the tree — the new panel then splits off the whole editor. */
    private static final float ROOT_FALLBACK_RATIO = 0.8F;

    private final UIUndoHistory history;
    private final Supplier<EditorLayoutNode> getLayoutRoot;
    private final Consumer<EditorLayoutNode> setLayoutRoot;
    private final Runnable relayout;

    /**
     * @param undoManager   the film's current undo manager; null while no film is loaded.
     * @param context       the film the undo entries apply to.
     * @param getLayoutRoot reads the layout tree of the editor that is currently shown.
     * @param setLayoutRoot writes it back (this is what persists the position).
     * @param relayout      rebuilds the editor's panels from the tree.
     */
    public FilmUndoHistoryDock(Supplier<UndoManager<ValueGroup>> undoManager, Supplier<ValueGroup> context,
        Supplier<EditorLayoutNode> getLayoutRoot, Consumer<EditorLayoutNode> setLayoutRoot, Runnable relayout)
    {
        /* onApplied is null: the film editor's widgets live-track the film data, so driving the undo
         * manager is enough — nothing has to be rebuilt afterwards. */
        this.history = new UIUndoHistory(undoManager, context, null);
        this.getLayoutRoot = getLayoutRoot;
        this.setLayoutRoot = setLayoutRoot;
        this.relayout = relayout;
    }

    /** The element the film panel registers as its dock panel. */
    public UIUndoHistory getPanel()
    {
        return this.history;
    }

    public boolean isDocked()
    {
        return BBSSettings.editorUndoHistoryDocked.get();
    }

    /**
     * Dock or undock, from the film's context menu. The panel element always exists; docking only means
     * putting its id into the layout tree, which is what makes the position survive a restart.
     */
    public void toggleDocked()
    {
        BBSSettings.editorUndoHistoryDocked.set(!this.isDocked());

        this.applyToLayout();
    }

    /**
     * Pick up a dock setting that was flipped outside the film editor (the BBS settings screen). Meant to
     * be called when the panel comes back on screen; it rebuilds nothing unless the tree really differs.
     */
    public void syncWithSettings()
    {
        this.applyToLayout();
    }

    private void applyToLayout()
    {
        EditorLayoutNode root = this.getLayoutRoot.get();
        EditorLayoutNode next = this.reconcile(root);

        if (next == root)
        {
            return;
        }

        this.setLayoutRoot.accept(next);
        this.relayout.run();
    }

    /**
     * Bring the layout tree in line with the setting. Returns the same instance when nothing has to
     * change, so callers can use identity to decide whether a relayout is needed — the same contract
     * UIFilmPanel's {@code ensureFilmLayoutPanels} already uses.
     */
    public EditorLayoutNode reconcile(EditorLayoutNode root)
    {
        if (root == null)
        {
            return null;
        }

        boolean present = EditorLayoutNode.containsPanel(root, PANEL_ID);

        if (this.isDocked() == present)
        {
            return root;
        }

        return present ? removePanel(root) : insertPanel(root);
    }

    private static EditorLayoutNode removePanel(EditorLayoutNode root)
    {
        EditorLayoutNode out = EditorLayoutNode.copyWithRemovedPanel(root, PANEL_ID);

        return out == null ? root : out;
    }

    /**
     * ⚠ FALLE — {@link EditorLayoutNode#copyWithInsertSplitAt} returns the tree UNCHANGED when the anchor
     * is not in it (a layout preset or an older file may not have the replay list). Silently returning
     * that would leave the setting saying "docked" while nothing appears: a switch that does nothing, with
     * no error anywhere. So the result is verified, and if the anchor was missing the panel is split off
     * the root instead — ugly placement beats an invisible panel.
     */
    private static EditorLayoutNode insertPanel(EditorLayoutNode root)
    {
        EditorLayoutNode out = EditorLayoutNode.copyWithInsertSplitAt(root, DEFAULT_ANCHOR_PANEL_ID, PANEL_ID, DEFAULT_ANCHOR_EDGE);

        if (out != null && EditorLayoutNode.containsPanel(out, PANEL_ID))
        {
            return out;
        }

        return new EditorLayoutNode.SplitterNode(false, ROOT_FALLBACK_RATIO, root, new EditorLayoutNode.PanelNode(PANEL_ID));
    }
}
