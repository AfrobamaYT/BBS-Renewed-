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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /* Meldeweg fuer render()-fehler. Siehe den kommentar im catch von render(): die stelle lief
     * jahrelang als leerer block, und genau deshalb suchte man den fehler danach an einer
     * stelle, die ihn nur ENTDECKT hat. */
    private static final Logger BBS_RENDER_LOG = LoggerFactory.getLogger("bbs/form-render");

    /** Zuletzt gemeldeter fehler je form-klasse — gegen 60 identische zeilen je sekunde. */
    private static final Map<String, String> bbsGemeldet = new HashMap<>();

    /**
     * Meldet einen render-fehler EINMAL je (form, fehler)-paar.
     *
     * <p>Gedrosselt, nicht verschluckt: wiederholungen werden unterdrueckt, ein NEUER fehler
     * kommt immer durch. Ein zaehler steht dabei, damit aus „einmal gemeldet" nicht „einmal
     * passiert" gelesen wird — genau diese verwechslung hat den urspruenglichen fehler
     * so teuer gemacht.
     */
    private static void bbsMeldeRenderFehler(Form form, Throwable fehler)
    {
        String art = form == null ? "null" : form.getClass().getSimpleName();
        String kennung = art + ":" + fehler.getClass().getName() + ":" + fehler.getMessage();
        String vorher = bbsGemeldet.put(art, kennung);

        mchorse.bbs_mod.client.AbsturzHinweise.merken(form, fehler);

        if (kennung.equals(vorher))
        {
            return;
        }

        BBS_RENDER_LOG.error("render({}) hat geworfen — der pose-stack kann jetzt unbalanciert "
            + "sein. Diese meldung erscheint je form-klasse nur beim ERSTEN mal und bei jedem "
            + "WECHSEL des fehlers, nicht je bild.", art, fehler);
    }

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
        mchorse.bbs_mod.ui.forms.editors.UIFormEditor.register(mchorse.bbs_mod.forms.forms.LightForm.class,
            mchorse.bbs_mod.ui.forms.editors.forms.UILightForm::new);
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
            com.mojang.blaze3d.vertex.PoseStack.Pose oben = context.stack == null ? null : context.stack.last();
            currentForm.push(form);

            try
            {
                renderer.render(context);
            }
            catch (Exception fehler)
            {
                /* ⛔ HIER STAND `catch (Exception e) {}` — EIN LEERER BLOCK (2026-08-10, halbe
                 * nacht). Was er verschluckte, kam eine bildaufnahme spaeter als etwas ganz
                 * anderes zurueck:
                 *
                 *     java.lang.IllegalStateException: Pose stack not empty
                 *         at LevelRenderer.checkPoseStack(LevelRenderer.java:631)
                 *
                 * Der weg dahin: renderer.render() wirft NACH seinem pushPose(). Die exception
                 * verschwand hier spurlos, der zugehoerige popPose() wurde nie erreicht, und
                 * vanilla fand einen bildschritt spaeter einen nicht leeren stapel — mit einem
                 * stapelabzug, in dem WEDER bbs NOCH bbstools vorkommt. Gemessen am haltepunkt:
                 * lastIndex=1 (genau ein push offen), und die matrix darin zeigte exakt auf die
                 * position des actor-replays (abstand 0.00, gegenprobe 47.13 zum naechsten).
                 *
                 * `crash-safe-exceptions` protokollierte nichts: dieses catch fing frueher.
                 * Deshalb wird hier NICHT repariert, sondern GEMELDET — wer die ursache sehen
                 * will, muss sie lesen koennen. Gedrosselt, weil der renderpfad je bild laeuft
                 * und eine ungebremste ausgabe das log unbrauchbar macht (gemessen: 50 treffer
                 * in wenigen sekunden). */
                bbsMeldeRenderFehler(form, fehler);
                /* Jetzt auch repariert (2026-10-12, Absturzbericht aus der Community): ein offener
                 * push riss das ganze Spiel mit "Pose stack not empty". PoseStack haelt je Ebene
                 * dasselbe Pose-Objekt, also bis zur Ebene vor dem Aufruf zurueckpoppen. */
                while (oben != null && context.stack.last() != oben && !context.stack.isEmpty()) context.stack.popPose();
            }
            finally
            {
                /* Auch das lief vorher nur, WEIL das catch alles schluckte. Ohne finally haette
                 * die erste durchgereichte exception den formen-stapel dauerhaft verschoben. */
                currentForm.pop();
            }
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
