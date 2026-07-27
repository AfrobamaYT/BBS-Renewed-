package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.ModelRagdollProxyActionClip;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Editor UI for {@link ModelRagdollProxyActionClip}. Currently no
 * tunable fields beyond the inherited start tick / duration — the
 * clip just triggers a default villager-proxy ragdoll on its target
 * actor. Future versions may add bone-mapping overrides + ragdoll
 * physics tuning here.
 */
public class UIModelRagdollProxyClip extends UIActionClip<ModelRagdollProxyActionClip>
{
    public UIModelRagdollProxyClip(ModelRagdollProxyActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UI.label(IKey.constant("§7ENTITY-PROXY  RAGDOLL")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("§7Spawns invisible villager at the actor's")));
        this.panels.add(UI.label(IKey.constant("§7position + kills it. PhysicsMod ragdolls")));
        this.panels.add(UI.label(IKey.constant("§7it; we map ragdoll body transforms onto")));
        this.panels.add(UI.label(IKey.constant("§7the actor's BOBJ bones (head, body,")));
        this.panels.add(UI.label(IKey.constant("§7arm_left/right, leg_left/right).")));
        this.panels.add(UI.label(IKey.constant("§7§oSingleplayer only.")).marginTop(8));
    }

}
