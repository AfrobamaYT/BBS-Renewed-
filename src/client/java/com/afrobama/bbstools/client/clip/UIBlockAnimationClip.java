package com.afrobama.bbstools.client.clip;

import com.afrobama.bbstools.clip.BlockAnimationActionClip;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UIBlockAnimationClip extends UIActionClip<BlockAnimationActionClip>
{
    private UITrackpad minX;
    private UITrackpad minY;
    private UITrackpad minZ;
    private UITrackpad maxX;
    private UITrackpad maxY;
    private UITrackpad maxZ;
    private UITrackpad offsetX;
    private UITrackpad offsetY;
    private UITrackpad offsetZ;
    private UITrackpad seconds;

    private static IKey key(String name)
    {
        return BBSModClient.getL10n().getKey("bbs.ui.actions.block_animation." + name);
    }

    public UIBlockAnimationClip(BlockAnimationActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private UITrackpad coordinate(ValueInt value)
    {
        UITrackpad pad = new UITrackpad(number -> this.editor.editMultiple(value, field -> field.set(number.intValue())));
        pad.integer();
        return pad;
    }

    private UITrackpad number(ValueFloat value)
    {
        return new UITrackpad(number -> this.editor.editMultiple(value, field -> field.set(number.floatValue())));
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();
        this.minX = this.coordinate(this.clip.minX);
        this.minY = this.coordinate(this.clip.minY);
        this.minZ = this.coordinate(this.clip.minZ);
        this.maxX = this.coordinate(this.clip.maxX);
        this.maxY = this.coordinate(this.clip.maxY);
        this.maxZ = this.coordinate(this.clip.maxZ);
        this.offsetX = this.number(this.clip.offsetX);
        this.offsetY = this.number(this.clip.offsetY);
        this.offsetZ = this.number(this.clip.offsetZ);
        this.seconds = this.number(this.clip.animDuration);
        this.seconds.limit(0.1D, 30D);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();
        this.panels.add(this.section(key("min"), UI.row(this.minX, this.minY, this.minZ)));
        this.panels.add(this.section(key("max"), UI.row(this.maxX, this.maxY, this.maxZ)));
        this.panels.add(this.section(key("offset"), UI.row(this.offsetX, this.offsetY, this.offsetZ)));
        this.panels.add(this.section(key("seconds"), this.seconds));
    }

    @Override
    public void fillData()
    {
        super.fillData();
        this.minX.setValue(this.clip.minX.get());
        this.minY.setValue(this.clip.minY.get());
        this.minZ.setValue(this.clip.minZ.get());
        this.maxX.setValue(this.clip.maxX.get());
        this.maxY.setValue(this.clip.maxY.get());
        this.maxZ.setValue(this.clip.maxZ.get());
        this.offsetX.setValue(this.clip.offsetX.get());
        this.offsetY.setValue(this.clip.offsetY.get());
        this.offsetZ.setValue(this.clip.offsetZ.get());
        this.seconds.setValue(this.clip.animDuration.get());
    }
}
