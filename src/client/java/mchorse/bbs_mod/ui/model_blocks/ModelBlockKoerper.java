package mchorse.bbs_mod.ui.model_blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.blocks.ModelBlockSound;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelBody;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.graphics.Draw;
import net.minecraft.core.BlockPos;
import mchorse.bbs_mod.utils.AABB;

/**
 * The model block panel's "Body" section: the block's hitbox (a cube, the form's hitbox or set by
 * hand), whether entities and the camera collide with it, how long it takes to mine, its light and
 * its sound. Light, sound and hardness save at once, since the server reads them from its own copy.
 * From BBS FS 996eb0adc (Wemppy4/bbs-fs, MIT).
 */
public class ModelBlockKoerper
{
    public final UISection section;
    /** The equipment section rides along, so the full panel needs one hook for both */
    public final ModelBlockAusruestung ausruestung;

    private final UIModelBlockPanel panel;
    private final UICirculate hitboxMode;
    private final UIElement hitboxManual;
    private final UITrackpad[] hitbox = new UITrackpad[6];
    private final UIToggle solid;
    private final UIToggle cameraCollision;
    private final UITrackpad hardness;
    private final UITrackpad lightLevel;
    private final UICirculate sound;

    public ModelBlockKoerper(UIModelBlockPanel panel)
    {
        this.panel = panel;
        this.ausruestung = new ModelBlockAusruestung(panel::getModelBlock, panel.verlauf);

        this.hitboxMode = new UICirculate((b) ->
        {
            this.panel.verlauf.aendern(null, () -> this.body().setHitboxMode(ModelBody.HitboxMode.values()[b.getValue()]));
            this.manualZeigen();
        });

        for (String mode : new String[] {"cube", "form", "manual"}) this.hitboxMode.addLabel(L10n.lang("bbs.ui.model_blocks.body.hitbox." + mode));

        for (int i = 0; i < 6; i++)
        {
            int index = i;

            this.hitbox[i] = new UITrackpad((v) -> this.panel.verlauf.aendern("hitbox" + index, () -> (index < 3 ? this.body().getHitboxMin() : this.body().getHitboxMax()).setComponent(index % 3, v.floatValue())));
        }

        this.hitboxManual = UI.column(
            UI.label(L10n.lang("bbs.ui.model_blocks.body.hitbox.min")), UI.row(this.hitbox[0], this.hitbox[1], this.hitbox[2]),
            UI.label(L10n.lang("bbs.ui.model_blocks.body.hitbox.max")), UI.row(this.hitbox[3], this.hitbox[4], this.hitbox[5])
        );

        this.solid = new UIToggle(L10n.lang("bbs.ui.model_blocks.body.solid"), (b) -> this.panel.verlauf.aendern(null, () -> this.body().setSolid(b.getValue())));
        this.cameraCollision = new UIToggle(L10n.lang("bbs.ui.model_blocks.body.camera"), (b) -> this.panel.verlauf.aendern(null, () -> this.body().setCameraCollision(b.getValue())));

        /* The server steps the break progress from its own copy, so hardness saves at once */
        this.hardness = new UITrackpad((v) ->
        {
            this.panel.verlauf.aendern("hardness", () -> this.body().setHardness(v.floatValue()));
            this.panel.save(this.panel.getModelBlock());
        });
        this.hardness.limit(0).tooltip(L10n.lang("bbs.ui.model_blocks.body.hardness_tooltip"));

        /* Light and sound live in the block state on the server: save at once, or nothing shows */
        this.lightLevel = new UITrackpad((v) ->
        {
            this.panel.verlauf.aendern("light", () -> this.body().setLightLevel(v.intValue()));
            this.panel.save(this.panel.getModelBlock());
        });
        this.lightLevel.limit(0, 15, true);

        this.sound = new UICirculate((b) ->
        {
            this.panel.verlauf.aendern(null, () -> this.body().setSound(ModelBlockSound.values()[b.getValue()]));
            this.panel.save(this.panel.getModelBlock());
        });

        for (ModelBlockSound sound : ModelBlockSound.values()) this.sound.addLabel(L10n.lang("bbs.ui.model_blocks.body.sound." + sound.id));

        /* A drag is one history entry, the next drag of the same field another */
        for (UITrackpad feld : new UITrackpad[] {this.hitbox[0], this.hitbox[1], this.hitbox[2], this.hitbox[3], this.hitbox[4], this.hitbox[5], this.hardness, this.lightLevel})
        {
            feld.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent.class, (e) -> panel.verlauf.undo.markLastUndoNoMerging());
            feld.getEvents().register(mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent.class, (e) -> panel.verlauf.undo.markLastUndoNoMerging());
        }

        this.section = new UISection(L10n.lang("bbs.ui.model_blocks.body.title"));
        this.section.setExpanded(false);
        this.section.fields.add(this.hitboxMode, this.solid, this.cameraCollision,
            UI.labelRow(L10n.lang("bbs.ui.model_blocks.body.hardness"), this.hardness),
            UI.labelRow(L10n.lang("bbs.ui.model_blocks.body.light"), this.lightLevel), this.sound);
    }

    private ModelBody body()
    {
        return this.panel.getModelBlock().getProperties().getBody();
    }

    public void fill(ModelBlockEntity modelBlock)
    {
        this.section.setVisible(modelBlock != null);
        this.ausruestung.fill(modelBlock);

        if (modelBlock == null)
        {
            return;
        }

        ModelBody body = modelBlock.getProperties().getBody();

        this.hitboxMode.setValue(body.getHitboxMode().ordinal());

        for (int i = 0; i < 6; i++) this.hitbox[i].setValue((i < 3 ? body.getHitboxMin() : body.getHitboxMax()).get(i % 3));

        this.solid.setValue(body.isSolid());
        this.cameraCollision.setValue(body.isCameraCollision());
        this.hardness.setValue(body.getHardness());
        this.lightLevel.setValue(body.getLightLevel());
        this.sound.setValue(body.getSound().ordinal());
        this.manualZeigen();
    }

    private void manualZeigen()
    {
        /* In the column only while manual: a hidden child still takes its rows here */
        boolean manual = this.body().getHitboxMode() == ModelBody.HitboxMode.MANUAL;

        if (this.hitboxManual.hasParent() != manual)
        {
            if (manual) this.section.fields.addAfter(this.hitboxMode, this.hitboxManual);
            else this.hitboxManual.removeFromParent();

            this.panel.scrollView.resize();
        }
    }

    /** The panel's frame around a block shows its actual hitbox, so shaping the body shows at once */
    public static void umriss(PoseStack stack, ModelBlockEntity entity, boolean hervorgehoben)
    {
        net.minecraft.world.phys.AABB box = entity.getShape().bounds();

        if (hervorgehoben)
        {
            Draw.renderBox(stack, box.minX, box.minY, box.minZ, box.getXsize(), box.getYsize(), box.getZsize(), 0, 0.5F, 1F);
        }
        else
        {
            Draw.renderBox(stack, box.minX, box.minY, box.minZ, box.getXsize(), box.getYsize(), box.getZsize());
        }
    }

    /** Picking in the panel goes by the same hitbox */
    public static AABB trefferBox(ModelBlockEntity entity)
    {
        BlockPos pos = entity.getBlockPos();
        net.minecraft.world.phys.AABB box = entity.getShape().bounds();

        return new AABB(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ, box.getXsize(), box.getYsize(), box.getZsize());
    }
}
