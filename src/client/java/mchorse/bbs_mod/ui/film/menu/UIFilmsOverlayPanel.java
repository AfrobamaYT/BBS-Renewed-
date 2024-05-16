package mchorse.bbs_mod.ui.film.menu;

import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
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
    private static String stateFilmId = "";
    private static boolean stateWithCamera = true;

    public UISearchList<String> films;
    public UIStringList replays;

    public UIToggle withCamera;
    public UIIcon play;
    public UIIcon command;

    public UIFilmsOverlayPanel()
    {
        super(UIKeys.FILMS_TITLE);

        this.films = new UISearchList<>(new UIStringList(null));
        this.films.label(UIKeys.GENERAL_SEARCH);
        this.films.list.background();
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

        this.icons.add(this.play, this.command);

        UIElement element = new UIElement();

        element.relative(this.content).xy(6, 0).w(0.5F, -12).h(1F, -6);
        this.withCamera.relative(element).y(1F).w(1F).anchor(0F, 1F);
        this.films.relative(element).w(1F).hTo(this.withCamera.area, 0F, -5);
        element.add(this.withCamera, this.films);

        this.content.add(element);

        ContentType.FILMS.getRepository().requestKeys((keys) ->
        {
            this.films.list.add(keys);
            this.films.list.sort();

            this.films.list.setCurrentScroll(stateFilmId);
        });

        this.withCamera.setValue(stateWithCamera);
        this.keys().register(Keys.PLAUSE, () -> this.play.clickItself());
    }

    @Override
    public void onClose()
    {
        stateFilmId = this.films.list.getCurrentFirst();
        stateWithCamera = this.withCamera.getValue();

        super.onClose();
    }
}