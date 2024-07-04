package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionManager
{
    private List<ActionPlayer> players = new ArrayList<>();
    private Map<ServerPlayerEntity, ActionRecorder> recorders = new HashMap<>();

    public void add(ServerWorld world, Film film, int tick)
    {
        this.players.add(new ActionPlayer(world, film, tick));
    }

    public void startRecording(ServerPlayerEntity entity, int tick)
    {
        this.recorders.put(entity, new ActionRecorder(tick));
    }

    public void addAction(ServerPlayerEntity entity, ActionClip clip)
    {
        ActionRecorder recorder = this.recorders.get(entity);

        if (recorder != null)
        {
            recorder.add(clip);
        }
    }

    public Clips stopRecording(ServerPlayerEntity entity)
    {
        ActionRecorder remove = this.recorders.remove(entity);

        return remove.getClips();
    }

    public void tick()
    {
        this.players.removeIf(ActionPlayer::tick);

        for (ActionRecorder recorder : this.recorders.values())
        {
            recorder.tick();
        }
    }

    public void reset()
    {
        this.players.clear();
    }
}