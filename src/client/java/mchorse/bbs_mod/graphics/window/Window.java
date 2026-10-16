package mchorse.bbs_mod.graphics.window;

import org.lwjgl.sdl.SDLMouse;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Window
{
    public static final int CURSOR_DEFAULT = SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT;
    public static final int CURSOR_TEXT = SDLMouse.SDL_SYSTEM_CURSOR_TEXT;
    public static final int CURSOR_POINTER = SDLMouse.SDL_SYSTEM_CURSOR_POINTER;
    public static final int CURSOR_CROSSHAIR = SDLMouse.SDL_SYSTEM_CURSOR_CROSSHAIR;
    public static final int CURSOR_EW_RESIZE = SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE;
    public static final int CURSOR_NS_RESIZE = SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE;
    private static int verticalScroll;
    private static long lastScroll;
    private static final Map<Integer, Long> standardCursors = new HashMap<>();
    private static int currentCursorShape = -1;

    public static long getWindow()
    {
        return Minecraft.getInstance().getWindow().handle();
    }

    public static void setVerticalScroll(int scroll)
    {
        verticalScroll = scroll;
        lastScroll = System.currentTimeMillis();
    }

    public static int getVerticalScroll()
    {
        if (lastScroll + 5 < System.currentTimeMillis())
        {
            return 0;
        }

        return verticalScroll;
    }

    public static boolean isMouseButtonPressed(int mouse)
    {
        return isNativeMouseButtonPressed(nativeMouseButton(mouse));
    }

    public static boolean isNativeMouseButtonPressed(int button)
    {
        return button > 0 && button <= 32
            && (SDLMouse.nSDL_GetMouseState(0L, 0L) & (1 << (button - 1))) != 0;
    }

    public static int nativeMouseButton(int button)
    {
        return switch (button)
        {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> button + 1;
        };
    }

    public static int legacyMouseButton(int button)
    {
        return switch (button)
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
    }

    public static void getCursorPosition(double[] x, double[] y)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            java.nio.FloatBuffer nativeX = stack.mallocFloat(1);
            java.nio.FloatBuffer nativeY = stack.mallocFloat(1);

            SDLMouse.SDL_GetMouseState(nativeX, nativeY);
            x[0] = nativeX.get(0);
            y[0] = nativeY.get(0);
        }
    }

    /* ⛔ MODIFIKATOREN LAUFEN DURCH DEN UEBERNAHME-HALTER (2026-08-04 verdrahtet).
     *
     * Ohne das kann kein Test eine Taste GEHALTEN pruefen -- und genau daran haengt der
     * halbe Editor: Alt schaltet den Auswahlpuffer von Knochen- auf Replay-Kennungen um.
     * Der Weg ueber das Betriebssystem wurde gemessen und verworfen (Robot ist headless,
     * Win32-Fokus scheitert still in der Haelfte der Faelle); Begruendung mit Zahlen steht
     * im Klassen-Javadoc von VirtuelleTasten.
     *
     * Nicht scharf -- Normalfall, auch beim Nutzer -- heisst: echter Wert unveraendert,
     * Kosten ein volatile-Read je Abfrage. */
    public static boolean isCtrlPressed()
    {
        return mchorse.bbs_mod.sonden.Sonden.eingabe.strg(
            Minecraft.getInstance().hasControlDown());
    }

    public static boolean isShiftPressed()
    {
        return mchorse.bbs_mod.sonden.Sonden.eingabe.umschalt(
            Minecraft.getInstance().hasShiftDown());
    }

    public static boolean isAltPressed()
    {
        return mchorse.bbs_mod.sonden.Sonden.eingabe.alt(
            Minecraft.getInstance().hasAltDown());
    }

    public static boolean isKeyPressed(int key)
    {
        if (mchorse.bbs_mod.ui.utils.keys.LegacyKeyboardKeys.isUnmapped(key))
        {
            return false;
        }

        return InputConstants.isKeyDown(key);
    }

    public static String getClipboard()
    {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    public static MapType getClipboardMap()
    {
        return DataToString.mapFromString(getClipboard());
    }

    /**
     * Get a data map from clipboard with verification key.
     */
    public static MapType getClipboardMap(String verificationKey)
    {
        MapType data = DataToString.mapFromString(getClipboard());

        return data != null && data.getBool(verificationKey) ? data : null;
    }

    public static ListType getClipboardList()
    {
        return DataToString.listFromString(getClipboard());
    }

    /**
     * ⛔ NOT through {@code KeyboardHandler.setClipboard}: Mojang's ClipboardManager encodes the
     * text on LWJGL's thread-local MemoryStack (64 KiB by default). A copied preset or film part
     * is larger than that, and the copy crashed the game with "OutOfMemoryError: Out of stack
     * space" in SDL_SetClipboardText (owner's instance, crash-2026-10-13_21.03.38). The text
     * goes on the native heap instead, for any size.
     */
    public static void setClipboard(String string)
    {
        java.nio.ByteBuffer text = org.lwjgl.system.MemoryUtil.memUTF8(string == null ? "" : string);

        try
        {
            if (!org.lwjgl.sdl.SDLClipboard.SDL_SetClipboardText(text))
            {
                System.err.println("[clipboard] SDL_SetClipboardText failed: " + org.lwjgl.sdl.SDLError.SDL_GetError());
            }
        }
        finally
        {
            org.lwjgl.system.MemoryUtil.memFree(text);
        }
    }

    public static void setClipboard(BaseType data)
    {
        if (data != null)
        {
            setClipboard(DataToString.toString(data, true));
        }
    }

    /**
     * Save given data to clipboard with a verification key that could be
     * used in {@link #getClipboardMap(String)} to decode data.
     */
    public static void setClipboard(MapType data, String verificationKey)
    {
        if (data != null)
        {
            data.putBool(verificationKey, true);
        }

        setClipboard(data);
    }

    public static void moveCursor(int x, int y)
    {
        SDLMouse.SDL_WarpMouseInWindow(getWindow(), x, y);
    }

    public static void setStandardCursor(int shape)
    {
        if (SDLMouse.SDL_GetWindowRelativeMouseMode(getWindow()))
        {
            currentCursorShape = -1;

            return;
        }

        if (currentCursorShape == shape)
        {
            return;
        }

        long cursor = standardCursors.computeIfAbsent(shape, SDLMouse::SDL_CreateSystemCursor);

        if (cursor == 0L || !SDLMouse.SDL_SetCursor(cursor))
        {
            throw new IllegalStateException("Cannot set BBS cursor: " + SDLError.SDL_GetError());
        }
        currentCursorShape = shape;
    }

    public static void resetCursor()
    {
        setStandardCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
    }
}
