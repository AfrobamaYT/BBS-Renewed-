package mchorse.bbs_mod.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.render.gl.Attributes;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public class ModelVAORenderer
{
    public static void render(ShaderProgram shader, ModelVAO modelVAO, MatrixStack stack, int packedLight)
    {
        // Постоянные параметры для каждой вершины
        GL30.glVertexAttrib4f(Attributes.COLOR, 1F, 1F, 1F, 1F);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, OverlayTexture.field_32953, OverlayTexture.field_32955);
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, packedLight & '\uffff', packedLight >> 16 & '\uffff');

        // Необходимо сделать копию текущих параметров, чтобы ничего не сломать при последующей отрисовке
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        RenderSystem.setShader(() -> shader);
        setupUniforms(stack, shader);

        shader.bind();
        modelVAO.render();
        shader.unbind();

        GL30.glBindVertexArray(currentVAO);
    }

    private static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
        for (int i = 0; i < 12; i++)
        {
            shader.addSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shader.projectionMat != null)
        {
            shader.projectionMat.set(RenderSystem.getProjectionMatrix());
        }

        if (shader.modelViewMat != null)
        {
            shader.modelViewMat.set(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix()));
        }

        // TODO: Model view matrix трансформирует только позицию, так что стоит дополнительно добавить NormalMat. При включенных шейдерах Iris уже имеет такую матрицу, так что для обычной отрисовки нужно добавить её в шейдер
        GlUniform normalUniform = shader.getUniform("NormalMat");

        if (normalUniform != null)
        {
            normalUniform.set(stack.peek().getNormalMatrix());
        }

        if (shader.viewRotationMat != null)
        {
            shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
        }

        if (shader.fogStart != null)
        {
            shader.fogStart.set(RenderSystem.getShaderFogStart());
        }

        if (shader.fogEnd != null)
        {
            shader.fogEnd.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.fogColor != null)
        {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }

        if (shader.fogShape != null)
        {
            shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
        }

        if (shader.colorModulator != null)
        {
            shader.colorModulator.set(1F, 1F, 1F, 1F);
        }

        if (shader.gameTime != null)
        {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }

        if (shader.textureMat != null)
        {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }

        RenderSystem.setupShaderLights(shader);
    }
}
