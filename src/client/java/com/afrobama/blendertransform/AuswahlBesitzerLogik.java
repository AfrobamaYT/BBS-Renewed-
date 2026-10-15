package com.afrobama.blendertransform;

import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;

/**
 * Logik hinter {@code LivingEntityUpdateMixin}, herausgeloest damit sie hotswapbar ist.
 *
 * <p>Haelt den Auswahl-Besitzer einer Entity aktuell — {@code baseTick} ist der Takt, in dem
 * BBS' Selektoren nachziehen.</p>
 *
 * <p>⚠️ LAEUFT JE ENTITY UND JE TICK. Was hier hineinwaechst, kostet in einer Welt mit vielen
 * Entities sofort spuerbar. Die Bruecke macht genau das aenderbar, ohne dafuer booten zu
 * muessen — was bei einem Pfad dieser Frequenz der eigentliche Gewinn ist.</p>
 */
public final class AuswahlBesitzerLogik
{
    private AuswahlBesitzerLogik() {}

    /**
     * @param entity die tickende Entity; kommt als Argument, weil der Cast auf
     *               {@link ISelectorOwnerProvider} den gewobenen {@code this} braucht
     */
    public static void besitzerAktualisieren(Object entity)
    {
        if (entity instanceof ISelectorOwnerProvider anbieter)
        {
            anbieter.getOwner().update();
        }
    }
}
