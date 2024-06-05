package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UICameraDopeSheetEditor;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

public class UIKeyframeClip extends UIClip<KeyframeClip>
{
    public UIButton edit;
    public UICameraDopeSheetEditor dope;

    public UIKeyframeClip(KeyframeClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.dope = new UICameraDopeSheetEditor(this.editor);

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.dope);
            this.dope.resetView();
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UIClip.label(UIKeys.CAMERA_PANELS_KEYFRAMES).marginTop(12));
        this.panels.add(this.edit);
    }

    @Override
    public void updateDuration(int duration)
    {
        super.updateDuration(duration);

        this.dope.updateConverter();
        this.dope.properties.setDuration(duration);
    }

    @Override
    public void editClip(Position position)
    {
        long tick = this.editor.getCursor() - this.clip.tick.get();

        this.clip.x.insert(tick, position.point.x);
        this.clip.y.insert(tick, position.point.y);
        this.clip.z.insert(tick, position.point.z);
        this.clip.yaw.insert(tick, (double) position.angle.yaw);
        this.clip.pitch.insert(tick, (double) position.angle.pitch);
        this.clip.roll.insert(tick, (double) position.angle.roll);
        this.clip.fov.insert(tick, (double) position.angle.fov);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateDuration(this.clip.duration.get());
        this.dope.setClip(this.clip);
    }
}