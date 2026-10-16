package com.afrobama.bbstools.preload;

import com.sun.jna.NativeLibrary;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Keep Linux system HarfBuzz and FreeType on the same allocator/ABI before LWJGL caches its choice. */
public final class LinuxFontBinding implements PreLaunchEntrypoint
{
    private static final String PROPERTY = "org.lwjgl.freetype.libname";

    @Override
    public void onPreLaunch()
    {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT
            || !System.getProperty("os.name", "").equalsIgnoreCase("Linux")
            || System.getProperty(PROPERTY) != null)
        {
            return;
        }

        /* The versioned ELF soname cannot resolve to LWJGL's bundled libfreetype.so. */
        try (NativeLibrary library = NativeLibrary.getInstance("libfreetype.so.6"))
        {
            library.getFunction("FT_Init_FreeType");
            library.getFunction("FT_Done_Face");
            System.setProperty(PROPERTY, "libfreetype.so.6");
            System.out.println("[bbs] Linux font binding: libfreetype.so.6");
        }
        catch (UnsatisfiedLinkError error)
        {
            System.err.println("[bbs] System FreeType unavailable; keeping LWJGL's default: " + error);
        }
    }
}
