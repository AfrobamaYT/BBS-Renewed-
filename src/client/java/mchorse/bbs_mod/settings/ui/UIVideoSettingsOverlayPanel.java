package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.settings.values.ValueVideoSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIVideoSettingsOverlayPanel extends UIOverlayPanel
{
    private ValueVideoSettings value;

    private UIScrollView editor;
    private UITextbox arguments;
    private UITrackpad width;
    private UITrackpad height;
    private UITrackpad frameRate;
    private UITrackpad motionBlur;
    private UITextbox path;

    public UIVideoSettingsOverlayPanel(ValueVideoSettings value)
    {
        super(UIKeys.VIDEO_SETTINGS_TITLE);

        this.value = value;

        this.arguments = new UITextbox(1024, (s) -> this.value.arguments.set(s));
        this.width = new UITrackpad((v) -> this.value.width.set(v.intValue()));
        this.width.limit(2, 8096, true);
        this.width.tooltip(UIKeys.VIDEO_SETTINGS_WIDTH);
        this.height = new UITrackpad((v) -> this.value.height.set(v.intValue()));
        this.height.limit(2, 8096, true);
        this.height.tooltip(UIKeys.VIDEO_SETTINGS_HEIGHT);
        this.frameRate = new UITrackpad((v) -> this.value.frameRate.set(v.intValue()));
        this.frameRate.limit(10, 1000, true);
        this.motionBlur = new UITrackpad((v) -> this.value.motionBlur.set(v.intValue()));
        this.motionBlur.limit(0, 6, true);
        this.motionBlur.tooltip(UIKeys.VIDEO_SETTINGS_MOTION_BLUR_TOOLTIP);
        this.path = new UITextbox(1024, (s) -> this.value.path.set(s));

        this.editor = UI.scrollView(5, 6,
            UI.label(UIKeys.VIDEO_SETTINGS_ARGS),
            this.arguments,
            UI.label(UIKeys.VIDEO_SETTINGS_RESOLUTION).marginTop(6),
            UI.row(this.width, this.height),
            UI.label(UIKeys.VIDEO_SETTINGS_FRAME_RATE).marginTop(6),
            this.frameRate,
            UI.label(UIKeys.VIDEO_SETTINGS_MOTION_BLUR).marginTop(6),
            this.motionBlur,
            UI.label(UIKeys.VIDEO_SETTINGS_PATH).marginTop(6),
            this.path
        );
        this.editor.relative(this.content).full();

        this.content.add(this.editor);

        UIIcon icon = new UIIcon(Icons.FILM, (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_720p, () ->
                {
                    this.value.arguments.set(ValueVideoSettings.DEFAULT_FFMPEG_ARGUMENTS);
                    this.value.width.set(1280);
                    this.value.height.set(720);
                    this.value.frameRate.set(60);
                    this.fill();
                });

                menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1080P, () ->
                {
                    this.value.arguments.set(ValueVideoSettings.DEFAULT_FFMPEG_ARGUMENTS);
                    this.value.width.set(1920);
                    this.value.height.set(1080);
                    this.value.frameRate.set(60);
                    this.fill();
                });

                menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_SHORTS_1080P, () ->
                {
                    this.value.arguments.set(ValueVideoSettings.DEFAULT_FFMPEG_ARGUMENTS);
                    this.value.width.set(1080);
                    this.value.height.set(1920);
                    this.value.frameRate.set(60);
                    this.fill();
                });
            });
        });

        this.icons.add(icon);

        this.fill();
    }

    private void fill()
    {
        this.arguments.setText(this.value.arguments.get());
        this.width.setValue(this.value.width.get());
        this.height.setValue(this.value.height.get());
        this.frameRate.setValue(this.value.frameRate.get());
        this.motionBlur.setValue(this.value.motionBlur.get());
        this.path.setText(this.value.path.get());
    }
}