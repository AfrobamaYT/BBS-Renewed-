package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.RemapperClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UICameraDopeSheetEditor;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIRemapperClip extends UIClip<RemapperClip>
{
    public UICameraDopeSheetEditor channel;
    public UIButton editChannel;

    public UIRemapperClip(RemapperClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.channel = new UICameraDopeSheetEditor(this.editor);

        this.editChannel = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.channel);
            this.channel.resetView();
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UIClip.label(UIKeys.C_CLIP.get("bbs:remapper")).marginTop(12), this.editChannel);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.channel.properties.setDuration(this.clip.duration.get());
        this.channel.setChannel(this.clip.channel, Colors.ACTIVE);
    }

    @Override
    public void updateDuration(int duration)
    {
        super.updateDuration(duration);

        this.channel.properties.setDuration(duration);
    }
}