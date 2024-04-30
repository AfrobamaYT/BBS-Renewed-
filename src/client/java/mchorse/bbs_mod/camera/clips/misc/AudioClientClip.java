package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

public class AudioClientClip extends AudioClip
{
    public AudioClientClip()
    {
        super();
    }

    @Override
    public boolean isGlobal()
    {
        return true;
    }

    @Override
    public void shutdown(ClipContext context)
    {
        Link link = this.audio.get();

        if (link != null)
        {
            BBSModClient.getSounds().stop(link);
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        Link link = this.audio.get();

        if (link != null)
        {
            SoundPlayer player = BBSModClient.getSounds().playUnique(link);

            if (player == null)
            {
                return;
            }

            player.setRelative(true);

            float tickTime = (context.relativeTick + context.transition) / 20F;
            float time = player.getPlaybackPosition();

            if (tickTime >= player.getBuffer().getDuration() || context.relativeTick >= this.duration.get())
            {
                if (!player.isStopped())
                {
                    player.stop();
                }

                return;
            }

            if (player.isStopped())
            {
                player.setPlaybackPosition(0);
                player.play();
            }

            if (player.isPlaying() && !context.playing)
            {
                player.pause();
            }
            else if (player.isPaused() && context.playing)
            {
                player.play();
            }

            float diff = Math.abs(tickTime - time);

            if (diff > 0.05F)
            {
                player.setPlaybackPosition(tickTime);
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new AudioClientClip();
    }
}