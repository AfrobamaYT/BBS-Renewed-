package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * Wohin der Auswahl-Durchgang gerade zeichnet.
 *
 * <h2>Das Problem, das diese Klasse loest</h2>
 *
 * <p>BBS zeichnet seine Gizmos und Formen ZWEIMAL: einmal sichtbar ins Bild und einmal als
 * Kennungsfarben in einen eigenen Puffer, aus dem der Klick zurueckliest, was getroffen
 * wurde. Bis 1.21.1 kostete das nichts: der Auswahl-Puffer wurde gebunden, und alles
 * Folgende landete dort. In 26.2 gibt es diese Bindung nicht mehr -- jeder Durchgang nennt
 * sein Ziel selbst.
 *
 * <p>Das Ziel muesste also durch jeden Zeichenaufruf gereicht werden. Es beginnt aber weit
 * oben (BaseFilmController, UIFilmController, UIPickableFormRenderer, UIModelBlockPanel)
 * und endet tief unten in einzelnen Griffen -- fuenf Aufrufketten mit je einem Parameter
 * mehr, quer durch Code, der sonst nichts mit Auswahl zu tun hat.
 *
 * <p>Darum diese Bruecke: {@code StencilFormFramebuffer.apply()} meldet hier an, wohin die
 * Kennungen gehen, {@code unbind()} meldet wieder ab. Genau die beiden Stellen, die vorher
 * gebunden und geloest haben.
 *
 * <h2>⚠️ Das ist eine BRUECKE, keine Loesung</h2>
 *
 * <p>Es ist wieder umgebender Zustand -- das, was 26.2 gerade abgeschafft hat. Der
 * Unterschied zum alten Binden: er ist auf zwei Methoden begrenzt, er heisst wie das, was
 * er tut, und er SCHEIGT NICHT. Wer Kennungen zeichnet, ohne dass ein Ziel angemeldet ist,
 * bekommt eine Ausnahme statt Kennungsfarben mitten im Bild und einer Auswahl, die ins
 * Leere greift.
 *
 * <p>Der Weg dahin, das hier loszuwerden: das Ziel als Parameter durch die fuenf Ketten
 * reichen. Wer das anfaengt, kann die Meldungen hier als Wegweiser benutzen -- sie zeigen
 * genau die Aufrufe, die es brauchen.
 */
public final class PickZiel
{
    private static GpuTextureView aktiv;
    private static GpuTextureView tiefe;

    /**
     * Meldet an, wohin die Kennungen gehen -- FARBE UND TIEFE.
     *
     * <h2>⛔ Warum die Tiefe dazugehoert und nicht weggelassen werden darf</h2>
     *
     * <p>Ein Kennungs-Durchgang ohne Tiefe zeichnet die Kennungen in der Reihenfolge, in
     * der die Teile gezeichnet werden -- der zuletzt gezeichnete gewinnt, auch wenn er
     * HINTER dem Koerper liegt. Der Klick trifft dann einen verdeckten Knochen. Genau
     * dagegen hing bis 1.21.1 ein Tiefenpuffer am Auswahl-Puffer.
     *
     * <p>Dazu kommt: alle vier Picker-Pipelines tragen einen Tiefenzustand
     * ({@code ModelVAOPipeline}). Blaze3D meldet einen Durchgang, dessen Pipeline Tiefe
     * will und keine bekommt, mit "Render pipeline {} wants a depth texture but none was
     * provided - this is probably a bug" -- je Zeichenaufruf, im Log abgelesen an
     * {@code GlCommandEncoder}, nicht vermutet.
     */
    public static void anmelden(GpuTextureView farbZiel, GpuTextureView tiefenZiel)
    {
        aktiv = farbZiel;
        tiefe = tiefenZiel;
    }

    public static void abmelden()
    {
        aktiv = null;
        tiefe = null;
    }

    /** Das angemeldete Ziel, oder {@code null} wenn gerade kein Auswahl-Durchgang laeuft. */
    public static GpuTextureView aktiv()
    {
        return aktiv;
    }

    /**
     * Das Tiefenziel des laufenden Auswahl-Durchgangs.
     *
     * <p>{@code null} heisst: der Auswahl-Puffer hat keinen Tiefenanhang. Dann zeichnet
     * der Durchgang ohne Verdeckungspruefung -- siehe {@link #anmelden}, warum das falsch
     * ist. Es ist trotzdem kein Absturz, sondern eine Warnung je Zeichenaufruf.
     */
    public static GpuTextureView tiefe()
    {
        return tiefe;
    }

    /**
     * Das angemeldete Ziel -- oder eine Ausnahme.
     *
     * <p>Fuer Zeichenaufrufe, die NUR im Auswahl-Durchgang vorkommen. Ohne Ziel waere das
     * Ergebnis ein doppelter stiller Fehler: Kennungsfarben sichtbar im Bild, und das
     * Angeklickte nicht mehr auffindbar.
     */
    public static GpuTextureView pflicht()
    {
        if (aktiv == null)
        {
            throw new IllegalStateException(
                "Auswahl-Zeichnung ohne angemeldetes Ziel. Erwartet wird ein "
                + "StencilFormFramebuffer.apply() davor; ohne das wuerden die Kennungsfarben "
                + "sichtbar ins Bild gemalt und der Klick fande nichts mehr.");
        }

        return aktiv;
    }

    private PickZiel()
    {}
}
