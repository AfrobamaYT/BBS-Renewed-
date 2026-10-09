package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Retain vanilla/Iris vertex layouts while preserving the native split-buffer path. */
public final class IrisModelBuffers
{
    private static final Map<GpuBuffer, Map<Context, Entry>> CACHE = new IdentityHashMap<>();
    private static Context current;

    private IrisModelBuffers()
    {}

    public static void draw(IModelVAO mesh, RenderPass pass, RenderPipeline pipeline, int light, int overlay)
    {
        Context saved = current;
        boolean entity = pipeline.getLocation().getNamespace().equals("minecraft")
            && pipeline.getLocation().getPath().contains("entity");
        current = entity && BBSRendering.isRenderingWorld() && BBSRendering.isIrisShadersEnabled()
            ? new Context(light, overlay, CapturedRenderingState.INSTANCE.getCurrentRenderedEntity(),
                CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity(),
                CapturedRenderingState.INSTANCE.getCurrentRenderedItem()) : null;

        try
        {
            mesh.zeichnen(pass);
        }
        finally
        {
            current = saved;
        }
    }

    public static boolean tryDraw(RenderPass pass, GpuBuffer positions, GpuBuffer normals,
        GpuBuffer uvs, GpuBuffer indices, int count)
    {
        if (current == null)
        {
            return false;
        }

        Map<Context, Entry> variants = CACHE.computeIfAbsent(positions, key -> new LinkedHashMap<>(8, 0.75F, true));
        Entry entry = variants.get(current);

        if (entry == null || entry.normals != normals || entry.uvs != uvs || entry.indices != indices)
        {
            if (entry != null) entry.vertices.close();
            entry = build(positions, normals, uvs, indices, count, current);
            variants.put(current, entry);
            if (variants.size() > 8)
            {
                Iterator<Entry> oldest = variants.values().iterator();
                oldest.next().vertices.close();
                oldest.remove();
            }
        }

        pass.setVertexBuffer(0, entry.vertices.slice());
        pass.draw(entry.count, 1, 0, 0);

        return true;
    }

    private static Entry build(GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs,
        GpuBuffer indices, int count, Context context)
    {
        if (IrisGpuModelPacking.supported())
        {
            GpuBuffer packed = IrisGpuModelPacking.pack(positions, normals, uvs, indices, count,
                context.light, context.overlay, context.entity, context.blockEntity, context.item);
            return new Entry(normals, uvs, indices, context.light, context.overlay, packed, count);
        }

        ByteBuffer p = read(positions);
        ByteBuffer n = null;
        ByteBuffer uv = null;
        ByteBuffer ix = null;

        try
        {
            n = read(normals);
            uv = read(uvs);
            if (indices != null) ix = read(indices);

            /* BufferBuilder's real Iris extension supplies tangents, mid-UV and entity
             * attributes. Expand indexed triangles so that extension sees actual faces. */
            try (ByteBufferBuilder storage = new ByteBufferBuilder(Math.max(256, Math.multiplyExact(count, 64))))
            {
                BufferBuilder builder = new BufferBuilder(storage, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.ENTITY);

                for (int i = 0; i < count; i++)
                {
                    int vertex = ix == null ? i : ix.getInt(i * Integer.BYTES);
                    int xyz = Math.multiplyExact(vertex, 3 * Float.BYTES);
                    int xy = Math.multiplyExact(vertex, 2 * Float.BYTES);

                    builder.addVertex(p.getFloat(xyz), p.getFloat(xyz + 4), p.getFloat(xyz + 8))
                        .setColor(255, 255, 255, 255)
                        .setUv(uv.getFloat(xy), uv.getFloat(xy + 4))
                        .setOverlay(context.overlay).setLight(context.light)
                        .setNormal(n.getFloat(xyz), n.getFloat(xyz + 4), n.getFloat(xyz + 8));
                }

                try (MeshData mesh = builder.buildOrThrow())
                {
                    GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "bbs-iris-model",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    return new Entry(normals, uvs, indices, context.light, context.overlay, buffer, count);
                }
            }
        }
        finally
        {
            MemoryUtil.memFree(p);
            if (n != null) MemoryUtil.memFree(n);
            if (uv != null) MemoryUtil.memFree(uv);
            if (ix != null) MemoryUtil.memFree(ix);
        }
    }

    private static ByteBuffer read(GpuBuffer buffer)
    {
        ByteBuffer data = MemoryUtil.memAlloc(Math.toIntExact(buffer.size())).order(ByteOrder.nativeOrder());

        try
        {
            int handle = ((GlBuffer) buffer).handle();
            if (GL.getCapabilities().OpenGL45)
            {
                GL45.glGetNamedBufferSubData(handle, 0L, data);
            }
            else
            {
                int previous = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, handle);
                try { GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, data); }
                finally { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous); }
            }
            return data;
        }
        catch (RuntimeException | Error failure)
        {
            MemoryUtil.memFree(data);
            throw failure;
        }
    }

    public static void invalidate(GpuBuffer buffer)
    {
        Iterator<Map.Entry<GpuBuffer, Map<Context, Entry>>> entries = CACHE.entrySet().iterator();
        while (entries.hasNext())
        {
            Map.Entry<GpuBuffer, Map<Context, Entry>> item = entries.next();
            if (item.getValue().isEmpty())
            {
                entries.remove();
                continue;
            }
            Entry entry = item.getValue().values().iterator().next();
            if (item.getKey() == buffer || entry.normals == buffer || entry.uvs == buffer || entry.indices == buffer)
            {
                for (Entry variant : item.getValue().values()) variant.vertices.close();
                entries.remove();
            }
        }
    }

    public static void close(GpuBuffer buffer)
    {
        invalidate(buffer);
        buffer.close();
    }

    private record Context(int light, int overlay, int entity, int blockEntity, int item)
    {}

    private record Entry(GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices, int light,
        int overlay, GpuBuffer vertices, int count)
    {}
}
