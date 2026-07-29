package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.UIContext;
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
 * <p>Der Balken liegt auf der Spurzeile, die dem {@code layer} des Clips entspricht, und ist
 * genau eine Spur hoch — mit Alt+Mausrad wächst und schrumpft er also mit den Spuren mit
 * (ebenfalls Vorgabe des Users). Gezeichnet wird über den Hintergrund-Haken der Zeitleiste,
 * also VOR Gitter und Keyframes; die Keyframes bleiben oben und behalten den Vorrang bei der
 * Maus.</p>
 */
public class KameraSpuren implements UIKeyframes.SpurZeiger
{
    /** Wie nah an der Kante gegriffen werden muss, um zu kürzen statt zu verschieben. */
    private static final int KANTE = 4;

    private static final int NICHTS = 0;
    private static final int VERSCHIEBEN = 1;
    private static final int LINKS = 2;
    private static final int RECHTS = 3;

    private final UIKeyframes ansicht;
    private final IUIClipsDelegate kamera;

    private final Area balken = new Area();

    private int modus = NICHTS;
    private Clip gezogen;
    private int griffTick;
    private int startTick;
    private int startDauer;

    public KameraSpuren(UIKeyframes ansicht, IUIClipsDelegate kamera)
    {
        this.ansicht = ansicht;
        this.kamera = kamera;
    }

    /* Was gezeichnet wird */

    @Override
    public void zeichne(UIContext context)
    {
        Clips clips = this.clips();

        if (clips == null)
        {
            return;
        }

        Clip gewaehlt = this.kamera.getClip();
        List<Clip> liste = clips.get();

        context.batcher.clip(this.ansicht.graphArea, context);

        for (Clip clip : liste)
        {
            if (this.flaeche(clip, this.balken))
            {
                KameraSpurBalken.zeichne(context, clip, this.balken, clip == gewaehlt);
            }
        }

        context.batcher.unclip(context);
    }

    /* Maus */

    @Override
    public boolean klick(UIContext context)
    {
        if (context.mouseButton != 0 || !this.ansicht.graphArea.isInside(context))
        {
            return false;
        }

        Clip clip = this.treffer(context.mouseX, context.mouseY);

        if (clip == null)
        {
            return false;
        }

        this.kamera.pickClip(clip);

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

    private Clips clips()
    {
        Film film = this.kamera == null ? null : this.kamera.getFilm();

        return film == null ? null : film.camera;
    }

    /** Fläche des Clips auf der Zeitleiste; false, wenn er ganz aus dem Bild fällt. */
    private boolean flaeche(Clip clip, Area ziel)
    {
        int hoehe = (int) this.ansicht.getDopeSheet().getTrackHeight();
        int y = this.ansicht.getDopeSheet().getDopeSheetY() + Math.max(0, clip.layer.get()) * hoehe;
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

        if (clips == null)
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
