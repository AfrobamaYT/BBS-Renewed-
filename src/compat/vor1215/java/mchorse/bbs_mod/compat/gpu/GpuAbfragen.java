package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuQuery;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;

/**
 * Fences, Zeitabfragen und Rueckmeldungen nach GPU-Arbeit fuer die Schicht vor 1.21.5 (1.21.4
 * hat kein RenderSystem.queueFencedTask): erledigte Aufgaben laufen beim Bildwechsel.
 */
public final class GpuAbfragen
{
    private final List<Aufgabe> aufgaben = new ArrayList<>();

    public void nachGpu(Runnable fertig)
    {
        this.aufgaben.add(new Aufgabe(GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0), fertig));
    }

    public void bildEnde()
    {
        Iterator<Aufgabe> it = this.aufgaben.iterator();

        while (it.hasNext())
        {
            Aufgabe a = it.next();

            if (erledigt(GL32.glClientWaitSync(a.sync, 0, 0L)))
            {
                GL32.glDeleteSync(a.sync);
                it.remove();
                a.fertig.run();
            }
        }
    }

    public GpuFence fence()
    {
        long sync = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

        return new GpuFence()
        {
            private boolean zu;

            @Override
            public void close()
            {
                if (!this.zu)
                {
                    this.zu = true;
                    GL32.glDeleteSync(sync);
                }
            }

            @Override
            public boolean awaitCompletion(long timeoutMs)
            {
                return erledigt(GL32.glClientWaitSync(sync, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutMs * 1_000_000L));
            }
        };
    }

    public GpuQuery zeitBeginn()
    {
        int abfrage = GL15.glGenQueries();

        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, abfrage);

        return new GpuQuery()
        {
            @Override
            public OptionalLong getValue()
            {
                if (GL15.glGetQueryObjecti(abfrage, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return OptionalLong.empty();

                return OptionalLong.of(GL33.glGetQueryObjecti64(abfrage, GL15.GL_QUERY_RESULT));
            }

            @Override
            public void close()
            {
                GL15.glDeleteQueries(abfrage);
            }
        };
    }

    public void zeitEnde()
    {
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
    }

    private static boolean erledigt(int ergebnis)
    {
        return ergebnis == GL32.GL_ALREADY_SIGNALED || ergebnis == GL32.GL_CONDITION_SATISFIED;
    }

    private record Aufgabe(long sync, Runnable fertig)
    {}
}
