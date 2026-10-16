package mchorse.bbs_mod.utils;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryStack;

public class WorldExportWindowSession
{
    private WindowSnapshot snapshot;
    private boolean changed;

    public void begin(int width, int height)
    {
        Window window = Minecraft.getInstance().getWindow();
        long handle = window.handle();

        if (this.snapshot == null)
        {
            this.snapshot = WindowSnapshot.capture(window, handle);
            this.changed = false;
        }

        if (this.snapshot.fullscreen)
        {
            return;
        }

        if (this.snapshot.maximized)
        {
            require(SDLVideo.SDL_RestoreWindow(handle));
        }

        if (window.getScreenWidth() != width || window.getScreenHeight() != height)
        {
            window.setWindowed(width, height);
        }

        this.restoreOriginalPosition(handle);

        this.changed = this.snapshot.maximized
            || this.snapshot.width != width
            || this.snapshot.height != height;
    }

    public void restore()
    {
        if (this.snapshot == null)
        {
            return;
        }

        Window window = Minecraft.getInstance().getWindow();
        long handle = window.handle();

        if (!this.snapshot.fullscreen && this.changed)
        {
            int width = Math.max(this.snapshot.width, 2);
            int height = Math.max(this.snapshot.height, 2);

            if (window.getScreenWidth() != width || window.getScreenHeight() != height)
            {
                window.setWindowed(width, height);
            }

            this.restoreOriginalPosition(handle);

            if (this.snapshot.maximized)
            {
                require(SDLVideo.SDL_MaximizeWindow(handle));
            }
        }

        this.clear();
    }

    public void clear()
    {
        this.snapshot = null;
        this.changed = false;
    }

    private void restoreOriginalPosition(long handle)
    {
        Position position = Position.capture(handle);

        if (position.x != this.snapshot.x || position.y != this.snapshot.y)
        {
            require(SDLVideo.SDL_SetWindowPosition(handle, this.snapshot.x, this.snapshot.y));
        }
    }

    private static class WindowSnapshot
    {
        private final int width;
        private final int height;
        private final int x;
        private final int y;
        private final boolean maximized;
        private final boolean fullscreen;

        private WindowSnapshot(int width, int height, int x, int y, boolean maximized, boolean fullscreen)
        {
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            this.maximized = maximized;
            this.fullscreen = fullscreen;
        }

        private static WindowSnapshot capture(Window window, long handle)
        {
            Position position = Position.capture(handle);
            long flags = SDLVideo.SDL_GetWindowFlags(handle);
            boolean maximized = (flags & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0L;
            boolean fullscreen = (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0L;

            return new WindowSnapshot(window.getScreenWidth(), window.getScreenHeight(), position.x, position.y, maximized, fullscreen);
        }
    }

    private static class Position
    {
        private final int x;
        private final int y;

        private Position(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        private static Position capture(long handle)
        {
            try (MemoryStack stack = MemoryStack.stackPush())
            {
                java.nio.IntBuffer x = stack.mallocInt(1);
                java.nio.IntBuffer y = stack.mallocInt(1);

                require(SDLVideo.SDL_GetWindowPosition(handle, x, y));

                return new Position(x.get(0), y.get(0));
            }
        }
    }

    private static void require(boolean success)
    {
        if (!success)
        {
            throw new IllegalStateException("Cannot update export window: " + SDLError.SDL_GetError());
        }
    }
}
