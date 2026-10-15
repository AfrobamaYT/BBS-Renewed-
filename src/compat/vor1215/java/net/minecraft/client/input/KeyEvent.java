package net.minecraft.client.input;

/**
 * BBS-Shim fuer Minecraft vor 1.21.9: das Tastenereignis, das KeyboardHandler ab 1.21.9
 * statt fuenf Zahlen weitergibt. Vor 1.21.9 baut KeyboardMixin es aus diesen Zahlen.
 */
public record KeyEvent(int key, int scancode, int modifiers)
{
    /** 26.x-Name des Scancodes. */
    public int keycode()
    {
        return this.scancode;
    }

    /* GLFW-Modifier-Bits (GLFW_MOD_SHIFT/CONTROL/ALT), wie Mojangs KeyEvent ab 1.21.9. */
    public boolean hasShiftDown()
    {
        return (this.modifiers & 1) != 0;
    }

    public boolean hasControlDown()
    {
        return (this.modifiers & 2) != 0;
    }

    public boolean hasAltDown()
    {
        return (this.modifiers & 4) != 0;
    }
}
