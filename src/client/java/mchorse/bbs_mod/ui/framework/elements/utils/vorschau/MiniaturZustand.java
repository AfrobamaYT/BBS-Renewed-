package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * Eine Formvorschau in der Oberflaeche — Bild im Bild statt Sofortzug.
 *
 * <h2>Wofuer</h2>
 *
 * <p>Die Miniaturen der Formpalette (Morph-Panel, Modell-Editor, Replay-Liste) zeichneten im
 * Port zwar — der Zaehler {@code miniatur.ModelFormRenderer} lief 366x in vier Sekunden —,
 * waren aber unsichtbar. Grund ist dieselbe Eigenheit von 26.2, die schon die grosse
 * Vorschau und das Bodengitter erwischt hat: die GUI wird in ZWEI Phasen gezeichnet. Der
 * Durchlauf SAMMELT nur, {@code GuiRenderer.render()} zeichnet danach. Ein sofort
 * geoeffneter Durchgang landet deshalb UNTER dem gesamten GUI und wird vom
 * Panel-Hintergrund uebermalt.</p>
 *
 * <p>Gemessen im A/B gegen 1.21.1, Morph-Panel, gleiche Welt und gleicher Standpunkt: die
 * alte Version zeigt elf Modellvorschauen, der Port keine einzige. Das allein macht den
 * grossen Teil der 12,55 % Bildunterschied dieses Panels aus.</p>
 *
 * <h2>Warum ein eigener Zustand und nicht {@link VorschauModellZustand}</h2>
 *
 * <p>Jener traegt Form, Kamera und Projektion und baut den Zeichenaufruf selbst. Eine
 * Miniatur baut ihre Ansicht dagegen INNERHALB von {@code renderInUI} aus dem uebergebenen
 * Rechteck ({@code getUIMatrix}), und jede Form-Art macht das anders. Dieser Zustand
 * umschliesst deshalb den Aufruf, statt ihn nachzubauen — damit gilt er fuer ALLE
 * Form-Arten, nicht nur fuer Modelle.</p>
 *
 * <p>⚠️ Der {@link UIContext} wird als Bezug gehalten. Das ist zulaessig, weil der
 * eingereihte Zustand im SELBEN Bild ausgefuehrt wird — dieselbe Annahme, unter der
 * {@link VorschauModellZustand} seine Entity und seine Kamera haelt.</p>
 */
public record MiniaturZustand(
    FormRenderer<?> zeichner,
    UIContext kontext,
    int x0,
    int y0,
    int x1,
    int y1,
    ScreenRectangle scissorArea,
    ScreenRectangle bounds
) implements PictureInPictureRenderState
{
    /** Massstab 1: die Miniatur rechnet ihre Groesse selbst aus dem Rechteck. */
    @Override
    public float scale()
    {
        return 1F;
    }
}
