package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioClientClip extends AudioClip
{
    static final class Playback
    {
        final Link link;
        final float seconds;
        final float gain;

        /**
         * Weltposition der schallquelle, oder {@code null} fuer "nicht raeumlich".
         *
         * <p>Wird beim anwenden des clips aufgeloest, nicht beim abspielen: nur dort
         * ist der actor-kontext ({@link CameraClipContext#entities}) erreichbar.</p>
         */
        final Vector3f position;

        Playback(Link link, float seconds, float gain, Vector3f position)
        {
            this.link = link;
            this.seconds = seconds;
            this.gain = gain;
            this.position = position;
        }
    }

    public AudioClientClip()
    {
        super();
    }

    /** Keyed by the CLIP, so two clips of the same file each keep their own playback (BBS FS dcf49cf95) */
    public static Map<Object, Playback> getPlayback(ClipContext context)
    {
        return context.clipData.get("audio", ConcurrentHashMap::new);
    }

    public static void manageSounds(ClipContext context)
    {
        Map<Object, Playback> playback = getPlayback(context);
        boolean muteFilmAudioDuringVideoCapture = BBSSettings.videoMuteAudioWhileRender.get()
            && BBSModClient.getVideoRecorder().isRecording()
            || mchorse.bbs_mod.ui.film.audio.UIAudioRecorder.mutesTimeline()
            /* Playing along with DaVinci Resolve: Resolve plays the sound, BBS would double it. */
            || com.davincilink.DavinciTabState.followsResolve;

        for (Map.Entry<Object, Playback> entry : playback.entrySet())
        {
            Playback state = entry.getValue();
            float tickTime = state.seconds;
            SoundPlayer player = BBSModClient.getSounds().playUnique(entry.getKey(), state.link, state.position == null);

            if (player == null)
            {
                continue;
            }

            player.setVolume(muteFilmAudioDuringVideoCapture ? 0F : state.gain);
            applySpatial(player, state);

            if (tickTime < 0 || tickTime >= player.getBuffer().getDuration())
            {
                if (player.isPlaying())
                {
                    player.pause();
                }

                continue;
            }

            float time = player.getPlaybackPosition();
            float diff = Math.abs(tickTime - time);

            if (context.playing && !player.isPlaying())
            {
                player.play();
            }
            else if (!context.playing && player.isPlaying())
            {
                player.pause();
            }

            if (diff > 0.05F)
            {
                player.setPlaybackPosition(tickTime);
            }
        }
    }

    /**
     * Eine OpenAL-quelle ist entweder LISTENER-RELATIV — ihre position ist dann ein offset
     * zum hoerer, und 0/0/0 heisst "direkt im kopf", also gar kein raum — oder absolut in
     * weltkoordinaten. Film-audio laeuft standardmaessig relativ (durchgehender soundtrack);
     * nur der raeumliche modus dreht das um.
     *
     * <p>Muss JEDE frame laufen, nicht nur beim erzeugen der quelle: ein unique-player
     * ueberlebt den moduswechsel am clip, und im actor-modus wandert die position.</p>
     */
    private static void applySpatial(SoundPlayer player, Playback state)
    {
        if (state.position == null)
        {
            player.setRelative(true);
            player.setPosition(0F, 0F, 0F);
        }
        else
        {
            player.setRelative(false);
            player.setPosition(state.position);
        }
    }

    @Override
    public boolean isGlobal()
    {
        return true;
    }

    @Override
    public void shutdown(ClipContext context)
    {
        BBSModClient.getSounds().stopOwned(this);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        abspielen(this, this.audio.get(), context, this.volume.get());
    }

    /** Plays {@code link} for {@code clip} at the clip's time; the video clip passes its extracted track */
    public static void abspielen(AudioClip clip, Link link, ClipContext context, float gain)
    {
        if (link != null)
        {
            /* Nicht "position" nennen — so heisst schon die kamera-position des clips */
            Vector3f source = resolveSpatialPosition(clip, context);
            SoundPlayer player = BBSModClient.getSounds().playUnique(clip, link, source == null);

            if (player == null)
            {
                return;
            }

            float tickTime = (context.relativeTick + context.transition) / 20F;
            Map<Object, Playback> playback = getPlayback(context);

            if (context.relativeTick >= clip.duration.get() || tickTime < 0)
            {
                playback.putIfAbsent(clip, new Playback(link, -1F, gain, source));
            }
            else
            {
                playback.put(clip, new Playback(link, TimeUtils.toSeconds(clip.offset.get()) + tickTime, gain, source));
            }
        }
    }

    /**
     * Wo die schallquelle diese frame steht, oder {@code null} wenn der clip nicht
     * raeumlich spielt.
     *
     * <p>Faellt bewusst auf {@code null} (= alt-verhalten, ton am hoerer) zurueck statt zu
     * verstummen, wenn der actor-modus ins leere zeigt: kein gewaehlter actor, ein
     * geloeschter replay, oder ein kontext ohne actor-liste (z.b. eine kamera-vorschau
     * ausserhalb des films). Ein stiller clip waere schwerer zu diagnostizieren als ein
     * nicht-raeumlicher.</p>
     */
    private static Vector3f resolveSpatialPosition(AudioClip clip, ClipContext context)
    {
        int mode = clip.spatial.get();

        if (mode == SPATIAL_WORLD)
        {
            Point point = clip.spatialPoint.get();

            return new Vector3f((float) point.x, (float) point.y, (float) point.z);
        }

        if (mode == SPATIAL_ACTOR && context instanceof CameraClipContext cameraContext)
        {
            int selector = clip.spatialSelector.get();
            IEntity entity = selector < 0 ? null : cameraContext.entities.get(selector);

            if (entity != null)
            {
                /* Zwischen den letzten beiden ticks interpolieren, sonst springt die
                 * schallquelle im 20-Hz-raster waehrend das bild mit voller framerate laeuft */
                float t = context.transition;

                return new Vector3f(
                    (float) (entity.getPrevX() + (entity.getX() - entity.getPrevX()) * t),
                    (float) (entity.getPrevY() + (entity.getY() - entity.getPrevY()) * t),
                    (float) (entity.getPrevZ() + (entity.getZ() - entity.getPrevZ()) * t)
                );
            }
        }

        return null;
    }

    @Override
    protected Clip create()
    {
        return new AudioClientClip();
    }
}