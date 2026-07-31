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
     */
    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        BlockState state = this.form.blockState.get();

        if (state != null)
        {
            UISymbol.mittig(context, new ItemStack(state.getBlock()), x1, y1, x2, y2, this.form.uiScale.get());
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

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrixCopy()).transformPosition(origin));
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
