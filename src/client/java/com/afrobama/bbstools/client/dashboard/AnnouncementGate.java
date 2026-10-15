package com.afrobama.bbstools.client.dashboard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Simple file-flag gate so the announcement popup shows once, ever.
 * One zero-byte sentinel at {@code <configDir>/bbstools/shown_announcement.flag}.
 */
public final class AnnouncementGate
{
    private AnnouncementGate()
    {
    }

    /** Bumped each time the announcement content meaningfully changes
     *  so users see the new version once. Current = v3 after Phase 9
     *  (undo overlay), Phase 10 (replay dock), and the new
     *  Ctrl+H / Ctrl+Shift+R / Ctrl+Shift+Z / Ctrl+Shift+Y shortcuts
     *  were added. */
    private static final String FLAG_VERSION = "v3";

    private static File flagFile()
    {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        File ours = new File(configDir, "bbstools");
        if (!ours.exists()) ours.mkdirs();
        return new File(ours, "shown_announcement_" + FLAG_VERSION + ".flag");
    }

    public static boolean shouldShow()
    {
        /* A developer note (/bbsdebug commands, STYLE_GUIDE.md), not a welcome: shown only
         * where the dev module is loaded. Audit 2026-10-13, finding U1: a new user's first
         * screen was this popup. */
        if (!FabricLoader.getInstance().isModLoaded("bbsdev"))
        {
            return false;
        }

        return !flagFile().exists();
    }

    public static void markShown()
    {
        try
        {
            Files.createFile(flagFile().toPath());
        }
        catch (IOException ignored)
        {
        }
    }

}
