package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

public class UIBillboardFormPanel extends UIFormPanel<BillboardForm>
{
    private final mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory props = new mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory();

    public UIButton pick;
    public UIToggle billboard;
    public UIToggle linear;
    public UIToggle mipmap;

    public UIButton openCrop;
    public UIToggle resizeCrop;
    public UIColor color;

    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad rotation;

    public UIToggle shading;

    public UIBillboardFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        this.billboard = this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard);
        this.linear = this.props.toggle(UIKeys.TEXTURES_LINEAR, () -> this.form.linear);
        this.mipmap = this.props.toggle(UIKeys.TEXTURES_MIPMAP, () -> this.form.mipmap);
        this.openCrop = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_EDIT_CROP, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UICropOverlayPanel(this.form.texture.get(), this.form.crop.get()), 0.5F, 0.5F);
        });
        this.resizeCrop = this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_RESIZE_CROP, () -> this.form.resizeCrop);
        this.color = this.props.color(() -> this.form.color).direction(Direction.LEFT);

        this.offsetX = this.props.zahl(() -> this.form.offsetX);
        this.offsetX.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_X);
        this.offsetY = this.props.zahl(() -> this.form.offsetY);
        this.offsetY.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_Y);
        this.rotation = this.props.zahl(() -> this.form.rotation);
        this.rotation.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_ROTATION);

        this.shading = this.props.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_SHADING, () -> this.form.shading);

        this.options.add(this.pick, this.color, this.billboard, this.linear, this.mipmap);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_CROP).marginTop(UIConstants.SECTION_GAP), this.openCrop, this.resizeCrop);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_UV_SHIFT).marginTop(UIConstants.SECTION_GAP), UI.row(this.offsetX, this.offsetY), this.rotation, this.shading);
    }

    @Override
    public void startEdit(BillboardForm form)
    {
        super.startEdit(form);

        this.props.sync();
    }
}