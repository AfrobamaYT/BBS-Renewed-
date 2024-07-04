package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Films
{
    private static Map<Integer, Consumer<Clips>> callbacks = new HashMap<>();
    private static int callbackId = 0;

    private List<FilmController> controllers = new ArrayList<FilmController>();
    private Recorder recorder;

    public static void playFilm(String filmId, boolean withCamera)
    {
        ContentType.FILMS.getRepository().load(filmId, (data) ->
        {
            MinecraftClient.getInstance().execute(() ->
            {
                Film film = (Film) data;
                FilmController filmController = new FilmController(film);

                if (withCamera)
                {
                    PlayCameraController controller = new PlayCameraController(film.camera);

                    controller.getContext().entities.addAll(filmController.getEntities());
                    BBSModClient.getCameraController().add(controller);
                }

                BBSModClient.getFilms().add(filmController);
            });
        });
    }

    public static void receivedClips(int callbackId, Clips clips)
    {
        Consumer<Clips> remove = callbacks.remove(callbackId);

        if (remove != null)
        {
            remove.accept(clips);
        }
    }

    public Recorder getRecorder()
    {
        return this.recorder;
    }

    public void startRecording(Film film, int replayId)
    {
        this.recorder = new Recorder(film, replayId);

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionRecording(0, this.recorder.tick, true);
        }
    }

    public Recorder stopRecording(Consumer<Clips> callback)
    {
        Recorder recorder = this.recorder;

        this.recorder = null;

        if (recorder != null)
        {
            for (BaseValue value : recorder.keyframes.getAll())
            {
                if (value instanceof KeyframeChannel channel)
                {
                    channel.simplify();
                }
            }

            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendActionRecording(callbackId, recorder.tick, false);
                callbacks.put(callbackId, callback);

                callbackId += 1;
            }
            else
            {
                callback.accept(null);
            }
        }

        return recorder;
    }

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
        this.controllers.removeIf((film) ->
        {
            film.update();

            return film.hasFinished();
        });

        if (this.recorder != null)
        {
            this.recorder.update();
        }
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        for (FilmController controller : this.controllers)
        {
            controller.render(context);
        }

        if (this.recorder != null)
        {
            this.recorder.render(context);
        }

        RenderSystem.disableDepthTest();
    }
}