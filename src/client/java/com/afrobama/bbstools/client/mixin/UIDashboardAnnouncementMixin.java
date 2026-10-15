package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the BBSTools-Fabric announcement popup once per install on
 * first dashboard open. Replaces the fork-side UIFnafOverlayPanel
 * trigger that lived in UIDashboard.
 */
@Mixin(UIDashboard.class)
public abstract class UIDashboardAnnouncementMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bbstools$showAnnouncement(CallbackInfo ci)
    {
        /* BRÜCKEN-MUSTER (2026-07-26): dieser handler wird beim klassenladen in UIDashboard
         * gewoben und liegt damit im KnotClassLoader — vom kernel-swap unerreichbar. Die
         * gate-prüfung + das anzeigen liegen jetzt in
         * `com.afrobama.blendertransform.UIDashboardAnnouncementLogik` und werden bei jedem
         * swap mitgetauscht. */
        MixinBruecke.rufe("UIDashboardAnnouncementLogik", "zeigeAnnouncement", (UIDashboard) (Object) this);
    }

}
