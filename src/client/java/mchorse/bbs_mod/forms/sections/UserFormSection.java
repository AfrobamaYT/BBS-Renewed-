package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.RecentFormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UserFormSection extends FormSection
{
    public List<UserFormCategory> categories = new ArrayList<>();

    public static File getUserCategoriesFile()
    {
        return BBSMod.getSettingsPath("forms.json");
    }

    public UserFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        this.readUserCategories(getUserCategoriesFile());
    }

    @Override
    public List<FormCategory> getCategories()
    {
        List<FormCategory> categoryList = new ArrayList<>();

        for (UserFormCategory category : this.categories)
        {
            categoryList.add(category);
        }

        return categoryList;
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {}

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

        for (UserFormCategory category : this.categories)
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
        this.parent.markDirty();
    }

    public void removeUserCategory(UserFormCategory category)
    {
        this.categories.remove(category);
        this.parent.markDirty();
    }
}