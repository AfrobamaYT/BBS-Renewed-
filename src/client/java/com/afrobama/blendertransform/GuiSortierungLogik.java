package com.afrobama.blendertransform;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;

import java.util.Comparator;

/**
 * Vanillas GUI-Elementsortierung ohne Boxing — Logik zu {@code GuiRendererSortierungMixin}.
 *
 * <p>{@code GuiRenderer.ELEMENT_SORT_COMPARATOR} ist eine Kette aus
 * {@code Comparator.comparing(...)} ueber int-Getter: jeder Vergleich boxt die Schluessel zu
 * {@code Integer}, und Bildschirmkoordinaten ueber 127 liegen nicht im Integer-Cache. Das war
 * laut Allokationsprofil (23.09.2026) mit 13,8 % die groesste Allokationsklasse des
 * Renderthreads im Filmeditor.</p>
 *
 * <p>Dieselben Schluessel in derselben Reihenfolge, je mit {@code Integer.compare} wie
 * {@code Integer.compareTo}: Schnitt-Rechteck ({@code nullsFirst}; top, bottom, left, right),
 * dann {@code RenderPipeline.getSortKey()}, dann {@code TextureSetup.getSortKey()}
 * ({@code nullsFirst}). Gleiche Vorzeichen ergeben bei der stabilen Sortierung dieselbe
 * Reihenfolge.</p>
 */
public final class GuiSortierungLogik
{
    private static final Comparator<GuiElementRenderState> VERGLEICH = GuiSortierungLogik::vergleiche;

    private GuiSortierungLogik()
    {}

    /** @return der boxfreie Vergleich an Stelle von {@code vanilla} */
    public static Object vergleicher(Object vanilla)
    {
        return VERGLEICH;
    }

    public static int vergleiche(GuiElementRenderState a, GuiElementRenderState b)
    {
        int ergebnis = schnitt(a.scissorArea(), b.scissorArea());

        if (ergebnis != 0)
        {
            return ergebnis;
        }

        ergebnis = Integer.compare(a.pipeline().getSortKey(), b.pipeline().getSortKey());

        if (ergebnis != 0)
        {
            return ergebnis;
        }

        return textur(a.textureSetup(), b.textureSetup());
    }

    private static int schnitt(ScreenRectangle a, ScreenRectangle b)
    {
        if (a == b)
        {
            return 0;
        }

        if (a == null)
        {
            return -1;
        }

        if (b == null)
        {
            return 1;
        }

        int ergebnis = Integer.compare(a.top(), b.top());

        if (ergebnis == 0)
        {
            ergebnis = Integer.compare(a.bottom(), b.bottom());
        }

        if (ergebnis == 0)
        {
            ergebnis = Integer.compare(a.left(), b.left());
        }

        if (ergebnis == 0)
        {
            ergebnis = Integer.compare(a.right(), b.right());
        }

        return ergebnis;
    }

    private static int textur(TextureSetup a, TextureSetup b)
    {
        if (a == b)
        {
            return 0;
        }

        if (a == null)
        {
            return -1;
        }

        if (b == null)
        {
            return 1;
        }

        return Integer.compare(a.getSortKey(), b.getSortKey());
    }
}
