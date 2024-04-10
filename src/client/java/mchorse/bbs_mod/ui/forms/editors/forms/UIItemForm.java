package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.ItemForm;

public class UIItemForm extends UIForm<ItemForm>
{
    public UIItemForm()
    {
        super();

        // this.defaultPanel = new UIBlockFormPanel(this);

        // this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_BLOCK_TITLE, Icons.BLOCK);
        this.registerDefaultPanels();
    }
}