package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.ui.UIKeys;

public class ParticleFormCategory extends FormCategory
{
    public ParticleFormCategory()
    {
        super(UIKeys.FORMS_CATEGORIES_PARTICLES);
    }

    @Override
    public void update()
    {
        super.update();

        this.forms.clear();

        for (String key : BBSModClient.getParticles().getKeys())
        {
            ParticleForm form = new ParticleForm();

            form.effect.set(key);
            this.forms.add(form);
        }
    }
}