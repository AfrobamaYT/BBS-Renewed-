package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ProceduralBone;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * The model editor's "Procedural animation" section: which bone plays head, torso, arms, legs and
 * the whole-model tilt, with a motion preview in the editor's viewport while the section is open.
 * From BBS FS f3113ccdd (Wemppy4/bbs-fs, MIT); FS has it as a tab of its newer config editor.
 */
public class ProzeduralAbschnitt
{
    private static final String[] BEWEGUNGEN = {"idle", "walk", "look", "attack", "swim"};

    public final UISection section;
    private final UIElement body;
    private final UIModelEditorPanel panel;
    private int bewegung;

    public ProzeduralAbschnitt(UIModelEditorPanel panel)
    {
        this.panel = panel;
        this.section = panel.section(L10n.lang("bbs.ui.model_editor.procedural_tab"), false);
        this.body = panel.body();
        this.section.fields.add(this.body);
    }

    /** The motion the preview plays, or -1 while the section is folded. */
    public int vorschau()
    {
        return this.section.isExpanded() ? this.bewegung : -1;
    }

    public void fill(ModelConfig config, ModelInstance bound)
    {
        this.body.removeAll();

        if (config == null || bound == null)
        {
            return;
        }

        this.body.add(this.panel.toggleRefresh(UIKeys.MODEL_EDITOR_PROCEDURAL, config.procedural));
        this.body.add(new UIButton(L10n.lang("bbs.ui.model_editor.procedural_detect"), (b) ->
        {
            Map<String, String> detected = new LinkedHashMap<>();

            for (ProceduralBone role : ProceduralBone.values()) detected.put(role.id, role.detect(bound.getModel()));

            config.proceduralBones.set(detected);
            this.fill(config, bound);
        }));
        this.body.add(new UIButton(L10n.lang("bbs.ui.model_editor.procedural_legacy"), (b) ->
        {
            config.proceduralBones.set(new LinkedHashMap<>());
            this.fill(config, bound);
        }));

        for (ProceduralBone role : ProceduralBone.values())
        {
            this.body.add(this.panel.labeledRow(L10n.lang("bbs.ui.model_editor.procedural_role_" + role.id), this.panel.bonePicker(
                () -> role.resolve(bound.getModel(), config.proceduralBones.get()),
                (bone) -> assign(config, bound, role, bone), () -> this.fill(config, bound))));
        }

        UICirculate preview = new UICirculate((b) -> this.bewegung = b.getValue());

        for (String bewegung : BEWEGUNGEN) preview.addLabel(L10n.lang("bbs.ui.model_editor.procedural_" + bewegung));

        preview.setValue(this.bewegung);
        this.body.add(UI.label(L10n.lang("bbs.ui.model_editor.procedural_preview")), preview);
        this.panel.resizeGeneral();
    }

    /** A bone has one role: any other role using it lets go explicitly. */
    private static void assign(ModelConfig config, ModelInstance bound, ProceduralBone role, String bone)
    {
        Map<String, String> assignments = new LinkedHashMap<>(config.proceduralBones.get());

        if (!bone.isEmpty())
        {
            for (ProceduralBone other : ProceduralBone.values())
            {
                if (other != role && bone.equals(other.resolve(bound.getModel(), assignments))) assignments.put(other.id, "");
            }
        }

        assignments.put(role.id, bone);
        config.proceduralBones.set(assignments);
    }

    /** The editor's viewport, which walks, looks around, swings or swims the model for the preview. */
    public static class Vorschau extends UIFormRenderer
    {
        public IntSupplier bewegung = () -> -1;
        private int letzte = -1;

        @Override
        protected void update()
        {
            super.update();

            int bewegung = this.bewegung.getAsInt();
            IEntity entity = this.entity;

            if (bewegung != this.letzte)
            {
                this.letzte = bewegung;
                entity.setHeadYaw(0F);
                entity.setPitch(0F);
                entity.setSwimming(false);
                entity.setLeaningPitch(0F);
                /* Finish a preview swing and settle the limbs before leaving the preview. */
                for (int i = 0; i < 7; i++) entity.update();
            }

            if (bewegung < 0)
            {
                return;
            }

            float age = entity.getAge();
            float speed = bewegung == 1 || bewegung == 4 ? 1F : 0F;

            entity.update();
            entity.getLimbAnimator().update(speed, 1F, 1F);
            entity.setHeadYaw(bewegung == 2 ? (float) Math.sin(age * 0.05F) * 60F : 0F);
            entity.setPitch(bewegung == 2 ? (float) Math.sin(age * 0.08F) * 30F : 0F);
            entity.setSwimming(bewegung == 4);
            entity.setLeaningPitch(bewegung == 4 ? 1F : 0F);

            if (bewegung == 3 && (int) age % 24 == 0) entity.swingArm();
        }
    }
}
