package mchorse.bbs_mod.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOPipeline;

/**
 * Die Zugangsstelle zu BBS' eigenen Zeichenprogrammen.
 *
 * <h2>26.2: aus Shader-Objekten werden Pipelines</h2>
 *
 * <p>Bis 1.21.1 baute diese Klasse zehn {@code RenderPipeline}-Objekte, lud sie ueber
 * eine {@code ProxyResourceFactory} aus BBS' Namensraum und gab sie an die Aufrufer, die
 * ihre Uniforms dann selbst setzten. Nichts davon existiert in 26.2: kein
 * {@code RenderPipeline}, keine Einzel-Uniforms, und {@code RenderSystem.setShader} gibt
 * es auch nicht mehr.
 *
 * <p>An ihre Stelle tritt die {@link RenderPipeline} — sie traegt Shader, Blend, Tiefe,
 * Cull und Vertexformat als EIN Objekt. Gewaehlt wird sie, nicht zusammengesetzt. Diese
 * Klasse ist damit nur noch ein Verzeichnis: sie zeigt auf die Pipelines, die anderswo
 * definiert sind.
 *
 * <p>Die {@code ProxyResourceFactory} entfaellt ersatzlos. Sie existierte nur, weil
 * {@code RenderPipeline} seine Dateien immer im minecraft-Namensraum suchte; eine
 * Pipeline nimmt einen vollen {@code Identifier} entgegen, {@code bbs:core/model} findet
 * {@code assets/bbs/shaders/core/model}.
 *
 * <h2>Wo die Pipelines liegen und warum getrennt</h2>
 *
 * <ul>
 *   <li>{@link ModelVAOPipeline} — alles, was ueber BBS' eigenen Modellweg zeichnet:
 *       der Modellshader selbst und die vier Auswahl-Durchgaenge. Sie teilen die drei
 *       Vertexbindungen und den {@code ModelInfo}-Block.</li>
 *   <li>{@link BBSPipelines} — alles, was ueber {@code Batcher2D} in die Oberflaeche
 *       geht. Diese erben Vanillas GUI-Zustand und koennen keine eigenen Uniforms
 *       tragen; ihre frueheren Werte reisen in der Vertexfarbe mit.</li>
 * </ul>
 */
public class BBSShaders
{
    /**
     * 26.2: kein Neuladen mehr noetig.
     *
     * <p>Frueher baute {@code setup()} bei jedem Ressourcen-Neuladen zehn Shader neu und
     * schloss die alten. Pipelines sind unveraenderliche Beschreibungen — Mojang uebersetzt
     * sie selbst neu, wenn sich die Ressourcen aendern. Die Methode bleibt, weil
     * Aufrufer sie kennen, und tut nichts.
     */
    public static void setup()
    {}

    public static RenderPipeline getModel()
    {
        /* SOLID-VORSCHAU. Es reicht NICHT, das nur in ModelVAOPipeline.waehlen zu tun:
         * CubicVAORenderer zeichnet mit GENAU der Pipeline, die diese Methode liefert
         * (ModelVAORenderer.render(this.pipeline, …)), und FormTranslucentQueue.needsSplit
         * befragt dieselbe. Saehen die beiden verschiedene Objekte, entschiede die
         * Warteschlange fuer den einen Shader und gezeichnet wuerde der andere.
         *
         * DURCHSICHTIG ist die Variante MIT Culling und MIT Tiefenschreiben — die
         * Solid-Wahl hier muss dieselben zwei Schalter tragen. */
        if (com.afrobama.bbstools.client.solid.SolidVorschau.aktiv())
        {
            return com.afrobama.bbstools.client.solid.SolidVorschauPipeline.holen(false, true, true);
        }

        return ModelVAOPipeline.DURCHSICHTIG;
    }

    public static RenderPipeline getMultilinkProgram()
    {
        return BBSPipelines.getMultilink();
    }

    public static RenderPipeline getSubtitlesProgram()
    {
        return BBSPipelines.getSubtitles();
    }

    public static RenderPipeline getSelectionProgram()
    {
        return BBSPipelines.getSelection();
    }

    public static RenderPipeline getPickerPreviewProgram()
    {
        return BBSPipelines.getPickerPreview();
    }

    /** Umriss um das bearbeitete Replay — liest die Kante aus demselben Stencil-Bild. */
    public static RenderPipeline getReplayOutlineProgram()
    {
        return BBSPipelines.getReplayOutline();
    }

    public static RenderPipeline getPickerBillboardProgram()
    {
        return ModelVAOPipeline.PICKER_BILLBOARD;
    }

    public static RenderPipeline getPickerBillboardNoShadingProgram()
    {
        return ModelVAOPipeline.PICKER_BILLBOARD_OHNE_SCHATTIERUNG;
    }

    public static RenderPipeline getPickerParticlesProgram()
    {
        return ModelVAOPipeline.PICKER_PARTIKEL;
    }

    public static RenderPipeline getPickerModelsProgram()
    {
        return ModelVAOPipeline.PICKER_MODELLE;
    }
}
