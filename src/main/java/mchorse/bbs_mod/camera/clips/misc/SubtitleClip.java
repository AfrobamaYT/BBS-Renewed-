package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.settings.values.core.ValueLink;

import java.util.ArrayList;
import java.util.List;

/** A subtitle over the frame, placed like the image and video clips (BBS FS 7f19dfbd5, 33de579fa) */
public class SubtitleClip extends CameraClip implements mchorse.bbs_mod.camera.clips.IPlaceableClip
{
    /** Old subtitles scaled their text 10x by default; in frame units that is 20 */
    public static final float DEFAULT_SCALE = 20F;

    public mchorse.bbs_mod.settings.values.core.ValuePlacement placement = new mchorse.bbs_mod.settings.values.core.ValuePlacement("placement", new mchorse.bbs_mod.camera.data.Placement(DEFAULT_SCALE));
    public ValueInt color = new ValueInt("color", Colors.WHITE);
    public ValueBoolean textShadow = new ValueBoolean("textShadow", true);
    public ValueInt background = new ValueInt("background", 0);
    public ValueFloat backgroundOffset = new ValueFloat("backgroundOffset", 2F);
    public ValueFloat shadow = new ValueFloat("shadow", 0F);
    public ValueBoolean shadowOpaque = new ValueBoolean("shadowOpaque", false);
    public ValueTransform transform = new ValueTransform("transform", new Transform());
    public ValueInt lineHeight = new ValueInt("lineHeight", 12);
    public ValueInt maxWidth = new ValueInt("maxWidth", 0);
    /* Font: a TrueType file in the assets, empty for Minecraft's own one (BBS FS fd189d58f) */
    public ValueLink font = new ValueLink("font", null);
    public ValueInt fontSize = new ValueInt("fontSize", 9);
    public ValueLink image = new ValueLink("image", null);
    public ValueBoolean imageRight = new ValueBoolean("imageRight", true);
    public ValueFloat imageScale = new ValueFloat("imageScale", 1F);

    private Subtitle subtitle = new Subtitle();

    public static List<Subtitle> getSubtitles(ClipContext context)
    {
        return context.clipData.get("subtitles", ArrayList::new);
    }

    @Override
    public mchorse.bbs_mod.settings.values.core.ValuePlacement getPlacement()
    {
        return this.placement;
    }

    @Override
    public OverlayBox getOverlayBox()
    {
        return this.subtitle.box;
    }

    /**
     * Subtitles used to carry flat half-framebuffer-pixel fields; their presence marks data from
     * before placement, converted with the values doubled (see Placement.fromLegacy), the transform's
     * translation included.
     */
    @Override
    public void fromData(mchorse.bbs_mod.data.types.BaseType data)
    {
        super.fromData(data);

        if (data.isMap() && (data.asMap().has("windowX") || data.asMap().has("x") || data.asMap().has("size")))
        {
            this.placement.set(mchorse.bbs_mod.camera.data.Placement.fromLegacy(data.asMap(), "size", 10F));

            Transform transform = this.transform.get();

            transform.translate.x *= 2F;
            transform.translate.y *= 2F;
        }
    }

    public SubtitleClip()
    {
        this.add(this.placement);
        this.add(this.color);
        this.add(this.textShadow);
        this.add(this.background);
        this.add(this.backgroundOffset);
        this.add(this.shadow);
        this.add(this.shadowOpaque);
        this.add(this.transform);
        this.add(this.lineHeight);
        this.add(this.maxWidth);
        this.add(this.font);
        this.add(this.fontSize);
        this.add(this.image);
        this.add(this.imageRight);
        this.add(this.imageScale);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<Subtitle> subtitles = getSubtitles(context);
        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);
        int color = Colors.setA(this.color.get(), factor * Colors.getA(this.color.get()));

        this.subtitle.update(this.title.get(), this.placement.get(), color, this.textShadow.get());
        this.subtitle.updateBackground(this.background.get(), this.backgroundOffset.get(), this.shadow.get(), this.shadowOpaque.get());
        this.subtitle.updateTransform(this.transform.get(), factor);
        this.subtitle.updateConstraints(this.lineHeight.get(), this.maxWidth.get());
        this.subtitle.updateFont(this.font.get(), this.fontSize.get());
        this.subtitle.updateImage(this.image.get(), this.imageRight.get(), this.imageScale.get());
        subtitles.add(this.subtitle);
    }

    @Override
    protected Clip create()
    {
        return new SubtitleClip();
    }
}
