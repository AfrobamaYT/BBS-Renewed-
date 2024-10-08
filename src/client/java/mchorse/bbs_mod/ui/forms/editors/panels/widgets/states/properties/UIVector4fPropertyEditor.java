package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.Vector4fProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import org.joml.Vector4f;

public class UIVector4fPropertyEditor extends UIFormPropertyEditor<Vector4f, Vector4fProperty>
{
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;
    public UITrackpad w;

    public UIVector4fPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, Vector4fProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(Vector4fProperty property)
    {
        this.x = new UITrackpad((v) -> this.setValue(new Vector4f((float) this.x.getValue(), (float) this.y.getValue(), (float) this.z.getValue(), (float) this.w.getValue())));
        this.x.setValue(property.get().x);
        this.y = new UITrackpad((v) -> this.setValue(new Vector4f((float) this.x.getValue(), (float) this.y.getValue(), (float) this.z.getValue(), (float) this.w.getValue())));
        this.y.setValue(property.get().y);
        this.z = new UITrackpad((v) -> this.setValue(new Vector4f((float) this.x.getValue(), (float) this.y.getValue(), (float) this.z.getValue(), (float) this.w.getValue())));
        this.z.setValue(property.get().z);
        this.w = new UITrackpad((v) -> this.setValue(new Vector4f((float) this.x.getValue(), (float) this.y.getValue(), (float) this.z.getValue(), (float) this.w.getValue())));
        this.w.setValue(property.get().w);

        this.add(this.x, this.y, this.z, this.w);
    }
}