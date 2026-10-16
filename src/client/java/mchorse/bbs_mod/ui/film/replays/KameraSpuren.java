package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.SpurZeiger;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.List;

/**
 * Die Kamera-Clips als Balken in der Zeitleiste des Replay-Editors — sehen, auswählen,
 * verschieben, kürzen, ohne in den Kamera-Tab zu wechseln.
 *
 * <p>USER-WUNSCH 2026-07-29: „das man andauern für die camera clips zum camera editor switchen
 * muss … es soll im Endeffekt dafür dienen den Workflow zu beschleunigen indem man nicht
 * konstant die taps wechseln muss."</p>
 *
 * <p>Der Balken liegt auf der Spurzeile, die sich aus der Ebenen-Reihenfolge ergibt (siehe
 * {@link #zeile}) — NICHT auf der Zeile mit der Nummer der Ebene —, und ist
 * genau eine Spur hoch — mit Alt+Mausrad wächst und schrumpft er also mit den Spuren mit
 * (ebenfalls Vorgabe des Users). Gezeichnet wird über den Hintergrund-Haken der Zeitleiste,
 * also VOR Gitter und Keyframes; die Keyframes bleiben oben und behalten den Vorrang bei der
 * Maus.</p>
 */
public class KameraSpuren implements SpurZeiger
{
    /** Wie nah an der Kante gegriffen werden muss, um zu kürzen statt zu verschieben. */
    private static final int KANTE = 4;

    private static final int NICHTS = 0;
    private static final int VERSCHIEBEN = 1;
    private static final int LINKS = 2;
    private static final int RECHTS = 3;

    private final UIKeyframes ansicht;
    private final UIClipsPanel kamera;

    /** Für die Clip-Eigenschaften im Dock — beide null lassen heißt: kein Dock, nur Balken. */
    private UIKeyframeEditor keyframeEditor;
    private UIElement editor;

    private final Area balken = new Area();

    private int modus = NICHTS;
    private Clip gezogen;
    private int griffTick;
    private int startTick;
    private int startDauer;

    public KameraSpuren(UIKeyframes ansicht, UIClipsPanel kamera)
    {
        this.ansicht = ansicht;
        this.kamera = kamera;
    }

    /** Ohne das hier zeichnet die Spur nur Balken; damit zeigt ein Klick auch die Eigenschaften. */
    public KameraSpuren dock(UIKeyframeEditor keyframeEditor, UIElement editor)
    {
        this.keyframeEditor = keyframeEditor;
        this.editor = editor;

        return this;
    }

    /* Was gezeichnet wird */

    /**
     * Die Clips EINER Spurzeile, aufgerufen aus der Zeile selbst — direkt nachdem sie ihren
     * Hintergrund gemalt hat und bevor sie ihre Keyframes zeichnet.
     *
     * <p>USER-EINWAND 2026-07-29 zur ersten Fassung: „ich wollte ja das es hinter den keyframes
     * selbst displayed wird weil jetzt verdeckt es sie nur." Genau daran lag es: der Balken lag
     * über der ganzen Zeile, also auch über deren Keyframes. Ein Durchgang nach allen Spuren
     * kann das nicht lösen — die Zeile malt Hintergrund UND Keyframes in einem Zug, dazwischen
     * kommt man nur von innen.</p>
     */
    @Override
    public void zeichneZeile(UIContext context, int zeile, int y, int hoehe)
    {
        Clips clips = this.clips();

        if (clips == null || !an())
        {
            return;
        }

        Clip gewaehlt = this.kamera.getClip();

        for (Clip clip : clips.get())
        {
            if (this.zeile(clips, clip) != zeile)
            {
                continue;
            }

            int x = this.ansicht.toGraphX(clip.tick.get());
            int breite = Math.max(KameraSpurBalken.MIN_BREITE, this.ansicht.toGraphX(clip.tick.get() + clip.duration.get()) - x);

            this.balken.set(x, y, breite, hoehe);

            KameraSpurBalken.zeichne(context, this.kamera.clips, clip, this.balken, clip == gewaehlt);

            /* The trim bracket of the camera timeline (UIClips#renderClips): the grab worked
             * here, its bracket was never drawn (owner, 2026-10-14). Same zones as griffModus. */
            Area b = new Area(x, y, breite, hoehe);
            int m = this.gezogen == clip ? this.modus
                : !b.isInside(context) || Window.isCtrlPressed() || b.w <= KANTE * 3 ? NICHTS
                : context.mouseX <= b.x + KANTE ? LINKS : context.mouseX >= b.ex() - KANTE ? RECHTS : NICHTS;
            int farbe = this.gezogen == clip ? mchorse.bbs_mod.utils.colors.Colors.WHITE : mchorse.bbs_mod.utils.colors.Colors.A50;

            if (m == LINKS)
            {
                context.batcher.icon(mchorse.bbs_mod.ui.utils.icons.Icons.CLIP_HANLDE_LEFT, farbe, b.x, b.my(), 0F, 0.5F);
            }
            else if (m == RECHTS)
            {
                context.batcher.icon(mchorse.bbs_mod.ui.utils.icons.Icons.CLIP_HANLDE_RIGHT, farbe, b.ex(), b.my(), 1F, 0.5F);
            }
        }
    }

    /* Maus */

    @Override
    public boolean klick(UIContext context)
    {
        if (context.mouseButton == 1 && an() && this.ansicht.graphArea.isInside(context))
        {
            return this.menue(context);
        }

        if (context.mouseButton != 0 || !an() || !this.ansicht.graphArea.isInside(context))
        {
            return false;
        }

        /* ⛔ Strg+Klick legt einen Keyframe an, Alt+Klick dupliziert eine Spalte — beide Gesten
         * gehören der Keyframe-Ansicht (UIKeyframes#subMouseClicked). Der Kamera-Balken darf sie
         * NICHT abfangen: sonst wählt ein Strg+Klick über einem Clip nur den Clip aus, und man
         * kann an dieser Stelle keinen Keyframe mehr setzen. Vom User gemeldet 2026-07-29:
         * „wenn ich über einem camera clip ein keyframe erstellen will dann selected er den
         * camera clip und ich kann kein keyframe drüber legen." */
        if (Window.isCtrlPressed() || Window.isAltPressed())
        {
            return false;
        }

        Clip clip = this.treffer(context.mouseX, context.mouseY);

        if (clip == null)
        {
            return false;
        }

        /* ⛔ NICHT kamera.pickClip(clip) — das setzt nur den ANGEZEIGTEN Clip. Die Kürzel der
         * Kamera-Zeitleiste arbeiten aber alle über getClipsFromSelection(), und die Auswahl
         * bliebe dabei leer: „Toggle enable" feuerte messbar und tat nichts, weil die Liste
         * leer war (gemessen 2026-07-29, docs/REIBUNGEN.md R12). UIClips#pickClip setzt beides
         * — Auswahl und Anzeige — und ist derselbe Weg, den ein Klick in der Kamera-Leiste geht. */
        this.kamera.clips.pickClip(clip);

        /* Die Eigenschaften des Clips ins Dock, wo sonst die Knochen-Werte eines Pose-Keyframes
         * stehen — ausdrücklicher Wunsch des Users. */
        KameraClipDock.zeige(this.editor, this.keyframeEditor, this.kamera.filmPanel, clip);

        this.gezogen = clip;
        this.startTick = clip.tick.get();
        this.startDauer = clip.duration.get();
        this.griffTick = this.tickBei(context.mouseX);
        this.modus = this.griffModus(context.mouseX, clip);

        /* Ein Zug ist eine Bearbeitung für sich — sonst verschmilzt er im Rückgängig-Stapel
         * mit dem, was der Nutzer davor getan hat. */
        this.kamera.markLastUndoNoMerging();

        return true;
    }

    /**
     * Right click on a bar: enable/disable and remove, the two actions the camera timeline offers
     * that could only be reached by switching back to it (owner, 2026-10-14). The clip gets
     * selected first, so both run through the camera timeline's own selection.
     */
    private boolean menue(UIContext context)
    {
        Clip clip = this.treffer(context.mouseX, context.mouseY);

        if (clip == null)
        {
            return false;
        }

        this.kamera.clips.pickClip(clip);
        context.replaceContextMenu((menu) ->
        {
            menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.DISABLED, mchorse.bbs_mod.ui.UIKeys.CAMERA_TIMELINE_KEYS_ENABLED, () ->
            {
                clip.enabled.set(!clip.enabled.get());
                this.kamera.fillData();
            });
            menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.REMOVE, mchorse.bbs_mod.ui.UIKeys.CAMERA_TIMELINE_CONTEXT_REMOVE_CLIPS,
                mchorse.bbs_mod.utils.colors.Colors.NEGATIVE, () ->
            {
                Clips clips = this.clips();

                if (clips != null)
                {
                    clips.remove(clip);
                    this.kamera.clips.pickClip(null);
                }
            });
        });

        return true;
    }

    @Override
    public void zug(UIContext context)
    {
        if (this.modus == NICHTS || this.gezogen == null)
        {
            return;
        }

        int diff = this.tickBei(context.mouseX) - this.griffTick;

        if (this.modus == VERSCHIEBEN)
        {
            this.gezogen.tick.set(Math.max(0, this.startTick + diff));
        }
        else if (this.modus == LINKS)
        {
            /* Die linke Kante zieht den Anfang, ohne das Ende mitzunehmen: Tick und Dauer
             * ändern sich gegenläufig. Mindestens ein Tick bleibt stehen. */
            int neu = Math.max(0, Math.min(this.startTick + diff, this.startTick + this.startDauer - 1));

            this.gezogen.tick.set(neu);
            this.gezogen.duration.set(this.startTick + this.startDauer - neu);
        }
        else if (this.modus == RECHTS)
        {
            this.gezogen.duration.set(Math.max(1, this.startDauer + diff));
        }
    }

    @Override
    public void losgelassen(UIContext context)
    {
        this.modus = NICHTS;
        this.gezogen = null;
    }

    /* Rechnerei */

    /**
     * ⚠ Neues statisches Feld: nach einem Hotswap steht dort null, bis die Instanz neu
     * startet. Im Zweifel an — das ist der bestellte Default.
     */
    public static boolean an()
    {
        try
        {
            return BBSSettings.editorCameraClipsInReplays.get();
        }
        catch (Throwable t)
        {
            return true;
        }
    }

    /**
     * Welche Spurzeile ein Clip bekommt.
     *
     * <p>⛔ NICHT die Ebenennummer. Die Kamera-Zeitleiste zählt Ebenen VON UNTEN
     * ({@code UIClips#toLayerY}: {@code ey() - MARGIN - (layer + 1) * h}), und sie sind beliebig
     * hoch nummeriert — im Film des Users liegen alle Clips auf Ebene 15. Als Zeile von oben
     * gelesen landen sie fünfzehn Zeilen tief, also weit unterhalb dessen, was die Kamera-Leiste
     * zeigt. Genau das hat der User gemeldet: „im camera editor sind die clips auf der vierten
     * zeile aber beim replay editor viel viel weiter unten."</p>
     *
     * <p>Deshalb werden die BENUTZTEN Ebenen gepackt, die oberste zuerst: die Reihenfolge bleibt
     * wie in der Kamera-Leiste (höhere Ebene = weiter oben), aber es entstehen keine Lücken, und
     * die Balken bleiben sichtbar, egal wie hoch die Ebenen nummeriert sind.</p>
     */
    private int zeile(Clips clips, Clip clip)
    {
        /* Row == layer, as the camera head rows are built (SpurGruppen: one "Camera n" per layer,
         * Camera 1 = layer 0). This counted the used layers ABOVE instead, so the order ran the
         * other way: a tracker on layer 0 under thirteen voice-over layers sat in the last row and
         * "Camera 1" showed a voice-over (owner, 2026-10-14). */
        return clip.layer.get();
    }

    private Clips clips()
    {
        Film film = this.kamera == null ? null : this.kamera.getFilm();

        return film == null ? null : film.camera;
    }

    /** Fläche des Clips auf der Zeitleiste; false, wenn er ganz aus dem Bild fällt. */
    private boolean flaeche(Clip clip, Area ziel)
    {
        int hoehe = (int) this.ansicht.getDopeSheet().getTrackHeight();
        int y = this.ansicht.getDopeSheet().getDopeSheetY() + this.zeile(this.clips(), clip) * hoehe;
        int x = this.ansicht.toGraphX(clip.tick.get());
        int breite = Math.max(KameraSpurBalken.MIN_BREITE, this.ansicht.toGraphX(clip.tick.get() + clip.duration.get()) - x);

        ziel.set(x, y, breite, hoehe);

        Area sicht = this.ansicht.graphArea;

        return ziel.ex() >= sicht.x && ziel.x <= sicht.ex() && ziel.ey() >= sicht.y && ziel.y <= sicht.ey();
    }

    /**
     * Der Clip unter der Maus — von hinten nach vorn gesucht, damit der zuletzt gezeichnete
     * (oben liegende) gewinnt, so wie es das Auge erwartet.
     */
    private Clip treffer(int mouseX, int mouseY)
    {
        Clips clips = this.clips();

        /* Not in the ruler band: a scrolled dope sheet lifts the bars' hit box under it, and a click
         * meant for the playhead picked the camera clip instead (2026-10-16) */
        if (clips == null || mouseY < this.ansicht.graphArea.y + mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph.TOP_MARGIN)
        {
            return null;
        }

        List<Clip> liste = clips.get();

        for (int i = liste.size() - 1; i >= 0; i--)
        {
            Clip clip = liste.get(i);

            if (this.flaeche(clip, this.balken) && this.balken.isInside(mouseX, mouseY))
            {
                return clip;
            }
        }

        return null;
    }

    private int griffModus(int mouseX, Clip clip)
    {
        this.flaeche(clip, this.balken);

        if (this.balken.w > KANTE * 3)
        {
            if (mouseX <= this.balken.x + KANTE)
            {
                return LINKS;
            }

            if (mouseX >= this.balken.ex() - KANTE)
            {
                return RECHTS;
            }
        }

        return VERSCHIEBEN;
    }

    private int tickBei(int mouseX)
    {
        return (int) Math.round(this.ansicht.fromGraphX(mouseX));
    }
}
