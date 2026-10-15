package dev.vitrail.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.pack.model.AlphaTest;
import dev.vitrail.pack.model.RenderStage;
import mchorse.bbs_mod.integrations.vitrail.VitrailGpuLayout;
import mchorse.bbs_mod.integrations.vitrail.VitrailLoadedProgram;
import mchorse.bbs_mod.integrations.vitrail.VitrailPipelineRegistry;
import mchorse.bbs_mod.integrations.vitrail.VitrailVertexSource;
import mchorse.bbs_mod.integrations.vitrail.VitrailRetainedAlpha;

import java.util.Map;

/** Narrow optional access to Vitrail's real package-private program/pass contracts. */
public final class BbsNativeGeometry
{
    private static final class Fields
    {
        private static final java.lang.reflect.Field PASS = pass();
        private static final java.lang.reflect.Field PARTICLE_OWNER = field(ParticleDraw.class, "owner", PackChain.class);
        private static final java.lang.reflect.Field PACK_VALUES = field(PackChain.class, "values", PackValues.class);
        private static final java.lang.reflect.Field ENTITY_READ = field(EntityDraw.class, "read", boolean.class);
        private static java.lang.reflect.Field field(Class<?> owner, String name, Class<?> type)
        {
            try
            {
                java.lang.reflect.Field field = owner.getDeclaredField(name);
                if (field.getType() != type) throw new IllegalStateException("Vitrail geometry field ABI changed: " + name);
                field.setAccessible(true); return field;
            }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Vitrail geometry field ABI changed: " + name, error); }
        }
        private static java.lang.reflect.Field pass()
        {
            try
            {
                var field = GeometryProgram.class.getDeclaredField("pass");
                if (field.getType() != GeometryProgram.Pass.class) throw new IllegalStateException("Vitrail pass ABI changed");
                field.setAccessible(true);
                return field;
            }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Vitrail pass ABI changed", error); }
        }
    }

    private BbsNativeGeometry() {}

    /** The entity family's asynchronous read can lag behind an already drawable chain. */
    public static boolean entitiesReady()
    {
        if (!EntityDraw.wanted() || !PackChain.drawingPack() || PackChain.warming()) return false;
        EntityDraw entities = PackChain.entities();
        if (entities == null) return false;
        try { return Fields.ENTITY_READ.getBoolean(entities); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read actual pack entity readiness", error); }
    }

    public static org.joml.Matrix4f particleView()
    {
        ParticleDraw particles = PackChain.particles();
        if (particles == null) throw new IllegalStateException("Active pack has no particle family");
        try
        {
            PackChain owner = (PackChain) Fields.PARTICLE_OWNER.get(particles);
            PackValues values = (PackValues) Fields.PACK_VALUES.get(owner);
            return new org.joml.Matrix4f(values.world().gbufferModelView());
        }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read actual pack particle view", error); }
    }

    @SuppressWarnings("unchecked")
    public static void register(Map<?, ?> cameraRows, Map<?, ?> blockRows, Map<?, ?> shadowRows)
    {
        VitrailPipelineRegistry.bootstrap();
        var camera = (Map<RenderPipeline, EntityDraw.Element>) cameraRows;
        var blocks = (Map<RenderPipeline, EntityDraw.Element>) blockRows;
        var shadows = (Map<RenderPipeline, EntityDraw.Element>) shadowRows;
        synchronized (EntityDraw.class)
        {
            for (VitrailRetainedAlpha.Row row : VitrailRetainedAlpha.rows())
            {
                camera.putIfAbsent(row.pipeline(), new EntityDraw.Element(row.pipeline(), row.name(),
                    "gbuffers_entities_translucent", AlphaTest.OFF));
                blocks.putIfAbsent(row.pipeline(), new EntityDraw.Element(row.pipeline(), "block_" + row.name(),
                    "gbuffers_block_translucent", AlphaTest.OFF, RenderStage.BLOCK_ENTITIES, false));
                shadows.putIfAbsent(row.pipeline(), new EntityDraw.Element(row.pipeline(), "shadow_" + row.name(),
                    "shadow_entities", AlphaTest.OFF, RenderStage.ENTITIES, false));
            }
            for (var entry : VitrailPipelineRegistry.entries())
            {
                boolean blended = entry.pipeline().bbsColorTargetStates().getFirst().blendFunction().isPresent();
                /* BBS tests sampled texture alpha before the pack; a second test on the
                 * pack's output alpha clips uniform fades and may test material data. */
                camera.putIfAbsent(entry.pipeline(), new EntityDraw.Element(entry.pipeline(), entry.name(),
                    blended ? "gbuffers_entities_translucent" : "gbuffers_entities", AlphaTest.OFF));
                blocks.putIfAbsent(entry.pipeline(), new EntityDraw.Element(entry.pipeline(), "block_" + entry.name(),
                    blended ? "gbuffers_block_translucent" : "gbuffers_block", AlphaTest.OFF,
                    RenderStage.BLOCK_ENTITIES, false));
                shadows.putIfAbsent(entry.pipeline(), new EntityDraw.Element(entry.pipeline(), "shadow_" + entry.name(),
                    "shadow_entities", AlphaTest.OFF, RenderStage.ENTITIES, false));
            }
        }
    }

    public static VertexFormat format(Object row, VertexFormat original)
    {
        return VitrailPipelineRegistry.deformed(((EntityDraw.Element) row).pipeline()) == null
            ? original : VitrailGpuLayout.CORNER;
    }

    public static PackProgram.Loaded program(PackProgram.Loaded loaded, Object row)
    {
        EntityDraw.Element element = (EntityDraw.Element) row;
        if (VitrailRetainedAlpha.own(element.pipeline())) return VitrailLoadedProgram.retainedAlpha(loaded);
        var entry = VitrailPipelineRegistry.deformed(element.pipeline());
        return entry == null ? loaded : VitrailLoadedProgram.deform(loaded, entry.kind(), element.shadow());
    }

    public static RenderPipeline.Builder vertex(Object owner, RenderPipeline.Builder builder, int binding, VertexFormat format,
        Operation<RenderPipeline.Builder> original)
    {
        var result = original.call(builder, binding, format);
        GeometryProgram.Pass pass;
        try { pass = (GeometryProgram.Pass) Fields.PASS.get(owner); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read actual Vitrail pass", error); }
        if (binding == 0 && VitrailRetainedAlpha.named(pass.name())) result.withBindGroupLayout(VitrailRetainedAlpha.BINDINGS);
        var entry = VitrailPipelineRegistry.named(pass.name());
        if (entry != null && binding == 0)
        {
            if (format != VitrailGpuLayout.CORNER) throw new IllegalStateException("Vitrail native format identity changed");
            result.withBindGroupLayout(VitrailGpuLayout.bindings(entry.kind()));
            if (entry.kind() == VitrailVertexSource.Kind.CROWD) result.withVertexBinding(1, VitrailGpuLayout.INSTANCE);
        }
        return result;
    }
}
