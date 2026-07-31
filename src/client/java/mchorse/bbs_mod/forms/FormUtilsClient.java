package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.AnchorFormRenderer;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.BlockFormRenderer;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FramebufferFormRenderer;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.forms.renderers.LabelFormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.ModelBakery;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Stack;

public class FormUtilsClient
{
    private static Map<Class, IFormRendererFactory> map = new HashMap<>();
    private static CustomVertexConsumerProvider customVertexConsumerProvider;
    private static Stack<Form> currentForm = new Stack<>();

    static
    {
        register(BillboardForm.class, BillboardFormRenderer::new);
        register(ExtrudedForm.class, ExtrudedFormRenderer::new);
        register(LabelForm.class, LabelFormRenderer::new);
        register(ModelForm.class, ModelFormRenderer::new);
        register(ParticleForm.class, ParticleFormRenderer::new);
        register(BlockForm.class, BlockFormRenderer::new);
        register(ItemForm.class, ItemFormRenderer::new);
        register(AnchorForm.class, AnchorFormRenderer::new);
        register(MobForm.class, MobFormRenderer::new);
        register(VanillaParticleForm.class, VanillaParticleFormRenderer::new);
        register(TrailForm.class, TrailFormRenderer::new);
        register(FramebufferForm.class, FramebufferFormRenderer::new);
        /* Gehoert untrennbar zum registry-eintrag in BBSMod: mehrere BBS-pfade rufen
         * getRenderer(form).collectMatrices(...) ohne null-check (gizmo-achsen des ausgewaehlten
         * actors) — eine registrierte form ohne renderer ist dort eine NPE. */
        register(mchorse.bbs_mod.forms.forms.LightForm.class, mchorse.bbs_mod.forms.renderers.LightFormRenderer::new);
    }

    /**
     * Forms must render into buffers of their own rather than into Minecraft's shared entity
     * consumers. Form renderers flush the provider themselves and install a {@link
     * CustomVertexConsumerProvider#hijackVertexFormat(java.util.function.Consumer)} hook that
     * overrides GL state (custom texture, picker shader, blending) per drawn layer. On the shared
     * provider that hook also fires for whatever the world or the GUI had buffered but not yet
     * drawn, so the state lands on somebody else's geometry — e.g. a mob form's custom texture
     * ends up on an unrelated layer instead of the mob, since it only applies to the first drawn
     * layer. Own buffers guarantee that everything drawn while the hook is installed belongs to
     * the form being rendered.
     */
    /**
     * BBS' Einreichstelle fuer Formen.
     *
     * <p>⛔ 26.2: hier stand der Aufbau eines eigenen Sofort-Zeichners -- eine Zuordnung
     * von einem Dutzend RenderTypes auf je einen Eckenspeicher, gebaut aus Sheets.*Sheet().
     * Beides gibt es nicht mehr: Sheets fuehrt nur noch Atlas-Namen, und einen geteilten
     * Sofort-Puffer je Ebene kennt 26.2 gar nicht. Der Bildgraph sammelt selbst.
     *
     * <p>Die Einreichstelle haengt sich darum an den Sammler des laufenden Weltdurchgangs
     * (siehe FormSammler). Sie wird bei jedem Bild neu gebaut -- sie haelt nichts mehr,
     * was sich zu behalten lohnte.
     */
    public static CustomVertexConsumerProvider getProvider()
    {
        return new CustomVertexConsumerProvider(FormSammler.pflicht());
    }

    public static <T extends Form> void register(Class<T> clazz, IFormRendererFactory<T> function)
    {
        map.put(clazz, function);
    }

    public static Form getCurrentForm()
    {
        return currentForm.isEmpty() ? null : currentForm.peek();
    }

    public static FormRenderer getRenderer(Form form)
    {
        if (form == null)
        {
            return null;
        }

        if (form.getRenderer() instanceof FormRenderer renderer)
        {
            return renderer;
        }

        IFormRendererFactory factory = map.get(form.getClass());

        if (factory != null)
        {
            FormRenderer formRenderer = factory.create(form);

            form.setRenderer(formRenderer);

            return formRenderer;
        }

        return null;
    }

    public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            /* ⚠️ EIGENER SAMMLER, kein Griff nach dem Weltsammler. Eine Form in einem
             * Bedienelement wird ausserhalb des Weltdurchgangs gezeichnet -- dort gibt es
             * keinen. Ruestung und Gegenstaende gehen aber ueber Vanillas Einreichweg und
             * brauchen einen. Details und Herkunft des Verfahrens: FormSammler. */
            FormSammler.mitEigenemSammler(() -> renderer.renderUI(context, x1, y1, x2, y2));
        }
    }

    public static void render(Form form, FormRenderingContext context)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            currentForm.push(form);

            try
            {
                renderer.render(context);
            }
            catch (Exception e)
            {}

            currentForm.pop();
        }
    }

    public static List<String> getBones(Form form)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            return renderer.getBones();
        }

        return Collections.emptyList();
    }

    public static interface IFormRendererFactory <T extends Form>
    {
        public FormRenderer<T> create(T form);
    }
}