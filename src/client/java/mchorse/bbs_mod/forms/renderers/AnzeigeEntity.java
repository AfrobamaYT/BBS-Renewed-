package mchorse.bbs_mod.forms.renderers;

import net.minecraft.world.entity.Entity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vergibt Kennungen an Wegwerf-Entities, die nur zum ANZEIGEN erzeugt werden.
 *
 * <h2>Warum das noetig ist (2026-07-31, reproduzierbarer Absturz)</h2>
 *
 * <p>BBS erzeugt an drei Stellen Entities, die nie in die Welt kommen -- sie dienen nur als
 * Vorlage zum Zeichnen einer Form. Solche Entities bekommen keine Kennung, weil die sonst
 * beim Einfuegen in die Welt vergeben wird.
 *
 * <p>Bis 1.21.1 war das folgenlos. In 26.2 liest der Zeichenweg die Kennung:
 *
 * <pre>
 * IllegalStateException: Tried to access entity ID before ID assignment
 *   Entity.getId -> ItemModelResolver.updateForLiving -> LivingEntityRenderer.extractRenderState
 *   -> EntityRenderDispatcher.extractEntity -> MobFormRenderer.entityEinreichen
 * </pre>
 *
 * <p>Der Client stirbt damit beim OEFFNEN DES MORPHING-PANELS, sobald die Form-Palette eine
 * Mob-Form zeichnen will -- dreimal von drei Versuchen. Gefunden hat es kein Screenshot,
 * sondern eine Uebergangs-Pruefung, die nach jeder Aktion fragt, ob die Instanz noch lebt.
 *
 * <h2>Warum negative Kennungen</h2>
 *
 * <p>Vanilla vergibt aufsteigend ab 1. Ein absteigender eigener Zaehler kann darum mit
 * keiner echten Entity kollidieren -- wichtig, weil Code, der Entities nach Kennung
 * nachschlaegt, sonst die falsche faende.
 *
 * <p>⚠️ ALLE DREI WEGWERF-PFADE MUESSEN HIER DURCH: {@code MobFormRenderer},
 * {@code EntityExportBridge.createPosingEntity} und {@code ReplayPoseReader.createPosingEntity}.
 * Ihre Klassendocs verlangen ohnehin, dass sie denselben Aufruf benutzen; wer einen vergisst,
 * bekommt denselben Absturz an anderer Stelle wieder.
 */
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
