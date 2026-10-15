package mchorse.bbs_mod.film.regie;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;

/** Die Regie-Marker eines Films; Aenderungen laufen ueber pre/postNotify und damit ins Undo. */
public class RegieMarkerListe extends ValueList<RegieMarker>
{
    public RegieMarkerListe(String id)
    {
        super(id);
    }

    /**
     * Neuen Marker anlegen; die Farbe wird wie im Design automatisch vergeben.
     *
     * @param art {@link RegieMarker#ORT} oder {@link RegieMarker#BLICK}
     */
    public RegieMarker neu(int art, String name, int start, int ende, double x, double y, double z)
    {
        if (ende <= start)
        {
            throw new IllegalArgumentException("Empty time window: " + start + "–" + ende);
        }

        RegieMarker marker = new RegieMarker(String.valueOf(this.list.size()));

        marker.art.set(art);
        marker.name.set(name);
        marker.start.set(start);
        marker.ende.set(ende);
        marker.x.set(x);
        marker.y.set(y);
        marker.z.set(z);
        marker.farbe.set(RegieFarben.naechste(this.list, start, ende, null));

        this.preNotify();
        this.add(marker);
        this.postNotify();

        return marker;
    }

    public void entfernen(RegieMarker marker)
    {
        int index = CollectionUtils.getIndex(this.list, marker);

        if (CollectionUtils.inRange(this.list, index))
        {
            this.preNotify();
            this.list.remove(index);
            this.sync();
            this.postNotify();
        }
    }

    public RegieMarker mitUuid(String uuid)
    {
        for (RegieMarker marker : this.list)
        {
            if (marker.uuid.get().equals(uuid))
            {
                return marker;
            }
        }

        return null;
    }

    @Override
    protected RegieMarker create(String id)
    {
        return new RegieMarker(id);
    }
}
