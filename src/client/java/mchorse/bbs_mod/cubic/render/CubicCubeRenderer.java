package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.obj.ShapeKey;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class CubicCubeRenderer implements ICubicRenderer
{
    private final static Vector3f v1 = new Vector3f();
    private final static Vector3f v2 = new Vector3f();
    private final static Vector3f v3 = new Vector3f();

    private final static Vector3f n1 = new Vector3f();
    private final static Vector3f n2 = new Vector3f();
    private final static Vector3f n3 = new Vector3f();

    private final static Vector2f u1 = new Vector2f();
    private final static Vector2f u2 = new Vector2f();
    private final static Vector2f u3 = new Vector2f();

    private static Matrix4f modelM = new Matrix4f();
    private static Matrix3f normalM = new Matrix3f();

    protected float r = 1;
    protected float g = 1;
    protected float b = 1;
    protected float a = 1;
    protected int light;
    protected int overlay;

    /* Temporary variables to avoid allocating and GC vectors */
    protected Vector3f normal = new Vector3f();
    protected Vector4f vertex = new Vector4f();

    private ModelVertex modelVertex = new ModelVertex();
    private boolean picking;

    public CubicCubeRenderer(int light, int overlay, boolean picking)
    {
        this.light = light;
        this.overlay = overlay;
        this.picking = picking;
    }

    public static void moveToPivot(MatrixStack stack, Vector3f pivot)
    {
        stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
    }

    public static void rotate(MatrixStack stack, Vector3f rotation)
    {
        if (rotation.x == 0 && rotation.y == 0 && rotation.z == 0)
        {
            return;
        }

        Matrix4f matrix4f = new Matrix4f();
        Matrix3f matrix3f = new Matrix3f();

        modelM.identity();
        matrix4f.identity().rotateZ(MathUtils.toRad(rotation.z));
        modelM.mul(matrix4f);

        matrix4f.identity().rotateY(MathUtils.toRad(rotation.y));
        modelM.mul(matrix4f);

        matrix4f.identity().rotateX(MathUtils.toRad(rotation.x));
        modelM.mul(matrix4f);

        normalM.identity();
        matrix3f.identity().rotateZ(MathUtils.toRad(rotation.z));
        normalM.mul(matrix3f);

        matrix3f.identity().rotateY(MathUtils.toRad(rotation.y));
        normalM.mul(matrix3f);

        matrix3f.identity().rotateX(MathUtils.toRad(rotation.x));
        normalM.mul(matrix3f);

        stack.peek().getPositionMatrix().mul(modelM);
        stack.peek().getNormalMatrix().mul(normalM);
    }

    public static void moveBackFromPivot(MatrixStack stack, Vector3f pivot)
    {
        stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    public void setColor(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        for (ModelCube cube : group.cubes)
        {
            this.renderCube(builder, stack, group, cube);
        }

        for (ModelMesh mesh : group.meshes)
        {
            this.renderMesh(builder, stack, model, group, mesh);
        }

        return false;
    }

    private void renderCube(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelCube cube)
    {
        stack.push();
        moveToPivot(stack, cube.pivot);
        rotate(stack, cube.rotate);
        moveBackFromPivot(stack, cube.pivot);

        for (ModelQuad quad : cube.quads)
        {
            this.normal.set(quad.normal.x, quad.normal.y, quad.normal.z);
            stack.peek().getNormalMatrix().transform(this.normal);

            if (quad.vertices.size() == 4)
            {
                this.writeVertex(builder, stack, group, quad.vertices.get(0), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(1), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(2), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(0), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(2), this.normal);
                this.writeVertex(builder, stack, group, quad.vertices.get(3), this.normal);
            }
        }

        stack.pop();
    }

    private void renderMesh(BufferBuilder builder, MatrixStack stack, Model model, ModelGroup group, ModelMesh mesh)
    {
        stack.push();
        moveToPivot(stack, mesh.origin);
        rotate(stack, mesh.rotate);
        moveBackFromPivot(stack, mesh.origin);

        List<ShapeKey> keys = new ArrayList<>();
        ShapeKey key = new ShapeKey();

        keys.add(key);

        key.name = "cool";
        key.value = 1F;

        for (int i = 0, c = mesh.baseData.vertices.size() / 3; i < c; i++)
        {
            v1.set(mesh.baseData.vertices.get(i * 3));
            v2.set(mesh.baseData.vertices.get(i * 3 + 1));
            v3.set(mesh.baseData.vertices.get(i * 3 + 2));

            n1.set(mesh.baseData.normals.get(i * 3));
            n2.set(mesh.baseData.normals.get(i * 3 + 1));
            n3.set(mesh.baseData.normals.get(i * 3 + 2));

            u1.set(mesh.baseData.uvs.get(i * 3));
            u2.set(mesh.baseData.uvs.get(i * 3 + 1));
            u3.set(mesh.baseData.uvs.get(i * 3 + 2));

            for (ShapeKey shapeKey : keys)
            {
                ModelData data = mesh.data.get(shapeKey.name);

                if (data != null)
                {
                    v1.lerp(data.vertices.get(i * 3), shapeKey.value);
                    v2.lerp(data.vertices.get(i * 3 + 1), shapeKey.value);
                    v3.lerp(data.vertices.get(i * 3 + 2), shapeKey.value);

                    n1.lerp(data.normals.get(i * 3), shapeKey.value);
                    n2.lerp(data.normals.get(i * 3 + 1), shapeKey.value);
                    n3.lerp(data.normals.get(i * 3 + 2), shapeKey.value);

                    u1.lerp(data.uvs.get(i * 3), shapeKey.value);
                    u2.lerp(data.uvs.get(i * 3 + 1), shapeKey.value);
                    u3.lerp(data.uvs.get(i * 3 + 2), shapeKey.value);
                }
            }

            /* Write vertices */
            this.normal.set(n1.x, n1.y, n1.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v1, u1, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);

            this.normal.set(n2.x, n2.y, n2.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v2, u2, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);

            this.normal.set(n3.x, n3.y, n3.z);
            stack.peek().getNormalMatrix().transform(this.normal);
            this.modelVertex.set(v3, u3, model);
            this.writeVertex(builder, stack, group, this.modelVertex, this.normal);
        }

        stack.pop();
    }

    protected void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelVertex vertex, Vector3f normal)
    {
        this.vertex.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1);
        stack.peek().getPositionMatrix().transform(this.vertex);

        builder.vertex(this.vertex.x, this.vertex.y, this.vertex.z)
            .color(this.r * group.color.r, this.g * group.color.g, this.b * group.color.b, this.a * group.color.a)
            .texture(vertex.uv.x, vertex.uv.y)
            .overlay(this.overlay);

        if (this.picking)
        {
            builder.light(group.index, 0);
        }
        else
        {
            int u = (int) Lerps.lerp(this.light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int v = this.light >> 16 & '\uffff';

            builder.light(u, v);
        }

        builder.normal(normal.x, normal.y, normal.z).next();
    }
}