package com.mojang.blaze3d.systems;

/* BBS-Shim fuer Minecraft vor 1.21.5: diese Klasse gibt es dort nicht; sie bildet die oeffentliche
 * Form der 1.21.11-Fassung nach (eigene Implementierung, siehe docs/MEHRVERSION.md "vor1215"). */
public class ScissorState
{
    private boolean enabled;
    private int x, y, width, height;

    public void enable(int x, int y, int width, int height)
    {
        this.enabled = true;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void disable()
    {
        this.enabled = false;
    }

    public boolean enabled() { return this.enabled; }
    public int x() { return this.x; }
    public int y() { return this.y; }
    public int width() { return this.width; }
    public int height() { return this.height; }
}
