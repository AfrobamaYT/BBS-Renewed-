package com.afrobama.blendertransform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.vitrail.render.BbsNativeGeometry;

import java.util.Map;

/** Mandatory handlers for the optional native Vitrail compiler door. */
public final class VitrailGeometryLogik
{
    private VitrailGeometryLogik() {}
    public static void register(Map<?, ?> camera, Map<?, ?> blocks, Map<?, ?> shadows)
    { BbsNativeGeometry.register(camera, blocks, shadows); }
    public static VertexFormat format(Object row, VertexFormat original)
    { return BbsNativeGeometry.format(row, original); }
    public static dev.vitrail.glsl.PackProgram.Loaded program(dev.vitrail.glsl.PackProgram.Loaded loaded, Object row)
    { return BbsNativeGeometry.program(loaded, row); }
    public static RenderPipeline.Builder vertex(Object owner, RenderPipeline.Builder builder, int index, VertexFormat format,
        Operation<RenderPipeline.Builder> original)
    { return BbsNativeGeometry.vertex(owner, builder, index, format, original); }
}
