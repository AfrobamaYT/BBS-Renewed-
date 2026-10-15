package mchorse.bbs_mod.ui.texturstudio;

import com.mojang.blaze3d.platform.InputConstants;
import de.keksuccino.rinku.RinkuBrowser;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * Zeichnet einen Rinku-Browser in seinen Bereich und reicht Maus und Tastatur an ihn weiter.
 *
 * <p>Der Browser rendert in echten Pixeln: Bereich (BBS-Einheiten) mal effektiver UI-Scale.
 * Mauskoordinaten gehen im selben Raum hinein. Tasten bekommt er nur, solange er den Fokus
 * hat (Klick hinein); Escape bleibt bei BBS, damit das Fenster sich schliessen laesst.</p>
 */
public class UIBrowserFlaeche extends UIElement
{
    private final RinkuBrowser browser;
    private int breite;
    private int hoehe;
    private int letzteX = Integer.MIN_VALUE;
    private int letzteY = Integer.MIN_VALUE;
    private boolean fokus;

    public UIBrowserFlaeche(RinkuBrowser browser)
    {
        this.browser = browser;
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
        return (Window.isShiftPressed() ? InputConstants.MOD_SHIFT : 0)
            | (Window.isCtrlPressed() ? InputConstants.MOD_CONTROL : 0)
            | (Window.isAltPressed() ? InputConstants.MOD_ALT : 0);
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
            this.fokus = innen;
            this.browser.setFocus(innen);
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

        this.browser.sendMouseWheel(this.px(context), this.py(context), context.mouseWheel, modifikatoren());

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!this.fokus || context.getKeyCode() == InputConstants.KEY_ESCAPE)
        {
            return false;
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
            ResourceLocation textur = this.browser.getTextureIdentifier();

            context.batcher.getContext().blit(RenderPipelines.GUI_TEXTURED, textur, this.area.x, this.area.y,
                0F, 0F, this.area.w, this.area.h, this.area.w, this.area.h);
        }

        super.render(context);
    }
}
