// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 79, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Puts the Resolve side into DaVinci's own script menu: Workspace &gt; Scripts &gt; BBS Link.
 *
 * <p>Scripts started from that menu run on the free DaVinci Resolve too (external scripting is
 * Studio only since 19.1). BBS ships {@code bbs_link.py} in its jar and writes it, plus a small
 * starter that knows this instance's port, into Resolve's Utility scripts folder -- only if that
 * folder's parent exists (Resolve is installed) and only when the content differs.</p>
 */
public final class DavinciScriptInstall
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private DavinciScriptInstall()
    {}

    /** Resolve's per-user Fusion scripts folder on this OS (documented in Resolve's scripting README). */
    static Path scriptsFolder()
    {
        String override = System.getProperty("davincilink.scripts");

        if (override != null)
        {
            return Paths.get(override);
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");

        if (os.contains("win"))
        {
            return Paths.get(System.getenv().getOrDefault("APPDATA", home), "Blackmagic Design", "DaVinci Resolve", "Support", "Fusion", "Scripts");
        }
        if (os.contains("mac"))
        {
            return Paths.get(home, "Library", "Application Support", "Blackmagic Design", "DaVinci Resolve", "Fusion", "Scripts");
        }

        return Paths.get(home, ".local", "share", "DaVinciResolve", "Fusion", "Scripts");
    }

    /** The port of the instance the user works in (the CF instance); test instances differ. */
    public static final int DEFAULT_PORT = 9980;

    public static void install(int port)
    {
        /* Only the instance the user works in installs into Resolve. A test instance would
         * otherwise overwrite the user's "BBS Link" with its port and development copy; tests
         * point -Ddavincilink.scripts at a folder of their own. */
        if (port != DEFAULT_PORT && System.getProperty("davincilink.scripts") == null)
        {
            return;
        }

        Path scripts = scriptsFolder();

        if (!Files.isDirectory(scripts))
        {
            LOGGER.info("[davincilink] no DaVinci Resolve scripts folder at {}, not installing the script", scripts);

            return;
        }

        try (InputStream in = DavinciScriptInstall.class.getResourceAsStream("/assets/bbs/davinci/bbs_link.py"))
        {
            if (in == null)
            {
                throw new IllegalStateException("bbs_link.py is missing from the BBS jar");
            }

            Path utility = scripts.resolve("Utility");
            String starter = "# BBS Link: connects this DaVinci Resolve to BBS (written by BBS, overwritten on update).\n"
                + "import sys\n"
                + "sys.path.insert(0, " + pythonString(utility.toString()) + ")\n"
                + "import bbs_link\n"
                + "bbs_link.main(app=resolve, port=" + port + ")\n";

            Files.createDirectories(utility);
            write(utility.resolve("bbs_link.py"), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            write(utility.resolve("BBS Link.py"), starter);
        }
        catch (Exception e)
        {
            LOGGER.error("[davincilink] could not install the DaVinci script into {}", scripts, e);
        }
    }

    private static void write(Path file, String content) throws java.io.IOException
    {
        if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content))
        {
            return;
        }

        Files.writeString(file, content, StandardCharsets.UTF_8);
        LOGGER.info("[davincilink] wrote {}", file);
    }

    private static String pythonString(String text)
    {
        return "r'" + text.replace("'", "\\'") + "'";
    }
}
