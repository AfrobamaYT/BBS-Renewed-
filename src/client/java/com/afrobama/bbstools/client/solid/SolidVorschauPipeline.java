package com.afrobama.bbstools.client.solid;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;

import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Die Pipeline-Familie der Solid-Vorschau — das Gegenstueck zu {@link ModelVAOPipeline},
 * mit demselben Vertrag ueber die Vertexpuffer, aber dem Shaderpaar
 * {@code assets/bbs/shaders/core/model_solid}.
 *
 * <h2>⛔ ZWEI FAMILIEN, UND SIE ZU VERWECHSELN IST EIN TREIBERABSTURZ OHNE MELDUNG</h2>
 *
 * <p>Das ist keine Vorsicht, das ist Aufgabe 89 aus diesem Baum. BBS liefert dieselben
 * Modelle auf zwei Arten verpackt:
 *
 * <ul>
 *   <li><b>GETRENNT</b> — {@code ModelVAO}/{@code BOBJModelVAO} binden DREI eigene Puffer auf
 *       die Nummern 0/1/2 (Position, Normale, UV0).</li>
 *   <li><b>VERSCHRAENKT</b> — {@code SofortZeichner}/{@code GehaltenesNetz} liefern EINEN
 *       Puffer im Vanilla-Format {@code ENTITY} auf Nummer 0. Mehr kann {@code MeshData}
 *       nicht.</li>
 * </ul>
 *
 * <p>Zeichnet man den zweiten Weg mit einer Pipeline des ersten, ist der Zeichenaufruf FORMAL
 * GUELTIG: {@code FORMAT_POSITION} ist 12 Byte breit, {@code ENTITY} 36 — der Shader nimmt
 * jede 12. Bytegruppe als neue Ecke und liest dreimal so weit, wie der Puffer reicht.
 * {@code glGetError} schweigt, es gibt keine Ausnahme, die JVM ist einfach weg. Darum gibt es
 * hier BEIDE Familien, und darum entscheidet {@link #holen(boolean, boolean, boolean)} das
 * ueber ein Argument statt ueber die Aufrufstelle.
 *
 * <p>⚠️ Warum EIN Shaderpaar fuer beide reicht: Blaze3D bindet Vertexattribute ueber ihren
 * NAMEN, nicht ueber ihre Lage. {@code ENTITY} traegt {@code Position}, {@code Color},
 * {@code UV0}, {@code UV1}, {@code UV2} und {@code Normal} — die zwei, die
 * {@code model_solid.vsh} liest ({@code Position}, {@code Normal}), sind darunter. Dieselbe
 * Begruendung steht in {@link ModelVAOPipeline}, sie ist dort mit javap nachgemessen.
 *
 * <h2>Warum TRIANGLES</h2>
 *
 * <p>Beide Zulieferer liefern Dreiecke: {@link ModelVAOPipeline} baut alle seine Pipelines
 * mit {@code VertexFormat.Mode.TRIANGLES}, und {@code ModelInstance.render} oeffnet den
 * Sofortweg woertlich mit {@code SofortZeichner.beginnen(…, VertexFormat.Mode.TRIANGLES,
 * DefaultVertexFormat.NEW_ENTITY)}. Eine Dreiecksliste in eine QUADS-Pipeline zu schreiben (oder
 * umgekehrt) ergibt keinen Fehler, sondern Splitter statt Flaechen — die Topologie ist hier
 * abgeschrieben, nicht gewaehlt.
 *
 * <h2>Was ABSICHTLICH fehlt</h2>
 *
 * <ul>
 *   <li><b>{@code LIGHTING} und {@code FOG}</b> — der Shader beleuchtet aus der Normalen und
 *       kennt keinen Nebel. Eine Bindegruppe, die das Programm nicht liest, ist bestenfalls
 *       eine Warnung.</li>
 *   <li><b>{@code GRUPPE_MODELL}</b> — sie buendelt den {@code ModelInfo}-Block untrennbar mit
 *       Sampler0/1/2. Der Solid-Shader liest keine Textur; die Begruendung im Einzelnen steht
 *       in {@code model_solid.vsh}.</li>
 *   <li><b>Der Zwei-Pass ({@code Modus.x})</b> — dieses Feld steckt in eben jenem Block. Diese
 *       Pipelines stehen deshalb bewusst NICHT in
 *       {@code ModelVAOPipeline.istModellPipeline}: {@code FormTranslucentQueue} teilt dann
 *       gar nicht erst auf, und die Solid-Vorschau zeichnet einpassig. Genau dieselbe
 *       Entscheidung mit derselben Begruendung wie bei
 *       {@code mchorse.bbs_mod.graphics.ModellOhneSchattierung}.
 *       <p>⚠️ NEBENWIRKUNG, die man sehen wird: halbdurchsichtige Teile werden in
 *       Einreihungsreihenfolge gezeichnet statt von hinten nach vorn. In der Vorschau
 *       tragbar, im Export nie sichtbar — dort laeuft der volle Weg.</li>
 * </ul>
 *
 * <h2>⚠️ Erst beim ersten Solid-Bild gebaut</h2>
 *
 * <p>Die Pipelines entstehen erst, wenn jemand sie holt, nicht beim Laden der Klasse. Zwei
 * Gruende: wer die Vorschau nie einschaltet, bezahlt kein Uebersetzen; und
 * {@link ModelVAOPipeline} ist beim Zugriff auf seine Konstanten dann sicher fertig
 * initialisiert (diese Klasse wird aus dessen {@code waehlen}-Methoden gerufen).
 *
 * <p>Der Preis ist ein einmaliges Stocken beim ersten Bild nach dem Einschalten — bis zu
 * acht Pipelines, aber nur die tatsaechlich angefragten.
 */
public final class SolidVorschauPipeline
{
    /** Schluessel: verschraenkt|culling|tiefeSchreiben. Vorbild: {@code FormPipelines}. */
    private static final Map<String, RenderPipeline> ZWISCHENSPEICHER = new HashMap<>();

    /**
     * Die Solid-Pipeline zu einer Zustandskombination; legt sie beim ersten Mal an.
     *
     * @param verschraenkt   {@code true} fuer EINEN Puffer im {@code ENTITY}-Format
     *                       (SofortZeichner), {@code false} fuer DREI eigene Puffer (ModelVAO).
     *                       ⛔ Die Verwechslung nimmt die JVM mit, ohne eine Meldung zu
     *                       hinterlassen — siehe Klassendoku.
     * @param culling        Rueckseiten verwerfen; {@code false} fuer Modelle mit
     *                       {@code isCulling() == false}
     * @param tiefeSchreiben Tiefe schreiben; der Tiefen-VERGLEICH bleibt immer an
     */
    public static RenderPipeline holen(boolean verschraenkt, boolean culling, boolean tiefeSchreiben)
    {
        String schluessel = verschraenkt + "|" + culling + "|" + tiefeSchreiben;

        return ZWISCHENSPEICHER.computeIfAbsent(schluessel,
            (s) -> bauen(verschraenkt, culling, tiefeSchreiben));
    }

    private static RenderPipeline bauen(boolean verschraenkt, boolean culling, boolean tiefeSchreiben)
    {
        Identifier datei = Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "core/model_solid");

        /* ⚠️ Der Name IST die Kennung der Pipeline. Ohne die Zusaetze truegen mehrere
         * dieselbe und die spaetere ueberschriebe die fruehere — dieselbe Falle wie beim
         * "_interleaved" in ModelVAOPipeline. */
        String name = "model_solid"
            + (verschraenkt ? "_interleaved" : "")
            + (culling ? "" : "_no_cull")
            + (tiefeSchreiben ? "" : "_no_depth_write");

        RenderPipeline.Builder bauer = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(datei)
            .withFragmentShader(datei)
            .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES)
            /* Nur diese zwei: ModelViewMat und ColorModulator kommen aus DYNAMIC_TRANSFORMS,
             * ProjMat aus PROJECTION. Mehr liest das Programm nicht.
             *
             * ⚠️ Damit setzt dieser Durchgang seine Projektion NICHT selbst — er erbt die des
             * Weltdurchgangs, in dem er zeichnet. Das ist hier RICHTIG und nicht die bekannte
             * Falle: es ist derselbe Zeichendurchgang und dieselbe Kamera wie beim vollen
             * Modellweg, nur mit anderem Shader. Der Fehlerfall waere ein EIGENER Durchgang
             * (Overlay, Nachbearbeitung), der die Projektion des Weltdurchgangs uebernimmt —
             * bei Perspektive ist w dann 0 an allen Ecken, die Flaeche entartet und es
             * entsteht kein einziges Fragment. Hier wird kein eigener Durchgang aufgemacht. */
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(culling);

        if (verschraenkt)
        {
            bauer.withVertexBinding(0, DefaultVertexFormat.NEW_ENTITY);
        }
        else
        {
            /* ⛔ ALLE DREI, auch wenn der Shader die UV nicht liest: ModelVAO.zeichnen setzt
             * immer alle drei Puffer, und eine Luecke in der Bindungsnummerierung waere ein
             * zweiter Fehler. Die Konstanten kommen aus ModelVAOPipeline statt abgeschrieben,
             * damit eine Aenderung dort hier nicht auseinanderlaeuft. */
            bauer.withVertexBinding(ModelVAOPipeline.BINDUNG_POSITION, ModelVAOPipeline.FORMAT_POSITION)
                .withVertexBinding(ModelVAOPipeline.BINDUNG_NORMALE, ModelVAOPipeline.FORMAT_NORMALE)
                .withVertexBinding(ModelVAOPipeline.BINDUNG_UV0, ModelVAOPipeline.FORMAT_UV0);
        }

        /* ⛔ 26.2 rechnet die Tiefe UMGEKEHRT (reversed-Z): naeher heisst groesserer Wert, der
         * Tiefenpuffer wird auf 0.0 geloescht. Das alte GL_LEQUAL uebersetzt sich zu
         * GREATER_THAN_OR_EQUAL, NICHT zum Wortlaut. Wer das mechanisch uebersetzt, dreht den
         * Test um und alles verschwindet hinter allem. */
        bauer.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, tiefeSchreiben));

        /* Gemischt wie der volle Modellweg. Blenders Solid-Modus ist zwar undurchsichtig, aber
         * in BBS traegt die Formfarbe ihr Alpha (Ein-/Ausblenden von Darstellern) — ohne
         * Mischung waere ein eingeblendeter Darsteller in der Vorschau schlagartig da. Die
         * Ersparnis dieses Features liegt im Shader (kein Sampler, kein Licht, kein Overlay,
         * kein Nebel, kein Zwei-Pass), nicht im Blendzustand.
         *
         * ⚠️ enableBlend() gibt es in 26.2 nicht mehr — die Mischung steht HIER und nirgends
         * sonst. */
        return bauer.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    }

    private SolidVorschauPipeline()
    {}
}
