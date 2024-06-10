package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.Collection;

public class UIActionsConfigKeyframeFactory extends UIKeyframeFactory<ActionsConfig>
{
    public UIActionsConfigEditor actionsEditor;

    public UIActionsConfigKeyframeFactory(Keyframe<ActionsConfig> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        UIKeyframeSheet property = editor.getSheet(keyframe);
        ModelForm form = (ModelForm) property.property.getForm();
        ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(form);
        CubicModel model = renderer.getModel();

        renderer.ensureAnimator();

        IAnimator animator = renderer.getAnimator();
        Collection<String> animations = model != null ? model.animations.animations.keySet() : null;
        Collection<String> actions = animator != null ? animator.getActions() : null;

        this.actionsEditor = new UIActionsConfigEditor(renderer::resetAnimator);
        this.actionsEditor.setConfigs(keyframe.getValue(), animations, actions);

        this.add(this.actionsEditor);
    }
}