package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.TimerCue;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

/** Native field layout, kept separate from cue selection and film transaction handling. */
public class UITimerCueFields extends UIElement
{
    public final UITextbox name;
    public final UITrackpad start;
    public final UITrackpad end;
    public final UIToggle enabled;
    public final UIToggle groundRoute;
    public final UIButton selectStart;
    public final UIButton selectEnd;
    public final UIButton remove;

    public UITimerCueFields(UITimerCuePanel panel)
    {
        this.name = new UITextbox(120, panel::rename);
        this.start = new UITrackpad((value) -> panel.setTime(false, value));
        this.end = new UITrackpad((value) -> panel.setTime(true, value));
        this.start.limit(0, Integer.MAX_VALUE / 20D).increment(0.05D);
        this.end.limit(0, Integer.MAX_VALUE / 20D).increment(0.05D);
        this.enabled = new UIToggle(IKey.raw("Show timer"), (button) -> panel.edit((cue) -> cue.enabled.set(button.getValue())));
        this.groundRoute = new UIToggle(IKey.raw("Ground route"), (button) -> panel.edit((cue) -> cue.groundRoute.set(button.getValue())));
        this.groundRoute.tooltip(IKey.raw("Walking guide to START, then END. Loaded terrain within 48 blocks; never included in video export."));
        this.selectStart = new UIButton(IKey.raw("Position START"), (button) -> panel.selectEndpoint(false));
        this.selectEnd = new UIButton(IKey.raw("Position END"), (button) -> panel.selectEndpoint(true));
        this.remove = new UIButton(IKey.raw("Delete timer pair"), (button) -> panel.removeCue());

        this.column(3).vertical().stretch();
        this.add(this.name, new UILabel(IKey.raw("Start (seconds)")).h(12), this.start,
            new UILabel(IKey.raw("End (seconds)")).h(12), this.end, this.enabled, this.groundRoute,
            this.selectStart, this.selectEnd, this.remove);
    }

    public void fill(TimerCue cue)
    {
        if (!this.name.isFocused())
        {
            this.name.setText(cue.label.get());
        }
        this.start.setValue(cue.startTick.get() / 20D);
        this.end.setValue(cue.endTick.get() / 20D);
        this.enabled.setValue(cue.enabled.get());
        this.groundRoute.setValue(cue.groundRoute.get());
        this.selectStart.color(cue.startColor.get());
        this.selectEnd.color(cue.endColor.get());
    }
}
