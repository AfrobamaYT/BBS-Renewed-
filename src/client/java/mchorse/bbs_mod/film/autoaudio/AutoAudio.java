package mchorse.bbs_mod.film.autoaudio;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto Audio — ein partikel-effekt bringt seinen Minecraft-ton mit.
 *
 * <p>Je {@code VanillaParticleForm} eines replays: ton suchen ({@link PartikelSoundKatalog}),
 * als datei bereitstellen ({@link VanillaSoundAuszug}), an jeder ZUENDSTELLE
 * ({@link AutoAudioZuendstellen}) einen normalen {@link AudioClip} in die kamera-spur legen.</p>
 *
 * <h2>⛔ WARUM ECHTE CLIPS UND KEINE EDITOR-VORSCHAU — die export-frage</h2>
 * Der naheliegende weg waere, den ton beim abspielen im editor einfach mitlaufen zu lassen.
 * Genau daran sind hier schon features gerissen: was der editor zusaetzlich tut, muss der
 * export ENTWEDER auch tun (dann steht im fertigen film ein ton, den der nutzer nie gesetzt
 * hat) ODER nicht tun (dann klingt die vorschau anders als das ergebnis). Beides ist falsch.
 *
 * <p>Deshalb gibt es hier keinen zweiten tonweg. Auto Audio erzeugt gewoehnliche
 * {@code AudioClip}s: sie stehen sichtbar in der zeitleiste, lassen sich anfassen,
 * verschieben, stummschalten und loeschen, wandern durch undo, werden mit dem film
 * gespeichert — und landen im export ueber exakt denselben pfad wie jeder handgesetzte ton
 * ({@code WorldVideoExportSession} sammelt {@code camera.getClips(AudioClip.class)}).
 * Vorschau und fertiger film sind damit dasselbe, per bauart und nicht per sorgfalt.</p>
 *
 * <p>Zweite haelfte derselben regel: Auto Audio laeuft NIE von selbst — ein automatismus,
 * der beim tippen toene in den film schriebe, waere genau der "nie gesetzte ton".</p>
 *
 * <h2>Wiederholbar statt stapelnd</h2>
 * Jeder erzeugte clip traegt in {@code AudioClip.autoAudio} seine herkunft
 * ({@code replay-uuid|form-pfad}); ein zweiter lauf raeumt GENAU diese clips weg und legt
 * neu an — clips ohne markierung werden nie angefasst. Der clip ist damit FEST im film und
 * wandert erst beim erneuten ausloesen: eine dauerhafte kopplung liesse handkorrekturen am
 * ton unangekuendigt verschwinden.
 */
public class AutoAudio
{
    /**
     * Was ein lauf getan hat — und vor allem, was er NICHT getan hat und warum.
     * Ein stiller fehlschlag ("nichts passiert, keine ahnung warum") ist hier der teuerste
     * fall, weil der nutzer den unterschied zu "kein ton noetig" nicht sehen kann.
     */
    public record Bericht(
        int angelegt,
        int entfernt,
        Set<String> ohneTon,
        Set<String> ohneDatei,
        boolean ohneEmitter,
        boolean abgeschnitten
    ) {
        public boolean leer()
        {
            return this.angelegt == 0 && this.entfernt == 0;
        }
    }

    private AutoAudio()
    {}

    /** Die markierung, an der ein lauf seine eigenen clips wiedererkennt. */
    public static String herkunft(Replay replay, String formPfad)
    {
        return replay.uuid.get() + "|" + formPfad;
    }

    public static Bericht anwenden(Film film, List<Replay> replays)
    {
        int angelegt = 0;
        int entfernt = 0;
        boolean abgeschnitten = false;
        boolean emitterGefunden = false;
        Set<String> ohneTon = new LinkedHashSet<>();
        Set<String> ohneDatei = new LinkedHashSet<>();
        int filmDauer = film.camera.calculateDuration();

        for (Replay replay : replays)
        {
            Form form = replay.form.get();

            if (form == null)
            {
                continue;
            }

            for (PartikelEmitterSuche.Emitter emitter : PartikelEmitterSuche.finden(form))
            {
                emitterGefunden = true;

                String markierung = herkunft(replay, emitter.pfad());

                /* Erst aufraeumen, dann anlegen — auch wenn danach kein ton gefunden wird.
                 * Sonst bliebe nach dem umstellen von "explosion" auf einen partikel ohne
                 * ton der ALTE explosionsknall im film stehen. */
                entfernt += alteEntfernen(film, markierung);

                /* ⚠ getOriginalValue, NICHT get — an BEIDEN stellen. Auch die
                 * partikel-einstellung ist keyframebar (ValueParticleSettings), und sobald
                 * der film im editor an einem tick steht, hat FormProperties den animierten
                 * wert als laufzeitwert gesetzt. Mit get() haette das ergebnis davon
                 * abgehangen, wo gerade der abspielkopf steht — derselbe lauf haette an
                 * verschiedenen ticks verschiedene toene erzeugt, ohne dass man sieht warum.
                 *
                 * GRENZE, bewusst: wer den partikel-TYP ueber die zeit keyframed, bekommt
                 * fuer alle zuendstellen den ton der grundeinstellung. Vorhersagbar und
                 * erklaerbar; ein ton je zuendstelle waere ein anderes feature. */
                ParticleSettings einstellung = emitter.formular().settings.getOriginalValue();
                Identifier soundId = PartikelSoundKatalog.finde(einstellung);

                if (soundId == null)
                {
                    ohneTon.add(String.valueOf(einstellung.particle));

                    continue;
                }

                Link link = VanillaSoundAuszug.holen(soundId);

                if (link == null)
                {
                    ohneDatei.add(soundId.toString());

                    continue;
                }

                /* Gleiche begruendung wie oben bei der partikel-einstellung: die ruhelage,
                 * nicht "paused an dem tick, an dem der abspielkopf gerade steht". */
                boolean ruhewert = emitter.formular().paused.getOriginalValue();
                KeyframeChannel<Boolean> kanal = pausedKanal(replay, emitter.pausedSchluessel());
                AutoAudioZuendstellen.Ergebnis zuendstellen =
                    AutoAudioZuendstellen.finden(kanal, ruhewert, filmDauer, replay.looping.get());

                abgeschnitten |= zuendstellen.abgeschnitten();

                int dauer = tonDauerInTicks(link);
                int index = film.replays.getList().indexOf(replay);
                String titel = "Auto Audio: " + einstellung.particle.getPath();

                for (int tick : zuendstellen.ticks())
                {
                    anlegen(film, link, tick, dauer, markierung, titel, index);

                    angelegt += 1;
                }
            }
        }

        return new Bericht(angelegt, entfernt, ohneTon, ohneDatei, !emitterGefunden, abgeschnitten);
    }

    /**
     * Entfernt alle clips eines FRUEHEREN laufs derselben quelle.
     *
     * <p>⚠ Erst sammeln, dann entfernen: {@code Clips.get()} ist eine sicht auf die echte
     * liste, und {@code remove} vergibt danach alle ids neu.</p>
     */
    private static int alteEntfernen(Film film, String markierung)
    {
        List<Clip> weg = new ArrayList<>();

        for (Clip clip : film.camera.get())
        {
            if (clip instanceof AudioClip audio && markierung.equals(audio.autoAudio.get()))
            {
                weg.add(clip);
            }
        }

        for (Clip clip : weg)
        {
            film.camera.remove(clip);
        }

        return weg.size();
    }

    private static void anlegen(Film film, Link link, int tick, int dauer, String markierung, String titel, int replayIndex)
    {
        Clip clip = film.camera.getFactory().create(Link.bbs("audio"));

        if (!(clip instanceof AudioClip audio))
        {
            return;
        }

        audio.audio.set(link);
        audio.tick.set(Math.max(0, tick));
        audio.duration.set(Math.max(1, dauer));
        audio.volume.set(BBSSettings.autoAudioVolume.get());
        audio.title.set(titel);
        audio.autoAudio.set(markierung);

        /* Der emitter sitzt am darsteller, also gehoert der ton dorthin — das ist dieselbe
         * raeumliche tonquelle, die der audio-clip seit dem raum-feature schon kann, hier
         * nur vorbelegt statt von hand gesetzt. Bei index < 0 (replay nicht in der liste)
         * bleibt der clip nicht-raeumlich statt auf einen falschen darsteller zu zeigen. */
        if (BBSSettings.autoAudioSpatial.get() && replayIndex >= 0)
        {
            audio.spatial.set(AudioClip.SPATIAL_ACTOR);
            audio.spatialSelector.set(replayIndex);
        }

        audio.layer.set(film.camera.findFreeLayer(audio));
        film.camera.addClip(audio);
    }

    /**
     * Volle laenge des tons in ticks. Faellt auf 20 ticks (eine sekunde) zurueck, wenn der
     * puffer gerade nicht ladbar ist — ein clip mit dauer 1 waere im editor ein unsichtbarer
     * strich und sieht aus wie ein bug.
     */
    private static int tonDauerInTicks(Link link)
    {
        try
        {
            SoundBuffer puffer = BBSModClient.getSounds().get(link, false);

            if (puffer != null)
            {
                return Math.max(1, (int) Math.ceil(puffer.getDuration() * 20F));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return 20;
    }

    @SuppressWarnings("unchecked")
    private static KeyframeChannel<Boolean> pausedKanal(Replay replay, String schluessel)
    {
        return replay.properties.properties.get(schluessel);
    }
}
