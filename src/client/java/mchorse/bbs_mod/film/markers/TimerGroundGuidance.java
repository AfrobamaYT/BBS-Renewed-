package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Samples only real client-tick player state, outside tracker hand/HUD render swaps. */
public final class TimerGroundGuidance
{
    private static boolean registered;
    private static int ticks;
    private static ClientLevel level;
    private static volatile Snapshot snapshot = new Snapshot(null, "", "", 0, List.of(), "");
    private static String targetKey = "";

    public record Target(TimerCue cue, boolean end)
    {}

    public record Snapshot(Film film, String uuid, String label, int color, List<Vec3> points, String error)
    {
        public Snapshot
        {
            points = List.copyOf(points);
        }
    }

    private TimerGroundGuidance()
    {}

    public static void register()
    {
        if (registered)
        {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(TimerGroundGuidance::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(TimerGroundRender::submit);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bbs", "timer_ground_route"), TimerGroundRender::hud);
    }

    public static Snapshot snapshot()
    {
        return snapshot;
    }

    public static String status(Film film, TimerCue cue)
    {
        if (!cue.groundRoute.get())
        {
            return "";
        }
        if (snapshot.film != film || !snapshot.uuid.equals(cue.uuid.get()))
        {
            return "Ground route: waiting for this cue's next marker";
        }

        return snapshot.error.isEmpty() ? "Ground route to " + snapshot.label
            + (snapshot.points.isEmpty() ? " (planning...)" : "")
            : "ERROR: Ground route: " + snapshot.error;
    }

    public static Target target(Film film, int tick)
    {
        return target(film, tick, false);
    }

    public static Target target(Film film, int tick, boolean performing)
    {
        Target target = null;
        int next = Integer.MAX_VALUE;
        boolean active = false;

        for (TimerCue cue : film.timerCues.getList())
        {
            if (!cue.enabled.get() || !cue.groundRoute.get() || tick >= cue.endTick.get())
            {
                continue;
            }
            boolean end = tick > cue.startTick.get() || (performing && tick == cue.startTick.get());
            int boundary = end ? cue.endTick.get() : cue.startTick.get();

            if ((end && !active) || (end == active && boundary < next))
            {
                target = new Target(cue, end);
                next = boundary;
                active = end;
            }
        }

        return target;
    }

    private static void tick(Minecraft client)
    {
        if (client.level != level)
        {
            level = client.level;
            ticks = 0;
            clear();
        }
        if (client.player == null || client.level == null || VideoExportSession.laeuftExport())
        {
            clear();
            return;
        }
        Recorder recorder = BBSModClient.getFilms().getRecorder();
        mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = BBSModClient.peekDashboard();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
        Film film = recorder != null ? recorder.film : panel == null ? null : panel.getData();
        int cursor = recorder != null ? recorder.getTick() : panel == null ? 0 : panel.getCursor();

        if (film == null)
        {
            clear();
            return;
        }
        Target target = target(film, cursor, recorder != null && recorder.countdown <= 0);

        if (target == null)
        {
            clear();
            return;
        }
        String key = target.cue.uuid.get() + ":" + target.end;

        if (snapshot.film != film || !targetKey.equals(key))
        {
            targetKey = key;
            TimerCue cue = target.cue;

            snapshot = new Snapshot(film, cue.uuid.get(), cue.label.get() + (target.end ? " END" : " START"),
                target.end ? cue.endColor.get() : cue.startColor.get(), List.of(), "");
        }
        /* Bound native path searches to at most two per second, including moving markers. */
        if (++ticks % 10 != 1)
        {
            return;
        }
        TimerCue cue = target.cue;
        String label = cue.label.get() + (target.end ? " END" : " START");
        int color = target.end ? cue.endColor.get() : cue.startColor.get();
        String error = TimerCueActions.error(film, cue);
        List<Vec3> points = List.of();

        if (error.isEmpty())
        {
            Replay replay = TimerCueActions.endpoint(film, cue, target.end);
            int tick = replay.getTick(cursor);
            Vec3 goal = new Vec3(replay.keyframes.x.interpolate(tick), replay.keyframes.y.interpolate(tick), replay.keyframes.z.interpolate(tick));
            TimerGroundPath.Result result = TimerGroundPath.find(client.level, client.player, goal);

            points = result.points();
            error = result.error();
        }
        snapshot = new Snapshot(film, cue.uuid.get(), label, color, points, error);
    }

    private static void clear()
    {
        if (snapshot.film != null)
        {
            snapshot = new Snapshot(null, "", "", 0, List.of(), "");
        }
    }
}
