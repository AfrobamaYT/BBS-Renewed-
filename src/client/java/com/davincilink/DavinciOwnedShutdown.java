package com.davincilink;

import com.mojang.logging.LogUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Bounded shutdown of this tab's exact process handles and private display only. */
final class DavinciOwnedShutdown
{
    private DavinciOwnedShutdown() {}

    static void stop(String display, ProcessHandle resolve, ProcessHandle link, ProcessHandle server)
    {
        Set<ProcessHandle> helpers = new LinkedHashSet<>();
        if (resolve != null) resolve.descendants().forEach(helpers::add);
        if (link != null)
        {
            helpers.add(link);
            link.descendants().forEach(helpers::add);
        }
        collect(display, resolve, helpers);
        if (resolve != null && resolve.isAlive() && DavinciLinkClient.SERVER.connectionCount() > 0)
        {
            try
            {
                DavinciLinkClient.SERVER.send(DavinciFilmSync.envelope("QUIT", new com.google.gson.JsonObject()));
                waitFor(resolve, 5000);
            }
            catch (RuntimeException error)
            {
                LogUtils.getLogger().warn("[davincilink] Resolve save/quit request failed; closing tab processes", error);
            }
        }
        if (resolve != null && resolve.isAlive() && com.sun.jna.Platform.isWindows())
        {
            DavinciWindowsSession.requestClose(resolve);
            waitFor(resolve, 2000);
        }
        terminate(resolve);
        /* Resolve can start its crash reporter while exiting. It has the same private DISPLAY. */
        collect(display, resolve, helpers);
        for (ProcessHandle helper : helpers) terminate(helper);
        terminate(server);
    }

    private static void collect(String display, ProcessHandle resolve, Set<ProcessHandle> processes)
    {
        if (display == null || !display.startsWith(":")) return;
        ProcessHandle.allProcesses().filter(process -> process.pid() != ProcessHandle.current().pid()).forEach(process ->
        {
            String command = process.info().command().orElse("");
            if ((command.endsWith("/resolve") || command.endsWith("/fuscript"))
                && (resolve == null || process.pid() != resolve.pid()) && display.equals(displayOf(process)))
                processes.add(process);
        });
    }

    private static String displayOf(ProcessHandle process)
    {
        try
        {
            for (String entry : new String(Files.readAllBytes(Path.of("/proc", Long.toString(process.pid()), "environ")),
                StandardCharsets.UTF_8).split("\0"))
                if (entry.startsWith("DISPLAY=")) return entry.substring(8);
        }
        catch (java.io.IOException ignored) { /* Already exited or belongs to another user. */ }
        return "";
    }

    private static void terminate(ProcessHandle process)
    {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        if (!waitFor(process, 2000))
        {
            process.destroyForcibly();
            if (!waitFor(process, 2000))
                throw new IllegalStateException("Resolve tab process " + process.pid() + " survived shutdown");
        }
    }

    private static boolean waitFor(ProcessHandle process, long milliseconds)
    {
        try
        {
            process.onExit().get(milliseconds, TimeUnit.MILLISECONDS);
            return true;
        }
        catch (java.util.concurrent.TimeoutException expected) { return !process.isAlive(); }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
        catch (java.util.concurrent.ExecutionException error)
        {
            throw new IllegalStateException("Cannot observe Resolve tab process " + process.pid(), error);
        }
    }
}
