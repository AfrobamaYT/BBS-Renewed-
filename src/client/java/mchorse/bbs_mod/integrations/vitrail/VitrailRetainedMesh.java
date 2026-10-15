package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mchorse.bbs_mod.graphics.GehaltenesNetz;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/** Retained entity meshes use Vitrail's existing entity format and pack compiler. */
public final class VitrailRetainedMesh
{
    private VitrailRetainedMesh()
    {}

    public static boolean active()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vitrail")
            && mchorse.bbs_mod.graphics.GpuBackend.isVulkan()
            && dev.vitrail.render.EntityDraw.wanted() && dev.vitrail.render.PackChain.drawingPack()
            && dev.vitrail.render.EntityMesh.carrying();
    }

    public static boolean eligible(RenderPipeline pipeline)
    {
        /* Preserve the original identity: EntityDraw's stock table is keyed by this object. */
        return pipeline == RenderPipelines.ENTITY_TRANSLUCENT
            || pipeline == RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    }

    public static void draw(GehaltenesNetz mesh, RenderPipeline pipeline, Matrix4f modelView, Texture texture,
        com.mojang.renderpearl.api.commands.RenderPass.RenderArea area)
    {
        if (!eligible(pipeline) || texture == null)
            throw new IllegalStateException("Native retained entity mesh lacks its stock pipeline or texture");
        /* Buffer preparation must not happen while a preceding geometry family owns its pass. */
        dev.vitrail.render.EntityDraw.endGroup();
        dev.vitrail.render.GraphicsApi.suspendLevelPass();
        net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo execution = mesh.execution(pipeline);
        com.mojang.renderpearl.api.buffers.GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, new Vector4f(1F, 1F, 1F, 1F));
        ScissorState scissor = new ScissorState();
        if (area != null) scissor.enable(area.x(), area.y(), area.width(), area.height());
        //? if >=26.3 {
        PreparedRenderType prepared = new PreparedRenderType("bbs-vitrail-retained", VitrailRetainedAlpha.forOriginal(pipeline), null,
            transforms, scissor, List.of(new PreparedRenderType.Texture("Sampler0", texture.view(), texture.sampler())));
        //?} else {
        /*PreparedRenderType prepared = new PreparedRenderType(VitrailRetainedAlpha.forOriginal(pipeline),
            net.minecraft.client.renderer.rendertype.OutputTarget.MAIN_TARGET,
            transforms, scissor, List.of(new PreparedRenderType.Texture("Sampler0", texture.view(), texture.sampler())));
        *///?}
        VitrailRetainedAlpha.recording(texture);
        try
        {
            /* Wie VitrailActorFrame.drawPart: waehrend ein Pack neu laedt (Shader-Reload, R)
             * bedient Vitrail noch kein Entity-Programm. Werfen beendete das Spiel bei offenem
             * Film (2026-10-13, crash 21.28.12); das Netz faellt fuer dieses Bild aus, einmal gesagt. */
            if (!dev.vitrail.render.EntityDraw.draw(prepared, execution) && !abgelehntGemeldet)
            {
                abgelehntGemeldet = true;
                com.mojang.logging.LogUtils.getLogger().warn("Vitrail refused a retained BBS entity draw (pack not ready?); skipped for that frame");
            }
        }
        finally { VitrailRetainedAlpha.recording(null); }
    }

    private static boolean abgelehntGemeldet;
}
