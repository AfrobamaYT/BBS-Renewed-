package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.factories;

import mchorse.bbs_mod.forms.properties.AnchorProperty;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.generic.GenericKeyframe;

public class UIAnchorKeyframeFactory extends UIKeyframeFactory<AnchorProperty.Anchor>
{
    private UIButton actor;
    private UIButton attachment;

    public UIAnchorKeyframeFactory(GenericKeyframe<AnchorProperty.Anchor> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, (b) -> this.displayActors());
        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayAttachments());

        this.add(this.actor, this.attachment);
    }

    private void displayActors()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            UIFilmPanel panel = this.getPanel();
            int value = this.keyframe.getValue().actor;

            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> this.setActor(-1));

            for (int i = 0; i < panel.getController().entities.size(); i++)
            {
                /* TODO: Entity entity = panel.getController().entities.get(i);
                Form form = entity.get(FormComponent.class).form;
                final int actor = i;
                IKey label = IKey.raw(i + (form == null ? "" : " - " + form.getIdOrName()));

                if (actor == value)
                {
                    menu.action(Icons.CLOSE, label, BBSSettings.primaryColor(0), () -> this.setActor(actor));
                }
                else
                {
                    menu.action(Icons.CLOSE, label, () -> this.setActor(actor));
                } */
            }
        });
    }

    private void displayAttachments()
    {
        /* TODO: UIFilmPanel panel = this.getPanel();
        int index = this.keyframe.getValue().actor;

        if (!CollectionUtils.inRange(panel.getController().entities, index))
        {
            return;
        }

        Entity entity = panel.getController().entities.get(index);
        Form form = entity.get(FormComponent.class).form;

        if (form == null)
        {
            return;
        }

        Map<String, Matrix4f> map = new HashMap<>();
        MatrixStack stack = new MatrixStack();

        form.getRenderer().collectMatrices(entity, stack, map, "", 0);

        List<String> attachments = new ArrayList<>(map.keySet());

        attachments.sort(String::compareToIgnoreCase);

        if (attachments.isEmpty())
        {
            return;
        }

        String value = this.keyframe.getValue().attachment;

        this.getContext().replaceContextMenu((menu) ->
        {
            for (String attachment : attachments)
            {
                if (attachment.equals(value))
                {
                    menu.action(Icons.LIMB, IKey.raw(attachment), BBSSettings.primaryColor(0), () -> this.setAttachment(attachment));
                }
                else
                {
                    menu.action(Icons.LIMB, IKey.raw(attachment), () -> this.setAttachment(attachment));
                }
            }
        }); */
    }

    private void setActor(int actor)
    {
        this.keyframe.getValue().actor = actor;

        this.editor.setValue(this.keyframe.getValue());
    }

    private void setAttachment(String attachment)
    {
        this.keyframe.getValue().attachment = attachment;

        this.editor.setValue(this.keyframe.getValue());
    }

    private UIFilmPanel getPanel()
    {
        return this.getContext().menu.getRoot().getChildren(UIFilmPanel.class).get(0);
    }
}