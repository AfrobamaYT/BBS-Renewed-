package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/**
 * Der Zeichenweg von BBS' Modellen, uebersetzt auf 26.2.
 *
 * <h2>Warum diese Klasse der Flaschenhals des ganzen Ports ist</h2>
 *
 * <p>Drei unabhaengige Pruefungen des Zustands-Blocks kamen auf denselben Befund: BBS
 * zeichnet seine Modelle NICHT ueber einen {@code RenderType}, sondern ueber einen
 * eigenen VAO plus {@code RenderPipeline}. In 26.2 traegt die {@link RenderPipeline}
 * den Zustand — und wo es keine Pipeline gibt, gibt es keinen Ort fuer Blend, Tiefe
 * und Cull. Darum blieben 22 Zustandsstellen offen: sie haengen alle an DIESER
 * Entscheidung, nicht an 22 einzelnen.
 *
 * <h2>Was sich aendert und was bleibt</h2>
 *
 * <p>BBS legt seine Vertexdaten in FUENF getrennten Puffern ab (Position, Normale,
 * Textur-UV, Tangenten, Mittel-UV) statt sie zu verschraenken. Das ueberlebt den Port
 * unveraendert: 26.2 kennt mehrere Vertex-Bindungen
 * ({@code RenderPipeline.Builder#withVertexBinding(int, VertexFormat)} und
 * {@code RenderPass#setVertexBuffer(int, GpuBufferSlice)}). Jeder Puffer bekommt hier
 * sein eigenes Format und seine eigene Bindungsnummer.
 *
 * <p>⛔ WAS NICHT UEBERLEBT: die konstanten Vertexattribute. Der alte Code setzte Farbe,
 * Licht und Overlay pro Zeichenaufruf ueber {@code glVertexAttrib4f} bzw.
 * {@code glVertexAttribI2i} — ein Attribut ohne Puffer, fuer alle Ecken gleich. Dieses
 * Mittel gibt es in 26.2 nicht mehr. Die drei Werte sind pro Zeichenaufruf konstant,
 * also gehoeren sie in einen Uniform-Block ({@link #MODELL_BLOCK}), und der
 * Vertex-Shader liest sie von dort statt aus einem {@code in}.
 *
 * <h2>Woher die uebrigen Werte kommen</h2>
 *
 * <p>So viel wie moeglich aus Vanillas fertigen Bindegruppen, statt einen eigenen
 * Block aufzublaehen:
 * <ul>
 *   <li>{@code ModelViewMat} und {@code ColorModulator} aus {@code DYNAMIC_TRANSFORMS}.
 *       BBS setzt eine EIGENE Modellansicht (die zum Zeitpunkt des Einreihens
 *       eingefangene, siehe {@code ModelVAORenderer#captureModelView}) — dafuer gibt es
 *       {@code RenderSystem.getDynamicUniforms().writeTransform(Matrix4f, Vector4f)},
 *       das genau diesen Block fuer einen Zeichenaufruf fuellt.</li>
 *   <li>{@code ProjMat} aus {@code PROJECTION}.</li>
 *   <li>Die Lichtrichtungen aus {@code LIGHTING}, der Nebel aus {@code FOG}.</li>
 *   <li>Nur was BBS eigen ist — Normalenmatrix, Farbe, Licht, Overlay, Durchgangsart —
 *       steht im eigenen Block.</li>
 * </ul>
 *
 * <p>⚠️ Die Reihenfolge der Bindegruppen ist Teil des Vertrags mit dem Shader. Wer hier
 * eine einfuegt, muss die GLSL-Dateien mitziehen; der Compiler sieht das nicht.
 */
public final class ModelVAOPipeline
{
    /** Name des eigenen Uniform-Blocks. Muss wortgleich im GLSL stehen. */
    public static final String MODELL_BLOCK = "ModelInfo";

    /**
     * Die Vertex-Bindungen, eine je Puffer. Die Nummern sind der Vertrag zwischen
     * {@link ModelVAO} (wer welchen Puffer bindet) und dieser Pipeline (welches Format
     * an welcher Nummer erwartet wird).
     */
    public static final int BINDUNG_POSITION = 0;
    public static final int BINDUNG_NORMALE = 1;
    public static final int BINDUNG_UV0 = 2;

    /** {@code in vec3 Position} — drei Fliesskommazahlen, dicht gepackt. */
    public static final VertexFormat FORMAT_POSITION = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .build();

    /** {@code in vec3 Normal} */
    public static final VertexFormat FORMAT_NORMALE = VertexFormat.builder(0)
        .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
        .build();

    /** {@code in vec2 UV0} */
    public static final VertexFormat FORMAT_UV0 = VertexFormat.builder(0)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .build();

    /**
     * Der eigene Block: alles, was Vanillas Bindegruppen nicht abdecken.
     *
     * <p>Sampler0 ist die Modelltextur. Sampler1 (Overlay) und Sampler2 (Lichtkarte)
     * werden vom alten Shader per {@code texelFetch} gelesen; sie bleiben Sampler,
     * waehrend ihre Koordinaten (frueher die konstanten Attribute UV1/UV2) in den
     * Uniform-Block wandern.
     */
    public static final BindGroupLayout GRUPPE_MODELL = BindGroupLayout.builder()
        .withUniform(MODELL_BLOCK, UniformType.UNIFORM_BUFFER)
        .withSampler("Sampler0")
        .withSampler("Sampler1")
        .withSampler("Sampler2")
        .build();

    /** Der uebliche Durchgang: durchsichtig gemischt, Tiefe gelesen und geschrieben. */
    public static final RenderPipeline DURCHSICHTIG = bauen("model", true, true, true);

    /** Wie {@link #DURCHSICHTIG}, aber beidseitig — fuer Modelle mit {@code isCulling() == false}. */
    public static final RenderPipeline DURCHSICHTIG_BEIDSEITIG = bauen("model", true, false, true);

    /**
     * Durchsichtig, aber OHNE Tiefenschreiben — der zweite Durchgang der
     * Zwei-Pass-Durchsichtigkeit.
     *
     * <p>Die sortierte Warteschlange zeichnet halbdurchsichtige Teile von hinten nach
     * vorn und darf dabei keine Tiefe schreiben, sonst verdeckt das erste gezeichnete
     * Teil alle dahinterliegenden. Frueher schaltete {@code depthMask(false)} das pro
     * Zeichenbefehl um; in 26.2 ist es eine Eigenschaft der Pipeline, also braucht es
     * eine zweite Konstante statt eines Schalters.
     */
    public static final RenderPipeline DURCHSICHTIG_OHNE_TIEFENSCHREIBEN = bauen("model", true, true, false);

    /** Wie {@link #DURCHSICHTIG_OHNE_TIEFENSCHREIBEN}, aber beidseitig. */
    public static final RenderPipeline DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG = bauen("model", true, false, false);

    /** Ohne Mischung: der Iris-Cutout-Fall, der frueher {@code disableBlend} rief. */
    public static final RenderPipeline UNDURCHSICHTIG = bauen("model", false, true, true);

    /*
     * Die Auswahl-Durchgaenge (Picking): dieselben Vertexbindungen und derselbe
     * Uniform-Block wie beim Modellweg, nur mit einem anderen Fragmentshader, der statt
     * Farbe eine Kennung schreibt.
     *
     * ⚠️ OHNE MISCHUNG, das ist hier kein Geschmack: Kennungsfarben sind Zahlen, keine
     * Farben. Werden sie gemischt, entsteht aus zwei Nachbarn eine dritte Kennung, die
     * auf nichts zeigt -- der Klick trifft dann ein Objekt, das es nicht gibt.
     */
    public static final RenderPipeline PICKER_MODELLE = bauen("picker_models", false, true, true);
    public static final RenderPipeline PICKER_BILLBOARD = bauen("picker_billboard", false, true, true);
    public static final RenderPipeline PICKER_BILLBOARD_OHNE_SCHATTIERUNG = bauen("picker_billboard_no_shading", false, true, true);
    public static final RenderPipeline PICKER_PARTIKEL = bauen("picker_particles", false, true, true);

    /**
     * Ob eine Pipeline zum BBS-Modellweg gehoert — und damit die Durchgangsart
     * ({@code Modus.x} im Uniform-Block) ueberhaupt auswertet.
     *
     * <p>Bis 1.21.1 fragte der Code {@code shader.getUniform("PassMode") != null}: nur
     * BBS' eigener Modell-Shader hatte dieses Uniform, Vanilla- und Iris-Programme nicht.
     * Genau diese Unterscheidung braucht die Durchsichtigkeits-Warteschlange weiterhin —
     * sie darf nur dort in zwei Durchgaenge aufteilen, wo der Shader das auch umsetzt.
     *
     * <p>In 26.2 gibt es keine Einzel-Uniforms mehr, nach denen man fragen koennte. Die
     * Frage lautet jetzt: ist es eine unserer Modell-Pipelines? Wer hier eine neue
     * Konstante ergaenzt, muss sie mit aufnehmen — sonst faellt sie still aus der
     * Zwei-Pass-Durchsichtigkeit heraus und halbdurchsichtige Modelle verlieren ihre
     * Sortierung.
     */
    public static boolean istModellPipeline(RenderPipeline pipeline)
    {
        return pipeline == DURCHSICHTIG
            || pipeline == DURCHSICHTIG_BEIDSEITIG
            || pipeline == DURCHSICHTIG_OHNE_TIEFENSCHREIBEN
            || pipeline == DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG
            || pipeline == UNDURCHSICHTIG;
    }

    /**
     * Waehlt die Modell-Pipeline nach den beiden Schaltern, die der alte Code pro
     * Zeichenbefehl umlegte.
     *
     * <p>Das ersetzt {@code enableCull()/disableCull()} und {@code depthMask(...)}: was
     * frueher ein Zustandswechsel war, ist jetzt die Wahl einer von vier Konstanten.
     */
    public static RenderPipeline waehlen(boolean culling, boolean tiefeSchreiben)
    {
        if (tiefeSchreiben)
        {
            return culling ? DURCHSICHTIG : DURCHSICHTIG_BEIDSEITIG;
        }

        return culling ? DURCHSICHTIG_OHNE_TIEFENSCHREIBEN : DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG;
    }

    private static RenderPipeline bauen(String shader, boolean mischen, boolean culling, boolean tiefeSchreiben)
    {
        Identifier datei = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "core/" + shader);
        String name = shader + (mischen ? "_translucent" : "_opaque")
            + (culling ? "" : "_no_cull") + (tiefeSchreiben ? "" : "_no_depth_write");

        RenderPipeline.Builder bauer = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(datei)
            .withFragmentShader(datei)
            .withVertexBinding(BINDUNG_POSITION, FORMAT_POSITION)
            .withVertexBinding(BINDUNG_NORMALE, FORMAT_NORMALE)
            .withVertexBinding(BINDUNG_UV0, FORMAT_UV0)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(GRUPPE_MODELL)
            .withCull(culling);

        /* ⛔ 26.2 rechnet die Tiefe UMGEKEHRT (reversed-Z): naeher heisst groesserer Wert,
         * der Tiefenpuffer wird auf 0.0 geloescht, und DepthStencilState.DEFAULT traegt
         * GREATER_THAN_OR_EQUAL. Der alte Code rief hier GL_LEQUAL — das uebersetzt sich
         * NICHT zu LESS_THAN_OR_EQUAL, sondern zum Gegenstueck. Wer das mechanisch
         * uebersetzt, dreht den Tiefentest um und alles verschwindet hinter allem. */
        bauer.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, tiefeSchreiben));

        /* DEFAULT ist der ungemischte Zustand — kein `new ColorTargetState(null)`, dessen
         * Konstruktor die Mischfunktion in ein Optional packt und an null scheitern wuerde. */
        return bauer.withColorTargetState(mischen
            ? new ColorTargetState(BlendFunction.TRANSLUCENT)
            : ColorTargetState.DEFAULT).build();
    }

    private ModelVAOPipeline()
    {}
}
