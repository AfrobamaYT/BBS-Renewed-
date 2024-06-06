package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic;

import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UIProperty
{
    public final String id;
    public IKey title;
    public int color;
    public KeyframeChannel channel;
    private List<Integer> selected = new ArrayList<>();
    public IFormProperty property;

    public UIProperty(String id, IKey title, int color, KeyframeChannel channel, IFormProperty property)
    {
        this.id = id;
        this.title = title;
        this.color = color;
        this.channel = channel;
        this.property = property;
    }

    public void addToSelection(int selected)
    {
        if (!this.selected.contains(selected))
        {
            this.selected.add(selected);
        }
    }

    public void addToSelection(List<Integer> sheetSelection)
    {
        for (Integer integer : sheetSelection)
        {
            this.addToSelection(integer);
        }
    }

    public void sort()
    {
        List<Keyframe> keyframes = new ArrayList<>();

        for (int index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                keyframes.add(keyframe);
            }
        }

        this.channel.preNotifyParent();
        this.channel.sort();
        this.channel.postNotifyParent();
        this.selected.clear();

        for (Keyframe keyframe : keyframes)
        {
            this.selected.add(this.channel.getKeyframes().indexOf(keyframe));
        }
    }

    public void setTick(double dx)
    {
        for (int index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                keyframe.setTick(keyframe.getTick() + (long) dx);
            }
        }
    }

    public void setValue(Object object)
    {
        for (int index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                keyframe.setValue(this.channel.getFactory().copy(object));
            }
        }
    }

    public void setInterpolation(Interpolation interp)
    {
        for (int index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                keyframe.getInterpolation().copy(interp);
            }
        }
    }

    public Keyframe getKeyframe()
    {
        if (this.selected.isEmpty())
        {
            return null;
        }

        return this.channel.get(this.selected.get(0));
    }

    public boolean hasSelected()
    {
        return !this.selected.isEmpty();
    }

    public boolean hasSelected(int i)
    {
        return this.selected.contains(i);
    }

    public void clearSelection()
    {
        this.selected.clear();
    }

    public int getSelected(int i)
    {
        return this.selected.get(0);
    }

    public Keyframe getSelectedKeyframe(int i)
    {
        return this.channel.get(this.selected.get(i));
    }

    public List<Integer> getSelection()
    {
        return this.selected;
    }

    public int getSelectedCount()
    {
        return this.selected.size();
    }

    public void removeSelectedKeyframes()
    {
        List<Integer> sorted = new ArrayList<>(this.selected);

        Collections.sort(sorted);
        Collections.reverse(sorted);

        this.clearSelection();

        for (int index : sorted)
        {
            this.channel.remove(index);
        }

        this.clearSelection();
    }

    public void duplicate(long tick)
    {
        List<Keyframe> selected = new ArrayList<>();
        List<Keyframe> created = new ArrayList<>();

        long minTick = Integer.MAX_VALUE;

        for (int index : this.selected)
        {
            Keyframe keyframe = this.channel.get(index);

            if (keyframe != null)
            {
                selected.add(keyframe);
                minTick = Math.min(keyframe.getTick(), minTick);
            }
        }

        selected.sort(Comparator.comparingLong(Keyframe::getTick));

        long diff = tick - minTick;

        for (Keyframe keyframe : selected)
        {
            long fin = keyframe.getTick() + diff;
            int index = this.channel.insert(fin, keyframe.getValue());
            Keyframe current = this.channel.get(index);

            current.copy(keyframe);
            current.setTick(fin);
            created.add(current);
        }

        this.clearSelection();

        for (Keyframe keyframe : created)
        {
            this.selected.add(this.channel.getKeyframes().indexOf(keyframe));
        }
    }

    public void selectAll()
    {
        this.clearSelection();

        for (int i = 0, c = this.channel.getKeyframes().size(); i < c; i++)
        {
            this.selected.add(i);
        }
    }
}
