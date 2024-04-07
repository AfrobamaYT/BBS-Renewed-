package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;
import mchorse.bbs_mod.utils.pose.Transform;

public class UITransformKeyframeFactory extends UIKeyframeFactory<Transform>
{
    private UIPropTransform transform;

    public UITransformKeyframeFactory(GenericKeyframe<Transform> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.transform = new UIPropTransform(keyframe::setValue);
        this.transform.verticalCompactNoIcons();
        this.transform.setTransform(keyframe.getValue());

        this.add(this.transform);
    }
}