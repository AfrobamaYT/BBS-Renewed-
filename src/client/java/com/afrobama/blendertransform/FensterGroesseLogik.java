package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;

/**
 * Logik hinter {@code WindowMixin} — waehrend einer Aufnahme luegt das Fenster ueber seine
 * eigene Groesse, damit alles in der VIDEO-Aufloesung rechnet statt in der des Bildschirms.
 *
 * <p>Sechs Getter, eine Bedingung: {@code canReplaceFramebuffer()}. Ist sie falsch, gilt
 * ueberall Vanillas Wert — jede Methode gibt dann den uebergebenen Originalwert zurueck.</p>
 *
 * <p>⛔ DIE DREI PAARE DUERFEN NICHT AUSEINANDERLAUFEN. Bildschirm-, Puffer- und
 * Oberflaechengroesse werden von verschiedenen Stellen im Spiel gelesen; kaeme eine davon aus
 * der Aufnahme und eine vom echten Fenster, waere das Bild verschoben oder beschnitten,
 * OHNE dass irgendwo ein Fehler auftaucht.</p>
 */
public final class FensterGroesseLogik
{
    private FensterGroesseLogik() {}

    /**
     * {@code setGuiScale} @HEAD — die eingestellte Oberflaechen-Vergroesserung erzwingen.
     *
     * <p>⚠️ DIE OBERGRENZE IST PFLICHT. Vanilla verlangt mindestens 320×240 Bildpunkte
     * Oberflaeche; ein zu grosser Faktor bei kleinem Puffer laesst Menues aus dem Bild
     * laufen. {@code max(1, …)} faengt dazu den Fall ab, dass der Puffer noch 0 ist.</p>
     *
     * @param vorgabe was Vanilla berechnet hat
     * @return der zu benutzende Faktor
     */
    public static int skalierung(int vorgabe, int pufferBreite, int pufferHoehe)
    {
        float eigene = BBSModClient.getCustomGUIScale();

        if (eigene <= 0F)
        {
            return vorgabe;
        }

        int hoechster = Math.max(1, Math.min(pufferBreite / 320, pufferHoehe / 240));

        /* Aufgerundet, nicht gerundet: den Rest bis zum Zwischenwert verkleinert die BBS-
         * Oberflaeche selbst (UISkala). Math.round machte aus 1,5 stumm eine 2. */
        return mchorse.bbs_mod.ui.framework.UISkala.ganzzahl(eigene, hoechster);
    }

    /** {@code getScreenWidth} @RETURN. */
    public static int bildschirmBreite(int original)
    {
        return BBSRendering.canReplaceFramebuffer() ? BBSRendering.getVideoWidth() : original;
    }

    /** {@code getScreenHeight} @RETURN. */
    public static int bildschirmHoehe(int original)
    {
        return BBSRendering.canReplaceFramebuffer() ? BBSRendering.getVideoHeight() : original;
    }

    /**
     * {@code getWidth} @RETURN — die Puffergroesse.
     *
     * <p>⚠️ MIT dem urspruenglichen Puffer-Faktor multipliziert, nicht ohne: auf einem
     * hochaufloesenden Bildschirm ist der Puffer groesser als das Fenster, und wer den Faktor
     * hier weglaesst, rendert die Aufnahme in Fenstergroesse und skaliert sie hoch.</p>
     */
    public static int pufferBreite(int original)
    {
        return BBSRendering.canReplaceFramebuffer()
            ? (int) (BBSRendering.getVideoWidth() * BBSModClient.getOriginalFramebufferScale())
            : original;
    }

    /** {@code getHeight} @RETURN — Gegenstueck zu {@link #pufferBreite}. */
    public static int pufferHoehe(int original)
    {
        return BBSRendering.canReplaceFramebuffer()
            ? (int) (BBSRendering.getVideoHeight() * BBSModClient.getOriginalFramebufferScale())
            : original;
    }

    /**
     * {@code getGuiScaledWidth} @RETURN.
     *
     * @param oberflaechenFaktor {@code guiScale}, ein {@code @Shadow}-Feld des Fensters
     */
    public static int skalierteBreite(int original, int oberflaechenFaktor)
    {
        return BBSRendering.canReplaceFramebuffer()
            ? (int) (BBSRendering.getVideoWidth() / oberflaechenFaktor
                * BBSModClient.getOriginalFramebufferScale())
            : original;
    }

    /** {@code getGuiScaledHeight} @HEAD. */
    public static int skalierteHoehe(int original, int oberflaechenFaktor)
    {
        return BBSRendering.canReplaceFramebuffer()
            ? (int) (BBSRendering.getVideoHeight() / oberflaechenFaktor
                * BBSModClient.getOriginalFramebufferScale())
            : original;
    }
}
