// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The windows around the Resolve tab: the private Xwayland on the host, Resolve inside it.
 *
 * <p>HOST: the rootful Xwayland is a normal Wayland window. It must not show on the user's screens
 * (on 2026-10-11 it opened on his workspace 1), and its size IS the X screen's size (tiled, it
 * shrank to 949x509). On Hyprland it goes floating, exact size, to a special workspace -- Larch's
 * Lua dispatcher syntax first, the classic one second -- and the result is checked.</p>
 *
 * <p>INSIDE: there is no window manager in that server, so Resolve's main window is sized to the
 * whole screen with xdotool, again whenever Resolve opens a new main window.</p>
 */
final class DavinciWindowing
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORKSPACE = "special:bbs-davinci";

    private DavinciWindowing()
    {}

    /**
     * Before Xwayland starts: a Hyprland rule makes its window born hidden, floating, at the exact
     * size, without taking focus (checked 2026-10-11: on special:bbs-davinci, 1600x900, focus kept).
     * Moving it afterwards let it flash onto the user's workspace first.
     *
     * <p>render_unfocused: a window nobody looks at gets no frame callbacks, and Resolve then drew
     * a new picture about once a second (scrubbing its ruler, measured 2026-10-12: 1.1 changes/s;
     * 13.8 with this rule at Hyprland's 15 fps default; 34 at 60, see {@link DavinciTakt#smooth}).</p>
     */
    static void prepare(String display, int width, int height)
    {
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null)
        {
            return;
        }

        String answer = hyprctl("eval", "hl.window_rule({ name = 'bbs-davinci-" + display.substring(1) + "', match = { title = '^Xwayland on "
            + display + "$' }, workspace = '" + WORKSPACE + " silent', float = true, size = {" + width + ", " + height
            + "}, no_initial_focus = true, no_focus = true, render_unfocused = true })").trim();

        if (!answer.equals("ok"))
        {
            LOGGER.warn("[davincilink] Hyprland rule for {} not set ({}), moving the window afterwards", display, answer);
        }
    }

    static void hide(String display, int width, int height) throws InterruptedException
    {
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null)
        {
            LOGGER.warn("[davincilink] not on Hyprland: the Xwayland window for Resolve stays visible");

            return;
        }

        String address = null;

        for (int i = 0; i < 100 && address == null; i++)
        {
            JsonObject client = client(display);

            address = client == null ? null : client.get("address").getAsString();
            if (address == null)
            {
                Thread.sleep(50);
            }
        }
        if (address == null)
        {
            throw new IllegalStateException("Xwayland " + display + " has no window in Hyprland");
        }

        /* By address, and every answer checked: a title selector silently matched nothing on
         * 2026-10-11 and the window stayed on the user's workspace 2. */
        String window = "address:" + address;
        JsonObject placed = client(display);

        if (placed != null && placed.getAsJsonObject("workspace").get("name").getAsString().startsWith("special:")
            && placed.get("floating").getAsBoolean())
        {
            return;                                   // the rule from prepare() did it
        }

        dispatch("hl.dsp.window.move({ window = '" + window + "', workspace = '" + WORKSPACE + "', silent = true })");
        dispatch("hl.dsp.window.float({ window = '" + window + "', action = 'set' })");
        dispatch("hl.dsp.window.resize({ window = '" + window + "', x = " + width + ", y = " + height + ", exact = true })");

        JsonObject client = client(display);
        String workspace = client == null ? "" : client.getAsJsonObject("workspace").get("name").getAsString();

        if (!workspace.startsWith("special:"))
        {
            throw new IllegalStateException("Xwayland " + display + " is on workspace '" + workspace + "', not hidden");
        }
    }

    private static JsonObject client(String display)
    {
        for (JsonElement element : JsonParser.parseString(hyprctl("clients", "-j")).getAsJsonArray())
        {
            JsonObject client = element.getAsJsonObject();

            if (("Xwayland on " + display).equals(client.get("title").getAsString()))
            {
                return client;
            }
        }

        return null;
    }

    private static void dispatch(String call)
    {
        String answer = hyprctl("dispatch", call).trim();

        if (!answer.equals("ok"))
        {
            throw new IllegalStateException("hyprctl dispatch " + call + " answered: " + answer);
        }
    }

    /** Keeps Resolve's main window as large as the private screen, for as long as Resolve runs. */
    static void fillScreen(ProcessHandle resolve, String display, int width, int height)
    {
        Thread thread = new Thread(() ->
        {
            while (resolve.isAlive())
            {
                run(display, "xdotool", "search", "--name", "^DaVinci Resolve", "windowsize", "%@",
                    String.valueOf(width), String.valueOf(height), "windowmove", "%@", "0", "0");
                try
                {
                    Thread.sleep(2000);
                }
                catch (InterruptedException e)
                {
                    return;
                }
            }
        }, "davincilink-fill");

        thread.setDaemon(true);
        thread.start();
    }

    static String hyprctl(String... args)
    {
        String[] command = new String[args.length + 1];

        command[0] = "hyprctl";
        System.arraycopy(args, 0, command, 1, args.length);

        return run(null, command);
    }

    private static String run(String display, String... command)
    {
        try
        {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);

            if (display != null)
            {
                builder.environment().put("DISPLAY", display);
            }

            Process process = builder.start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            process.waitFor(10, TimeUnit.SECONDS);

            return out;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("could not run " + command[0] + " (" + e.getMessage() + ")", e);
        }
    }

    static String freeDisplay()
    {
        for (int n = 90; n < 100; n++)
        {
            if (!new File("/tmp/.X11-unix/X" + n).exists())
            {
                return ":" + n;
            }
        }

        throw new IllegalStateException("no free X display between :90 and :99");
    }

    static DavinciX11 waitForServer(String display) throws InterruptedException
    {
        for (int i = 0; i < 100; i++)
        {
            DavinciX11 x11 = DavinciX11.open(display);

            if (x11 != null)
            {
                return x11;
            }
            Thread.sleep(50);
        }

        throw new IllegalStateException("Xwayland " + display + " did not come up");
    }

    /** A fresh connection once the screen has settled near the asked size (Hyprland takes a
     * few pixels for border and gaps: 1920x1080 asked, 1908x1028 got). */
    static DavinciX11 waitForSize(String display, int width, int height) throws InterruptedException
    {
        DavinciX11 x11 = null;

        for (int i = 0; i < 60; i++)
        {
            x11 = waitForServer(display);
            if (Math.abs(x11.width() - width) <= 64 && Math.abs(x11.height() - height) <= 64)
            {
                return x11;
            }
            x11.close();
            Thread.sleep(100);
        }

        throw new IllegalStateException("Xwayland " + display + " did not take the size " + width + "x" + height);
    }
}
