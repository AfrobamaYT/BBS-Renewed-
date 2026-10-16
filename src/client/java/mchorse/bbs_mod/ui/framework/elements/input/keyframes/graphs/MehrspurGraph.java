package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.keyframes.factories.Vector3fKeyframeFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Several number tracks in one graph, each in its track's colour, from BBS FS 3fa2db306
 * (Wemppy4/bbs-fs, MIT). The single-track graph does the drawing and the editing; this one runs
 * it once per track. The track of the key last grabbed is the active one: new keys land there, it
 * draws on top, and dragging moves every selected key of every track by the grabbed key's offset.
 */
public class MehrspurGraph extends UIKeyframeGraph
{
    private final List<UIKeyframeSheet> spuren;
    private Keyframe gegriffen;

    public MehrspurGraph(UIKeyframes keyframes, List<UIKeyframeSheet> spuren)
    {
        super(keyframes, spuren.get(0));

        this.spuren = new ArrayList<>(spuren);
    }

    /** The tracks that can share a graph: plain numbers, since a vector track has its own three curves */
    public static List<UIKeyframeSheet> waehlbar(List<UIKeyframeSheet> sheets)
    {
        List<UIKeyframeSheet> out = new ArrayList<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            IKeyframeFactory factory = sheet.channel.getFactory();

            if (!sheet.groupHeader && sheet.selection.hasAny() && KeyframeFactories.isNumeric(factory) && !(factory instanceof Vector3fKeyframeFactory)) out.add(sheet);
        }

        return out;
    }

    /**
     * A rebuilt editor (undo, a refill) opens the same graph again -- several tracks or one -- matched
     * by id, with their keys selected and the graph's view. Without the single track, Ctrl+Z in a
     * track's graph dropped back to the dope sheet (measured; Blender's undo never changes the editor).
     */
    public static void uebernehmen(UIKeyframes alt, UIKeyframes neu)
    {
        if (!(alt.getGraph() instanceof UIKeyframeGraph graph)) return;

        boolean mehrere = graph instanceof MehrspurGraph;
        List<UIKeyframeSheet> spuren = new ArrayList<>();
        List<UIKeyframeSheet> alte = new ArrayList<>();

        for (UIKeyframeSheet vorher : graph.getSheets())
        {
            for (UIKeyframeSheet sheet : neu.getGraph().getSheets())
            {
                if (!sheet.id.equals(vorher.id) || sheet.channel.getFactory() != vorher.channel.getFactory()) continue;

                spuren.add(sheet);
                alte.add(vorher);
                break;
            }
        }

        if (mehrere ? spuren.size() < 2 : spuren.size() != 1) return;

        /* editSheet clears the dope sheet's selection (the same sheets), so the keys come back after it */
        if (mehrere) neu.editGraph(new MehrspurGraph(neu, spuren));
        else neu.editSheet(spuren.get(0));

        for (int i = 0; i < spuren.size(); i++)
        {
            spuren.get(i).selection.clear();
            spuren.get(i).selection.addAll(alte.get(i).selection.getIndices());
        }

        neu.getGraph().restoreState(saved(graph));
    }

    private static mchorse.bbs_mod.data.types.MapType saved(UIKeyframeGraph graph)
    {
        mchorse.bbs_mod.data.types.MapType state = new mchorse.bbs_mod.data.types.MapType();

        graph.saveState(state);

        return state;
    }

    @Override
    public List<UIKeyframeSheet> getSheets()
    {
        return this.spuren;
    }

    @Override
    public Keyframe getSelected()
    {
        UIKeyframeSheet sheet = this.gegriffen == null ? null : this.getSheet(this.gegriffen);

        return sheet != null && sheet.selection.has(this.gegriffen) ? this.gegriffen : super.getSelected();
    }

    @Override
    public void resetView()
    {
        this.keyframes.resetViewX();

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        for (UIKeyframeSheet sheet : this.spuren)
        {
            for (Object o : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) o;
                double y = key.getFactory().getY(key.getValue());

                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }

        if (min > max) { min = -10; max = 10; }

        if (Math.abs(max - min) < 0.01F)
        {
            this.yAxis.setShift(min);
            this.yAxis.anchor(0.5F);
        }
        else
        {
            this.yAxis.viewOffset(min, max, this.keyframes.area.h, 30);
        }
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        UIKeyframeSheet aktiv = this.sheet;

        for (UIKeyframeSheet sheet : this.reihe(aktiv, true))
        {
            this.sheet = sheet;

            Pair<Keyframe, KeyframeType> treffer = super.findKeyframe(mouseX, mouseY);

            if (treffer != null)
            {
                this.gegriffen = treffer.a;

                return treffer;
            }
        }

        this.sheet = aktiv;

        return null;
    }

    @Override
    public void selectByX(int mouseX)
    {
        this.jede(() -> super.selectByX(mouseX));
    }

    @Override
    public void selectInArea(Area area)
    {
        this.jede(() -> super.selectInArea(area));
    }

    @Override
    public void render(UIContext context)
    {
        this.yAxis.updateZoom();
        this.renderGrid(context);
        this.jede(() -> this.renderGraph(context));

        /* Which colour is which track, top right, below the ruler */
        int y = this.keyframes.area.y + 22;

        for (UIKeyframeSheet sheet : this.spuren)
        {
            String name = sheet.title.get();
            int x = this.keyframes.area.ex() - 6 - context.batcher.getFont().getWidth(name);

            context.batcher.textShadow(name, x, y, sheet == this.sheet ? Colors.WHITE : Colors.setA(sheet.color, 1F));
            y += 12;
        }
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        this.jede(() -> super.renderTopmostKeyframes(context));
    }

    /** Runs the single-track step for every track, the active one last so it ends on top */
    private void jede(Runnable schritt)
    {
        UIKeyframeSheet aktiv = this.sheet;

        for (UIKeyframeSheet sheet : this.reihe(aktiv, false))
        {
            this.sheet = sheet;
            schritt.run();
        }

        this.sheet = aktiv;
    }

    private List<UIKeyframeSheet> reihe(UIKeyframeSheet aktiv, boolean aktivZuerst)
    {
        List<UIKeyframeSheet> reihe = new ArrayList<>(this.spuren);

        reihe.remove(aktiv);
        reihe.add(aktivZuerst ? 0 : reihe.size(), aktiv);

        return reihe;
    }
}
