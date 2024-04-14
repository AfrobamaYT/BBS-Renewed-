package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.math.MathUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelFormRenderer extends FormRenderer<ModelForm> implements ITickable
{
    private static Matrix4f uiMatrix = new Matrix4f();

    private Map<String, Matrix4f> bones = new HashMap<>();

    private Animator animator;
    private long lastCheck;

    private IEntity entity = new StubEntity();

    public static Matrix4f getUIMatrix(UIContext context, int x1, int y1, int x2, int y2)
    {
        float scale = (y2 - y1) / 2.5F;
        int x = x1 + (x2 - x1) / 2;
        float y = y1 + (y2 - y1) * 0.85F;

        uiMatrix.identity();
        uiMatrix.translate(x, y, 40);
        uiMatrix.scale(scale, -scale, scale);
        uiMatrix.rotateX(MathUtils.PI / 8);
        uiMatrix.rotateY(MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI);

        return uiMatrix;
    }

    public static CubicModel getModel(ModelForm form)
    {
        return BBSModClient.getModels().getModel(form.model.get());
    }

    public ModelFormRenderer(ModelForm form)
    {
        super(form);
    }

    public Animator getAnimator()
    {
        return this.animator;
    }

    public CubicModel getModel()
    {
        return getModel(this.form);
    }

    public Pose getPose(float transition)
    {
        return this.form.pose.get(transition);
    }

    public void resetAnimator()
    {
        this.animator = null;
        this.lastCheck = 0;
    }

    public void ensureAnimator()
    {
        CubicModel model = this.getModel();

        if (model == null || this.lastCheck >= model.loadTime)
        {
            return;
        }

        this.animator = new Animator();
        this.animator.setup(model, this.form.actions.get());

        this.lastCheck = model.loadTime;
    }

    @Override
    public List<String> getBones()
    {
        return new ArrayList<>(this.getModel().model.getAllGroupKeys());
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        this.ensureAnimator();

        CubicModel model = this.getModel();

        if (this.animator != null && model != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = getUIMatrix(context, x1, y1, x2, y2);

            uiMatrix.mul(this.form.transform.get(context.getTransition()).createMatrix());

            Link link = this.form.texture.get(context.getTransition());
            Link texture = link == null ? model.texture : link;
            Color color = this.form.color.get(context.getTransition());

            CubicModelAnimator.resetPose(model.model);

            this.animator.applyActions(null, model.model, context.getTransition());
            model.model.apply(this.getPose(context.getTransition()));

            MatrixStackUtils.multiply(stack, uiMatrix);

            RenderSystem.setShaderTexture(0, BBSModClient.getTextures().getTexture(texture).id);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentCullProgram);

            this.renderModel(stack, model.model, LightmapTextureManager.pack(15, 15), color, true, false);

            /* Render body parts */
            this.captureMatrices(model);
            this.renderBodyParts(FormRenderingContext.set(this.entity, stack, 0, context.getTransition()));

            RenderSystem.depthFunc(GL11.GL_ALWAYS);

            stack.pop();
        }
    }

    private void renderModel(MatrixStack stack, Model model, int light, Color color, boolean ui, boolean picking)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        MatrixStack newStack = new MatrixStack();
        CubicCubeRenderer renderProcessor = new CubicCubeRenderer(light, picking);

        renderProcessor.setColor(color.r, color.g, color.b, color.a);

        newStack.push();

        MatrixStackUtils.multiply(newStack, stack.peek().getPositionMatrix());

        if (ui)
        {
            newStack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            newStack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);
        }

        CubicRenderer.processRenderModel(renderProcessor, builder, newStack, model);
        newStack.pop();

        BufferRenderer.drawWithGlobalProgram(builder.end());

        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureAnimator();

        CubicModel model = this.getModel();

        if (this.animator != null && model != null)
        {
            Link link = this.form.texture.get(context.getTransition());
            Link texture = link == null ? model.texture : link;
            Color color = this.form.color.get(context.getTransition());

            CubicModelAnimator.resetPose(model.model);

            this.animator.applyActions(context.entity, model.model, context.getTransition());
            model.model.apply(this.getPose(context.getTransition()));

            context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));

            RenderSystem.setShaderTexture(0, BBSModClient.getTextures().getTexture(texture).id);
            RenderSystem.setShader(this.getShader(context,
                GameRenderer::getRenderTypeEntityTranslucentCullProgram,
                BBSShaders::getPickerModelsProgram));

            this.renderModel(context.stack, model.model, context.light, color, false, context.isPicking());

            this.captureMatrices(model);
        }
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        CubicModel model = this.getModel();

        if (model == null || model.model == null)
        {
            return;
        }

        for (ModelGroup group : model.model.getOrderedGroups())
        {
            context.stencilMap.addPicking(this.form, group.id);
        }
    }

    private void captureMatrices(CubicModel model)
    {
        MatrixStack stack = new MatrixStack();
        CubicMatrixRenderer renderer = new CubicMatrixRenderer(model.model);

        CubicRenderer.processRenderModel(renderer, null, stack, model.model);

        List<Matrix4f> matrices = renderer.matrices;

        for (ModelGroup group : model.model.getAllGroups())
        {
            Matrix4f matrix = new Matrix4f(matrices.get(group.index));

            matrix.translate(
                group.initial.translate.x / 16,
                group.initial.translate.y / 16,
                group.initial.translate.z / 16
            );
            matrix.rotateY(MathUtils.PI);
            this.bones.put(group.id, matrix);
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAll())
        {
            Matrix4f matrix = this.bones.get(part.bone);

            context.stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(context.stack, matrix);
            }
            else
            {
                context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            renderBodyPart(part, context);

            context.stack.pop();
        }

        this.bones.clear();
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, Map<String, Matrix4f> matrices, String prefix, float transition)
    {
        stack.push();
        MatrixStackUtils.multiply(stack, this.form.transform.get(transition).createMatrix());

        matrices.put(prefix, new Matrix4f(stack.peek().getPositionMatrix()));

        /* Collect bones and add them to matrix list */
        CubicModel model = this.getModel();

        if (this.animator != null && model != null)
        {
            CubicModelAnimator.resetPose(model.model);

            this.animator.applyActions(entity, model.model, transition);
            model.model.apply(this.getPose(transition));

            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            this.captureMatrices(model);
        }

        for (Map.Entry<String, Matrix4f> entry : this.bones.entrySet())
        {
            stack.push();
            MatrixStackUtils.multiply(stack, entry.getValue());
            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), new Matrix4f(stack.peek().getPositionMatrix()));
            stack.pop();
        }

        int i = 0;

        /* Recursively do the same thing with body parts */
        for (BodyPart part : this.form.parts.getAll())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f matrix = this.bones.get(part.bone);

                stack.push();

                if (matrix != null)
                {
                    MatrixStackUtils.multiply(stack, matrix);
                }
                else
                {
                    stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }

                MatrixStackUtils.multiply(stack, part.getTransform().createMatrix());

                FormRenderer formRenderer = FormUtilsClient.getRenderer(form);

                if (formRenderer != null)
                {
                    formRenderer.collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);
                }

                stack.pop();
            }

            i += 1;
        }

        stack.pop();

        this.bones.clear();
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureAnimator();

        if (this.animator != null)
        {
            this.animator.update(entity);
        }
    }
}