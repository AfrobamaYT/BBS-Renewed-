package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.client.render.ModelVAO;
import mchorse.bbs_mod.client.render.ModelVAORenderer;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;

public class CubicVAORenderer extends CubicCubeRenderer
{
    private ShaderProgram program;
    private CubicModel model;

    public CubicVAORenderer(ShaderProgram program, CubicModel model, int light, int overlay, boolean picking, ShapeKeys shapeKeys)
    {
        super(light, overlay, picking, shapeKeys);

        this.program = program;
        this.model = model;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        ModelVAO modelVAO = this.model.getVaos().get(group);

        if (modelVAO != null)
        {
            ModelVAORenderer.render(this.program, modelVAO, stack, this.light);
        }

        return false;
    }
}