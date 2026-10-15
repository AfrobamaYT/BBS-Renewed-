package com.afrobama.blendertransform;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;

/** SDL uses one process event queue; a shared upload window must not resize Minecraft. */
public final class SdlWindowEventLogik
{
    private SdlWindowEventLogik()
    {}

    public static boolean foreign(Window window, SDL_Event event)
    {
        int type = event.type();
        if (!isWindowEvent(type)) return false;
        int owner = SDLVideo.SDL_GetWindowID(window.handle());
        if (owner == 0) throw new IllegalStateException("Minecraft SDL window has no ID: " + SDLError.SDL_GetError());
        return isForeignEvent(type, event.window().windowID(), owner);
    }

    public static boolean isForeignEvent(int type, int eventWindow, int owner)
    {
        if (!isWindowEvent(type)) return false;
        if (owner == 0) throw new IllegalArgumentException("A real SDL window ID is required");
        return eventWindow != owner;
    }

    private static boolean isWindowEvent(int type)
    {
        return type >= SDLEvents.SDL_EVENT_WINDOW_FIRST && type <= SDLEvents.SDL_EVENT_WINDOW_LAST;
    }
}
