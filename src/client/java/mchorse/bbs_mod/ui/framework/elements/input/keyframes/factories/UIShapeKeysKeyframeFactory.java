package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
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

    /** For the controllers' handles in the film viewport */
    public final ModelForm form;
    public final mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers controls;

    public UIShapeKeysKeyframeFactory(Keyframe<ShapeKeys> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        ModelForm form = (ModelForm) FormUtils.getForm(sheet.property);
        ModelInstance model = ((ModelFormRenderer) FormUtilsClient.getRenderer(form)).getModel();

        this.form = form;
        Set<String> shapeKeys = model.model.getShapeKeys();

        this.shapeKeys = new UIShapeKeysEditor(this);

        /* The model's shape key controllers first, when it has any (BBS FS 7a049804a) */
        mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers controls = this.controls = new mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers((edit) ->
        {
            this.keyframe.preNotify();
            edit.accept(this.keyframe.getValue());
            this.keyframe.postNotify();
        });

        controls.boundary(() -> this.keyframe.preNotify(mchorse.bbs_mod.settings.values.IValueListener.FLAG_UNMERGEABLE));
        controls.presets(model::getPoseGroup);
        controls.fill(model.config.shapeControllers.getAllTyped(), keyframe.getValue());

        if (!model.config.shapeControllers.getAllTyped().isEmpty()) this.scroll.add(controls);

        if (!shapeKeys.isEmpty())
        {
            this.shapeKeys.setShapeKeys(model.getPoseGroup(), shapeKeys, keyframe.getValue());
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
        protected void changedShapeKeys(Runnable runnable)
        {
            super.changedShapeKeys(runnable);
        }

        @Override
        protected void setValue(float v)
        {
            this.editor.keyframe.preNotify();
            super.setValue(v);
            this.editor.keyframe.postNotify();
        }
    }
}