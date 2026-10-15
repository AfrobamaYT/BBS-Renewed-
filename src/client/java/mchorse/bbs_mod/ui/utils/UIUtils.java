package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.OS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import java.io.File;
import java.io.IOException;

public class UIUtils
{
    /**
     * Open web link (in default web browser)
     */
    public static boolean openWebLink(String address)
    {
        if (OS.CURRENT == OS.WINDOWS)
        {
            return runSysCommand("rundll32", "url.dll,FileProtocolHandler", address);
        }
        else if (OS.CURRENT == OS.MACOS)
        {
            return runSysCommand("open", address);
        }

        return runSysCommand("kde-open", address)
            || runSysCommand("gnome-open", address)
            || runSysCommand("xdg-open", address);
    }

    /**
     * Open a folder (in default file browser)
     */
    public static boolean openFolder(File folder)
    {
        try
        {
            String path = folder.getAbsolutePath();

            if (OS.CURRENT == OS.WINDOWS)
            {
                return runSysCommand("explorer", path);
            }
            else if (OS.CURRENT == OS.MACOS)
            {
                return runSysCommand("open", path);
            }

            return runSysCommand("kde-open", path)
                || runSysCommand("gnome-open", path)
                || runSysCommand("xdg-open", path);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    private static boolean runSysCommand(String... command)
    {
        try
        {
            Process p = Runtime.getRuntime().exec(command);

            if (p == null)
            {
                return false;
            }

            try
            {
                return p.exitValue() == 0;
            }
            catch (IllegalThreadStateException e)
            {
                return true;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return false;
        }
    }

    /**
     * Map a GUI area to a framebuffer-pixel viewport and apply it. GUI units map
     * to pixels by the window's scale factor, which since ui_scale became a float
     * can be fractional — so no rounding of the scale itself, only of the final
     * pixel edges. The window getters are the overridden ones during video
     * export, so the same mapping holds there.
     *
     * @return {x, y, w, h} of the applied viewport, for building a matching projection
     */
    public static int[] viewportArea(Area area)
    {
        Minecraft mc = Minecraft.getInstance();
        float scale = mchorse.bbs_mod.BBSModClient.getGUIScale();

        int vx = Math.round(area.x * scale);
        int vy = Math.round(mc.getWindow().getHeight() - (area.y + area.h) * scale);
        int vw = Math.round(area.w * scale);
        int vh = Math.round(area.h * scale);

        /* 26.2: einen globalen viewport gibt es nicht mehr -- der Ausschnitt gehoert zu
         * jedem Zeichendurchgang. Angemeldet wird er hier, freigegeben von der Stelle,
         * die ihn gesetzt hat (siehe Zeichenbereich). */
        mchorse.bbs_mod.graphics.Zeichenbereich.setzen(vx, vy, vw, vh);

        return new int[] {vx, vy, vw, vh};
    }

    public static void playClick()
    {
        playClick(1F);
    }

    public static void playClick(float pitch)
    {
        if (BBSSettings.clickSound.get())
        {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BBSMod.CLICK, pitch));
        }
        else
        {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }
}