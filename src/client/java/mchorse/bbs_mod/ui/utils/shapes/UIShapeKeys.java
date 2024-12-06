package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.Set;

public class UIShapeKeys extends UIElement
{
    public UIStringList list;
    public UITrackpad value;

    private ShapeKeys shapeKeys;

    public UIShapeKeys()
    {
        this.list = new UIStringList((l) -> this.pick(l.get(0), false));
        this.list.background().h(this.list.scroll.scrollItemSize * 6);
        this.list.cancelScrollEdge();
        this.value = new UITrackpad((v) -> this.setValue(v.floatValue()));

        this.column().vertical().stretch();

        this.add(UI.label(UIKeys.SHAPE_KEYS_TITLE), this.list, this.value);
    }

    public void setShapeKeys(Set<String> keys, ShapeKeys shapeKeys)
    {
        this.shapeKeys = shapeKeys;

        this.list.add(keys);
        this.list.sort();
        this.pick(this.list.getList().get(0), true);
    }

    protected void setValue(float v)
    {
        this.shapeKeys.shapeKeys.put(this.list.getCurrentFirst(), v);
    }

    private void pick(String key, boolean select)
    {
        this.value.setValue(this.shapeKeys.shapeKeys.computeIfAbsent(key, (k) -> 0F));

        if (select)
        {
            this.list.setCurrentScroll(key);
        }
    }
}