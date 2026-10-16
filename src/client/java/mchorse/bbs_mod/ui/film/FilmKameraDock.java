package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.settings.EditorZustaende;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The camera view ({@link UIFilmKameraAnsicht}) as a dock panel, after {@code FilmUndoHistoryDock}:
 * WHETHER it is docked lives in {@link EditorZustaende#kameraAnsicht}, WHERE in the saved layout tree
 * under {@link #PANEL_ID}. Absent from an older layout means "never docked", not "closed".
 * First placement: above the replay list, top left as in Blender's Animation workspace.
 */
public class FilmKameraDock
{
    /** ⚠ Persisted in the user's layout file -- never rename. */
    public static final String PANEL_ID = "cameraView";

    private static final String ANKER = "replaysList";
    private static final float FALLBACK = 0.25F;

    private final UIFilmKameraAnsicht ansicht = new UIFilmKameraAnsicht();
    private final Supplier<EditorLayoutNode> lesen;
    private final Consumer<EditorLayoutNode> schreiben;
    private final Runnable neuLegen;

    public FilmKameraDock(Supplier<EditorLayoutNode> lesen, Consumer<EditorLayoutNode> schreiben, Runnable neuLegen)
    {
        this.lesen = lesen;
        this.schreiben = schreiben;
        this.neuLegen = neuLegen;
    }

    public UIFilmKameraAnsicht getPanel()
    {
        return this.ansicht;
    }

    public boolean isDocked()
    {
        return EditorZustaende.kameraAnsicht.get();
    }

    public void toggleDocked()
    {
        EditorZustaende.kameraAnsicht.set(!this.isDocked());

        EditorLayoutNode root = this.lesen.get();
        EditorLayoutNode next = this.reconcile(root);

        if (next != root)
        {
            this.schreiben.accept(next);
            this.neuLegen.run();
        }
    }

    /** Change the selected area between its original editor and the film-camera view. */
    public void editorTyp(String von, String nach)
    {
        if (von.equals(nach))
        {
            return;
        }

        EditorLayoutNode root = this.lesen.get();
        EditorLayoutNode next;

        if (PANEL_ID.equals(nach))
        {
            String original = ursprung(root);
            if (original != null) root = EditorLayoutNode.copyWithStackActivePanel(root, PANEL_ID, original);
            next = EditorLayoutNode.copyWithInsertStackAt(root, von, PANEL_ID);
            EditorZustaende.kameraAnsicht.set(true);
        }
        else
        {
            root = EditorLayoutNode.copyWithStackActivePanel(root, PANEL_ID, nach);
            next = EditorLayoutNode.copyWithRemovedPanel(root, PANEL_ID);
            if (next == null) next = new EditorLayoutNode.PanelNode(nach);
            EditorZustaende.kameraAnsicht.set(false);
        }

        this.schreiben.accept(next);
        this.neuLegen.run();
    }

    public String urspruenglicherTyp()
    {
        String id = ursprung(this.lesen.get());
        return id == null ? "preview" : id;
    }

    private static String ursprung(EditorLayoutNode node)
    {
        if (node instanceof EditorLayoutNode.StackNode stack && stack.containsPanel(PANEL_ID))
        {
            int index = stack.getPanelIds().indexOf(PANEL_ID);
            if (index > 0) return stack.getPanelIds().get(index - 1);
        }
        else if (node instanceof EditorLayoutNode.SplitterNode split)
        {
            String first = ursprung(split.getFirst());
            return first != null ? first : ursprung(split.getSecond());
        }

        return null;
    }

    /** A layout preset carries the view: its tree sets the switch, else reconcile would undo the preset */
    public EditorLayoutNode ausPreset(EditorLayoutNode root)
    {
        EditorZustaende.kameraAnsicht.set(EditorLayoutNode.containsPanel(root, PANEL_ID));

        return root;
    }

    /** The tree in line with the switch; the same instance when nothing changes */
    public EditorLayoutNode reconcile(EditorLayoutNode root)
    {
        if (root == null || this.isDocked() == EditorLayoutNode.containsPanel(root, PANEL_ID))
        {
            return root;
        }

        if (!this.isDocked())
        {
            EditorLayoutNode out = EditorLayoutNode.copyWithRemovedPanel(root, PANEL_ID);

            return out == null ? root : out;
        }

        /* copyWithInsertSplitAt returns the tree unchanged without the anchor: verify, else split the root */
        EditorLayoutNode out = EditorLayoutNode.copyWithInsertSplitAt(root, ANKER, PANEL_ID, EditorLayoutNode.EDGE_TOP);

        return out != null && EditorLayoutNode.containsPanel(out, PANEL_ID) ? out
            : new EditorLayoutNode.SplitterNode(false, FALLBACK, new EditorLayoutNode.PanelNode(PANEL_ID), root);
    }
}
