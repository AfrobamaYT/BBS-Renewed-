package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeKeys;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.Set;

public class UIShapeKeysKeyframeFactory extends UIKeyframeFactory<ShapeKeys>
{
    private UIShapeKeys shapeKeys;

    public UIShapeKeysKeyframeFactory(Keyframe<ShapeKeys> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.shapeKeys = new UIShapeKeysEditor(this);

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        ModelForm form = (ModelForm) sheet.property.getForm();
        CubicModel model = ((ModelFormRenderer) FormUtilsClient.getRenderer(form)).getModel();

        Set<String> shapeKeys = model.model.getShapeKeys();

        if (!shapeKeys.isEmpty())
        {
            this.shapeKeys.setShapeKeys(shapeKeys, keyframe.getValue());
            this.scroll.add(this.shapeKeys);
        }
    }

    public static class UIShapeKeysEditor extends UIShapeKeys
    {
        private UIShapeKeysKeyframeFactory editor;

        public UIShapeKeysEditor(UIShapeKeysKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected void setValue(float v)
        {
            this.editor.keyframe.preNotifyParent();
            super.setValue(v);
            this.editor.keyframe.postNotifyParent();
        }
    }
}