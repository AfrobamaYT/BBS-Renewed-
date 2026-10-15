package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UIGeneralFormPanel extends UIFormPanel
{
    private final mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory props = new mchorse.bbs_mod.ui.forms.editors.UIFormPropFactory();

    public UIKeybind hotkey;

    public UIToggle visible;
    public UIButton filterTracks;
    public UIToggle boneTracks;
    
    public UITextbox trackName;
    public UIToggle lighting;
    public UIToggle shaderShadow;
    public UIToggle additiveColor;
    public UITrackpad uiScale;
    public UITextbox name;
    public UIPropTransform transform;

    public UIToggle hitbox;
    public UITrackpad hitboxWidth;
    public UITrackpad hitboxHeight;
    public UISliderTrackpad hitboxSneakMultiplier;
    public UISliderTrackpad hitboxEyeHeight;

    public UITrackpad hp;
    public UITrackpad speed;
    public UITrackpad stepHeight;

    public UIGeneralFormPanel(UIForm editor)
    {
        super(editor);

        this.hotkey = new UIKeybind((combo) ->
        {
            this.form.hotkey.set(combo.keys.isEmpty() ? 0 : combo.keys.get(0));
        });
        this.hotkey.single().tooltip(UIKeys.FORMS_EDITORS_GENERAL_HOTKEY);

        this.visible = this.props.toggle(UIKeys.FORMS_EDITORS_GENERAL_VISIBLE, () -> this.form.visible);
        this.filterTracks = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS, (b) -> this.openTrackFilter());
        this.filterTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS_TOOLTIP);
        this.boneTracks = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS, (b) ->
        {
            if (this.form instanceof ModelForm m) m.boneTracks.set(b.getValue());
        });
        this.boneTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS_TOOLTIP);
        this.trackName = this.props.text(() -> this.form.trackName);
        this.trackName.tooltip(UIKeys.FORMS_EDITORS_GENERAL_TRACK_NAME_TOOLTIP);
        this.lighting = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, (b) -> this.form.lighting.set(b.getValue() ? 1F : 0F));
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING_TOOLTIP);
        this.shaderShadow = this.props.toggle(UIKeys.FORMS_EDITORS_GENERAL_SHADER_SHADOW, () -> this.form.shaderShadow);
        this.additiveColor = this.props.toggle(UIKeys.FORMS_EDITORS_ADDITIVE_COLOR, () -> this.form.additiveColor);
        this.uiScale = this.props.zahl(() -> this.form.uiScale);
        this.name = this.props.text(() -> this.form.name);

        this.transform = new UIPropTransform().callbacks(() -> this.form.transform).barBackground();
        this.transform.enableHotkeys();

        this.hitbox = this.props.toggle(UIKeys.CAMERA_PANELS_ENABLED, () -> this.form.hitbox);
        this.hitboxWidth = this.props.zahl(() -> this.form.hitboxWidth);
        this.hitboxWidth.tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_WIDTH);
        this.hitboxHeight = this.props.zahl(() -> this.form.hitboxHeight);
        this.hitboxHeight.tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_HEIGHT);
        this.hitboxSneakMultiplier = this.props.schieber(() -> this.form.hitboxSneakMultiplier);
        this.hitboxEyeHeight = this.props.schieber(() -> this.form.hitboxEyeHeight);

        this.hp = this.props.zahl(() -> this.form.hp);
        this.speed = this.props.zahl(() -> this.form.speed);
        this.stepHeight = this.props.zahl(() -> this.form.stepHeight);

        UISection display = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_DISPLAY);

        display.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_DISPLAY, this.name),
            this.hotkey, this.visible,
            this.lighting, this.shaderShadow, this.additiveColor,
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_UI_SCALE, this.uiScale)
        );

        UISection tracks = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRACKS);

        tracks.fields.add(this.filterTracks, this.boneTracks, this.trackName);

        UISection transform = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRANSFORM);

        transform.fields.add(this.transform);

        UISection hitbox = new UISection(UIKeys.FORMS_EDITORS_GENERAL_HITBOX);

        hitbox.fields.add(
            this.hitbox,
            UI.row(this.hitboxWidth, this.hitboxHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_SNEAK_MULTIPLIER, this.hitboxSneakMultiplier),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_EYE_HEIGHT, this.hitboxEyeHeight)
        );

        UISection movement = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_MOVEMENT);

        movement.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HP, this.hp),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED, this.speed.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_STEP_HEIGHT, this.stepHeight)
        );

        this.options.add(
            display,
            tracks,
            transform,
            hitbox,
            movement
        );
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        this.hotkey.setKeyCombo(new KeyCombo(IKey.EMPTY, form.hotkey.get()));

        this.props.sync();

        if (form instanceof ModelForm m)
        {
            this.boneTracks.setValue(m.boneTracks.get());
            this.boneTracks.setVisible(true);
        }
        else
        {
            this.boneTracks.setVisible(false);
        }
        this.lighting.setValue(form.lighting.get() > 0F);
        this.transform.setTransform(form.transform.get());



        this.options.resize();
    }

    private void openTrackFilter()
    {
        if (this.form == null)
        {
            return;
        }

        Set<String> disabled = this.form.disabledTracks.get();
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> keyToColor = new HashMap<>();

        for (UIKeyframeSheet sheet : UIReplaysEditorUtils.collectFormTrackSheets(this.form))
        {
            String key = UIReplaysEditor.getSheetFilterKey(sheet);

            keys.add(key);
            keyToColor.put(key, sheet.color);
        }

        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(disabled, keys, keyToColor);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose(e -> this.form.disabledTracks.set(disabled));
    }
}