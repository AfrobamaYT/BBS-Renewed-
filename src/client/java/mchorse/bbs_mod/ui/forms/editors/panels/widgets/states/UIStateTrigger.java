package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import java.util.Collection;

public class UIStateTrigger extends UIElement
{
    public UIKeybind hotkey;
    public UIStringList actions;
    public UIButton states;

    private final ModelForm modelForm;

    public UIStateTrigger(ModelForm form, StateTrigger trigger, Collection<String> actions)
    {
        this.modelForm = form;

        this.hotkey = new UIKeybind((combo) -> trigger.hotkey = combo.getMainKey());
        this.hotkey.setKeyCombo(new KeyCombo("", IKey.EMPTY, trigger.hotkey));

        this.actions = new UIStringList((l) -> trigger.action = l.get(0));
        this.actions.background().add(actions);
        this.actions.h(16 * 5);
        this.actions.setCurrentScroll(trigger.action);

        this.states = new UIButton(UIKeys.STATE_TRIGGERS_EDIT, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFormStatesOverlayPanel(this.modelForm, trigger), 0.5F, 0.9F);
        });

        this.column().vertical().stretch();
        this.add(this.hotkey, this.actions, this.states);
    }
}