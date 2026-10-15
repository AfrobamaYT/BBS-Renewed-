package mchorse.bbs_mod.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One thread that feeds export frames to ffmpeg in submit order, off the render thread. */
public final class VideoPipeWriter
{
    private final ExecutorService thread = Executors.newSingleThreadExecutor((runnable) ->
    {
        Thread worker = new Thread(runnable, "bbs-video-pipe");

        worker.setDaemon(true);

        return worker;
    });

    public Future<?> write(ByteBuffer data, WritableByteChannel channel)
    {
        return this.thread.submit(() ->
        {
            while (data.hasRemaining())
            {
                if (channel.write(data) <= 0)
                {
                    throw new IOException("Video encoder input made no progress");
                }
            }

            return null;
        });
    }

    /** Waits for a write; a failed or stuck write fails loudly. */
    public static void await(Future<?> write, long timeoutNanos)
    {
        try
        {
            write.get(timeoutNanos, TimeUnit.NANOSECONDS);
        }
        catch (ExecutionException error)
        {
            throw new IllegalStateException("Could not write video frame", error.getCause());
        }
        catch (InterruptedException | TimeoutException error)
        {
            throw new IllegalStateException("Video pipe write did not finish", error);
        }
    }

    public void close()
    {
        this.thread.shutdown();

        try
        {
            this.thread.awaitTermination(10L, TimeUnit.SECONDS);
        }
        catch (InterruptedException error)
        {
            Thread.currentThread().interrupt();
        }
    }
}
