package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Zeichnet ein selbst aufgebautes Netz sofort — der Ersatz fuer
 * {@code Tesselator} + {@code BufferUploader.drawWithShader}.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>Der alte Weg war ueberall im Baum derselbe: {@code Tesselator.getInstance().begin(...)},
 * Ecken hineinschreiben, {@code RenderSystem.setShader(...)}, dann
 * {@code BufferUploader.drawWithShader(builder.end())}. Beide Klassen gibt es in 26.2 nicht
 * mehr — zusammen sind sie die groesste einzelne Ursache im Port (rund 150 Fundstellen).
 *
 * <p>Was bleibt, ist der {@link BufferBuilder}: Ecken sammeln geht unveraendert. Was
 * wegfaellt, ist das Abschicken. An seine Stelle tritt der Weg, den Vanillas eigene
 * Sofort-Zeichner gehen (Wolken, Himmel, Weltgrenze, Lightmap): Puffer auf die Karte
 * legen, Durchgang gegen das Ziel oeffnen, Pipeline waehlen, zeichnen.
 *
 * <h2>Benutzung</h2>
 *
 * <pre>{@code
 * try (ByteBufferBuilder speicher = new ByteBufferBuilder(1024))
 * {
 *     BufferBuilder bauer = SofortZeichner.beginnen(speicher, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
 *
 *     bauer.addVertex(x, y, z).setColor(farbe);
 *     // ...
 *
 *     SofortZeichner.zeichnen(FormPipelines.holen(...), bauer.build(), modelView, farbe, null);
 * }
 * }</pre>
 *
 * <p>⚠️ EIN DURCHGANG JE AUFRUF. Vanilla sammelt gleichartige Geometrie und schickt sie
 * gebuendelt; dieser Weg oeffnet fuer jedes Netz einen eigenen Durchgang, weil BBS'
 * Hilfsgeometrie an beliebiger Stelle im Bild auftaucht. Fuer Gizmos, Pfade und
 * Drahtgitter ist das richtig. Wer damit hunderte Netze pro Bild zeichnet, hat den
 * Flaschenhals hier und nicht im Aufrufer — dann sammeln, nicht optimieren.
 */
public final class SofortZeichner
{
    /**
     * Beginnt ein Netz. Reine Bequemlichkeit: {@code new BufferBuilder(...)} tut dasselbe,
     * aber der Name macht die Absicht an der Aufrufstelle lesbar, wo frueher
     * {@code Tesselator.getInstance().begin(...)} stand.
     */
    public static BufferBuilder beginnen(ByteBufferBuilder speicher, PrimitiveTopology topologie, VertexFormat format)
    {
        return new BufferBuilder(speicher, topologie, format);
    }

    /**
     * Schickt ein fertiges Netz ab.
     *
     * @param pipeline  bestimmt Shader, Blend, Tiefe und Cull
     * @param netz      das Ergebnis von {@code bauer.build()}; darf {@code null} sein
     *                  (leeres Netz — dann passiert nichts)
     * @param modelView die Modellansicht fuer diesen Zug
     * @param farbe     der Farbmodulator; {@code null} bedeutet weiss und undurchsichtig
     * @param textur    Texturbindung, oder {@code null} fuer Pipelines ohne Sampler
     */
    public static void zeichnen(RenderPipeline pipeline, MeshData netz, Matrix4f modelView, Vector4f farbe, TextureSetup textur)
    {
        if (netz == null)
        {
            return;
        }

        try (MeshData zuSchliessen = netz)
        {
            MeshData.DrawState zustand = zuSchliessen.drawState();

            if (zustand.vertexCount() <= 0)
            {
                return;
            }

            GpuBuffer ecken = RenderSystem.getDevice().createBuffer(
                () -> "bbs-sofort-ecken", GpuBuffer.USAGE_VERTEX, zuSchliessen.vertexBuffer());

            try
            {
                zeichneMitPuffern(pipeline, zustand, zuSchliessen.indexBuffer(), ecken, modelView, farbe, textur);
            }
            finally
            {
                ecken.close();
            }
        }
    }

    private static void zeichneMitPuffern(RenderPipeline pipeline, MeshData.DrawState zustand, ByteBuffer eigeneIndizes,
        GpuBuffer ecken, Matrix4f modelView, Vector4f farbe, TextureSetup textur)
    {
        GpuBuffer indizes;
        boolean eigen = eigeneIndizes != null;

        if (eigen)
        {
            /* Sortierte Durchsichtigkeit liefert eigene Indizes mit — die haben eine
             * Reihenfolge, die der geteilte Puffer nicht kennt. */
            indizes = RenderSystem.getDevice().createBuffer(
                () -> "bbs-sofort-indizes", GpuBuffer.USAGE_INDEX, eigeneIndizes);
        }
        else
        {
            /* Der Normalfall: Vierecke und Dreiecke haben eine feste Indexfolge, die sich
             * alle teilen. Mojang haelt genau dafuer einen wachsenden Puffer bereit. */
            indizes = RenderSystem.getSequentialBuffer(zustand.primitiveTopology()).getBuffer(zustand.indexCount());
        }

        RenderTarget ziel = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuBufferSlice transformationen = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, farbe == null ? new Vector4f(1F, 1F, 1F, 1F) : farbe);

        try (RenderPass durchgang = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> "bbs-sofort", ziel.getColorTextureView(), Optional.empty(),
            ziel.getDepthTextureView(), OptionalDouble.empty()))
        {
            durchgang.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(durchgang);
            durchgang.setUniform("DynamicTransforms", transformationen);

            if (textur != null)
            {
                durchgang.bindTexture("Sampler0", textur.texure0(), textur.sampler0());
            }

            durchgang.setVertexBuffer(0, ecken.slice());
            durchgang.setIndexBuffer(indizes, zustand.indexType());

            /* Reihenfolge (Indexzahl, Instanzen, erster Index, Eckenversatz, erste Instanz)
             * — abgelesen an Vanillas SkyRenderer, CubeMap und CloudRenderer, die alle
             * drawIndexed(n, 1, 0, 0, 0) rufen. javap traegt keine Parameternamen, und die
             * Verwechslung zeichnet lautlos nichts. */
            durchgang.drawIndexed(zustand.indexCount(), 1, 0, 0, 0);
        }
        finally
        {
            /* Nur eigene Indexpuffer gehoeren uns — der geteilte lebt weiter. */
            if (eigen)
            {
                indizes.close();
            }
        }
    }

    private SofortZeichner()
    {}
}
