package mchorse.bbs_mod.forms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.Zeichenziel;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Der Sammler, in den Formen gerade ihre Zeichenauftraege einreichen.
 *
 * <h2>Warum es ihn gibt</h2>
 *
 * <p>In 26.2 zeichnet niemand mehr sofort: man reicht einen Auftrag bei einem
 * {@link SubmitNodeCollector} ein, und der Bildgraph zeichnet ihn spaeter in der richtigen
 * Reihenfolge. Der Sammler kommt vom Weltdurchgang ganz oben; BBS' Form-Renderer brauchen
 * ihn ganz unten.
 *
 * <p>Dieselbe Bruecke wie {@code PickZiel} und {@code Zeichenbereich}, aus demselben Grund:
 * das Ding als Parameter durch jede Form-Renderer-Signatur zu reichen waere richtig, aber
 * es sind ein Dutzend Ketten, und der Port haette dann eine Baustelle mehr statt einer
 * weniger.
 *
 * <h2>⚠️ Die Oberflaechen-Wege haben KEINEN Sammler</h2>
 *
 * <p>{@code renderInUI} zeichnet eine Form in ein Bedienelement, nicht in die Welt -- dort
 * laeuft kein Weltdurchgang und es gibt nichts einzureichen. Diese Wege muessen ihren
 * eigenen Durchgang oeffnen (siehe SofortZeichner/GehaltenesNetz) oder, fuer ganze
 * Vanilla-Szenen, ueber Mojangs PictureInPicture gehen.
 *
 * <p>Darum wirft {@link #pflicht()} statt {@code null} zurueckzugeben: ein Form-Renderer,
 * der im UI-Weg nach dem Weltsammler greift, bekommt eine klare Meldung und keine
 * Nullzeiger-Ausnahme funfzehn Zeilen spaeter.
 */
public final class FormSammler
{
    private static SubmitNodeCollector aktiv;

    public static void anmelden(SubmitNodeCollector sammler)
    {
        aktiv = sammler;
    }

    public static void abmelden()
    {
        aktiv = null;
    }

    /** Der angemeldete Sammler, oder {@code null} ausserhalb eines Weltdurchgangs. */
    public static SubmitNodeCollector aktiv()
    {
        return aktiv;
    }

    /**
     * Fuehrt {@code zeichnen} mit einem EIGENEN Sammler aus und laesst ihn danach zeichnen.
     *
     * <h2>Der Weg, den Vanilla selbst geht</h2>
     *
     * <p>Genau so loest 26.2 dieselbe Aufgabe fuer seine eigenen Vorschaubilder in
     * Bedienoberflaechen (Spielermodell im Inventar, Bannertisch, Buchmodell). Der Ablauf
     * steht in {@code PictureInPictureRenderer.prepare} und besteht aus drei Schritten:
     *
     * <ol>
     *   <li>einen frischen {@link SubmitNodeStorage} anlegen,</li>
     *   <li>die Geometrie dort EINREICHEN,</li>
     *   <li>{@code FeatureRenderDispatcher.renderAllFeatures(storage)} rufen -- das ist der
     *       Schritt, der aus eingereichten Auftraegen ein Bild macht.</li>
     * </ol>
     *
     * <p>Nachgelesen in Vanillas Bytecode, nicht erfunden: ohne den dritten Schritt sammelt
     * man Auftraege, die niemand ausfuehrt, und das Bedienelement bleibt leer -- ein stiller
     * Fehler ohne jede Meldung.
     *
     * <p>⚠️ Der vorherige Sammler wird gemerkt und wiederhergestellt. Ohne das risse ein
     * Vorschaubild, das waehrend eines Weltdurchgangs gezeichnet wird, dem Weltdurchgang
     * seinen Sammler weg.
     *
     * <p>⚠️ {@code renderAllFeatures} laeuft NACH dem Wiederherstellen, aber mit dem eigenen
     * Sammler als Argument -- es zeichnet also genau das, was in diesem Abschnitt eingereicht
     * wurde, und nichts sonst.
     */
    public static void mitEigenemSammler(Runnable zeichnen)
    {
        SubmitNodeStorage eigener = new SubmitNodeStorage();
        SubmitNodeCollector vorheriger = aktiv;

        aktiv = eigener;

        try
        {
            zeichnen.run();
        }
        finally
        {
            aktiv = vorheriger;
        }

        zeichnenLassen(eigener);
    }

    /**
     * Zeichnet mit einem BEREITS VORHANDENEN Sammler und stellt den vorherigen wieder her.
     *
     * <p>Unterschied zu {@link #mitEigenemSammler(Runnable)}: dort wird ein frischer Sammler
     * angelegt und am Ende selbst ausgefuehrt. Hier ist der richtige Sammler schon da — er
     * kommt als Parameter aus Vanillas Zeichenweg — und wird nur fuer die Dauer des Abschnitts
     * angemeldet. Ausgefuehrt wird er von dem, dem er gehoert.
     *
     * <p>⛔ WOFUER DAS GEBRAUCHT WIRD (2026-08-10, halbe nacht gekostet). Der FormSammler wurde
     * nur in den beiden {@code LevelRenderEvents} angemeldet (BBSModClient) — also im Film-Weg
     * von BBS. Ein ACTOR-Replay laeuft aber nicht darueber: es ist eine echte {@code
     * ActorEntity}, die VANILLA rendert, ueber {@code LevelRenderer.submitEntities}. Dort war
     * kein Sammler angemeldet, und {@code ModelFormRenderer.renderArmor} griff per {@link
     * #pflicht()} danach:
     *
     *     IllegalStateException: Kein Sammler angemeldet.
     *
     * Diese Exception verschwand in einem leeren {@code catch} in FormUtilsClient.render, der
     * dazugehoerige {@code popPose()} wurde nie erreicht, und ein Bild spaeter starb das Spiel
     * an {@code LevelRenderer.checkPoseStack} — "Pose stack not empty", mit einem Stapelabzug
     * ohne eine einzige BBS-Zeile darin. Betroffen waren NUR Actors, weil nur sie Ruestung
     * zeichnen.
     *
     * <p>⚠️ Verschachtelungssicher wie oben: der vorherige Sammler wird gemerkt, nicht
     * ueberschrieben. {@code abmelden()} waere hier falsch — es setzt hart auf {@code null} und
     * risse einem umschliessenden Weltdurchgang seinen Sammler weg.
     */
    public static void mitSammler(SubmitNodeCollector sammler, Runnable zeichnen)
    {
        SubmitNodeCollector vorheriger = aktiv;

        aktiv = sammler;

        try
        {
            zeichnen.run();
        }
        finally
        {
            aktiv = vorheriger;
        }
    }

    /**
     * Laesst den eingereichten Stapel zeichnen.
     *
     * <p>⚠️ Hier werden die Ziel-Umleitungen NICHT angefasst, und das ist Absicht. Ein
     * Versuch, sie zu setzen, stand hier zwischenzeitlich -- er half nicht, weil der
     * Fehler, den er beheben sollte, aus dem WELTdurchgang kam und nicht von hier
     * (LevelRenderer.render, nicht dieser Aufruf). Die Ursache lag in
     * {@code BBSRendering.reassignFramebuffer}, das nur die Farbe umlenkte und die Tiefe
     * stehen liess. Dokumentiert, damit niemand denselben Umweg noch einmal geht.
     */
    private static void zeichnenLassen(SubmitNodeStorage eigener)
    {
        zielMelden();

        /* ⛔ HIER GEHOERT DAS LICHT HIN — UND ES FEHLTE (2026-08-11, vom Nutzer gemeldet).
         * FormRenderer#renderUI traegt seit dem Port den Hinweis, das Licht werde "jetzt in
         * FormSammler.zeichnenLassen" gesetzt. Es wurde nie gesetzt: die Miniaturen zeichneten
         * mit dem Licht, das der GUI-Durchgang gerade gebunden hatte. Gemessen an der
         * Formen-Palette gegen 1.21.1 (gleiche Ansicht): Lorenwagen Helligkeit 164 statt 188,
         * die flache Platte daneben fast schwarz. Keine Ausnahme, kein Log — nur zu dunkel.
         *
         * ansichtLoeschen() zuerst: ohne das erbt die Palette die Umlaufkamera der zuletzt
         * gezeichneten Modellvorschau. Ohne Kamera liefert UiLicht die festen Richtungen
         * (0,1,-0.2) und (-0.85,0.85,1) — woertlich die aus 1.21.1s FormRenderer:58.
         *
         * ⚠️ Sichern und zuruecksetzen ist Pflicht: nach diesem Aufruf zeichnet der
         * GUI-Durchgang weiter, und ein haengengebliebener Lichtblock faerbt alles Folgende. */
        com.mojang.renderpearl.api.buffers.GpuBufferSlice gesichertesLicht =
            com.mojang.blaze3d.systems.RenderSystem.getShaderLights();

        mchorse.bbs_mod.graphics.UiLicht.ansichtLoeschen();
        mchorse.bbs_mod.graphics.UiLicht.setzen();

        try
        {
            //? if >=26.3 {
            FeatureRenderDispatcher dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            GpuTextureView color = Zeichenziel.eigenes() ? Zeichenziel.farbe()
                : BBSRendering.outputColorTextureOverride != null
                    ? BBSRendering.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depth = Zeichenziel.eigenes() ? Zeichenziel.tiefe()
                : BBSRendering.outputColorTextureOverride != null
                    ? BBSRendering.outputDepthTextureOverride : target.getDepthTextureView();

            try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(eigener);
                RenderPass pass = depth == null
                    ? RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "bbs-form-preview", color, Optional.empty())
                    : RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "bbs-form-preview", color, Optional.empty(), depth, OptionalDouble.empty()))
            {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
            //?} else {
            /*Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(eigener);
            *///?}
        }
        finally
        {
            com.mojang.blaze3d.systems.RenderSystem.setShaderLights(gesichertesLicht);
        }
    }

    /**
     * Meldet EINMAL, gegen welche Masse hier tatsaechlich gezeichnet wird.
     *
     * <p>Die Fehlermeldung nennt nur die Zahlen, nicht ihre Herkunft ("Is 854x480 expected
     * 2560x1440"). Solange nicht feststeht, WELCHE Flaeche welche Groesse hat, waere jeder
     * Fix geraten -- und ein Ziel, das man nicht gemessen hat, ist ein Ziel, das man nicht
     * kennt. Die Zeile faellt weg, sobald die Ursache steht.
     */
    private static boolean zielGemeldet;

    private static void zielMelden()
    {
        if (zielGemeldet)
        {
            return;
        }

        zielGemeldet = true;

        try
        {
            com.mojang.blaze3d.pipeline.RenderTarget haupt =
                Minecraft.getInstance().gameRenderer.mainRenderTarget();

            System.err.println("[ziel] hauptziel farbe="
                + haupt.getColorTexture().getWidth(0) + "x" + haupt.getColorTexture().getHeight(0)
                + " tiefe=" + (haupt.getDepthTexture() == null ? "KEINE"
                    : haupt.getDepthTexture().getWidth(0) + "x" + haupt.getDepthTexture().getHeight(0))
                + " | fenster=" + Minecraft.getInstance().getWindow().getWidth()
                + "x" + Minecraft.getInstance().getWindow().getHeight()
                + " | umleitung farbe=" + (BBSRendering.outputColorTextureOverride == null ? "keine" : "gesetzt")
                + " tiefe=" + (BBSRendering.outputDepthTextureOverride == null ? "keine" : "gesetzt"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static SubmitNodeCollector pflicht()
    {
        if (aktiv == null)
        {
            throw new IllegalStateException(
                "Kein Sammler angemeldet. Formen koennen nur WAEHREND eines Weltdurchgangs "
                + "einreichen; die Oberflaechen-Wege (renderInUI) muessen einen eigenen "
                + "Durchgang oeffnen statt nach dem Weltsammler zu greifen.");
        }

        return aktiv;
    }

    private FormSammler()
    {}
}
