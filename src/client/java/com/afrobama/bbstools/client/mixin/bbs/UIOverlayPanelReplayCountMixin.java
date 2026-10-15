// Coded by Afrobama/Claude — Army System: replay count in the replay menu.
//
// Shows how many replays are in the film, in the replay overlay panel's title
// ("Replay" -> "Replay (247)"). Injected into UIOverlayPanel.render (the base
// class that owns the title label) and filtered to the replays panel; the
// count is recomputed only when it changes (no per-frame allocation).
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIOverlayPanel.class, remap = false)
public abstract class UIOverlayPanelReplayCountMixin
{
    @Shadow public UILabel title;

    /* @Unique-feld bleibt stehen (wird beim weben zum echten instanzfeld der zielklasse) —
     * die kernel-logik liest/schreibt es über MixinBruecke.feldLesen/feldSchreiben auf DIESER
     * instanz, nicht in einer kernel-globalen map. */
    @Unique private int bbstools$lastCount = -1;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbstools$replayCount(UIContext context, CallbackInfo ci)
    {
        if (!((Object) this instanceof UIReplaysOverlayPanel rp)) return;

        /* BRÜCKEN-MUSTER (2026-07-26): logik liegt in
         * com.afrobama.blendertransform.UIOverlayPanelReplayCountLogik. */
        MixinBruecke.rufe("UIOverlayPanelReplayCountLogik", "replayCount", this, rp);
    }

}
