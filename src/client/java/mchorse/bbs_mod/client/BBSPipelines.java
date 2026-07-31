package mchorse.bbs_mod.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * BBS' eigene Render-Pipelines fuer die Oberflaeche.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>Bis 1.21.1 war ein eigener Shader ein {@code RenderPipeline}, den man baute,
 * dessen Uniforms man von Hand setzte und den man {@code RenderSystem.setShader}
 * ueberreichte. In 26.2 gibt es das nicht mehr: ein Shader ist Teil einer
 * {@link RenderPipeline}, die Blend-, Tiefen- und Vertexzustand gleich mitbringt.
 * Gewaehlt wird eine Pipeline, nicht mehr zusammengesetzt.
 *
 * <h2>⛔ EIN GUI-ELEMENT HAT KEINE EIGENEN UNIFORMS — gemessen, nicht vermutet</h2>
 *
 * <p>{@code GuiElementRenderState} kennt genau vier Methoden: {@code buildVertices},
 * {@code pipeline}, {@code textureSetup}, {@code scissorArea}. Es gibt keinen Haken,
 * ueber den ein Element einen eigenen Uniform-Puffer binden koennte, und
 * {@code GuiRenderer} ruft {@code setUniform} genau einmal — fuer seine eigene
 * Projektion.
 *
 * <p>Wer trotzdem einen Wert in einen GUI-Shader braucht, macht es wie Vanilla:
 * <b>er kodiert ihn in die Vertexfarbe</b>. Vanillas Uebelkeits-Ueberlagerung
 * uebergibt ihre Staerke genauso. Der andere Weg waere ein
 * {@code PictureInPictureRenderer} mit eigenem Render-Pass — richtig fuer ganze
 * Szenen (Entity, Buch, Skin), viel zu schwer fuer zwei Zahlen.
 *
 * <h2>Warum von GUI_TEXTURED geerbt wird</h2>
 *
 * <p>Der {@code GuiRenderer} sammelt alle Elemente eines Bildes in EINEN
 * Vertexpuffer. Eine Pipeline mit abweichendem Vertexformat oder abweichender
 * Topologie passt dort nicht hinein. Statt die Werte abzuschreiben — und beim
 * naechsten Mojang-Update still falsch zu liegen — werden sie zur Laufzeit von
 * {@code RenderPipelines.GUI_TEXTURED} uebernommen. Nur die beiden Shader werden
 * ausgetauscht.
 */
public final class BBSPipelines
{
    private static final RenderPipeline SELECTION = guiPipelineMit("selection");

    /* Die uebrigen BBS-Shader, die ueber Batcher2D in die Oberflaeche zeichnen. Alle nach
     * demselben Muster: Vanillas GUI-Zustand erben, nur die beiden Shader tauschen.
     *
     * ⚠️ Ihre frueheren Einzel-Uniforms (Blur/TextureSize, Size/Filters, Target/
     * HighlightColor, Thickness/OutlineColor/…) haben in der GUI keinen Ort mehr --
     * GuiElementRenderState kann keinen Uniform-Block binden. Sie reisen wie bei
     * selection in der Vertexfarbe mit; welcher Wert in welchem Kanal steckt, steht im
     * jeweiligen Fragmentshader UND an der Aufrufstelle. */
    private static final RenderPipeline SUBTITLES = guiPipelineMit("subtitles");
    /** Der Name des eigenen Blocks der Filter-Pipelines. Muss zum layout(std140) im Shader passen. */
    public static final String FILTER_BLOCK = "FilterInfo";

    /**
     * Bindegruppe der Filter-Pipelines: ein eigener Uniform-Block plus die Quelltextur.
     *
     * <p>Genau das, was ein GUI-Element NICHT haben kann -- darum laufen die Filter auch
     * nicht durch die Oberflaeche, sondern vorher in eine eigene Textur (siehe Bildfilter).
     */
    private static final BindGroupLayout GRUPPE_FILTER = BindGroupLayout.builder()
        .withUniform(FILTER_BLOCK, UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0")
        .build();

    /**
     * Wie {@link #GRUPPE_FILTER}, aber mit zwei Quelltexturen -- multilink liest zusaetzlich
     * das Symbol-Atlasbild fuer das Radiergummi-Muster.
     *
     * <p>Zwei Gruppen statt einer mit optionalem zweiten Sampler: eine Pipeline mit einer
     * Bindung, die niemand fuellt, zeichnet nicht falsch, sondern GAR NICHT.
     */
    private static final BindGroupLayout GRUPPE_FILTER2 = BindGroupLayout.builder()
        .withUniform(FILTER_BLOCK, UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0")
        .withSampler("Sampler1")
        .build();

    private static final RenderPipeline PICKER_PREVIEW = filterPipeline("picker_preview", GRUPPE_FILTER);
    private static final RenderPipeline REPLAY_OUTLINE = filterPipeline("replay_outline", GRUPPE_FILTER);

    /* Nach den Bindegruppen, nicht davor: statische Felder werden der Reihe nach
     * ausgewertet, und ein Vorgriff waere hier null statt eines Fehlers. */
    private static final RenderPipeline MULTILINK = filterPipeline("multilink", GRUPPE_FILTER2);

    /**
     * Der Auswahlrahmen des Textur-Editors (laufende Ameisen um die Auswahl).
     *
     * <p>Erwartet in der Vertexfarbe: R/G = Skalierung als 16-Bit-Festkomma
     * (Bildschirmpixel je Dokumenttexel, ×256), B = Phase (0 oder 255).
     * Gebaut wird sie von {@code UIPixelsEditor.auswahlFarbe}.
     */
    public static RenderPipeline getSelection()
    {
        return SELECTION;
    }

    /** Untertitel-Blit im Filmfenster. Erwartet Weichzeichnung und Texturgroesse in der Vertexfarbe. */
    public static RenderPipeline getSubtitles()
    {
        return SUBTITLES;
    }

    /** Der Mehrfach-Verweis-Editor. */
    public static RenderPipeline getMultilink()
    {
        return MULTILINK;
    }

    /** Vorschaubild des Auswahl-Durchgangs (Gizmo, Formen-Editor, Modellblock-Panel). */
    public static RenderPipeline getPickerPreview()
    {
        return PICKER_PREVIEW;
    }

    /** Umriss um das bearbeitete Replay -- liest die Kante aus demselben Stencil-Bild. */
    public static RenderPipeline getReplayOutline()
    {
        return REPLAY_OUTLINE;
    }

    /**
     * Eine GUI-Pipeline, die alles von Vanillas texturiertem GUI erbt und nur die
     * beiden Shader ersetzt.
     *
     * <p>Der Name loest zu {@code assets/bbs/shaders/core/<name>.vsh} bzw. {@code .fsh}
     * auf. Die alte {@code ProxyResourceFactory}, die BBS-Shader in den
     * minecraft-Namensraum umleitete, wird damit ueberfluessig: 26.2 nimmt einen
     * vollen {@link Identifier} entgegen.
     */
    /**
     * Eine Pipeline fuer einen Filterdurchgang: bildfuellendes Viereck, kein Tiefentest,
     * eigener Uniform-Block.
     *
     * <p>Sie zeichnet NICHT in die Oberflaeche, sondern in eine Textur, die die Oberflaeche
     * danach ganz normal blittet. Deshalb darf sie haben, was ein GUI-Element nicht haben
     * kann.
     */
    private static RenderPipeline filterPipeline(String name, BindGroupLayout gruppe)
    {
        Identifier shader = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "core/" + name);

        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(gruppe)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    }

    private static RenderPipeline guiPipelineMit(String name)
    {
        RenderPipeline vorlage = RenderPipelines.GUI_TEXTURED;
        Identifier shader = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "core/" + name);

        RenderPipeline.Builder bauer = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(shader)
            .withFragmentShader(shader)
            .withVertexBinding(0, vorlage.getVertexFormatBinding(0))
            .withPrimitiveTopology(vorlage.getPrimitiveTopology())
            .withDepthStencilState(vorlage.getDepthStencilState())
            .withColorTargetState(vorlage.getColorTargetState())
            .withCull(vorlage.isCull());

        /* Die Bindegruppen bestimmen, welche Uniform-Bloecke und Sampler der Shader
         * ueberhaupt sehen darf. Uebernommen statt neu aufgezaehlt, sonst weicht die
         * Pipeline von dem ab, was der GuiRenderer tatsaechlich bindet. */
        for (BindGroupLayout gruppe : vorlage.getBindGroupLayouts())
        {
            bauer.withBindGroupLayout(gruppe);
        }

        return bauer.build();
    }

    private BBSPipelines()
    {}
}
