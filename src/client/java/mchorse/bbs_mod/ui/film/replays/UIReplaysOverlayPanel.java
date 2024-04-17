package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

import java.util.function.Consumer;

public class UIReplaysOverlayPanel extends UIOverlayPanel
{
    public UIReplayList replays;

    private Consumer<Replay> callback;

    public UIReplaysOverlayPanel(UIFilmPanel filmPanel, Consumer<Replay> callback)
    {
        super(UIKeys.FILM_REPLAY_TITLE);

        this.callback = callback;

        this.replays = new UIReplayList((l) -> this.callback.accept(l.get(0)), filmPanel);
        this.replays.relative(this.content).full();

        this.content.add(this.replays);
    }
}