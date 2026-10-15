package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteOrder;
import java.util.List;

/** One real actor pose copied before a later actor mutates a shared VAO. */
public record VitrailActorPart(VitrailMeshInputs.Snapshot mesh, RenderPipeline pipeline,
    GpuBufferSlice transforms, GpuBufferSlice parameters, PreparedRenderType.Texture texture,
    int passMode, float emission, GpuBufferSlice translucentParameters,
    RenderPipeline opaquePipeline, RenderPipeline translucentPipeline, double distanceSquared) implements AutoCloseable
{
    public static VitrailActorPart capture(VitrailMeshInputs inputs, RenderPipeline pipeline, Texture texture,
        Matrix4f modelView, Matrix4f relativeWorld, float r, float g, float b, float a,
        int light, int overlay, int passMode, float emission)
    {
        if (texture == null) throw new IllegalStateException("BBS Vitrail actor has no actual texture");
        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var limits = VitrailDeviceLimits.read();
        boolean split = passMode == 0 && a >= 1F && texture.hasTranslucency();
        int stride = Math.toIntExact(((VitrailGpuLayout.DRAW_BYTES + limits.uniformAlignment() - 1)
            / limits.uniformAlignment()) * limits.uniformAlignment());
        var snapshot = inputs.capture(device, encoder, limits.texelAlignment(), limits.maximumTexels(),
            limits.uniformAlignment(), limits.maximumUniformBytes());
        GpuBuffer ownedTransforms = null, ownedParameters = null;
        var bytes = MemoryUtil.memAlloc(Math.max(split ? stride + VitrailGpuLayout.DRAW_BYTES : VitrailGpuLayout.DRAW_BYTES,
            net.minecraft.client.renderer.DynamicGpuData.TRANSFORM_UBO_SIZE)).order(ByteOrder.nativeOrder());
        try
        {
            new net.minecraft.client.renderer.DynamicGpuData.Transform(modelView, new Vector4f(1),
                new org.joml.Vector3f(), new Matrix4f()).write(bytes);
            bytes.position(0).limit(net.minecraft.client.renderer.DynamicGpuData.TRANSFORM_UBO_SIZE);
            ownedTransforms = device.createBuffer(() -> "bbs-vitrail-transform", GpuBuffer.USAGE_UNIFORM, bytes);
            bytes.clear().limit(VitrailGpuLayout.DRAW_BYTES);
            bytes.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
            bytes.putInt(light & 65535).putInt(light >>> 16).putInt(overlay & 65535).putInt(overlay >>> 16);
            bytes.putInt(0).putInt(0).putInt(0).putInt(0);
            relativeWorld.get(48, bytes);
            bytes.position(112).putInt(split ? 1 : passMode).putInt(Float.floatToRawIntBits(emission)).putInt(0).putInt(0);
            if (split)
            {
                java.nio.ByteBuffer copy = bytes.duplicate().position(0).limit(VitrailGpuLayout.DRAW_BYTES);
                bytes.limit(bytes.capacity()).position(stride).put(copy);
                bytes.putInt(stride + 112, 2);
            }
            bytes.flip();
            ownedParameters = device.createBuffer(() -> "bbs-vitrail-draw", GpuBuffer.USAGE_UNIFORM, bytes);
            VitrailPipelineRegistry.Entry source = VitrailPipelineRegistry.deformed(pipeline);
            return new VitrailActorPart(snapshot, pipeline, ownedTransforms.slice(), ownedParameters.slice(0, VitrailGpuLayout.DRAW_BYTES),
                new PreparedRenderType.Texture("Sampler0", texture.view(), texture.sampler()), passMode, emission,
                split ? ownedParameters.slice(stride, VitrailGpuLayout.DRAW_BYTES) : null,
                split ? VitrailPipelineRegistry.pass(source.original(), 1).pipeline() : null,
                split ? VitrailPipelineRegistry.pass(source.original(), 2).pipeline() : null,
                (double)modelView.m30()*modelView.m30() + (double)modelView.m31()*modelView.m31()
                    + (double)modelView.m32()*modelView.m32());
        }
        catch (Throwable failure)
        {
            snapshot.close();
            if (ownedTransforms != null) ownedTransforms.close();
            if (ownedParameters != null) ownedParameters.close();
            throw failure;
        }
        finally { MemoryUtil.memFree(bytes); }
    }

    public boolean drawnIn(boolean translucent)
    {
        return this.translucentParameters != null ||
            this.pipeline.getColorTargetStates().getFirst().blendFunction().isPresent() == translucent;
    }

    public PreparedRenderType prepared(boolean translucent)
    {
        RenderPipeline selected = this.translucentParameters == null ? this.pipeline :
            translucent ? this.translucentPipeline : this.opaquePipeline;
        //? if >=26.3 {
        return new PreparedRenderType("bbs-vitrail-actor", selected, null, this.transforms,
            new com.mojang.blaze3d.systems.ScissorState(), List.of(this.texture));
        //?} else {
        /*return new PreparedRenderType(selected, net.minecraft.client.renderer.rendertype.OutputTarget.MAIN_TARGET, this.transforms,
            new com.mojang.blaze3d.systems.ScissorState(), List.of(this.texture));
        *///?}
    }

    public StagedVertexBuffer.ExecuteInfo execution()
    {
        //? if >=26.3 {
        return new StagedVertexBuffer.ExecuteInfo(this.mesh.corners().buffer(), this.mesh.corners().buffer(),
            com.mojang.renderpearl.api.pipeline.IndexType.INT, 0, 0, this.mesh.count(), PrimitiveTopology.TRIANGLES);
        //?} else {
        /*return new StagedVertexBuffer.ExecuteInfo(this.mesh.corners().buffer(), this.mesh.corners().buffer(),
            com.mojang.renderpearl.api.pipeline.IndexType.INT, 0, 0, this.mesh.count());
        *///?}
    }

    public void draw(RenderPass pass, boolean translucent)
    {
        if (this.emission != 0)
            throw new IllegalStateException("Vitrail BBS emission adaptation is not built yet");
        pass.setUniform("BbsDraw", translucent && this.translucentParameters != null ? this.translucentParameters : this.parameters);
        pass.setUniform("BbsAlphaTexture", this.texture.textureView(), this.texture.sampler());
        this.mesh.texels().forEach(pass::setUniform);
        if (this.mesh.bones() != null) pass.setUniform("BbsBones", this.mesh.bones());
        pass.setVertexBuffer(0, this.mesh.corners());
        pass.draw(this.mesh.count(), 1, 0, 0);
    }

    @Override public void close()
    {
        try { this.mesh.close(); }
        finally { try { this.transforms.buffer().close(); } finally { this.parameters.buffer().close(); } }
    }
}
