package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.systems.GpuDevice;

/** vor1215: das Geraet der eigenen GL-Schicht (Mojangs GpuDevice gibt es vor 1.21.5 nicht). */
public final class Geraet
{
    private Geraet()
    {}

    public static GpuDevice get()
    {
        return com.mojang.blaze3d.opengl.GlDevice.instanz();
    }
}
