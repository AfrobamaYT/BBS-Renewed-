package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.IPlaceableClip;
import mchorse.bbs_mod.camera.data.Placement;
import mchorse.bbs_mod.settings.values.core.ValuePlacement;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * A video file over the frame: the picture placed the way an image clip's is, the sound through
 * the inherited audio clip ({@link #audio} names the video file itself). From BBS FS 3efe13ebc
 * and fb2c638c3 (Wemppy4/bbs-fs, MIT).
 */
public class VideoClip extends AudioClip implements IPlaceableClip
{
    public ValuePlacement placement = new ValuePlacement("placement", new Placement());
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean loop = new ValueBoolean("loop", false);
    public ValueBoolean fullscreen = new ValueBoolean("fullscreen", false);
    public ValueBoolean smooth = new ValueBoolean("smooth", true);
    public ValueTransform transform = new ValueTransform("transform", new Transform());

    protected ImageOverlay overlay = new ImageOverlay();

    public VideoClip()
    {
        super();

        this.add(this.placement);
        this.add(this.color);
        this.add(this.loop);
        this.add(this.fullscreen);
        this.add(this.smooth);
        this.add(this.transform);
    }

    @Override
    public ValuePlacement getPlacement()
    {
        /* Nothing to move while it covers the frame */
        return this.fullscreen.get() ? null : this.placement;
    }

    @Override
    public OverlayBox getOverlayBox()
    {
        return this.overlay.box;
    }

    @Override
    protected Clip create()
    {
        return new VideoClip();
    }
}
