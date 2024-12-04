package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;

import java.util.List;
import java.util.Set;

public class UIShapeKeys extends UIElement
{
    public UIShapeKeyList list;
    public UITrackpad value;
    public UIToggle relative;

    private Set<String> keys;
    private ShapeKeys shapeKeys;
    private ShapeKey shapeKey;

    public UIShapeKeys()
    {
        this.list = new UIShapeKeyList((l) -> this.pick(l.get(0), false));
        this.list.background().h(this.list.scroll.scrollItemSize * 8);
        this.list.context((menu) ->
        {
            menu.action(Icons.ADD, IKey.raw("Add a shape key..."), () ->
            {
                for (String key : this.keys)
                {
                    this.getContext().replaceContextMenu((newMenu) ->
                    {
                        newMenu.action(Icons.MORE, IKey.raw(key), () -> this.addShapeKey(key));
                    });
                }
            });

            if (this.shapeKey != null)
            {
                menu.action(Icons.REMOVE, IKey.raw("Remove shape key"), this::removeShapeKey);
            }
        });
        this.value = new UITrackpad((v) -> this.setValue(v.floatValue()));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (v) -> this.setRelative(v.getValue()));

        this.column().vertical().stretch();

        this.add(this.list, this.value, this.relative);
        this.pick(null, false);
    }

    public void setShapeKeys(Set<String> keys, ShapeKeys shapeKeys)
    {
        this.keys = keys;
        this.shapeKeys = shapeKeys;

        this.list.setList(this.shapeKeys.shapeKeys);
        this.pick(this.shapeKeys.shapeKeys.isEmpty() ? null : this.shapeKeys.shapeKeys.get(0), true);
    }

    protected void addShapeKey(String key)
    {
        ShapeKey shapeKey = new ShapeKey(key, 0F);

        this.shapeKeys.shapeKeys.add(shapeKey);
        this.list.update();
        this.pick(shapeKey, true);
    }

    protected void removeShapeKey()
    {
        this.shapeKeys.shapeKeys.remove(this.shapeKey);
        this.pick(this.shapeKeys.shapeKeys.isEmpty() ? null : this.shapeKeys.shapeKeys.get(0), true);
        this.list.update();
    }

    protected void setRelative(boolean v)
    {
        this.shapeKey.relative = v;
    }

    protected void setValue(float v)
    {
        this.shapeKey.value = v;
    }

    private void pick(ShapeKey shapeKey, boolean select)
    {
        this.shapeKey = shapeKey;

        this.value.setVisible(shapeKey != null);
        this.relative.setVisible(shapeKey != null);

        if (this.shapeKey != null)
        {
            this.value.setValue(shapeKey.value);
            this.relative.setValue(shapeKey.relative);
        }

        if (select)
        {
            List<ShapeKey> list = this.list.getList();
            int index = CollectionUtils.getIndex(list, this.shapeKey);

            this.list.setCurrentScroll(CollectionUtils.getSafe(list, index));
        }
    }
}