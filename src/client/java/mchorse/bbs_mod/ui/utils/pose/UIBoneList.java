package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Self-contained bone list: the multi-select {@link UIPoseBoneStringList} plus the header that
 * belongs to it &mdash; a name search and the mirror-edit / alternate-invert toggles. Bundling
 * them here means wherever the list is placed (form pose editor, film pose keyframe editor) the
 * header travels with it, instead of every host re-assembling the row by hand.
 *
 * <p>The host wires {@link #onFiltered} to react after each (re)fill: pick a bone and show or hide
 * the dependent editors. Mirror/invert are global {@link BBSSettings} flags, so the buttons just
 * flip them and the highlight reflects their state.</p>
 */
public class UIBoneList extends UIElement
{
    public final UIPoseBoneStringList list;

    private final UITextbox search;
    private final UIIcon mirror;
    private final UIIcon invert;

    /** Unfiltered source kept so the list can be re-filtered as the query changes; {@link #sort}
     *  remembers whether that source should be sorted alphabetically. */
    private final List<String> allGroups = new ArrayList<>();
    private boolean sort;

    /** Fired after each filter pass with the fill's {@code reset} flag, so the host can re-pick a
     *  bone and toggle its dependent editors. */
    public Consumer<Boolean> onFiltered;

    public UIBoneList(Consumer<List<String>> callback)
    {
        /* ⛔ Nach der Wahl eines Knochens den Fokus aus dem SUCHFELD nehmen. Sonst
         * schluckt das Textfeld die naechste Taste: wer den Knochen ertippt und dann R
         * drueckt, tippt ein "R" in die Suche statt zu drehen — der Zug startet nicht
         * (gemessen 2026-08-15: Fokus keiner -> isEditing=true, Fokus UITextbox ->
         * isEditing=false; als Fehler gemeldet und am Bildschirmmitschnitt gefunden). */
        this.list = new UIPoseBoneStringList((namen) ->
        {
            mchorse.bbs_mod.ui.framework.UIContext c = this.getContext();

            if (c != null && c.activeElement instanceof UITextbox)
            {
                c.unfocus();
            }

            callback.accept(namen);
        });
        this.list.background();
        this.list.scroll.cancelScrolling();

        this.search = new UITextbox(100, (str) -> this.filter(false)).placeholder(UIKeys.GENERAL_SEARCH);
        this.search.h(20);

        this.mirror = new UIIcon(Icons.CONVERT, (b) -> this.toggleMirrorEdit());
        this.mirror.tooltip(UIKeys.TRANSFORMS_MIRROR_EDIT);
        this.mirror.wh(20, 20);
        this.invert = new UIIcon(Icons.REVERSE, (b) -> this.toggleAlternateInvert());
        this.invert.tooltip(UIKeys.TRANSFORMS_ALTERNATE_INVERT);
        this.invert.wh(20, 20);

        UIElement header = new UIElement();
        header.h(20).row(0).height(20);
        /* The search fills the row; the two toggles sit fixed-width at the right (all 20px tall). */
        header.add(this.search, this.mirror, this.invert);

        /* Strict: this list sits inside the pose editor and hears keys first, so a loose Shift+F would also swallow Ctrl+Shift+F (flip pose) */
        this.keys().register(Keys.TRANSFORMATIONS_MIRROR_EDIT, this::toggleMirrorEdit).strict().category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
        this.keys().register(Keys.TRANSFORMATIONS_SEARCH_BONE, this::focusSearch).strict().category(UIKeys.TRANSFORMS_KEYS_CATEGORY);

        this.column().vertical().stretch();
        this.add(header, this.list.marginTop(-UIConstants.MARGIN));
    }

    /**
     * Jump into the search box (Ctrl+F by default) so a bone can be found by typing instead of
     * scrolling a long list. The existing query is selected, so the first keystroke replaces it —
     * the same behaviour every editor's find shortcut has.
     */
    private void focusSearch()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.focus(this.search, true);
        }
    }

    /** Whether the source holds any bones at all (independent of the current search query). */
    public boolean hasBones()
    {
        return !this.allGroups.isEmpty();
    }

    public void setSource(Collection<String> groups, boolean sort)
    {
        this.allGroups.clear();
        this.allGroups.addAll(groups);
        this.sort = sort;
    }

    /**
     * Repopulate the visible list from the source, keeping only bones whose name contains the search
     * query, then hand control back to {@link #onFiltered}. Runs on a fresh fill and on every
     * keystroke in the search box.
     */
    public void filter(boolean reset)
    {
        String query = this.search.getText().trim().toLowerCase();
        List<String> visible = new ArrayList<>();

        for (String bone : this.allGroups)
        {
            if (query.isEmpty() || bone.toLowerCase().contains(query))
            {
                visible.add(bone);
            }
        }

        this.list.clear();
        this.list.add(visible);

        if (this.sort)
        {
            this.list.sort();
        }

        if (this.onFiltered != null)
        {
            this.onFiltered.accept(reset);
        }
    }

    private void toggleMirrorEdit()
    {
        BBSSettings.poseMirrorEdit.set(!BBSSettings.poseMirrorEdit.get());
        UIUtils.playClick();
    }

    private void toggleAlternateInvert()
    {
        BBSSettings.poseAlternateInvert.set(!BBSSettings.poseAlternateInvert.get());
        UIUtils.playClick();
    }

    @Override
    public void render(UIContext context)
    {
        if (BBSSettings.poseMirrorEdit.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.mirror.area, Direction.BOTTOM);
        }

        if (BBSSettings.poseAlternateInvert.get())
        {
            UIDashboardPanels.renderHighlight(context.batcher, this.invert.area, Direction.BOTTOM);
        }

        super.render(context);
    }
}
