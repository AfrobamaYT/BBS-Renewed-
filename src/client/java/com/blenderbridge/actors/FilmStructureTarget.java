package com.blenderbridge.actors;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

import java.util.List;

/** Resolves and refreshes the live BBS film targeted by a structure write. */
final class FilmStructureTarget
{
    final Film film;
    final UIFilmPanel panel;
    final String source;

    private FilmStructureTarget(Film film, UIFilmPanel panel, String source)
    {
        this.film = film;
        this.panel = panel;
        this.source = source;
    }

    static FilmStructureTarget resolve()
    {
        UIDashboard dashboard = com.blenderbridge.BBSCompat.peekDashboard();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null)
        {
            return new FilmStructureTarget(panel.getData(), panel, "editor");
        }
        if (BBSModClient.getFilms() == null)
        {
            return null;
        }

        List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();

        if (controllers != null)
        {
            for (BaseFilmController controller : controllers)
            {
                if (controller != null && controller.film != null)
                {
                    return new FilmStructureTarget(controller.film, null, "playing");
                }
            }
        }

        return null;
    }

    void refreshAndSave()
    {
        if (this.panel == null)
        {
            return;
        }
        if (this.panel.getUndoHandler() != null)
        {
            this.panel.getUndoHandler().submitUndo();
        }
        if (this.panel.replayEditor != null && this.panel.replayEditor.replaysList != null)
        {
            this.panel.replayEditor.replaysList.replays.refreshReplayList();
        }
        if (this.panel.getController() != null)
        {
            com.blenderbridge.BBSCompat.createEntitiesNow(this.panel.getController());
        }

        this.panel.save();
    }
}
