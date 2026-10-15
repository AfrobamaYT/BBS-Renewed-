package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.CompareOp;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.ResourceLocation;

/**
 * Der BBS-Modellweg OHNE Schattierung — die fehlende Schwester von
 * {@link ModelVAOPipeline#DURCHSICHTIG}.
 *
 * <h2>⛔ Warum hier keine Vanilla-Pipeline stehen darf</h2>
 *
 * <p>Gezeichnet wird ueber {@code ModelVAORenderer.render(pipeline, ModelVAO, …)}, und
 * {@code ModelVAO.zeichnen} bindet BBS' DREI getrennte Vertexpuffer: Position an Bindung 0,
 * Normale an 1, UV0 an 2. Eine Vanilla-Pipeline erklaert dagegen EINE verschraenkte Bindung 0
 * ({@code POSITION_TEX_COLOR} bzw. {@code ENTITY}). Setzt man sie hier ein, liest der Shader
 * den reinen Positionspuffer als ganzen verschraenkten Vertex — <b>kein Fehler, sondern Muell
 * im Bild</b>. Genau das stand bis zu dieser Aenderung in {@code ExtrudedFormRenderer}, in
 * beiden Vanilla-Zweigen.
 *
 * <p>Bis 1.21.1 ging es, weil Attribute damals ueber ihren NAMEN gebunden wurden: BBS' VAO
 * legte den Positionspuffer auf {@code Position}, liess den Rest leer und setzte Farbe, Licht
 * und Overlay als konstante Attribute. Beides — Namensbindung und konstante Attribute — gibt
 * es in 26.2 nicht mehr.
 *
 * <h2>Was diese Pipeline zeichnet</h2>
 *
 * <p>Dasselbe wie Vanillas {@code position_tex_color} vorher: Textur mal Formfarbe, Verwerfen
 * bei Deckkraft 0, sonst nichts. Kein Richtungslicht, keine Lichtkarte, kein Overlay, kein
 * Nebel. Der Shader dazu liegt in {@code assets/bbs/shaders/core/model_no_shading.vsh/.fsh}
 * und erklaert die Schwellen im Einzelnen.
 *
 * <h2>⚠️ Absichtlich NICHT in {@code ModelVAOPipeline.istModellPipeline}</h2>
 *
 * <p>Die Zwei-Pass-Durchsichtigkeit hing bis 1.21.1 an der Frage
 * {@code shader.getUniform("PassMode") != null} — die nur BBS' beschatteter Modell-Shader
 * bejahte. {@code position_tex_color} hatte das Uniform nie, also lief der unbeschattete Fall
 * schon damals einpassig. Diese Pipeline dort einzutragen waere also keine Reparatur, sondern
 * eine Verhaltensaenderung.
 *
 * <h2>⚠️ Warum die Klasse hier liegt und nicht neben ihren Geschwistern</h2>
 *
 * <p>Sie gehoert sachlich in {@link ModelVAOPipeline}. Diese Datei ist in der laufenden Welle
 * einem anderen Arbeiter zugeteilt; ein Zusammenlegen ist ein Verschieben von zwei Methoden
 * und sollte beim Zusammenfuehren passieren, nicht in zwei Haenden gleichzeitig.
 */
public final class ModellOhneSchattierung
{
    private static final RenderPipeline DURCHSICHTIG = bauen();

    /**
     * Der uebliche Durchgang: durchsichtig gemischt, Tiefe gelesen und geschrieben,
     * Rueckseiten verworfen — dieselbe Zustandswahl wie {@link ModelVAOPipeline#DURCHSICHTIG},
     * nur mit dem unbeschatteten Shaderpaar.
     */
    public static RenderPipeline durchsichtig()
    {
        return DURCHSICHTIG;
    }

    private static RenderPipeline bauen()
    {
        ResourceLocation datei = ResourceLocation.fromNamespaceAndPath(BBSMod.MOD_ID, "core/model_no_shading");

        return RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath(BBSMod.MOD_ID, "pipeline/model_no_shading_translucent"))
            .withVertexShader(datei)
            .withFragmentShader(datei)
            /* Dieselben drei Bindungen und Formate wie der beschattete Weg — sie sind der
             * Vertrag mit ModelVAO.zeichnen, das immer alle drei Puffer setzt. Uebernommen
             * statt abgeschrieben, damit eine Aenderung dort hier nicht auseinanderlaeuft. */
            .withVertexBinding(ModelVAOPipeline.BINDUNG_POSITION, ModelVAOPipeline.FORMAT_POSITION)
            .withVertexBinding(ModelVAOPipeline.BINDUNG_NORMALE, ModelVAOPipeline.FORMAT_NORMALE)
            .withVertexBinding(ModelVAOPipeline.BINDUNG_UV0, ModelVAOPipeline.FORMAT_UV0)
            .withPrimitiveTopology(VertexFormat.Mode.TRIANGLES)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            /* Nur die Modelltextur. Die drei Sampler und der ModelInfo-Block des beschatteten
             * Weges fehlen hier, weil dieser Shader sie nicht liest — eine Bindegruppe, die
             * das Programm nicht kennt, ist bestenfalls eine Warnung und schlimmstenfalls
             * eine Bindung, die niemand fuellt. */
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(true)
            /* ⛔ 26.2 rechnet die Tiefe UMGEKEHRT (reversed-Z): naeher heisst groesserer Wert.
             * Das alte GL_LEQUAL uebersetzt sich zu GREATER_THAN_OR_EQUAL, nicht zum
             * Wortlaut. Wer das mechanisch uebersetzt, dreht den Test um. */
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    }

    private ModellOhneSchattierung()
    {}
}
