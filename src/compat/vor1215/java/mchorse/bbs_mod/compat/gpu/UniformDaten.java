package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import mchorse.bbs_mod.compat.alt.TransientMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Uniform-Bloecke der Schicht vor 1.21.5 mit CPU-Kopie: hochgeladen wie in 1.21.6+, dazu im
 * Bild aufbewahrt, damit 1.21.4s Kern-Shader (Einzel-Uniforms) aus denselben Daten gefuellt
 * werden koennen (EinzelUniforms). Die Kopien gelten bis zum Bildwechsel.
 */
public final class UniformDaten
{
    private static final Map<Object, ByteBuffer> KOPIEN = new IdentityHashMap<>();

    private UniformDaten()
    {}

    public static GpuBufferSlice hochladen(ByteBuffer daten)
    {
        ByteBuffer kopie = ByteBuffer.allocate(daten.remaining()).order(ByteOrder.nativeOrder());

        kopie.put(daten.duplicate()).flip();

        GpuBufferSlice slice = TransientMemory.INSTANZ.uploadGpu(daten, 0L, PufferNutzung.USAGE_UNIFORM);

        KOPIEN.put(slice.buffer(), kopie);

        return slice;
    }

    /** CPU-Kopie des Blocks hinter dem Ausschnitt, oder null fuer fremde Puffer. */
    public static ByteBuffer kopie(GpuBufferSlice slice)
    {
        ByteBuffer ganz = KOPIEN.get(slice.buffer());

        if (ganz == null || slice.offset() + slice.length() > ganz.capacity())
        {
            return null;
        }

        return ganz.duplicate().position((int) slice.offset()).limit((int) (slice.offset() + slice.length())).slice().order(ByteOrder.nativeOrder());
    }

    public static void bildEnde()
    {
        KOPIEN.clear();
    }
}
