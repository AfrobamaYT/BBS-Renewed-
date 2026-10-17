package mchorse.bbs_mod.ui.texturstudio;

import com.mojang.blaze3d.platform.InputConstants;
import de.keksuccino.rinku.RinkuBrowser;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.cef.browser.CefZoomCommand;
import org.lwjgl.sdl.SDLScancode;

/**
 * Zeichnet einen Rinku-Browser in seinen Bereich und reicht Maus und Tastatur an ihn weiter.
 *
 * <p>Der Browser rendert in echten Pixeln: Bereich (BBS-Einheiten) mal effektiver UI-Scale.
 * Mauskoordinaten gehen im selben Raum hinein. Tasten bekommt er nur, solange er den Fokus
 * hat (Klick hinein); Escape bleibt bei BBS, damit das Fenster sich schliessen laesst.</p>
 */
public class UIBrowserFlaeche extends UIElement implements IFocusedUIElement
{
    private final RinkuBrowser browser;
    private int breite;
    private int hoehe;
    private int letzteX = Integer.MIN_VALUE;
    private int letzteY = Integer.MIN_VALUE;
    private boolean fokus;

    public UIBrowserFlaeche(RinkuBrowser browser)
    {
        this.browser = browser.useBrowserControls(false);
    }

    @Override
    public boolean isFocused()
    {
        return this.fokus;
    }

    @Override
    public void focus(UIContext context)
    {
        this.fokus = true;
        this.browser.setFocus(true);
    }

    @Override
    public void unfocus(UIContext context)
    {
        this.fokus = false;
        this.browser.setFocus(false);
    }

    @Override
    public void selectAll(UIContext context)
    {
        this.browser.sendKeyPress(InputConstants.KEY_A, 0L, InputConstants.MOD_CONTROL);
        this.browser.sendKeyRelease(InputConstants.KEY_A, 0L, InputConstants.MOD_CONTROL);
    }

    @Override
    public void unselect(UIContext context)
    {
        /* The embedded document owns its selection; changing BBS focus must preserve it. */
    }

    @Override
    public void resize()
    {
        super.resize();

        float skala = BBSModClient.getGUIScale();
        int b = Math.max(1, Math.round(this.area.w * skala));
        int h = Math.max(1, Math.round(this.area.h * skala));

        if (b != this.breite || h != this.hoehe)
        {
            this.breite = b;
            this.hoehe = h;
            this.browser.resize(b, h);
        }
    }

    private int px(UIContext context)
    {
        return Math.round((context.mouseX - this.area.x) * BBSModClient.getGUIScale());
    }

    private int py(UIContext context)
    {
        return Math.round((context.mouseY - this.area.y) * BBSModClient.getGUIScale());
    }

    private static int modifikatoren()
    {
        return (KeyCombo.isKeyDown(InputConstants.KEY_LSHIFT) ? InputConstants.MOD_SHIFT : 0)
            | (KeyCombo.isKeyDown(InputConstants.KEY_LCONTROL) ? InputConstants.MOD_CONTROL : 0)
            | (KeyCombo.isKeyDown(InputConstants.KEY_LALT) ? InputConstants.MOD_ALT : 0);
    }

    private boolean zoomTaste(UIContext context)
    {
        if (!KeyCombo.isKeyDown(InputConstants.KEY_LCONTROL))
        {
            return false;
        }

        int taste = context.getKeyCode();
        int zeichen = context.getScanCode();
        CefZoomCommand zoom;

        if (taste == InputConstants.KEY_EQUALS || zeichen == '+' || taste == SDLScancode.SDL_SCANCODE_KP_PLUS)
        {
            zoom = CefZoomCommand.IN;
        }
        else if (taste == InputConstants.KEY_MINUS || taste == SDLScancode.SDL_SCANCODE_KP_MINUS)
        {
            zoom = CefZoomCommand.OUT;
        }
        else if (taste == InputConstants.KEY_0 || taste == SDLScancode.SDL_SCANCODE_KP_0)
        {
            zoom = CefZoomCommand.RESET;
        }
        else
        {
            return false;
        }

        if (context.getKeyAction() != KeyAction.RELEASED)
        {
            this.zoomAnsicht(zoom);
        }

        return true;
    }

    private void zoomAnsicht(CefZoomCommand zoom)
    {
        if (this.browser.getURL().startsWith(TexturStudioKanal.SEITE))
        {
            String aktion = switch (zoom)
            {
                case IN -> "zoomIn";
                case OUT -> "zoomOut";
                case RESET -> KeyCombo.isKeyDown(InputConstants.KEY_LALT) ? "pixelToPixel" : "fitTheArea";
            };

            this.browser.executeJavaScript("bbsAnsicht('" + aktion + "');", TexturStudioKanal.SEITE, 0);
        }
        else
        {
            this.browser.zoom(zoom);
        }
    }

    /** BBS zaehlt Maustasten alt (0 links, 1 rechts, 2 Mitte), Rinku erwartet Minecrafts SDL-Nummern. */
    private static int taste(int bbs)
    {
        return bbs == 1 ? InputConstants.MOUSE_BUTTON_RIGHT : bbs == 2 ? InputConstants.MOUSE_BUTTON_MIDDLE : InputConstants.MOUSE_BUTTON_LEFT;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        boolean innen = this.area.isInside(context);

        if (innen != this.fokus)
        {
            context.focus(innen ? this : null);
        }

        if (!innen)
        {
            return false;
        }

        this.browser.sendMousePress(this.px(context), this.py(context), taste(context.mouseButton));

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (!this.fokus)
        {
            return false;
        }

        this.browser.sendMouseRelease(this.px(context), this.py(context), taste(context.mouseButton));

        return this.area.isInside(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        if (KeyCombo.isKeyDown(InputConstants.KEY_LALT) && context.mouseWheel != 0D
            && this.browser.getURL().startsWith(TexturStudioKanal.SEITE))
        {
            this.zoomAnsicht(context.mouseWheel > 0D ? CefZoomCommand.IN : CefZoomCommand.OUT);
        }
        else if (KeyCombo.isKeyDown(InputConstants.KEY_LCONTROL) && context.mouseWheel != 0D)
        {
            this.browser.zoom(context.mouseWheel > 0D ? CefZoomCommand.IN : CefZoomCommand.OUT);
        }
        else
        {
            this.browser.sendMouseWheel(this.px(context), this.py(context), context.mouseWheel, modifikatoren());
        }

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!this.fokus || context.getKeyCode() == InputConstants.KEY_ESCAPE)
        {
            return false;
        }

        if (this.zoomTaste(context))
        {
            return true;
        }

        if (context.getKeyAction() == KeyAction.RELEASED)
        {
            this.browser.sendKeyRelease(context.getKeyCode(), context.getScanCode(), modifikatoren());
        }
        else
        {
            this.browser.sendKeyPress(context.getKeyCode(), context.getScanCode(), modifikatoren());
        }

        return true;
    }

    @Override
    protected boolean subTextInput(UIContext context)
    {
        if (!this.fokus)
        {
            return false;
        }

        if (KeyCombo.isKeyDown(InputConstants.KEY_LCONTROL) && "+-=0".indexOf(context.getInputCharacter()) >= 0)
        {
            return true;
        }

        this.browser.sendKeyTyped(context.getInputCharacter(), modifikatoren());

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.px(context);
        int y = this.py(context);

        if (this.area.isInside(context) && (x != this.letzteX || y != this.letzteY))
        {
            this.letzteX = x;
            this.letzteY = y;
            this.browser.sendMouseMove(x, y);
        }

        if (this.browser.isTextureReady())
        {
            Identifier textur = this.browser.getTextureIdentifier();

            context.batcher.getContext().blit(RenderPipelines.GUI_TEXTURED, textur, this.area.x, this.area.y,
                0F, 0F, this.area.w, this.area.h, this.area.w, this.area.h);
        }

        super.render(context);
    }
}
