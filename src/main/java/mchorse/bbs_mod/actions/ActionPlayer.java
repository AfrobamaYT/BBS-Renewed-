package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;
import net.minecraft.entity.MovementType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionPlayer
{
    public Film film;
    public int tick;
    public boolean playing = true;
    public int exception;
    public boolean syncing;

    private ServerWorld world;
    private int duration;

    private Map<String, ActorEntity> actors = new HashMap<>();

    public ActionPlayer(ServerWorld world, Film film, int tick, int exception)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.exception = exception;

        this.duration = film.camera.calculateDuration();

        this.updateReplayEntities();
    }

    public void updateReplayEntities()
    {
        for (ActorEntity entity : this.actors.values())
        {
            entity.discard();
        }

        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);

            if (!replay.actor.get() || i == this.exception)
            {
                continue;
            }

            ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, this.world);

            double x = replay.keyframes.x.interpolate(0F);
            double y = replay.keyframes.y.interpolate(0F);
            double z = replay.keyframes.z.interpolate(0F);
            float yawHead = replay.keyframes.headYaw.interpolate(0F).floatValue() + 180F;
            float yawBody = replay.keyframes.bodyYaw.interpolate(0F).floatValue();
            float pitch = replay.keyframes.pitch.interpolate(0F).floatValue();

            actor.setPosition(x, y, z);
            actor.setYaw(yawHead);
            actor.setHeadYaw(yawHead);
            actor.setPitch(pitch);
            actor.setBodyYaw(yawBody);
            actor.setForm(FormUtils.copy(replay.form.get()));
            actor.setSneaking(replay.keyframes.sneaking.interpolate(0F) > 0);

            this.actors.put(replay.getId(), actor);
            this.world.spawnEntity(actor);
        }
    }

    public ServerWorld getWorld()
    {
        return this.world;
    }

    public boolean tick()
    {
        for (Map.Entry<String, ActorEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                double x = replay.keyframes.x.interpolate(this.tick);
                double y = replay.keyframes.y.interpolate(this.tick);
                double z = replay.keyframes.z.interpolate(this.tick);
                float yawHead = replay.keyframes.headYaw.interpolate(this.tick).floatValue() + 180F;
                float yawBody = replay.keyframes.bodyYaw.interpolate(this.tick).floatValue();
                float pitch = replay.keyframes.pitch.interpolate(this.tick).floatValue();
                ActorEntity actor = entry.getValue();

                Vec3d pos = actor.getPos();

                actor.move(MovementType.SELF, new Vec3d(x - pos.x, y - pos.y, z - pos.z));
                actor.setPosition(x, y, z);
                actor.setYaw(yawHead);
                actor.setHeadYaw(yawHead);
                actor.setPitch(pitch);
                actor.setBodyYaw(yawBody);
                actor.setSneaking(replay.keyframes.sneaking.interpolate(this.tick) > 0);

                actor.fallDistance = replay.keyframes.fall.interpolate(this.tick).floatValue();
            }
        }

        if (!this.playing)
        {
            return false;
        }

        if (this.tick >= 0)
        {
            this.applyAction();
        }

        this.tick += 1;

        return !this.syncing ? this.tick >= this.duration : false;
    }

    private void applyAction()
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            if (i == this.exception)
            {
                continue;
            }

            Replay replay = list.get(i);

            replay.applyActions(fakePlayer, this.film, this.tick);
        }
    }

    public void syncData(String key, BaseType data)
    {
        BaseValue baseValue = this.film.getRecursively(new DataPath(key));

        if (baseValue != null)
        {
            baseValue.fromData(data);

            if (baseValue.getId().equals("actor") || baseValue.getId().equals("replays"))
            {
                this.updateReplayEntities();
            }
        }
    }

    public void goTo(int tick)
    {
        if (this.tick != tick)
        {
            while (this.tick != tick)
            {
                this.tick += this.tick > tick ? -1 : 1;

                this.applyAction();
            }
        }
    }

    public void stop()
    {
        for (ActorEntity value : this.actors.values())
        {
            value.discard();
        }
    }
}