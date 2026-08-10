package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;

/**
 * Logik hinter dem Trefferflaechen-Handler in {@code LivingEntityMixin}: traegt eine Gestalt
 * eine eigene Trefferflaeche, gilt sie statt der des Vanilla-Mobs.
 *
 * <p>⚠️ DAS ERSETZT EINEN RUECKGABEWERT. Der Handler haengt an {@code getDefaultDimensions}
 * @RETURN und ist {@code cancellable}: was hier zurueckkommt, ist die Trefferflaeche. Ein
 * {@code null} heisst "nichts aendern" — der Vanilla-Wert bleibt dann stehen.</p>
 *
 * <p>⚠️ FEST ODER SKALIERBAR IST KEINE FORMSACHE. Ob die Flaeche mitwaechst, entscheidet der
 * VANILLA-Wert ({@code dimensions.fixed()}), nicht die Gestalt. Wer hier pauschal
 * {@code fixed} nimmt, bricht die Skalierung bei allem, was waechst — Babys, Slimes.</p>
 */
public final class MorphTrefferflaecheLogik
{
    private MorphTrefferflaecheLogik() {}

    /**
     * @param traeger   der gewobene {@code this}
     * @param vanilla   was Vanilla ausgerechnet hat
     * @return die neue Trefferflaeche, oder {@code null} wenn Vanillas gelten soll
     */
    public static EntityDimensions trefferflaeche(Object traeger, EntityDimensions vanilla)
    {
        if (!(traeger instanceof IMorphProvider anbieter) || !(traeger instanceof LivingEntity wesen))
        {
            return null;
        }

        Form form = anbieter.getMorph().getForm();

        if (form == null || !form.hitbox.get() || vanilla == null)
        {
            return null;
        }

        /* Geduckt wird die Gestalt gestaucht — derselbe Multiplikator wie im Vanilla-Verhalten,
         * aber auf die Hoehe der Gestalt statt auf die des Mobs. */
        float hoehe = form.hitboxHeight.get()
            * (wesen.isShiftKeyDown() ? form.hitboxSneakMultiplier.get() : 1F);

        return vanilla.fixed()
            ? EntityDimensions.fixed(form.hitboxWidth.get(), hoehe)
            : EntityDimensions.scalable(form.hitboxWidth.get(), hoehe);
    }
}
