package com.davincilink;

/** The Xlib display, damage events, shared memory and pixel reads all belong to this main thread. */
public final class DavinciXDamageMain
{
    private DavinciXDamageMain() {}

    public static void main(String[] args) throws Exception
    {
        if (args.length != 4) throw new IllegalArgumentException("display width height parent-pid required");
        int width = Integer.parseInt(args[1]), height = Integer.parseInt(args[2]);
        ProcessHandle parent = ProcessHandle.of(Long.parseLong(args[3])).orElseThrow();
        if (ProcessHandle.current().parent().orElseThrow().pid() != parent.pid())
            throw new IllegalArgumentException("Capture helper parent does not match");
        try (DavinciXDamage source = new DavinciXDamage(args[0], width, height))
        {
            parent.onExit().thenRun(source::stop);
            byte[] bytes = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
            java.io.OutputStream output = new java.io.BufferedOutputStream(System.out, 1024 * 1024);
            while (source.next(bytes))
            {
                output.write(bytes);
                output.flush();
            }
        }
    }
}
