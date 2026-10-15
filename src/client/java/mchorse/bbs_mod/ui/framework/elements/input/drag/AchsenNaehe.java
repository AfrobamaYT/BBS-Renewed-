package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.utils.Axis;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Welche der drei Achsen ein Bildschirm-Zug am staerksten trifft.
 *
 * <p>Das ist Blenders {@code setNearestAxis3d} ({@code transform_constraints.cc}): jede
 * Achse des aktiven Bezugsrahmens wird auf den Bildschirm abgebildet, und gewaehlt wird
 * die, neben der der Mauszug den KLEINSTEN SENKRECHTEN REST laesst — die Formel der
 * Vorlage, damit sie hier ablesbar bleibt.
 *
 * <p>Reine Rechnung, kein Zustand: die Geste selbst liegt in {@link MittelklickAchse}.
 *
 * <h2>⛔ DIE FALLE: der Ursprung kann HINTER der Near-Plane liegen</h2>
 *
 * <p>Sitzt die Kamera auf dem bearbeiteten Knochen (First Person, oder ein Kamera-Clip auf
 * dem Kopf), liegt dessen Weltposition hinter der Near-Plane -- gemessen w = -0.297. Als
 * Weltpunkt gueltig, aber NICHT projizierbar: {@link GizmoDrag#projectToScreen} gibt dort
 * {@code false}, und jede Rechnung, die trotzdem durch w teilt, entartet (genau daran
 * stirbt der gesweepte Winkel der Strahl-Gesten, siehe {@code FilmGizmoDrag.isGizmoUsable}).
 *
 * <p>Deshalb ZWEI Wege, und der zweite ist kein Notnagel, sondern die einzige Antwort, die
 * es dort ueberhaupt gibt:
 *
 * <ol>
 * <li><b>Projiziert</b> (wie Blender): Ursprung und {@code Ursprung + Achse * Schritt}
 *     projizieren, Differenz = Bildschirmrichtung der Achse. Zeigt eine Achse auf die
 *     Kamera zu, faellt ihr Vorwaerts-Punkt hinter die Near-Plane -- dann wird der Punkt
 *     RUECKWAERTS genommen und die Richtung negiert. Dieselbe Gerade, andere Seite. (Blender
 *     laesst so eine Achse einfach herausfallen; der Rueckwaerts-Versuch kostet drei Zeilen
 *     und rettet den Fall, in dem sie noch eindeutig ist.)</li>
 * <li><b>Kamerarelativ</b>: scheitert der Ursprung selbst, existiert die perspektivische
 *     Bildschirmrichtung an dieser Stelle NICHT -- die Achse ist dort nicht abbildbar. Dann
 *     wird die Achse nur durch die Ansichtsdrehung geschoben ({@link GizmoDrag#view} ist
 *     reine Drehung, siehe dortige Doku) und mit den Massstaeben der Projektion auf Bildpunkte
 *     skaliert. Das ist die orthographische Lesart derselben Frage: "wohin zeigt diese Achse
 *     aus Sicht der Kamera". Sie kann nicht entarten -- eine Drehmatrix ist immer invertierbar
 *     und teilt durch nichts.</li>
 * </ol>
 *
 * <p>⛔ ENTWEDER-ODER, NIE GEMISCHT. Scheitert auch nur EINE Achse auf dem projizierten Weg,
 * werden ALLE DREI kamerarelativ gerechnet. Zwei Achsen im perspektivischen Massstab gegen
 * eine im orthographischen zu vergleichen, waere derselbe Fehler, an dem in Aufgabe #9 die
 * erste Fassung starb: zwei Bezugssysteme in einer Rechnung. Die Reihenfolge ist frei
 * waehlbar, solange alle drei aus demselben Topf kommen.
 */
public final class AchsenNaehe
{
    /** Mindestzug in Bildpunkten, bevor eine Richtung ueberhaupt gemeint sein kann —
     *  darunter ist der Zug Rauschen und die Wahl spraenge bei jedem Zittern um. */
    public static final float MINDESTZUG = 6F;

    /**
     * Laenge des Achsenstuecks, das projiziert wird -- als Anteil des Kameraabstands, damit
     * es aus jeder Entfernung ungefaehr gleich viele Bildpunkte lang wird (Blender rechnet
     * das ueber {@code zfac} auf ~30 Bildpunkte). Nur die RICHTUNG wird gebraucht, die Laenge
     * faellt beim Normieren wieder heraus; sie muss bloss gross genug fuer die Genauigkeit
     * von {@code float} und klein genug sein, um nicht durch das halbe Bild zu schiessen.
     */
    private static final float ANTEIL_KAMERAABSTAND = 0.1F;

    /** Untergrenze fuer das Achsenstueck, falls die Kamera exakt auf dem Ursprung sitzt. */
    private static final float MINDESTSCHRITT = 1.0E-3F;

    /**
     * Ab wann eine Achse als "zeigt in die Kamera" gilt und aus der Wahl faellt: ihre
     * Bildschirmlaenge im Verhaeltnis zur laengsten der drei. Relativ statt absolut, weil
     * der Massstab der beiden Wege verschieden ist -- eine feste Bildpunktzahl waere auf dem
     * kamerarelativen Weg eine andere Schwelle als auf dem projizierten.
     */
    private static final float RELATIVE_NULLLAENGE = 1.0E-3F;

    private AchsenNaehe()
    {}

    /**
     * Die Achse, an der der Zug {@code (dx, dy)} am ehesten entlanglaeuft, oder {@code null}
     * wenn keine Aussage moeglich ist: zu kurzer Zug, kein Zug-Schnappschuss, oder alle drei
     * Achsen zeigen in die Kamera.
     *
     * @param zug  Schnappschuss mit Kamera, Sichtfeld und Gizmo-Ursprung. Muss NICHT
     *             projizierbar sein -- der zweite Weg oben faengt genau das ab.
     * @param raum Bezugsrahmen, dessen Achsen verglichen werden ({@link GizmoDrag#frameBasis}).
     * @param dx   waagerechter Zug in Bildpunkten, seit dem Ursprung der Geste.
     * @param dy   senkrechter Zug in Bildpunkten, Y WAECHST NACH UNTEN (Zeigerkonvention
     *             von {@link GizmoDrag#projectToScreen}).
     */
    public static Axis naechsteAchse(GizmoDrag zug, TransformSpace raum, float dx, float dy)
    {
        if (zug == null || raum == null || dx * dx + dy * dy < MINDESTZUG * MINDESTZUG)
        {
            return null;
        }

        Matrix3f basis = zug.frameBasis(raum);
        Vector2f[] richtungen = projizierteRichtungen(zug, basis);

        if (richtungen == null)
        {
            richtungen = kamerarelativeRichtungen(zug, basis);
        }

        return kleinsterRest(richtungen, dx, dy);
    }

    /**
     * Weg 1: echte Projektion. Gibt {@code null} zurueck, sobald AUCH NUR EINE Achse nicht
     * abbildbar ist -- dann muss der Aufrufer alle drei kamerarelativ holen (siehe
     * Klassendoku, "entweder-oder").
     */
    private static Vector2f[] projizierteRichtungen(GizmoDrag zug, Matrix3f basis)
    {
        Vector2f mitte = new Vector2f();

        if (!zug.projectToScreen(zug.gizmoOrigin, mitte))
        {
            /* Der Ursprung selbst liegt hinter der Near-Plane: der gemessene
             * First-Person-Fall (w = -0.297). Hier hat "wohin zeigt die Achse auf dem
             * Bildschirm" schlicht keine Antwort. */
            return null;
        }

        float schritt = (float) Math.max(zug.cameraOrigin.distance(zug.gizmoOrigin) * ANTEIL_KAMERAABSTAND, MINDESTSCHRITT);
        Vector2f[] richtungen = new Vector2f[3];
        Vector3f achse = new Vector3f();
        Vector2f punkt = new Vector2f();

        for (int i = 0; i < 3; i++)
        {
            basis.getColumn(i, achse);

            double ax = zug.gizmoOrigin.x + achse.x * schritt;
            double ay = zug.gizmoOrigin.y + achse.y * schritt;
            double az = zug.gizmoOrigin.z + achse.z * schritt;

            if (zug.projectToScreen(ax, ay, az, punkt))
            {
                richtungen[i] = new Vector2f(punkt.x - mitte.x, punkt.y - mitte.y);

                continue;
            }

            /* Vorwaerts hinter der Near-Plane, also rueckwaerts nehmen und umdrehen: die
             * Achse ist eine GERADE, ihre Bildschirmrichtung bis aufs Vorzeichen dieselbe.
             * Und das Vorzeichen ist egal -- gewaehlt wird ueber den senkrechten Rest, der
             * fuer +Achse und -Achse gleich ist. */
            ax = zug.gizmoOrigin.x - achse.x * schritt;
            ay = zug.gizmoOrigin.y - achse.y * schritt;
            az = zug.gizmoOrigin.z - achse.z * schritt;

            if (!zug.projectToScreen(ax, ay, az, punkt))
            {
                return null;
            }

            richtungen[i] = new Vector2f(mitte.x - punkt.x, mitte.y - punkt.y);
        }

        return richtungen;
    }

    /**
     * Weg 2: kamerarelativ, ohne jede Projektion des Ursprungs.
     *
     * <p>{@link GizmoDrag#view} ist die reine Dreh-Abbildung Welt&rarr;Kamera (die
     * Verschiebung steckt in {@link GizmoDrag#cameraOrigin}), also liefert sie die Achse in
     * Kamerakoordinaten. Die Massstaebe {@code m00}/{@code m11} der Projektion mal der halben
     * Sichtfeldgroesse rechnen das in Bildpunkte um -- so gilt das Ergebnis auch bei einem
     * Sichtfeld, das nicht quadratisch ist. Y wird umgedreht, weil Kamera-Y nach oben zeigt
     * und Bildschirm-Y nach unten ({@link GizmoDrag#projectToScreen}).
     *
     * <p>Was dabei fehlt, ist ehrlich benannt: die perspektivische Verkuerzung ueber die
     * Bildflaeche. Die haengt an der Position des Ursprungs -- und genau die ist hier nicht
     * verwertbar. Fuer die Frage "welche der drei Achsen meint der Zug" reicht die Richtung;
     * sie waere nur dann irrefuehrend, wenn zwei Achsen ohnehin fast gleich nah liegen.
     */
    private static Vector2f[] kamerarelativeRichtungen(GizmoDrag zug, Matrix3f basis)
    {
        Matrix3f ansicht = zug.view.get3x3(new Matrix3f());
        float skalaX = zug.projection.m00() * (zug.viewportW / 2F);
        float skalaY = zug.projection.m11() * (zug.viewportH / 2F);
        Vector2f[] richtungen = new Vector2f[3];
        Vector3f achse = new Vector3f();

        for (int i = 0; i < 3; i++)
        {
            basis.getColumn(i, achse);
            ansicht.transform(achse);

            richtungen[i] = new Vector2f(achse.x * skalaX, -achse.y * skalaY);
        }

        return richtungen;
    }

    /**
     * Blenders Auswahl: Zug auf jede Achsenrichtung projizieren, der senkrechte Rest ist das
     * Mass. Achsen, die praktisch in die Kamera zeigen, haben keine brauchbare
     * Bildschirmrichtung und fallen heraus.
     */
    private static Axis kleinsterRest(Vector2f[] richtungen, float dx, float dy)
    {
        float laengste = 0F;

        for (Vector2f richtung : richtungen)
        {
            laengste = Math.max(laengste, richtung.length());
        }

        if (laengste <= 0F)
        {
            return null;
        }

        Axis gewaehlt = null;
        float bester = Float.MAX_VALUE;

        for (int i = 0; i < 3; i++)
        {
            Vector2f richtung = richtungen[i];

            if (richtung.length() < laengste * RELATIVE_NULLLAENGE)
            {
                continue;
            }

            richtung.normalize();

            float entlang = dx * richtung.x + dy * richtung.y;
            float querX = dx - richtung.x * entlang;
            float querY = dy - richtung.y * entlang;
            float quer = (float) Math.sqrt(querX * querX + querY * querY);

            if (quer < bester)
            {
                bester = quer;
                gewaehlt = Axis.values()[i];
            }
        }

        return gewaehlt;
    }
}
