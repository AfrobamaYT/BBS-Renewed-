package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

public class KeyframeState
{
    public List<List<Integer>> selected = new ArrayList<>();
    public MapType extra = new MapType();

    /** Restore the edited track before applying its viewport and selection. */
    public void restoreGraph(UIKeyframes view)
    {
        String id = this.extra.getString("graph_sheet");
        UIKeyframeSheet sheet = null;

        for (UIKeyframeSheet candidate : view.getDopeSheet().getSheets())
        {
            if (id.equals(candidate.id))
            {
                sheet = candidate;

                break;
            }
        }

        view.editSheet(sheet);
        view.getXAxis().view(this.extra.getDouble("x_min"), this.extra.getDouble("x_max"));
        view.getGraph().restoreState(this.extra);
    }
}
