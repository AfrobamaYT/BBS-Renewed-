package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.compat.alt.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
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
    private static final VertexFormat FORMAT_MODELL_VERSCHRAENKT = eigenesModellformat();

    private static VertexFormat eigenesModellformat()
    {
        //? if >=26.1 {
        /*/^ BBS packs the original entity fields, independent of shader-engine identity hooks. ^/
        mchorse.bbs_mod.compat.alt.VertexFormats.Builder builder = mchorse.bbs_mod.compat.alt.VertexFormats.builder(DefaultVertexFormat.NEW_ENTITY.getStepRate());
        java.util.List<com.mojang.blaze3d.vertex.VertexFormatElement> elements = DefaultVertexFormat.NEW_ENTITY.getElements();
        for (int index = 0; index < elements.size(); index++)
        {
            com.mojang.blaze3d.vertex.VertexFormatElement element = elements.get(index);
            int end = index + 1 < elements.size() ? elements.get(index + 1).offset() : DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            builder.addAttribute(element.name(), element.offset(), end - element.offset(), element.format(), 1);
        }
        VertexFormat format = builder.build();
        if (format == DefaultVertexFormat.NEW_ENTITY || !format.equals(DefaultVertexFormat.NEW_ENTITY))
            throw new IllegalStateException("BBS model format does not preserve the actual entity layout");
        return format;
        *///?} else {
        /** Vor 26.1 hat VertexFormat keine Attributnamen/-versaetze zum Nachbauen; das verschraenkte
         * Modellformat ist dort Mojangs Entity-Format selbst (dieselben Felder, dieselbe Folge). **/
        return DefaultVertexFormat.NEW_ENTITY;
        //?}
    }

    /**
     * Die Vertex-Bindungen, eine je Puffer. Die Nummern sind der Vertrag zwischen
     * {@link ModelVAO} (wer welchen Puffer bindet) und dieser Pipeline (welches Format
     * an welcher Nummer erwartet wird).
     */
    public static final int BINDUNG_POSITION = 0;
    public static final int BINDUNG_NORMALE = 1;
    public static final int BINDUNG_UV0 = 2;
    public static final int BINDUNG_LICHT = 3;
    public static final int BINDUNG_JOINTS0 = 4;
    public static final int BINDUNG_WEIGHTS0 = 5;
    public static final int BINDUNG_JOINTS1 = 6;
    public static final int BINDUNG_WEIGHTS1 = 7;

    private static final VertexFormat FORMAT_JOINTS0 = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("SkinJoints0", GpuFormat.RGBA32_SINT).build();
    private static final VertexFormat FORMAT_WEIGHTS0 = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("SkinWeights0", GpuFormat.RGBA32_FLOAT).build();
    private static final VertexFormat FORMAT_JOINTS1 = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("SkinJoints1", GpuFormat.RGBA32_SINT).build();
    private static final VertexFormat FORMAT_WEIGHTS1 = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("SkinWeights1", GpuFormat.RGBA32_FLOAT).build();

    /** {@code in vec3 Position} — drei Fliesskommazahlen, dicht gepackt. */
    public static final VertexFormat FORMAT_POSITION = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .build();

    /** {@code in vec3 Normal} */
    public static final VertexFormat FORMAT_NORMALE = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
        .build();

    /** {@code in vec2 UV0} */
    public static final VertexFormat FORMAT_UV0 = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .build();

    /** {@code flat in ivec2 UV2} — je Ecke der Knochenteil der Pick-Kennung (Stencil-
     *  Durchgang; der Knochen mit dem groessten Gewicht). model.vsh liest das Attribut
     *  nicht (Blaze3D bindet ueber Namen, Ungelesenes ist erlaubt) — nur picker_models
     *  rechnet damit {@code Modus.y + UV2.x}, wie 1.21.1s {@code Target + texCoord2.x}. */
    public static final VertexFormat FORMAT_LICHT = mchorse.bbs_mod.compat.alt.VertexFormats.builder(0)
        .addAttribute("UV2", GpuFormat.RG32_SINT)
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

    private static final BindGroupLayout GRUPPE_SKIN = BindGroupLayout.builder()
        .withUniform("SkinMatrices", UniformType.UNIFORM_BUFFER)
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

    private static final RenderPipeline GPU_DURCHSICHTIG = bauenGpu("model", true, true, true);
    private static final RenderPipeline GPU_DURCHSICHTIG_BEIDSEITIG = bauenGpu("model", true, false, true);
    private static final RenderPipeline GPU_OHNE_TIEFENSCHREIBEN = bauenGpu("model", true, true, false);
    private static final RenderPipeline GPU_OHNE_TIEFENSCHREIBEN_BEIDSEITIG = bauenGpu("model", true, false, false);
    private static final RenderPipeline GPU_UNDURCHSICHTIG = bauenGpu("model", false, true, true);
    private static final RenderPipeline GPU_PICKER_MODELLE = bauenGpu("picker_models", false, true, true);

    /** The GPU path is only valid for BOBJ pipelines with a matching vertex shader. */
    public static RenderPipeline gpuVariante(RenderPipeline normal)
    {
        if (normal == DURCHSICHTIG) return GPU_DURCHSICHTIG;
        if (normal == DURCHSICHTIG_BEIDSEITIG) return GPU_DURCHSICHTIG_BEIDSEITIG;
        if (normal == DURCHSICHTIG_OHNE_TIEFENSCHREIBEN) return GPU_OHNE_TIEFENSCHREIBEN;
        if (normal == DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG) return GPU_OHNE_TIEFENSCHREIBEN_BEIDSEITIG;
        if (normal == UNDURCHSICHTIG) return GPU_UNDURCHSICHTIG;
        if (normal == PICKER_MODELLE) return GPU_PICKER_MODELLE;
        return null;
    }

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
            || pipeline == UNDURCHSICHTIG
            || pipeline == VERSCHRAENKT_DURCHSICHTIG
            || pipeline == VERSCHRAENKT_DURCHSICHTIG_BEIDSEITIG
            || pipeline == VERSCHRAENKT_OHNE_TIEFENSCHREIBEN
            || pipeline == VERSCHRAENKT_OHNE_TIEFENSCHREIBEN_BEIDSEITIG;
    }

    /*
     * ═══ DIE VERSCHRAENKTE FAMILIE: EIN PUFFER STATT DREI ═══
     *
     * BBS hat ZWEI Wege, dieselben Modelle zu zeichnen, und sie liefern ihre Ecken
     * unterschiedlich verpackt:
     *
     *   ModelVAO / BOBJModelVAO  -> DREI getrennte Puffer (Position, Normale, UV0),
     *                               gebunden auf die Nummern 0/1/2. Dafuer sind die
     *                               Pipelines oben gebaut.
     *   SofortZeichner / GehaltenesNetz -> EIN verschraenkter Puffer aus einem
     *                               BufferBuilder im Vanilla-Format ENTITY, gebunden
     *                               auf Nummer 0. Mehr kann MeshData gar nicht liefern.
     *
     * ⛔ WAS PASSIERT, WENN MAN DEN ZWEITEN WEG MIT DER ERSTEN PIPELINE ZEICHNET
     * (genau das war Aufgabe 89, ein Absturz im Grafiktreiber):
     *
     *   - Die Bindungen 1 und 2 werden nie gesetzt. Die Pipeline liest sie trotzdem.
     *   - Schlimmer noch die Bindung 0: FORMAT_POSITION ist 12 Byte breit, ENTITY ist
     *     36. Der Shader nimmt also jede 12. Byte-Gruppe als neue Ecke und liest
     *     dreimal so weit, wie der Puffer reicht.
     *
     * Beides ist ein FORMAL GUELTIGER Zeichenaufruf -- glGetError schweigt, es gibt
     * keine Ausnahme, die JVM ist einfach weg. Dieselbe Fehlerklasse wie Aufgabe 88
     * (dort las eine Textur ueber ihren Puffer hinaus), nur eine Ebene tiefer.
     *
     * ⚠️ Warum EIN Shader fuer beide Familien reicht: Blaze3D bindet Vertexattribute
     * ueber ihren NAMEN, nicht ueber ihre Lage. ENTITY traegt Position, Color, UV0,
     * UV1, UV2 und Normal (nachgemessen mit javap, nicht vermutet) -- die drei, die
     * core/model liest, sind darunter. Die uebrigen bleiben ungelesen, das kostet
     * Bandbreite, aber nichts an Richtigkeit.
     */

    /** Wie {@link #DURCHSICHTIG}, aber fuer EINEN verschraenkten Puffer im ENTITY-Format. */
    public static final RenderPipeline VERSCHRAENKT_DURCHSICHTIG = bauenVerschraenkt("model", true, true, true);

    /** Wie {@link #DURCHSICHTIG_BEIDSEITIG}, verschraenkt. */
    public static final RenderPipeline VERSCHRAENKT_DURCHSICHTIG_BEIDSEITIG = bauenVerschraenkt("model", true, false, true);

    /** Wie {@link #DURCHSICHTIG_OHNE_TIEFENSCHREIBEN}, verschraenkt. */
    public static final RenderPipeline VERSCHRAENKT_OHNE_TIEFENSCHREIBEN = bauenVerschraenkt("model", true, true, false);

    /** Wie {@link #DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG}, verschraenkt. */
    public static final RenderPipeline VERSCHRAENKT_OHNE_TIEFENSCHREIBEN_BEIDSEITIG = bauenVerschraenkt("model", true, false, false);

    /**
     * Waehlt die Pipeline fuer den SOFORT-Weg -- also fuer alles, was ueber
     * {@link mchorse.bbs_mod.graphics.SofortZeichner} oder
     * {@link mchorse.bbs_mod.graphics.GehaltenesNetz} geht.
     *
     * <p>Wer einen BufferBuilder baut, braucht diese Methode. Wer drei eigene Puffer
     * bindet, braucht {@link #waehlen(boolean, boolean)}. Die Verwechslung nimmt die JVM
     * mit, ohne eine einzige Meldung zu hinterlassen.
     */
    public static RenderPipeline waehlenVerschraenkt(boolean culling, boolean tiefeSchreiben)
    {
        /* SOLID-VORSCHAU, verschraenkte Familie. ⛔ Diese Stelle DARF NICHT vergessen werden,
         * wenn waehlen() sie bekommt: ModelInstance.drawImmediate benutzt die Pipeline aus
         * BBSShaders.getModel NUR fuer die Warteschlangen-Frage und zeichnet dann mit dem
         * Ergebnis DIESER Methode. Bliebe sie unveraendert, zeichneten Modelle mit Shape-Keys,
         * Welds oder ohne VAO weiter texturiert, waehrend alle anderen solid werden — ein
         * halbes Feature, das wie ein Fehler aussaehe. */
        if (com.afrobama.bbstools.client.solid.SolidVorschau.aktiv())
        {
            return com.afrobama.bbstools.client.solid.SolidVorschauPipeline.holen(true, culling, tiefeSchreiben);
        }

        if (tiefeSchreiben)
        {
            return culling ? VERSCHRAENKT_DURCHSICHTIG : VERSCHRAENKT_DURCHSICHTIG_BEIDSEITIG;
        }

        return culling ? VERSCHRAENKT_OHNE_TIEFENSCHREIBEN : VERSCHRAENKT_OHNE_TIEFENSCHREIBEN_BEIDSEITIG;
    }

    /**
     * Waehlt die Modell-Pipeline nach den beiden Schaltern, die der alte Code pro
     * Zeichenbefehl umlegte.
     *
     * <p>Das ersetzt {@code enableCull()/disableCull()} und {@code depthMask(...)}: was
     * frueher ein Zustandswechsel war, ist jetzt die Wahl einer von vier Konstanten.
     *
     * <p>⛔ NUR FUER DEN VAO-WEG, also fuer Aufrufer, die DREI eigene Puffer auf die
     * Bindungen 0/1/2 legen ({@link ModelVAO}, {@link BOBJModelVAO}). Wer aus einem
     * BufferBuilder zeichnet, braucht {@link #waehlenVerschraenkt(boolean, boolean)} --
     * die Verwechslung nimmt die JVM mit, ohne eine Meldung zu hinterlassen (Aufgabe 89).
     */
    public static RenderPipeline waehlen(boolean culling, boolean tiefeSchreiben)
    {
        /* SOLID-VORSCHAU: der Darstellungsmodus haengt an der ANSICHT, nicht am Objekt —
         * darum steht die Entscheidung in der zentralen WAHL und nicht an den Aufrufstellen.
         * Blender baut es genauso (View3D.shading, das der Renderpfad gar nicht erst liest);
         * dadurch gibt es hier nichts, was ein Video-Export uebernehmen koennte. */
        if (com.afrobama.bbstools.client.solid.SolidVorschau.aktiv())
        {
            return com.afrobama.bbstools.client.solid.SolidVorschauPipeline.holen(false, culling, tiefeSchreiben);
        }

        if (tiefeSchreiben)
        {
            return culling ? DURCHSICHTIG : DURCHSICHTIG_BEIDSEITIG;
        }

        return culling ? DURCHSICHTIG_OHNE_TIEFENSCHREIBEN : DURCHSICHTIG_OHNE_TIEFENSCHREIBEN_BEIDSEITIG;
    }

    private static RenderPipeline bauen(String shader, boolean mischen, boolean culling, boolean tiefeSchreiben)
    {
        return bauen(shader, mischen, culling, tiefeSchreiben, false);
    }

    /** Dieselbe Pipeline, aber mit EINER verschraenkten Bindung statt dreier getrennter. */
    private static RenderPipeline bauenVerschraenkt(String shader, boolean mischen, boolean culling, boolean tiefeSchreiben)
    {
        return bauen(shader, mischen, culling, tiefeSchreiben, true);
    }

    private static RenderPipeline bauen(String shader, boolean mischen, boolean culling,
        boolean tiefeSchreiben, boolean verschraenkt)
    {
        return bauen(shader, mischen, culling, tiefeSchreiben, verschraenkt, false);
    }

    private static RenderPipeline bauenGpu(String shader, boolean mischen, boolean culling, boolean tiefeSchreiben)
    {
        return bauen(shader, mischen, culling, tiefeSchreiben, false, true);
    }

    private static RenderPipeline bauen(String shader, boolean mischen, boolean culling,
        boolean tiefeSchreiben, boolean verschraenkt, boolean gpu)
    {
        Identifier vertexDatei = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID,
            "core/" + shader + (gpu ? "_gpu" : ""));
        Identifier fragmentDatei = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "core/" + shader);
        String name = shader + (mischen ? "_translucent" : "_opaque")
            + (culling ? "" : "_no_cull") + (tiefeSchreiben ? "" : "_no_depth_write")
            /* ⚠️ Der Name IST die Kennung der Pipeline. Ohne diesen Zusatz truegen beide
             * Familien dieselbe und die zweite ueberschriebe die erste. */
            + (verschraenkt ? "_interleaved" : "") + (gpu ? "_gpu_skin" : "");

        RenderPipeline.Builder bauer = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(vertexDatei)
            .withFragmentShader(fragmentDatei)
            .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(GRUPPE_MODELL)
            .withCull(culling);

        if (gpu)
        {
            bauer.withBindGroupLayout(GRUPPE_SKIN);
        }

        if (verschraenkt)
        {
            bauer.withVertexBinding(0, FORMAT_MODELL_VERSCHRAENKT);
        }
        else
        {
            bauer.withVertexBinding(BINDUNG_POSITION, FORMAT_POSITION)
                .withVertexBinding(BINDUNG_NORMALE, FORMAT_NORMALE)
                .withVertexBinding(BINDUNG_UV0, FORMAT_UV0)
                .withVertexBinding(BINDUNG_LICHT, FORMAT_LICHT);

            if (gpu)
            {
                bauer.withVertexBinding(BINDUNG_JOINTS0, FORMAT_JOINTS0)
                    .withVertexBinding(BINDUNG_WEIGHTS0, FORMAT_WEIGHTS0)
                    .withVertexBinding(BINDUNG_JOINTS1, FORMAT_JOINTS1)
                    .withVertexBinding(BINDUNG_WEIGHTS1, FORMAT_WEIGHTS1);
            }
        }

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
