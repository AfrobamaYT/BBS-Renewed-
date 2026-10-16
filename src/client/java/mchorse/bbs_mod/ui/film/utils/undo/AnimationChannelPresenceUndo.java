package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.undo.IUndo;

/** Undo a newly authored FormProperties track without replacing unrelated animation channels. */
public final class AnimationChannelPresenceUndo extends FilmEditorUndo
{
    private final String replayUuid;
    private final String channel;
    private final MapType after;

    public AnimationChannelPresenceUndo(Replay replay, KeyframeChannel<?> channel)
    {
        this(replay.uuid.get(), channel.getId(), channel.toData().copy().asMap());
    }

    private AnimationChannelPresenceUndo(String replayUuid, String channel, MapType after)
    {
        if (replayUuid.isBlank() || channel.isBlank() || !after.has("keyframes") || !after.get("keyframes").isList())
            throw new IllegalArgumentException("Animation track checkpoint requires an actor, channel and native keyframes");
        this.replayUuid = replayUuid;
        this.channel = channel;
        this.after = after.copy().asMap();
        this.factory();
    }

    private IKeyframeFactory<?> factory()
    {
        IKeyframeFactory<?> factory = KeyframeFactories.FACTORIES.get(this.after.getString("type"));
        if (factory == null) throw new IllegalStateException("Animation track checkpoint has an unknown value factory");
        return factory;
    }

    private Replay replay(Film film)
    {
        return film.replays.getList().stream().filter(replay -> replay.uuid.get().equals(this.replayUuid))
            .findFirst().orElseThrow(() -> new IllegalStateException("Track actor no longer exists: " + this.replayUuid));
    }

    public void preflight(Film film)
    {
        Replay replay = this.replay(film);
        KeyframeChannel<?> existing = replay.properties.properties.get(this.channel);
        if (existing != null && existing.getFactory() != this.factory())
            throw new IllegalStateException("Track value factory changed: " + this.channel);
        /* The native edit guard also protects leased bone tracks that are currently absent. */
        KeyframeChannel<?> target = existing == null ? replay.properties.registerChannel(this.channel, this.factory()) : existing;
        try
        {
            if (Film.editGuard != null) Film.editGuard.accept(film, target);
        }
        finally
        {
            if (existing == null) remove(replay, this.channel);
        }
    }

    private static void remove(Replay replay, String name)
    {
        KeyframeChannel<?> existing = replay.properties.properties.remove(name);
        if (existing != null) replay.properties.remove(existing);
    }

    @Override
    public void undo(ValueGroup context)
    {
        remove(this.replay((Film) context), this.channel);
    }

    @Override
    public void redo(ValueGroup context)
    {
        this.replay((Film) context).properties.registerChannel(this.channel, this.factory()).fromData(this.after.copy());
    }

    public MapType toData()
    {
        MapType data = new MapType();
        data.putString("replay_uuid", this.replayUuid);
        data.putString("channel", this.channel);
        data.put("after", this.after.copy());
        return data;
    }

    public static AnimationChannelPresenceUndo fromData(MapType data)
    {
        for (String key : data.keys())
            if (!java.util.Set.of("t", "replay_uuid", "channel", "after").contains(key))
                throw new IllegalArgumentException("Unknown animation track checkpoint field: " + key);
        BaseType after = data.get("after");
        if (after == null || !after.isMap()) throw new IllegalArgumentException("Missing native animation track data");
        return new AnimationChannelPresenceUndo(data.getString("replay_uuid"), data.getString("channel"), after.asMap());
    }

    @Override
    public IUndo<ValueGroup> noMerging() { return this; }

    @Override
    public boolean isMergeable(IUndo<ValueGroup> undo) { return false; }

    @Override
    public void merge(IUndo<ValueGroup> undo) { throw new IllegalStateException("Track creation cannot merge"); }
}
