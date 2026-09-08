package com.afrobama.bbstools.sonden;

import java.util.UUID;
import java.util.function.Predicate;


public final class ArmeeFilter
{
    /** Wird EINMAL beim Start der Companion gesetzt, nie im laufenden Betrieb getauscht. */
    public static volatile Predicate<UUID> istArmee = u -> false;

    private ArmeeFilter()
    {}
}
