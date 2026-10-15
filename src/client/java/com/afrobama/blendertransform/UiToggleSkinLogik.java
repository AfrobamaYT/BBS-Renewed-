// Coded by Afrobama/Claude — kernel-logik für UIToggleSkinMixin.
// UI Overhaul Phase 3: ersetzt BBS' toggle-skin durch unsere rechteckige variante.
package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

/**
 * Kapert {@link UIToggle#renderSkin(UIContext)} BBS-weit, damit JEDER toggle in der gesamten
 * BBS-CML-UI unseren AE/Premiere-artigen rechteckigen schalter statt BBS' stock-chrome bekommt.
 *
 * <p>Zeichnet: label links, rechteckiger track + thumb rechts. Track füllt sich mit
 * {@link BBSToolsTheme#ACCENT_PRIMARY}, wenn AN, sitzt bei {@link BBSToolsTheme#BG_FIELD},
 * wenn AUS. Hover gibt einen helleren rand. Keine abgerundeten pillen — scharfe kanten.</p>
 */
public final class UiToggleSkinLogik
{
    private UiToggleSkinLogik() {}

    public static void ourSkin(UIToggle self, UIContext context)
    {
        boolean on = self.getValue();

        int x = self.area.x;
        int y = self.area.y;
        int w = self.area.w;
        int h = self.area.h;

        /* Label (left-aligned, vertically centered). */
        try
        {
            String label = self.label == null ? "" : self.label.get();
            int fh = context.batcher.getFont().getHeight();
            int ty = y + (h - fh) / 2;
            context.batcher.text(label, x, ty, BBSToolsTheme.TEXT, false);
        }
        catch (Throwable ignored) {}

        /* Track on the right side. 22×10 like our standalone toggle. */
        int trackW = 22;
        int trackH = 10;
        int tx2 = x + w;
        int tx1 = tx2 - trackW;
        int ty1 = y + (h - trackH) / 2;
        int ty2 = ty1 + trackH;

        boolean hover = self.area.isInside(context.mouseX, context.mouseY);
        int trackBg = on ? BBSToolsTheme.ACCENT_PRIMARY : BBSToolsTheme.BG_FIELD;
        int border = hover
            ? (on ? BBSToolsTheme.ACCENT_PRIMARY : BBSToolsTheme.BORDER_HOVER)
            : BBSToolsTheme.BORDER;

        context.batcher.box(tx1, ty1, tx2, ty2, trackBg);
        context.batcher.outline(tx1, ty1, tx2, ty2, border);

        /* Thumb. */
        int thumbInset = 1;
        int thumbW = (trackW / 2) - (thumbInset * 2);
        int thumbX = on ? tx2 - thumbInset - thumbW : tx1 + thumbInset;
        int thumbColor = on ? 0xFFFFFFFF : BBSToolsTheme.TEXT_DIM;
        context.batcher.box(thumbX, ty1 + thumbInset,
            thumbX + thumbW, ty2 - thumbInset, thumbColor);
    }
}
