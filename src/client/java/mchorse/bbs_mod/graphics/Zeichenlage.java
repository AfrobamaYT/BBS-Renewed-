package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * ALLE Eingaenge einer Zeichenstelle auf einmal — gegen das Raten, welcher davon schuld ist.
 *
 * <h2>⛔ Warum es das gibt (2026-08-03, 2 Tage an EINEM Bug)</h2>
 *
 * <p>Der orange Auswahl-Umriss stand bei Kamerabewegung neben der Figur. Bauen des Features:
 * 1-2 Stunden. Fixen: zwei Tage, <b>neun gescheiterte Fixe</b>. Der Grund war nicht, dass
 * die Messwerkzeuge fehlten — es gibt 63 davon. Sie messen alle einen ZUSTAND (Bilder je
 * Sekunde, Zaehler, Pufferinhalt, Absturzrate). Keins beantwortet die Frage, die bei einem
 * Zeichenfehler zaehlt: <b>dieser Wert ist falsch — woher kommt er?</b></p>
 *
 * <p>Schlimmer war der eigene Messstil: jede Sonde prueft EINE Vermutung.</p>
 *
 * <pre>
 *   "ist es der Zeitversatz?"    -> bauen, tauschen, messen, nein     ~20 min
 *   "ist es die Drosselung?"     -> bauen, tauschen, messen, nein     ~20 min
 *   "ist es die Kameraposition?" -> bauen, tauschen, messen, nein     ~20 min
 *   ... neunmal
 * </pre>
 *
 * <p><b>Ein Messpunkt mit neun Werten kostet exakt so viel wie einer mit einem.</b> Sparsam
 * messen heisst neunmal bezahlen. Genau diese Lehre stand schon in {@code WorldRendererMixin}
 * — „ALLE verdaechtigen Werte auf einmal, nicht einen pro Lauf" — dort aber nur, weil ein
 * Mixin einen Neustart kostet. Sie gilt ueberall.</p>
 *
 * <h2>Gebrauch</h2>
 *
 * <p>An zwei Stellen haengen, die dasselbe sehen muessten, und die Ausgaben nebeneinander
 * legen. Der abweichende Wert IST die Ursache — ohne Vermutung, ohne zweiten Durchgang:</p>
 *
 * <pre>
 *   Zeichenlage.melden("stencil");     // dort, wo der Auswahlpuffer gefuellt wird
 *   Zeichenlage.melden("weltbild");    // dort, wo das Bild darunter entsteht
 *
 *   python tools/lagen.py stencil weltbild      // stellt beide gegenueber
 * </pre>
 *
 * <p><b>Kein Vergleich mit einer alten Fassung noetig</b> — zwei Stellen im selben laufenden
 * Programm genuegen. Das trennt diese Klasse vom Differenz-Modus gegen 1.21.1, der nur bei
 * Regressionen hilft.</p>
 *
 * <h2>⚠ Was hier NICHT hineingehoert</h2>
 *
 * <p>Nichts, was den Render-Thread braucht und aus einem anderen laufen koennte, und nichts
 * Teures. Die Liste ist bewusst kurz und fest: was jede Zeichenstelle in 26.2 als Eingang
 * hat. Waechst sie, wird der Vergleich unlesbar — und ein unlesbarer Vergleich wird nicht
 * gelesen.</p>
 */
public final class Zeichenlage
{
    private Zeichenlage()
    {}

    /**
     * Alle Eingaenge dieser Stelle in EINE Messpunkt-Zeile schreiben.
     *
     * <p>Bewusst tolerant: faellt ein einzelner Wert aus (Feld weg, falscher Thread, nach
     * einem Hotswap noch nicht gesetzt), steht dort ein Fehlertext statt einer Ausnahme.
     * Eine Sonde, die das gemessene Programm reisst, ist wertlos.</p>
     *
     * @param stelle kurzer Name, unter dem die Zeile im Bericht steht
     */
    public static void melden(String stelle)
    {
        /* ⛔ SCHALTER ZUERST. Messpunkt#zaehle prueft ihn auch, aber erst INNEN — der
         * StringBuilder samt acht Abfragen liefe dann in jedem Bild mit, obwohl niemand
         * misst. Eine Sonde, die im Ruhezustand Bilder kostet, wird wieder ausgebaut. */
        if (System.getProperty("bbs.mess") == null)
        {
            return;
        }

        StringBuilder b = new StringBuilder();

        b.append("kamera=").append(wert(Zeichenlage::kamera));
        b.append(" | drehung=").append(wert(Zeichenlage::drehung));
        b.append(" | projektion=").append(wert(Zeichenlage::projektion));
        b.append(" | teilbild=").append(wert(Zeichenlage::teilbild));
        b.append(" | ziel=").append(wert(Zeichenlage::ziel));
        b.append(" | fenster=").append(wert(Zeichenlage::fenster));
        b.append(" | video=").append(wert(Zeichenlage::video));
        b.append(" | bild=").append(wert(Zeichenlage::bildnummer));

        Messpunkt.zaehle("lage." + stelle, b.toString());
    }

    /* ⛔ HIER STAND EINE NEUE METHODE ansicht(...) — SIE KOSTETE EINEN HOTSWAP (2026-08-03).
     * Die JVM lehnt das Hinzufuegen von Methoden an einer geladenen Klasse ab
     * ("add method not implemented"), auch mit AllowEnhancedClassRedefinition. Geaenderte
     * KOERPER gehen, neue Methoden nicht. Die Sonde steht deshalb inline an den beiden
     * Zeichenstellen (UIFilmPanel#renderInWorld, UIFilmController) und ruft direkt
     * Messpunkt#zaehle — das ist eine Zeile mehr Text und spart einen Neustart. */

    /** Ein einzelner Wert darf ausfallen, ohne die Sonde zu reissen. */
    private static String wert(java.util.function.Supplier<String> holen)
    {
        try
        {
            return holen.get();
        }
        catch (Throwable t)
        {
            return "<" + t.getClass().getSimpleName() + ">";
        }
    }

    private static String kamera()
    {
        net.minecraft.world.phys.Vec3 p =
            Minecraft.getInstance().gameRenderer.mainCamera().position();

        return String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", p.x, p.y, p.z);
    }

    private static String drehung()
    {
        net.minecraft.client.Camera k = Minecraft.getInstance().gameRenderer.mainCamera();

        return String.format(java.util.Locale.ROOT, "%.2f/%.2f", k.yRot(), k.xRot());
    }

    /**
     * Die Projektion, die BBS zuletzt gesetzt hat.
     *
     * <p>⚠ 26.2 gibt die AKTIVE Projektion nicht mehr als Zahlen heraus (sie liegt als
     * Puffer auf der Grafikkarte). Lesbar ist der eingefrorene Kamerazustand — derselbe Weg,
     * den {@link Projektion#lesen()} nimmt. Wer eine andere Matrix aktiviert hat, sieht das
     * hier NICHT; genau darum steht daneben auch die Art (perspektivisch/flach), die sich
     * unterscheidet, wenn jemand dazwischengefunkt hat.</p>
     */
    private static String projektion()
    {
        Matrix4f m = Projektion.lesen();

        return String.format(java.util.Locale.ROOT, "m00=%.4f m11=%.4f", m.m00(), m.m11());
    }

    private static String teilbild()
    {
        return String.format(java.util.Locale.ROOT, "%.4f",
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    /**
     * Welcher Puffer wird gerade beschrieben — der haeufigste stille Unterschied.
     *
     * <p>⚠ Gemeldet wird BBS' Umlenkziel, nicht der GL-Bindungszustand: den koennte nur der
     * Render-Thread lesen, und diese Sonde muss aus jedem Thread laufen duerfen.</p>
     */
    private static String ziel()
    {
        /* ⚠ 26.2 hat kein Minecraft#getMainRenderTarget() mehr. Ob BBS umgelenkt hat, ist
         * ohnehin der Wert, auf den es ankommt — die Groessen stehen in `fenster` und
         * `video` daneben. */
        return mchorse.bbs_mod.client.BBSRendering.renderingWorld ? "bbs-umgelenkt" : "vanilla";
    }

    private static String fenster()
    {
        com.mojang.blaze3d.platform.Window f = Minecraft.getInstance().getWindow();

        return f.getWidth() + "x" + f.getHeight() + " gui=" + f.getGuiScale();
    }

    private static String video()
    {
        return mchorse.bbs_mod.client.BBSRendering.getVideoWidth() + "x"
             + mchorse.bbs_mod.client.BBSRendering.getVideoHeight();
    }

    /**
     * Laufende Bildnummer.
     *
     * <p>⛔ DER WICHTIGSTE WERT DER LISTE. Ohne ihn ist nicht zu unterscheiden, ob zwei
     * Stellen verschiedene Werte sehen (echter Befund) oder ob sie in verschiedenen BILDERN
     * gemessen wurden (Messfehler). Am 2026-08-03 hat genau diese Verwechslung einen
     * "Befund" von 32-89 px erzeugt, der komplett aus dem Messgeraet kam.</p>
     */
    private static String bildnummer()
    {
        /* ⛔ DAS IST DIE FRAME-DAUER, KEINE BILDNUMMER — und damit als Vergleichswert
         * UNBRAUCHBAR (gemessen 2026-08-03). Zwei Sonden aus VERSCHIEDENEN Bildern melden
         * denselben Wert, sobald beide Bilder gleich lange dauerten; zwei aus DEMSELBEN Bild
         * melden verschiedene, sobald die Dauer dazwischen neu gesetzt wurde. Ausgerechnet
         * der Wert, mit dem die Regel "IMMER die Bildnummer mitmessen" abgesichert werden
         * sollte, sichert also nichts — und die Vergleichbarkeitspruefung in tools/lagen.py
         * ("VERSCHIEDENE BILDER -> nicht vergleichbar") haengt daran.
         *
         * WARUM ES TROTZDEM NOCH HIER STEHT: 26.2 bietet keinen erreichbaren Bildzaehler.
         * Minecraft hat keinen (nachgesehen: framebufferSizeChanged, getFrameTimeNs,
         * getFramerateLimitTracker, getPendingTasksCount, renderFrame — sonst nichts), und
         * RenderSystem darf aus dem EXEC-Kanal nicht gefragt werden (Render-Thread-Guard).
         * Der Ausweg ist ein eigener Zaehler, hochgezaehlt in einem echten Render-Hook —
         * ein neues statisches Feld, also erst beim naechsten ohnehin faelligen Boot.
         * BIS DAHIN: Urteilen aus "gleiche Bildnummer" NICHT trauen. */
        return String.valueOf(Minecraft.getInstance().getFrameTimeNs()) + " (DAUER, keine Nr)";
    }
}
