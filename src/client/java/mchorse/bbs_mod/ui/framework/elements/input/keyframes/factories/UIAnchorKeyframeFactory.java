package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.properties.AnchorProperty;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic.UIPropertyEditor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIAnchorKeyframeFactory extends UIKeyframeFactory<AnchorProperty.Anchor>
{
    private UIButton actor;
    private UIButton attachment;

    public static void displayActors(UIContext context, List<IEntity> entities, int value, Consumer<Integer> callback)
    {
        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(-1));

            for (int i = 0; i < entities.size(); i++)
            {
                IEntity entity = entities.get(i);
                Form form = entity.getForm();
                final int actor = i;
                IKey label = IKey.raw(i + (form == null ? "" : " - " + form.getIdOrName()));

                menu.action(Icons.CLOSE, label, actor == value ? BBSSettings.primaryColor(0) : 0, () -> callback.accept(actor));
            }
        });
    }

    public UIAnchorKeyframeFactory(Keyframe<AnchorProperty.Anchor> keyframe, UIPropertyEditor editor)
    {
        super(keyframe, editor);

        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, (b) -> this.displayActors());
        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayAttachments());

        this.add(this.actor, this.attachment);
    }

    private void displayActors()
    {
        UIFilmPanel panel = this.getPanel();

        displayActors(this.getContext(), panel.getController().entities, this.keyframe.getValue().actor, this::setActor);
    }

    private void displayAttachments()
    {
        UIFilmPanel panel = this.getPanel();
        int index = this.keyframe.getValue().actor;

        if (!CollectionUtils.inRange(panel.getController().entities, index))
        {
            return;
        }

        IEntity entity = panel.getController().entities.get(index);
        Form form = entity.getForm();

        if (form == null)
        {
            return;
        }

        Map<String, Matrix4f> map = new HashMap<>();
        MatrixStack stack = new MatrixStack();

        FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, map, "", 0);

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
        });
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