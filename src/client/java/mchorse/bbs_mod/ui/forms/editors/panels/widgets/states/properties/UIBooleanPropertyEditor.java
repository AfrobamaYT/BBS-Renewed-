package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.BooleanProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

public class UIBooleanPropertyEditor extends UIFormPropertyEditor<Boolean, BooleanProperty>
{
    public UIToggle toggle;

    public UIBooleanPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, BooleanProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(BooleanProperty property)
    {
        this.toggle = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.setValue(b.getValue()));
        this.toggle.setValue(property.get());

        this.add(this.toggle);
    }
}