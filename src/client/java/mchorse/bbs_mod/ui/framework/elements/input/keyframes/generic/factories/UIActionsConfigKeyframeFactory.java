package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIProperty;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;

import java.util.Collection;

public class UIActionsConfigKeyframeFactory extends UIKeyframeFactory<ActionsConfig>
{
    public UIActionsConfigEditor actionsEditor;

    public UIActionsConfigKeyframeFactory(GenericKeyframe<ActionsConfig> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        UIProperty property = editor.properties.getProperty(keyframe);
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