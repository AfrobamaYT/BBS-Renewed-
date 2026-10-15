package dev.bbs.nanovg.probe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.vulkan.VK;

/** Native ABI/loader checks only. This program creates no Vulkan instance or device. */
public final class NativeLoaderProbe
{
    private static Method method(Class<?> api, String name, Class<?>... arguments) throws Exception
    {
        Method result = api.getDeclaredMethod(name, arguments);
        result.setAccessible(true);
        return result;
    }

    public static void main(String[] arguments) throws Exception
    {
        System.setProperty("bbs.nanovg.vulkan.library", arguments[0]);
        Class<?> api = Class.forName("com.mcstudio.nano.NanoVkNative");
        method(api, "load").invoke(null);
        if ((Integer) method(api, "abiVersion").invoke(null) != 2)
        {
            throw new AssertionError("Native ABI mismatch");
        }
        for (String name : new String[] {"nvgCreateInternal", "nvgDeleteInternal", "nvgInternalParams"})
        {
            if ((Long) method(api, "core", String.class).invoke(null, name) == 0L)
            {
                throw new AssertionError("Missing existing LWJGL core address: " + name);
            }
        }
        if ((Long) method(api, "takeRecording", long.class).invoke(null, 0L) != 0L)
        {
            throw new AssertionError("Empty recording was not empty");
        }
        method(api, "destroy", long.class).invoke(null, 0L);
        try
        {
            method(api, "prepare", long.class, long.class, long.class, int.class, int.class)
                .invoke(null, 0L, 0L, 0L, 0, 0);
            throw new AssertionError("Invalid recording was accepted");
        }
        catch (InvocationTargetException expected)
        {
            if (!(expected.getCause() instanceof IllegalStateException))
            {
                throw expected;
            }
        }
        try
        {
            long address = VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr");
            if (address == 0L)
            {
                throw new AssertionError("Existing LWJGL Vulkan provider has no instance entry point");
            }
            String provider = VK.getFunctionProvider() instanceof SharedLibrary library
                ? library.getName() : VK.getFunctionProvider().getClass().getName();
            System.out.println("LWJGL_VULKAN_PROVIDER " + provider);
        }
        catch (UnsatisfiedLinkError | ExceptionInInitializerError unavailable)
        {
            System.out.println("LWJGL_VULKAN_PROVIDER_UNAVAILABLE " + unavailable.getClass().getSimpleName());
        }
        System.out.println("NATIVE_ABI_PASS version=2 existing_core_addresses=true invalid_recording_rejected=true gpu_created=false");
    }
}
