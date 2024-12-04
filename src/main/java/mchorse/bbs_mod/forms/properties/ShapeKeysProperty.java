package mchorse.bbs_mod.forms.properties;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ShapeKeysProperty extends BaseTweenProperty<ShapeKeys>
{
    public ShapeKeysProperty(Form form, String key, ShapeKeys value)
    {
        super(form, key, value, KeyframeFactories.SHAPE_KEYS);
    }
}