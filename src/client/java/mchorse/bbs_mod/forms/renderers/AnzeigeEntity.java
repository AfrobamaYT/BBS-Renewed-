package mchorse.bbs_mod.forms.renderers;

import net.minecraft.world.entity.Entity;

import java.util.concurrent.atomic.AtomicInteger;


public final class AnzeigeEntity
{
    /** Zaehlt abwaerts, damit keine Kennung mit einer echten Entity zusammenfaellt. */
    private static final AtomicInteger NAECHSTE = new AtomicInteger(-1);

    /**
     * Gibt der Entity eine Kennung, falls sie noch keine hat.
     *
     * <p>{@code null} ist erlaubt und wird still uebergangen -- die Aufrufer pruefen ihre
     * Entity ohnehin, und eine zweite Ausnahme an dieser Stelle waere nur Rauschen.
     */
    public static void kennungGeben(Entity entity)
    {
        if (entity != null)
        {
            entity.setId(NAECHSTE.getAndDecrement());
        }
    }

    private AnzeigeEntity()
    {}
}
