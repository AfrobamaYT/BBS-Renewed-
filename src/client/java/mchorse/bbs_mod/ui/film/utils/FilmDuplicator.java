package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import java.util.Map;

/**
 * Baut aus einem vorhandenen Film einen neuen, der dessen Zustand zu EINEM Zeitpunkt einfriert.
 *
 * <p>Das ist keine Kopie: der neue Film bekommt eine frische Kamera-Fahrt und von jedem Replay nur
 * den an der Cursor-Position interpolierten Wert als einzelnes Keyframe. Aus einer Aufnahme wird
 * damit ein Standbild, auf dem man weiterbaut — genau das, was der Nutzer beim Duplizieren aus der
 * Film-Liste erwartet.</p>
 *
 * <p>Steht als eigene Klasse und nicht im {@code UIFilmPanel}, weil hier nichts an der Oberflaeche
 * haengt: rein Daten rein, Daten raus. Das macht den Schritt einzeln pruefbar und haelt das Panel
 * bei seiner eigentlichen Aufgabe.</p>
 */
public final class FilmDuplicator
{
    private FilmDuplicator() {}

    /**
     * @param name   Id des neuen Films.
     * @param source Vorlage; wird nicht veraendert.
     * @param tick   Zeitpunkt, an dem die Replays eingefroren werden.
     * @param camera Kamera-Stand, aus dem der einzelne Idle-Clip gebaut wird.
     */
    public static Film duplicate(String name, Film source, int tick, Camera camera)
    {
        Film data = new Film();
        Position position = new Position();
        IdleClip idle = new IdleClip();

        position.set(camera);
        idle.duration.set(BBSSettings.getDefaultDuration());
        idle.position.set(position);
        data.camera.addClip(idle);
        data.setId(name);
        data.stampCreationTimeNow();

        for (Replay replay : source.replays.getList())
        {
            /* A frozen scene has a new timeline: do not turn its old timing helpers into actors. */
            if (mchorse.bbs_mod.film.markers.TimerCueActions.isEndpoint(source, replay))
            {
                continue;
            }

            data.replays.add(freezeReplay(replay, tick));
        }

        return data;
    }

    /** Ein Replay auf seinen Zustand zum Zeitpunkt {@code tick} eindampfen. */
    private static Replay freezeReplay(Replay replay, int tick)
    {
        Replay copy = new Replay(replay.getId());

        copy.form.set(FormUtils.copy(replay.form.get()));

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (!channel.isEmpty())
            {
                KeyframeChannel newChannel = (KeyframeChannel) copy.keyframes.get(channel.getId());

                newChannel.insert(0, channel.interpolate(tick));
            }
        }

        for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet())
        {
            KeyframeChannel channel = entry.getValue();

            if (channel.isEmpty())
            {
                continue;
            }

            KeyframeChannel newChannel = new KeyframeChannel(channel.getId(), channel.getFactory());
            KeyframeSegment segment = channel.find(tick);

            if (segment != null)
            {
                newChannel.insert(0, segment.createInterpolated());
            }

            if (!newChannel.isEmpty())
            {
                /* Beide Eintragungen sind noetig: die Map ist der Zugriff nach Namen, add() haengt
                 * den Kanal zusaetzlich in die Wertehierarchie, damit er gespeichert wird. */
                copy.properties.properties.put(newChannel.getId(), newChannel);
                copy.properties.add(newChannel);
            }
        }

        return copy;
    }
}
