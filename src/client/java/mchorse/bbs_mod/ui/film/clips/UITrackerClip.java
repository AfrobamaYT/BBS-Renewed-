package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UITrackerClip extends UIClip<TrackerClip>
{
    public UIButton selector;
    public UIButton group;

    public UIPointModule point;
    public UIPointModule angle;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;

    public UITrackerClip(TrackerClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().entities, this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayGroups());

        this.point = new UIPointModule(editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.angle = new UIPointModule(editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.lookAt = new UIToggle(IKey.raw("Look at"), b -> this.clip.lookAt.set(b.getValue()));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, b -> this.clip.relative.set(b.getValue()));
        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();
        this.active.bits.remove(this.active.bits.size() - 1);
    }

    private void displayGroups()
    {
        List<UIFilmPanel> children = this.getContext().menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        int index = this.clip.selector.get();
        IEntity entity = CollectionUtils.getSafe(panel.getController().entities, index);

        if (entity == null || entity.getForm() == null)
        {
            return;
        }

        Form form = entity.getForm();
        Map<String, Matrix4f> map = new HashMap<>();
        MatrixStack stack = new MatrixStack();

        FormUtilsClient.getRenderer(form).collectMatrices(entity, null, stack, map, "", 0);

        List<String> groups = new ArrayList<>(map.keySet());

        groups.sort(String::compareToIgnoreCase);

        if (groups.isEmpty())
        {
            return;
        }

        String value = this.clip.group.get();

        this.getContext().replaceContextMenu((menu) ->
        {
            for (String attachment : groups)
            {
                menu.action(Icons.LIMB, IKey.raw(attachment), attachment.equals(value), () -> this.clip.group.set(attachment));
            }
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UIClip.label(UIKeys.CAMERA_PANELS_TARGET).marginTop(12), this.selector, this.group);

        this.panels.add(this.point.marginTop(6));
        this.panels.add(this.angle.marginTop(6));
        this.panels.add(this.lookAt);
        this.panels.add(this.relative);
        this.panels.add(this.active);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(this.clip.offset);
        this.angle.fill(this.clip.angle);
        this.lookAt.setValue(this.clip.lookAt.get());
        this.relative.setValue(this.clip.relative.get());
        this.active.setValue(this.clip.active.get());
    }
}
