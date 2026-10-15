package mchorse.bbs_mod.compat.gpu;

/**
 * Die Nutzungsbits von 1.21.11s GpuBuffer (USAGE_*), fuer die compat-Schicht beider Stufen.
 * Vor 1.21.5 ist GpuBuffer eine andere,
 * vorhandene Klasse ohne diese Konstanten; die Regel GpuBuffer.USAGE_ -> PufferNutzung.USAGE_
 * (stonecutter-regeln-1214.gradle) lenkt die Zugriffe hierher. Werte wie in 1.21.11.
 */
public final class PufferNutzung
{
    public static final int USAGE_MAP_READ = 1;
    public static final int USAGE_MAP_WRITE = 2;
    public static final int USAGE_HINT_CLIENT_STORAGE = 4;
    public static final int USAGE_COPY_DST = 8;
    public static final int USAGE_COPY_SRC = 16;
    public static final int USAGE_VERTEX = 32;
    public static final int USAGE_INDEX = 64;
    public static final int USAGE_UNIFORM = 128;
    public static final int USAGE_UNIFORM_TEXEL_BUFFER = 256;

    private PufferNutzung()
    {}
}
