package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * P8c-Pilot: die Widgets kommen aus {@link UIFormPropFactory} — Verdrahtung,
 * Range, Drag-Feinheit und startEdit-Sync stecken in der Value-Definition
 * ({@link LabelForm}) bzw. der Factory; hier steht nur noch Layout.
 */
public class UILabelFormPanel extends UIFormPanel<LabelForm>
{
    private final UIFormPropFactory props = new UIFormPropFactory();

    public UITextbox text;
    public UIToggle billboard;
    public UIColor color;
    public UITrackpad max;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UIToggle anchorLines;

    public UITrackpad shadowX;
    public UITrackpad shadowY;
    public UIColor shadowColor;

    public UIColor background;
    public UITrackpad offset;

    public UILabelFormPanel(UIForm editor)
    {
        super(editor);

        this.text = this.props.text(() -> this.form.text);
        this.billboard = this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard);
        this.color = this.props.color(() -> this.form.color);
        this.max = this.props.zahl(() -> this.form.max);
        this.anchorX = this.props.zahl(() -> this.form.anchorX);
        this.anchorY = this.props.zahl(() -> this.form.anchorY);
        this.anchorLines = this.props.toggle(UIKeys.FORMS_EDITORS_LABEL_ANCHOR_LINES, () -> this.form.anchorLines);

        this.shadowX = this.props.zahl(() -> this.form.shadowX);
        this.shadowY = this.props.zahl(() -> this.form.shadowY);
        this.shadowColor = this.props.color(() -> this.form.shadowColor);

        this.background = this.props.color(() -> this.form.background);
        this.offset = this.props.zahl(() -> this.form.offset);

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL), this.text, this.billboard, this.color, this.max);

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_ANCHOR).marginTop(UIConstants.SECTION_GAP), UI.row(this.anchorX, this.anchorY), this.anchorLines);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_SHADOW_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(this.shadowX, this.shadowY));
        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_LABEL_SHADOW_COLOR, this.shadowColor).marginTop(UIConstants.SECTION_GAP));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_BACKGROUND).marginTop(UIConstants.SECTION_GAP), this.background, this.offset);
    }

    @Override
    public void startEdit(LabelForm form)
    {
        super.startEdit(form);

        this.props.sync();
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.color.picker.removeFromParent();
        this.shadowColor.picker.removeFromParent();
        this.background.picker.removeFromParent();
    }
}
