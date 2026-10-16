package com.davincilink;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** One BGRA pipe contract for the Linux XDamage helper and Windows WGC/ffmpeg. */
class DavinciProcessFrames extends DavinciFrameSource
{
    private final Process process;
    private final DataInputStream input;
    private final String description;
    private final StringBuilder errors = new StringBuilder();

    DavinciProcessFrames(Process process, String description)
    {
        this.process = process;
        this.description = description;
        this.input = new DataInputStream(process.getInputStream());
        Thread stderr = new Thread(() ->
        {
            try (java.io.InputStream stream = process.getErrorStream())
            {
                byte[] bytes = new byte[1024];
                int count;
                while ((count = stream.read(bytes)) != -1)
                {
                    synchronized (this.errors)
                    {
                        this.errors.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
                        if (this.errors.length() > 4096) this.errors.delete(0, this.errors.length() - 4096);
                    }
                }
            }
            catch (IOException error) { if (this.active) this.failure = error.toString(); }
        }, "davincilink-capture-errors");
        stderr.setDaemon(true);
        stderr.start();
    }

    @Override boolean next(byte[] pixels) throws IOException
    {
        try
        {
            this.input.readFully(pixels);
            return this.active;
        }
        catch (IOException error)
        {
            if (!this.active) return false;
            synchronized (this.errors)
            {
                throw new IOException(this.description + " stopped. " + this.errors, error);
            }
        }
    }

    @Override void stop()
    {
        super.stop();
        this.process.destroy();
    }

    @Override public void close()
    {
        this.stop();
        try { this.input.close(); }
        catch (IOException error) { this.failure = error.toString(); }
    }
}
