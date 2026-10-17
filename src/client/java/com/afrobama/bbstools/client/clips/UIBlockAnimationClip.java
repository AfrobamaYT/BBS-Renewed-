package com.afrobama.bbstools.client.clips;

import com.afrobama.bbstools.clip.BlockAnimationActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

/** Region, translation and duration controls for both saved Block Animation IDs. */
public final class UIBlockAnimationClip extends UIActionClip<BlockAnimationActionClip>
{
    public UITrackpad minX, minY, minZ, maxX, maxY, maxZ;
    public UITrackpad offsetX, offsetY, offsetZ, seconds;

    public UIBlockAnimationClip(BlockAnimationActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
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
        this.offsetX = this.decimal(this.clip.offsetX);
        this.offsetY = this.decimal(this.clip.offsetY);
        this.offsetZ = this.decimal(this.clip.offsetZ);
        this.seconds = this.decimal(this.clip.animDuration);
        this.seconds.limit(0.1D, 30D);
        this.addBlockPositionContext(this.minX, this.minY, this.minZ,
            this.clip.minX::get, this.clip.minY::get, this.clip.minZ::get,
            value -> this.editor.editMultiple(this.clip.minX, value),
            value -> this.editor.editMultiple(this.clip.minY, value),
            value -> this.editor.editMultiple(this.clip.minZ, value));
        this.addBlockPositionContext(this.maxX, this.maxY, this.maxZ,
            this.clip.maxX::get, this.clip.maxY::get, this.clip.maxZ::get,
            value -> this.editor.editMultiple(this.clip.maxX, value),
            value -> this.editor.editMultiple(this.clip.maxY, value),
            value -> this.editor.editMultiple(this.clip.maxZ, value));
    }

    private UITrackpad coordinate(ValueInt property)
    {
        UITrackpad field = new UITrackpad(value -> this.editor.editMultiple(property, value.intValue()));
        field.integer();
        return field;
    }

    private UITrackpad decimal(ValueFloat property)
    {
        UITrackpad field = new UITrackpad(value -> this.editor.editMultiple(property, target -> target.set(value.floatValue())));
        field.increment(0.1D);
        return field;
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();
        this.panels.add(this.section(IKey.constant("First corner"), UI.row(this.minX, this.minY, this.minZ)));
        this.panels.add(this.section(IKey.constant("Second corner"), UI.row(this.maxX, this.maxY, this.maxZ)));
        this.panels.add(this.section(IKey.constant("Offset X / Y / Z"), UI.row(this.offsetX, this.offsetY, this.offsetZ)));
        this.panels.add(this.section(IKey.constant("Seconds"), this.seconds));
        this.seconds.tooltip(IKey.constant("Moves rendered copies; the world blocks remain unchanged."));
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
