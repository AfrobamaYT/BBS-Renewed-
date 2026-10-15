package mchorse.bbs_mod.film.regie;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * Regiehilfe eines Films: Startpunkt mit Vorlauf und die Regie-Marker.
 *
 * <p>Der Startpunkt ist der Ort, an dem jeder Versuch beginnt; der Trail fuehrt von dort
 * durch die Ort-Marker. Der Vorlauf laesst die Figuren schon vor Aufnahmebeginn laufen.</p>
 */
public class Regie extends ValueGroup
{
    /** Ist ein Startpunkt gesetzt? Ohne ihn beginnt der Trail beim ersten Ort-Marker. */
    public final ValueBoolean startGesetzt = new ValueBoolean("start_gesetzt", false);
    public final ValueDouble startX = new ValueDouble("start_x", 0D);
    public final ValueDouble startY = new ValueDouble("start_y", 0D);
    public final ValueDouble startZ = new ValueDouble("start_z", 0D);
    public final ValueFloat startYaw = new ValueFloat("start_yaw", 0F);
    /** Ab diesem Tick zaehlt die Aufnahme; Vorlauf davor. */
    public final ValueInt anfang = new ValueInt("anfang", 0, 0, Integer.MAX_VALUE);
    /** Vorlauf in Ticks (Vorgabe 2 s wie im Design). */
    public final ValueInt vorlauf = new ValueInt("vorlauf", 40, 0, 20 * 30);
    public final RegieMarkerListe marker = new RegieMarkerListe("marker");

    public Regie(String id)
    {
        super(id);

        this.add(this.startGesetzt);
        this.add(this.startX);
        this.add(this.startY);
        this.add(this.startZ);
        this.add(this.startYaw);
        this.add(this.anfang);
        this.add(this.vorlauf);
        this.add(this.marker);
    }
}
