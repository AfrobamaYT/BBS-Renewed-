package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.utils.CollectionUtils;

/**
 * Alle Merkstellen eines Films.
 *
 * <p>Aufbau wie {@link mchorse.bbs_mod.film.replays.Replays}: eine Werteliste, deren
 * Änderungen über {@code preNotify}/{@code postNotify} laufen, damit sie im Undo-Stapel
 * landen und beim Speichern des Films mitgeschrieben werden.</p>
 */
public class Markers extends ValueList<Marker>
{
    public Markers(String id)
    {
        super(id);
    }

    /**
     * Eine Merkstelle an diesem Tick anlegen. Steht dort schon eine, wird KEINE zweite
     * gesetzt — sonst stapeln sich unsichtbar Marker aufeinander, wenn das Kürzel zweimal
     * gedrückt wird.
     *
     * @return die neue Merkstelle, oder die bereits vorhandene an diesem Tick
     */
    public Marker setzeBei(int tick)
    {
        Marker vorhanden = this.bei(tick, 0);

        if (vorhanden != null)
        {
            return vorhanden;
        }

        Marker marker = new Marker(String.valueOf(this.list.size()));

        marker.tick.set(Math.max(0, tick));

        this.preNotify();
        this.add(marker);
        this.postNotify();

        return marker;
    }

    public void remove(Marker marker)
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

    /**
     * Die Merkstelle am Tick, mit Toleranz.
     *
     * @param toleranz erlaubter Abstand in Ticks; 0 heißt genau dieser Tick
     */
    public Marker bei(int tick, int toleranz)
    {
        for (Marker marker : this.list)
        {
            if (Math.abs(marker.tick.get() - tick) <= toleranz)
            {
                return marker;
            }
        }

        return null;
    }

    @Override
    protected Marker create(String id)
    {
        return new Marker(id);
    }
}
