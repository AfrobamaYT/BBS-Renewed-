package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * Der WELT-Weg zum Replay-Waehler: welchen Film man gerade meint, und was ein Klick bewirkt.
 *
 * <p>Steht bewusst nicht in {@link BBSModClient}. Dort liegen nur die vier Zeilen, die eine
 * Vanilla-Taste braucht (Feld, {@code createKey}, Getter, {@code wasPressed}-Schleife) — die
 * Frage "welcher Film ist gemeint" ist eine eigene Sache und gehoert neben den Waehler, den
 * sie fuellt.</p>
 *
 * <h2>Warum die Film-Suche drei Stufen hat</h2>
 *
 * <p>In der Welt gibt es kein offenes Panel, das einem sagt, woran man arbeitet. Also der
 * Reihe nach: der Film im Editor-Panel, dann der gerade aufgenommene, dann jeder laufende
 * Controller. Erster brauchbarer gewinnt.</p>
 *
 * <p>"Brauchbar" heisst: hat ueberhaupt Replays, UND enthaelt das gerade gewaehlte — sonst
 * zeigte die Box die Replays eines fremden Films, und die Auswahl liefe ins Leere.</p>
 */
public final class QuickReplayWelt
{
    private QuickReplayWelt()
    {
    }

    /**
     * Waehler in der Welt oeffnen. Ohne brauchbaren Film passiert nichts — eine leere Box
     * waere nur ein Sichtblocker, den man wieder wegklicken muss.
     */
    public static void oeffnen()
    {
        UIFilmPanel panel = filmPanel();
        Replay gewaehlt = panel == null ? null : panel.replayEditor.getReplay();
        Film film = findeFilm(panel, gewaehlt);

        if (film == null)
        {
            return;
        }

        UIQuickReplayOverlayPanel.open(new UIQuickReplayOverlayPanel(
            film.replays.getList(),
            gewaehlt,
            QuickReplayWelt::uebernehmen
        ));
    }

    /**
     * Die Auswahl wirkt im Editor: {@code setReplay} ist der kanonische Weg, das
     * Bearbeitungsziel zu wechseln. Oeffnet man danach das Dashboard, steht man beim
     * gewaehlten Replay — ohne dass das Auswaehlen einen dorthin gezogen haette.
     */
    private static void uebernehmen(Replay replay)
    {
        UIFilmPanel panel = filmPanel();

        if (panel != null && panel.getData() != null && panel.getData().replays.getList().contains(replay))
        {
            panel.replayEditor.setReplay(replay);
        }
    }

    /**
     * ⚠ {@code getDashboardIfCreated()}, NICHT {@code getDashboard()}: letzteres BAUT das
     * Dashboard, wenn es noch keins gibt. Ein Tastendruck in der Welt soll nichts anlegen —
     * und ohne Dashboard gibt es ohnehin kein Panel, dessen Film man meinen koennte.
     */
    private static UIFilmPanel filmPanel()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        return dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
    }

    private static Film findeFilm(UIFilmPanel panel, Replay gewaehlt)
    {
        if (panel != null && brauchbar(panel.getData(), gewaehlt))
        {
            return panel.getData();
        }

        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null && brauchbar(recorder.film, gewaehlt))
        {
            return recorder.film;
        }

        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            if (brauchbar(controller.film, gewaehlt))
            {
                return controller.film;
            }
        }

        return null;
    }

    private static boolean brauchbar(Film film, Replay gewaehlt)
    {
        if (film == null || film.replays.getList().isEmpty())
        {
            return false;
        }

        return gewaehlt == null || film.replays.getList().contains(gewaehlt);
    }
}
