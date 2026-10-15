package mchorse.bbs_mod.ui.hilfe;

import de.keksuccino.rinku.RinkuBrowser;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.function.Supplier;

/**
 * The video's flight between the Help window and the mini-player (spec section 9): for 420 ms the
 * picture moves from where it was to where it goes, drawn on top of everything, while the target's
 * own video waits hidden. The page is not resized for it — the texture is only drawn larger or
 * smaller, so the flight costs no reload.
 */
final class HilfeFlug extends UIElement
{
    private static final long DAUER = 420L;

    private final RinkuBrowser browser;
    private final float[] von;
    private final Supplier<Area> nach;
    private final long beginn = System.currentTimeMillis();

    /** Null when there is nothing to fly from. {@code nach} gives null when the target shows no video: the flight ends. */
    static HilfeFlug starten(RinkuBrowser browser, float[] von, Supplier<Area> nach)
    {
        if (von == null || BBSModClient.getDashboard() == null)
        {
            return null;
        }

        HilfeFlug flug = new HilfeFlug(browser, von, nach);

        BBSModClient.getDashboard().getRoot().add(flug);

        return flug;
    }

    /** Where an element's video is on screen, or null when it shows none. */
    static float[] rechteck(UIElement video)
    {
        return video == null || !video.isVisible() || video.area.w <= 0 ? null : new float[] {video.area.x, video.area.y, video.area.w, video.area.h};
    }

    private HilfeFlug(RinkuBrowser browser, float[] von, Supplier<Area> nach)
    {
        this.browser = browser;
        this.von = von;
        this.nach = nach;
    }

    boolean fliegt()
    {
        return this.hasParent() && System.currentTimeMillis() - this.beginn < DAUER;
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return true;
    }

    @Override
    public void render(UIContext context)
    {
        Area ziel = this.nach.get();

        if (!this.fliegt() || ziel == null || ziel.w <= 0)
        {
            this.removeFromParent();

            return;
        }

        float t = (System.currentTimeMillis() - this.beginn) / (float) DAUER;
        float e = 1F - (1F - t) * (1F - t) * (1F - t);
        int x = Math.round(this.von[0] + (ziel.x - this.von[0]) * e);
        int y = Math.round(this.von[1] + (ziel.y - this.von[1]) * e);
        int w = Math.round(this.von[2] + (ziel.w - this.von[2]) * e);
        int h = Math.round(this.von[3] + (ziel.h - this.von[3]) * e);

        if (this.browser.isTextureReady())
        {
            context.batcher.getContext().blit(RenderPipelines.GUI_TEXTURED, this.browser.getTextureIdentifier(), x, y, 0F, 0F, w, h, w, h);
        }
    }
}
