package mchorse.bbs_mod.ui.framework.elements.utils.vorschau;

import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;

/**
 * Eine Grenze in der Zeichenreihenfolge: alles Wartende geht hier raus.
 *
 * <p>Zwischen zwei Geschwister gehaengt, liegt alles davor Eingereihte UNTER dem, was danach
 * gezeichnet wird. Ohne sie gehen die Miniaturen erst am Ende des Durchlaufs raus und heben sich
 * dabei mit {@code nextStratum()} ueber das ganze GUI — also auch ueber Leisten, die lange vorher
 * gezeichnet wurden.</p>
 *
 * <p>Gemessen im A/B gegen 1.21.1 (2026-08-11), Suchleiste der Formliste: dort deckt die Leiste
 * die Vorschauen ab, im Port deckten die Vorschauen die Leiste zu. Leisteninneres
 * 620x30+70+382: <b>3387 von 18600 Punkten verschieden, danach 0</b>.</p>
 *
 * <p>⚠️ Wer sie setzt, ist nicht diese Klasse: nur ein Element, das andere VERDECKT, weiss, dass
 * es eine Grenze ist. Dieselbe Ueberlegung steht in {@code UIFormPalette#render}, wo dieselbe
 * Abgabe von Hand steht — dort, weil die Palette das Panel darunter verdeckt.</p>
 *
 * <p>Eine leere Schlange ist ein No-op, die Grenze kostet also nichts, wo nichts wartet.</p>
 */
public class MiniaturGrenze extends UIRenderable
{
    public MiniaturGrenze()
    {
        super(context -> Miniaturschlange.abgeben(context, context.menu.width, context.menu.height));
    }
}
