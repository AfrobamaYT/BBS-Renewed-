package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.vorschau.Miniaturschlange;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Supplier;

/**
 * Das 64x64-Miniaturbild in der Ecke des Modell-Editors.
 *
 * <p>Aus {@code UIModelEditorPanel} herausgeloest (2026-08-11), als der Umriss darin einen
 * Reihenfolge-Fehler bekam — die Datei ist ueber 800 Zeilen und darf nur schrumpfen.</p>
 */
public class UIModellMiniatur extends UIElement
{
    private final Supplier<Form> form;

    public UIModellMiniatur(Supplier<Form> form)
    {
        this.form = form;
    }

    @Override
    public void render(UIContext context)
    {
        int x1 = this.area.x;
        int y1 = this.area.y;
        int x2 = this.area.ex();
        int y2 = this.area.ey();

        context.batcher.box(x1, y1, x2, y2, BBSSettings.deepSurface());
        FormUtilsClient.renderUI(this.form.get(), context, x1, y1, x2, y2);

        /* ⛔ GRENZE, SONST LIEGT DIE VORSCHAU UEBER DEM UMRISS (2026-08-11).
         *
         * Die Vorschau wird nur EINGEREIHT und faehrt am Ende des Durchlaufs mit
         * {@code nextStratum()} ueber das ganze GUI. Umriss und Kinder darunter zeichnen aber
         * genau auf DIESES Rechteck und kaemen damit unter die Vorschau. In 1.21.1 liegt der
         * Umriss oben, weil dort sofort gezeichnet wurde. Details in MiniaturGrenze. */
        Miniaturschlange.abgeben(context, context.menu.width, context.menu.height);

        context.batcher.outline(x1, y1, x2, y2, Colors.setA(Colors.WHITE, 0.2F));

        super.render(context);
    }

    /** Es ist ein Miniaturbild — Klicks schlucken, damit sie nicht den Viewport darunter drehen. */
    @Override
    public boolean subMouseClicked(UIContext context)
    {
        return this.area.isInside(context);
    }
}
