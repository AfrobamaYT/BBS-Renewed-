package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** CPU/weld/shape-key vertices already carry their transformed normals, UVs, colour and light. */
public final class VitrailImmediateModels
{
    private VitrailImmediateModels() {}

    public static com.mojang.blaze3d.vertex.VertexFormat format()
    {
        return VitrailAttachmentCapture.ownedWorld() && VitrailRetainedMesh.active()
            ? RenderPipelines.ENTITY_TRANSLUCENT.getVertexFormatBinding(0)
            : com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY;
    }

    public static boolean retain(BufferBuilder builder, com.mojang.blaze3d.vertex.PoseStack stack,
        Texture texture, boolean culling, StencilMap picking)
    {
        if (picking != null || !VitrailAttachmentCapture.ownedWorld()
            || !VitrailAttachmentCapture.earlyWorld() && !VitrailAttachmentCapture.replaying()) return false;
        if (VitrailAttachmentCapture.mesh("model-immediate")) return true;
        if (!VitrailRetainedMesh.active())
            throw new IllegalStateException("Native CPU model has no available retained entity family");
        RenderPipeline pipeline = culling ? RenderPipelines.ENTITY_TRANSLUCENT_CULL : RenderPipelines.ENTITY_TRANSLUCENT;
        MeshData mesh = builder.build();
        if (mesh == null) return true;
        if (!mesh.drawState().format().equals(pipeline.getVertexFormatBinding(0)))
        {
            mesh.close(); throw new IllegalStateException("Native CPU model vertices do not match the actual entity binding");
        }
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrixCopy());
        Vector3f origin = view.transformPosition(stack.last().pose().getTranslation(new Vector3f()));
        FormTranslucentQueue.NetzBefehl command = new FormTranslucentQueue.NetzBefehl(
            new GehaltenesNetz(mesh), () -> pipeline, texture, view, null, origin, true, null, null);
        try { VitrailAttachmentCapture.retain(command); }
        catch (RuntimeException | Error error) { command.release(); throw error; }
        return true;
    }
}
