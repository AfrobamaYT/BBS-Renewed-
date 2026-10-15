package mchorse.bbs_mod.ui.dashboard.panels;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A top-bar tab that is not a dashboard panel: an add-on view laid over one (DaVinci Resolve's
 * tab, task 109). {@link UITopbarNano} draws these after the panel tabs while {@code visible}.
 *
 * <p>While one is {@code active} it, not the panel underneath, carries the highlight; a click on
 * any panel tab first calls {@code leave}, so the panel tabs always lead back to the panels.</p>
 */
public record TopbarReiter(Supplier<String> name, BooleanSupplier visible, BooleanSupplier active, Runnable open, Runnable leave)
{
    public static final List<TopbarReiter> ALL = new CopyOnWriteArrayList<>();

    /** Everything the bar shows of these tabs; when it changes, the bar repaints. */
    static String state()
    {
        StringBuilder state = new StringBuilder();

        for (TopbarReiter reiter : ALL)
        {
            state.append(reiter.visible().getAsBoolean()).append(reiter.active().getAsBoolean()).append(reiter.name().get());
        }

        return state.toString();
    }

    static boolean anyActive()
    {
        for (TopbarReiter reiter : ALL)
        {
            if (reiter.visible().getAsBoolean() && reiter.active().getAsBoolean())
            {
                return true;
            }
        }

        return false;
    }
}
