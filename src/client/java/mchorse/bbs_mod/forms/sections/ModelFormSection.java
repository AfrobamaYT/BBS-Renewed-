package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ModelFormSection extends SubFormSection
{
    public ModelFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        List<String> keys = BBSModClient.getModels().getAvailableKeys();

        keys.sort((a, b) -> a.compareToIgnoreCase(b));

        for (String key : keys)
        {
            this.add(key);
        }
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.FORMS_CATEGORIES_MODELS;
    }

    @Override
    protected Form create(String key)
    {
        ModelForm form = new ModelForm();

        form.model.set(key);

        return form;
    }

    @Override
    protected boolean isEqual(Form form, String key)
    {
        ModelForm modelForm = (ModelForm) form;

        return Objects.equals(modelForm.model.get(), key);
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link.path.startsWith("models/"))
        {
            String extension = this.getExtension(link);

            if (extension == null)
            {
                return;
            }

            String key = link.path.substring("models/".length());

            key = key.substring(0, key.length() - extension.length());

            if (event == WatchDogEvent.DELETED)
            {
                this.remove(key);
                this.parent.markDirty();
            }
            else if (event == WatchDogEvent.CREATED)
            {
                this.add(key);
                this.parent.markDirty();
            }
        }
    }

    private String getExtension(Link link)
    {
        if (BBSModClient.getModels().isRelodable(link))
        {
            return link.path.substring(link.path.lastIndexOf('/') + 1);
        }

        return null;
    }
}