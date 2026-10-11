package mchorse.bbs_mod.client;

/** One-frame export requests finish after the native world image has been drawn. */
public final class ExportFrameCallbacks
{
    private static Runnable pending;

    private ExportFrameCallbacks()
    {}

    public static void schedule(Runnable action)
    {
        pending = action;
    }

    public static boolean hasPending()
    {
        return pending != null;
    }

    public static void completeFrame()
    {
        Runnable action = pending;
        pending = null;

        if (action != null)
        {
            BBSRendering.fuellen();
            action.run();
        }
    }
}
