package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParticleFormSection extends FormSection
{
    private Map<String, FormCategory> categories = new LinkedHashMap<>();

    public ParticleFormSection(FormCategories parent)
    {
        super(parent);
    }

    @Override
    public void initiate()
    {
        for (String key : BBSModClient.getParticles().getKeys())
        {
            this.addParticle(key);
        }
    }

    private String getKey(String key)
    {
        int slash = key.lastIndexOf('/');

        return slash >= 0 ? key.substring(0, slash) : "";
    }

    private FormCategory getCategory(String key)
    {
        return this.categories.computeIfAbsent(this.getKey(key), (k) ->
        {
            IKey uiKey = UIKeys.FORMS_CATEGORIES_PARTICLES;

            if (!key.isEmpty())
            {
                uiKey = IKey.comp(Arrays.asList(uiKey, IKey.raw(" (" + this.getKey(key) + ")")));
            }

            return new FormCategory(uiKey);
        });
    }

    private void addParticle(String key)
    {
        FormCategory category = this.getCategory(key);

        for (Form form : category.forms)
        {
            if (((ParticleForm) form).effect.get().equals(key))
            {
                return;
            }
        }

        ParticleForm form = new ParticleForm();

        form.effect.set(key);
        category.forms.add(form);
    }

    private void removeParticle(String key)
    {
        FormCategory category = this.getCategory(key);
        Iterator<Form> it = category.forms.iterator();

        while (it.hasNext())
        {
            ParticleForm next = (ParticleForm) it.next();

            if (next.effect.get().equals(key))
            {
                it.remove();
                this.parent.markDirty();
            }
        }

        if (category.forms.isEmpty())
        {
            this.categories.remove(this.getKey(key));
            this.parent.markDirty();
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        return new ArrayList<>(this.categories.values());
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link.path.startsWith("particles/") && link.path.endsWith(".json"))
        {
            String key = link.path.substring("particles/".length());

            key = key.substring(0, key.length() - ".json".length());

            if (event == WatchDogEvent.DELETED)
            {
                this.removeParticle(key);
            }
            else
            {
                this.addParticle(key);
            }

            this.parent.markDirty();
        }
    }
}