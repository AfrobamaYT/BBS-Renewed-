package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;

/**
 * Die eine Regel, wie gross der Auswahlpuffer des Film-Viewports sein muss.
 *
 * <p>Herausgeloest aus {@link UIFilmController} (2026-08-04), weil genau hier ein Fehler
 * lag, der in einer 1700-Zeilen-Klasse niemandem auffiel — und weil eine Groessenregel
 * eine eigene Sache ist.</p>
 *
 * <h2>⛔ Bildpunkte, nicht GUI-Einheiten</h2>
 *
 * <p>{@link BBSRendering#getVideoWidth()}/{@code getVideoHeight()} liefern BILDPUNKTE.
 * {@link StencilFormFramebuffer#resizeGUI} multipliziert zusaetzlich mit der
 * GUI-Skalierung und ist damit fuer GUI-Einheiten gedacht — hier also falsch. Gemessen
 * am laufenden Spiel (Logpoint):</p>
 *
 * <pre>
 *   resizeGUI bekommt w=1280 h=720 | guiScale=2.0 | fenster=1280x720
 *   Textur danach: 2560x1440
 * </pre>
 *
 * <p>Zweite Folge derselben Verwechslung: der Vergleich unten prueft gegen das
 * UNskalierte Mass. Mit {@code resizeGUI} war {@code 2560 != 1280} immer wahr, der Puffer
 * wurde also in JEDEM Bild neu angelegt (Logpoint feuerte 4x in unter einer Sekunde).</p>
 *
 * <p><b>Warum 1.21.1 dieselbe Zeile ohne Fehler fuhr:</b> dort lieferte
 * {@code BBSModClient.getGUIScale()} die BBS-eigene UI-Skalierung
 * ({@code BBSSettings.getUIScaleFactor()}), im Port liefert dieselbe Methode
 * {@code Window.getGuiScale()}. Die Bedeutung der Methode hat sich geaendert, nicht diese
 * Zeile — deshalb faellt es nur an Aufrufstellen auf, die schon Bildpunkte in der Hand
 * haben. Vergleich und Messwerte: {@code docs/26-2-VIEWPORT-BEFUNDE.md}.</p>
 *
 * <p>⚠️ Ein richtig grosser Puffer heisst NICHT, dass das Picken funktioniert. Nach dem
 * Fix ist {@code hasPicked} an jeder gemessenen Mausposition weiter {@code false}; die
 * offenen Spuren stehen im selben Dokument.</p>
 */
public final class StencilPuffergroesse
{
    private StencilPuffergroesse()
    {}

    /**
     * Legt den Puffer an, falls noetig, und bringt ihn auf die Video-Aufloesung.
     *
     * @param stencil der Auswahlpuffer des Film-Viewports
     */
    public static void sicherstellen(StencilFormFramebuffer stencil)
    {
        stencil.setup(Link.bbs("stencil_film"));

        Texture mainTexture = stencil.getFramebuffer().getMainTexture();
        int w = BBSRendering.getVideoWidth();
        int h = BBSRendering.getVideoHeight();

        if (mainTexture.width != w || mainTexture.height != h)
        {
            /* resize, NICHT resizeGUI — Begruendung im Klassen-Javadoc. */
            stencil.resize(w, h);
        }
    }
}
