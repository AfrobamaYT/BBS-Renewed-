package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIVector3KeyframeGraph;
import mchorse.bbs_mod.utils.keyframes.factories.Vector3fKeyframeFactory;

import java.util.ArrayList;
import java.util.List;

public class KeyframeState
{
    public List<List<Integer>> selected = new ArrayList<>();
    public MapType extra = new MapType();

    public static IUIKeyframeGraph createGraph(UIKeyframes view, UIKeyframeSheet sheet)
    {
        UIKeyframeDopeSheet dope = view.getDopeSheet();

        if (sheet == null)
        {
            return dope;
        }

        dope.clearSelection();
        dope.pickSelected();

        return sheet.channel.getFactory() instanceof Vector3fKeyframeFactory
            ? new UIVector3KeyframeGraph(view, sheet) : new UIKeyframeGraph(view, sheet);
    }

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
