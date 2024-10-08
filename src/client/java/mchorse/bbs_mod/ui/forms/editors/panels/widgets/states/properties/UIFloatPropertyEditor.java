package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.FloatProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

public class UIFloatPropertyEditor extends UIFormPropertyEditor<Float, FloatProperty>
{
    public UITrackpad trackpad;

    public UIFloatPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, FloatProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(FloatProperty property)
    {
        this.trackpad = new UITrackpad((v) -> this.setValue(v.floatValue()));
        this.trackpad.setValue(property.get());

        this.add(this.trackpad);
    }
}