package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class UIFormRenderer extends UIModelRenderer
{
    public Form form;

    @Override
    protected void renderUserModel(UIContext context)
    {
        /* Messfuehler fuer Aufgabe 96 (schwarze Vorschau). Die Frage, die von aussen nicht
         * zu beantworten ist: kommt hier ueberhaupt etwas an, und mit welcher Form?
         * Standardmaessig aus, siehe Messpunkt. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("formrenderer.aufruf",
            this.form == null ? "form=null" : "form=" + this.form.getClass().getSimpleName());

        if (this.form == null)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("formrenderer.form-fehlt");

            return;
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.pose(), net.minecraft.util.LightCoordsUtil.pack(15, 15), OverlayTexture.NO_OVERLAY, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        FormUtilsClient.render(this.form, formContext);

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("formrenderer.durch");
    }
}