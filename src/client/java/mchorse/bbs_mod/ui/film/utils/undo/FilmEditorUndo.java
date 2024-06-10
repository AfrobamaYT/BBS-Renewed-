package mchorse.bbs_mod.ui.film.utils.undo;

import mchorse.bbs_mod.settings.values.ValueGroup;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeState;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.ArrayList;
import java.util.List;

public abstract class FilmEditorUndo implements IUndo<ValueGroup>
{
    /* Timeline */
    public int tick;
    public ClipsData cameraClips;
    public ClipsData voiceLinesClips;
    public int panel;

    /* Replays */
    private KeyframeState propertiesBefore = new KeyframeState();
    private KeyframeState propertiesAfter = new KeyframeState();

    public KeyframeState getPropertiesSelection(boolean redo)
    {
        return redo ? this.propertiesAfter : this.propertiesBefore;
    }

    public void editor(UIFilmPanel editor)
    {
        UIClips cameraClips = editor.cameraClips.clips;
        UIClips voiceLineClips = editor.screenplay.editor.clips;

        if (editor.screenplay.isVisible())
        {
            this.panel = 2;
        }
        else if (editor.replayEditor.isVisible())
        {
            this.panel = 1;
        }
        else
        {
            this.panel = 0;
        }

        this.tick = editor.getCursor();
        this.cameraClips = new ClipsData(cameraClips);
        this.voiceLinesClips = new ClipsData(voiceLineClips);

        if (editor.replayEditor.keyframeEditor == null)
        {
            this.propertiesBefore = this.propertiesAfter = editor.replayEditor.keyframeEditor.view.cacheState();
        }
    }

    public void selectedBefore(List<Integer> cameraClipsSelection, List<Integer> voiceLineSelection, KeyframeState properties)
    {
        this.cameraClips.selectedBefore.clear();
        this.cameraClips.selectedBefore.addAll(cameraClipsSelection);

        this.voiceLinesClips.selectedBefore.clear();
        this.voiceLinesClips.selectedBefore.addAll(voiceLineSelection);

        this.propertiesBefore = properties;
    }

    public static class ClipsData
    {
        public double viewMin;
        public double viewMax;
        public int scroll;

        public List<Integer> selectedBefore = new ArrayList<>();
        public List<Integer> selectedAfter = new ArrayList<>();

        public ClipsData(UIClips clips)
        {
            this.viewMin = clips.scale.getMinValue();
            this.viewMax = clips.scale.getMaxValue();
            this.scroll = (int) clips.vertical.scroll;

            this.selectedAfter.addAll(clips.getSelection());
            this.selectedBefore.addAll(this.selectedAfter);
        }

        public List<Integer> getSelection(boolean redo)
        {
            return redo ? this.selectedAfter : this.selectedBefore;
        }

        public void apply(UIClips clips)
        {
            clips.scale.view(this.viewMin, this.viewMax);
            clips.vertical.scrollTo(this.scroll);
        }
    }
}