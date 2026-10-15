package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.render.vao.*;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix4f;
import java.util.ArrayList;

/** Capture real model rendering; immutable GPU copies survive both pack windows. */
public final class VitrailActorFrame
{
    private static final ArrayList<VitrailActorPart> PARTS = new ArrayList<>();
    private static final VitrailFormReceipts FORMS = new VitrailFormReceipts();
    private static final ArrayList<mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl> EARLY_MESHES = new ArrayList<>();
    private static boolean frameOpen;
    private static boolean collecting;
    private static VitrailActorPart recording;
    private static boolean recordingTranslucent;
    private record TranslucentDraw(double distanceSquared, Runnable draw) {}
    private VitrailActorFrame() {}
    public static boolean collecting() { return collecting; }
    public static boolean traversing() { return FORMS.active(); }
    public static boolean replaying() { return FORMS.replaying(); }
    public static boolean ownedWorld() { return FORMS.worldScope() && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes(); }
    public static boolean earlyWorld() { return collecting && FORMS.worldScope() && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes(); }
    public static boolean suppressModel(RenderPipeline pipeline, IModelVAO vao, Matrix4f pose,
        float r, float g, float b, float a, int light, int overlay, int pass, Texture texture, float emission)
    {
        if (texture == null) texture = BBSModClient.getTextures().getLastBound();
        return vao.eckenZahl() > 0 && FORMS.suppressModel(new VitrailFormReceipts.Model(vao, pipeline,
            pose, texture, light, overlay, pass, r, g, b, a, emission));
    }
    public static boolean mesh(String kind) { return FORMS.mesh(kind); }

    public static boolean world(FormRenderingContext context)
    {
        return !context.ui && !context.worldLabel && context.stencilMap == null
            && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes()
            && !mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass();
    }

    public static void enter(Form form, FormRenderingContext context)
    {
        FORMS.enter(world(context) ? new VitrailFormReceipts.Key(form, context.entity,
            context.stack.last().pose(), context.world == null ? null : context.world.last().pose(),
            context.light, context.overlay, context.color, context.transition) : null);
    }

    public static void leave(boolean completed) { FORMS.leave(completed); }

    public static void begin()
    {
        close();
        frameOpen = true;
    }

    public static void retainEarlyMesh(mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl mesh)
    {
        RenderSystem.assertOnRenderThread();
        if (!frameOpen) throw new IllegalStateException("Early native mesh arrived outside its owning frame");
        EARLY_MESHES.add(mesh);
    }

    public static boolean retainParticle(mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl mesh)
    {
        if (!earlyWorld()) return mchorse.bbs_mod.client.renderer.NativeMorphCapture.retainParticle(mesh);
        retainEarlyMesh(mesh);
        return true;
    }

    public static boolean collected(Form form, IEntity entity)
    {
        return FORMS.has(form, entity);
    }

    public static boolean collect(Form form, FormRenderingContext context)
    {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vitrail")
            || !mchorse.bbs_mod.graphics.GpuBackend.isVulkan() || !dev.vitrail.render.BbsNativeGeometry.entitiesReady()
            || !frameOpen || !world(context)) return false;
        var base = mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form);
        if (!(base instanceof ModelFormRenderer)) return false;
        captureTraversal(form, context.entity, () -> mchorse.bbs_mod.forms.FormUtilsClient.render(form, context));
        return true;
    }

    /** The existing morph submission keeps its actual item/block/equipment collector once. */
    public static void original(Form form, IEntity entity, Runnable submission)
    {
        if (FORMS.active() || mchorse.bbs_mod.graphics.Zeichenziel.eigenes()
            || mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()
            || !frameOpen || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vitrail")
            || !mchorse.bbs_mod.graphics.GpuBackend.isVulkan()
            || !dev.vitrail.render.BbsNativeGeometry.entitiesReady()) { submission.run(); return; }
        captureTraversal(form, entity, submission);
    }

    private static void captureTraversal(Form form, IEntity entity, Runnable submission)
    {
        FORMS.begin(form, entity, false);
        int before = PARTS.size(), meshesBefore = EARLY_MESHES.size();
        boolean queueWasActive = mchorse.bbs_mod.forms.FormTranslucentQueue.suspend();
        collecting = true;
        try { submission.run(); FORMS.finish(); }
        catch (RuntimeException | Error error)
        {
            FORMS.abort();
            while (PARTS.size() > before)
                try { PARTS.removeLast().close(); } catch (RuntimeException | Error cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            while (EARLY_MESHES.size() > meshesBefore)
                try { EARLY_MESHES.removeLast().release(); } catch (RuntimeException | Error cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
            throw error;
        }
        finally { collecting = false; mchorse.bbs_mod.forms.FormTranslucentQueue.restore(queueWasActive); }
    }

    /** Evaluate bones/states again, but discard collector nodes and consume each owned draw. */
    public static void replay(Form form, IEntity entity, Runnable submission)
    {
        if (!collected(form, entity)) { submission.run(); return; }
        FORMS.begin(form, entity, true);
        boolean queueWasActive = mchorse.bbs_mod.forms.FormTranslucentQueue.suspend();
        try
        {
            mchorse.bbs_mod.forms.FormSammler.mitSammler(new net.minecraft.client.renderer.SubmitNodeStorage(), submission);
            FORMS.finish();
        }
        finally { FORMS.abort(); mchorse.bbs_mod.forms.FormTranslucentQueue.restore(queueWasActive); }
    }

    public static boolean capture(RenderPipeline pipeline, IModelVAO vao, Matrix4f modelView,
        float r, float g, float b, float a, int light, int overlay, int passMode, Texture texture, float emission)
    {
        if (!earlyWorld()) return false;
        VitrailMeshInputs inputs = vao instanceof BOBJModelVAO bobj ? bobj.vitrailInputs()
            : vao instanceof ModelVAO model ? model.vitrailInputs() : null;
        if (inputs == null) throw new IllegalStateException("Vitrail model capture lacks an actual VAO input contract: " + vao.getClass());
        if (texture == null) texture = BBSModClient.getTextures().getLastBound();
        VitrailFormReceipts.Model receipt = new VitrailFormReceipts.Model(vao, pipeline, modelView,
            texture, light, overlay, passMode, r, g, b, a, emission);
        if (inputs.kind() == VitrailVertexSource.Kind.SKIN) pipeline = ModelVAOPipeline.gpuVariante(pipeline);
        VitrailPipelineRegistry.bootstrap();
        var entry = VitrailPipelineRegistry.original(pipeline);
        if (entry == null) throw new IllegalStateException("Unregistered BBS Vitrail pipeline: " + pipeline);
        if (passMode != 0)
        {
            entry = VitrailPipelineRegistry.pass(pipeline, passMode);
            if (entry == null) throw new IllegalStateException("Unsupported BBS Vitrail alpha pass: " + passMode);
        }
        var world = new Matrix4f(new org.joml.Matrix4f(RenderSystem.getModelViewMatrix())).invert().mul(modelView);
        PARTS.add(VitrailActorPart.capture(inputs, entry.pipeline(), texture, modelView, world,
            r, g, b, a, light, overlay, passMode, emission));
        FORMS.model(receipt);
        return true;
    }

    public static void drawWindow(boolean translucent)
    {
        java.util.List<mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl> meshes = translucent
            ? new ArrayList<>(mchorse.bbs_mod.forms.FormTranslucentQueue.takeVitrailMeshes()) : java.util.List.of();
        if (translucent)
        {
            frameOpen = false;
            meshes.addAll(EARLY_MESHES);
            EARLY_MESHES.clear();
        }
        try
        {
            if (translucent)
            {
                ArrayList<TranslucentDraw> draws = new ArrayList<>();
                for (VitrailActorPart part : PARTS)
                    if (part.drawnIn(true)) draws.add(new TranslucentDraw(part.distanceSquared(), () -> drawPart(part, true)));
                for (mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl mesh : meshes)
                    draws.add(new TranslucentDraw(mesh.distanceSq, () -> { recording = null; mesh.drawVitrail(); }));
                draws.sort(java.util.Comparator.comparingDouble(TranslucentDraw::distanceSquared).reversed());
                for (TranslucentDraw draw : draws) draw.draw().run();
            }
            else for (VitrailActorPart part : PARTS) if (part.drawnIn(false)) drawPart(part, false);
        }
        finally
        {
            recording = null;
            try { dev.vitrail.render.EntityDraw.endGroup(); }
            finally { mchorse.bbs_mod.forms.FormTranslucentQueue.releaseVitrailMeshes(meshes); }
        }
    }

    private static void drawPart(VitrailActorPart part, boolean translucent)
    {
        recording = part;
        recordingTranslucent = translucent;
        /* Vitrail serves no entity program while a pack (re)loads -- PackChain.entities() is null
         * until the new chain is ready. Throwing here ended the game on every pack "Apply" with
         * actors on screen (2026-10-12); the part is skipped for that frame instead, said once. */
        if (!dev.vitrail.render.EntityDraw.draw(part.prepared(translucent), part.execution()) && !abgelehntGemeldet)
        {
            abgelehntGemeldet = true;
            com.mojang.logging.LogUtils.getLogger().warn("Vitrail refused a captured BBS actor draw (pack not ready?); skipped for that frame");
        }
    }

    private static boolean abgelehntGemeldet;

    public static boolean record(RenderPass pass)
    {
        if (recording == null) return false;
        recording.draw(pass, recordingTranslucent); return true;
    }

    public static void close()
    {
        RenderSystem.assertOnRenderThread();
        Throwable failure = null;
        try
        {
            try { mchorse.bbs_mod.forms.FormTranslucentQueue.releaseVitrailMeshes(EARLY_MESHES); }
            catch (RuntimeException | Error error) { failure = error; }
            for (VitrailActorPart part : PARTS)
            {
                try { part.close(); }
                catch (RuntimeException | Error error)
                {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
        }
        finally { EARLY_MESHES.clear(); frameOpen = false; PARTS.clear(); FORMS.clear(); collecting = false; recording = null; }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
}
