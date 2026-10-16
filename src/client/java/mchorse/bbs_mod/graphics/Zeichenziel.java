package mchorse.bbs_mod.graphics;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * In WELCHES Bild BBS gerade zeichnet.
 *
 * <h2>Das Problem</h2>
 *
 * <p>{@code ModelVAORenderer} holte sein Ziel fest verdrahtet aus
 * {@code gameRenderer.mainRenderTarget()}. In der Welt ist das richtig -- das Modell soll
 * ins Weltbild. In einer Oberflaechen-Vorschau ist es falsch: dort muss dasselbe Modell in
 * die Textur eines Bild-im-Bild-Zeichners, sonst landet es im Hauptbild und wird vom
 * Panel-Hintergrund uebermalt (gemessen, Aufgabe 96: 574 Zeichendurchgaenge je vier
 * Sekunden, alle unsichtbar unter UIModelEditorPanel:303).
 *
 * <h2>Dieselbe Bruecke wie {@link Zeichenbereich} und {@link Projektion}</h2>
 *
 * <p>Aus demselben Grund: der Wert wird weit oben bestimmt (im Zeichner der Vorschau) und
 * weit unten gebraucht (in jedem einzelnen Zeichenaufruf des Modells). Ihn durchzureichen
 * hiesse, ein Dutzend Signaturen anzufassen, die mit Oberflaeche nichts zu tun haben.
 *
 * <p>{@code null} heisst: das Hauptbild. Das ist der Normalfall und die Welt.
 *
 * <h2>⚠️ Wer setzt, gibt selbst wieder frei</h2>
 *
 * <p>Genau wie bei {@link Zeichenbereich}. Dort hatte ein Aufrufer das Freigeben vergessen,
 * und der Ausschnitt der Vorschau schnitt danach jedes Netz im ganzen Spiel zu (06fbc62e0).
 * Hier waere der Schaden groesser: alles Folgende zeichnete in eine Vorschautextur.
 */
public final class Zeichenziel
{
    private static GpuTextureView farbe;
    private static GpuTextureView tiefe;

    public static void setzen(GpuTextureView farbZiel, GpuTextureView tiefenZiel)
    {
        farbe = farbZiel;
        tiefe = tiefenZiel;
    }

    public static void freigeben()
    {
        farbe = null;
        tiefe = null;
    }

    /** Das Farbziel, oder {@code null} fuer das Hauptbild. */
    public static GpuTextureView farbe()
    {
        return farbe;
    }

    /** Das Tiefenziel, oder {@code null} fuer das Hauptbild. */
    public static GpuTextureView tiefe()
    {
        return tiefe;
    }

    /** Zeichnet gerade jemand in ein eigenes Ziel? */
    public static boolean eigenes()
    {
        return farbe != null;
    }

    private Zeichenziel()
    {}
}
