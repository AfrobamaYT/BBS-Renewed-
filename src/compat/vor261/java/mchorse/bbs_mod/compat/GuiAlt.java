package mchorse.bbs_mod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 26.2 moved the current screen into Gui (NeoForge primer 26.2, "Gui Reorganization"); before
 * that Minecraft holds it. Injected into Gui. 26.2's gui.hud is the Gui itself before 26.1
 * (stonecutter-regeln.gradle).
 */
public interface GuiAlt
{
    default Screen screen()
    {
        return Minecraft.getInstance().screen;
    }

    default void setScreen(Screen screen)
    {
        Minecraft.getInstance().setScreen(screen);
    }

    /** 26.2's Hud.isHidden(); before 26.1 Options.hideGui. */
    default boolean isHidden()
    {
        return Minecraft.getInstance().options.hideGui;
    }

    /** 26.2's Hud.toggle(). */
    default void toggle()
    {
        Minecraft.getInstance().options.hideGui = !Minecraft.getInstance().options.hideGui;
    }
}
