package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.graphics.Blockmodell;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.UISymbol;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    public static final Color color = new Color();

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    /**
     * ⚠️ 26.2: die Vorschau ist das Inventarsymbol des Blocks, nicht mehr die frei gedrehte
     * 3D-Ansicht -- Grund und Rueckweg stehen in {@link UISymbol}. Farbe und Drehung der Form
     * wirken hier darum nicht; im Film (render3D) wirken beide unveraendert.
     *
     * <p>⛔ WIE PIXELIG ES IST, IST GEMESSEN (2026-08-04, Bugliste #13): die Treppenstufe an
     * der schraegen Wuerfelkante betraegt 4-5 Bildpunkte bei ~140 px Kantenlaenge -- die Quelle
     * hatte also ~30 px, ein 32-px-Atlassymbol, 4,4-fach vergroessert. Nicht die Textur ist
     * grob, das fertige Bild wird aufgeblasen. Die Meldung des Nutzers ist damit bestaetigt.</p>
     *
     * <p>⭐ DER ERSATZ IST GEBAUT, ABER NICHT VERDRAHTET: {@link mchorse.bbs_mod.graphics.Blockvorschau}
     * schreibt die Vierecke selbst in einen Puffer und schickt sie sofort ab -- der Weg, den
     * auch {@code BillboardFormRenderer} geht. Verdrahten heisst hier: statt {@code UISymbol}
     * {@code Blockvorschau.zeichnen(state, stapel, getUIMatrix(...), farbe, FULL_BRIGHT,
     * NO_OVERLAY)} rufen und bei {@code false} auf das Symbol zurueckfallen.</p>
     *
     * <p>⚠️ WARUM NOCH NICHT: es ist nicht BELEGT, dass es sichtbar zeichnet. Der vorherige
     * Anlauf ueber {@code Blockmodell.einreichen} sah genauso plausibel aus und zeichnete NICHTS
     * (22085 Blockpixel mit Symbol, 0 mit Einreihung, wieder 22085 danach). Zum Nachmessen fehlt
     * eine Buehne, in der eine Blockkachel zuverlaessig im Bild liegt -- die Kacheln der
     * Formpalette liegen bei GUI y=240 und damit unter dem Rand, und die Rollung ueber die
     * Bruecke greift nicht. Ein Renderfix ohne Bild ist eine Behauptung; deshalb steht hier
     * weiter das Symbol.</p>
     */
    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        BlockState state = this.form.blockState.get();

        if (state == null)
        {
            return;
        }

        PoseStack stapel = context.batcher.pose();

        stapel.pushPose();

        mchorse.bbs_mod.utils.MatrixStackUtils.multiply(stapel,
            ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2));

        float skalierung = this.form.uiScale.get();

        stapel.scale(skalierung, skalierung, skalierung);
        stapel.translate(-0.5F, -0.5F, -0.5F);

        Color farbe = Color.white();

        farbe.mul(this.form.color.get());

        boolean gezeichnet = mchorse.bbs_mod.graphics.Blockvorschau.zeichnen(state, stapel,
            new Matrix4f(), new org.joml.Vector4f(farbe.r, farbe.g, farbe.b, farbe.a),
            mchorse.bbs_mod.compat.alt.LightCoordsUtil.FULL_BRIGHT,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

        stapel.popPose();

        if (!gezeichnet)
        {
            UISymbol.mittig(context, new ItemStack(state.getBlock()), x1, y1, x2, y2, skalierung);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.pushPose();
        if (context.world != null)
        {
            context.world.pushPose();
        }
        context.stack.translate(-0.5F, 0F, -0.5F);
        if (context.world != null)
        {
            context.world.translate(-0.5F, 0F, -0.5F);
        }

        if (context.isPicking())
        {
            /* ⛔ 26.2: der Hijack ist ersatzlos entfallen. Er setzte beim Zeichnen JEDER Ebene
             * einen anderen Shader -- ein Haken in Vanillas Sofort-Zeichnung, die es nicht
             * mehr gibt. Der Shader steht jetzt in der Pipeline des jeweiligen Zuges; fuer
             * den Auswahl-Durchgang waehlt ihn die Pipeline dort, nicht ein Rueckruf hier. */

            light = 0;
        }

        /* 26.2: der Hijack des Nicht-Picking-Zweigs ist ersatzlos entfallen. Er tat nichts
         * anderes, als je gezeichneter RenderType-Ebene (RenderLayerMixin haengt an
         * RenderType#setupRenderState) den globalen Blend-Schalter anzuschalten. Die
         * Block-Ebenen, die der Vanilla-Blockrenderer unten selbst waehlt, tragen ihr Blending
         * in ihrer eigenen Pipeline (RenderType#pipeline()). clearRunnables() unten bleibt
         * stehen und laeuft nun ins Leere - harmlos. */

        color.set(context.color);
        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        /* Publishing the form's camera-space origin opts its translucent layers into the
         * deferred sorted pass (see CustomVertexConsumerProvider#draw(RenderLayer)); the
         * picking branch above never publishes, so the stencil keeps every pixel. */
        if (!context.isPicking())
        {
            Vector3f origin = context.stack.last().pose().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).transformPosition(origin));
        }

        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        Blockmodell.einreichen(this.form.blockState.get(), context.stack, consumers, light, context.overlay);
        consumers.draw();
        consumers.setSubstitute(null);
        FormTranslucentQueue.setSortOrigin(null);


        context.stack.popPose();
        if (context.world != null)
        {
            context.world.popPose();
        }

        /* 26.2: hier ist nichts mehr zurueckzustellen. Der Tiefentest ist kein globaler Zustand
         * mehr, den der Block-Draw verstellt haben koennte - jeder Draw bekommt ihn aus seiner
         * Pipeline (RenderType#pipeline()). Ersatzlos entfallen. */
    }
}
