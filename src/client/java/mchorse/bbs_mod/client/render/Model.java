package mchorse.bbs_mod.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.gl.Attributes;
import mchorse.bbs_mod.client.render.gl.TextureMaps;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL30;

public class Model
{
    private int vao;
    private int count;

    private int oldNormalMap;
    private int oldSpecularMap;

    private int colorTexture;
    private int normalTexture;
    private int specularTexture;

    public Model(ModelData data)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        this.upload(data);

        GL30.glBindVertexArray(currentVAO);
    }

    public void upload(ModelData data)
    {
        this.vao = GL30.glGenVertexArrays();

        GL30.glBindVertexArray(this.vao);

        int vertexBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.vertices(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.POSITION, 3, GL30.GL_FLOAT, false, 0, 0);

        int normalBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, normalBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.normals(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.NORMAL, 3, GL30.GL_FLOAT, false, 0, 0);

        int tangentsBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, tangentsBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.tangents(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TANGENTS, 4, GL30.GL_FLOAT, false, 0, 0);

        int texCoordBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        int midTexCoordBuffer = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, midTexCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glVertexAttribPointer(1, 4, GL30.GL_FLOAT, false, 0, 0);

        this.colorTexture = data.colorTexture();
        this.normalTexture = data.normalTexture();
        this.specularTexture = data.specularTexture();
        this.count = data.vertices().length / 3;
    }

    public void render(ShaderProgram shader)
    {
        var hasShaders = isShadersEnabled();

        uploadTextures(shader, hasShaders);

        GL30.glBindVertexArray(vao);

        enableAttributes(hasShaders);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
        disableAttributes(hasShaders);

        restoreTextures(shader, hasShaders);
    }

    private static void disableAttributes(boolean hasShaders)
    {
        GL30.glDisableVertexAttribArray(Attributes.POSITION);
        GL30.glDisableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glDisableVertexAttribArray(Attributes.NORMAL);

        if (hasShaders) GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
        if (hasShaders) GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);
    }

    private static void enableAttributes(boolean hasShaders)
    {
        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);

        if(hasShaders) GL30.glEnableVertexAttribArray(Attributes.TANGENTS);
        if(hasShaders) GL30.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);
    }

    private void uploadTextures(ShaderProgram shader, boolean hasShaders)
    {
        GL30.glActiveTexture(TextureMaps.COLOR_MAP_INDEX);
        RenderSystem.bindTexture(colorTexture);

        if (!hasShaders)
        {
            return;
        }

        int normalLocation = GL30.glGetUniformLocation(shader.getGlRef(), "normals");
        int specularLocation = GL30.glGetUniformLocation(shader.getGlRef(), "specular");

        if (normalLocation != -1)
        {
            GL30.glActiveTexture(TextureMaps.COLOR_MAP_INDEX + GL30.glGetUniformi(shader.getGlRef(), normalLocation));

            this.oldNormalMap = GL30.glGetInteger(GL30.GL_TEXTURE_BINDING_2D);

            GL30.glBindTexture(GL30.GL_TEXTURE_2D, this.normalTexture);
        }

        if (specularLocation != -1)
        {
            GL30.glActiveTexture(TextureMaps.COLOR_MAP_INDEX + GL30.glGetUniformi(shader.getGlRef(), specularLocation));

            this.oldSpecularMap = GL30.glGetInteger(GL30.GL_TEXTURE_BINDING_2D);

            GL30.glBindTexture(GL30.GL_TEXTURE_2D, this.specularTexture);
        }
    }

    private void restoreTextures(ShaderProgram shader, boolean hasShaders)
    {
        if (!hasShaders)
        {
            return;
        }

        int normalLocation = GL30.glGetUniformLocation(shader.getGlRef(), "normals");
        int specularLocation = GL30.glGetUniformLocation(shader.getGlRef(), "specular");

        if (normalLocation != -1)
        {
            GL30.glActiveTexture(TextureMaps.COLOR_MAP_INDEX + GL30.glGetUniformi(shader.getGlRef(), normalLocation));
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, oldNormalMap);
        }

        if (specularLocation != -1)
        {
            GL30.glActiveTexture(TextureMaps.COLOR_MAP_INDEX + GL30.glGetUniformi(shader.getGlRef(), specularLocation));
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, oldSpecularMap);
        }
    }

    public static boolean isShadersEnabled()
    {
        return BBSRendering.isIrisShadersEnabled();
    }
}
