package mchorse.bbs_mod.ui.framework.elements.input.drag;

import org.lwjgl.glfw.GLFW; /* SDLMouse */
import mchorse.bbs_mod.graphics.window.Window;

import org.lwjgl.BufferUtils;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_Surface;
*///?} else {
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
//?}

import java.nio.ByteBuffer;

/**
 * Der Mauszeiger waehrend einer Transform-Bearbeitung — Blenders Rueckmeldung (Akte 07):
 * Grab zeigt das Move-Kreuz, Scale den Doppelpfeil, Rotate ein Rotier-Symbol.
 *
 * <p>Hardware-Cursor ueber GLFW: Move und Scale sind Standard-Cursor (GLFW 3.4,
 * RESIZE_ALL/RESIZE_EW), fuers Rotieren gibt es keinen Standard — der wird einmalig als
 * 24x24-Bitmap gemalt (Kreisring mit zwei Pfeilspitzen) und per glfwCreateCursor angelegt.</p>
 *
 * <p>⛔ Gesetzt wird NUR bei Moduswechsel, und {@link #zuruecksetzen()} MUSS am Ende jeder
 * Bearbeitung laufen (der {@code UITransformHandler} verlaesst mit ihr den Baum) — ein
 * haengender Hardware-Cursor ueberlebt sonst den Editor. glfwSetCursor(handle, 0) stellt
 * den Pfeil wieder her.</p>
 *
 * <p>⚠ Framebuffer-Screenshots zeigen Hardware-Cursor NICHT — der Beleg fuer diese Klasse
 * ist der gemessene Zustand ({@link #aktuellerModus}), die Sichtpruefung gehoert dem
 * Nutzer am echten Bildschirm.</p>
 */
public final class TransformZeiger
{
    public enum Modus
    {
        NORMAL, MOVE, SCALE, ROTATE
    }

    private static Modus aktuellerModus = Modus.NORMAL;

    private static long moveCursor;
    private static long scaleCursor;
    private static long rotateCursor;

    private TransformZeiger()
    {}

    public static Modus aktuellerModus()
    {
        return aktuellerModus;
    }

    /** Zeiger fuer eine Transform-Art setzen; {@code null} = normaler Pfeil. */
    public static void setzen(TransformOp op)
    {
        Modus modus = op == null ? Modus.NORMAL
            : op == TransformOp.TRANSLATE ? Modus.MOVE
            : op == TransformOp.SCALE ? Modus.SCALE
            : Modus.ROTATE;

        if (modus == aktuellerModus)
        {
            return;
        }

        aktuellerModus = modus;

        long cursor = switch (modus)
        {
            case MOVE -> moveCursor();
            case SCALE -> scaleCursor();
            case ROTATE -> rotateCursor();
            //? if >=26.3 {
            /*default -> SDLMouse.SDL_GetDefaultCursor();
            *///?} else {
            default -> 0L;
            //?}
        };

        //? if >=26.3 {
        /*if (cursor == 0L || !SDLMouse.SDL_SetCursor(cursor))
        {
            throw new IllegalStateException("Cannot set transform cursor: " + SDLError.SDL_GetError());
        }
        *///?} else {
        GLFW.glfwSetCursor(Window.getWindow(), cursor);
        //?}
    }

    public static void zuruecksetzen()
    {
        setzen(null);
    }

    private static long moveCursor()
    {
        if (moveCursor == 0L)
        {
            //? if >=26.3 {
            /*moveCursor = SDLMouse.SDL_CreateSystemCursor(GLFW.GLFW_RESIZE_ALL_CURSOR);
            *///?} else {
            moveCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR);
            //?}
        }

        return moveCursor;
    }

    private static long scaleCursor()
    {
        if (scaleCursor == 0L)
        {
            //? if >=26.3 {
            /*scaleCursor = SDLMouse.SDL_CreateSystemCursor(GLFW.GLFW_HRESIZE_CURSOR);
            *///?} else {
            scaleCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
            //?}
        }

        return scaleCursor;
    }

    /**
     * Blenders Rotier-Symbol als prozedurales Bitmap: ein Kreisring mit zwei
     * gegenueberliegenden Pfeilspitzen. Weiss mit schwarzem Rand, damit er auf hellem wie
     * dunklem Grund lesbar ist.
     */
    private static long rotateCursor()
    {
        if (rotateCursor != 0L)
        {
            return rotateCursor;
        }

        int groesse = 24;
        int[] bild = new int[groesse * groesse];
        float mitte = (groesse - 1) / 2F;

        /* Ring: Radius 8, Staerke ~2.4, mit Luecken bei den Pfeilspitzen (0deg und 180deg). */
        for (int y = 0; y < groesse; y++)
        {
            for (int x = 0; x < groesse; x++)
            {
                float dx = x - mitte;
                float dy = y - mitte;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                double winkel = Math.toDegrees(Math.atan2(dy, dx));

                boolean ring = r > 6.6F && r < 9.4F;
                boolean luecke = Math.abs(winkel) < 28 || Math.abs(winkel) > 152;

                if (ring && !luecke)
                {
                    bild[y * groesse + x] = 0xFFFFFFFF;
                }
            }
        }

        /* Pfeilspitzen in den Luecken: rechts zeigt nach unten, links nach oben (Drehsinn). */
        pfeilspitze(bild, groesse, (int) (mitte + 8), (int) mitte, true);
        pfeilspitze(bild, groesse, (int) (mitte - 8), (int) mitte, false);
        rand(bild, groesse);

        ByteBuffer pixel = BufferUtils.createByteBuffer(groesse * groesse * 4);

        for (int argb : bild)
        {
            pixel.put((byte) ((argb >> 16) & 0xFF)).put((byte) ((argb >> 8) & 0xFF)).put((byte) (argb & 0xFF)).put((byte) ((argb >> 24) & 0xFF));
        }

        pixel.flip();

        //? if >=26.3 {
        /*SDL_Surface abbild = SDLSurface.SDL_CreateSurfaceFrom(groesse, groesse,
            SDLPixels.SDL_PIXELFORMAT_RGBA32, pixel, groesse * 4);
        *///?} else {
        GLFWImage abbild = GLFWImage.create();
        //?}

        //? if >=26.3 {
        /*if (abbild == null)
        {
            throw new IllegalStateException("Cannot create transform cursor image: " + SDLError.SDL_GetError());
        }

        try
        {
            rotateCursor = SDLMouse.SDL_CreateColorCursor(abbild, groesse / 2, groesse / 2);
        }
        finally
        {
            SDLSurface.SDL_DestroySurface(abbild);
        }

        if (rotateCursor == 0L)
        {
            throw new IllegalStateException("Cannot create transform cursor: " + SDLError.SDL_GetError());
        }
        *///?} else {
        abbild.set(groesse, groesse, pixel);
        rotateCursor = GLFW.glfwCreateCursor(abbild, groesse / 2, groesse / 2);
        //?}

        return rotateCursor;
    }

    private static void pfeilspitze(int[] bild, int groesse, int sx, int sy, boolean nachUnten)
    {
        for (int i = 0; i < 5; i++)
        {
            int breite = 4 - i;
            int y = sy + (nachUnten ? i : -i);

            for (int x = sx - breite; x <= sx + breite; x++)
            {
                if (x >= 0 && x < groesse && y >= 0 && y < groesse)
                {
                    bild[y * groesse + x] = 0xFFFFFFFF;
                }
            }
        }
    }

    /** Ein Pixel schwarzer Rand um alles Weisse — Lesbarkeit auf hellem Grund. */
    private static void rand(int[] bild, int groesse)
    {
        int[] kopie = bild.clone();

        for (int y = 0; y < groesse; y++)
        {
            for (int x = 0; x < groesse; x++)
            {
                if (kopie[y * groesse + x] != 0)
                {
                    continue;
                }

                aussen:
                for (int oy = -1; oy <= 1; oy++)
                {
                    for (int ox = -1; ox <= 1; ox++)
                    {
                        int nx = x + ox;
                        int ny = y + oy;

                        if (nx >= 0 && nx < groesse && ny >= 0 && ny < groesse && kopie[ny * groesse + nx] == 0xFFFFFFFF)
                        {
                            bild[y * groesse + x] = 0xFF000000;

                            break aussen;
                        }
                    }
                }
            }
        }
    }
}
