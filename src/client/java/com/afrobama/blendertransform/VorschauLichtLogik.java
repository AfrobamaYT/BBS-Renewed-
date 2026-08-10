package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.util.PreviewPassState;
import net.minecraft.util.LightCoordsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik hinter {@code PreviewLightOverrideMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Im Kameravorschau-Durchgang wird jede Lichtabfrage auf volle Helligkeit gezwungen. Die
 * Vorschau zeichnet ausserhalb des normalen Weltdurchgangs, wo die Lichtkoordinaten der Szene
 * nicht gelten — ohne die Ueberschreibung waere das Vorschaubild schwarz.</p>
 *
 * <p>Das Merkfeld {@code gemeldet} ist mit umgezogen: die Meldung soll EINMAL kommen, nicht bei
 * jeder Lichtabfrage. Es steht bewusst hier und nicht im gewobenen Handler — dort waere es ein
 * neues statisches Feld in einer fremden Zielklasse.</p>
 */
public final class VorschauLichtLogik
{
    private VorschauLichtLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/cam-preview/light-override");

    private static boolean gemeldet;

    /**
     * @return die erzwungene Lichtkoordinate, oder {@code null}, wenn kein Vorschau-Durchgang
     *         laeuft und Vanillas Wert stehen bleiben soll
     */
    public static Integer volleHelligkeit()
    {
        if (!PreviewPassState.PREVIEW_PASS_ACTIVE)
        {
            return null;
        }

        if (!gemeldet)
        {
            gemeldet = true;
            LOG.info("Light override ENGAGED — preview pass returning MAX_LIGHT_COORDINATE");
        }

        return Integer.valueOf(LightCoordsUtil.FULL_BRIGHT);
    }
}
