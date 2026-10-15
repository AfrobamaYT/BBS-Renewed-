// Coded by Afrobama/Claude — BBS <-> DaVinci Resolve live link (task 109, docs/DAVINCI-LINK-PLAN.md).
package com.davincilink;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.opengl.GL11;

/**
 * The Resolve tab: the real DaVinci Resolve over the whole film editor, one to one.
 *
 * <p>Where Resolve's viewer shows BBS' film (the placeholder colour, {@link DavinciViewerFinder}),
 * the user switches between Resolve's own picture and BBS' live viewport: in BBS mode the film
 * preview is laid onto exactly that rectangle and drawn above Resolve, and the mouse there goes
 * to BBS. Everything else goes to Resolve through XTest -- never the user's own pointer.</p>
 */
public class UIDavinciOverlay extends UIElement
{
    private final UIFilmPanel panel;
    private final Texture texture = new Texture();
    private boolean bbsViewport = true;
    private int lastX = -1, lastY = -1;
    /** Buttons whose press went to Resolve: only their release follows (2026-10-11 a release in
     * BBS' viewport still moved Resolve's pointer there and let go of a button never pressed). */
    private final java.util.Set<Integer> pressed = new java.util.HashSet<>();
    private final java.util.Set<Integer> keys = new java.util.HashSet<>();

    public UIDavinciOverlay(UIFilmPanel panel)
    {
        this.panel = panel;
        this.texture.setFormat(TextureFormat.RGBA_U8);
        this.setVisible(false);
    }

    public void toggle()
    {
        this.setVisible(!this.isVisible());

        if (this.isVisible())
        {
            this.start();
        }
        else
        {
            DavinciScreen.INSTANCE.pause();
            this.panel.resize();                      // the preview goes back to its dock
        }
    }

    /** Starts Resolve in the tab; nothing happens while it already runs. */
    public void start()
    {
        /* The private screen in real pixels, so Resolve is as sharp as the editor itself. */
        double scale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();

        DavinciScreen.INSTANCE.startAsync((int) (this.area.w * scale), (int) (this.area.h * scale));
    }

    public void switchViewport()
    {
        this.bbsViewport = !this.bbsViewport;
    }

    public boolean showsBbsViewport()
    {
        return this.bbsViewport;
    }

    /** The viewer rectangle in this element's coordinates, or null. */
    private int[] viewer()
    {
        DavinciScreen screen = DavinciScreen.INSTANCE;
        int[] box = screen.viewer();

        if (box == null || screen.width() == 0)
        {
            return null;
        }

        float sx = this.area.w / (float) screen.width(), sy = this.area.h / (float) screen.height();

        return new int[] {this.area.x + Math.round(box[0] * sx), this.area.y + Math.round(box[1] * sy),
            Math.round(box[2] * sx), Math.round(box[3] * sy)};
    }

    private boolean inBbsViewport(UIContext context)
    {
        int[] box = this.bbsViewport ? this.viewer() : null;

        return box != null && context.mouseX >= box[0] && context.mouseX < box[0] + box[2]
            && context.mouseY >= box[1] && context.mouseY < box[1] + box[3];
    }

    private void pointer(UIContext context)
    {
        DavinciScreen screen = DavinciScreen.INSTANCE;
        DavinciX11 x11 = screen.input();

        if (x11 == null || this.area.w <= 0)
        {
            return;
        }

        int x = (context.mouseX - this.area.x) * screen.width() / this.area.w;
        int y = (context.mouseY - this.area.y) * screen.height() / this.area.h;

        if (x != this.lastX || y != this.lastY)
        {
            x11.move(x, y);
            this.lastX = x;
            this.lastY = y;
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || this.inBbsViewport(context) || DavinciScreen.INSTANCE.input() == null)
        {
            return false;
        }

        this.pointer(context);
        DavinciScreen.INSTANCE.input().button(DavinciKeys.xButton(context.mouseButton), true);
        this.pressed.add(context.mouseButton);

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (DavinciScreen.INSTANCE.input() == null || !this.pressed.remove(context.mouseButton))
        {
            return false;
        }

        /* A drag that began in Resolve ends there, wherever the mouse is let go. */
        this.pointer(context);
        DavinciScreen.INSTANCE.input().button(DavinciKeys.xButton(context.mouseButton), false);

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context) || this.inBbsViewport(context) || DavinciScreen.INSTANCE.input() == null)
        {
            return false;
        }

        this.pointer(context);
        DavinciScreen.INSTANCE.input().scroll((int) Math.signum(context.mouseWheel));

        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        DavinciX11 x11 = DavinciScreen.INSTANCE.input();
        /* Physical keys: in 26.3 the SDL scancode is UIContext's key code (KeyEvent.key()); its "scan
         * code" holds the SDL keycode -- Space arrived in Resolve as "3" (measured with X RECORD,
         * 2026-10-12) and Resolve's own shortcuts moved the clips. The private server applies the
         * user's layout. */
        int code = DavinciKeys.toX(context.getKeyCode());
        boolean release = context.getKeyAction() == KeyAction.RELEASED;

        /* A key pressed for Resolve is let go there too, even over BBS' viewport by then. */
        if (x11 == null || code == 0 || (release ? !this.keys.remove(code) : this.inBbsViewport(context)))
        {
            return false;
        }
        if (!release)
        {
            this.keys.add(code);
        }

        x11.keycode(code, !release);

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        DavinciScreen screen = DavinciScreen.INSTANCE;

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

        if (screen.width() > 0)
        {
            screen.upload((buffer) ->
            {
                /* The first frame of a size allocates; after that the bytes go straight to the GPU.
                 * Texture.uploadTexture would scan all 1.9 million pixels for translucency on
                 * every frame, 30 times a second, for a picture that is always opaque. */
                if (this.texture.width != screen.width() || this.texture.height != screen.height())
                {
                    this.texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, screen.width(), screen.height(), buffer);
                }
                else
                {
                    mchorse.bbs_mod.graphics.texture.TextureUpload.write(this.texture.gpu(), TextureFormat.RGBA_U8,
                        0, screen.width(), screen.height(), buffer);
                }
            });
        }

        if (screen.frames() > 0)
        {
            context.batcher.fullTexturedBox(this.texture, this.area.x, this.area.y, this.area.w, this.area.h);
        }
        else
        {
            String text = screen.error().isEmpty() ? "DaVinci Resolve starts..." : screen.error();

            context.batcher.textCard(text, this.area.mx() - context.batcher.getFont().getWidth(text) / 2, this.area.my(), Colors.WHITE, Colors.A50);
        }

        if (screen.frames() > 0 && DavinciLinkClient.SERVER.connectionCount() == 0)
        {
            /* Without the link the tab is only Resolve: no film, no sync. Studio gets the link
             * started by BBS; the free Resolve only takes it from its own script menu. */
            String hint = "DaVinci: Workspace > Scripts > BBS Link";

            context.batcher.textCard(hint, this.area.x + 8, this.area.ey() - 20, Colors.WHITE, Colors.A75 | 0x8a2b2b);
        }

        if (this.area.isInside(context) && !this.inBbsViewport(context))
        {
            this.pointer(context);
            /* The editor's fields under Resolve are drawn first and claim the tooltip by their own
             * area: their hints showed over Resolve (owner's screenshots, 2026-10-12). */
            context.tooltip.set(context, null);
        }

        int[] box = this.bbsViewport ? this.viewer() : null;

        if (box != null)
        {
            this.panel.preview.area.set(box[0], box[1], box[2], box[3]);
            this.panel.preview.render(context);
        }

        super.render(context);
    }
}
