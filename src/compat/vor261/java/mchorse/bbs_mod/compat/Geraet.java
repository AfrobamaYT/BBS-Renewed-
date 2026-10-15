package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Das GPU-Geraet fuer die compat-Schicht. Die compat-Quellen laufen nicht durch Stonecutter;
 * vor 1.21.5 ersetzt vor1215 diese Datei (dort gibt es RenderSystem.getDevice() nicht).
 */
public final class Geraet
{
    private Geraet()
    {}

    public static GpuDevice get()
    {
        return RenderSystem.getDevice();
    }
}
