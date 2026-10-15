// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * The Resolve tab's processes, found again by what they are rather than by a handle BBS kept.
 *
 * <p>Resolve and its Xwayland outlive the JVM that started them (measured 2026-10-11: both kept
 * running after the BBS instance ended). After a crash BBS re-attaches to them instead of
 * starting a second Resolve, which would fight the first one over the project database.</p>
 */
final class DavinciProcesses
{
    /** The project the desktop Resolve had open when the tab took over; the tab's link reopens it. */
    private static String handedOver = "";

    private DavinciProcesses()
    {}

    /** The X display of a Resolve tab still running from an earlier BBS, or null. */
    static String leftoverDisplay()
    {
        for (int n = 90; n < 100; n++)
        {
            String display = ":" + n;

            if (xwayland(display).isPresent() && resolveOn(display).isPresent())
            {
                return display;
            }
        }

        return null;
    }

    static Optional<ProcessHandle> xwayland(String display)
    {
        return ProcessHandle.allProcesses().filter((p) ->
            p.info().command().orElse("").endsWith("Xwayland")
                && Arrays.asList(p.info().arguments().orElse(new String[0])).contains(display)).findFirst();
    }

    /** The Resolve whose DISPLAY is this private server (read from /proc, Linux). */
    static Optional<ProcessHandle> resolveOn(String display)
    {
        return ProcessHandle.allProcesses().filter((p) ->
            isResolve(p) && display.equals(environment(p.pid(), "DISPLAY"))).findFirst();
    }

    /** Any Resolve outside a tab: then the tab must not start a second one. */
    static boolean resolveElsewhere()
    {
        return ProcessHandle.allProcesses().anyMatch((p) ->
            isResolve(p) && !environment(p.pid(), "DISPLAY").matches(":9[0-9]"));
    }

    /**
     * Resolve itself, not its crash reporter: after a crash Resolve starts the same binary as
     * {@code resolve -reportCrash <file>}. On 2026-10-12 the tab re-attached to such a reporter on
     * :91 and waited for a Resolve that was gone.
     */
    private static boolean isResolve(ProcessHandle process)
    {
        return process.info().command().orElse("").endsWith("/resolve")
            && !Arrays.asList(process.info().arguments().orElse(new String[0])).contains("-reportCrash");
    }

    /**
     * Resolve runs on the desktop and the user opens the tab: it is asked over the link to save and
     * quit, so the tab can start it inside BBS. Without a link that request cannot be made, and a
     * second Resolve would fight the first over the project database -- then the user must close it.
     */
    static void quitElsewhere() throws InterruptedException
    {
        if (DavinciLinkClient.SERVER.connectionCount() == 0)
        {
            throw new IllegalStateException("DaVinci Resolve is already running -- close it, the tab starts its own");
        }

        handedOver = DavinciFilmSync.project();
        DavinciLinkClient.SERVER.send(DavinciFilmSync.envelope("QUIT", new com.google.gson.JsonObject()));
        for (int i = 0; i < 300 && resolveElsewhere(); i++)
        {
            Thread.sleep(100);
        }
        if (resolveElsewhere())
        {
            throw new IllegalStateException("DaVinci Resolve did not close within 30 s -- close it, the tab starts its own");
        }
    }

    /**
     * The link inside the tab's Resolve. Studio lets BBS start it from outside (external
     * scripting); the free Resolve does not (since 19.1): the started script gives up after a
     * minute and the tab asks the user to start Workspace > Scripts > BBS Link once, inside it.
     */
    /** The tab's private X server: a rootful Xwayland of exactly the tab's size. */
    static ProcessHandle startXwayland(String display, int width, int height) throws java.io.IOException
    {
        return new ProcessBuilder("Xwayland", display, "-geometry", width + "x" + height, "-glamor", "gl")
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().toHandle();
    }

    /** Resolve on the tab's X server only. */
    static ProcessHandle startResolve(String display) throws java.io.IOException
    {
        ProcessBuilder app = new ProcessBuilder("/opt/resolve/bin/resolve");

        app.environment().remove("WAYLAND_DISPLAY");          // else Qt opens it on the user's screen
        app.environment().put("DISPLAY", display);
        app.environment().put("QT_QPA_PLATFORM", "xcb");

        return app.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().toHandle();
    }

    static ProcessHandle startLink(String display)
    {
        try
        {
            java.io.File script = java.io.File.createTempFile("bbs_link", ".py");
            try (java.io.InputStream in = DavinciProcesses.class.getResourceAsStream("/assets/bbs/davinci/bbs_link.py"))
            {
                Files.write(script.toPath(), in.readAllBytes());
            }
            script.deleteOnExit();

            ProcessBuilder link = new ProcessBuilder("python3", "-u", script.getAbsolutePath(), String.valueOf(DavinciLinkClient.SERVER.getPort()));

            link.environment().put("DISPLAY", display);
            link.environment().put("BBS_DAVINCI_PROJECT", handedOver);
            return link.redirectErrorStream(true).redirectOutput(new java.io.File(script.getPath() + ".log")).start().toHandle();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("could not start the link for the tab's Resolve: " + e.getMessage(), e);
        }
    }

    private static String environment(long pid, String name)
    {
        try
        {
            for (String entry : new String(Files.readAllBytes(Path.of("/proc", String.valueOf(pid), "environ")), StandardCharsets.UTF_8).split("\0"))
            {
                if (entry.startsWith(name + "="))
                {
                    return entry.substring(name.length() + 1);
                }
            }
        }
        catch (Exception ignored)
        {
            /* not ours to read, or already gone */
        }

        return "";
    }
}
