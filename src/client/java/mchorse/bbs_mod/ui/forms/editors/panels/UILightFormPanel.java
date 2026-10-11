package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UILightFormPanel extends UIFormPanel<LightForm>
{
    public final UIToggle enabled;
    public final UITrackpad level;

    public UILightFormPanel(UIForm editor)
    {
        super(editor);

        this.enabled = new UIToggle(IKey.raw("Enabled"), (toggle) -> this.form.enabled.set(toggle.getValue()));
        this.level = new UITrackpad((value) -> this.form.level.set(value.intValue())).integer().limit(0, 15);
        this.options.add(this.enabled, UI.labelRow(IKey.raw("Light level"), this.level));
    }

    @Override
    public void startEdit(LightForm form)
    {
        super.startEdit(form);

        this.enabled.setValue(form.enabled.get());
        this.level.setValue(form.level.get());
    }
}
