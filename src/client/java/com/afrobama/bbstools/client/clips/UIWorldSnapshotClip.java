package com.afrobama.bbstools.client.clips;

import com.afrobama.bbstools.clips.WorldSnapshotClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;

/** Configures the saved vanilla structure, inclusive region and restore policy. */
public final class UIWorldSnapshotClip extends UIActionClip<WorldSnapshotClip>
{
    public UITextbox snapshot;
    public UITrackpad minX, minY, minZ, maxX, maxY, maxZ;
    public UIToggle restoreBlockEntities, diffOnly;

    public UIWorldSnapshotClip(WorldSnapshotClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();
        this.snapshot = new UITextbox(256, value -> this.editor.editMultiple(this.clip.snapshotFile, target -> target.set(value)));
        this.minX = this.coordinate(this.clip.minX);
        this.minY = this.coordinate(this.clip.minY);
        this.minZ = this.coordinate(this.clip.minZ);
        this.maxX = this.coordinate(this.clip.maxX);
        this.maxY = this.coordinate(this.clip.maxY);
        this.maxZ = this.coordinate(this.clip.maxZ);
        this.restoreBlockEntities = new UIToggle(IKey.constant("Restore block entities"),
            value -> this.editor.editMultiple(this.clip.restoreBlockEntities, target -> target.set(value.getValue())));
        this.diffOnly = new UIToggle(IKey.constant("Only changed blocks"),
            value -> this.editor.editMultiple(this.clip.diffOnly, target -> target.set(value.getValue())));
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

    @Override
    protected void registerPanels()
    {
        super.registerPanels();
        this.panels.add(this.section(IKey.constant("Saved structure ID"), this.snapshot));
        this.snapshot.tooltip(IKey.constant("Save the snapshot with a vanilla structure block first."));
        this.panels.add(this.section(IKey.constant("First corner (X / Y / Z)"), UI.row(this.minX, this.minY, this.minZ)));
        this.panels.add(this.section(IKey.constant("Second corner (X / Y / Z)"), UI.row(this.maxX, this.maxY, this.maxZ)));
        this.panels.add(this.section(IKey.constant("Restore options"), this.restoreBlockEntities, this.diffOnly));
    }

    @Override
    public void fillData()
    {
        super.fillData();
        this.snapshot.setText(this.clip.snapshotFile.get());
        this.minX.setValue(this.clip.minX.get());
        this.minY.setValue(this.clip.minY.get());
        this.minZ.setValue(this.clip.minZ.get());
        this.maxX.setValue(this.clip.maxX.get());
        this.maxY.setValue(this.clip.maxY.get());
        this.maxZ.setValue(this.clip.maxZ.get());
        this.restoreBlockEntities.setValue(this.clip.restoreBlockEntities.get());
        this.diffOnly.setValue(this.clip.diffOnly.get());
    }
}
