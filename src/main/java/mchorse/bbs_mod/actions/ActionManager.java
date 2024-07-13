package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ActionManager
{
    private List<ActionPlayer> players = new ArrayList<>();
    private Map<ServerPlayerEntity, ActionRecorder> recorders = new HashMap<>();

    public void play(ServerWorld world, Film film, int tick, int exception)
    {
        this.players.add(new ActionPlayer(world, film, tick, exception));
    }

    public void stop(String filmId)
    {
        Iterator<ActionPlayer> it = this.players.iterator();

        while (it.hasNext())
        {
            ActionPlayer next = it.next();

            if (next.film.getId().equals(filmId))
            {
                next.getDC().restore();
                it.remove();
            }
        }
    }

    public void startRecording(Film film, ServerPlayerEntity entity, int tick)
    {
        this.recorders.put(entity, new ActionRecorder(film, tick));
    }

    public void addAction(ServerPlayerEntity entity, Supplier<ActionClip> supplier)
    {
        ActionRecorder recorder = this.recorders.get(entity);

        if (recorder != null && supplier != null)
        {
            ActionClip actionClip = supplier.get();

            if (actionClip != null)
            {
                recorder.add(actionClip);
            }
        }
    }

    public Clips stopRecording(ServerPlayerEntity entity)
    {
        ActionRecorder remove = this.recorders.remove(entity);
        Clips clips = remove.getClips();

        clips.sortLayers();

        this.stop(remove.getFilm().getId());

        return clips;
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
        this.recorders.clear();
    }

    public void changedBlock(BlockPos pos, BlockState state, BlockEntity blockEntity)
    {
        for (ActionPlayer player : this.players)
        {
            player.getDC().addBlock(pos, state, blockEntity);
        }
    }

    public void spawnedEntity(Entity entity)
    {
        for (ActionPlayer player : this.players)
        {
            player.getDC().addEntity(entity);
        }
    }
}