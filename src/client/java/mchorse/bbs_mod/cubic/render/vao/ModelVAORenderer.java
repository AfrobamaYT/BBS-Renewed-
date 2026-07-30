package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.graphics.InverseView;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public class ModelVAORenderer
{
    /**
     * The full model-view a draw issued right now would use. Deferred translucent commands
     * capture it at enqueue time, because the global model-view is different by the time
     * the queue flushes at the end of the frame.
     */
    public static Matrix4f captureModelView(PoseStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose());
    }

    public static void render(ShaderInstance shader, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        render(shader, modelVAO, captureModelView(stack), stack.last().normal(), r, g, b, a, light, overlay);
    }

    public static void render(ShaderInstance shader, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        setupUniforms(shader, modelView, normalMat);

        shader.apply();
        modelVAO.render(shader.getVertexFormat(), r, g, b, a, light, overlay);
        shader.clear();

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
    }

    public static void setupUniforms(PoseStack stack, ShaderInstance shader)
    {
        setupUniforms(shader, captureModelView(stack), stack.last().normal());
    }

    public static void setupUniforms(ShaderInstance shader, Matrix4f modelView, Matrix3f normalMat)
    {
        for (int i = 0; i < 12; i++)
        {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }

        if (shader.PROJECTION_MATRIX != null)
        {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }

        if (shader.MODEL_VIEW_MATRIX != null)
        {
            shader.MODEL_VIEW_MATRIX.set(modelView);
        }

        /* NormalMat is present by default in Iris' shaders, but when there is no Iris,
         * the BBS mod's model.json shader is being used instead that provides NormalMat
         * uniform.
         */
        Uniform normalUniform = shader.getUniform("NormalMat");

        if (normalUniform != null)
        {
            normalUniform.set(normalMat);
        }

        Uniform viewRotationUniform = shader.getUniform("ViewRotationMat");

        if (viewRotationUniform != null)
        {
            viewRotationUniform.set(InverseView.get());
        }

        if (shader.FOG_START != null)
        {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (shader.FOG_END != null)
        {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.FOG_COLOR != null)
        {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (shader.FOG_SHAPE != null)
        {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getId());
        }

        if (shader.COLOR_MODULATOR != null)
        {
            shader.COLOR_MODULATOR.setMat2x2(1F, 1F, 1F, 1F);
        }

        if (shader.GAME_TIME != null)
        {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (shader.TEXTURE_MATRIX != null)
        {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        RenderSystem.setupShaderLights(shader);
    }
}
