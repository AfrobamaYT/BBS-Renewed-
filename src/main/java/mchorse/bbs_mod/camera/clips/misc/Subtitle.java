package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;

public class Subtitle
{
    public String label = "";
    public mchorse.bbs_mod.camera.data.Placement placement;
    public int color;
    public boolean textShadow;
    public int backgroundColor;
    public float backgroundOffset;
    public float shadow;
    public boolean shadowOpaque;

    public Transform transform;
    public float factor;

    /** Where the subtitle ended up in the last drawn frame, for the placement gizmo (BBS FS 33de579fa) */
    public final OverlayBox box = new OverlayBox();

    /** A TrueType font from the assets and its layout size, null for Minecraft's own (BBS FS fd189d58f) */
    public Link font;
    public int fontSize;

    public int lineHeight;
    public int maxWidth;

    public Link image;
    public boolean imageRight;
    public float imageScale;

    public void update(String label, mchorse.bbs_mod.camera.data.Placement placement, int color, boolean textShadow)
    {
        this.label = label;
        this.placement = placement;
        this.color = color;
        this.textShadow = textShadow;
    }

    public void updateBackground(int backgroundColor, float backgroundOffset, float shadow, boolean shadowOpaque)
    {
        this.backgroundColor = backgroundColor;
        this.backgroundOffset = backgroundOffset;
        this.shadow = shadow;
        this.shadowOpaque = shadowOpaque;
    }

    public void updateTransform(Transform transform, float factor)
    {
        this.transform = transform;
        this.factor = factor;
    }

    public void updateConstraints(int lineHeight, int maxWidth)
    {
        this.lineHeight = lineHeight;
        this.maxWidth = maxWidth;
    }

    public void updateFont(Link font, int fontSize)
    {
        this.font = font;
        this.fontSize = fontSize;
    }

    public void updateImage(Link image, boolean imageRight, float imageScale)
    {
        this.image = image;
        this.imageRight = imageRight;
        this.imageScale = imageScale;
    }
}
