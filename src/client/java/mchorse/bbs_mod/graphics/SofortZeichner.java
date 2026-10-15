package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

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
 * <p>Der Vulkan-Weltweg benutzt den vorhandenen Durchgang bei identischen Anhaengen.
 * Ausserhalb davon wird ein eigener Durchgang geoeffnet. GL kann aufeinanderfolgende
 * Zuege weiterhin ueber {@link Sammelpass} sammeln. Die Geometrie gehoert dabei nur
 * zum aktuellen RenderPearl-Submit.
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
        zeichnen(pipeline, netz, modelView, farbe, textur, null);
    }

    /**
     * Wie oben, aber in ein anderes Ziel als das Hauptbild.
     *
     * <p>Gebraucht fuer Durchgaenge, die NICHT auf den Bildschirm gehen: der Pick-Puffer
     * etwa bekommt Kennungsfarben statt sichtbarer Geometrie. Mit fest verdrahtetem
     * Hauptziel waere das ein doppelter stiller Fehler — die Kennungen landeten sichtbar
     * im Bild, und das Angeklickte waere nicht mehr auffindbar. Eine Pruefung der Welle
     * hat genau deshalb vier Stellen offen gelassen, statt sie umzustellen.
     *
     * @param ziel das Render-Ziel, oder {@code null} fuer das Hauptbild
     */
    public static void zeichnen(RenderPipeline pipeline, MeshData netz, Matrix4f modelView, Vector4f farbe, TextureSetup textur, RenderTarget ziel)
    {
        zeichnen(pipeline, netz, modelView, farbe, textur, ziel, null);
    }

    /** Additional bindings belong to this draw, including the model uniform's actual transient slice. */
    public static void zeichnen(RenderPipeline pipeline, MeshData netz, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, RenderTarget ziel, Consumer<RenderPass> zusatz)
    {
        if (netz == null)
        {
            return;
        }

        /* Sofort heisst hier: hochladen, einmal zeichnen, sofort wieder freigeben. Der
         * Zeichenweg selbst ist derselbe wie beim gehaltenen Netz -- er steht dort einmal
         * und nicht zweimal, damit ein spaeterer Eingriff (Sampler, Ziel, Uniform-Block)
         * nicht an einer der beiden Kopien vorbeigeht. */
        try (GehaltenesNetz kurzlebig = GehaltenesNetz.kurzlebig(netz))
        {
            kurzlebig.zeichnen(pipeline, modelView, farbe, textur, ziel, zusatz);
        }
    }

    /**
     * Wie oben, aber mit den Zieltexturen statt eines {@link RenderTarget}.
     *
     * <p>Fuer BBS' eigene Puffer, die kein Vanilla-RenderTarget sind -- vor allem der
     * Auswahl-Durchgang (siehe {@link PickZiel}).
     */
    public static void zeichnen(RenderPipeline pipeline, MeshData netz, Matrix4f modelView, Vector4f farbe,
        TextureSetup textur, com.mojang.renderpearl.api.textures.GpuTextureView farbZiel,
        com.mojang.renderpearl.api.textures.GpuTextureView tiefenZiel)
    {
        if (netz == null)
        {
            return;
        }

        try (GehaltenesNetz kurzlebig = GehaltenesNetz.kurzlebig(netz))
        {
            kurzlebig.zeichnen(pipeline, modelView, farbe, textur, farbZiel, tiefenZiel, null);
        }
    }

    /**
     * Baut die Texturbindung fuer Vanillas Entity-Weg: BBS-Textur, Overlay und Lichtkarte.
     *
     * <p>Das ersetzt {@code setupOverlayColor()} und {@code turnOnLightLayer()}, die ihren
     * Zustand frueher global setzten. In 26.2 gehoert er zum Zeichenaufruf.
     *
     * <p>⚠️ Die BBS-Textur liefert ihre eigene Ansicht seit dem Umbau auf GpuTexture — es
     * braucht dafuer keine Umwege ueber rohe GL-Nummern mehr.
     */
    public static TextureSetup entityTextur(mchorse.bbs_mod.graphics.texture.Texture textur)
    {
        return TextureSetup.singleTextureWithLightmap(textur.view(), textur.sampler());
    }

    public static TextureSetup modellTextur(mchorse.bbs_mod.graphics.texture.Texture textur)
    {
        net.minecraft.client.renderer.GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
        return new TextureSetup(textur.view(), renderer.overlayTexture().getTextureView(), renderer.lightmap(),
            textur.sampler(), linear, linear);
    }

    private SofortZeichner()
    {}
}
