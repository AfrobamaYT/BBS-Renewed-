package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;

public class UIExtrudedFormPanel extends UIFormPanel<ExtrudedForm>
{
    public UIButton pick;

    public UIExtrudedFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.editor.editor, this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        this.options.add(this.pick);
    }
}