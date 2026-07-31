package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Ein Netz, das auf der Grafikkarte LIEGENBLEIBT und mehrfach gezeichnet werden kann.
 *
 * <h2>Warum es das getrennt von {@link SofortZeichner} gibt</h2>
 *
 * <p>Der Sofortzeichner verbraucht sein Netz: hochladen, zeichnen, freigeben. Fuer den
 * groessten Teil von BBS ist das richtig. Die Zwei-Pass-Durchsichtigkeit braucht aber das
 * Gegenteil — dieselbe Geometrie wird ZWEIMAL gezeichnet, einmal sofort (die
 * undurchsichtigen Texel, mit Tiefenschreiben) und einmal am Bildende, wenn die
 * durchsichtige Warteschlange sortiert abgearbeitet wird. Bis dahin muss sie liegenbleiben.
 *
 * <p>Genau das war frueher {@code VertexBuffer}: aufbauen, behalten, mehrfach
 * {@code drawWithShader}. Die Klasse gibt es in 26.2 nicht mehr, die Aufgabe schon.
 *
 * <h2>Wem das Netz gehoert</h2>
 *
 * <p>Dem Aufrufer. Wer es anlegt, ruft {@link #close()} — bei der Warteschlange ist das
 * {@code release()} nach dem Leeren. Wird es vergessen, bleibt der Speicher auf der
 * Grafikkarte liegen, solange das Spiel laeuft; einen Sammler dafuer gibt es nicht.
 */
public class GehaltenesNetz implements AutoCloseable
{
    private GpuBuffer ecken;

    /**
     * Eigene Indizes gibt es nur bei sortierter Durchsichtigkeit — dort bestimmt die
     * Reihenfolge das Bild. Sonst bleibt das Feld leer und es wird Mojangs geteilter
     * Indexpuffer benutzt, der fuer Vierecke und Dreiecke immer dieselbe Folge hat.
     */
    private GpuBuffer eigeneIndizes;

    private final MeshData.DrawState zustand;

    public GehaltenesNetz(MeshData netz)
    {
        try (MeshData zuSchliessen = netz)
        {
            this.zustand = zuSchliessen.drawState();
            this.ecken = RenderSystem.getDevice().createBuffer(
                () -> "bbs-gehaltene-ecken", GpuBuffer.USAGE_VERTEX, zuSchliessen.vertexBuffer());

            ByteBuffer indizes = zuSchliessen.indexBuffer();

            if (indizes != null)
            {
                this.eigeneIndizes = RenderSystem.getDevice().createBuffer(
                    () -> "bbs-gehaltene-indizes", GpuBuffer.USAGE_INDEX, indizes);
            }
        }
    }

    public int eckenZahl()
    {
        return this.zustand.vertexCount();
    }

    /**
     * Zeichnet das Netz in einem eigenen Durchgang.
     *
     * @param pipeline  bestimmt Shader, Blend, Tiefe und Cull -- in 26.2 ist das der Ort,
     *                  an dem frueher depthMask/enableCull/disableCull standen
     * @param modelView die Modellansicht dieses Zuges (bei aufgeschobenen Zuegen die
     *                  EINGEFANGENE, nicht die gerade aktuelle)
     * @param farbe     Farbmodulator, {@code null} = weiss und undurchsichtig
     * @param textur    Texturbindung, {@code null} fuer Pipelines ohne Sampler
     * @param ziel      Renderziel, {@code null} fuer das Hauptbild
     * @param zusatz    optionale weitere Bindungen (etwa BBS' eigener Modell-Block mit
     *                  Normalenmatrix und Durchgangsart), oder {@code null}
     */
    public void zeichnen(RenderPipeline pipeline, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, RenderTarget ziel, Consumer<RenderPass> zusatz)
    {
        RenderTarget echtes = ziel != null ? ziel : Minecraft.getInstance().gameRenderer.mainRenderTarget();

        this.zeichnen(pipeline, modelView, farbe, textur,
            echtes.getColorTextureView(), echtes.getDepthTextureView(), zusatz);
    }

    /**
     * Wie oben, aber mit den Zieltexturen statt eines {@link RenderTarget}.
     *
     * <p>Gebraucht fuer BBS' EIGENE Puffer: der Auswahl-Durchgang und die Kugel-Maske des
     * Gizmos zeichnen in einen BBS-Framebuffer, der kein Vanilla-RenderTarget ist. Ohne
     * diesen Weg landeten ihre Kennungsfarben sichtbar im Bild UND das Angeklickte waere
     * nicht mehr auffindbar -- ein doppelter stiller Fehler.
     *
     * @param tiefenZiel darf {@code null} sein: ein Durchgang ohne Tiefenanhang. Richtig
     *                   fuer Masken, die ohnehin ohne Tiefentest zeichnen; wer Tiefe
     *                   braucht und {@code null} uebergibt, bekommt keine Fehlermeldung,
     *                   sondern ein Bild ohne Verdeckung.
     */
    public void zeichnen(RenderPipeline pipeline, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, GpuTextureView farbZiel, GpuTextureView tiefenZiel, Consumer<RenderPass> zusatz)
    {
        if (this.ecken == null || this.zustand.vertexCount() <= 0)
        {
            return;
        }

        GpuBuffer indizes = this.eigeneIndizes != null
            ? this.eigeneIndizes
            : RenderSystem.getSequentialBuffer(this.zustand.primitiveTopology()).getBuffer(this.zustand.indexCount());

        GpuBufferSlice transformationen = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, farbe == null ? new Vector4f(1F, 1F, 1F, 1F) : farbe);

        /* Der Ausschnitt gehoert in 26.2 zum Durchgang, nicht mehr in einen globalen
         * viewport-Aufruf. null heisst ganzes Ziel; siehe Zeichenbereich. */
        RenderPass.RenderArea bereich = Zeichenbereich.aktiv();

        try (RenderPass durchgang = tiefenZiel == null
            ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "bbs-gehalten", farbZiel, Optional.empty())
            : bereich == null
                ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "bbs-gehalten", farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty())
                : RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "bbs-gehalten", farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty(), bereich))
        {
            durchgang.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(durchgang);
            durchgang.setUniform("DynamicTransforms", transformationen);

            if (textur != null)
            {
                bindeWennDa(durchgang, "Sampler0", textur.texure0(), textur.sampler0());
                bindeWennDa(durchgang, "Sampler1", textur.texure1(), textur.sampler1());
                bindeWennDa(durchgang, "Sampler2", textur.texure2(), textur.sampler2());
            }

            if (zusatz != null)
            {
                zusatz.accept(durchgang);
            }

            durchgang.setVertexBuffer(0, this.ecken.slice());
            durchgang.setIndexBuffer(indizes, this.zustand.indexType());
            durchgang.drawIndexed(this.zustand.indexCount(), 1, 0, 0, 0);
        }
    }

    private static void bindeWennDa(RenderPass durchgang, String name, GpuTextureView ansicht, GpuSampler abtaster)
    {
        if (ansicht != null)
        {
            durchgang.bindTexture(name, ansicht, abtaster);
        }
    }

    @Override
    public void close()
    {
        if (this.ecken != null)
        {
            this.ecken.close();
            this.ecken = null;
        }

        if (this.eigeneIndizes != null)
        {
            this.eigeneIndizes.close();
            this.eigeneIndizes = null;
        }
    }
}
