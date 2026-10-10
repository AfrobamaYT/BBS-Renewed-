package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.TimerCue;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** Native field layout, kept separate from cue selection and film transaction handling. */
public class UITimerCueFields extends UIElement
{
    public final UITextbox name;
    public final UITrackpad start;
    public final UITrackpad end;
    public final UIIcon startNow;
    public final UIIcon endNow;
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
        this.name.tooltip(IKey.raw("Timer name"));
        this.startNow = new UIIcon(Icons.POINTER, (button) -> panel.usePlayhead(false));
        this.endNow = new UIIcon(Icons.POINTER, (button) -> panel.usePlayhead(true));
        this.startNow.tooltip(IKey.raw("Use playhead for START"));
        this.endNow.tooltip(IKey.raw("Use playhead for END"));
        this.startNow.wh(20, 20);
        this.endNow.wh(20, 20);
        this.groundRoute = new UIToggle(IKey.raw("Walking guide"),
            (button) -> panel.edit((cue) -> cue.groundRoute.set(button.getValue())));
        this.selectStart = new UIButton(IKey.raw("Select START"), (button) -> panel.selectEndpoint(false));
        this.selectEnd = new UIButton(IKey.raw("Select END"), (button) -> panel.selectEndpoint(true));
        this.remove = new UIButton(IKey.raw("Delete timer pair"), (button) -> panel.removeCue());

        this.column(3).vertical().stretch();
        this.add(this.name, new UILabel(IKey.raw("Start (seconds)")).h(12), UI.row(2, this.start, this.startNow),
            new UILabel(IKey.raw("End (seconds)")).h(12), UI.row(2, this.end, this.endNow),
            this.groundRoute, UI.row(3, this.selectStart, this.selectEnd), this.remove);
    }

    public void fill(TimerCue cue)
    {
        if (!this.name.isFocused())
        {
            this.name.setText(cue.label.get());
        }
        this.start.setValue(cue.startTick.get() / 20D);
        this.end.setValue(cue.endTick.get() / 20D);
        this.groundRoute.setValue(cue.groundRoute.get());
        this.selectStart.color(cue.startColor.get());
        this.selectEnd.color(cue.endColor.get());
    }
}
