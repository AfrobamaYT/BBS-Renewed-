package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.render.vao.*;
import mchorse.bbs_mod.film.NativeReplayShadows;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.joml.Matrix4f;
import java.util.ArrayList;

/** Own model poses from the light's independent submission, never the camera's actor list. */
public final class VitrailShadowFrame
{
    private record Receipt(mchorse.bbs_mod.forms.forms.Form form, IModelVAO vao, RenderPipeline pipeline, int passMode) {}
    private static final ArrayList<VitrailActorPart> PARTS = new ArrayList<>();
    private static final ArrayList<Receipt> RECEIPTS = new ArrayList<>();
    private static final Object SCENE = new Object();
    private static final VitrailFormReceipts FORMS = new VitrailFormReceipts();
    private static final ArrayList<mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl> MESHES = new ArrayList<>();
    private static boolean collecting;
    private static boolean preparing;
    private static boolean prepared;
    private static int replayCursor;
    private static boolean window;
    private static VitrailActorPart recording;
    private static boolean recordingTranslucent;

    private VitrailShadowFrame() {}

    public static boolean collecting() { return collecting; }
    public static boolean replaying() { return collecting && !preparing; }
    public static boolean ownedWorld() { return collecting && FORMS.worldScope() && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes(); }
    public static boolean earlyWorld() { return preparing && ownedWorld(); }
    public static boolean mesh(String kind) { return FORMS.mesh(kind); }
    public static void enter(mchorse.bbs_mod.forms.forms.Form form, mchorse.bbs_mod.forms.renderers.FormRenderingContext context)
    {
        boolean world = !context.ui && !context.worldLabel && context.stencilMap == null
            && !mchorse.bbs_mod.graphics.Zeichenziel.eigenes();
        FORMS.enter(world ? new VitrailFormReceipts.Key(form, context.entity, context.stack.last().pose(),
            context.world == null ? null : context.world.last().pose(), context.light, context.overlay,
            context.color, context.transition) : null);
    }
    public static void leave(boolean completed) { FORMS.leave(completed); }
    public static void retainEarlyMesh(mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl mesh)
    {
        if (!earlyWorld()) throw new IllegalStateException("Native light mesh lacks its early frame owner");
        MESHES.add(mesh);
    }
    public static boolean active() { return collecting || window; }
    public static void window(boolean open) { window = open; }

    public static boolean enabled()
    {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vitrail")
            && mchorse.bbs_mod.graphics.GpuBackend.isVulkan();
    }

    /** Snapshot before terrain opens a pass; collector-only equipment is discarded here. */
    public static void prepare(Runnable submission)
    {
        if (!enabled()) return;
        if (prepared) throw new IllegalStateException("BBS light models prepared twice in one shadow walk");
        scope(true, submission);
        prepared = true;
    }

    /** The original late submission keeps real equipment but cannot allocate model buffers. */
    public static void collect(Runnable submission)
    {
        if (!enabled()) { submission.run(); return; }
        if (!prepared) throw new IllegalStateException("BBS light models were not prepared before terrain");
        replayCursor = 0;
        scope(false, submission);
        validateReplay();
    }

    private static void validateReplay()
    {
        if (replayCursor != RECEIPTS.size())
            throw new IllegalStateException("BBS light submission omitted an early model pose");
    }

    private static void scope(boolean early, Runnable submission)
    {
        if (collecting) throw new IllegalStateException("Nested BBS light submission");
        FORMS.begin(SCENE, null, !early);
        collecting = true;
        preparing = early;
        boolean queued = mchorse.bbs_mod.forms.FormTranslucentQueue.suspend();
        try { submission.run(); FORMS.finish(true); }
        catch (RuntimeException | Error failure)
        {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
        finally
        {
            FORMS.abort();
            collecting = false;
            preparing = false;
            mchorse.bbs_mod.forms.FormTranslucentQueue.restore(queued);
        }
    }

    public static boolean capture(RenderPipeline pipeline, IModelVAO vao, Matrix4f modelView,
        float r, float g, float b, float a, int light, int overlay, int passMode, Texture texture, float emission)
    {
        if (!collecting || FORMS.excludedScope() || mchorse.bbs_mod.graphics.Zeichenziel.eigenes()) return false;
        Receipt actual = new Receipt(mchorse.bbs_mod.forms.FormUtilsClient.getCurrentForm(), vao, pipeline, passMode);
        if (!preparing)
        {
            Receipt expected = replayCursor < RECEIPTS.size() ? RECEIPTS.get(replayCursor) : null;
            if (expected == null || expected.form() != actual.form() || expected.vao() != actual.vao()
                || expected.pipeline() != actual.pipeline() || expected.passMode() != actual.passMode())
                throw new IllegalStateException("BBS late light model has no matching early pose: " + vao.getClass());
            replayCursor++;
            return true;
        }
        VitrailMeshInputs inputs = vao instanceof BOBJModelVAO bobj ? bobj.vitrailInputs()
            : vao instanceof ModelVAO model ? model.vitrailInputs() : null;
        if (inputs == null) throw new IllegalStateException("BBS shadow has no actual model/skin inputs: " + vao.getClass());
        if (texture == null) texture = BBSModClient.getTextures().getLastBound();
        if (inputs.kind() == VitrailVertexSource.Kind.SKIN) pipeline = ModelVAOPipeline.gpuVariante(pipeline);
        VitrailPipelineRegistry.bootstrap();
        VitrailPipelineRegistry.Entry entry = passMode == 0 ? VitrailPipelineRegistry.original(pipeline)
            : VitrailPipelineRegistry.pass(pipeline, passMode);
        if (entry == null) throw new IllegalStateException("Unregistered BBS shadow pipeline/pass: " + pipeline + "/" + passMode);
        /* captureModelView is global-view * pose. Shadow shaders consume camera-relative
         * world positions and apply Vitrail's published light view/projection themselves. */
        Matrix4f relative = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).invert().mul(modelView);
        /* Emission changes the visible material, not its geometry or texture-alpha shadow. */
        PARTS.add(VitrailActorPart.capture(inputs, entry.pipeline(), texture, modelView, relative,
            r, g, b, a, light, overlay, passMode, 0F));
        /* Keep the caller's pipeline identity, before the SKIN variant is selected. */
        RECEIPTS.add(actual);
        return true;
    }

    public static void draw()
    {
        try
        {
            for (VitrailActorPart part : PARTS)
            {
                if (part.drawnIn(false)) draw(part, false);
                if (part.drawnIn(true)) draw(part, true);
            }
            recording = null;
            for (mchorse.bbs_mod.forms.FormTranslucentQueue.NetzBefehl mesh : MESHES) mesh.drawVitrail();
        }
        finally
        {
            recording = null;
            dev.vitrail.render.EntityDraw.endGroup();
        }
    }

    private static void draw(VitrailActorPart part, boolean translucent)
    {
        recording = part;
        recordingTranslucent = translucent;
        if (!dev.vitrail.render.EntityDraw.draw(part.prepared(translucent), part.execution()))
            throw new IllegalStateException("Vitrail refused an actual BBS light draw");
    }

    public static boolean record(RenderPass pass)
    {
        if (recording == null) return false;
        recording.draw(pass, recordingTranslucent);
        return true;
    }

    public static void close()
    {
        Throwable failure = null;
        try
        {
            try { mchorse.bbs_mod.forms.FormTranslucentQueue.releaseVitrailMeshes(MESHES); }
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
        finally
        {
            PARTS.clear();
            MESHES.clear(); FORMS.clear();
            RECEIPTS.clear();
            NativeReplayShadows.clear();
            collecting = false;
            preparing = false;
            prepared = false;
            replayCursor = 0;
            window = false;
            recording = null;
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
}
