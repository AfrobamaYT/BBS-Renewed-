// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.VideoSettings;
import mchorse.bbs_mod.ui.film.PanelVideoExportSession;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The film as footage for Resolve's camera clips, so they show the film outside BBS' tab too.
 *
 * <p>Each film has its own media file in Resolve, at the timeline's rate; camera clips are cut from
 * it at their film time. It holds the placeholder or the film's last render (see {@link #media}),
 * and the link has Resolve re-read it when that changes (ReplaceClip): the clips keep what was done
 * to them in Resolve.</p>
 */
public final class DavinciProxy
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final IKey RENDER = L10n.lang("bbs.ui.davinci.proxy.render");
    private static final IKey NO_RESOLVE = L10n.lang("bbs.ui.davinci.proxy.no_resolve");
    private static final IKey STARTED = L10n.lang("bbs.ui.davinci.proxy.started");
    private static final IKey DONE = L10n.lang("bbs.ui.davinci.proxy.done");
    private static final IKey FAILED = L10n.lang("bbs.ui.davinci.proxy.failed");

    private DavinciProxy()
    {}

    /**
     * This film's media for Resolve's camera clips; null until the placeholder exists.
     *
     * <p>In BBS' own Resolve tab it is the placeholder: the tab finds its colour and draws the live
     * BBS viewport there (owner, 2026-10-15: after a render the tab showed a still). In a Resolve
     * of its own it is the last render. Switching copies the source over the file -- a copy, so the
     * file is newer than what Resolve read and the link has Resolve re-read it; the clips stay.</p>
     */
    public static File media(String filmId, String fps)
    {
        File file = file(filmId, fps);
        /* Inline, not a helper: a new method cannot be swapped into the running game. */
        File render = new File(file.getPath().replaceAll("\\.mp4$", ".render.mp4"));
        File placeholder = DavinciPlaceholder.get(fps);

        if (placeholder == null)
        {
            return file.isFile() ? file : null;
        }

        File marker = new File(file.getPath() + ".source");

        try
        {
            /* Before renders had a file of their own the render went into the film's file itself. */
            if (file.isFile() && !marker.isFile() && !render.isFile() && file.length() != placeholder.length())
            {
                Files.move(file.toPath(), render.toPath());
            }

            File source = DavinciScreen.INSTANCE.running() || !render.isFile() ? placeholder : render;
            String wanted = source.getName() + " " + source.lastModified();

            if (!file.isFile() || !marker.isFile() || !Files.readString(marker.toPath()).equals(wanted))
            {
                File part = new File(file.getPath() + ".part.mp4");

                Files.copy(source.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Files.writeString(marker.toPath(), wanted);
                LOGGER.info("[davincilink] camera clips of '{}' now cut from the {}", filmId, source == render ? "render" : "placeholder (live in the tab)");
            }

            return file;
        }
        catch (IOException e)
        {
            LOGGER.error("[davincilink] could not prepare {} for Resolve", file, e);

            return file.isFile() ? file : null;
        }
    }

    /** Renders the open film silently and hands it to Resolve when done. */
    public static void render(UIFilmPanel panel)
    {
        String fps = DavinciFilmSync.fps();
        Film film = panel.getData();

        if (fps.isEmpty() || film == null)
        {
            panel.getContext().notifyError(NO_RESOLVE);

            return;
        }

        int duration = film.camera.calculateDuration();
        int height = even(VideoSettings.resolveProxyHeight.get());
        int width = even(Math.round(height * (float) BBSSettings.videoWidth.get() / Math.max(1, BBSSettings.videoHeight.get())));
        File target = new File(file(film.getId(), fps).getPath().replaceAll("\\.mp4$", ".render.mp4"));
        long started = System.currentTimeMillis();

        panel.recorder.setFinishedListener((cancelled) ->
        {
            if (!cancelled)
            {
                deliver(panel, newestRender(started), target, fps);
            }
        });
        BBSRendering.setCustomSize(true, width, height);
        BBSRendering.scheduleAfterNextExportFrame(() ->
            panel.recorder.startProxy(duration, () -> BBSRendering.getTexture().gpu(), width, height));
        panel.getContext().notifyInfo(STARTED);
        LOGGER.info("[davincilink] rendering film '{}' for Resolve: {} ticks at {}x{}, timeline {} fps", film.getId(), duration, width, height, fps);
    }

    /** Brings the render to the timeline's rate (ffmpeg, in the background) and puts it in place. */
    private static void deliver(UIFilmPanel panel, File movie, File target, String fps)
    {
        Thread thread = new Thread(() ->
        {
            File part = new File(target.getPath() + ".part.mp4");
            boolean ok = movie != null && FFMpegUtils.execute(target.getParentFile(), "-y", "-v", "error", "-i", movie.getAbsolutePath(),
                "-vf", "fps=" + fps, "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", part.getAbsolutePath());

            try
            {
                if (ok)
                {
                    /* A rename, not a write into the file: the placeholder it was linked to stays intact. */
                    Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
            catch (IOException e)
            {
                ok = false;
                LOGGER.error("[davincilink] could not put the render in place", e);
            }

            if (movie != null)
            {
                /* The render and what the export left beside it (log, game sound, mux log). */
                String base = movie.getName().replaceAll("\\.mp4$", "");
                File[] leftovers = movie.getParentFile().listFiles((dir, name) -> name.startsWith(base + "."));

                for (File file : leftovers == null ? new File[0] : leftovers)
                {
                    file.delete();
                }
            }

            boolean done = ok;

            LOGGER.info("[davincilink] render for Resolve {}: {}", done ? "ready" : "failed", target);
            Minecraft.getInstance().execute(() -> panel.getContext().notify(done ? DONE : FAILED, done ? 0xff22aa55 : 0xffcc3333));
        }, "davincilink-proxy");

        thread.setDaemon(true);
        thread.start();
    }

    private static File newestRender(long since)
    {
        File[] files = BBSRendering.getVideoFolder().listFiles((dir, name) -> name.startsWith(PanelVideoExportSession.PROXY_PREFIX) && name.endsWith(".mp4"));
        File newest = null;

        for (File file : files == null ? new File[0] : files)
        {
            if (file.lastModified() >= since && (newest == null || file.lastModified() > newest.lastModified()))
            {
                newest = file;
            }
        }

        return newest;
    }

    private static File file(String filmId, String fps)
    {
        File folder = new File(BBSMod.getSettingsFolder(), "davinci");

        folder.mkdirs();

        return new File(folder, "film-" + filmId.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + fps.replaceAll("[^0-9.]", "_") + ".mp4");
    }

    private static int even(int value)
    {
        return Math.max(2, value + (value & 1));
    }
}
