package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIUserFormCategory;

public class UserFormCategory extends FormCategory
{
    public UserFormCategory(IKey title)
    {
        super(title);
    }

    @Override
    public boolean canModify(Form form)
    {
        return true;
    }

    @Override
    public UIFormCategory createUI(UIFormList list)
    {
        return new UIUserFormCategory(this, list);
    }
}