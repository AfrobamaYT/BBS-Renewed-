package mchorse.bbs_mod.film.regie;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** Per-film presentation. Existing loaded Regie objects need no new JVM fields. */
public final class RegieDarstellung extends ValueGroup
{
    public final ValueInt stil = new ValueInt("style", 0, 0, 1);
    public final ValueBoolean zeit = new ValueBoolean("time_bar", true);
    public final ValueBoolean text = new ValueBoolean("marker_text", false);

    private RegieDarstellung()
    {
        super("display");
        this.add(this.stil);
        this.add(this.zeit);
        this.add(this.text);
    }

    /** Attach only when constructing a Film or editing its presentation, never while drawing. */
    public static RegieDarstellung fuer(Regie regie)
    {
        RegieDarstellung wert = vorhanden(regie);

        if (wert == null)
        {
            wert = new RegieDarstellung();
            regie.add(wert);
        }

        return wert;
    }

    private static RegieDarstellung vorhanden(Regie regie)
    {
        if (regie.get("display") == null) return null;
        if (regie.get("display") instanceof RegieDarstellung wert) return wert;
        throw new IllegalStateException("Unexpected recording-guide display settings");
    }

    public static boolean einfach(Regie regie)
    {
        RegieDarstellung wert = vorhanden(regie);
        return wert == null || wert.stil.get() == 0;
    }

    public static boolean zeit(Regie regie)
    {
        RegieDarstellung wert = vorhanden(regie);
        return wert == null || wert.zeit.get();
    }

    public static boolean text(Regie regie)
    {
        RegieDarstellung wert = vorhanden(regie);
        return wert != null && wert.text.get();
    }
}
