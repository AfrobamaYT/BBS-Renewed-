package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.film.controller.ZielPipette;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIAnchorKeyframeFactory extends UIKeyframeFactory<Anchor>
{
    private UIButton actor;
    private UIButton attachment;
    /** Which components of the target's frame the anchored form rides (BBS FS 14f2ef586) */
    private UIToggle inheritPosition, inheritRotation, inheritScale;
    private UIToggle keepTransform;
    private mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad physics;
    public UIPropTransform transform;

    public static void displayActors(UIContext context, IntObjectMap<IEntity> entities, int value, Consumer<Integer> callback)
    {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : null;

        /* The map is keyed by the replay's index and disabled replays get no entity, so its size is not
         * the range of keys: counting up to it dropped the last actor per disabled one (BBS FS 58b2b46ce) */
        int count = replays == null ? 0 : replays.size();

        for (int key : entities.keySet())
        {
            count = Math.max(count, key + 1);
        }

        final int total = count;

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> callback.accept(-1));

            for (int i = 0; i < total; i++)
            {
                final int actor = i;
                IEntity entity = entities.get(i);

                if (entity == null)
                {
                    continue;
                }

                Replay replay = replays == null || i >= replays.size() ? null : replays.get(i);
                Form form = entity.getForm();
                String stringLabel = i + (replay != null ? " - " + replay.getName() : (form == null ? "" : " - " + form.getFormIdOrName()));
                IKey label = IKey.constant(stringLabel);

                menu.action(Icons.CLOSE, label, actor == value, () -> callback.accept(actor));
            }
        });
    }

    public static void displayAttachments(UIFilmPanel panel, int index, String value, Consumer<String> consumer)
    {
        IEntity entity = panel.getController().getEntities().get(index);

        if (entity == null || entity.getForm() == null)
        {
            return;
        }

        Form form = entity.getForm();
        List<String> attachments = new ArrayList<>(FormUtilsClient.getRenderer(form).collectMatrices(entity, 0F).keySet());

        attachments.sort(String::compareToIgnoreCase);

        /* Collect labels (substitute track names) */
        List<String> labels = new ArrayList<>(attachments);

        for (int i = 0; i < labels.size(); i++)
        {
            String label = labels.get(i);
            Form path = FormUtils.getForm(form, label);

            if (path != null)
            {
                labels.set(i, path.getTrackName(label));
            }
        }

        if (attachments.isEmpty())
        {
            return;
        }

        panel.getContext().replaceContextMenu((menu) ->
        {
            for (int i = 0; i < attachments.size(); i++)
            {
                String attachment = attachments.get(i);
                String label = labels.get(i);

                menu.action(Icons.LIMB, IKey.constant(label), attachment.equals(value), () -> consumer.accept(attachment));
            }
        });
    }

    public UIAnchorKeyframeFactory(Keyframe<Anchor> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, (b) -> this.displayActors());
        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            displayAttachments(this.getPanel(), this.keyframe.getValue().replay, this.keyframe.getValue().attachment, this::setAttachment);
        });
        this.keepTransform = new UIToggle(mchorse.bbs_mod.l10n.L10n.lang("bbs.config.editor.anchor_keep_transform"), BBSSettings.anchorKeepTransform.get(), (b) -> BBSSettings.anchorKeepTransform.set(b.getValue()));
        this.inheritPosition = new UIToggle(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.inherit.position"), (b) -> this.retarget((anchor) -> anchor.inheritPosition = b.getValue()));
        this.inheritPosition.setValue(keyframe.getValue().inheritPosition);
        this.inheritRotation = new UIToggle(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.inherit.rotation"), (b) -> this.retarget((anchor) -> anchor.inheritRotation = b.getValue()));
        this.inheritRotation.setValue(keyframe.getValue().inheritRotation);
        this.inheritScale = new UIToggle(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.inherit.scale"), (b) -> this.retarget((anchor) -> anchor.inheritScale = b.getValue()));
        this.inheritScale.setValue(keyframe.getValue().inheritScale);
        this.transform = new UIAnchorTransforms(this);
        this.transform.enableHotkeys();
        this.transform.setTransform(keyframe.getValue().transform);

        /* How much the bone physics feels the target's motion, in percent (BBS FS 89b786ca6) */
        this.physics = new mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad((v) -> BaseValue.edit(this.keyframe, (value) -> value.getValue().physics = v.floatValue() / 100F));
        this.physics.limit(0, 100, true).setValue(keyframe.getValue().physics * 100F);
        this.physics.tooltip(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.generic_keyframes.anchor.physics_tooltip"));

        this.scroll.add(UI.row(this.actor, this.pipette(false)), UI.row(this.attachment, this.pipette(true)), this.keepTransform, this.inheritPosition, this.inheritRotation, this.inheritScale,
            UI.labelRow(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.generic_keyframes.anchor.physics"), this.physics), this.transform);
    }

    /**
     * An eyedropper beside the button: armed, the next click on a replay in the film viewport sets
     * the actor, and with the attachment one also the bone under the pointer (BBS FS fb6795eeb).
     */
    private UIIcon pipette(boolean knochen)
    {
        UIIcon icon = new UIIcon(Icons.EYEDROPPER, null)
        {
            @Override
            public void render(UIContext context)
            {
                this.active(ZielPipette.aktiv(this)); /* lit while armed */
                super.render(context);
            }
        };

        icon.callback = (b) -> ZielPipette.umschalten(b, (replay, picked) ->
        {
            this.setActor(replay);
            boolean ort = knochen && picked != null && picked.a != null && (picked.b != null && !picked.b.isEmpty() || !FormUtils.getPath(picked.a).isEmpty());

            this.setAttachment(ort ? StringUtils.combinePaths(FormUtils.getPath(picked.a), picked.b == null ? "" : picked.b) : "");
        });
        icon.activeColor(BBSSettings.primaryColor(Colors.A100)).wh(16, 16).tooltip(knochen ? UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT : UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR);

        return icon;
    }

    private void displayActors()
    {
        UIFilmPanel panel = this.getPanel();

        displayActors(this.getContext(), panel.getController().getEntities(), this.keyframe.getValue().replay, this::setActor);
    }

    private void setActor(int actor)
    {
        this.retarget((anchor) -> anchor.replay = actor);
    }

    private void setAttachment(String attachment)
    {
        this.retarget((anchor) -> anchor.attachment = attachment);
    }

    /**
     * Changes what the anchor hangs off; with "keep transform" on, the transform is rebased so the
     * form stays where it is. Measured on copies before the edit and written inside it, so the
     * retarget and its compensation are one undo step (BBS FS a220e30c5).
     */
    private void retarget(java.util.function.Consumer<mchorse.bbs_mod.forms.forms.utils.Anchor> change)
    {
        mchorse.bbs_mod.forms.forms.utils.Anchor from = this.keyframe.getValue().copy();
        mchorse.bbs_mod.forms.forms.utils.Anchor to = from.copy();

        change.accept(to);

        boolean rebased = BBSSettings.anchorKeepTransform.get() && this.rebase(from, to);

        BaseValue.edit(this.keyframe, (keyframe) ->
        {
            change.accept(keyframe.getValue());

            if (rebased) keyframe.getValue().transform.copy(to.transform);
        });

        if (rebased) this.transform.setTransform(this.keyframe.getValue().transform);
    }

    /** Against the replay this keyframe belongs to: its entity carries the live pose everything is measured from */
    private boolean rebase(mchorse.bbs_mod.forms.forms.utils.Anchor from, mchorse.bbs_mod.forms.forms.utils.Anchor to)
    {
        UIFilmPanel panel = this.getPanel();
        mchorse.bbs_mod.film.replays.Replay replay = panel == null ? null : panel.replayEditor.getReplay();

        if (replay == null) return false;

        int index = panel.getData().replays.getList().indexOf(replay);
        io.netty.util.collection.IntObjectMap<mchorse.bbs_mod.forms.entities.IEntity> entities = panel.getController().getEntities();

        return mchorse.bbs_mod.film.AnchorRebase.keepWorldTransform(entities, entities.get(index), index, 0F, from, to);
    }



    private UIFilmPanel getPanel()
    {
        return this.getParent(UIFilmPanel.class);
    }

    public static class UIAnchorTransforms extends UIKeyframePropTransform
    {
        private final UIAnchorKeyframeFactory editor;

        public UIAnchorTransforms(UIAnchorKeyframeFactory editor)
        {
            this.editor = editor;
        }

        @Override
        protected void applyToSelection(Consumer<Transform> consumer)
        {
            apply(this.editor.editor, this.editor.keyframe, consumer);
        }

        @Override
        protected void applyDuringRecording(float tick, Consumer<Transform> consumer)
        {
            applyRecording(this.editor.editor, this.editor.keyframe, tick, consumer);
        }

        @Override
        protected Transform getRecordedTransform(float tick)
        {
            UIKeyframeSheet sheet = this.editor.editor.getGraph().getSheet(this.editor.keyframe);
            Keyframe<?> recorded = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);

            return recorded == null ? null : ((Anchor) recorded.getValue()).transform;
        }

        public static void applyRecording(UIKeyframes editor, Keyframe<?> keyframe, float tick, Consumer<Transform> consumer)
        {
            UIReplaysEditorUtils.forEachRecordedKeyframe(editor, keyframe, tick, (recorded) ->
            {
                Anchor anchor = (Anchor) recorded.getValue();

                recorded.preNotify();
                consumer.accept(anchor.transform);
                recorded.postNotify();
            });
        }

        public static void apply(UIKeyframes editor, Keyframe<?> keyframe, Consumer<Transform> consumer)
        {
            UIReplaysEditorUtils.forEachSelectedKeyframe(editor, keyframe, (selected) ->
            {
                Anchor anchor = (Anchor) selected.getValue();

                selected.preNotify();
                consumer.accept(anchor.transform);
                selected.postNotify();
            });
        }
    }
}
