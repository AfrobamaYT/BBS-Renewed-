// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * The real DaVinci Resolve, running inside BBS' Resolve tab (task 109).
 *
 * <p>Resolve runs in a private, GPU-accelerated X server (rootful Xwayland) so it has its own
 * pointer and keyboard. BBS reads that server's picture (ffmpeg x11grab: 60 fps at about 3 % of a
 * core without conversion, measured) and sends input with XTest ({@link DavinciX11}). The
 * Xwayland window itself is moved to a hidden Hyprland workspace; on 2026-10-11 it first opened on
 * the user's workspace 1, so the move is checked, not assumed. Linux only; Windows is untested.</p>
 *
 * <p>VIEWER: camera clips are cut from the link's placeholder, so wherever Resolve's viewer shows
 * that colour, its picture is BBS' film. The largest connected patch of it (thumbnails in the media
 * pool are small) is where BBS draws its own viewport -- no layout guessing.</p>
 */
public final class DavinciScreen
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DavinciScreen INSTANCE = new DavinciScreen();

    private String display;
    private ProcessHandle xwayland;
    private ProcessHandle resolve;
    private ProcessHandle link;
    private DavinciCapture capture;
    private boolean paused;
    private volatile DavinciX11 x11;
    private volatile String error = "";

    private DavinciScreen()
    {}

    public synchronized boolean running()
    {
        return this.capture != null && (this.paused || this.capture.running());
    }

    /**
     * The tab is hidden: Resolve stays (switching back is instant), its picture is not needed.
     * Grabbing it on cost 24 % of a core in ffmpeg and more in BBS with nothing shown (2026-10-12).
     */
    public synchronized void pause()
    {
        if (this.capture != null && !this.paused)
        {
            this.capture.stop();
            this.paused = true;
        }
    }

    public String error()
    {
        return this.error;
    }

    /** Starts in the background: waiting for Xwayland and Resolve must never freeze BBS. */
    public void startAsync(int width, int height)
    {
        Thread thread = new Thread(() -> this.start(width, height), "davincilink-start");

        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void start(int width, int height)
    {
        if (this.paused && this.resolve != null && this.resolve.isAlive())
        {
            this.paused = false;
            try
            {
                this.capture = new DavinciCapture(this.display, this.x11.width(), this.x11.height());

                return;
            }
            catch (java.io.IOException e)
            {
                LOGGER.warn("[davincilink] Resolve tab picture could not resume, starting over", e);
            }
        }
        this.paused = false;
        if (this.running())
        {
            return;
        }
        if (this.resolve != null || this.xwayland != null)
        {
            /* An earlier run whose picture is gone: its X server died (kill, crash), and Resolve
             * then hung on as a process without a screen, its link still connected to BBS
             * (2026-10-12, :90). Clear it all before starting again on a free display. */
            LOGGER.warn("[davincilink] Resolve tab on {} lost its screen; clearing it before a new start", this.display);
            this.stop();
        }

        try
        {
            String leftover = DavinciProcesses.leftoverDisplay();

            if (leftover != null)
            {
                /* BBS ended (or crashed) while the tab ran: Resolve kept running. Use it. */
                this.display = leftover;
                this.xwayland = DavinciProcesses.xwayland(leftover).orElseThrow();
                this.resolve = DavinciProcesses.resolveOn(leftover).orElseThrow();
                this.x11 = DavinciWindowing.waitForServer(this.display);
                LOGGER.info("[davincilink] Resolve tab: re-attached to the Resolve still running on {}", leftover);
            }
            else
            {
                if (DavinciProcesses.resolveElsewhere())
                {
                    DavinciProcesses.quitElsewhere();
                }

                this.display = DavinciWindowing.freeDisplay();
                DavinciWindowing.prepare(this.display, width, height);
                this.xwayland = DavinciProcesses.startXwayland(this.display, width, height);
                /* Only the final connection goes into the field the render thread reads. */
                DavinciX11 first = DavinciWindowing.waitForServer(this.display);

                DavinciWindowing.hide(this.display, width, height);
                /* Xlib keeps the screen size from connect time; after Hyprland set the window the
                 * screen is smaller (1920x1080 asked, 1908x1028 got). Read it on a new connection. */
                first.close();
                this.x11 = DavinciWindowing.waitForSize(this.display, width, height);

                this.resolve = DavinciProcesses.startResolve(this.display);
            }
            DavinciWindowing.fillScreen(this.resolve, this.display, this.x11.width(), this.x11.height());

            this.capture = new DavinciCapture(this.display, this.x11.width(), this.x11.height());
            DavinciTakt.smooth(true);
            if (leftover == null)
            {
                this.link = DavinciProcesses.startLink(this.display);
            }
            this.error = "";
            LOGGER.info("[davincilink] Resolve tab: {} at {}x{}", this.display, this.x11.width(), this.x11.height());
        }
        catch (Exception e)
        {
            this.error = e.getMessage() == null ? e.toString() : e.getMessage();
            LOGGER.error("[davincilink] could not start the Resolve tab", e);
            this.stop();
        }
    }

    /** Ends everything at once: only for a start that failed half way. */
    public synchronized void stop()
    {
        this.paused = false;
        if (this.capture != null)
        {
            this.capture.stop();
        }
        this.closeInput();
        for (ProcessHandle process : new ProcessHandle[] {this.link, this.resolve, this.xwayland})
        {
            if (process != null && process.isAlive())
            {
                process.destroy();
            }
        }
        this.release();
    }

    /**
     * BBS is closing. Resolve may hold unsaved edits, so it is asked over the link to save and
     * quit; only once it is gone does its X server go. Without a link it keeps running (and is
     * re-attached on the next start) rather than being killed with the user's work.
     */
    public synchronized void shutdown()
    {
        if (this.capture != null)
        {
            this.capture.stop();
        }
        if (this.display != null)
        {
            /* Not the handle BBS started: the user may have restarted Resolve inside the tab, and
             * trusting the old, dead handle closed the X server under the new one (2026-10-11). */
            this.resolve = DavinciProcesses.resolveOn(this.display).orElse(null);
        }
        if (this.resolve != null && this.resolve.isAlive() && DavinciLinkClient.SERVER.connectionCount() > 0)
        {
            DavinciLinkClient.SERVER.send(DavinciFilmSync.envelope("QUIT", new com.google.gson.JsonObject()));
            this.resolve.onExit().completeOnTimeout(this.resolve, 20, TimeUnit.SECONDS).join();
        }
        if (this.resolve != null && this.resolve.isAlive())
        {
            LOGGER.warn("[davincilink] Resolve keeps running on {} (not saved by the link); BBS re-attaches next time", this.display);
        }
        else if (this.xwayland != null)
        {
            this.closeInput();
            this.xwayland.destroy();
            if (this.link != null && this.link.isAlive())
            {
                this.link.destroy();           // its Resolve is gone; otherwise it would linger on
            }
        }
        this.release();
    }

    /**
     * Before the X server goes: the render thread must not send into a closing connection. A
     * connection whose server already died is dropped, never closed: XCloseDisplay on it ran into
     * Xlib's I/O error and corrupted the NVIDIA driver's state -- BBS crashed moments later in
     * glBindFramebuffer (SIGSEGV, 2026-10-12, after Xwayland :91 was killed).
     */
    private void closeInput()
    {
        DavinciX11 x11 = this.x11;
        boolean serverAlive = this.xwayland != null && this.xwayland.isAlive();

        this.x11 = null;
        if (x11 != null && serverAlive)
        {
            x11.close();
        }
    }

    private void release()
    {
        DavinciTakt.smooth(false);
        this.closeInput();
        this.capture = null;
        this.resolve = this.xwayland = this.link = null;
        this.x11 = null;
    }

    public DavinciX11 input()
    {
        return this.x11;
    }

    /** Hands the newest frame to {@code upload} if there is one the caller has not seen yet. */
    public boolean upload(java.util.function.Consumer<ByteBuffer> upload)
    {
        DavinciCapture capture = this.capture;

        return capture != null && capture.upload(upload);
    }

    public int width()
    {
        DavinciCapture capture = this.capture;

        return capture == null ? 0 : capture.width();
    }

    public int height()
    {
        DavinciCapture capture = this.capture;

        return capture == null ? 0 : capture.height();
    }

    public long frames()
    {
        DavinciCapture capture = this.capture;

        return capture == null ? 0 : capture.frames();
    }

    public int[] viewer()
    {
        DavinciCapture capture = this.capture;

        return capture == null ? null : capture.viewer();
    }
}
