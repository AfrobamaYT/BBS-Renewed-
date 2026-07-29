package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

/**
 * Logik der beiden farb-mixins der oberflaeche (icon-akzent, scrollbalken) — im kernel-paket,
 * damit sie hotswapbar ist.
 *
 * <p>Herausgeloest aus {@code UIIconColorMixin} und {@code ScrollBarSkinMixin} (2026-07-29,
 * bruecken-nachruestung). Vorher lag die farbwahl im gewobenen handler: jede aenderung an
 * einem farbton kostete einen boot, obwohl sie nichts weiter ist als eine zahl.</p>
 *
 * <p>⚠ KEINE KERNEL-TYPEN IN DER SIGNATUR. Der aufrufer ist der gewobene handler im stabilen
 * loader — ein BBS-typ waere fuer ihn eine andere klasse als hier. Deshalb reicht der handler
 * das UIIcon NICHT herein, sondern holt sich nur die farbe und setzt seine felder selbst.</p>
 */
public final class OberflaechenFarbenLogik
{
    private OberflaechenFarbenLogik()
    {
    }

    /** Akzentfarbe fuer hover und aktiv-zustand eines icons. */
    public static int iconAkzent()
    {
        return BBSToolsTheme.ACCENT_PRIMARY;
    }

    /**
     * Farbe des scrollbalken-griffs.
     *
     * <p>Die alpha-stufe der urspruenglichen farbe bleibt erhalten, sonst verliert ein
     * halbtransparenter balken seine transparenz; kodiert BBS keine (alpha 0), wird voll
     * deckend gezeichnet — 0 hiesse sonst unsichtbar.</p>
     */
    public static int scrollbalken(int original)
    {
        int alpha = (original >>> 24) & 0xFF;

        if (alpha == 0)
        {
            alpha = 0xFF;
        }

        return (alpha << 24) | (BBSToolsTheme.ACCENT_PRIMARY & 0x00FFFFFF);
    }
}
