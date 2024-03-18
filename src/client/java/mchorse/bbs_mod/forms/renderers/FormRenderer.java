package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class FormRenderer <T extends Form>
{
    private static final IEntity DUMMY = new StubEntity();

    protected T form;

    public static void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        IEntity oldEntity = context.entity;

        context.entity = part.useTarget ? oldEntity : DUMMY;

        if (part.getForm() != null && part.enabled)
        {
            context.stack.push();
            MatrixStackUtils.applyTransform(context.stack, part.getTransform());

            FormUtilsClient.render(part.getForm(), context);

            context.stack.pop();
        }

        context.entity = oldEntity;
    }

    public FormRenderer(T form)
    {
        this.form = form;
    }

    public T getForm()
    {
        return this.form;
    }

    public List<String> getBones()
    {
        return Collections.emptyList();
    }

    public abstract void renderUI(UIContext context, int x1, int y1, int x2, int y2);

    public final void render(FormRenderingContext context)
    {
        boolean isPicking = false; // TODO: context instanceof UIRenderingContext && ((UIRenderingContext) context).getStencil().picking;

        context.stack.push();
        MatrixStackUtils.multiply(context.stack, this.form.transform.get(context.getTransition()).createMatrix());

        if (isPicking)
        {
            // TODO: this.setupUniform((UIRenderingContext) context);
        }

        this.render3D(context);

        if (isPicking)
        {
            // TODO: this.handlePicking((UIRenderingContext) context);
        }

        this.renderBodyParts(context);

        context.stack.pop();
    }

    /* TODO: protected void setupUniform(FormRenderingContext context)
    {
        for (Shader shader : context.getShaders().getAll())
        {
            Uniform pickerIndex = shader.getUniform("u_picker_index");

            if (pickerIndex instanceof UniformInt)
            {
                ((UniformInt) pickerIndex).set(context.getStencil().objectIndex);
            }
        }
    }

    protected void handlePicking(UIRenderingContext context)
    {
        context.getStencil().addPicking(this.form);
    } */

    protected void render3D(FormRenderingContext context)
    {}

    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAll())
        {
            renderBodyPart(part, context);
        }
    }

    public void collectMatrices(IEntity entity, MatrixStack stack, Map<String, Matrix4f> matrices, String prefix, float transition)
    {
        stack.push();
        MatrixStackUtils.multiply(stack, this.form.transform.get(transition).createMatrix());

        matrices.put(prefix, new Matrix4f(stack.peek().getPositionMatrix()));

        int i = 0;

        for (BodyPart part : this.form.parts.getAll())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.push();
                MatrixStackUtils.multiply(stack, part.getTransform().createMatrix());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }
}