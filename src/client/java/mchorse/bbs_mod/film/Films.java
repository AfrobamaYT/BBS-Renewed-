package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.ArrayList;
import java.util.List;

public class Films
{
    private List<FilmController> controllers = new ArrayList<FilmController>();

    public void addFilm(FilmController controller)
    {
        this.controllers.add(controller);
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