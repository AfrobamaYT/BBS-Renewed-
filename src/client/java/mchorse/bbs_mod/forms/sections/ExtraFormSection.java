package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Collections;
import java.util.List;

public class ExtraFormSection extends FormSection
{
    private FormCategory extra;

    public ExtraFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        FormCategory extra = new FormCategory(UIKeys.FORMS_CATEGORIES_EXTRA);
        AnchorForm anchor = new AnchorForm();
        BillboardForm billboard = new BillboardForm();
        LabelForm label = new LabelForm();
        ExtrudedForm extruded = new ExtrudedForm();
        BlockForm block = new BlockForm();
        ItemForm item = new ItemForm();
        MobForm mob = new MobForm();

        billboard.texture.set(Link.assets("textures/error.png"));
        extruded.texture.set(Link.assets("textures/error.png"));
        block.blockState.set(Blocks.GRASS_BLOCK.getDefaultState());
        item.stack.set(new ItemStack(Items.STICK));

        extra.addForm(anchor);
        extra.addForm(billboard);
        extra.addForm(label);
        extra.addForm(extruded);
        extra.addForm(block);
        extra.addForm(item);
        extra.addForm(mob);

        this.extra = extra;
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return Collections.singletonList(this.extra);
    }
}