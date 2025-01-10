package mchorse.bbs_mod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.render.gl.Attributes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

public class ModelRenderer
{
    private static final Model model = new Model(ModelData.createCube());

    public static void render(MatrixStack stack, int packedLight)
    {
        RenderLayer state = RenderLayer.getEntityCutout(TextureManager.MISSING_IDENTIFIER);
        ShaderProgram shader = GameRenderer.getRenderTypeEntityCutoutProgram();
        assert shader != null;

        // Постоянные параметры для каждой вершины
        GL30.glVertexAttrib4f(Attributes.COLOR, 1f, 1f, 1f, 1f);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, OverlayTexture.field_32953, OverlayTexture.field_32955);
        GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, packedLight & '\uffff', packedLight >> 16 & '\uffff');

        // Необходимо сделать копию текущих параметров, чтобы ничего не сломать при последующей отрисовке
        int activeTexture = GlStateManager._getActiveTexture();
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        // Установка стандартных самплеров для шейдера
        int oldLight = GlStateManager.TEXTURES[GlStateManager.activeTexture].boundTexture;
        int oldOverlay = GlStateManager.TEXTURES[GlStateManager.activeTexture].boundTexture;
        int oldColor = GlStateManager.TEXTURES[GlStateManager.activeTexture].boundTexture;

        GlStateManager._activeTexture(GL30.GL_TEXTURE2);
        GlStateManager._bindTexture(BBSModClient.lightTexture);

        GlStateManager._activeTexture(GL33.GL_TEXTURE1);
        MinecraftClient.getInstance().gameRenderer.getOverlayTexture().setupOverlayColor();
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(1));
        MinecraftClient.getInstance().gameRenderer.getOverlayTexture().teardownOverlayColor();
        GlStateManager._activeTexture(GL33.GL_TEXTURE0);

        // Установка параметров смешивания, глубины и т.п.
        state.startDrawing();

        RenderSystem.setShader(() -> shader);
        setupUniforms(stack, shader);

        shader.bind();
        model.render(shader);
        shader.unbind();

        // Восстановление параметров глубины
        state.endDrawing();

        // Восстановление прежних текстур (если этого не делать некоторые шейдеры могут поломаться)
        GlStateManager._activeTexture(GL33.GL_TEXTURE2);
        GlStateManager._bindTexture(oldLight);
        GlStateManager._activeTexture(GL33.GL_TEXTURE1);
        GlStateManager._bindTexture(oldOverlay);
        GlStateManager._activeTexture(GL33.GL_TEXTURE0);
        GlStateManager._bindTexture(oldColor);
        GlStateManager._activeTexture(activeTexture);

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
        GlStateManager._glUseProgram(0);
    }

    private static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
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
