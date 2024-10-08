package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.ActionsConfigProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;

public class UIActionsConfigPropertyEditor extends UIFormPropertyEditor<ActionsConfig, ActionsConfigProperty>
{
    public UIActionsConfigEditor configEditor;

    public UIActionsConfigPropertyEditor(ModelForm modelForm, StateTrigger trigger, String id, ActionsConfigProperty property)
    {
        super(modelForm, trigger, id, property);
    }

    @Override
    protected void fillData(ActionsConfigProperty property)
    {
        ActionsConfig newConfig = new ActionsConfig();

        newConfig.copy(property.get());

        this.configEditor = new UIActionsConfigEditor(() -> this.setValue(newConfig));
        this.configEditor.setConfigs(newConfig, this.modelForm);

        this.add(this.configEditor);
    }
}