package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;

public class Anchor implements IMapSerializable
{
    public static final int NO_ATTACHMENT = -1;

    public int replay = NO_ATTACHMENT;
    public String attachment = "";

    /**
     * Which components of the target's frame the anchored form rides, as body parts do. All three on
     * (the default) takes the target's matrix whole; a component not inherited comes from the frame
     * the form would have had unanchored. They replace the older translate/scale pair, which meant
     * opposite things (take ONLY the position / take all BUT the scale); old files are converted on
     * read (BBS FS 14f2ef586).
     */
    public boolean inheritPosition = true;
    public boolean inheritRotation = true;
    public boolean inheritScale = true;

    /**
     * How much of the target's motion the anchored form's bone physics feels, 0..1. At 1 (the default)
     * the physics lives in the world like an unanchored form's: a car driving off drags a passenger's
     * hair behind. Below it the chains ride the target's frame along with the rest of that share, so the
     * hair hangs still in the moving car (BBS FS 89b786ca6).
     */
    public float physics = 1F;
    public final Transform transform = new Transform();

    /* Interpolation data */
    public Anchor previous;
    public float x;

    public Anchor()
    {}

    public Anchor(int replay, String attachment, boolean inheritPosition, boolean inheritRotation, boolean inheritScale)
    {
        this.replay = replay;
        this.attachment = attachment;
        this.inheritPosition = inheritPosition;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
    }

    public boolean inheritsWholeTarget()
    {
        return this.inheritPosition && this.inheritRotation && this.inheritScale;
    }

    /** The target matrix with what this anchor does not inherit taken from {@code fallback}, the unanchored frame */
    public Matrix4f filterMatrix(Matrix4f matrix, Matrix4f fallback)
    {
        if (matrix == null || fallback == null || this.inheritsWholeTarget())
        {
            return matrix;
        }

        return Matrices.compose(this.inheritPosition ? matrix : fallback, this.inheritRotation ? matrix : fallback, this.inheritScale ? matrix : fallback);
    }

    public boolean isFadeIn()
    {
        return this.previous != null && this.replay != Anchor.NO_ATTACHMENT && this.previous.replay == Anchor.NO_ATTACHMENT;
    }

    public boolean isFadeOut()
    {
        return this.previous != null && this.replay == Anchor.NO_ATTACHMENT && this.previous.replay != Anchor.NO_ATTACHMENT;
    }

    public boolean hasSameTarget(Anchor anchor)
    {
        return anchor != null
            && this.replay == anchor.replay
            && this.attachment.equals(anchor.attachment)
            && this.inheritPosition == anchor.inheritPosition
            && this.inheritRotation == anchor.inheritRotation
            && this.inheritScale == anchor.inheritScale;
    }

    public Anchor copy()
    {
        Anchor anchor = new Anchor(this.replay, this.attachment, this.inheritPosition, this.inheritRotation, this.inheritScale);

        anchor.physics = this.physics;
        anchor.transform.copy(this.transform);

        return anchor;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (super.equals(obj))
        {
            return true;
        }

        if (obj instanceof Anchor anchor)
        {
            return this.hasSameTarget(anchor) && this.physics == anchor.physics
                && this.transform.equals(anchor.transform);
        }

        return false;
    }

    @Override
    public void fromData(MapType data)
    {
        this.replay = data.getInt("actor");
        this.attachment = data.getString("attachment");

        if (data.has("inheritPosition") || data.has("inheritRotation") || data.has("inheritScale"))
        {
            this.inheritPosition = data.getBool("inheritPosition", true);
            this.inheritRotation = data.getBool("inheritRotation", true);
            this.inheritScale = data.getBool("inheritScale", true);
        }
        else
        {
            /* The old pair: translate kept only the position, scale dropped only the scale */
            boolean translate = data.getBool("translate", false);

            this.inheritPosition = true;
            this.inheritRotation = !translate;
            this.inheritScale = !translate && !data.getBool("scale", false);
        }

        this.physics = data.getFloat("physics", 1F);

        if (data.has("transform"))
        {
            this.transform.fromData(data.getMap("transform"));
        }
        else
        {
            this.transform.identity();
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.putInt("actor", this.replay);
        data.putString("attachment", this.attachment);
        data.putBool("inheritPosition", this.inheritPosition);
        data.putBool("inheritRotation", this.inheritRotation);
        data.putBool("inheritScale", this.inheritScale);

        if (this.physics != 1F)
        {
            data.putFloat("physics", this.physics);
        }

        if (!this.transform.isDefault())
        {
            data.put("transform", this.transform.toData());
        }
    }
}
