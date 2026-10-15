package com.mojang.blaze3d.opengl;

import org.lwjgl.opengl.GL11;

/* BBS-Shim fuer Minecraft vor 1.21.5: oeffentliche Form der 1.21.11-Fassung, eigene Implementierung
 * (siehe docs/MEHRVERSION.md "vor1215"). */
public class GlStateManager extends com.mojang.blaze3d.platform.GlStateManager
{
    /** Vor 1.21.5 gibt es keinen Zwischenspeicher fuer den Lesepuffer: direkt an GL. */
    public static void _glReadBuffer(int buffer)
    {
        GL11.glReadBuffer(buffer);
    }
}
