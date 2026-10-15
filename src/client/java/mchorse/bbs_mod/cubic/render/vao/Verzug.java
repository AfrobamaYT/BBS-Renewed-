package mchorse.bbs_mod.cubic.render.vao;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Shape-Key-Verzug eines Netzes, gerechnet nur ueber die Ecken, die ein Key wirklich bewegt.
 *
 * <h2>Warum</h2>
 *
 * <p>Der alte Weg ({@code BOBJModelVAO.ausgangslagen}) kopierte bei jeder neuen Pose die ganze
 * Ruhelage und lief dann je wirkendem Key ueber ALLE Werte: {@code v[i] += w * (ziel[i] - basis[i])}.
 * Danach legte {@code schreiben} je Upload einen nativen Zwischenspeicher in voller Groesse an.
 * Gemessen am 27.09.2026 (rigtest, laufender Film, REDACTED_Advanced): Limbs hat 1 193 841 Werte,
 * jeder Key bewegt davon 0,9-5,7 %; die korrigierenden Keys haengen an den Knochen und aendern
 * sich in jedem Bild (181 Signaturen, 181 verschiedene Gewichtssaetze). Der Verzug lief darum
 * dreimal je Bild ueber das ganze Netz.</p>
 *
 * <p>Der etablierte Weg (duenn besetzte Morph-Deltas wie in Unreals Morph Targets): je Key nur
 * (Index, Differenz) der betroffenen Werte speichern. Beim naechsten Aufruf werden nur die zuletzt
 * beruehrten Werte auf die Ruhelage zurueckgesetzt und dann die Deltas addiert.</p>
 *
 * <h2>Bitgleich zum alten Weg</h2>
 *
 * <p>Je Wert laufen dieselben Additionen in derselben Reihenfolge (Iterationsfolge der
 * Gewichts-Map, {@code wert * (ziel - basis)} mit derselben einmal gerechneten Differenz). Ein
 * Key, der einen Wert nicht bewegt, addierte dort frueher {@code wert * 0} -- das aendert den Wert
 * nicht, einzig eine Ruhelage von {@code -0.0} wurde dadurch zu {@code +0.0}. Das Ergebnis liegt
 * in einem dauerhaften nativen Puffer, der direkt hochgeladen wird.</p>
 */
final class Verzug implements AutoCloseable
{
    private final float[] basis;
    private final Map<String, Bahn> bahnen = new HashMap<>();
    private final boolean[] markiert;
    private final int[] beruehrt;
    private int beruehrtZahl;
    private ByteBuffer roh;
    private final FloatBuffer werte;

    Verzug(float[] basis, Map<String, float[]> ziele)
    {
        this.basis = basis;
        this.markiert = new boolean[basis.length];

        int summe = 0;

        for (Map.Entry<String, float[]> e : ziele.entrySet())
        {
            Bahn bahn = Bahn.aus(basis, e.getValue());

            this.bahnen.put(e.getKey(), bahn);
            summe += bahn.index.length;
        }

        this.beruehrt = new int[Math.min(summe, basis.length)];
        this.roh = MemoryUtil.memAlloc(basis.length * Float.BYTES).order(ByteOrder.nativeOrder());
        this.werte = this.roh.asFloatBuffer();
        this.werte.put(0, basis);
    }

    /**
     * Rechnet den Verzug fuer diese Gewichte in {@link #daten()}.
     *
     * @return ob ein Key wirkt -- wie frueher {@code ausgangslagen() != posData}
     */
    boolean anwenden(Map<String, Float> gewichte)
    {
        for (int k = 0; k < this.beruehrtZahl; k++)
        {
            int i = this.beruehrt[k];

            this.werte.put(i, this.basis[i]);
            this.markiert[i] = false;
        }

        this.beruehrtZahl = 0;

        boolean wirkt = false;

        for (Map.Entry<String, Float> e : gewichte.entrySet())
        {
            Bahn bahn = this.bahnen.get(e.getKey());
            float wert = e.getValue() == null ? 0F : e.getValue();

            if (bahn == null || wert == 0F)
            {
                continue;
            }

            wirkt = true;

            int[] index = bahn.index;
            float[] differenz = bahn.differenz;

            for (int j = 0; j < index.length; j++)
            {
                int i = index[j];

                if (!this.markiert[i])
                {
                    this.markiert[i] = true;
                    this.beruehrt[this.beruehrtZahl++] = i;
                }

                this.werte.put(i, this.werte.get(i) + wert * differenz[j]);
            }
        }

        return wirkt;
    }

    /** Der verzogene Stand als native Bytes, Position 0 -- direkt fuer {@code writeToBuffer}. */
    ByteBuffer daten()
    {
        return this.roh;
    }

    @Override
    public void close()
    {
        if (this.roh != null)
        {
            MemoryUtil.memFree(this.roh);
            this.roh = null;
        }
    }

    /** Die Werte, die ein Key bewegt, und seine Differenz zur Ruhelage. */
    private record Bahn(int[] index, float[] differenz)
    {
        static Bahn aus(float[] basis, float[] ziel)
        {
            int n = 0;

            for (int i = 0; i < basis.length; i++)
            {
                if (ziel[i] - basis[i] != 0F)
                {
                    n++;
                }
            }

            int[] index = new int[n];
            float[] differenz = new float[n];
            int j = 0;

            for (int i = 0; i < basis.length; i++)
            {
                float d = ziel[i] - basis[i];

                if (d != 0F)
                {
                    index[j] = i;
                    differenz[j] = d;
                    j++;
                }
            }

            return new Bahn(index, differenz);
        }
    }
}
