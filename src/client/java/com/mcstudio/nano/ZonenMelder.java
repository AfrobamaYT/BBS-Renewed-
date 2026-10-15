package com.mcstudio.nano;

import java.util.ArrayList;
import java.util.List;

/**
 * Klickflaechen aus Zeichencode, der keine {@link NanoFlaeche} ist.
 *
 * <p>Doppelt gepuffert wie {@link NanoZonen}: gemeldet wird waehrend des Zeichnens in
 * {@code imBau}, uebernommen erst am Ende. Ein abgebrochener Zeichenlauf hinterlaesst
 * sonst eine halbe Liste, und eine halbe Liste sieht aus wie ein Befund.</p>
 *
 * <p>Render-Thread only — dieselbe Bedingung wie beim Nano-Weg.</p>
 */
public final class ZonenMelder
{
    /** Eine Klickflaeche in GUI-Koordinaten. */
    public record Zone(String id, float x, float y, float w, float h) {}

    private final List<Zone> aktiv = new ArrayList<>();
    private final List<Zone> imBau = new ArrayList<>();

    /** Am Anfang des Zeichenlaufs rufen. */
    public void beginnen()
    {
        this.imBau.clear();
    }

    /** Waehrend des Zeichnens: eine Flaeche melden. Leere Masse werden verworfen. */
    public void melden(String id, float x, float y, float w, float h)
    {
        if (w > 0F && h > 0F)
        {
            this.imBau.add(new Zone(id, x, y, w, h));
        }
    }

    /** Am Ende des Zeichenlaufs rufen — erst danach ist die Liste abfragbar. */
    public void uebernehmen()
    {
        this.aktiv.clear();
        this.aktiv.addAll(this.imBau);
    }

    /** Die Flaechen des letzten FERTIGEN Zeichenlaufs. */
    public List<Zone> alle()
    {
        return this.aktiv;
    }
}
