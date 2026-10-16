package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.function.Supplier;

/**
 * The model block panel's "Equipment" section: six vanilla slots in a 2×3 grid, drawn on the
 * block's form by the armor and held item renderers there are already. The slot's name is its
 * tooltip; the armor icons tell the slots apart. From BBS FS 996eb0adc (Wemppy4/bbs-fs, MIT).
 */
public class ModelBlockAusruestung
{
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] NAMEN = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final Icon[] ICONS = {Icons.ARMOR_HELMET, Icons.ARMOR_CHESTPLATE, Icons.ARMOR_LEGGINGS, Icons.ARMOR_BOOTS, Icons.LIMB, Icons.LIMB};

    public final UISection section;
    private final UIItemStack[] slots = new UIItemStack[SLOTS.length];

    public ModelBlockAusruestung(Supplier<ModelBlockEntity> block, ModelBlockVerlauf verlauf)
    {
        for (int i = 0; i < SLOTS.length; i++)
        {
            EquipmentSlot slot = SLOTS[i];

            this.slots[i] = new UIItemStack((stack) -> verlauf.aendern(null, () -> block.get().getProperties().getEquipment().set(slot, stack)));
            this.slots[i].placeholder(ICONS[i]).tooltip(L10n.lang("bbs.ui.model_blocks.equipment." + NAMEN[i]));
        }

        this.section = new UISection(L10n.lang("bbs.ui.model_blocks.equipment.title"));
        this.section.setExpanded(false);
        this.section.fields.add(UI.row(this.slots[0], this.slots[1]), UI.row(this.slots[2], this.slots[3]), UI.row(this.slots[4], this.slots[5]));
    }

    public void fill(ModelBlockEntity modelBlock)
    {
        this.section.setVisible(modelBlock != null);

        for (int i = 0; modelBlock != null && i < SLOTS.length; i++)
        {
            this.slots[i].setStack(modelBlock.getProperties().getEquipment().get(SLOTS[i]));
        }
    }
}
