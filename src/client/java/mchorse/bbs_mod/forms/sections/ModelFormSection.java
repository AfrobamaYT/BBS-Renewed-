package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModelFormSection extends FormSection
{
    private ModelFormCategory formCategory = new ModelFormCategory();

    public ModelFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        List<Link> models = new ArrayList<>(BBSMod.getProvider().getLinksFromPath(Link.assets("models"), false));

        models.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        for (Link link : models)
        {
            DataPath dataPath = new DataPath(link.path);

            if (!dataPath.folder)
            {
                continue;
            }

            ModelForm form = new ModelForm();

            form.model.set(dataPath.getLast());
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return Collections.singletonList(this.formCategory);
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        super.accept(path, event);
    }
}