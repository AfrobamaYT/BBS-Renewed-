package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.UIStateTrigger;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Collection;
import java.util.List;

public class UIStateTriggersFormPanel extends UIFormPanel<ModelForm>
{
    public UIStateTriggersFormPanel(UIForm editor)
    {
        super(editor);

        this.options.context((menu) ->
        {
            menu.action(Icons.ADD, IKey.raw("Add trigger"), () ->
            {
                StateTrigger trigger = new StateTrigger();

                this.form.triggers.triggers.add(trigger);
                this.addElement(trigger);
                this.options.resize();
            });
        });
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.options.removeAll();

        for (StateTrigger trigger : form.triggers.triggers)
        {
            this.addElement(trigger);
        }

        this.options.resize();
    }

    private void addElement(StateTrigger trigger)
    {
        ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(this.form);
        CubicModel model = renderer.getModel();
        Collection<String> animations = model != null ? model.animations.animations.keySet() : null;
        UIStateTrigger uiTrigger = new UIStateTrigger(this.form, trigger, animations);

        uiTrigger.marginBottom(12);
        uiTrigger.context((menu) ->
        {
            menu.action(Icons.REMOVE, IKey.raw("Remove trigger"), () ->
            {
                this.form.triggers.triggers.remove(trigger);
                uiTrigger.removeFromParent();
                this.options.resize();
            });
        });
        this.options.add(uiTrigger);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.options.getChildren().isEmpty())
        {
            String label = IKey.raw("Right click here to add a state trigger...").get();
            List<String> wrap = context.batcher.getFont().wrap(label, this.options.area.w - 10);

            int x = this.options.area.mx();
            int y = this.options.area.my();

            for (String s : wrap)
            {
                context.batcher.textCard(s, x - context.batcher.getFont().getWidth(s) / 2, y, Colors.WHITE, Colors.A50);

                y += 12;
            }
        }
    }
}