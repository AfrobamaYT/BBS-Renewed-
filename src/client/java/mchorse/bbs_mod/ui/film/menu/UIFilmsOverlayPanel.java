package mchorse.bbs_mod.ui.film.menu;

import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIFilmsOverlayPanel extends UIOverlayPanel
{
    private static String stateFilmId = "";
    private static boolean stateWithCamera = true;

    public UIStringList films;
    public UIStringList replays;

    public UIToggle withCamera;
    public UIIcon play;

    public UIFilmsOverlayPanel()
    {
        super(UIKeys.FILMS_TITLE);

        this.films = new UIStringList(null);
        this.withCamera = new UIToggle(UIKeys.FILMS_CAMERA, null);

        this.play = new UIIcon(Icons.PLAY, (b) ->
        {
            String filmId = this.films.getCurrentFirst();
            boolean withCamera = this.withCamera.getValue();

            if (filmId != null)
            {
                Films.playFilm(filmId, withCamera);

                this.close();
            }
        });

        this.icons.add(this.play);

        UIElement element = new UIElement();

        element.relative(this.content).xy(6, 6).w(0.5F, -12).h(1F, -12);
        this.withCamera.relative(element).y(1F).w(1F).anchor(0F, 1F);
        this.films.relative(element).w(1F).hTo(this.withCamera.area, 0F, -5);
        element.add(this.withCamera, this.films);

        this.content.add(element);

        ContentType.FILMS.getRepository().requestKeys((keys) ->
        {
            this.films.add(keys);
            this.films.sort();

            this.films.setCurrentScroll(stateFilmId);
        });

        this.withCamera.setValue(stateWithCamera);
        this.keys().register(Keys.PLAUSE, () -> this.play.clickItself());
    }

    @Override
    public void onClose()
    {
        stateFilmId = this.films.getCurrentFirst();
        stateWithCamera = this.withCamera.getValue();

        super.onClose();
    }
}