package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.LinkProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;

public class UILinkPropertyEditor extends UIFormPropertyEditor<Link, LinkProperty>
{
    public UIButton pickTexture;

    public UILinkPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, LinkProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(LinkProperty property)
    {
        this.pickTexture = new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (b) ->
        {
            // TODO: Fix overlay panel being too small
            // TODO: Fix property
            UITexturePicker.open(this.getContext(), property.get(), this::setValue);
        });

        this.add(this.pickTexture);
    }
}