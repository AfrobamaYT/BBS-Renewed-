// Coded by Afrobama/Claude — kernel-logik für UIOverlayMinSizeMixin.
package com.afrobama.blendertransform;

import org.joml.Vector2i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * BBS-CML merkt sich pro {@code UIOverlayPanel}-unterklasse die zuletzt genutzte größe
 * (statische {@code sizes}-map in {@code UIOverlay}). Wurde ein picker (model/biome/mob/
 * particle/structure — teilen sich alle die klasse {@code UIListOverlayPanel}) einmal auf eine
 * winzige größe gezogen, klebt diese größe an jedem zukünftigen picker derselben klasse — das
 * popup öffnet dann kollabiert auf einen nutzlosen streifen.
 *
 * <p>Diese logik invalidiert den gecachten eintrag bei jedem {@code setupPanel}-aufruf, wenn er
 * unter ein sinnvolles minimum gefallen ist. BBS fällt dann auf die größe zurück, die der
 * aufrufer explizit angefordert hat (z.B. der model-picker mit 280px × 0.5F-verhältnis).</p>
 *
 * <p>Der positions-cache ({@code offsets}) wird bewusst NICHT angefasst — hat der nutzer das
 * panel an eine bestimmte stelle gezogen, bleibt das erhalten. Nur die größe wird bereinigt.</p>
 */
public final class UiOverlayMinSizeLogik
{
    private UiOverlayMinSizeLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/overlay-size");

    private static final int MIN_W = 160;
    private static final int MIN_H = 80;

    public static void invalidateStaleSize(Object panel, Map<String, Vector2i> sizes)
    {
        try
        {
            String key = panel.getClass().getSimpleName();
            Vector2i cached = sizes.get(key);

            if (cached != null && (cached.x < MIN_W || cached.y < MIN_H))
            {
                sizes.remove(key);
                LOG.info("Invalidated stale overlay size cache for {} (was {}x{})",
                    key, cached.x, cached.y);
            }
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to sanitise overlay size cache", t);
        }
    }
}
