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
 * {@link RenderPass.RenderArea} und damit zu jedem einzelnen Durchgang.
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
    private static RenderPass.RenderArea aktiv;

    public static void setzen(int x, int y, int breite, int hoehe)
    {
        aktiv = new RenderPass.RenderArea(x, y, breite, hoehe);
    }

    public static void freigeben()
    {
        aktiv = null;
    }

    /** Der aktive Ausschnitt, oder {@code null} fuer das ganze Ziel. */
    public static RenderPass.RenderArea aktiv()
    {
        return aktiv;
    }

    private Zeichenbereich()
    {}
}
