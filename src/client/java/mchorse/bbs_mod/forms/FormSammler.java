package mchorse.bbs_mod.forms;

import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Der Sammler, in den Formen gerade ihre Zeichenauftraege einreichen.
 *
 * <h2>Warum es ihn gibt</h2>
 *
 * <p>In 26.2 zeichnet niemand mehr sofort: man reicht einen Auftrag bei einem
 * {@link SubmitNodeCollector} ein, und der Bildgraph zeichnet ihn spaeter in der richtigen
 * Reihenfolge. Der Sammler kommt vom Weltdurchgang ganz oben; BBS' Form-Renderer brauchen
 * ihn ganz unten.
 *
 * <p>Dieselbe Bruecke wie {@code PickZiel} und {@code Zeichenbereich}, aus demselben Grund:
 * das Ding als Parameter durch jede Form-Renderer-Signatur zu reichen waere richtig, aber
 * es sind ein Dutzend Ketten, und der Port haette dann eine Baustelle mehr statt einer
 * weniger.
 *
 * <h2>⚠️ Die Oberflaechen-Wege haben KEINEN Sammler</h2>
 *
 * <p>{@code renderInUI} zeichnet eine Form in ein Bedienelement, nicht in die Welt -- dort
 * laeuft kein Weltdurchgang und es gibt nichts einzureichen. Diese Wege muessen ihren
 * eigenen Durchgang oeffnen (siehe SofortZeichner/GehaltenesNetz) oder, fuer ganze
 * Vanilla-Szenen, ueber Mojangs PictureInPicture gehen.
 *
 * <p>Darum wirft {@link #pflicht()} statt {@code null} zurueckzugeben: ein Form-Renderer,
 * der im UI-Weg nach dem Weltsammler greift, bekommt eine klare Meldung und keine
 * Nullzeiger-Ausnahme funfzehn Zeilen spaeter.
 */
public final class FormSammler
{
    private static SubmitNodeCollector aktiv;

    public static void anmelden(SubmitNodeCollector sammler)
    {
        aktiv = sammler;
    }

    public static void abmelden()
    {
        aktiv = null;
    }

    /** Der angemeldete Sammler, oder {@code null} ausserhalb eines Weltdurchgangs. */
    public static SubmitNodeCollector aktiv()
    {
        return aktiv;
    }

    public static SubmitNodeCollector pflicht()
    {
        if (aktiv == null)
        {
            throw new IllegalStateException(
                "Kein Sammler angemeldet. Formen koennen nur WAEHREND eines Weltdurchgangs "
                + "einreichen; die Oberflaechen-Wege (renderInUI) muessen einen eigenen "
                + "Durchgang oeffnen statt nach dem Weltsammler zu greifen.");
        }

        return aktiv;
    }

    private FormSammler()
    {}
}
