package com.afrobama.bbstools.sonden;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * "Verwaltet die Companion-Mod diese Kreatur?" — die Frage, die der Ragdoll-Umbau stellen
 * muss, um solche Kreaturen in Ruhe zu lassen.
 *
 * <p>Ohne Companion ist die Antwort immer nein, und der Aufrufer laeuft unveraendert
 * weiter.</p>
 *
 * <p>⛔ Kein {@code Class.forName} an der Aufrufstelle: die drei Stellen liegen in Schleifen
 * ueber ALLE Kreaturen im Umkreis. Ein Praedikat, das ohne Companion konstant {@code false}
 * liefert, kostet dort nichts; eine Reflexionssuche je Kreatur waere messbar.</p>
 */
public final class ArmeeFilter
{
    /** Wird EINMAL beim Start der Companion gesetzt, nie im laufenden Betrieb getauscht. */
    public static volatile Predicate<UUID> istArmee = u -> false;

    private ArmeeFilter()
    {}
}
