package mchorse.bbs_mod.ui.hilfe;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;
import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Show me (spec section 7): gets the place ready — the film editor, the side bar tab, the
 * timeline on Camera clips — then rings it in orange with a pulse and "Here: …" for 5.2 s. Never
 * dims and never takes input: clicks, wheel and keys reach the editor underneath; Esc or a click on
 * the target ends it early. It lies under the mini-player, so nothing of ours is on the video.
 */
public class UIHilfeSpot extends UIElement
{
    private final NanoFlaeche flaeche;
    private final String ziel;
    private final String etikett;
    private final long beginn = System.currentTimeMillis() + 430L;
    private boolean ende;
    private boolean ausgewichen;

    /** False when this version has no such place. */
    static boolean zeigen(String ziel)
    {
        UIDashboard d = BBSModClient.getDashboard();
        String etikett = HilfeZiel.etikett(ziel);

        if (d == null || etikett == null)
        {
            return false;
        }

        HilfeZiel.vorbereiten(d, ziel);
        d.overlay.prepend(new UIHilfeSpot(ziel, etikett));

        return true;
    }

    private UIHilfeSpot(String ziel, String etikett)
    {
        this.ziel = ziel;
        this.etikett = etikett;
        this.flaeche = new NanoFlaeche(this::zeichnen);
        this.flaeche.relative(this).w(1F).h(1F);
        this.add(this.flaeche);
    }

    /* The ring takes no input: nothing reaches its surface */
    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        return null;
    }

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        return null;
    }

    @Override
    protected IUIElement childrenMouseReleased(UIContext context)
    {
        return null;
    }

    @Override
    protected IUIElement childrenKeyPressed(UIContext context)
    {
        return null;
    }

    @Override
    protected IUIElement childrenTextInput(UIContext context)
    {
        return null;
    }

    /** A click on the target ends it — and still lands on the target. */
    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        float[] r = HilfeZiel.ort(this.ziel);
        int mx = context.mouseX(), my = context.mouseY();

        this.ende |= r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];

        return false;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(InputConstants.KEY_ESCAPE))
        {
            this.ende = true;

            return true;
        }

        return false;
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.ende || System.currentTimeMillis() - this.beginn > 5200L)
        {
            this.removeFromParent();

            return;
        }

        if (this.area.w != context.menu.width || this.area.h != context.menu.height)
        {
            this.set(0, 0, context.menu.width, context.menu.height);
            this.resize();
        }

        if (!this.ausgewichen && System.currentTimeMillis() >= this.beginn)
        {
            this.ausgewichen = true;
            UIHilfeMini.ausweichen(HilfeZiel.ort(this.ziel));
        }

        this.flaeche.neuMalen();
        super.render(context);
    }

    private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
    {
        float[] r = HilfeZiel.ort(this.ziel);
        long t = System.currentTimeMillis() - this.beginn;

        if (t < 0)
        {
            return;
        }

        float k = Math.max(1F, f.skala());

        if (r == null)
        {
            /* Nothing to ring: say so under the top bar instead of showing nothing */
            String text = HilfeZiel.fehlt(this.ziel);
            float tw = NanoStifte.textBreite(vg, NanoDesign.SANS, 11F * k, text) + 20F * k;

            NanoStifte.flaeche(vg, (b - tw) / 2F, 44F * k, tw, 24F * k, 4F * k, NanoDesign.ORANGE);
            NanoStifte.textInBox(vg, NanoDesign.SANS, 11F * k, (b - tw) / 2F + 10F * k, 56F * k, 0x151515, text);

            return;
        }

        float x = (r[0] - 4F) * k, y = (r[1] - 4F) * k, w = (r[2] + 8F) * k, hh = (r[3] + 8F) * k;
        float puls = (t % 1100L) / 1100F;
        float s = 1F + 0.22F * puls;

        ring(vg, x - 3F * k, y - 3F * k, w + 6F * k, hh + 6F * k, 10F * k, 4F * k, 0x38000000 | NanoDesign.ORANGE);
        ring(vg, x, y, w, hh, 7F * k, 2F * k, 0xFF000000 | NanoDesign.ORANGE);
        ring(vg, x + w / 2F - w * s / 2F, y + hh / 2F - hh * s / 2F, w * s, hh * s, 7F * k * s, 2F * k,
            ((int) (0xD9 * (1F - puls)) << 24) | NanoDesign.ORANGE);

        /* Label 10 under the target, above it when there is no room, kept on screen */
        float lw = NanoStifte.textBreite(vg, NanoDesign.BOLD, 11F * k, this.etikett) + 16F * k;
        float lh = 20F * k;
        float lx = Math.max(4F * k, Math.min(x, b - lw - 4F * k));
        float ly = y + hh + 10F * k + lh < h ? y + hh + 10F * k : Math.max(4F * k, y - 10F * k - lh);

        NanoStifte.flaeche(vg, lx, ly, lw, lh, 4F * k, NanoDesign.ORANGE);
        NanoStifte.textInBox(vg, NanoDesign.BOLD, 11F * k, lx + 8F * k, ly + lh / 2F, 0x151515, this.etikett);
    }

    private static void ring(long vg, float x, float y, float w, float h, float radius, float strich, int argb)
    {
        try (MemoryStack st = MemoryStack.stackPush())
        {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, radius);
            nvgStrokeColor(vg, NanoStifte.farbe(argb & 0xFFFFFF, argb >>> 24, NVGColor.malloc(st)));
            nvgStrokeWidth(vg, strich);
            nvgStroke(vg);
        }
    }
}
