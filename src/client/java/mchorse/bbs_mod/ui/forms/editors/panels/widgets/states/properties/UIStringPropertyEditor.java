package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.StringProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;

public class UIStringPropertyEditor extends UIFormPropertyEditor<String, StringProperty>
{
    public UITextbox textbox;

    public UIStringPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, StringProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(StringProperty property)
    {
        this.textbox = new UITextbox(1000, this::setValue);
        this.textbox.setText(property.get());

        this.add(this.textbox);
    }
}