package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UISpurSuche;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Invisible overlay that captures Ctrl+Z and Ctrl+Y for undo/redo
 * so they work on top of all other keybinds in the film panel (e.g. Y for body fix toggle).
 */
public class UIFilmPanelUndoKeys extends UIElement
{
    public UIFilmPanelUndoKeys(UIFilmPanel panel)
    {
        this.keys().ignoreFocus();
        this.keys().register(Keys.UNDO, panel::undo).category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        this.keys().register(Keys.REDO, panel::redo).category(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE);
        /* Here, above the timeline's own keys, so it opens wherever the pointer is (BBS FS 0c1708ac7) */
        this.keys().register(Keys.FILM_TRACK_SEARCH, () -> UISpurSuche.oeffnen(this.getContext(), panel.replayEditor.keyframeEditor.view))
            .active(() -> panel.replayEditor.isVisible() && panel.replayEditor.keyframeEditor != null && panel.replayEditor.getReplay() != null && !this.ueberPose())
            .category(UIKeys.FILM_REPLAY_TITLE);
        this.noCulling();
    }

    /** Over a pose editor Ctrl+Shift+F flips the pose instead, as Ctrl+F searches its bones there (BBS FS 5c38791ea) */
    private boolean ueberPose()
    {
        return this.getContext().menu.main.getChildren(mchorse.bbs_mod.ui.utils.pose.UIPoseEditor.class).stream()
            .anyMatch((editor) -> editor.isVisible() && editor.area.isInside(this.getContext()) && editor.groups.hasBones());
    }
}
