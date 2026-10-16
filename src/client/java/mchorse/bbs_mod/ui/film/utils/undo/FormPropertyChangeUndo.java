package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.undo.IUndo;

/** A Form's detached native value tree needs an actor identity in its Undo path. */
public final class FormPropertyChangeUndo extends ValueChangeUndo
{
    private final String replayUuid;

    public FormPropertyChangeUndo(Replay replay, BaseValue value, BaseType before, BaseType after)
    {
        this(replay.uuid.get(), relative(value, replay.form.get()), before, after);
    }

    private FormPropertyChangeUndo(String replayUuid, DataPath path, BaseType before, BaseType after)
    {
        super(path, copy(before), copy(after));
        if (replayUuid.isBlank() || path.strings.isEmpty())
            throw new IllegalArgumentException("Form property Undo requires an actor and exact property path");
        this.replayUuid = replayUuid;
        this.noMerging();
    }

    private static BaseType copy(BaseType value)
    {
        return value == null ? null : value.copy();
    }

    private static DataPath relative(BaseValue value, BaseValue root)
    {
        DataPath path = new DataPath(false);
        BaseValue current = value;
        while (current != root && current != null)
        {
            path.strings.add(0, current.getId());
            current = current.getParent();
        }
        if (current == null) throw new IllegalArgumentException("Property does not belong to this actor's Form");
        return path;
    }

    private Replay replay(Film film)
    {
        return film.replays.getList().stream().filter(replay -> replay.uuid.get().equals(this.replayUuid))
            .findFirst().orElseThrow(() -> new IllegalStateException("Form actor no longer exists: " + this.replayUuid));
    }

    private BaseValue value(Film film)
    {
        BaseValue value = this.replay(film).form.get();
        for (String segment : this.name.strings)
        {
            value = value instanceof BaseValueGroup group ? group.get(segment) : null;
            if (value == null) throw new IllegalStateException("Form property no longer exists: " + this.name);
        }
        return value;
    }

    public void preflight(Film film)
    {
        this.value(film);
        /* Lease policy currently guards the actor's Form container conservatively. The
         * checkpoint itself stays at the precise leaf so unrelated Form edits survive. */
        if (Film.editGuard != null) Film.editGuard.accept(film, this.replay(film).form);
    }

    @Override
    public void undo(ValueGroup context)
    {
        this.value((Film) context).fromData(copy(this.oldValue));
    }

    @Override
    public void redo(ValueGroup context)
    {
        this.value((Film) context).fromData(copy(this.newValue));
    }

    public MapType toData()
    {
        MapType data = new MapType();
        data.putString("replay_uuid", this.replayUuid);
        data.putString("path", this.name.toString());
        data.put("before", copy(this.oldValue));
        data.put("after", copy(this.newValue));
        if (this.oldValue == null) data.putBool("before_null", true);
        if (this.newValue == null) data.putBool("after_null", true);
        if (this.uiBefore != null) data.put("ui_before", this.uiBefore);
        if (this.uiAfter != null) data.put("ui_after", this.uiAfter);
        return data;
    }

    public static FormPropertyChangeUndo fromData(MapType data)
    {
        for (String key : data.keys())
            if (!java.util.Set.of("t", "replay_uuid", "path", "before", "after", "before_null", "after_null",
                "ui_before", "ui_after").contains(key))
                throw new IllegalArgumentException("Unknown Form checkpoint field: " + key);
        if ((!data.has("before") && !data.getBool("before_null"))
            || (!data.has("after") && !data.getBool("after_null"))
            || (data.has("before") && data.getBool("before_null"))
            || (data.has("after") && data.getBool("after_null")))
            throw new IllegalArgumentException("Form checkpoint is missing its native before/after data");
        FormPropertyChangeUndo undo = new FormPropertyChangeUndo(data.getString("replay_uuid"),
            new DataPath(data.getString("path")), data.getBool("before_null") ? null : data.get("before"),
            data.getBool("after_null") ? null : data.get("after"));
        if (data.has("ui_before")) undo.uiBefore = data.getMap("ui_before");
        if (data.has("ui_after")) undo.uiAfter = data.getMap("ui_after");
        return undo;
    }

    @Override
    public boolean isMergeable(IUndo<ValueGroup> undo) { return false; }
}
