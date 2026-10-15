package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.integrations.vitrail.VitrailActorFrame;
import org.joml.Matrix4f;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Captures a morph's native body while retaining its original collector and late bodyparts. */
public final class NativeMorphCapture
{
    public static final class CapturedBody
    {
        private final Form form;
        private final IEntity entity;
        private final Set<IModelVAO> vaos = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<ParticleKey> particles = new HashSet<>();
        private final int generation = resetGeneration;

        private CapturedBody(Form form, IEntity entity)
        {
            this.form = form;
            this.entity = entity;
        }
    }

    private static CapturedBody capturing;
    private static CapturedBody replaying;
    private static int resetGeneration;
    private static boolean accepting;
    private static ParticleKey particle;
    private static final Map<Actor, CapturedBody> ORIGINAL = new HashMap<>();
    private record Actor(Form form, IEntity entity)
    {
        @Override public boolean equals(Object value)
        { return value instanceof Actor actor && this.form == actor.form && this.entity == actor.entity; }
        @Override public int hashCode()
        { return System.identityHashCode(this.form) * 31 + System.identityHashCode(this.entity); }
    }
    private record ParticleKey(Actor actor, Matrix4f pose) {}

    private NativeMorphCapture()
    {}

    public static CapturedBody capture(Form form, IEntity entity, PoseStack stack,
        int light, int overlay, float transition)
    {
        /* Optional mod linkage must stop before any native compiler class is reached. */
        if (!accepting || !FabricLoader.getInstance().isModLoaded("vitrail") || !GpuBackend.isVulkan()) return null;
        CapturedBody body = ORIGINAL.getOrDefault(new Actor(form, entity), new CapturedBody(form, entity));
        if (VitrailActorFrame.collected(form, entity)) return body;
        CapturedBody previous = capturing;
        int generation = resetGeneration;
        boolean[] accepted = {false};
        try
        {
            capturing = body;
            FormSammler.mitSammler(new SubmitNodeStorage(), () ->
                accepted[0] = VitrailActorFrame.collect(form, new FormRenderingContext()
                    .set(FormRenderType.ENTITY, entity, stack, light, overlay, transition)
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera())));
        }
        finally
        {
            capturing = generation == resetGeneration ? previous : null;
        }
        return accepted[0] && !body.vaos.isEmpty() || !body.particles.isEmpty() ? body : null;
    }

    /** The original collector still renders every equipment/bodypart exactly where it did. */
    public static void original(Form form, FormRenderingContext context, Runnable render)
    {
        if (!VitrailActorFrame.world(context)) { render.run(); return; }
        original(form, context.entity, render);
    }

    public static void original(Form form, IEntity entity, Runnable render)
    {
        if (!accepting || mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass()
            || !mchorse.bbs_mod.integrations.vitrail.VitrailRetainedParticles.active())
        {
            render.run();
            return;
        }
        CapturedBody previous = capturing;
        int generation = resetGeneration;
        capturing = ORIGINAL.computeIfAbsent(new Actor(form, entity), ignored -> new CapturedBody(form, entity));
        try { VitrailActorFrame.original(form, entity, render); }
        finally { capturing = generation == resetGeneration ? previous : null; }
    }

    public static boolean capturingParticles()
    {
        return accepting && capturing != null;
    }

    public static boolean suppressesParticle(Form form, IEntity entity, Matrix4f pose)
    {
        ParticleKey key = new ParticleKey(new Actor(form, entity), new Matrix4f(pose));
        return capturing != null && capturing.particles.contains(key)
            || replaying != null && replaying.particles.contains(key);
    }

    public static void particle(Form form, IEntity entity, Matrix4f pose, Runnable render)
    {
        if (!capturingParticles()) throw new IllegalStateException("Particle capture has no owning morph frame");
        ParticleKey previous = particle;
        int generation = resetGeneration;
        particle = new ParticleKey(new Actor(form, entity), new Matrix4f(pose));
        try { render.run(); }
        finally { particle = generation == resetGeneration ? previous : null; }
    }

    /** Transfer a real owned mesh; the native actor frame releases it on drain or abort. */
    public static boolean retainParticle(FormTranslucentQueue.NetzBefehl mesh)
    {
        if (particle == null) return false;
        if (!capturingParticles()) throw new IllegalStateException("Particle capture frame expired before transfer");
        VitrailActorFrame.retainEarlyMesh(mesh);
        capturing.particles.add(particle);
        return true;
    }

    public static void captured(IModelVAO vao)
    {
        if (capturing != null) capturing.vaos.add(vao);
    }

    public static boolean replayingBody(Form form, IEntity entity)
    {
        return replaying != null && replaying.form == form && replaying.entity == entity;
    }

    public static boolean suppresses(IModelVAO vao)
    {
        if (VitrailActorFrame.replaying()) throw new IllegalStateException("Native model replay lacks its actual draw parameters");
        return replaying != null && replaying.form == FormUtilsClient.getCurrentForm()
            && replaying.vaos.contains(vao);
    }

    public static boolean suppresses(com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
        IModelVAO vao, Matrix4f pose, float r, float g, float b, float a, int light, int overlay,
        int pass, mchorse.bbs_mod.graphics.texture.Texture texture, float emission)
    {
        return VitrailActorFrame.replaying() ? VitrailActorFrame.suppressModel(pipeline, vao, pose,
            r, g, b, a, light, overlay, pass, texture, emission) : suppresses(vao);
    }

    public static void replay(CapturedBody body, Runnable render)
    {
        CapturedBody previous = replaying;
        int generation = resetGeneration;
        replaying = body != null && body.generation == resetGeneration ? body : null;
        try
        {
            if (replaying != null && VitrailActorFrame.collected(replaying.form, replaying.entity))
                VitrailActorFrame.replay(replaying.form, replaying.entity, render);
            else render.run();
        }
        finally
        {
            replaying = generation == resetGeneration ? previous : null;
        }
    }

    public static void reset()
    {
        resetGeneration++;
        accepting = false;
        capturing = null;
        replaying = null;
        particle = null;
        ORIGINAL.clear();
    }

    public static void beginCollection()
    {
        accepting = true;
    }

    public static void endCollection()
    {
        accepting = false;
    }
}
