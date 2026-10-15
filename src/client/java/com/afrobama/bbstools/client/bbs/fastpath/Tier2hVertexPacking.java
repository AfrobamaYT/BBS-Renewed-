package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.client.bbs.mesh.SkinnedMeshIR;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/** Shared byte contracts; CPU animation remains unchanged, skinning runs in the vertex shader. */
final class Tier2hVertexPacking
{
    private Tier2hVertexPacking()
    {}

    static void mesh(ByteBuffer data, SkinnedMeshIR ir)
    {
        for (int v = 0; v < ir.vertCount; v++)
        {
            int p = v * 3, uv = v * 2;
            data.putFloat(ir.positions[p]).putFloat(ir.positions[p + 1]).putFloat(ir.positions[p + 2]);
            data.putFloat(ir.uvs[uv]).putFloat(ir.uvs[uv + 1]);
            data.putFloat(ir.normals[p]).putFloat(ir.normals[p + 1]).putFloat(ir.normals[p + 2]);
            int bone = Math.max(0, ir.boneIndices[v * 4]);
            if (bone >= Math.max(1, ir.boneCount)) throw new IllegalArgumentException("Bone outside mesh palette: " + bone);
            data.putInt(bone);
        }
        data.flip();
    }

    static void indices(ByteBuffer data, int quads)
    {
        for (int q = 0; q < quads; q++)
        {
            int base = q * 4;
            data.putInt(base).putInt(base + 1).putInt(base + 2);
            data.putInt(base).putInt(base + 2).putInt(base + 3);
        }
        data.flip();
    }

    static void instance(ByteBuffer data, FastPathBatch.Submission s, int row)
    {
        glInstance(data, s, row);
        data.putInt(s.overlay);
    }

    /** The preserved GL prefix is 76 bytes; native instances append overlay as word 20. */
    static void glInstance(ByteBuffer data, FastPathBatch.Submission s, int row)
    {
        requireDirect(data);
        Matrix4f world = new Matrix4f(s.worldMatrix).rotateY((float) Math.PI);
        world.get(data.position(), data);
        data.position(data.position() + 64);
        var captured = NativeCrowdFrame.appearance(s);
        data.putInt(s.light).putInt(captured == null ? resolvedColor(s) : captured.color()).putInt(row);
    }

    static int resolvedColor(FastPathBatch.Submission s)
    {
        Color form = s.form.color.get().copy();
        form.mul(s.color);
        Color model = new Color().set(s.instance.config.color.get());
        return color(form.r * model.r, form.g * model.g, form.b * model.b, form.a * model.a);
    }

    static int color(float r, float g, float b, float a)
    {
        return channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float value)
    {
        return Math.max(0, Math.min(255, (int) (value * 255F)));
    }

    static void palette(ByteBuffer data, float[] matrices, int bones)
    {
        int available = matrices == null ? 0 : Math.min(bones, matrices.length / 16);
        if (available > 0)
        {
            data.asFloatBuffer().put(matrices, 0, available * 16);
            data.position(data.position() + available * 64);
        }
        for (int bone = available; bone < bones; bone++)
        {
            for (int i = 0; i < 16; i++) data.putFloat(i % 5 == 0 ? 1F : 0F);
        }
    }

    static void palette(ByteBuffer data, java.util.List<Matrix4f> matrices, int bones)
    {
        requireDirect(data);
        int available = Math.min(matrices.size(), bones);
        for (int bone = 0; bone < available; bone++)
        {
            matrices.get(bone).get(data.position(), data);
            data.position(data.position() + 64);
        }
        for (int bone = available; bone < bones; bone++)
            for (int i = 0; i < 16; i++) data.putFloat(i % 5 == 0 ? 1F : 0F);
    }

    private static void requireDirect(ByteBuffer buffer)
    {
        if (!buffer.isDirect()) throw new IllegalArgumentException("JOML GPU matrix packing requires a direct ByteBuffer");
    }
}
