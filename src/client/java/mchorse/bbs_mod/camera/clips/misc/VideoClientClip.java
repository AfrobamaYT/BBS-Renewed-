package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.video.VideoTextures;
import mchorse.bbs_mod.video.VideoTon;

/**
 * The video clip on the client: its frame through the video form's decoders, put on screen as an
 * image overlay, and its extracted sound through the audio clip's path. Global like an audio clip,
 * so the sound stays paused and in sync outside the clip; the picture shows only inside it. From
 * BBS FS 3efe13ebc and fb2c638c3 (Wemppy4/bbs-fs, MIT).
 */
public class VideoClientClip extends VideoClip
{
    @Override
    public boolean isGlobal()
    {
        return true;
    }

    /** The video's path for the decoders: an assets link is relative to the assets folder */
    public String getVideoPath()
    {
        Link link = this.audio.get();

        return link == null ? null : "assets".equals(link.source) ? link.path : link.toString();
    }

    @Override
    public void shutdown(ClipContext context)
    {
        BBSModClient.getSounds().stopOwned(this);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        String path = this.getVideoPath();

        if (path == null)
        {
            return;
        }

        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);
        float tickTime = (context.relativeTick + context.transition) / 20F;
        double length = VideoTextures.seconds(path);
        boolean inside = tickTime >= 0F && context.relativeTick < this.duration.get();

        AudioClientClip.abspielen(this, VideoTon.link(path), context, this.volume.get() * factor);

        if (!inside)
        {
            return;
        }

        double seconds = TimeUtils.toSeconds(this.offset.get()) + tickTime;

        if (this.loop.get() && length > 0)
        {
            seconds = ((seconds % length) + length) % length;
        }

        Link frame = VideoTextures.frame(path, seconds, this.loop.get(), this.overlay);

        if (frame == null)
        {
            return;
        }

        this.overlay.update(frame, this.placement.get(), Colors.setA(this.color.get(), factor * Colors.getA(this.color.get())), this.fullscreen.get(), this.smooth.get());
        this.overlay.updateTransform(this.transform.get(), factor);
        ImageClip.getImages(context).add(this.overlay);
    }

    @Override
    protected Clip create()
    {
        return new VideoClientClip();
    }
}
