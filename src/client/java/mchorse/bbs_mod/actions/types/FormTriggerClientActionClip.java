package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.triggers.StateTrigger;

public class FormTriggerClientActionClip extends FormTriggerActionClip
{
    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        if (entity.getForm() instanceof ModelForm modelForm)
        {
            for (StateTrigger stateTrigger : modelForm.triggers.triggers)
            {
                if (stateTrigger.id.equals(this.trigger.get()))
                {
                    ((ModelFormRenderer) FormUtilsClient.getRenderer(modelForm)).triggerState(stateTrigger);

                    return;
                }
            }
        }
    }
}