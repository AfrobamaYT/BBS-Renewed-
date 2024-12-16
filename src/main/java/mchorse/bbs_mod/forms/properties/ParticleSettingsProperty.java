package mchorse.bbs_mod.forms.properties;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ParticleSettingsProperty extends BaseTweenProperty<ParticleSettings>
{
    public ParticleSettingsProperty(Form form, String key, ParticleSettings value)
    {
        super(form, key, value, KeyframeFactories.PARTICLE_SETTINGS);
    }
}