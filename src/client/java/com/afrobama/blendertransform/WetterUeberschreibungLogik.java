package com.afrobama.blendertransform;

import mchorse.bbs_mod.client.BBSRendering;

/**
 * Logik hinter {@code WorldMixin} (client), herausgeloest damit sie hotswapbar ist.
 *
 * <p>Der Film kann den Regenpegel der Welt uebersteuern — {@code Level.getRainLevel} liefert
 * dann den Wert aus dem Wetter-Clip statt den der echten Welt.</p>
 *
 * <p>⚠️ {@code null} HEISST "NICHT UEBERSTEUERN", nicht "kein Regen". Der Handler darf den
 * Rueckgabewert nur setzen, wenn hier etwas herauskommt — sonst waere jede Welt ohne
 * Wetter-Clip dauerhaft trocken.</p>
 */
public final class WetterUeberschreibungLogik
{
    private WetterUeberschreibungLogik() {}

    /**
     * @return der uebersteuerte Regenpegel, oder {@code null}, wenn die Welt ihren eigenen
     *         behalten soll
     */
    public static Float regenpegel()
    {
        Double faktor = BBSRendering.getWeather();

        return faktor == null ? null : Float.valueOf(faktor.floatValue());
    }
}
