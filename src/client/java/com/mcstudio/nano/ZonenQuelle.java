package com.mcstudio.nano;

import java.util.List;

/**
 * Etwas, das Klickflaechen meldet, ohne eine {@link NanoFlaeche} zu sein.
 *
 * <p>⛔ WOFUER (gemessen 2026-08-23). {@code /ui/zonen} sammelt ausschliesslich
 * {@code NanoFlaeche}n. Der Replay-Zweig zeichnet ueber den alten Batcher und meldet
 * deshalb KEINE Zone — ein Zonenlauf sah dort nichts und meldete trotzdem „alle gruen".
 * Genau diese Sorte Auskunft beendet die Suche.</p>
 *
 * <p>Der Elementbaum ({@code /ui/elemente}) hilft nur zum Teil: er kennt Kindelemente,
 * aber nicht die Ziele, die INNERHALB einer Leinwand gezeichnet werden — Kanalzeilen,
 * einzelne Keyframes, die Beschriftungsspalte. Genau die fehlen der Abnahme.</p>
 *
 * <p>Masse in GUI-Koordinaten, nicht in Texturpixeln: der alte Batcher rechnet ohnehin
 * darin, und {@code /ui/klick} nimmt sie so entgegen.</p>
 */
public interface ZonenQuelle
{
    /** Die gemeldeten Flaechen des letzten fertigen Zeichenlaufs. */
    List<ZonenMelder.Zone> gemeldeteZonen();
}
