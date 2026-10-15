package mchorse.bbs_mod.film.regie;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

import java.util.UUID;

/**
 * Ein Regie-Marker: wo man in einem Zeitfenster stehen (Ort) oder wohin man schauen (Blick)
 * soll. Zeit in Film-Ticks (20/s) wie der Rest von BBS.
 */
public class RegieMarker extends ValueGroup
{
    public static final int ORT = 0;
    public static final int BLICK = 1;

    public final ValueString uuid = new ValueString("uuid", "");
    public final ValueInt art = new ValueInt("art", ORT, ORT, BLICK);
    public final ValueString name = new ValueString("name", "");
    public final ValueInt farbe = new ValueInt("farbe", 0xFF000000 | RegieFarben.PALETTE[0]).colorAlpha();
    public final ValueInt start = new ValueInt("start", 0, 0, Integer.MAX_VALUE);
    public final ValueInt ende = new ValueInt("ende", 80, 0, Integer.MAX_VALUE);
    public final ValueDouble x = new ValueDouble("x", 0D);
    public final ValueDouble y = new ValueDouble("y", 0D);
    public final ValueDouble z = new ValueDouble("z", 0D);
    /** Blick-Marker: UUID des Replays, das angeschaut wird. Leer heisst: der Punkt x/y/z. */
    public final ValueString blickZiel = new ValueString("blick_ziel", "");

    public RegieMarker(String id)
    {
        super(id);

        this.add(this.uuid);
        this.add(this.art);
        this.add(this.name);
        this.add(this.farbe);
        this.add(this.start);
        this.add(this.ende);
        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.blickZiel);
        this.uuid.set(UUID.randomUUID().toString());
    }

    public boolean istOrt()
    {
        return this.art.get() == ORT;
    }

    /** Liegt der Tick im Zeitfenster? Ende ist ausschliesslich, wie bei Clips. */
    public boolean aktivBei(int tick)
    {
        return tick >= this.start.get() && tick < this.ende.get();
    }
}
