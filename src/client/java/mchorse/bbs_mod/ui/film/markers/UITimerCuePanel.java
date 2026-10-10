package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.TimerCue;
import mchorse.bbs_mod.film.markers.TimerCueActions;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/** Reachable viewport sidebar: create/configure cues and select their native transform gizmos. */
public class UITimerCuePanel extends UIElement implements UISeitenleiste.Auffrischbar
{
    public final UIList<TimerCue> list;
    public final UIButton create;
    public final UIText status;
    public final UITimerCueFields fields;
    private final UIFilmPanel panel;
    private String selected = "";
    private String snapshot = "";
    private String editError = "";

    public UITimerCuePanel(UIFilmPanel panel)
    {
        mchorse.bbs_mod.film.markers.TimerGroundGuidance.register();
        this.panel = panel;
        this.create = new UIButton(IKey.raw("+ 3D Timer"), (button) -> this.createCue());
        this.list = new UIList<>((items) ->
        {
            this.selected = items.isEmpty() ? "" : items.get(0).uuid.get();
            this.snapshot = "";
            this.editError = "";
            this.auffrischen();
        })
        {
            @Override
            protected String elementToString(UIContext context, int index, TimerCue cue)
            {
                return cue.label.get();
            }

            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 1 && this.area.isInside(context))
                {
                    int index = this.getIndexAtCursor(context);

                    if (index >= 0)
                    {
                        TimerCue cue = this.list.get(index);

                        this.setCurrent(cue);
                        this.callback.accept(java.util.List.of(cue));
                    }
                }
                return super.subMouseClicked(context);
            }
        };
        this.list.h(80);
        this.list.background();
        this.list.context((menu) ->
        {
            TimerCue cue = this.selectedCue();
            if (cue != null)
            {
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.VISIBLE, IKey.raw("Show timer"), cue.enabled.get(),
                    () -> this.edit((value) -> value.enabled.set(!value.enabled.get())));
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.REMOVE, IKey.raw("Delete timer pair"), this::removeCue);
            }
        });
        this.status = new UIText();
        this.fields = new UITimerCueFields(this);
        this.column(5).vertical().stretch().padding(4);
        this.add(this.create, this.list, this.fields, this.status);
        this.auffrischen();
    }

    public TimerCue selectedCue()
    {
        Film film = this.panel.getData();

        return film == null ? null : film.timerCues.find(this.selected);
    }

    public void createCue()
    {
        UITimerPlacement.find(this.panel).begin();
    }

    void createAt(int tick, Point start, Point end)
    {
        this.fence();
        TimerCue cue = TimerCueActions.create(this.panel.getData(), tick, start, end);

        this.selected = cue.uuid.get();
        this.persist(true);
        this.selectEndpoint(false);
    }

    void selectCue(String uuid)
    {
        if (!uuid.equals(this.selected))
        {
            this.selected = uuid;
            this.snapshot = "";
            this.auffrischen();
        }
    }

    void edit(Consumer<TimerCue> change)
    {
        TimerCue cue = this.selectedCue();

        if (cue != null)
        {
            this.fence();
            BaseValue.edit(cue, change);
            this.editError = "";
            this.persist(false);
        }
    }

    void rename(String name)
    {
        this.edit((cue) -> TimerCueActions.rename(this.panel.getData(), cue, name));
    }

    public void setTime(boolean end, double seconds)
    {
        TimerCue cue = this.selectedCue();
        long tick = Math.round(seconds * 20D);

        if (cue == null)
        {
            return;
        }
        if (!Double.isFinite(seconds) || tick < 0 || tick > Integer.MAX_VALUE
            || (end ? tick <= cue.startTick.get() : tick >= cue.endTick.get()))
        {
            this.editError = "ERROR: End must be after start";
            this.snapshot = "";
            this.auffrischen();

            return;
        }

        this.edit((value) ->
        {
            if (end)
            {
                value.endTick.set((int) tick);
            }
            else
            {
                value.startTick.set((int) tick);
            }
        });
    }

    void usePlayhead(boolean end)
    {
        this.setTime(end, this.panel.getCursor() / 20D);
    }

    public void selectEndpoint(boolean end)
    {
        TimerCue cue = this.selectedCue();
        Replay replay = cue == null ? null : TimerCueActions.endpoint(this.panel.getData(), cue, end);

        if (replay == null)
        {
            this.editError = "ERROR: Missing " + (end ? "END" : "START") + " replay";
            this.snapshot = "";
            this.auffrischen();

            return;
        }

        this.panel.replayEditor.setReplay(replay);
        this.panel.replayEditor.replaysList.replays.scrollToReplay(replay);
        KeyframeChannel<Transform> channel = replay.properties.getOrCreate(replay.form.get(), "transform");

        if (channel.getKeyframes().isEmpty())
        {
            this.fence();
            channel.insert(0, replay.form.get().transform.get().copy());
            this.persist(false);
        }
        this.panel.replayEditor.updateChannelsList();
        for (UIKeyframeSheet sheet : this.panel.replayEditor.keyframeEditor.view.getGraph().getSheets())
        {
            if (sheet.channel == channel)
            {
                this.panel.replayEditor.keyframeEditor.view.getGraph().clearSelection();
                sheet.selection.add(channel.find(this.panel.getCursor()).getClosest());
                this.panel.replayEditor.keyframeEditor.view.pickKeyframe(channel.find(this.panel.getCursor()).getClosest());
                return;
            }
        }
        this.editError = "ERROR: Marker transform track is not available";
        this.snapshot = "";
        this.auffrischen();
    }

    public void removeCue()
    {
        TimerCue cue = this.selectedCue();

        if (cue != null)
        {
            this.fence();
            try
            {
                TimerCueActions.remove(this.panel.getData(), cue);
            }
            catch (IllegalArgumentException exception)
            {
                this.editError = "ERROR: " + exception.getMessage();
                this.snapshot = "";
                this.auffrischen();

                return;
            }
            this.selected = "";
            this.editError = "";
            this.persist(true);
        }
    }

    private void fence()
    {
        if (this.panel.getUndoHandler() != null)
        {
            this.panel.getUndoHandler().submitUndo();
            this.panel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
        }
    }

    private void persist(boolean entitiesChanged)
    {
        this.fence();
        if (entitiesChanged)
        {
            this.panel.replayEditor.replaysList.replays.refreshReplayList();
            this.panel.getController().createEntities();
        }
        this.panel.save();
        this.snapshot = "";
        this.auffrischen();
    }

    @Override
    public void auffrischen()
    {
        Film film = this.panel.getData();
        TimerCue cue = this.selectedCue();
        String error = cue == null || film == null ? "" : TimerCueActions.error(film, cue);
        StringBuilder state = new StringBuilder(film == null ? "none" : film.getId());

        if (film != null)
        {
            for (TimerCue item : film.timerCues.getList())
            {
                state.append('|').append(item.uuid.get()).append(':').append(item.label.get())
                    .append(':').append(item.startTick.get()).append(':').append(item.endTick.get())
                    .append(':').append(item.enabled.get()).append(':').append(item.startColor.get())
                    .append(':').append(item.endColor.get()).append(':').append(item.groundRoute.get());
            }
        }
        String navigation = cue == null ? "" : mchorse.bbs_mod.film.markers.TimerGroundGuidance.status(film, cue);
        String snapshot = state.append('|').append(this.selected).append('|').append(error).append('|').append(navigation)
            .append('|').append(this.editError).toString();

        if (snapshot.equals(this.snapshot))
        {
            return;
        }
        this.snapshot = snapshot;
        this.create.setEnabled(film != null);
        this.fields.setVisible(cue != null);
        this.list.setList(film == null ? java.util.List.of() : film.timerCues.getList());
        if (cue != null)
        {
            this.list.setCurrent(cue);
            this.fields.fill(cue);
        }
        String message = !error.isEmpty() ? "ERROR: " + error : !this.editError.isEmpty()
            ? this.editError : !navigation.isEmpty() ? navigation : "Select an endpoint and move it with the regular viewport gizmo. Timers stay out of final videos.";

        this.status.text(message).color(message.startsWith("ERROR:") ? Colors.RED : Colors.WHITE, true);
        /* Reset the owning column's child cursor and update its scroll extent. */
        if (this.getParent() == null)
        {
            this.resize();
        }
        else
        {
            this.getParent().resize();
        }
    }
}
