package com.mojang.blaze3d.platform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/* BBS-Shim fuer Minecraft vor 1.21.5: diese Klasse gibt es dort nicht; sie bildet die oeffentliche
 * Form der 1.21.11-Fassung nach (eigene Implementierung, siehe docs/MEHRVERSION.md "vor1215"). */
public enum DestFactor
{
    CONSTANT_ALPHA(GL14.GL_CONSTANT_ALPHA), CONSTANT_COLOR(GL14.GL_CONSTANT_COLOR), DST_ALPHA(GL11.GL_DST_ALPHA),
    DST_COLOR(GL11.GL_DST_COLOR), ONE(GL11.GL_ONE), ONE_MINUS_CONSTANT_ALPHA(GL14.GL_ONE_MINUS_CONSTANT_ALPHA),
    ONE_MINUS_CONSTANT_COLOR(GL14.GL_ONE_MINUS_CONSTANT_COLOR), ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA),
    ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR), ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA),
    ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR), SRC_ALPHA(GL11.GL_SRC_ALPHA), SRC_COLOR(GL11.GL_SRC_COLOR),
    ZERO(GL11.GL_ZERO);

    /** GL-Konstante des Faktors (Schicht-intern). */
    public final int gl;

    DestFactor(int gl)
    {
        this.gl = gl;
    }
}
