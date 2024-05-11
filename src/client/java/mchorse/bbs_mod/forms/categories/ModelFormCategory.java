package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIModelFormCategory;

public class ModelFormCategory extends FormCategory
{
    public ModelFormCategory()
    {
        super(UIKeys.FORMS_CATEGORIES_MODELS);
    }

    @Override
    public UIFormCategory createUI(UIFormList list)
    {
        return new UIModelFormCategory(this, list);
    }
}