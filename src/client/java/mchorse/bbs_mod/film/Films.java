package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Films
{
    private List<FilmController> controllers = new ArrayList<FilmController>();

    public void add(FilmController controller)
    {
        this.controllers.add(controller);
    }

    public Film remove(String id)
    {
        Iterator<FilmController> it = this.controllers.iterator();

        while (it.hasNext())
        {
            FilmController next = it.next();

            if (next.film.getId().equals(id))
            {
                it.remove();

                return next.film;
            }
        }

        return null;
    }

    public void update()
    {
        this.controllers.removeIf(FilmController::update);
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (FilmController controller : this.controllers)
        {
            controller.render(context);
        }

        RenderSystem.disableDepthTest();
    }
}