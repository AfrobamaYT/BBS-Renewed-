package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores click-to-morph in the dashboard morphing tab.
 *
 * <p>CML commit c5847babc ("Add morph button and siabled morph when cliking
 * on form", 2026-05-08) gated the palette pick callback behind the new
 * {@code BBSSettings.morphingAutoMorph} setting (default FALSE, CMLSettings
 * "morphing.auto_morph"). Result: picking a form in the morph tab no longer
 * morphs the real player — only the tiny new checkmark button or the opt-in
 * setting does. Our first-person hand render depends on the real player's
 * morph form ({@code PlayerEntityRendererMixin.onRenderArmBegin} resolves the
 * arm via {@code Morph.getMorph(player).getForm()}), so without the morph the
 * hand is always the default player hand.</p>
 *
 * <p>Fix: after the panel constructor built the palette with the gated
 * callback, replace {@code palette.callback} (public field) with one that
 * ALWAYS calls the upstream private {@code setForm} (via
 * {@link UIMorphingPanelSetFormInvoker}) — i.e. exactly what upstream does
 * when {@code morphingAutoMorph} is enabled, just unconditionally. The
 * separate "Morph" button keeps working; the setting becomes irrelevant.</p>
 */
@Mixin(value = UIMorphingPanel.class, remap = false)
public abstract class UIMorphingPanelClickMorphMixin
{
    /* require = 1 on purpose: this config's defaultRequire is 0 (silent
     * failure). The target is our own tree's class — if this stops applying
     * after an upstream merge, we want a loud boot error, not a silently
     * dead morph tab again. */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void bbstools$restoreClickToMorph(UIDashboard dashboard, CallbackInfo ci)
    {
        MixinBruecke.rufe("UIMorphingPanelLogik", "klickZumMorphen", this);
    }
}
