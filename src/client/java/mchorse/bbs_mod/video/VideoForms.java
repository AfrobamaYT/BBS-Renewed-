package mchorse.bbs_mod.video;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.renderers.VideoFormRenderer;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.forms.UIVideoForm;

/** Meldet Renderer und Editor der Video-Form an (aus dem statischen Block von FormUtilsClient). */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class VideoForms
{
    private VideoForms()
    {}

    public static void register()
    {
        /* VideoFormRenderer ist ueber Billboard als FormRenderer<BillboardForm> getypt. */
        FormUtilsClient.register(VideoForm.class, (FormUtilsClient.IFormRendererFactory) (form) -> new VideoFormRenderer((VideoForm) form));
        UIFormEditor.register(VideoForm.class, UIVideoForm::new);
    }
}
