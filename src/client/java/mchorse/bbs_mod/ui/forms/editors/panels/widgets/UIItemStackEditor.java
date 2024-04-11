package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIItemStackEditor extends UIElement
{
    private static final List<String> itemIDs = new ArrayList<>();

    public UISearchList<String> itemList;

    private Consumer<ItemStack> callback;
    private ItemStack stack;

    static
    {
        for (RegistryKey<Item> key : Registries.ITEM.getKeys())
        {
            itemIDs.add(key.getValue().toString());
        }

        itemIDs.sort(String::compareToIgnoreCase);
    }

    public UIItemStackEditor(Consumer<ItemStack> callback)
    {
        this.callback = callback;

        this.itemList = new UISearchList<>(new UIStringList((l) -> this.setItem(l.get(0))));
        this.itemList.label(UIKeys.GENERAL_SEARCH).list.background();
        this.itemList.h(20 + 96);

        this.column().vertical().stretch();

        this.itemList.list.clear();
        this.itemList.list.add(itemIDs);

        this.add(this.itemList);
    }

    private void setItem(String s)
    {
        ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier(s)));

        this.stack = stack;

        if (this.callback != null)
        {
            this.callback.accept(stack);
        }
    }

    public void setStack(ItemStack itemStack)
    {
        this.stack = itemStack.copy();

        this.itemList.list.setCurrentScroll(Registries.ITEM.getId(itemStack.getItem()).toString());
    }
}