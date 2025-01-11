package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.client.render.ModelVAO;
import mchorse.bbs_mod.client.render.ModelVAORenderer;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
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
            float r = this.r * group.color.r;
            float g = this.g * group.color.g;
            float b = this.b * group.color.b;
            float a = this.a * group.color.a;
            int light = this.light;

            if (this.picking)
            {
                light = group.index;
            }
            else
            {
                int u = (int) Lerps.lerp(light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
                int v = light >> 16 & '\uffff';

                light = u | v << 16;
            }

            ModelVAORenderer.render(this.program, modelVAO, stack, r, g, b, a, light, this.overlay);
        }

        return false;
    }
}