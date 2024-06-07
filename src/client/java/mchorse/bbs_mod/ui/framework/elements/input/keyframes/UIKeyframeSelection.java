package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UIKeyframeSelection
{
    private KeyframeChannel channel;
    private Set<Integer> selected = new LinkedHashSet<>();

    private List<Keyframe> tmp = new ArrayList<>();

    public UIKeyframeSelection(KeyframeChannel channel)
    {
        this.channel = channel;
    }

    public boolean has(int index)
    {
        return this.selected.contains(index);
    }

    public void clear()
    {
        this.selected.clear();
    }

    public void add(int i)
    {
        this.selected.add(i);
    }

    public void remove(int index)
    {
        this.selected.remove(index);
    }

    public Keyframe getFirst()
    {
        for (Integer integer : this.selected)
        {
            return this.channel.get(integer);
        }

        return null;
    }

    public List<Keyframe> getSelected()
    {
        this.tmp.clear();

        for (Integer index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                this.tmp.add(keyframe);
            }
        }

        return this.tmp;
    }
}