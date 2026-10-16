package com.davincilink;

import java.io.IOException;
import java.nio.file.Path;

/** Keep all native grab calls on one thread in a helper, independently of BBS' XTest input. */
final class DavinciXDamagePipe extends DavinciProcessFrames
{
    DavinciXDamagePipe(String display, int width, int height) throws IOException
    {
        super(start(display, width, height), "Resolve XDamage capture");
    }

    private static Process start(String display, int width, int height) throws IOException
    {
        try
        {
            String classes = Path.of(DavinciXDamageMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String jna = Path.of(com.sun.jna.Native.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String mod = Path.of(DavinciCapture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String classpath = System.getProperty("java.class.path") + java.io.File.pathSeparator + classes
                + java.io.File.pathSeparator + jna + java.io.File.pathSeparator + mod;
            return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Xms8m", "-Xmx128m", "-XX:+UseSerialGC",
                "-cp", classpath, DavinciXDamageMain.class.getName(), display, Integer.toString(width),
                Integer.toString(height), Long.toString(ProcessHandle.current().pid())).start();
        }
        catch (java.net.URISyntaxException error)
        {
            throw new IOException("Cannot find the Resolve capture helper classes", error);
        }
    }
}
