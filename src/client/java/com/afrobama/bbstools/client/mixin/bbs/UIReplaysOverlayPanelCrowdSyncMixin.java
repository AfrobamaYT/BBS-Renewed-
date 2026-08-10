/* Coded by Afrobama/Claude — Tier 1d crowdMode toggle selection sync.
 *
 * BBS's UIReplaysOverlayPanel.setReplay(Replay) syncs every built-in
 * widget (label, enabled, actor, shadow, ...) from the newly selected
 * replay's value snapshots. Our injected "Crowd Mode" toggle is added
 * via the extensions list AFTER construction, so it isn't part of the
 * built-in sync block. This @Inject(TAIL) mirrors a single line of that
 * sync logic for our widget so switching between replays in the
 * left-side list updates the checkbox's displayed state.
 *
 * remap = false because the BBS classes are not Yarn-remapped (BBS
 * ships its own POJOs in production, identical to dev names).
 */
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.client.ui.ReplayCrowdModeToggle;
import com.afrobama.bbstools.client.util.CrowdModeRegistry;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIReplaysOverlayPanel.class, remap = false)
public abstract class UIReplaysOverlayPanelCrowdSyncMixin
{
    @Inject(method = "setReplay", at = @At("TAIL"), remap = false)
    private void bbstools$syncCrowdModeToggle(Replay replay, CallbackInfo ci)
    {
        MixinBruecke.rufe("CrowdModeSyncLogik", "schalterAbgleichen", replay);
    }

}
