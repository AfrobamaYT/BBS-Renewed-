package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.platform.Window;

/** vor1215: 1.21.9+ Window.handle() (GLFW-Fenster) heisst in 1.21.4 getWindow(). */
public interface WindowAlt
{
    default long handle()
    {
        return ((Window) this).getWindow();
    }
}
