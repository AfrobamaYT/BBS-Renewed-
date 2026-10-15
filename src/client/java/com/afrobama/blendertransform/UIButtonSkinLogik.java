package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

/**
 * Logik des button-skin-mixins — im kernel-paket, damit sie hotswapbar ist.
 *
 * <p>Herausgelöst aus {@code UIButtonSkinMixin} (2026-07-26, brücken-umbau). Zeichnet den
 * AE/Premiere-artigen rechteck-look für JEDEN button in der gesamten BBS-CML-UI:</p>
 * <ul>
 *   <li>Scharfe 2px-ecken (statt Refreshed's pillen)</li>
 *   <li>Dezenter 1px-rand, hellt bei hover auf</li>
 *   <li>Custom-color-buttons ({@code custom == true}) behalten ihre eigene farbe als akzent</li>
 *   <li>Disabled-state wird über UIClickable.isEnabled() gehandhabt — in render() nicht
 *       renderSkin(), dieser mixin sieht das also nicht (unverändert übernommen)</li>
 * </ul>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR — {@link UIButton}/{@link UIContext} sind BBS-typen.</p>
 */
public final class UIButtonSkinLogik
{
    private UIButtonSkinLogik() {}

    public static void zeichneSkin(UIButton self, UIContext context)
    {
        int x = self.area.x;
        int y = self.area.y;
        int x2 = x + self.area.w;
        int y2 = y + self.area.h;
        boolean hover = self.area.isInside(context.mouseX, context.mouseY);

        /* Background: custom-tinted, hovered or default. */
        int bg;
        int border;
        if (self.custom && self.background)
        {
            /* ⛔ `UIButton.color(int)` maskiert das alpha WEG (`customColor = color & RGB`).
             * BBS' eigener renderSkin gleicht das mit `| A100` aus — dieser painter tat es
             * nicht, also zeichnete jeder so gefaerbte knopf eine voellig DURCHSICHTIGE
             * flaeche: die rig-layer-knoepfe standen auf 0x4772b3 und lieferten 0 blaue
             * pixel im bild (gemessen, magenta-sonde ebenfalls unsichtbar). Nur alpha==0
             * wird aufgefuellt — wer ueber color(int,int) bewusst durchsichtig faerbt,
             * behaelt seinen wert. */
            int voll = (self.customColor >>> 24) == 0 ? (self.customColor | 0xFF000000) : self.customColor;

            bg = hover ? brighten(voll, 0.18F) : voll;
            border = voll;
        }
        else if (self.background)
        {
            bg = hover ? BBSToolsTheme.BG_HOVER : BBSToolsTheme.BG_ELEVATED;
            border = hover ? BBSToolsTheme.BORDER_HOVER : BBSToolsTheme.BORDER;
        }
        else
        {
            /* background=false → label-only, no fill. */
            bg = 0;
            border = 0;
        }

        if (self.background)
        {
            context.batcher.box(x, y, x2, y2, bg);
            context.batcher.outline(x, y, x2, y2, border);
        }

        /* Label centered. */
        try
        {
            String label = self.label == null ? "" : self.label.get();

            /* ⛔ BBS' eigener renderSkin kuerzt mit limitToWidth; dieser painter tat es
             * nicht — lange beschriftungen liefen ueber den knopfrand hinaus und ueber
             * die nachbarn ("Face (Secondary)" ragte beidseitig aus einem 67px-knopf). */
            label = context.batcher.getFont().limitToWidth(label, self.area.w - 4);

            if (!label.isEmpty())
            {
                int tw = context.batcher.getFont().getWidth(label);
                int fh = context.batcher.getFont().getHeight();
                int tx = x + (self.area.w - tw) / 2;
                int ty = y + (self.area.h - fh) / 2;
                int textC = self.textColor != 0 ? self.textColor : BBSToolsTheme.TEXT;
                context.batcher.text(label, tx, ty, textC, self.textShadow);
            }
        }
        catch (Throwable ignored) {}
    }

    private static int brighten(int argb, float amt)
    {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amt));
        g = Math.min(255, Math.round(g + (255 - g) * amt));
        b = Math.min(255, Math.round(b + (255 - b) * amt));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
