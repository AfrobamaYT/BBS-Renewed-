package com.afrobama.blendertransform;

import net.minecraft.client.Minecraft;

/**
 * Sharp item icons in the HUD that the film editor draws inside its viewport (task 116).
 *
 * <p>26.x renders GUI item icons once into an atlas at the window's GUI scale and blits them
 * scaled. With the dashboard open the window runs at BBS' UI scale (measured: 2), while the
 * viewport HUD is laid out at the game's scale (measured: 4 at 1080p, about 9 at 4K) -- every
 * atlas pixel came out 2x2 to 4x4 on screen. While a viewport HUD asks for it, the atlas renders
 * at the game's scale instead; icons are then drawn down, never up.</p>
 */
public final class AtlasSchaerfeLogik
{
    private static volatile int stufe;
    private static volatile long bis;

    private AtlasSchaerfeLogik() {}

    /** Each frame a viewport HUD is drawn: the GUI scale its icons are shown at. */
    public static void anfordern(int gewuenscht)
    {
        stufe = gewuenscht;
        /* Held a second, not a frame: the atlas is rebuilt whenever its scale changes. */
        bis = System.nanoTime() + 1_000_000_000L;
    }

    /** GuiRenderer: the scale the item atlas is rendered at. */
    public static int atlasStufe(int fenster)
    {
        return System.nanoTime() < bis ? Math.max(fenster, stufe) : fenster;
    }
}
