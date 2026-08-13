package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleColorPresets;
import mchorse.bbs_mod.ui.forms.editors.utils.UIParticleSettings;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;

public class UIVanillaParticleFormPanel extends UIFormPanel<VanillaParticleForm>
{
    private final mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory props = new mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory();

    public UIParticleSettings settings;
    public UIToggle paused;
    public UIToggle local;
    public UITrackpad velocity;
    public UITrackpad count;
    public UITrackpad frequency;
    public UITrackpad scatteringYaw;
    public UITrackpad scatteringPitch;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad offsetZ;
    public UIParticleColorPresets colorPresets;

    public UIVanillaParticleFormPanel(UIForm editor)
    {
        super(editor);

        this.settings = new UIParticleSettings();
        this.colorPresets = new UIParticleColorPresets((args) -> this.settings.setArgumentsText(args));
        this.settings.callback((s) -> this.colorPresets.update(s));
        this.paused = this.props.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_PAUSED, () -> this.form.paused);
        this.local = this.props.toggle(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_LOCAL, () -> this.form.local);
        this.velocity = this.props.zahl(() -> this.form.velocity);
        this.count = this.props.zahl(() -> this.form.count);
        this.count.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_COUNT);
        this.frequency = this.props.zahl(() -> this.form.frequency);
        this.frequency.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_FREQUENCY);
        this.scatteringYaw = this.props.zahl(() -> this.form.scatteringYaw);
        this.scatteringYaw.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_HORIZONTAL);
        this.scatteringPitch = this.props.zahl(() -> this.form.scatteringPitch);
        this.scatteringPitch.tooltip(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VERTICAL);
        this.offsetX = this.props.zahl(() -> this.form.offsetX);
        this.offsetX.tooltip(UIKeys.GENERAL_X);
        this.offsetY = this.props.zahl(() -> this.form.offsetY);
        this.offsetY.tooltip(UIKeys.GENERAL_Y);
        this.offsetZ = this.props.zahl(() -> this.form.offsetZ);
        this.offsetZ.tooltip(UIKeys.GENERAL_Z);

        this.options.add(this.settings, this.paused.marginTop(UIConstants.SECTION_GAP), this.local, UI.labelRow(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_VELOCITY, this.velocity).marginTop(UIConstants.SECTION_GAP));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EMISSION).marginTop(UIConstants.SECTION_GAP), UI.row(this.count, this.frequency));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_SCATTER).marginTop(UIConstants.SECTION_GAP), UI.row(this.scatteringYaw, this.scatteringPitch));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(this.offsetX, this.offsetY, this.offsetZ));

        /* Last in the column on purpose: the block is hidden for every particle but
         * entity_effect, and the column resizer still reserves the row of a hidden child —
         * at the bottom of the scroll view that gap costs nothing. */
        this.options.add(this.colorPresets.marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void startEdit(VanillaParticleForm form)
    {
        super.startEdit(form);

        this.settings.setSettings(form.settings.get());
        this.props.sync();
        this.colorPresets.update(form.settings.get());
    }
}