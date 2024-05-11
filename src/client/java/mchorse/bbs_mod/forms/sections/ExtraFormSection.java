package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
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

        billboard.texture.set(Link.assets("textures/error.png"));
        extruded.texture.set(Link.assets("textures/error.png"));
        block.blockState.set(Blocks.GRASS_BLOCK.getDefaultState());
        item.stack.set(new ItemStack(Items.STICK));

        extra.forms.add(anchor);
        extra.forms.add(billboard);
        extra.forms.add(label);
        extra.forms.add(extruded);
        extra.forms.add(block);
        extra.forms.add(item);

        this.extra = extra;
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return Collections.singletonList(this.extra);
    }
}