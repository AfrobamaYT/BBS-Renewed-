package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;

import java.util.ArrayList;
import java.util.List;

public class FormCategory implements IMapSerializable
{
    public IKey title;
    public final List<Form> forms = new ArrayList<>();
    public boolean hidden;

    public FormCategory(IKey title)
    {
        this.title = title;
    }

    public boolean canModify(Form form)
    {
        return false;
    }

    public UIFormCategory createUI(UIFormList list)
    {
        return new UIFormCategory(this, list);
    }

    @Override
    public void fromData(MapType data)
    {
        this.hidden = data.getBool("hidden");

        for (BaseType formData : data.getList("forms"))
        {
            this.forms.add(FormUtils.fromData(formData.asMap()));
        }
    }

    @Override
    public void toData(MapType data)
    {
        ListType forms = new ListType();

        data.putBool("hidden", this.hidden);
        data.put("forms", forms);

        for (Form form : this.forms)
        {
            forms.add(FormUtils.toData(form));
        }
    }
}