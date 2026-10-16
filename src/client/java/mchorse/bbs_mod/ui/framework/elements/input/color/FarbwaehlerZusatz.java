package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.GpuReadback;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * What the color picker gained in BBS FS 7aa8cf2a4 (Wemppy4/bbs-fs, MIT), kept beside it because the
 * picker may not grow: HSV/RGB tabs in the popup (the setting is the last one picked), the channels
 * as numbers (RGB to 255, hue in degrees, the rest in percent), the preview as "before | now" where a
 * click on the left half goes back, and an eyedropper that takes a color off the screen.
 *
 * <p>The eyedropper differs from FS: 26.3 draws the whole interface after it was collected, so
 * nothing can be read "under" the popup mid-frame. While armed the popup draws only a small swatch
 * at the cursor, and the pixel is read back from the last frame, which then shows what is beneath.</p>
 */
public class FarbwaehlerZusatz
{
    /** Height of the tab row above the sliders, and of the number row below them */
    public static final int KOPF = 16;
    public static final int FELDER = 24;

    public final UIElement reiter = new UIElement();
    public final UIColorFields felder;
    public final UIIcon pipette;

    private final UIColorPicker picker;
    private boolean picking;
    private GpuReadback.Request anfrage;
    private int gesehen = Colors.A100;

    public FarbwaehlerZusatz(UIColorPicker picker)
    {
        this.picker = picker;
        this.felder = new UIColorFields(this::kanal)
        {
            @Override
            public void render(UIContext context)
            {
                FarbwaehlerZusatz.this.felderAuffrischen();
                super.render(context);
            }
        };
        this.pipette = new UIIcon(Icons.EYEDROPPER, (b) -> this.picking = true);
        this.pipette.iconColor(Colors.A100 | 0x333333).tooltip(L10n.lang("bbs.ui.color.eyedropper"));
        this.reiter.add(new Reiter(IKey.constant("HSV"), true), new Reiter(IKey.constant("RGB"), false));
    }

    /** Tabs above the sliders, numbers below them, the dropper beside the hex field */
    public void anordnen(int x, int y, int breite, int felderY)
    {
        this.reiter.set(x, y, breite, KOPF - 2);
        this.reiter.getChildren(Reiter.class).forEach((r) -> r.set(r.hsv ? x : x + breite / 2, y, breite / 2, KOPF - 2));
        this.felder.set(x, felderY, breite, 20);
        this.felder.mode(BBSSettings.hsvColorPicker.get(), this.picker.editAlpha);
        this.felder.resize();
        this.pipette.set(this.picker.input.area.ex() + 2, this.picker.input.area.y, 18, 20);
        this.reiter.resize();
        this.pipette.resize();
    }

    private void felderAuffrischen()
    {
        Color c = BBSSettings.hsvColorPicker.get() ? this.picker.hsv : this.picker.color;

        this.felder.mode(BBSSettings.hsvColorPicker.get(), this.picker.editAlpha);
        this.felder.update(c.r, c.g, c.b, this.picker.color.a);
    }

    /** A number was typed or dragged: in HSV the hue of a black color survives, since HSV is kept */
    private void kanal(int kanal, float wert)
    {
        boolean hsv = BBSSettings.hsvColorPicker.get();
        Color c = hsv ? this.picker.hsv : this.picker.color;

        if (kanal == 3) { this.picker.color.a = wert; this.picker.hsv.a = wert; }
        else if (kanal == 0) c.r = wert;
        else if (kanal == 1) c.g = wert;
        else c.b = wert;

        if (hsv) this.picker.syncColorFromHsv();
        else this.picker.syncHsvFromColor();

        this.picker.notifyColorChanged();
    }

    /** "Before | now"; the left half is the color the popup opened with */
    public void vorschau(Batcher2D batcher, Area preview, Color initial)
    {
        int mitte = preview.x + preview.w / 2;

        batcher.box(preview.x, preview.y, mitte, preview.ey(), initial.getARGBColor() | (this.picker.editAlpha ? 0 : Colors.A100));
        this.picker.renderRect(batcher, mitte, preview.y, preview.ex(), preview.ey());
    }

    /** Clicks the picker hands over first: a dropper pick, or the left half of the preview */
    public boolean klick(UIContext context, Area preview, Color initial)
    {
        if (this.picking)
        {
            this.picking = false;
            this.picker.setValue(this.gesehen, false);
            this.picker.notifyColorChanged();

            return true;
        }

        if (context.mouseButton == 0 && preview.isInside(context) && context.mouseX < preview.mx())
        {
            this.picker.color.copy(initial);
            this.picker.syncHsvFromColor();
            this.picker.notifyColorChanged();

            return true;
        }

        return false;
    }

    /** Escape puts the dropper away first; the picker stays open */
    public boolean abbrechen()
    {
        boolean war = this.picking;

        this.picking = false;

        return war;
    }

    /** While armed: only a swatch at the cursor, and the next pixel read; true when the popup must not draw */
    public boolean pipetteZeichnen(UIContext context)
    {
        if (!this.picking)
        {
            return false;
        }

        this.lesen(context);
        context.batcher.box(context.mouseX + 8, context.mouseY + 8, context.mouseX + 26, context.mouseY + 26, Colors.A100);
        context.batcher.box(context.mouseX + 9, context.mouseY + 9, context.mouseX + 25, context.mouseY + 25, this.gesehen | Colors.A100);

        return true;
    }

    /** The pixel under the cursor out of the last frame, which had no popup in it */
    private void lesen(UIContext context)
    {
        if (this.anfrage != null)
        {
            GpuReadback.Raw roh = this.anfrage.poll();

            if (roh == null)
            {
                return;
            }

            ByteBuffer rgba = roh.rgba8(GpuReadback.Origin.NATIVE);

            this.gesehen = Colors.A100 | (rgba.get(0) & 0xff) << 16 | (rgba.get(1) & 0xff) << 8 | (rgba.get(2) & 0xff);
            MemoryUtil.memFree(rgba);
            roh.close();
            this.anfrage = null;
        }

        var ziel = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        float skala = ziel.width / (float) context.menu.width;
        int x = Math.max(0, Math.min(ziel.width - 1, Math.round(context.globalX(context.mouseX) * skala)));
        int y = Math.max(0, Math.min(ziel.height - 1, ziel.height - 1 - Math.round(context.globalY(context.mouseY) * skala)));

        this.anfrage = GpuReadback.requestRaw(ziel.getColorTexture(), new GpuReadback.Region(x, y, 1, 1));
    }

    /** One of the two tabs: the word, lit when it is the mode in use */
    private class Reiter extends UIElement
    {
        private final IKey label;
        private final boolean hsv;

        private Reiter(IKey label, boolean hsv)
        {
            this.label = label;
            this.hsv = hsv;
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0 && BBSSettings.hsvColorPicker.get() != this.hsv)
            {
                BBSSettings.hsvColorPicker.set(this.hsv);
                FarbwaehlerZusatz.this.picker.resize();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean aktiv = BBSSettings.hsvColorPicker.get() == this.hsv;
            String text = this.label.get();

            if (aktiv) context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.A100 | BBSSettings.primaryColor.get());

            context.batcher.text(text, this.area.mx(context.batcher.getFont().getWidth(text)), this.area.my(context.batcher.getFont().getHeight() - 1),
                aktiv || this.area.isInside(context) ? Colors.A100 : Colors.GRAY);
            super.render(context);
        }
    }
}
