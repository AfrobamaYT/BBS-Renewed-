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
    /**
     * Modell als Bild im Bild einreihen (an) oder wie bisher sofort zeichnen (aus).
     *
     * <p>Der Sofortzug ist nicht kaputt -- er ist nur zu frueh. Wo nichts darueber gemalt
     * wird (Morphing-Panel), sieht man das Modell auch damit. Im Modell-Editor deckt der
     * Panel-Hintergrund es zu; nur dort bringt der Umweg etwas. Der Schalter bleibt, bis
     * beide Faelle gemessen sind.
     */
    private static final boolean MODELL_UEBER_BILD_IM_BILD = true;

    public Form form;

    /** The model id last framed, so a new model is framed once and the user zooms freely after. */
    private String gerahmt;

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

        if (this.form instanceof mchorse.bbs_mod.forms.forms.ModelForm modell
            && !modell.model.get().equals(this.gerahmt)
            && ModellRahmen.rahmen(this, modell))
        {
            this.gerahmt = modell.model.get();
        }

        /* ⭐ AUFGABE 96: das Modell als Bild im Bild einreihen statt sofort zeichnen.
         *
         * Der Sofortzug funktioniert -- er zeichnete 574 Mal in vier Sekunden --, aber er
         * landet VOR dem gesamten GUI, und UIModelEditorPanel:303 malt danach einen
         * deckenden Kasten darueber. Eingereiht wird der fertige Ausschnitt IM GUI-strom
         * geblittet, also nach dem Hintergrund. Am Bodengitter bewiesen (41ef900aa). */
        if (MODELL_UEBER_BILD_IM_BILD)
        {
            org.joml.Matrix4f pose = new org.joml.Matrix4f(context.batcher.pose().last().pose());
            net.minecraft.client.gui.navigation.ScreenRectangle flaeche =
                new net.minecraft.client.gui.navigation.ScreenRectangle(
                    this.area.x, this.area.y, this.area.w, this.area.h);

            context.batcher.bildImBild(new mchorse.bbs_mod.ui.framework.elements.utils.vorschau.VorschauModellZustand(
                this.form, this.entity, this.camera,
                new org.joml.Matrix4f(this.camera.projection), pose,
                /* Im Standbild 0 statt des echten Uebergangs: sonst interpoliert der
                 * Zeichner zwischen zwei Posen weiter, obwohl der Tick steht, und die
                 * Nullinie einer Messung wird nie 0. */
                mchorse.bbs_mod.graphics.Standbild.uebergang(context.getTransition()),
                this.area.x, this.area.y, this.area.ex(), this.area.ey(),
                flaeche, flaeche));

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("formrenderer.eingereiht");

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