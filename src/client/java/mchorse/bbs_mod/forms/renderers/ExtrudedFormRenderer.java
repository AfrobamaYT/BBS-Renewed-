package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.graphics.ModellOhneSchattierung;
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
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
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
        PoseStack stack = context.batcher.pose();

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

        /* ⛔ 26.2: JEDE Pipeline hier muss BBS' DREI Vertexbindungen tragen (Position an 0,
         * Normale an 1, UV0 an 2). Gezeichnet wird ueber
         * ModelVAORenderer.render(pipeline, ModelVAO, ...), und ModelVAO.zeichnen setzt genau
         * diese drei Puffer. Eine Vanilla-Pipeline erklaert dagegen EINE verschraenkte
         * Bindung 0 (ENTITY bzw. POSITION_TEX_COLOR); mit ihr liest der Shader den reinen
         * Positionspuffer als ganzen Vertex -- kein Fehler, sondern Muell im Bild. Beide
         * Vanilla-Zweige, die bis hierher standen, waren aus diesem Grund falsch, auch wenn
         * sie uebersetzten.
         *
         * Die lokale Variable "format" ist ersatzlos entfallen. Sie war schon vor dem Port
         * ohne Wirkung -- ModelVAORenderer nahm das Format des SHADERS, nie diese Variable --
         * und in 26.2 steht das Format ohnehin in der Pipeline. */
        Supplier<RenderPipeline> normal;

        if (!shading)
        {
            /* Unbeschattet: die BBS-Pipeline mit model_no_shading. Sie tut genau das, was
             * Vanillas position_tex_color hier tat -- Textur mal Formfarbe, ohne
             * Richtungslicht, ohne Lichtkarte, ohne Nebel.
             *
             * ⚠️ Sie steht ABSICHTLICH nicht in ModelVAOPipeline.istModellPipeline: die
             * Zwei-Pass-Durchsichtigkeit hing bis 1.21.1 am Uniform "PassMode", das nur BBS'
             * beschatteter Modell-Shader hatte. position_tex_color hatte es nicht, dieser
             * Fall lief also auch damals schon einpassig. */
            normal = ModellOhneSchattierung::durchsichtig;
        }
        else
        {
            /* Beschattet. Unter Iris stand hier bis 1.21.1 Vanillas Entity-Shader, damit der
             * Shaderpack die Form wie eine Entity beleuchtet. Das kann in 26.2 keine
             * Vanilla-Pipeline mehr leisten (Bindungsproblem oben), und die Iris-Anbindung
             * ist in diesem Port stillgelegt -- BBSRendering.isIrisShadersEnabled() liefert
             * konstant false, der Iris-Zweig ist derzeit unerreichbar.
             *
             * ⚠️ VERHALTENSAENDERUNG, sobald Iris zurueckkommt: die Form wird dann von BBS'
             * eigenem Zwei-Richtungslicht beschattet und NICHT mehr vom Shaderpack. Wer das
             * zurueckwill, braucht eine Iris-gefuetterte Pipeline mit denselben drei
             * Bindungen; eine Vanilla-Pipeline kann es nie sein. */
            normal = BBSShaders::getModel;
        }

        Supplier<RenderPipeline> shader = this.getShader(context,
            normal,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    private void renderModel(Supplier<RenderPipeline> shader, PoseStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
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

            // 26.2: turnOn/OffLightLayer entfaellt -- die Lichtkarte ist kein globaler Zustand mehr, sondern Sampler2 des Zeichenaufrufs (siehe SofortZeichner.entityTextur). Wer hier zeichnet, muss sie binden; ein globaler Schalter erreicht die Pipeline nicht.
            // 26.2: setup/teardownOverlayColor entfaellt -- das Overlay ist kein globaler Zustand mehr, sondern Sampler1 des Zeichenaufrufs (SofortZeichner bindet alle drei).

            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Same law as model forms under Iris: the draw always runs right now, in the phase
             * its program is meant for (the end-of-frame replay lands after a deferred pack's
             * shading composite — Photon never shades it), and texture translucency at full
             * colour degrades to alpha cutout — transparent texels become holes, semi-transparent
             * ones draw solid. */
            boolean cutout = defer && irisWorld && textureObject != null && textureObject.hasTranslucency()
                && color.a >= 1F && !this.form.additiveColor.get();

            /* 26.2: der Cutout-Fall bekommt ModelVAOPipeline.UNDURCHSICHTIG statt Vanillas
             * Entity-Cutout-Shader. Zwei Gruende, beide zwingend:
             *
             *  1. Vanillas Pipeline hat die falschen Vertexbindungen (siehe oben) -- sie
             *     zeichnete hier Muell.
             *  2. UNDURCHSICHTIG ist die blendfreie Variante des Modell-Shaders und ersetzt
             *     damit das disableBlend(), das hier bis 1.21.1 vor dem Zug stand. In 26.2
             *     ist Mischen kein Schalter mehr, sondern Teil der Pipeline -- die Absicht
             *     "mischen aus" hat nur noch hier einen Ort.
             *
             * Die Verwerfungsschwelle bleibt dabei gleich: model.fsh verwirft bei a < 0.1,
             * genau wie der Entity-Cutout-Shader. */
            RenderPipeline finalShader = cutout ? ModelVAOPipeline.UNDURCHSICHTIG : shader.get();
            boolean wasActive = false;

            if (irisWorld)
            {
                wasActive = FormTranslucentQueue.suspend();
            }

            try
            {
                if (defer && FormTranslucentQueue.needsSplit(finalShader, null, textureObject, color.a))
                {
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.last().normal());

                    /* 26.2: die Durchgangsart wurde frueher als Uniform am Shader-Objekt
                     * gesetzt (setPassMode), galt dann fuer alles Folgende und musste danach
                     * zurueckgestellt werden. Jetzt reist sie als PARAMETER dieses einen
                     * Zeichenaufrufs mit -- das Zuruecksetzen entfaellt damit ersatzlos, denn
                     * ein Parameter kann keine fremde Zeichnung einfaerben. */
                    ModelVAORenderer.render(finalShader, data, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, FormTranslucentQueue.PASS_OPAQUE);

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
             * Pipeline. Das disableBlend() des Cutout-Falls ist oben in die Pipeline-Wahl
             * gewandert (UNDURCHSICHTIG) -- dort, wo Mischen in 26.2 seinen Ort hat. */

        }
    }
}
