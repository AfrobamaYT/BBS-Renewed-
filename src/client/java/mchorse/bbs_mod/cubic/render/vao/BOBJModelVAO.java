package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public class BOBJModelVAO implements IModelVAO
{
    public BOBJLoader.CompiledData data;
    public BOBJArmature armature;

    private int vao;
    private int count;

    /* GL buffers */
    public int vertexBuffer;
    public int normalBuffer;
    public int texCoordBuffer;

    private float[] tmpVertices;
    private float[] tmpNormals;

    public BOBJModelVAO(BOBJLoader.CompiledData data)
    {
        this.data = data;
        this.armature = this.data.mesh.armature;

        this.initBuffers();
    }

    /**
     * Initiate buffers. This method is responsible for allocating 
     * buffers for the data to be passed to VBOs and also generating the 
     * VBOs themselves. 
     */
    private void initBuffers()
    {
        this.vao = GL30.glGenVertexArrays();

        GL30.glBindVertexArray(this.vao);

        this.vertexBuffer = GL30.glGenBuffers();
        this.normalBuffer = GL30.glGenBuffers();
        this.texCoordBuffer = GL30.glGenBuffers();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.vertexBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.posData, GL30.GL_DYNAMIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.POSITION, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.normalBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.normData, GL30.GL_DYNAMIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.NORMAL, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, this.texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, this.data.texData, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        this.count = this.data.normData.length / 3;
        this.tmpVertices = new float[this.data.posData.length];
        this.tmpNormals = new float[this.data.normData.length];
    }

    /**
     * Clean up resources which were used by this  
     */
    public void delete()
    {
        GL30.glDeleteVertexArrays(this.vao);

        GL15.glDeleteBuffers(this.vertexBuffer);
        GL15.glDeleteBuffers(this.normalBuffer);
        GL15.glDeleteBuffers(this.texCoordBuffer);
    }

    /**
     * Update this mesh. This method is responsible for applying 
     * matrix transformations to vertices and normals according to its 
     * bone owners and these bone influences.
     */
    public void updateMesh()
    {
        Vector4f sum = new Vector4f();
        Vector4f result = new Vector4f(0F, 0F, 0F, 0F);
        Vector3f sumNormal = new Vector3f();
        Vector3f resultNormal = new Vector3f();

        float[] oldVertices = this.data.posData;
        float[] newVertices = this.tmpVertices;
        float[] oldNormals = this.data.normData;
        float[] newNormals = this.tmpNormals;

        Matrix4f[] matrices = this.armature.matrices;

        for (int i = 0, c = this.count; i < c; i++)
        {
            int count = 0;

            for (int w = 0; w < 4; w++)
            {
                float weight = this.data.weightData[i * 4 + w];

                if (weight > 0)
                {
                    int index = this.data.boneIndexData[i * 4 + w];

                    sum.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                    matrices[index].transform(sum);
                    result.add(sum.mul(weight));

                    sumNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
                    Matrices.TEMP_3F.set(matrices[index]).transform(sumNormal);
                    resultNormal.add(sumNormal.mul(weight));

                    count++;
                }
            }

            if (count == 0)
            {
                result.set(oldVertices[i * 3], oldVertices[i * 3 + 1], oldVertices[i * 3 + 2], 1F);
                resultNormal.set(oldNormals[i * 3], oldNormals[i * 3 + 1], oldNormals[i * 3 + 2]);
            }

            result.x /= result.w;
            result.y /= result.w;
            result.z /= result.w;

            newVertices[i * 3] = result.x;
            newVertices[i * 3 + 1] = result.y;
            newVertices[i * 3 + 2] = result.z;

            newNormals[i * 3] = resultNormal.x;
            newNormals[i * 3 + 1] = resultNormal.y;
            newNormals[i * 3 + 2] = resultNormal.z;

            result.set(0F, 0F, 0F, 0F);
            resultNormal.set(0F, 0F, 0F);
        }

        this.processData(newVertices, newNormals);

        this.updateVertices(newVertices);
        this.updateNormals(newNormals);
    }

    protected void processData(float[] newVertices, float[] newNormals)
    {}

    /**
     * Update mesh with given data 
     */
    public void updateVertices(float[] data)
    {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Update mesh with given data 
     */
    public void updateNormals(float[] data)
    {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.normalBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
    }

    @Override
    public void render()
    {
        boolean hasShaders = isShadersEnabled();

        GL30.glBindVertexArray(this.vao);

        enableAttributes(hasShaders);
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
        disableAttributes(hasShaders);
    }

    private static void disableAttributes(boolean hasShaders)
    {
        GL30.glDisableVertexAttribArray(Attributes.POSITION);
        GL30.glDisableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glDisableVertexAttribArray(Attributes.NORMAL);
    }

    private static void enableAttributes(boolean hasShaders)
    {
        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);
    }

    public static boolean isShadersEnabled()
    {
        return BBSRendering.isIrisShadersEnabled();
    }
}