package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.ArrayList;
import java.util.List;

/**
 * What gets drawn over the finished frame: the images, the subtitles, and whatever an addon adds.
 * Both the game and the editor's preview ask this, so a new family is one entry, not two copies.
 * From BBS FS 33de579fa (Wemppy4/bbs-fs, MIT).
 */
public class FrameOverlays
{
    private static final List<IFrameOverlayRenderer> RENDERERS = new ArrayList<>();

    static
    {
        register((batcher, context, frame) -> UIImageRenderer.renderImages(batcher, ImageClip.getImages(context), frame.x, frame.y, frame.w, frame.h));
        register((batcher, context, frame) -> UISubtitleRenderer.renderSubtitles(batcher, SubtitleClip.getSubtitles(context), frame));
    }

    public static void register(IFrameOverlayRenderer renderer)
    {
        RENDERERS.add(renderer);
    }

    /** {@code frame}: where the frame sits in the GUI, the preview in the editor or the whole screen in the game */
    public static void render(Batcher2D batcher, ClipContext context, mchorse.bbs_mod.ui.utils.Area frame)
    {
        for (IFrameOverlayRenderer renderer : RENDERERS)
        {
            renderer.render(batcher, context, frame);
        }
    }

    public static interface IFrameOverlayRenderer
    {
        public void render(Batcher2D batcher, ClipContext context, mchorse.bbs_mod.ui.utils.Area frame);
    }
}
