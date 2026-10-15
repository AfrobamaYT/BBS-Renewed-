package com.mcstudio.nano;

import org.lwjgl.nanovg.NanoVG;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** JNI boundary for the existing LWJGL NanoVG core and its Vulkan renderer. */
final class NanoVkNative
{
    private static final int ABI_VERSION = 2;
    private static boolean loaded;

    private NanoVkNative()
    {}

    static synchronized void load()
    {
        if (loaded)
        {
            return;
        }

        String override = System.getProperty("bbs.nanovg.vulkan.library");

        if (override != null && !override.isBlank())
        {
            System.load(Path.of(override).toAbsolutePath().toString());
            verifyAbi();
            loaded = true;
            return;
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String platform = os.startsWith("windows") ? "windows" : os.contains("mac") || os.equals("darwin") ? "macos"
            : os.contains("linux") ? "linux" : null;
        String machine = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String architecture = machine.equals("aarch64") || machine.equals("arm64") ? "arm64"
            : machine.equals("amd64") || machine.equals("x86_64") ? "x64" : null;

        if (platform == null || architecture == null)
        {
            throw new IllegalStateException("Unsupported NanoVG Vulkan native platform: " + os + "/" + machine);
        }
        String file = System.mapLibraryName("bbs_nanovg_vk");
        String resource = "/assets/bbs/natives/" + platform + "-" + architecture + "/" + file;

        try (InputStream input = NanoVkNative.class.getResourceAsStream(resource))
        {
            if (input == null)
            {
                throw new IllegalStateException("NanoVG Vulkan native library is missing: " + resource);
            }

            Path extracted = Files.createTempFile("bbs-nanovg-vulkan-", file);
            Files.copy(input, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toAbsolutePath().toString());
            verifyAbi();
            loaded = true;
        }
        catch (IOException error)
        {
            throw new IllegalStateException("Cannot load NanoVG Vulkan library " + resource, error);
        }
    }

    private static void verifyAbi()
    {
        try
        {
            int actual = abiVersion();

            if (actual != ABI_VERSION)
            {
                throw new IllegalStateException("NanoVG Vulkan ABI mismatch: expected " + ABI_VERSION + ", found " + actual);
            }
        }
        catch (UnsatisfiedLinkError error)
        {
            throw new IllegalStateException("NanoVG Vulkan library predates the current native ABI", error);
        }
    }

    /** LWJGL exposes these core addresses for external NanoVG rendering backends. */
    static long core(String name)
    {
        try
        {
            Field address = NanoVG.class.getDeclaredField(name);
            address.setAccessible(true);
            long result = address.getLong(null);

            if (result == 0L)
            {
                throw new IllegalStateException("LWJGL NanoVG core function is null: " + name);
            }

            return result;
        }
        catch (ReflectiveOperationException error)
        {
            throw new IllegalStateException("Incompatible LWJGL NanoVG core: " + name, error);
        }
    }

    static native int abiVersion();
    static native long create(NanoVkImages images, long instance, long physicalDevice, long device,
        long instanceProc, long deviceProc,
        long createCore, long deleteCore, long paramsCore, int colorFormat);
    static native void prepare(long context, long commandBuffer, long colorView, int width, int height);
    static native long takeRecording(long context);
    static native void releaseRecording(long context, long recording);
    static native void destroy(long context);
}
