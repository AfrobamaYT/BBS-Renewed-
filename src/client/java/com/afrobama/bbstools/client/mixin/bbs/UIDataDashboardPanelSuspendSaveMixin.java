// Coded by Afrobama/Claude — suspend the editor's periodic auto-save while a film is playing.
//
// UIDataDashboardPanel.checkPeriodicSave runs every frame from render() and, when its timer fires
// (every BBSSettings.editorPeriodicSave seconds), calls save() → forceSave() → the repository
// serializes the ENTIRE film (for a 20k-replay crowd that's MapType/ListType walking 20k replays
// each with ~40 keyframe channels) and GZIPs it — all SYNCHRONOUSLY on the render thread. Caught
// red-handed in a playback thread dump: Deflater.deflate ← GZIPOutputStream ← DataStorage.write ←
// MapType.write ← ... ← checkPeriodicSave ← render. It is a multi-100ms render-thread stall mid
// playback (the frame-timeline "final"-phase spikes) and ~7% of playback CPU.
//
// Fix: skip the periodic save while filmIsPlaying (published by the render-loop bypass). Nothing is
// lost — the save resumes the moment the user pauses (the timer is still checked) and close() force-
// saves on panel exit. Flag-gated ENABLE_SUSPEND_SAVE_WHILE_PLAYING (default ON).
package com.afrobama.bbstools.client.mixin.bbs;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.afrobama.bbstools.perf.BBSPerfConfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel", remap = false)
public abstract class UIDataDashboardPanelSuspendSaveMixin
{
    @Inject(method = "checkPeriodicSave", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbstools$suspendSaveWhilePlaying(CallbackInfo ci)
    {
        /* BRUECKEN-MUSTER (Aufgabe #56): logik in
         * com.afrobama.blendertransform.SuspendSaveLogik. Der zaehler bleibt in
         * BBSPerfConfig -- ein statisches feld in der logik-klasse haette nach einem
         * kernel-tausch eine zweite, leere kopie. */
        if (MixinBruecke.rufeUndAbbrechen("SuspendSaveLogik", "aussetzen"))
        {
            ci.cancel();
        }
    }

}
