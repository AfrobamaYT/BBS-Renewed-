package mchorse.bbs_mod.film.regie;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.CompareOp;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Pipelines der Weltschicht: je Material eine fuer die SICHTBAREN und eine fuer die
 * VERDECKTEN Teile — der Editor-Standard des Ports (siehe FormPipelines#hinterGeometrie,
 * wie Unitys „occluded gizmo color" und Unreals Control Rig). 26.3 rechnet die Tiefe umgekehrt:
 * sichtbar ist GREATER_THAN_OR_EQUAL, verdeckt LESS_THAN. Tiefe wird nie geschrieben.
 *
 * <p>Gemischt wird vormultipliziert (ONE, ONE_MINUS_SRC_ALPHA) — die Shader des Designs geben
 * Farbe und Deckung so aus. Werte je Zug kommen im Block {@value #INFO}.</p>
 */
final class RegieWeltPipelines
{
    static final String INFO = "RegieInfo";

    private static final BindGroupLayout GRUPPE = BindGroupLayout.builder()
        .withUniform(INFO, UniformType.UNIFORM_BUFFER)
        .build();
    private static final Map<String, RenderPipeline> ZWISCHENSPEICHER = new HashMap<>();

    private RegieWeltPipelines()
    {
    }

    /** @param material Name des Fragment-Shaders ohne Vorsilbe (trail, platte, strahl, start) */
    static RenderPipeline holen(String material, boolean verdeckt)
    {
        return ZWISCHENSPEICHER.computeIfAbsent(material + (verdeckt ? "_verdeckt" : ""), (name) -> RenderPipeline.builder()
            .withLocation(new ResourceLocation("bbs", "pipeline/regie_" + name))
            .withVertexShader(new ResourceLocation("bbs", "core/regie_welt"))
            .withFragmentShader(new ResourceLocation("bbs", "core/regie_" + material))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(VertexFormat.Mode.QUADS)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(GRUPPE)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(verdeckt ? CompareOp.LESS_THAN : CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .build());
    }

    /**
     * Block {@value #INFO} fuer einen Zug: drei vec4 (std140, dicht gepackt), im fluechtigen
     * Speicher dieser Abgabe — wie GehaltenesNetz seine kurzlebigen Ecken. Die Daten werden
     * sofort kopiert; das geht auch bei offenem Weltdurchgang und braucht kein Schliessen.
     * Ausrichtung 256: der groesste in Vulkan zulaessige Mindestversatz fuer Uniforms.
     */
    static GpuBufferSlice info(int farbe, float deckkraft, float[] werte, float zeit, boolean verdeckt, float extra1, float extra2)
    {
        ByteBuffer daten = MemoryUtil.memAlloc(48);

        try
        {
            daten.putFloat(((farbe >> 16) & 0xFF) / 255F).putFloat(((farbe >> 8) & 0xFF) / 255F)
                .putFloat((farbe & 0xFF) / 255F).putFloat(deckkraft);

            for (int i = 0; i < 4; i++)
            {
                daten.putFloat(i < werte.length ? werte[i] : 0F);
            }

            daten.putFloat(zeit).putFloat(verdeckt ? 1F : 0F).putFloat(extra1).putFloat(extra2);
            daten.flip();

            return mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder().transientMemory().uploadGpu(daten, 256L, mchorse.bbs_mod.compat.gpu.PufferNutzung.USAGE_UNIFORM);
        }
        finally
        {
            MemoryUtil.memFree(daten);
        }
    }
}
