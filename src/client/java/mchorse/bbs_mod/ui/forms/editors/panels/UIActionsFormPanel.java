package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;

public class UIActionsFormPanel extends UIFormPanel<ModelForm>
{
    public UIStringList actions;

    public UISearchList<String> animations;
    public UIToggle loop;
    public UITrackpad speed;
    public UITrackpad fade;
    public UITrackpad tick;

    private ActionConfig action;

    public UIActionsFormPanel(UIForm editor)
    {
        super(editor);

        this.actions = new UIStringList((l) -> this.pickAction(l.get(0), false));
        this.actions.scroll.cancelScrolling();
        this.actions.background().h(112);

        this.animations = new UISearchList<>(new UIStringList((l) ->
        {
            this.action.name = l.get(0);
            this.resetAnimator();
        }));
        this.animations.list.cancelScrollEdge();
        this.animations.label(UIKeys.GENERAL_SEARCH).list.background();
        this.animations.h(132);
        this.loop = new UIToggle(UIKeys.FORMS_EDITORS_ACTIONS_LOOPS, (b) ->
        {
            this.action.loop = b.getValue();
            this.resetAnimator();
        });
        this.speed = new UITrackpad((v) ->
        {
            this.action.speed = v.floatValue();
            this.resetAnimator();
        });
        this.fade = new UITrackpad((v) ->
        {
            this.action.fade = v.floatValue();
            this.resetAnimator();
        });
        this.fade.limit(0);
        this.tick = new UITrackpad((v) ->
        {
            this.action.tick = v.intValue();
            this.resetAnimator();
        });
        this.tick.limit(0).integer();

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actions);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS).marginTop(6), this.animations, this.loop);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_SPEED).marginTop(6), this.speed);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_FADE).marginTop(6), this.fade);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_TICK).marginTop(6), this.tick);
    }

    private void resetAnimator()
    {
        ((ModelFormRenderer) FormUtilsClient.getRenderer(this.form)).resetAnimator();
    }

    private void pickAction(String key, boolean select)
    {
        ActionsConfig config = this.form.actions.get();

        this.action = config.actions.get(key);

        if (this.action == null)
        {
            this.action = new ActionConfig(key);

            config.actions.put(key, this.action);
        }

        this.animations.list.setCurrentScroll(this.action.name);
        this.loop.setValue(this.action.loop);
        this.speed.setValue(this.action.speed);
        this.fade.setValue(this.action.fade);
        this.tick.setValue(this.action.tick);

        if (select)
        {
            this.actions.setCurrentScroll(key);
        }
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(this.form);

        renderer.ensureAnimator();

        this.animations.list.clear();
        this.animations.list.add(renderer.getModel().animations.animations.keySet());
        this.animations.list.sort();

        this.actions.clear();
        this.actions.add(renderer.getAnimator().getActions());
        this.actions.sort();

        this.pickAction("idle", true);
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        ActionsConfig.removeDefaultActions(this.form.actions.get().actions);
    }
}