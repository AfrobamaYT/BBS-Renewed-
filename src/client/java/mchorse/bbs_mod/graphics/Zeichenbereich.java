package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderPass;

/**
 * Auf welchen Ausschnitt des Bildes BBS gerade zeichnet.
 *
 * <h2>Das Problem</h2>
 *
 * <p>BBS zeichnet seine 3D-Vorschauen nicht auf den ganzen Bildschirm, sondern in das
 * Rechteck eines Oberflaechen-Elements -- die Modellvorschau, der Formeditor, das
 * Gizmo-Fenster. Bis 1.21.1 stellte {@code RenderSystem.viewport(x, y, w, h)} das global
 * ein, alles Folgende landete darin, und danach wurde auf Vollbild zurueckgestellt.
 *
 * <p>In 26.2 gibt es diesen globalen Ausschnitt nicht mehr. Er gehoert zum
 * {@link mchorse.bbs_mod.compat.alt.RenderArea} und damit zu jedem einzelnen Durchgang.
 *
 * <h2>⚠️ Dieselbe Bruecke wie {@link PickZiel}, aus demselben Grund</h2>
 *
 * <p>Der Ausschnitt wird weit oben gesetzt (in den UI-Elementen) und weit unten gebraucht
 * (in jedem einzelnen Zeichenaufruf). Ihn als Parameter durchzureichen hiesse, ein Dutzend
 * Signaturen anzufassen, die sonst nichts mit Oberflaeche zu tun haben.
 *
 * <p>Unterschied zum alten globalen Zustand: es steckt an EINER Stelle und es heisst wie
 * das, was es tut.
 *
 * <p>⚠️ HIER STAND, {@link #freigeben()} laufe "im finally-Zweig der Aufrufer" -- das war
 * nie wahr. Am 2026-08-01 gab es im ganzen Baum genau zwei Freigaben (Gizmo:684 und :1605)
 * gegen drei Anmeldungen; der dritte Aufrufer (UIModelRenderer) gab nie frei, und sein
 * Ausschnitt blieb ueber das Bild hinaus stehen. Ein vergessenes Zuruecksetzen schneidet
 * hier sehr wohl alles Folgende ab -- nur trifft es statt aller Bilder eben alle
 * Netz-Zeichenauftraege (einziger Verbraucher: GehaltenesNetz).
 *
 * <p>Wer {@link #setzen} ruft, gibt selbst wieder frei. Das ist eine Pflicht des Aufrufers,
 * keine Eigenschaft dieser Klasse.
 *
 * <p>{@code null} bedeutet: ganzes Ziel, kein Ausschnitt. Das ist der Normalfall.
 */
public final class Zeichenbereich
{
    private static mchorse.bbs_mod.compat.alt.RenderArea aktiv;

    public static void setzen(int x, int y, int breite, int hoehe)
    {
        aktiv = new mchorse.bbs_mod.compat.alt.RenderArea(x, y, breite, hoehe);
    }

    public static void freigeben()
    {
        aktiv = null;
    }

    /** Der aktive Ausschnitt, oder {@code null} fuer das ganze Ziel. */
    public static mchorse.bbs_mod.compat.alt.RenderArea aktiv()
    {
        return aktiv;
    }

    /**
     * Schneidet einen Ausschnitt auf ein Ziel dieser Groesse zurecht.
     *
     * <p>⛔ WOFUER (2026-08-04, Bugliste #9 / Absturzgruppe G16). Ein Ausschnitt, der ueber
     * die Zieltextur hinausragt, ist in 26.2 kein Schoenheitsfehler, sondern ein Abbruch:</p>
     *
     * <pre>
     *   RenderArea out of bounds for texture of 854x480
     *       at ...GehaltenesNetz.zeichnen(GehaltenesNetz.java:141)
     * </pre>
     *
     * <p>Wie die Paarung entsteht — am 2026-08-04 an der laufenden Instanz gemessen:</p>
     *
     * <pre>
     *   fenster = 854x480     video = 1280x720
     * </pre>
     *
     * <p>Gesetzt wird der Ausschnitt an genau einer Stelle ({@code UIUtils#viewportArea}),
     * und zwar gegen {@code getWindow().getHeight()}. Beim Video-Export sind diese Getter
     * ueberschrieben und liefern die VIDEO-Groesse. Wird derselbe Ausschnitt danach gegen
     * die echte Fenstertextur benutzt, ist er zu gross — 1280x720 auf 854x480.</p>
     *
     * <p>⚠️ Das Zurechtschneiden repariert den ABSTURZ, nicht die Ursache. Damit die
     * Fehlpaarung nicht still verschwindet, wird jede neue Groessen-Kombination einmal
     * gemeldet — ein stiller Fehlschlag waere hier der teurere Bug
     * ({@code docs/FEHLER-DIE-KEIN-BILD-ZEIGT.md}).</p>
     *
     * @return der passende Ausschnitt, oder {@code null} fuer das ganze Ziel
     */
    public static mchorse.bbs_mod.compat.alt.RenderArea begrenztAuf(mchorse.bbs_mod.compat.alt.RenderArea bereich,
        int zielBreite, int zielHoehe)
    {
        if (bereich == null || zielBreite <= 0 || zielHoehe <= 0)
        {
            return null;
        }

        int x = Math.max(0, Math.min(bereich.x(), zielBreite));
        int y = Math.max(0, Math.min(bereich.y(), zielHoehe));
        int breite = Math.max(0, Math.min(bereich.width(), zielBreite - x));
        int hoehe = Math.max(0, Math.min(bereich.height(), zielHoehe - y));

        if (breite == bereich.width() && hoehe == bereich.height()
            && x == bereich.x() && y == bereich.y())
        {
            return bereich;
        }

        melden(bereich, zielBreite, zielHoehe);

        /* Bleibt nichts uebrig, liegt der Ausschnitt ganz ausserhalb. Dann ist das ganze
         * Ziel die alte Bedeutung von null und das kleinere Uebel gegenueber einem Abbruch. */
        return breite <= 0 || hoehe <= 0
            ? null
            : new mchorse.bbs_mod.compat.alt.RenderArea(x, y, breite, hoehe);
    }

    /**
     * Meldet eine Fehlpaarung, aber nicht dieselbe zweimal hintereinander — sonst schreibt
     * sie sich pro Bild ins Log und man gewoehnt sich das Wegsehen an.
     *
     * <p>Bewusst ein einzelner String und keine Menge: ein neues statisches Feld ist nach
     * einem Hotswap {@code null}. Ein Vergleich haelt das aus, ein {@code Set.add} nicht.</p>
     */
    private static void melden(mchorse.bbs_mod.compat.alt.RenderArea bereich, int zielBreite, int zielHoehe)
    {
        String kennung = bereich.x() + "," + bereich.y() + "," + bereich.width() + ","
            + bereich.height() + " -> " + zielBreite + "x" + zielHoehe;

        if (!kennung.equals(letzteMeldung))
        {
            letzteMeldung = kennung;

            System.out.println("[bbs] Zeichenbereich passt nicht zum Ziel: " + kennung
                + " -- zurechtgeschnitten. Siehe Zeichenbereich#begrenztAuf.");
        }
    }

    private static volatile String letzteMeldung;

    private Zeichenbereich()
    {}
}
