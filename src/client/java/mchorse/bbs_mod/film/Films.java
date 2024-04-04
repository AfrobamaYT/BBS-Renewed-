package mchorse.bbs_mod.film;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.ArrayList;
import java.util.List;

public class Films
{
    public List<FilmController> controllers = new ArrayList<FilmController>();

    public void addFilm(Film film)
    {
        this.controllers.add(new FilmController(film));
    }

    public void update()
    {
        this.controllers.removeIf(FilmController::update);
    }

    public void render(WorldRenderContext context)
    {
        for (FilmController controller : this.controllers)
        {
            controller.render(context);
        }
    }
}