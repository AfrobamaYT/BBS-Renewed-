package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.obj.shapes.ShapeKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;

import java.util.List;
import java.util.function.Consumer;

public class UIShapeKeyList extends UIList<ShapeKey>
{
    public UIShapeKeyList(Consumer<List<ShapeKey>> callback)
    {
        super(callback);

        this.sorting();

        this.scroll.scrollItemSize = 16;
    }

    @Override
    protected String elementToString(UIContext context, int i, ShapeKey element)
    {
        return element.name + " - " + element.value;
    }
}