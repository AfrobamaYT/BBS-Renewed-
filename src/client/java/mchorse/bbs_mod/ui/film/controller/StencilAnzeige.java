package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.colors.Colors;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.utils.Pair;

import java.util.function.Supplier;

/**
 * Was über dem Stencil-Bild liegt: der Umriss des bearbeiteten Replays und die Beschriftung
 * dessen, worüber die Maus steht.
 *
 * <p>Beides zeichnet auf denselben Puffer, den {@code UIFilmController} ohnehin jeden Frame
 * füllt — der Stencil trägt pro Pixel den Index des Objekts, das dort zu sehen ist. Deshalb
 * braucht der Umriss keine zweite Geometrie-Runde: er liest die Kante aus dem Index-Bild.</p>
 */
public final class StencilAnzeige
{
    private StencilAnzeige()
    {
    }

    /**
     * Der Umriss um das gerade bearbeitete Replay — Blenders Silhouette im Object Mode.
     *
     * <p>USER-WUNSCH 2026-07-29: „wenn ich ein replay auswaehle dann will ich das es eine
     * outline um den replay umrum hat so wie blender objekten einen orangene Silhouette gibt
     * wenn man im object mode ist."</p>
     *
     * <p>Die Farbe stammt aus Blender selbst statt aus dem Gedächtnis: in der Instanz des Users
     * (Blender 5.2 LTS) steht {@code object_active} auf #FFA028 und {@code object_selected} auf
     * #ED5700, die Linienbreite auf 1 Bildschirmpixel. Das ist der Vorgabewert der Einstellung;
     * ändern darf der Nutzer sie.</p>
     *
     * <p>⚠ Die Grenze, ab der ein Index zum gewählten Replay gehört, hängt davon ab, WAS gerade
     * im Stencil steht — das hat mich einen halben Tag gekostet:</p>
     * <ul>
     *   <li>Normalfall: {@code UIFilmController#renderStencil} zeichnet NUR das gewählte Replay.
     *       {@code StencilMap#setup} beginnt bei {@code Gizmo.STENCIL_MAX + 1}, also ist alles
     *       ab {@code offset} das Replay; darunter liegen nur die Gizmo-Griffe.</li>
     *   <li>Mit Alt: alle Replays liegen drin, jedes mit {@code sein Index + offset}. Das
     *       gewählte beginnt erst hinter allen anderen, bei {@code anzahl + offset}.</li>
     * </ul>
     */
    public static void umriss(UIContext context, Area area, UIFilmPanel panel,
        StencilFormFramebuffer stencil, boolean altGedrueckt, int offset, int bisIndex)
    {
        Framebuffer puffer = stencil.getFramebuffer();

        if (panel.getData() == null || puffer == null)
        {
            return;
        }

        Texture texture = puffer.getMainTexture();
        int abIndex = altGedrueckt ? panel.getData().replays.getList().size() + offset : offset;

        /* ⛔ NICHT die übergebene Panel-Fläche, sondern das SICHTFENSTER. getViewport() legt das
         * Seitenverhältnis des Videos mittig ins Panel (Letterbox) — der Stencil-Inhalt gehört
         * also in dieses innere Rechteck. Spannt man ihn über das ganze Panel, liegt der Umriss
         * verzerrt und verschoben über der Figur.
         *
         * Warum das erst beim User auffiel: sein DEV-Panel ist zufällig fast genau 16:9, dort
         * fallen Panel und Sichtfenster zusammen. In seiner CF-Instanz nicht — dort zeichnete
         * der Umriss riesige Konturen quer durchs Bild (gemeldet 2026-07-29 mit Screenshots).
         * Ich habe erst die Mods verdächtigt; BBS' eigene Auswahl lieferte in CF aber saubere
         * Indizes (20 auf der Figur, 0 daneben) — der Puffer war also immer in Ordnung. */
        area = panel.preview.getViewport();

        if (texture == null)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("umriss.keine-textur");

            return;
        }

        RenderPipeline programm = BBSShaders.getReplayOutlineProgram();

        if (programm == null)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("umriss.kein-programm");

            return;
        }

        int farbe;
        int breite;

        /* Zum try/catch siehe aktiv(): fehlendes Einstellungsfeld darf nichts reissen. */
        try
        {
            if (!aktiv())
            {
                mchorse.bbs_mod.graphics.Messpunkt.zaehle("umriss.nicht-aktiv");

                return;
            }

            farbe = BBSSettings.editorReplayOutlineColor.get();
            breite = Math.max(1, BBSSettings.editorReplayOutlineWidth.get());
        }
        catch (Throwable t)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("umriss.einstellung-fehlt", String.valueOf(t));

            return;
        }

        /* Der Umriss wird VORHER in ein eigenes Bild gerechnet; die Oberflaeche blittet
         * danach nur ein fertiges Bild. Grund und verworfene Auswege: Bildfilter.
         *
         * ⚠ Die Schrittweite ist EIN BILDSCHIRMPIXEL in UV-Einheiten, nicht ein Texel. Die
         * Kachel spannt die ganze Textur auf, also ist 1/Breite genau ein Pixel -- der
         * Stencil liegt in Videoaufloesung und ein Texel dort ist viel kleiner. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("umriss.gezeichnet",
            "farbe=" + Integer.toHexString(farbe) + " breite=" + breite
            + " abIndex=" + abIndex
            + " quelle=" + texture.width + "x" + texture.height
            /* Die GL-Nummer der Textur, aus der der Umriss LIEST -- gegen die Nummer, die
             * StencilFormFramebuffer beim Zuruecklesen bindet (Sonde stencil.leseziel).
             * Zwei verschiedene Nummern hiesse: der Umriss sieht Kennungen, der Klick
             * greift auf einen anderen Puffer. */
            + " texturId=" + texture.id + " pufferId=" + puffer.id
            + " zwischenbild=" + (stencil.zwischenbild() == null ? "null"
                : stencil.zwischenbild().width + "x" + stencil.zwischenbild().height)
            + " flaeche=" + area.w + "x" + area.h);

        /* ⭐ EIGENER MARKER IM FRAME-DEBUGGER -- ohne ihn ist dieser Zug im Capture ein
         * namenloser unter tausenden. Die Miniaturen haben seit 2026-08-04 je einen, und
         * genau das machte #13 in EINEM Capture loesbar (Ziel, Viewport und Scissor je
         * Kachel ablesbar statt erraten). Der Umriss hatte keinen: im Schwenk-Capture vom
         * 2026-08-05 standen nur "BBS Auswahlpuffer (Stencil)" und die Miniaturen -- der
         * Zug, um den es bei #2 geht, war nicht auffindbar.
         *
         * Die Flaeche reist mit, weil bei #2 genau sie in Frage steht: liegt der Umriss an
         * derselben Stelle wie das Modell, oder blittet er ein Bild von woanders. */
        mchorse.bbs_mod.sonden.Sonden.gpu.gruppeAuf(mchorse.bbs_mod.sonden.Sonden.gpu.marken()
            ? "bbs-umriss " + area.w + "x" + area.h + " ab=" + abIndex
            : null);

        try
        {
            mchorse.bbs_mod.graphics.Bildfilter.umrissZeichnen(texture, stencil.zwischenbild(),
                abIndex, bisIndex, farbe, breite, 1F / area.w, 1F / area.h);
        }
        finally
        {
            /* Paarig -- ein Push ohne Pop verschachtelt den Event Browser falsch, und das ist
             * schlimmer als gar kein Marker. */
            mchorse.bbs_mod.sonden.Sonden.gpu.gruppeZu();
        }

        /* ⚗️ Auf Anforderung nachsehen, ob der Umriss ueberhaupt IM Zwischenbild steht.
         * Punktproben taugen bei einer Ein-Pixel-Kante nichts, darum wird die ganze Textur
         * nach der Umrissfarbe durchgezaehlt. Trennt „wird nicht gezeichnet" von „wird
         * gezeichnet und geht beim Blit verloren" -- der Unterschied ist von aussen
         * unsichtbar und hat am Himmel zwei Sitzungen gekostet. */
        /* ⛔ HIER STAND EINE SONDE, UND SIE HAT ZWEIMAL DAS FALSCHE GEMESSEN. Diese Methode
         * laeuft JEDES Bild (120 in 4 s), der Stencil wird aber gedrosselt gefuellt (4 in 4 s).
         * Eine Messung hier trifft den gefuellten Zustand also so gut wie nie und meldet
         * verlaesslich „leer" -- und sie schnappt der Sonde in StencilFormFramebuffer.unbind()
         * die Anforderung weg, die genau hinter der Fuellung sitzt und die richtige Antwort
         * haette. Wer hier wieder misst, misst wieder den falschen Augenblick. */

        int w = texture.width;
        int h = texture.height;

        /* 26.2: enableBlend() entfaellt ersatzlos. Mischung ist kein Schalter mehr, sondern
         * Teil der Pipeline, die der Batcher zum Zeichnen gereicht bekommt — die schlichte
         * texturedBox-Ueberladung gibt dafuer RenderPipelines.GUI_TEXTURED durch.
         *
         * ⚠ Der Umriss braucht die Mischung zwingend: sein Shader liefert ueberall ausserhalb
         * der Kante durchsichtige Pixel, ohne Mischung deckte diese Kachel das ganze
         * Vorschaubild zu. Die Pipeline hinter getReplayOutlineProgram() muss deshalb
         * BlendFunction.TRANSLUCENT tragen. */
        context.batcher.texturedBox(() -> net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, stencil.zwischenbild(), Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);
    }

    /**
     * Die Maus steht nicht über dem Vorschaufenster. Zu picken gibt es dann nichts — der Umriss
     * braucht das Index-Bild aber trotzdem, sonst zeigt er die Silhouette des letzten Frames, in
     * dem die Maus drin war: eine Geisterlinie, die beim Abspielen neben der Figur stehen bleibt.
     *
     * @return ob der Stencil trotzdem gefüllt werden soll
     */
    public static boolean ohneMaus(StencilFormFramebuffer stencil)
    {
        stencil.clearPicking();

        return aktiv();
    }

    /**
     * Ob der Umriss überhaupt gemalt wird.
     *
     * <p>⛔ Einstellungsfelder entstehen beim Start. Nach einem Hotswap dieser Klasse in eine
     * Instanz, die sie noch nicht kennt, wirft der Zugriff NoSuchFieldError — und zwar in JEDEM
     * Frame, was die Instanz sofort reisst (am 2026-07-29 genau so passiert). Ein Umriss ist es
     * nicht wert, dass die Arbeit des Nutzers stirbt: im Zweifel nichts malen.</p>
     */
    public static boolean aktiv()
    {
        try
        {
            return BBSSettings.editorReplayOutline.get();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Die Beschriftung dessen, worüber die Maus steht — mit Alt zeigt sie den Replay-Namen,
     * sonst die Form unter dem Zeiger.
     *
     * <p>Aus {@code UIFilmController#renderPickingPreview} herausgelöst: die Datei lag über der
     * Monolith-Schwelle, und dieser Block hat mit dem Zeichnen des Stencils nichts zu tun — er
     * beschriftet nur, was der Stencil ergeben hat.</p>
     *
     * @return der Index des überfahrenen Replays, oder -1
     */
    public static int beschriftung(UIContext context, UIFilmPanel panel, boolean altPressed,
        int index, int replayOffset, int gewaehlterReplay, Pair<Form, String> gepickt)
    {
        int ueberfahren = -1;

        if (altPressed)
        {
            int stencilIndex = index - replayOffset;

            if (stencilIndex >= 0 && stencilIndex < panel.getData().replays.getList().size() && stencilIndex != gewaehlterReplay)
            {
                ueberfahren = stencilIndex;

                context.batcher.textCard(panel.getData().replays.getList().get(stencilIndex).getName(),
                    context.mouseX + 12, context.mouseY + 8);

                return ueberfahren;
            }
        }

        if (gepickt != null && gepickt.a != null)
        {
            String label = gepickt.a.getFormIdOrName();

            if (!gepickt.b.isEmpty())
            {
                label += " - " + gepickt.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }

        return ueberfahren;
    }
}
