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
import net.minecraft.client.renderer.ShaderInstance;
import mchorse.bbs_mod.utils.Pair;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;

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
        StencilFormFramebuffer stencil, boolean altGedrueckt, int offset)
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
            return;
        }

        ShaderInstance programm = BBSShaders.getReplayOutlineProgram();

        if (programm == null)
        {
            return;
        }

        int farbe;
        int breite;

        /* Zum try/catch siehe aktiv(): fehlendes Einstellungsfeld darf nichts reissen. */
        try
        {
            if (!aktiv())
            {
                return;
            }

            farbe = BBSSettings.editorReplayOutlineColor.get();
            breite = Math.max(1, BBSSettings.editorReplayOutlineWidth.get());
        }
        catch (Throwable t)
        {
            return;
        }

        Supplier<ShaderInstance> lieferant = BBSShaders::getReplayOutlineProgram;

        setzeInt(programm, "TargetMin", abIndex);
        setzeFarbe(programm, "OutlineColor", farbe);
        setzeInt(programm, "Thickness", breite);

        /* Ein Bildschirmpixel in UV-Einheiten. Die Kachel unten spannt die ganze Textur auf,
         * also ist 1/Breite genau ein Pixel — das muss der Shader wissen, weil der Stencil in
         * Video-Aufloesung liegt und ein Texel dort viel kleiner ist als ein Pixel hier. */
        setzeZwei(programm, "Schritt", 1F / area.w, 1F / area.h);

        int w = texture.width;
        int h = texture.height;

        RenderSystem.enableBlend();
        context.batcher.texturedBox(lieferant, texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, h, w, 0, w, h);
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

    private static void setzeInt(ShaderInstance programm, String name, int wert)
    {
        Uniform uniform = programm.getUniform(name);

        if (uniform != null)
        {
            uniform.set(wert);
        }
    }

    private static void setzeZwei(ShaderInstance programm, String name, float a, float b)
    {
        Uniform uniform = programm.getUniform(name);

        if (uniform != null)
        {
            uniform.set(a, b);
        }
    }

    private static void setzeFarbe(ShaderInstance programm, String name, int farbe)
    {
        Uniform uniform = programm.getUniform(name);

        if (uniform != null)
        {
            uniform.set(Colors.getR(farbe), Colors.getG(farbe), Colors.getB(farbe), Colors.getA(farbe));
        }
    }
}
