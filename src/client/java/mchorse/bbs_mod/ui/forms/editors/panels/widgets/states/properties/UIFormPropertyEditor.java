package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

public abstract class UIFormPropertyEditor <V, T extends IFormProperty<V>> extends UIElement
{
    protected ModelForm modelForm;
    protected StateTrigger trigger;
    protected String id;
    protected T property;

    public UIFormPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, T property)
    {
        this.modelForm = modelForm;
        this.trigger = trigger;
        this.id = id;
        this.property = property;

        V oldValue = this.property.get();

        if (trigger.states.has(this.id))
        {
            this.property.fromData(trigger.states.get(this.id));
        }

        this.fillData(property);
        this.property.set(oldValue);

        this.column().vertical().stretch().padding(5);
    }

    protected abstract void fillData(T property);

    protected void setValue(V value)
    {
        V oldValue = this.property.get();

        this.property.set(value);
        this.trigger.states.put(this.id, this.property.toData());
        this.property.set(oldValue);
    }
}