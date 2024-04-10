package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FormCategories
{
    private List<FormCategory> categories = new ArrayList<>();

    public static File getUserCategoriesFile()
    {
        return BBSMod.getSettingsPath("forms.json");
    }

    public void setup()
    {
        FormCategory extra = new FormCategory(UIKeys.FORMS_CATEGORIES_EXTRA);
        BillboardForm billboard = new BillboardForm();
        LabelForm label = new LabelForm();
        ExtrudedForm extruded = new ExtrudedForm();
        BlockForm block = new BlockForm();
        ItemForm item = new ItemForm();

        billboard.texture.set(Link.assets("textures/error.png"));
        extruded.texture.set(Link.assets("textures/error.png"));
        block.blockState.set(Blocks.GRASS_BLOCK.getDefaultState());
        item.stack.set(new ItemStack(Items.STICK));

        extra.forms.add(billboard);
        extra.forms.add(label);
        extra.forms.add(extruded);
        extra.forms.add(block);
        extra.forms.add(item);

        this.categories.add(new RecentFormCategory());
        this.readUserCategories();
        this.categories.add(new ModelFormCategory());
        this.categories.add(new ParticleFormCategory());
        this.categories.add(extra);
    }

    /* User categories */

    public List<FormCategory> getCategories()
    {
        return Collections.unmodifiableList(this.categories);
    }

    public List<UserFormCategory> getUserCategories()
    {
        List<UserFormCategory> categories = new ArrayList<>();

        for (FormCategory category : this.categories)
        {
            if (category instanceof UserFormCategory)
            {
                categories.add((UserFormCategory) category);
            }
        }

        return categories;
    }

    public void readUserCategories()
    {
        this.readUserCategories(getUserCategoriesFile());
    }

    public void readUserCategories(File file)
    {
        if (!file.exists())
        {
            return;
        }

        try
        {
            MapType data = (MapType) DataToString.read(file);

            for (String key : data.keys())
            {
                UserFormCategory category = new UserFormCategory(IKey.raw(key));

                category.fromData(data.getMap(key));
                this.categories.add(category);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void writeUserCategories()
    {
        this.writeUserCategories(getUserCategoriesFile());
    }

    public void writeUserCategories(File file)
    {
        MapType data = new MapType(false);

        for (UserFormCategory category : this.getUserCategories())
        {
            data.put(category.title.get(), category.toData());
        }

        try
        {
            DataToString.write(file, data, true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void addUserCategory(UserFormCategory category)
    {
        int index = 0;

        for (FormCategory formCat : this.categories)
        {
            if (formCat instanceof RecentFormCategory || formCat instanceof UserFormCategory)
            {
                index += 1;
            }
            else
            {
                break;
            }
        }

        this.categories.add(index, category);
    }
}