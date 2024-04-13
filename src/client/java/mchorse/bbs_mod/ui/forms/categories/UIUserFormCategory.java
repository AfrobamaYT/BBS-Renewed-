package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIUserFormCategory extends UIFormCategory
{
    public UIUserFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        this.context((menu) ->
        {
            menu.action(Icons.EDIT, UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY, () ->
            {
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_TITLE,
                    UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_DESCRIPTION,
                    (str) ->
                    {
                        this.getCategory().title = IKey.raw(str);
                        BBSModClient.getFormCategories().writeUserCategories();
                    }
                );

                panel.text.setText(this.getCategory().title.get());

                UIOverlay.addOverlay(this.getContext(), panel);
            });

            try
            {
                MapType data = Window.getClipboardMap();
                Form form = FormUtils.fromData(data);

                menu.action(Icons.PASTE, UIKeys.FORMS_CATEGORIES_CONTEXT_PASTE_FORM, () ->
                {
                    this.category.forms.add(form);
                    BBSModClient.getFormCategories().writeUserCategories();
                });
            }
            catch (Exception e)
            {}

            if (this.selected != null)
            {
                menu.action(Icons.REMOVE, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FORM, () ->
                {
                    this.category.forms.remove(this.selected);
                    this.select(null, false);
                    BBSModClient.getFormCategories().writeUserCategories();
                });
            }
        });
    }

    private UserFormCategory getCategory()
    {
        return (UserFormCategory) this.category;
    }
}