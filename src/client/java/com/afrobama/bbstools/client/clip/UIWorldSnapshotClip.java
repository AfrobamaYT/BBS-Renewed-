package com.afrobama.bbstools.client.clip;

import com.afrobama.bbstools.clips.WorldSnapshotClip;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;

/** Complete field editor for native world snapshot capture and restoration. */
public class UIWorldSnapshotClip extends UIActionClip<WorldSnapshotClip>
{
    private UITrackpad minX;
    private UITrackpad minY;
    private UITrackpad minZ;
    private UITrackpad maxX;
    private UITrackpad maxY;
    private UITrackpad maxZ;
    private UITextbox file;
    private UIToggle capture;
    private UIToggle blockEntities;
    private UIToggle diffOnly;

    private static IKey key(String name)
    {
        return BBSModClient.getL10n().getKey("bbs.ui.actions.snapshot." + name);
    }

    public UIWorldSnapshotClip(WorldSnapshotClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private UITrackpad coordinate(ValueInt value)
    {
        UITrackpad pad = new UITrackpad(number -> this.editor.editMultiple(value, field -> field.set(number.intValue())));
        pad.integer();
        return pad;
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
        this.file = new UITextbox(256, text -> this.editor.editMultiple(this.clip.snapshotFile, field -> field.set(text)));
        this.capture = new UIToggle(key("capture"), toggle -> this.editor.editMultiple(this.clip.capture, field -> field.set(toggle.getValue())));
        this.blockEntities = new UIToggle(key("block_entities"), toggle -> this.editor.editMultiple(this.clip.restoreBlockEntities, field -> field.set(toggle.getValue())));
        this.diffOnly = new UIToggle(key("diff_only"), toggle -> this.editor.editMultiple(this.clip.diffOnly, field -> field.set(toggle.getValue())));
        this.file.tooltip(key("file_hint"));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();
        this.panels.add(this.section(key("region_min"), UI.row(this.minX, this.minY, this.minZ)));
        this.panels.add(this.section(key("region_max"), UI.row(this.maxX, this.maxY, this.maxZ)));
        this.panels.add(this.section(key("file"), this.file, this.capture, this.blockEntities, this.diffOnly));
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
        this.file.setText(this.clip.snapshotFile.get());
        this.capture.setValue(this.clip.capture.get());
        this.blockEntities.setValue(this.clip.restoreBlockEntities.get());
        this.diffOnly.setValue(this.clip.diffOnly.get());
    }
}
