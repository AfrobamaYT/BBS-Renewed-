package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    public ExtrudedFormRenderer(ExtrudedForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        PoseStack stack = context.batcher.getContext().pose();

        stack.pushPose();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        stack.last().normal().getScale(Vectors.EMPTY_3F);
        stack.last().normal().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        /* 26.2: der Tiefentest ist kein Schalter mehr, er steckt in der Pipeline des Draws.
         * Das alte GL_LEQUAL traegt ModelVAOPipeline: DepthStencilState mit
         * GREATER_THAN_OR_EQUAL und Tiefenschreiben. Das ist KEIN Vorzeichenfehler — 26.2
         * rechnet die Tiefe umgekehrt (reversed-Z), naeher heisst groesserer Wert. */
        this.renderModel(BBSShaders::getModel,
            stack,
            OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, Colors.WHITE,
            context.getTransition(), false
        );

        /* Das GL_ALWAYS danach setzte den Schalter fuer die Oberflaeche zurueck. Es entfaellt
         * ersatzlos: der GUI-Raum hat keine Tiefe mehr, die Ueberdeckung entscheidet
         * GuiGraphicsExtractor.nextStratum() (siehe Batcher2D). */

        stack.popPose();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        VertexFormat format = shading ? DefaultVertexFormat.ENTITY : DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR;
        Supplier<ShaderInstance> normal = shading && !BBSRendering.isIrisShadersEnabled()
            ? BBSShaders::getModel
            : (shading ? GameRenderer::getRendertypeEntityTranslucentShader : GameRenderer::getPositionTexColorShader);
        Supplier<ShaderInstance> shader = this.getShader(context,
            normal,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(Supplier<ShaderInstance> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Link texture = this.form.texture.get();
        ModelVAO data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
            if (this.form.billboard.get())
            {
                MatrixStackUtils.billboard(matrices);
            }

            Color color = Colors.COLOR.set(overlayColor, true);
            GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor, this.form.additiveColor.get());

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(textureObject);

            /* 26.2: Blend ist kein Schalter mehr. Gezeichnet wird ueber ModelVAOPipeline, deren
             * durchsichtige Variante BlendFunction.TRANSLUCENT traegt — genau die Mischung, die
             * defaultBlendFunc() einstellte. Beide Zeilen entfallen darum ersatzlos. */

            gameRenderer.lightTexture().turnOnLightLayer();
            gameRenderer.overlayTexture().setupOverlayColor();

            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Same law as model forms under Iris: the draw always runs right now, in the phase
             * its program is meant for (the end-of-frame replay lands after a deferred pack's
             * shading composite — Photon never shades it), and texture translucency at full
             * colour degrades to alpha cutout — transparent texels become holes, semi-transparent
             * ones draw solid. */
            boolean cutout = defer && irisWorld && textureObject != null && textureObject.hasTranslucency()
                && color.a >= 1F && !this.form.additiveColor.get();

            ShaderInstance finalShader = cutout ? GameRenderer.getRendertypeEntityCutoutShader() : shader.get();
            boolean wasActive = false;

            if (irisWorld)
            {
                wasActive = FormTranslucentQueue.suspend();
            }

            if (cutout)
            {
                RenderSystem.disableBlend();
            }

            try
            {
                if (defer && FormTranslucentQueue.needsSplit(finalShader, null, textureObject, color.a))
                {
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.last().normal());

                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_OPAQUE);
                    ModelVAORenderer.render(finalShader, data, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay);
                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_SINGLE);

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else if (defer && FormTranslucentQueue.needsWholeDefer(finalShader, null, color.a))
                {
                    /* A uniform colour fade defers the whole draw into the sorted end-of-frame
                     * pass with depth kept on, so the faded form still self-occludes. */
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.last().normal());

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, () -> finalShader, FormTranslucentQueue.PASS_SINGLE, true, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else
                {
                    ModelVAORenderer.render(finalShader, data, matrices, color.r, color.g, color.b, color.a, light, overlay);
                }
            }
            finally
            {
                if (irisWorld)
                {
                    FormTranslucentQueue.restore(wasActive);
                }
            }

            /* Das abschliessende disableBlend() entfaellt ersatzlos: es schloss nur den globalen
             * Schalter fuer die folgenden Draws, und die holen ihr Blending aus ihrer eigenen
             * Pipeline. Das disableBlend() des Cutout-Falls oben bleibt dagegen offen — es will
             * die blendfreie ModelVAOPipeline.UNDURCHSICHTIG, und die gehoert an die
             * Pipeline-Wahl des Draws, nicht an eine Zustandszeile hier. */

            gameRenderer.lightTexture().turnOffLightLayer();
            gameRenderer.overlayTexture().teardownOverlayColor();
        }
    }
}
