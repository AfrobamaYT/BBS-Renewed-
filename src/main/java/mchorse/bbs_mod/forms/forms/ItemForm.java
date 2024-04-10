package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.properties.ItemStackProperty;
import net.minecraft.item.ItemStack;

public class ItemForm extends Form
{
    public final ItemStackProperty stack = new ItemStackProperty(this, "item_stack", ItemStack.EMPTY);

    public ItemForm()
    {
        this.register(this.stack);
    }
}