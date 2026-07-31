package mchorse.bbs_mod.forms;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.function.Function;

/**
 * BBS' Einreichstelle fuer Formen -- der Ort, an dem eine Umfaerbung dazwischenkommt.
 *
 * <h2>Was diese Klasse frueher war und warum davon wenig bleibt</h2>
 *
 * <p>Bis 1.21.1 hing BBS sich in Vanillas SOFORT-Zeichnung ein. Es gab einen geteilten
 * Puffer je Ebene; man holte sich einen {@code VertexConsumer}, schrieb hinein, und am
 * Ende des Bildes wurde Ebene fuer Ebene gezeichnet. Diese Klasse nutzte beide Enden:
 *
 * <ul>
 *   <li>beim HOLEN legte sie einen Umfaerber um den Puffer, damit eine Form getoent
 *       werden kann,</li>
 *   <li>beim ZEICHNEN stahl sie halbdurchsichtige Ebenen aus Vanillas Warteschlange und
 *       schob sie in BBS' eigene, sortierte -- sonst schrieben deren Pixel mitten im Bild
 *       Tiefe und verdeckten Formen, die danach kamen.</li>
 * </ul>
 *
 * <h2>26.2: der zweite Teil ist ERLEDIGT, nicht portiert</h2>
 *
 * <p>Es gibt keine Sofort-Zeichnung mehr. Man reicht einen Auftrag ein, und der Bildgraph
 * zeichnet ihn spaeter -- sortiert. Genau das war der Zweck des Stehlens. Die Ebene aus
 * einer Warteschlange zu ziehen, die es nicht mehr gibt, um sie in eine eigene zu legen,
 * die dasselbe tut, waere doppelte Arbeit mit doppelter Fehlerquelle.
 *
 * <p>⚠️ BBS' eigene Warteschlange BLEIBT trotzdem -- aber nur fuer BBS' EIGENE Geometrie
 * (Modellnetze, Billboards). Die kennt der Bildgraph nicht, also kann er sie auch nicht
 * einsortieren. Wer beides verwechselt, baut die geloeschte Haelfte wieder ein.
 *
 * <p>Was bleibt, ist der erste Teil: die Umfaerbung. Sie greift jetzt dort, wo BBS seine
 * Ecken WIRKLICH schreibt -- im Rumpf eines {@code submitCustomGeometry}, wo der
 * Zeichenweg einem den Verbraucher reicht. {@link #umfaerben(VertexConsumer)} ist die
 * Stelle, an der das passiert.
 */
public class CustomVertexConsumerProvider
{
    private final SubmitNodeCollector sammler;

    private Function<VertexConsumer, VertexConsumer> substitute;

    public CustomVertexConsumerProvider(SubmitNodeCollector sammler)
    {
        this.sammler = sammler;
    }

    public SubmitNodeCollector sammler()
    {
        return this.sammler;
    }

    /**
     * Setzt die Umfaerbung fuer die naechsten Zuege, oder {@code null} zum Abschalten.
     *
     * <p>Bleibt ein Aufruf mit {@code null} aus, faerbt die naechste Form mit -- das war
     * schon vor 26.2 so und ist der Grund, warum die Aufrufer sie paarweise setzen.
     */
    public void setSubstitute(Function<VertexConsumer, VertexConsumer> substitute)
    {
        this.substitute = substitute;

        if (this.substitute == null)
        {
            RecolorVertexConsumer.newColor = null;
        }
    }

    /**
     * Friert die AKTUELL eingestellte Umfaerbung ein.
     *
     * <p>⛔ DAS IST DIE VARIANTE, DIE MAN BEI {@code submitCustomGeometry} BRAUCHT, und der
     * Unterschied ist kein Stilfrage. Der Rumpf eines {@code submitCustomGeometry} laeuft
     * VERZOEGERT: der Sammler legt ihn als Feld in einem Auftrags-Record ab und fuehrt ihn
     * erst aus, wenn der Bildgraph das Bild baut. Zu diesem Zeitpunkt hat die Aufrufstelle
     * ihr {@code setSubstitute(null)} laengst gesetzt -- {@link #umfaerben} im Rumpf gibt
     * dann den ROHEN Verbraucher zurueck, und die Form-Farbe ist still verloren.
     *
     * <p>Deshalb: hier einfrieren, BEVOR eingereicht wird, und die eingefrorene Funktion in
     * den Rumpf mitnehmen. Sie traegt die Farbe im Abschluss (siehe
     * {@code BBSRendering.getColorConsumer}), haengt also nicht mehr am Zustand des Anbieters.
     *
     * @return eine Funktion, die den Verbraucher umfaerbt -- oder ihn unveraendert
     *         durchreicht, wenn beim Einreichen keine Umfaerbung eingestellt war.
     */
    public Function<VertexConsumer, VertexConsumer> umfaerbungEinfrieren()
    {
        Function<VertexConsumer, VertexConsumer> eingefroren = this.substitute;

        if (eingefroren == null)
        {
            return (verbraucher) -> verbraucher;
        }

        return (verbraucher) ->
        {
            VertexConsumer umgefaerbt = eingefroren.apply(verbraucher);

            return umgefaerbt == null ? verbraucher : umgefaerbt;
        };
    }

    /**
     * Legt die eingestellte Umfaerbung um einen Verbraucher.
     *
     * <p>Aufzurufen im Rumpf eines {@code submitCustomGeometry}, wo der Zeichenweg den
     * Verbraucher reicht. Ohne eingestellte Umfaerbung kommt er unveraendert zurueck.
     */
    public VertexConsumer umfaerben(VertexConsumer verbraucher)
    {
        if (this.substitute == null)
        {
            return verbraucher;
        }

        VertexConsumer umgefaerbt = this.substitute.apply(verbraucher);

        return umgefaerbt == null ? verbraucher : umgefaerbt;
    }

    /**
     * ⛔ 26.2: ersatzlos entfallen, absichtlich als leerer Rumpf stehengelassen.
     *
     * <p>Frueher schaltete das den Tiefenvergleich fuer Formen in der Oberflaeche auf
     * "immer bestehen", weil die Sofort-Zeichnung ihn auf GL_LESS zuruecksetzte. Beides
     * gibt es nicht mehr: kein globaler Tiefenvergleich, keine Sofort-Zeichnung, die ihn
     * verstellen koennte. Der Zustand gehoert jetzt zur Pipeline des jeweiligen Zuges.
     *
     * <p>Der Rumpf bleibt leer statt die Methode zu loeschen, damit die zehn Aufrufstellen
     * lesbar bleiben -- sie zeigen, welche Wege durch die Oberflaeche gehen, und das ist
     * beim naechsten Schritt (eigener Durchgang je UI-Form) genau die Liste, die man braucht.
     */
    public void setUI(boolean ui)
    {}

    /**
     * ⛔ 26.2: ersatzlos entfallen, siehe {@link #setUI(boolean)}.
     *
     * <p>Es gibt keinen halbfertigen Stapel mehr, den man leeren koennte. Eingereichte
     * Auftraege zeichnet der Bildgraph, wenn er an der Reihe ist.
     */
    public void draw()
    {}
}
