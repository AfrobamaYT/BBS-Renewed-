package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Convert existing GPU geometry without reading it back or rebuilding tangents on the CPU. */
final class IrisGpuModelPacking
{
    private static int program;

    private IrisGpuModelPacking()
    {}

    static boolean supported()
    {
        GLCapabilities capabilities = GL.getCapabilities();
        return capabilities.OpenGL43 || (capabilities.GL_ARB_compute_shader
            && capabilities.GL_ARB_shader_storage_buffer_object && capabilities.GL_ARB_gpu_shader_fp64
            && capabilities.GL_ARB_gpu_shader5 && capabilities.GL_ARB_shading_language_420pack);
    }

    static GpuBuffer pack(GpuBuffer positions, GpuBuffer normals, GpuBuffer uvs, GpuBuffer indices,
        int count, int light, int overlay, int entity, int blockEntity, int item)
    {
        VertexFormat format = IrisVertexFormats.ENTITY;
        if (format.getElements().size() != 9 || count % 3 != 0)
        {
            throw new IllegalArgumentException("Unsupported Iris entity triangle layout: " + format);
        }

        int shader = program();
        int previousProgram = GL15.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousBinding = GL15.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] bindings = new int[5];
        long[] starts = new long[5];
        long[] sizes = new long[5];
        for (int i = 0; i < 5; i++)
        {
            bindings[i] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, i);
            starts[i] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, i);
            sizes[i] = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, i);
        }

        GpuBuffer output = RenderSystem.getDevice().createBuffer(() -> "bbs-iris-packed-model",
            GpuBuffer.USAGE_VERTEX, Math.multiplyExact(count, format.getVertexSize()));
        boolean complete = false;

        try
        {
            GL20.glUseProgram(shader);
            GL20.glUniform4i(GL20.glGetUniformLocation(shader, "layout0"), format.getVertexSize() / 4,
                offset(format, "Position"), offset(format, "Normal"), offset(format, "UV0"));
            GL20.glUniform4i(GL20.glGetUniformLocation(shader, "layout1"), offset(format, "Color"),
                offset(format, "UV1"), offset(format, "UV2"), offset(format, IrisVertexFormats.ENTITY_ID_ATTRIBUTE));
            GL20.glUniform4i(GL20.glGetUniformLocation(shader, "layout2"), offset(format, IrisVertexFormats.MID_TEXTURE_ATTRIBUTE),
                offset(format, IrisVertexFormats.TANGENT_ATTRIBUTE), count, indices == null ? 0 : 1);
            GL20.glUniform4i(GL20.glGetUniformLocation(shader, "context0"), light, overlay, entity, blockEntity);
            GL20.glUniform1i(GL20.glGetUniformLocation(shader, "itemId"), item);
            bind(0, positions);
            bind(1, normals);
            bind(2, uvs);
            bind(3, indices == null ? positions : indices);
            bind(4, output);
            GL43.glDispatchCompute((count + 255) / 256, 1, 1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
                | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            complete = true;
            return output;
        }
        finally
        {
            for (int i = 0; i < 5; i++)
            {
                if (bindings[i] != 0 && sizes[i] != 0)
                {
                    GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, i, bindings[i], starts[i], sizes[i]);
                }
                else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, i, bindings[i]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousBinding);
            GL20.glUseProgram(previousProgram);
            if (!complete) output.close();
        }
    }

    private static int offset(VertexFormat format, String attribute)
    {
        int offset = format.getElement(attribute).offset();
        if (offset % 4 != 0) throw new IllegalArgumentException("Unaligned Iris attribute " + attribute);
        return offset / 4;
    }

    private static void bind(int slot, GpuBuffer buffer)
    {
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, slot, ((GlBuffer) buffer).handle());
    }

    private static int program()
    {
        if (program != 0) return program;
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        int candidate = 0;

        try (InputStream source = IrisGpuModelPacking.class.getResourceAsStream("/assets/bbs/shaders/core/iris_model_pack.comp"))
        {
            if (source == null) throw new IOException("Missing Iris model packing shader");
            String code = new String(source.readAllBytes(), StandardCharsets.UTF_8);
            if (!GL.getCapabilities().OpenGL43)
            {
                code = code.replace("#version 430", "#version 330\n"
                    + "#extension GL_ARB_compute_shader : require\n"
                    + "#extension GL_ARB_shader_storage_buffer_object : require\n"
                    + "#extension GL_ARB_gpu_shader_fp64 : require\n"
                    + "#extension GL_ARB_gpu_shader5 : require\n"
                    + "#extension GL_ARB_shading_language_420pack : require");
            }
            GL20.glShaderSource(shader, code);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            {
                throw new IllegalStateException("Iris model packing shader: " + GL20.glGetShaderInfoLog(shader));
            }
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, shader);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0)
            {
                throw new IllegalStateException("Iris model packing program: " + GL20.glGetProgramInfoLog(candidate));
            }
            program = candidate;
            return program;
        }
        catch (IOException | RuntimeException | Error failure)
        {
            if (candidate != 0) GL20.glDeleteProgram(candidate);
            throw new IllegalStateException("Cannot initialize Iris GPU model packing", failure);
        }
        finally
        {
            GL20.glDeleteShader(shader);
        }
    }
}
