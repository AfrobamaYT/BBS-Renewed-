package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.IntegerProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

public class UIIntegerPropertyEditor extends UIFormPropertyEditor<Integer, IntegerProperty>
{
    public UITrackpad trackpad;

    public UIIntegerPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, IntegerProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(IntegerProperty property)
    {
        this.trackpad = new UITrackpad((v) -> this.setValue(v.intValue()));
        this.trackpad.integer().setValue(property.get());

        this.add(this.trackpad);
    }
}