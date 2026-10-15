package com.afrobama.bbstools.sonden;

import java.util.List;
import java.util.function.Consumer;

import com.afrobama.bbstools.debug.IFeatureDebugger;

/**
 * Der Anschluss an die Mess-Registratur, die in der Companion-Mod liegt.
 *
 * <p>Oeffentlicher Code, der eine Textausgabe oder einen Schalter anbieten WILL, meldet ihn
 * hier an; ob jemand zuhoert, entscheidet allein, ob die Companion installiert ist. Ohne sie
 * ist jede Anmeldung ein no-op und {@link #alle()} leer — der Aufrufer laeuft unveraendert
 * weiter.</p>
 *
 * <p>⛔ {@link IFeatureDebugger} liegt bewusst WEITER im oeffentlichen Baum, obwohl sein
 * Name nach Debug klingt: die Perf-HUD und der AsyncMeshLoader implementieren es, und beide
 * sind Consumer-Features. Nur der Paketname sagt "debug", der Inhalt ist das Messgeruest
 * eines ausgelieferten Features.</p>
 */
public interface Messregister
{
    /** Standardfassung ohne Companion: nimmt alles an und tut nichts damit. */
    Messregister NICHTS = new Messregister() {};

    /** Wird EINMAL beim Start der Companion gesetzt, nie im laufenden Betrieb getauscht. */
    Halter HALTER = new Halter();

    /** Eine benannte Textausgabe anmelden — {@code /bbsdebug <name>} ruft den Schreiber. */
    default void text(String name, Consumer<StringBuilder> schreiber)
    {}

    /** Einen Schalter samt Status- und Zaehlerausgabe anmelden. */
    default void feature(IFeatureDebugger feature)
    {}

    /** Alle angemeldeten Schalter. Ohne Companion: leer. */
    default Iterable<IFeatureDebugger> alle()
    {
        return List.of();
    }

    /** Traegt die aktive Fassung. Eigene Klasse, weil ein Interface kein Feld haben kann. */
    final class Halter
    {
        public volatile Messregister aktiv = NICHTS;

        private Halter()
        {}
    }
}
