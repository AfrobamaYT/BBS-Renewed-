package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.UISymbol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ItemFormRenderer extends FormRenderer<ItemForm>
{
    public ItemFormRenderer(ItemForm form)
    {
        super(form);
    }

    /** Das Inventarsymbol geht ueber den Flaechenstapel des Zeichners, nicht ueber einen
     *  eigenen Durchgang — also nicht einreihen, siehe {@link FormRenderer#renderUI}. */
    @Override
    protected boolean flacheVorschau()
    {
        return true;
    }

    /**
     * ⚠️ 26.2: die Vorschau ist das Inventarsymbol, nicht mehr die frei gedrehte 3D-Ansicht --
     * Grund und Rueckweg stehen in {@link UISymbol}.
     */
    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        UISymbol.mittig(context, this.form.stack.get(), x1, y1, x2, y2, this.form.uiScale.get());
    }

    /**
     * Loest den Gegenstand in Zeichenauftraege auf und reicht sie ein -- der Ersatz fuer
     * {@code ItemRenderer.renderStatic}.
     *
     * <p>⛔ HIER GEHT DIE FORM-FARBE VERLOREN, und zwar anders als beim Block. Vanillas
     * Gegenstands-Auftrag ({@code ItemFeatureRenderer.Submit}) hat ueberhaupt kein Feld fuer
     * eine Gesamtfarbe -- nur Toenungsschichten, die der Gegenstand selbst mitbringt
     * (Traenkfarbe, gefaerbtes Leder). Beim Block gab es wenigstens ein Farbfeld, das die
     * oeffentliche Methode nicht fuellte; hier gibt es keins. Wer die Farbe zurueckwill, muss
     * die Vierecke je Schicht selbst einreichen, so wie {@link mchorse.bbs_mod.graphics.Blockmodell}
     * es fuer Bloecke tut. Siehe Aufgabe #82.
     *
     * <p>Ein FRISCHER Zustand je Aufruf, kein geteilter: der Auftrag haelt die Vierecksliste
     * des Zustands weiter (nicht kopiert, im Bytecode nachgesehen), bis der Bildgraph zeichnet.
     * Ein wiederverwendeter Zustand wuerde dem schon abgeschickten Auftrag den Gegenstand
     * unter den Fuessen wegtauschen.
     */
    private void einreichen(PoseStack stapel, CustomVertexConsumerProvider consumers, int licht, int overlay)
    {
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(this.form.stack.get(), this.form.modelTransform.get(), licht, overlay, stapel, mchorse.bbs_mod.compat.gpu.SofortSammler.puffer(consumers.sammler()), mc.level, 0);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.pushPose();

        if (context.isPicking())
        {
            /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
             * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
             * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
             * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */

            light = 0;
        }

        /* 26.2: der else-Zweig ist ersatzlos entfallen - gleicher Grund wie in
         * ModelFormRenderer#renderItems: sein Hijack tat nichts anderes, als je gezeichneter
         * RenderType-Ebene den globalen Blend-Schalter anzuschalten. Die Item-Ebenen, die der
         * Vanilla-Item-Renderer unten selbst waehlt, tragen ihr Blending in ihrer eigenen
         * Pipeline (RenderType#pipeline()). clearRunnables() weiter unten bleibt stehen und
         * laeuft im Nicht-Picking-Fall ins Leere - harmlos. */

        BlockFormRenderer.color.set(context.color);
        FormColorBlend.blend(BlockFormRenderer.color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)). */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(BlockFormRenderer.color));
        this.einreichen(context.stack, consumers, light, context.overlay);
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);


        context.stack.popPose();

        /* 26.2: das enableDepthTest() am Methodenende ist ersatzlos entfallen. Es stellte den
         * globalen Tiefentest wieder her, den der Item-Draw verstellt haben koennte - beides
         * gibt es nicht mehr. Jeder folgende Draw bekommt seine Tiefe aus der Pipeline seines
         * RenderType (RenderType#pipeline()). */
    }
}
