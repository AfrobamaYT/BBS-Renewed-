package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.function.Consumer;

/**
 * A flat, searchable list of every form in the library, with a small 3D preview per row.
 *
 * <p>The form picker ({@link UIFormList}) lays the same forms out as a grid of category
 * sections; that is the right shape when browsing, but not when you already know the name
 * and just want to type it. This list is the typing shape: one row per form, filtered by
 * the same text the picker matches on (display name OR form id — see {@link
 * #elementToString(UIContext, int, Form)} and {@code UIFormCategory.search}), so a query
 * that finds a form in the picker finds it here too.</p>
 *
 * <p>The preview is drawn by {@link FormUtilsClient#renderUI} — the same call the replay
 * menu (F4) uses per row, and it obeys {@link BBSSettings#listModelPreview} like every
 * other list that carries one.</p>
 *
 * <p>FALLE: {@link #addLibraryForms()} does NOT de-duplicate. The "recent" category holds
 * copies of forms that also live in a library category, so a form can appear twice. That
 * is deliberate: two rows with the same display name are not necessarily the same form
 * (a user form can share a name and type with another while differing in texture/pose),
 * and dropping one would silently hide a choice the user has.</p>
 */
public class UIFormSearchList extends UIList<Form>
{
    public static final int ROW_HEIGHT = 24;

    public UIFormSearchList(Consumer<List<Form>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = ROW_HEIGHT;
    }

    /**
     * Fill this list with every form of every category the user has left enabled.
     *
     * <p>The disabled-category rule is copied from {@link UIFormList#setupForms}: a category
     * the user filtered out of the picker must not come back through this list.</p>
     */
    public void addLibraryForms()
    {
        for (FormCategory category : BBSModClient.getFormCategories().getAllCategories())
        {
            if (BBSSettings.disabledMorphFormCategories.get().contains(category.visible.getId()))
            {
                continue;
            }

            this.add(category.getForms());
        }
    }

    /**
     * The row a keyboard confirm (Enter) should act on: the selected form when it is
     * actually visible under the current filter, otherwise the topmost visible row.
     *
     * <p>The visibility check matters — {@link #filter(String)} clears the selection, but a
     * selection made before a filter change could otherwise point at a row the user can no
     * longer see, and Enter would create something else than what is on screen.</p>
     */
    public Form getHighlighted()
    {
        Form current = this.getCurrentFirst();

        return this.getVisibleRow(current) >= 0 ? current : this.getElementAt(0);
    }

    /**
     * Move the selection by whole rows in VISIBLE (filtered) space and scroll it into view.
     * With nothing selected, both directions land on the first row.
     */
    public void moveHighlight(int delta)
    {
        int count = this.getVisibleCount();

        if (count <= 0)
        {
            return;
        }

        int row = MathUtils.clamp(this.getVisibleRow(this.getCurrentFirst()) + delta, 0, count - 1);
        Form form = this.getElementAt(row);

        if (form != null)
        {
            /* Identity, not equals: the same form object is what getElementAt handed out,
             * and Form doesn't define equals — two library entries can look alike. */
            this.setCurrentDirect(form);
            this.scroll.scrollIntoView(row * this.scroll.scrollItemSize, this.scroll.scrollItemSize);
        }
    }

    /** Number of rows currently on screen-able display, i.e. after filtering. */
    public int getVisibleCount()
    {
        int size = this.scroll.scrollItemSize;

        return size <= 0 ? 0 : this.scroll.scrollSize / size;
    }

    private int getVisibleRow(Form form)
    {
        if (form == null)
        {
            return -1;
        }

        for (int i = 0, c = this.getVisibleCount(); i < c; i++)
        {
            if (this.getElementAt(i) == form)
            {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void filter(String filter)
    {
        super.filter(filter);

        /* A selection from the previous query may not be part of the new result. Dropping it
         * keeps getHighlighted() on the topmost match, which is what typing-then-Enter means. */
        this.current.clear();
        this.scroll.scrollTo(0);
    }

    @Override
    protected String elementToString(UIContext context, int i, Form element)
    {
        /* Both halves are searchable on purpose: the picker matches display name OR form id,
         * and UIList matches this string as a whole, so it has to carry both. */
        return element.getDisplayName() + " " + element.getFormId();
    }

    @Override
    protected void renderElementPart(UIContext context, Form element, int i, int x, int y, boolean hover, boolean selected)
    {
        int offset = 4;

        if (BBSSettings.listModelPreview.get())
        {
            int size = this.scroll.scrollItemSize - 4;
            int px = x + 2;
            int py = y + 2;

            /* The form renders through its own vertex consumers and GL state; anything the
             * batcher still holds has to be on screen first or it inherits that state
             * (same flush the replay menu does before its per-row preview). */
            context.batcher.flush();

            FormUtilsClient.renderUI(element, context, px, py, px + size, py + size);

            offset = size + 6;
        }

        context.batcher.textShadow(
            element.getDisplayName(),
            x + offset,
            y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2,
            hover ? Colors.HIGHLIGHT : Colors.WHITE
        );
    }
}
