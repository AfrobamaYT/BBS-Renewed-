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

    public mchorse.bbs_mod.ui.framework.elements.buttons.UIButton pickFont;
    public mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon openFontFolder;
    public UITrackpad fontSize;
    public UITrackpad lineHeight;

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

        /* A TrueType font from the assets' fonts folder, its size and line step (BBS FS b0224f806) */
        mchorse.bbs_mod.l10n.keys.IKey pick = mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.forms.editors.label.font_pick");

        this.pickFont = new mchorse.bbs_mod.ui.framework.elements.buttons.UIButton(pick, (b) ->
        {
            mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel panel = mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel.links(pick, true, mchorse.bbs_mod.fonts.FontManager.getFontLinks(), (l) -> this.form.font.set(l));

            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(this.getContext(), panel.set(this.form.font.get()));
        });
        this.openFontFolder = new mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon(mchorse.bbs_mod.ui.utils.icons.Icons.FOLDER, (b) ->
        {
            java.io.File folder = mchorse.bbs_mod.fonts.FontManager.getFolder();

            folder.mkdirs();
            mchorse.bbs_mod.ui.utils.UIUtils.openFolder(folder);
        });
        this.fontSize = this.props.zahl(() -> this.form.fontSize);
        this.fontSize.limit(mchorse.bbs_mod.fonts.FontManager.MIN_SIZE, mchorse.bbs_mod.fonts.FontManager.MAX_SIZE, true).tooltip(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.forms.editors.label.font_size"));
        this.lineHeight = this.props.zahl(() -> this.form.lineHeight);
        this.lineHeight.limit(0, Integer.MAX_VALUE, true).tooltip(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.forms.editors.label.line_height"));

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL), this.text, this.billboard, this.color, this.max);

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_ANCHOR).marginTop(UIConstants.SECTION_GAP), UI.row(this.anchorX, this.anchorY), this.anchorLines);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_SHADOW_OFFSET).marginTop(UIConstants.SECTION_GAP), UI.row(this.shadowX, this.shadowY));
        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_LABEL_SHADOW_COLOR, this.shadowColor).marginTop(UIConstants.SECTION_GAP));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_BACKGROUND).marginTop(UIConstants.SECTION_GAP), this.background, this.offset);
        this.options.add(UI.label(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.forms.editors.label.font")).marginTop(UIConstants.SECTION_GAP), UI.row(this.pickFont, this.openFontFolder), UI.row(this.fontSize, this.lineHeight));
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
