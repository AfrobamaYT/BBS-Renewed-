package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIVideoFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIVideoForm extends UIForm<VideoForm>
{
    public UIVideoForm()
    {
        super();

        this.defaultPanel = new UIVideoFormPanel(this);

        this.registerPanel(this.defaultPanel, UIVideoFormPanel.TITLE, Icons.VIDEO_CAMERA);
        this.registerDefaultPanels();
    }
}
