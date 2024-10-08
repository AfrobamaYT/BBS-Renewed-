package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.ColorProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.utils.colors.Color;

public class UIColorPropertyEditor extends UIFormPropertyEditor<Color, ColorProperty>
{
    public UIColor color;

    public UIColorPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, ColorProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(ColorProperty property)
    {
        this.color = new UIColor((c) ->
        {
            Color color = new Color();

            color.set(c);
            this.setValue(color);
        });
        this.color.withAlpha().setColor(property.get().getARGBColor());

        this.add(this.color);
    }
}