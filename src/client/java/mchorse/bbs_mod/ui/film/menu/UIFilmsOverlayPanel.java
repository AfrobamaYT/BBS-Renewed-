package mchorse.bbs_mod.ui.film.menu;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIFilmsOverlayPanel extends UIOverlayPanel
{
    private static String stateFilmId;
    private static boolean stateWithCamera = true;
    private static int stateReplayId = -1;

    public UISearchList<String> films;
    public UIStringList replays;

    public UIToggle withCamera;
    public UIIcon play;
    public UIIcon record;
    public UIIcon command;

    public UIFilmsOverlayPanel()
    {
        super(UIKeys.FILMS_TITLE);

        this.films = new UISearchList<>(new UIStringList((l) ->
        {
            stateReplayId = -1;

            this.setFilm(l.get(0));
        }));
        this.films.label(UIKeys.GENERAL_SEARCH);
        this.films.list.background();
        this.replays = new UIStringList((l) ->
        {
            stateReplayId = this.replays.getIndex();
        });
        this.replays.background().relative(this.content).x(0.5F, 6).w(0.5F, -12).h(1F, -6);

        this.withCamera = new UIToggle(UIKeys.FILMS_CAMERA, null);
        this.play = new UIIcon(Icons.PLAY, (b) ->
        {
            String filmId = this.films.list.getCurrentFirst();
            boolean withCamera = this.withCamera.getValue();

            if (filmId != null)
            {
                Films.playFilm(filmId, withCamera);

                this.close();
            }
        });
        this.play.tooltip(UIKeys.FILMS_PLAY, Direction.LEFT);
        this.record = new UIIcon(Icons.SPHERE, (b) ->
        {
            String filmId = this.films.list.getCurrentFirst();

            if (filmId != null && !filmId.isEmpty() && stateReplayId >= 0)
            {
                BBSModClient.getFilms().startRecording(filmId, stateReplayId);

                this.close();
            }
        });
        this.record.tooltip(UIKeys.FILMS_RECORD, Direction.LEFT);
        this.command = new UIIcon(Icons.CONSOLE, (b) ->
        {
            String filmId = this.films.list.getCurrentFirst();
            boolean withCamera = this.withCamera.getValue();

            if (filmId != null)
            {
                Window.setClipboard("/bbs films @a play " + filmId + " " + withCamera);

                this.getContext().notify(UIKeys.FILMS_PLAY_COMMAND_NOTIFICATION, Colors.mulRGB(Colors.GREEN, 0.75F) | Colors.A100);
            }
        });
        this.command.tooltip(UIKeys.FILMS_PLAY_COMMAND, Direction.LEFT);

        this.icons.add(this.play, this.record, this.command);

        UIElement element = new UIElement();

        element.relative(this.content).xy(6, 0).w(0.5F, -12).h(1F, -6);
        this.withCamera.relative(element).y(1F).w(1F).anchor(0F, 1F);
        this.films.relative(element).w(1F).hTo(this.withCamera.area, 0F, -5);
        element.add(this.withCamera, this.films);

        this.content.add(element, this.replays);

        ContentType.FILMS.getRepository().requestKeys((keys) ->
        {
            this.films.list.add(keys);
            this.films.list.sort();

            this.films.list.setCurrentScroll(stateFilmId);
            this.setFilm(stateFilmId);
        });

        this.withCamera.setValue(stateWithCamera);

        this.keys().register(Keys.PLAUSE, () -> this.play.clickItself());
        this.keys().register(Keys.FILM_CONTROLLER_START_RECORDING, () -> this.record.clickItself());
    }

    private void setFilm(String filmId)
    {
        this.replays.setVisible(false);

        if (filmId != null && !filmId.isEmpty())
        {
            ContentType.FILMS.getRepository().load(filmId, (data) ->
            {
                if (data instanceof Film film)
                {
                    this.setFilm(film);
                }
            });
        }
    }

    private void setFilm(Film film)
    {
        this.replays.setVisible(true);
        this.replays.clear();

        for (Replay replay : film.replays.getList())
        {
            Form form = replay.form.get();

            this.replays.add(form != null ? form.getDisplayName() : "-");
        }

        this.replays.setIndex(stateReplayId);
    }

    @Override
    public void onClose()
    {
        stateFilmId = this.films.list.getCurrentFirst();
        stateWithCamera = this.withCamera.getValue();
        stateReplayId = this.replays.getIndex();

        super.onClose();
    }
}